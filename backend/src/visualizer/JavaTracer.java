package visualizer;

import com.sun.jdi.ArrayReference;
import com.sun.jdi.Bootstrap;
import com.sun.jdi.Field;
import com.sun.jdi.LocalVariable;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.PrimitiveValue;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.StackFrame;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.Value;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventQueue;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.event.ExceptionEvent;
import com.sun.jdi.event.MethodEntryEvent;
import com.sun.jdi.event.StepEvent;
import com.sun.jdi.event.VMDeathEvent;
import com.sun.jdi.event.VMDisconnectEvent;
import com.sun.jdi.event.VMStartEvent;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.ExceptionRequest;
import com.sun.jdi.request.MethodEntryRequest;
import com.sun.jdi.request.StepRequest;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JavaTracer {
    private static final int MAX_STEPS = 3000;
    private static final int MAX_HEAP_OBJECTS = 500;
    private static final int MAX_ARRAY_ITEMS = 200;
    private static final int MAX_NODE_DEPTH = 60;
    private static final int MAX_TIME_LIMIT_SECONDS = 15;

    private JavaTracer() {
    }

    public static List<Map<String, Object>> trace(String code, String input) throws Exception {
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try {
                return executeTrace(code, input);
            } catch (Exception e) {
                if (e.getCause() instanceof Exception) {
                    throw new RuntimeException(e.getCause());
                }
                throw new RuntimeException(e);
            }
        }).get(MAX_TIME_LIMIT_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
    }

    private static List<Map<String, Object>> executeTrace(String code, String input) throws Exception {
        CodeBuilder.BuildResult build = CodeBuilder.build(code, input);

        Path tempDir = Files.createTempDirectory("java-trace-" + Instant.now().toEpochMilli());
        try {
            Path primaryFile = tempDir.resolve(build.primaryClassName + ".java");
            Files.writeString(primaryFile, build.primaryCode, StandardCharsets.UTF_8);
            if (!build.isDirect && build.mainCode != null) {
                Path mainFile = tempDir.resolve("Main.java");
                Files.writeString(mainFile, build.mainCode, StandardCharsets.UTF_8);
            }
            compile(tempDir);
            return runWithJdi(tempDir, build.mainClassName, build.primaryClassName, input);
        } finally {
            deleteDirectory(tempDir.toFile());
        }
    }

    private static void compile(Path tempDir) throws IOException, InterruptedException {
        List<String> sources = new ArrayList<>();
        Files.list(tempDir)
                .filter(path -> path.getFileName().toString().endsWith(".java"))
                .forEach(path -> sources.add(path.getFileName().toString()));
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("No Java sources to compile.");
        }
        List<String> command = new ArrayList<>();
        command.add("javac");
        command.add("-g");
        command.addAll(sources);
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(tempDir.toFile());
        Process process = builder.start();
        String stderr = readAll(process.getErrorStream());
        int exit = process.waitFor();
        if (exit != 0) {
            throw new IllegalArgumentException("Compilation failed: " + stderr);
        }
    }

    private static List<Map<String, Object>> runWithJdi(Path tempDir, String mainClass, String targetClass, String input) throws Exception {
        LaunchingConnector connector = findLaunchingConnector();
        Map<String, Connector.Argument> args = connector.defaultArguments();
        args.get("main").setValue(mainClass);
        args.get("options").setValue("-cp " + tempDir.toAbsolutePath());

        VirtualMachine vm = connector.launch(args);
        Process process = vm.process();
        ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();
        consumeStream(process.getInputStream(), System.out);
        consumeStream(process.getErrorStream(), stderrBuffer);
        writeInput(process, input);

        List<Map<String, Object>> trace = new ArrayList<>();
        try {
            EventRequestManager manager = vm.eventRequestManager();

            MethodEntryRequest entryRequest = manager.createMethodEntryRequest();
            entryRequest.addClassFilter(targetClass + "*");
            entryRequest.setSuspendPolicy(StepRequest.SUSPEND_EVENT_THREAD);
            entryRequest.enable();

            ExceptionRequest exceptionRequest = manager.createExceptionRequest(null, true, true);
            exceptionRequest.addClassFilter(targetClass + "*");
            exceptionRequest.setSuspendPolicy(StepRequest.SUSPEND_EVENT_THREAD);
            exceptionRequest.enable();

            EventQueue queue = vm.eventQueue();
            boolean running = true;
            StepRequest activeStep = null;
            int steps = 0;

            while (running) {
                EventSet eventSet = queue.remove();
                for (Event event : eventSet) {
                    if (event instanceof VMStartEvent) {
                        // VM started
                    } else if (event instanceof MethodEntryEvent) {
                        MethodEntryEvent entryEvent = (MethodEntryEvent) event;
                        ThreadReference thread = entryEvent.thread();
                        if (activeStep != null) {
                            activeStep.disable();
                        }
                        activeStep = manager.createStepRequest(thread, StepRequest.STEP_LINE, StepRequest.STEP_INTO);
                        activeStep.addClassFilter(targetClass + "*");
                        activeStep.addClassExclusionFilter("java.*");
                        activeStep.addClassExclusionFilter("sun.*");
                        activeStep.addClassExclusionFilter("jdk.*");
                        activeStep.setSuspendPolicy(StepRequest.SUSPEND_EVENT_THREAD);
                        activeStep.enable();
                    } else if (event instanceof ExceptionEvent) {
                        // Exception caught
                        ExceptionEvent exEvent = (ExceptionEvent) event;
                        // We could capture variables here to show the state at exception time
                    } else if (event instanceof StepEvent) {
                        StepEvent stepEvent = (StepEvent) event;
                        ThreadReference thread = stepEvent.thread();
                        if (steps++ > MAX_STEPS) {
                            running = false;
                            break;
                        }
                        Map<String, Object> step = captureStep(thread);
                        if (step != null) {
                            trace.add(step);
                        }
                    } else if (event instanceof VMDeathEvent || event instanceof VMDisconnectEvent) {
                        running = false;
                    }
                }
                eventSet.resume();
            }
            
            String stderr = stderrBuffer.toString(StandardCharsets.UTF_8);
            if (!stderr.isEmpty() && trace.isEmpty()) {
                throw new RuntimeException("Runtime Error: " + stderr);
            }
            
        } finally {
            try {
                vm.dispose();
            } catch (Exception ignored) {
            }
        }
        return trace;
    }

    private static void consumeStream(InputStream stream, OutputStream target) {
        Thread thread = new Thread(() -> {
            try {
                stream.transferTo(target);
            } catch (IOException ignored) {
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    private static Map<String, Object> captureStep(ThreadReference thread) {
        try {
            List<StackFrame> frames = thread.frames();
            if (frames.isEmpty()) {
                return null;
            }
            StackFrame frame = frames.get(0);
            int line = frame.location().lineNumber();
            Map<String, Object> locals = new LinkedHashMap<>();
            List<Map<String, Object>> heap = TraceModels.heapList();
            Map<Long, Map<String, Object>> heapSeen = new HashMap<>();

            for (LocalVariable var : frame.visibleVariables()) {
                Value value = frame.getValue(var);
                Object mapped = mapValue(value, heap, heapSeen, var.name(), 0);
                locals.put(var.name(), mapped);
            }

            Map<String, Object> focus = TraceModels.focus(null, null, null);
            return TraceModels.step(line, locals, heap, focus.isEmpty() ? null : focus);
        } catch (Exception ex) {
            return null;
        }
    }

    private static Object mapValue(Value value, List<Map<String, Object>> heap, Map<Long, Map<String, Object>> heapSeen, String nameHint, int depth) {
        if (value == null) {
            return null;
        }
        if (value instanceof PrimitiveValue) {
            return mapPrimitive((PrimitiveValue) value);
        }
        if (value instanceof ArrayReference) {
            return mapArray((ArrayReference) value, heap, heapSeen, nameHint);
        }
        if (value instanceof ObjectReference) {
            ObjectReference ref = (ObjectReference) value;
            String refId = "obj-" + ref.uniqueID();
            if (!heapSeen.containsKey(ref.uniqueID())) {
                Map<String, Object> object = mapObject(ref, heap, heapSeen, depth + 1);
                if (object != null) {
                    heap.add(object);
                    heapSeen.put(ref.uniqueID(), object);
                }
            }
            return TraceModels.ref(refId);
        }
        return value.toString();
    }

    private static Map<String, Object> mapArray(ArrayReference array, List<Map<String, Object>> heap, Map<Long, Map<String, Object>> heapSeen, String nameHint) {
        String refId = "arr-" + array.uniqueID();
        if (heapSeen.containsKey(array.uniqueID())) {
            return TraceModels.ref(refId);
        }
        List<Object> items = new ArrayList<>();
        int count = Math.min(array.length(), MAX_ARRAY_ITEMS);
        for (int i = 0; i < count; i++) {
            Value value = array.getValue(i);
            if (value instanceof PrimitiveValue) {
                items.add(mapPrimitive((PrimitiveValue) value));
            } else {
                items.add(mapValue(value, heap, heapSeen, null, 0));
            }
        }
        Map<String, Object> arrayModel = TraceModels.heapArray(refId, nameHint, items);
        heap.add(arrayModel);
        heapSeen.put(array.uniqueID(), arrayModel);
        return TraceModels.ref(refId);
    }

    private static Map<String, Object> mapObject(ObjectReference ref, List<Map<String, Object>> heap, Map<Long, Map<String, Object>> heapSeen, int depth) {
        if (heapSeen.size() > MAX_HEAP_OBJECTS || depth > MAX_NODE_DEPTH) {
            return null;
        }
        ReferenceType type = ref.referenceType();
        List<Field> fields = type.allFields();
        Field valueField = findField(fields, "value", "val");
        Field nextField = findField(fields, "next");
        Field leftField = findField(fields, "left");
        Field rightField = findField(fields, "right");

        if (valueField != null || nextField != null || leftField != null || rightField != null) {
            String refId = "node-" + ref.uniqueID();
            Object value = valueField != null ? mapValue(ref.getValue(valueField), heap, heapSeen, null, depth + 1) : "";
            String next = nextField != null ? refId(ref.getValue(nextField)) : null;
            String left = leftField != null ? refId(ref.getValue(leftField)) : null;
            String right = rightField != null ? refId(ref.getValue(rightField)) : null;
            Map<String, Object> node = TraceModels.heapNode(refId, unwrapRefValue(value), next, left, right);
            if (next != null) {
                heap.add(TraceModels.heapList("list-" + ref.uniqueID(), "list", refId));
            }
            return node;
        }
        return null;
    }

    private static Object unwrapRefValue(Object value) {
        if (value instanceof Map) {
            return ((Map<?, ?>) value).get("ref");
        }
        return value;
    }

    private static Object mapPrimitive(PrimitiveValue value) {
        String type = value.type().name();
        switch (type) {
            case "boolean":
                return Boolean.parseBoolean(value.toString());
            case "char":
                return value.toString();
            case "byte":
            case "short":
            case "int":
            case "long":
                return Long.parseLong(value.toString());
            case "float":
            case "double":
                return Double.parseDouble(value.toString());
            default:
                return value.toString();
        }
    }

    private static String refId(Value value) {
        if (value instanceof ObjectReference) {
            return "node-" + ((ObjectReference) value).uniqueID();
        }
        return null;
    }

    private static Field findField(List<Field> fields, String... names) {
        for (String name : names) {
            for (Field field : fields) {
                if (field.name().equals(name)) {
                    return field;
                }
            }
        }
        return null;
    }

    private static String extractClassName(String code) {
        String[] tokens = code.split("\\bclass\\b");
        if (tokens.length < 2) {
            return null;
        }
        String tail = tokens[1].trim();
        String[] parts = tail.split("\\s+");
        return parts.length > 0 ? parts[0] : null;
    }

    private static LaunchingConnector findLaunchingConnector() {
        for (LaunchingConnector connector : Bootstrap.virtualMachineManager().launchingConnectors()) {
            if ("com.sun.jdi.CommandLineLaunch".equals(connector.name())) {
                return connector;
            }
        }
        throw new IllegalStateException("Could not find command line launching connector");
    }

    private static void writeInput(Process process, String input) throws IOException {
        if (input == null) {
            return;
        }
        OutputStream out = process.getOutputStream();
        out.write((input + "\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private static String readAll(InputStream stream) throws IOException {
        return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void deleteDirectory(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] files = file.listFiles();
        if (files != null) {
            for (File child : files) {
                deleteDirectory(child);
            }
        }
        file.delete();
    }
}

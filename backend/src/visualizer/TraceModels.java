package visualizer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TraceModels {
    private TraceModels() {
    }

    public static Map<String, Object> traceFile(String title, String language, String code, List<Map<String, Object>> inputs) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("title", title);
        root.put("language", language);
        root.put("code", code);
        root.put("inputs", inputs);
        return root;
    }

    public static Map<String, Object> inputCase(String id, String label, String value, List<Map<String, Object>> trace) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("id", id);
        input.put("label", label);
        input.put("value", value);
        input.put("trace", trace);
        return input;
    }

    public static Map<String, Object> step(int line, Map<String, Object> locals, List<Map<String, Object>> heap, Map<String, Object> focus) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("line", line);
        step.put("locals", locals);
        step.put("heap", heap);
        if (focus != null) {
            step.put("focus", focus);
        }
        return step;
    }

    public static Map<String, Object> focus(String arrayRef, Map<String, Integer> indices, List<String> refs) {
        Map<String, Object> focus = new LinkedHashMap<>();
        if (arrayRef != null) {
            focus.put("array", arrayRef);
        }
        if (indices != null && !indices.isEmpty()) {
            focus.put("indices", indices);
        }
        if (refs != null && !refs.isEmpty()) {
            focus.put("refs", refs);
        }
        return focus;
    }

    public static Map<String, Object> heapArray(String ref, String name, List<Object> items) {
        Map<String, Object> array = new LinkedHashMap<>();
        array.put("kind", "array");
        array.put("ref", ref);
        if (name != null) {
            array.put("name", name);
        }
        array.put("items", items);
        return array;
    }

    public static Map<String, Object> heapNode(String ref, Object value, String next, String left, String right) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("kind", "node");
        node.put("ref", ref);
        node.put("value", value);
        if (next != null) {
            node.put("next", next);
        }
        if (left != null) {
            node.put("left", left);
        }
        if (right != null) {
            node.put("right", right);
        }
        return node;
    }

    public static Map<String, Object> heapList(String ref, String name, String head) {
        Map<String, Object> list = new LinkedHashMap<>();
        list.put("kind", "list");
        list.put("ref", ref);
        if (name != null) {
            list.put("name", name);
        }
        list.put("head", head);
        return list;
    }

    public static Map<String, Object> heapGraph(String ref, String name, List<Map<String, Object>> nodes, List<Map<String, Object>> edges) {
        Map<String, Object> graph = new LinkedHashMap<>();
        graph.put("kind", "graph");
        graph.put("ref", ref);
        if (name != null) {
            graph.put("name", name);
        }
        graph.put("nodes", nodes);
        graph.put("edges", edges);
        return graph;
    }

    public static Map<String, Object> ref(String refId) {
        Map<String, Object> ref = new LinkedHashMap<>();
        ref.put("ref", refId);
        return ref;
    }

    public static List<Map<String, Object>> heapList() {
        return new ArrayList<>();
    }
}

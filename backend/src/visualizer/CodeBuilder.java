package visualizer;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class CodeBuilder {
    private static final Pattern CLASS_PATTERN = Pattern.compile("\\bclass\\s+(\\w+)");
    private static final Pattern MAIN_PATTERN = Pattern.compile("\\bstatic\\s+void\\s+main\\s*\\(");
    private static final Pattern METHOD_PATTERN = Pattern.compile(
            "(public|private|protected)?\\s*(static\\s+)?([\\w\\[\\]<>]+)\\s+(\\w+)\\s*\\(([^)]*)\\)");

    private CodeBuilder() {
    }

    static BuildResult build(String code, String input) {
        String className = extractClassName(code);
        boolean hasMain = MAIN_PATTERN.matcher(code).find();
        if (className != null && hasMain) {
            String fullCode = addImports(code);
            return BuildResult.direct(className, fullCode);
        }

        String solutionClass = className != null ? className : "Solution";
        MethodSig method = extractMethod(code);
        if (method == null || "main".equals(method.name)) {
            throw new IllegalArgumentException("Could not find an entry method to run.");
        }

        String solutionCode = className != null ? addImports(code) : wrapInSolution(code, solutionClass);
        String mainCode = buildMain(solutionClass, method, input);
        return BuildResult.wrapper(solutionClass, solutionCode, mainCode);
    }

    private static String addImports(String code) {
        if (code.contains("import java.util.")) {
            return code;
        }
        String trimmed = code.trim();
        if (trimmed.startsWith("package ")) {
            int semi = trimmed.indexOf(';');
            if (semi > 0 && semi < trimmed.indexOf('\n', semi)) {
                return trimmed.substring(0, semi + 1) + "\n\nimport java.util.*;\nimport java.util.stream.*;\n" + trimmed.substring(semi + 1);
            }
        }
        return "import java.util.*;\nimport java.util.stream.*;\n" + code;
    }

    private static String extractClassName(String code) {
        Matcher matcher = CLASS_PATTERN.matcher(code);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private static MethodSig extractMethod(String code) {
        Matcher matcher = METHOD_PATTERN.matcher(code);
        MethodSig candidate = null;
        while (matcher.find()) {
            String returnType = matcher.group(3);
            String name = matcher.group(4);
            String params = matcher.group(5);
            if ("class".equals(returnType)) {
                continue;
            }
            if ("main".equals(name)) {
                continue;
            }
            candidate = new MethodSig(returnType, name, parseParams(params));
            break;
        }
        return candidate;
    }

    private static List<Param> parseParams(String params) {
        List<Param> result = new ArrayList<>();
        String trimmed = params == null ? "" : params.trim();
        if (trimmed.isEmpty()) {
            return result;
        }
        String[] parts = trimmed.split(",");
        for (String part : parts) {
            String cleaned = part.trim().replace("final ", "");
            String[] tokens = cleaned.split("\\s+");
            if (tokens.length < 2) {
                continue;
            }
            String name = tokens[tokens.length - 1];
            StringBuilder typeBuilder = new StringBuilder();
            for (int i = 0; i < tokens.length - 1; i++) {
                if (i > 0) {
                    typeBuilder.append(' ');
                }
                typeBuilder.append(tokens[i]);
            }
            result.add(new Param(typeBuilder.toString(), name));
        }
        return result;
    }

    private static String wrapInSolution(String code, String className) {
        return "import java.util.*;\nimport java.util.stream.*;\n\npublic class " + className + " {\n" + code + "\n}\n";
    }

    private static String buildMain(String solutionClass, MethodSig method, String input) {
        List<String> argExpr = InputParser.buildArguments(method.params, input);
        String invocation = buildInvocation(method.returnType, solutionClass, method.name, argExpr);
        return "public class Main {\n" +
                "  public static void main(String[] args) throws Exception {\n" +
                "    " + solutionClass + " solution = new " + solutionClass + "();\n" +
                "    " + invocation + "\n" +
                "  }\n" +
                "}\n";
    }

    private static String buildInvocation(String returnType, String className, String methodName, List<String> args) {
        String argList = String.join(", ", args);
        if ("void".equals(returnType)) {
            return "solution." + methodName + "(" + argList + ");";
        }
        return returnType + " result = solution." + methodName + "(" + argList + ");";
    }

    static final class MethodSig {
        final String returnType;
        final String name;
        final List<Param> params;

        MethodSig(String returnType, String name, List<Param> params) {
            this.returnType = returnType;
            this.name = name;
            this.params = params;
        }
    }

    static final class Param {
        final String type;
        final String name;

        Param(String type, String name) {
            this.type = type;
            this.name = name;
        }
    }

    static final class BuildResult {
        final boolean isDirect;
        final String mainClassName;
        final String primaryClassName;
        final String primaryCode;
        final String mainCode;

        private BuildResult(boolean isDirect, String mainClassName, String primaryClassName, String primaryCode, String mainCode) {
            this.isDirect = isDirect;
            this.mainClassName = mainClassName;
            this.primaryClassName = primaryClassName;
            this.primaryCode = primaryCode;
            this.mainCode = mainCode;
        }

        static BuildResult direct(String className, String code) {
            return new BuildResult(true, className, className, code, null);
        }

        static BuildResult wrapper(String primaryClass, String primaryCode, String mainCode) {
            return new BuildResult(false, "Main", primaryClass, primaryCode, mainCode);
        }
    }
}

package visualizer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class InputParser {
    private InputParser() {
    }

    static List<String> buildArguments(List<CodeBuilder.Param> params, String rawInput) {
        List<String> args = new ArrayList<>();
        Map<String, String> named = parseNamed(rawInput);
        for (CodeBuilder.Param param : params) {
            String rawValue = named.get(param.name);
            if (rawValue == null && params.size() == 1) {
                rawValue = rawInput == null ? "" : rawInput.trim();
            }
            if (rawValue == null) {
                rawValue = "";
            }
            args.add(buildLiteral(param.type, rawValue.trim()));
        }
        return args;
    }

    private static Map<String, String> parseNamed(String rawInput) {
        Map<String, String> map = new HashMap<>();
        if (rawInput == null) {
            return map;
        }
        List<String> parts = splitByComma(rawInput);
        for (String part : parts) {
            int idx = part.indexOf('=');
            if (idx > 0) {
                map.put(part.substring(0, idx).trim(), part.substring(idx + 1).trim());
            }
        }
        return map;
    }

    private static String buildLiteral(String type, String raw) {
        int arrayDepth = countArrayDepth(type);
        String baseType = type.replace("[]", "").trim();
        if (arrayDepth > 0) {
            return buildArrayLiteral(baseType, arrayDepth, raw);
        }
        if (type.startsWith("List") || type.startsWith("ArrayList") || type.startsWith("LinkedList")) {
            return buildListLiteral(type, raw);
        }
        return buildScalarLiteral(baseType, raw);
    }

    private static String buildListLiteral(String type, String raw) {
        String cleaned = raw.trim();
        List<String> elements = splitTopLevel(cleaned);
        // Extract generic type if present, e.g. List<Integer> -> Integer
        String genericType = "Object";
        int open = type.indexOf('<');
        int close = type.lastIndexOf('>');
        if (open > 0 && close > open) {
            genericType = type.substring(open + 1, close).trim();
        }
        
        List<String> inner = new ArrayList<>();
        for (String element : elements) {
            inner.add(buildScalarLiteral(genericType, element));
        }
        
        String listImpl = "ArrayList";
        if (type.startsWith("LinkedList")) {
            listImpl = "LinkedList";
        }
        
        if (inner.isEmpty()) {
            return "new java.util." + listImpl + "<>()";
        }
        return "new java.util." + listImpl + "<>(java.util.Arrays.asList(" + String.join(", ", inner) + "))";
    }

    private static int countArrayDepth(String type) {
        int depth = 0;
        int idx = type.indexOf("[]");
        while (idx >= 0) {
            depth++;
            idx = type.indexOf("[]", idx + 2);
        }
        return depth;
    }

    private static String buildArrayLiteral(String baseType, int depth, String raw) {
        String cleaned = raw.trim();
        if (cleaned.isEmpty()) {
            return "new " + baseType + repeat("[]", depth) + "{}";
        }
        List<String> elements = splitTopLevel(cleaned);
        List<String> inner = new ArrayList<>();
        for (String element : elements) {
            if (depth == 1) {
                inner.add(buildScalarLiteral(baseType, element.trim()));
            } else {
                inner.add(buildArrayLiteral(baseType, depth - 1, element.trim()));
            }
        }
        return "new " + baseType + repeat("[]", depth) + "{" + String.join(", ", inner) + "}";
    }

    private static String buildScalarLiteral(String type, String raw) {
        String value = raw.trim();
        if (value.isEmpty()) {
            return defaultFor(type);
        }
        switch (type) {
            case "int":
            case "long":
            case "double":
            case "float":
            case "short":
            case "byte":
                return value;
            case "boolean":
                return value.toLowerCase();
            case "String":
                return quote(value);
            default:
                return value;
        }
    }

    private static String defaultFor(String type) {
        switch (type) {
            case "int":
            case "long":
            case "short":
            case "byte":
                return "0";
            case "double":
            case "float":
                return "0.0";
            case "boolean":
                return "false";
            case "String":
                return "\"\"";
            default:
                return "null";
        }
    }

    private static String quote(String value) {
        String trimmed = value;
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed;
        }
        if (trimmed.startsWith("'") && trimmed.endsWith("'")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        return "\"" + trimmed.replace("\\\"", "\\\\\"") + "\"";
    }

    private static List<String> splitTopLevel(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        return splitByComma(trimmed);
    }

    private static List<String> splitByComma(String raw) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (ch == '[') {
                depth++;
            } else if (ch == ']') {
                depth--;
            } else if (ch == ',' && depth == 0) {
                parts.add(raw.substring(start, i).trim());
                start = i + 1;
            }
        }
        if (start < raw.length()) {
            parts.add(raw.substring(start).trim());
        }
        List<String> cleaned = new ArrayList<>();
        for (String part : parts) {
            if (!part.isEmpty()) {
                cleaned.add(part);
            }
        }
        return cleaned;
    }

    private static String repeat(String value, int count) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < count; i++) {
            builder.append(value);
        }
        return builder.toString();
    }
}

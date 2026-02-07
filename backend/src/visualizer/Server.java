package visualizer;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public final class Server {
    private static final int PORT = 8080;

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/run", new RunHandler());
        // Use a fixed thread pool to prevent resource exhaustion from too many concurrent requests
        server.setExecutor(Executors.newFixedThreadPool(10));
        server.start();
        System.out.println("Java tracer running on http://localhost:" + PORT);
    }

    private static final class RunHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                Headers headers = exchange.getResponseHeaders();
                headers.set("Access-Control-Allow-Origin", "*");
                headers.set("Access-Control-Allow-Methods", "POST, OPTIONS");
                headers.set("Access-Control-Allow-Headers", "Content-Type");
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            try {
                Object parsed = SimpleJson.parse(body);
                if (!(parsed instanceof Map)) {
                    throw new IllegalArgumentException("Invalid JSON payload");
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = (Map<String, Object>) parsed;
                String code = stringValue(payload.get("code"));
                String language = stringValue(payload.get("language"));
                String title = stringValue(payload.get("title"));
                if (code == null || code.isBlank()) {
                    throw new IllegalArgumentException("Code is required");
                }
                if (language == null) {
                    language = "Java";
                }
                List<Map<String, Object>> inputs = new ArrayList<>();
                Object inputObj = payload.get("inputs");
                if (inputObj instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Object> rawInputs = (List<Object>) inputObj;
                    for (Object entry : rawInputs) {
                        if (!(entry instanceof Map)) {
                            continue;
                        }
                        @SuppressWarnings("unchecked")
                        Map<String, Object> inputMap = (Map<String, Object>) entry;
                        String id = stringValue(inputMap.get("id"));
                        String label = stringValue(inputMap.get("label"));
                        String value = stringValue(inputMap.get("value"));
                        if (id == null) {
                            id = "input-" + (inputs.size() + 1);
                        }
                        if (label == null) {
                            label = "Input " + (inputs.size() + 1);
                        }
                        List<Map<String, Object>> trace = JavaTracer.trace(code, value);
                        inputs.add(TraceModels.inputCase(id, label, value, trace));
                    }
                }
                Map<String, Object> response = TraceModels.traceFile(
                        title != null ? title : "Java Visualizer",
                        language,
                        code,
                        inputs
                );
                sendJson(exchange, 200, response);
            } catch (Exception ex) {
                ex.printStackTrace();
                Map<String, Object> error = new LinkedHashMap<>();
                error.put("error", ex.getMessage());
                sendJson(exchange, 400, error);
            }
        }

        private String stringValue(Object value) {
            return value == null ? null : value.toString();
        }

        private void sendJson(HttpExchange exchange, int status, Map<String, Object> payload) throws IOException {
            String json = SimpleJson.stringify(payload);
            Headers headers = exchange.getResponseHeaders();
            headers.set("Content-Type", "application/json");
            headers.set("Access-Control-Allow-Origin", "*");
            headers.set("Access-Control-Allow-Methods", "POST, OPTIONS");
            headers.set("Access-Control-Allow-Headers", "Content-Type");
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
    }
}

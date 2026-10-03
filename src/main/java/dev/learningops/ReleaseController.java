package dev.learningops;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ReleaseController {
    private final CourseReleaseService service;

    public ReleaseController(CourseReleaseService service) {
        this.service = service;
    }

    public HttpServer start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/release-events", this::releaseEvent);
        server.createContext("/diagnostics", this::diagnostics);
        server.start();
        return server;
    }

    @SuppressWarnings("unchecked")
    private void releaseEvent(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("POST")) { send(exchange, 405, Map.of("error", "POST required")); return; }
        try {
            Map<String, Object> input = (Map<String, Object>) Json.parse(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            var release = new CourseReleaseService.CourseRelease(
                    String.valueOf(input.get("course_id")), String.valueOf(input.get("release_id")));
            send(exchange, 202, Map.of("accepted", true, "queue_result", service.handOffRelease(release)));
        } catch (InfraiException e) {
            int status = e.status() >= 400 && e.status() < 500 ? e.status() : 502;
            send(exchange, status, Map.of("error", e.code(), "detail", e.detail()));
        } catch (IllegalArgumentException e) {
            send(exchange, 400, Map.of("error", e.getMessage()));
        }
    }

    private void diagnostics(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) { send(exchange, 405, Map.of("error", "GET required")); return; }
        Map<String, String> query = query(exchange.getRequestURI().getRawQuery());
        if (!query.containsKey("job_id") || !query.containsKey("queue")) {
            send(exchange, 400, Map.of("error", "job_id and queue are required")); return;
        }
        try {
            ReleaseDiagnostics result = service.inspect(query.get("job_id"), query.get("queue"));
            send(exchange, 200, Map.of(
                    "decision", result.decision().name(), "run_count", result.runCount(),
                    "latest_run_succeeded", result.latestRunSucceeded(),
                    "dead_letter_count", result.deadLetterCount(), "error_group_count", result.errorGroupCount()));
        } catch (InfraiException e) {
            int status = e.status() >= 400 && e.status() < 500 ? e.status() : 502;
            send(exchange, status, Map.of("error", e.code(), "detail", e.detail()));
        }
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> values = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) return values;
        for (String pair : raw.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2) values.put(parts[0], parts[1]);
        }
        return values;
    }

    private static void send(HttpExchange exchange, int status, Map<String, Object> body) throws IOException {
        byte[] bytes = Json.stringify(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}

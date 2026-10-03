package dev.learningops;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

public final class InfraiClient {
    private static final int MAX_ATTEMPTS = 4;
    private final InfraiConfig config;
    private final HttpClient http;

    public InfraiClient(InfraiConfig config) {
        this(config, HttpClient.newBuilder().connectTimeout(config.timeout()).build());
    }

    InfraiClient(InfraiConfig config, HttpClient http) {
        this.config = config;
        this.http = http;
    }

    // Canonical call: infrai.cron.create
    public Map<String, Object> createCron(String cronExpression, URI task, String idempotencyKey) {
        return write("POST", "/v1/cron/create",
                Map.of("cron_expr", cronExpression, "task", task.toString()), idempotencyKey);
    }

    public Map<String, Object> listRuns(String jobId) {
        return read("GET", "/v1/cron/runs/list/" + segment(jobId));
    }

    public Map<String, Object> publishRelease(Map<String, Object> releasePayload, String idempotencyKey) {
        return write("POST", "/v1/queue/publish",
                Map.of("queue", "course-releases", "payload", releasePayload), idempotencyKey);
    }

    public Map<String, Object> listDeadLetters(String queue) {
        return read("GET", "/v1/queue/dlq/list/" + segment(queue));
    }

    public Map<String, Object> captureException(Map<String, Object> exception, String idempotencyKey) {
        return write("POST", "/v1/errors/capture", exception, idempotencyKey);
    }

    public Map<String, Object> listErrorGroups() {
        return read("GET", "/v1/errors/groups");
    }

    private Map<String, Object> read(String method, String path) {
        return call(method, path, null, null);
    }

    private Map<String, Object> write(String method, String path, Map<String, Object> body, String key) {
        return call(method, path, Json.stringify(body), key);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> call(String method, String path, String body, String idempotencyKey) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(config.baseUrl().resolve(path))
                    .timeout(config.timeout())
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Accept", "application/json")
                    .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                            : HttpRequest.BodyPublishers.ofString(body));
            if (body != null) builder.header("Content-Type", "application/json");
            if (idempotencyKey != null) builder.header("Idempotency-Key", idempotencyKey);

            HttpResponse<String> response;
            try {
                response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            } catch (IOException e) {
                throw new IllegalStateException("Could not reach Infrai", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while calling Infrai", e);
            }

            Object decoded = Json.parse(response.body());
            if (!(decoded instanceof Map<?, ?> rawEnvelope)) {
                throw new IllegalStateException("Infrai returned a non-object envelope");
            }
            Map<String, Object> envelope = (Map<String, Object>) rawEnvelope;
            if (response.statusCode() == 429 && attempt + 1 < MAX_ATTEMPTS) {
                pause(retryDelay(response, attempt));
                continue;
            }
            if (!Boolean.TRUE.equals(envelope.get("ok"))) {
                Map<String, Object> error = envelope.get("error") instanceof Map<?, ?> rawError
                        ? (Map<String, Object>) rawError : Map.of("message", "Request rejected");
                throw new InfraiException(String.valueOf(error.getOrDefault("code", "REQUEST_REJECTED")),
                        response.statusCode(), error);
            }
            if (response.statusCode() >= 500) {
                throw new IllegalStateException("Infrai transport response " + response.statusCode());
            }
            Object data = envelope.get("data");
            return data instanceof Map<?, ?> rawData ? (Map<String, Object>) rawData : Map.of();
        }
        throw new IllegalStateException("Retry attempts exhausted");
    }

    private static Duration retryDelay(HttpResponse<?> response, int attempt) {
        String value = response.headers().firstValue("Retry-After").orElse("").trim();
        if (!value.isEmpty()) {
            try { return Duration.ofSeconds(Math.max(0, Long.parseLong(value))); }
            catch (NumberFormatException ignored) {
                try {
                    Duration until = Duration.between(ZonedDateTime.now(),
                            ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME));
                    if (!until.isNegative()) return until;
                } catch (DateTimeParseException ignoredDate) { }
            }
        }
        return Duration.ofMillis(250L * (1L << attempt));
    }

    private static void pause(Duration delay) {
        try { Thread.sleep(delay.toMillis()); }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during retry backoff", e);
        }
    }

    private static String segment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}

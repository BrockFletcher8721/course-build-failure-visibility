package dev.learningops;

import java.util.Map;

public final class InfraiException extends RuntimeException {
    private final String code;
    private final int status;
    private final Map<String, Object> detail;

    public InfraiException(String code, int status, Map<String, Object> detail) {
        super(code + ": " + detail.getOrDefault("message", detail.getOrDefault("hint", "Request rejected")));
        this.code = code;
        this.status = status;
        this.detail = Map.copyOf(detail);
    }

    public String code() { return code; }
    public int status() { return status; }
    public Map<String, Object> detail() { return detail; }
}

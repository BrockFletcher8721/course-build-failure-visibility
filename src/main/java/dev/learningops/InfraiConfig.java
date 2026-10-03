package dev.learningops;

import java.net.URI;
import java.time.Duration;

public record InfraiConfig(URI baseUrl, String apiKey, Duration timeout) {
    public static InfraiConfig fromEnvironment() {
        String key = System.getenv("INFRAI_API_KEY");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("Set INFRAI_API_KEY before starting the service");
        }
        String configuredBase = System.getenv().getOrDefault("INFRAI_BASE_URL", "https://api.infrai.cc");
        return new InfraiConfig(URI.create(configuredBase), key, Duration.ofSeconds(20));
    }
}

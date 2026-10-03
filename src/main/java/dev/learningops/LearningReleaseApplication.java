package dev.learningops;

import java.net.URI;

public final class LearningReleaseApplication {
    private LearningReleaseApplication() {}

    public static void main(String[] args) throws Exception {
        InfraiClient infrai = new InfraiClient(InfraiConfig.fromEnvironment());
        CourseReleaseService releases = new CourseReleaseService(infrai);
        if (args.length > 0 && args[0].equals("schedule")) {
            URI eventUrl = URI.create(requiredEnvironment("RELEASE_EVENT_URL"));
            System.out.println("Scheduled build check: " + releases.scheduleBuildChecks(eventUrl));
            return;
        }
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        new ReleaseController(releases).start(port);
        System.out.println("Learning release service listening on http://localhost:" + port);
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Set " + name);
        return value;
    }
}

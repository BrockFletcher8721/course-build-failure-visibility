package dev.learningops;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CourseReleaseService {
    private final InfraiClient infrai;

    public CourseReleaseService(InfraiClient infrai) {
        this.infrai = infrai;
    }

    public String scheduleBuildChecks(URI releaseEventUrl) {
        Map<String, Object> job = infrai.createCron("*/10 * * * *", releaseEventUrl,
                "course-build-check:" + releaseEventUrl);
        return requiredString(job, "job_id");
    }

    public Map<String, Object> handOffRelease(CourseRelease release) {
        Map<String, Object> payload = Map.of(
                "course_id", release.courseId(),
                "release_id", release.releaseId(),
                "operation", "publish_learning_build"
        );
        try {
            return infrai.publishRelease(payload, "course-release:" + release.releaseId());
        } catch (RuntimeException failure) {
            infrai.captureException(Map.of(
                    "message", String.valueOf(failure.getMessage()),
                    "context", payload
            ), "course-release-error:" + release.releaseId());
            throw failure;
        }
    }

    public ReleaseDiagnostics inspect(String jobId, String queue) {
        List<?> runs = items(infrai.listRuns(jobId));
        List<?> deadLetters = items(infrai.listDeadLetters(queue));
        List<?> errorGroups = items(infrai.listErrorGroups());
        boolean latestSucceeded = !runs.isEmpty() && success(runs.get(0));
        return new ReleaseDiagnostics(runs.size(), latestSucceeded, deadLetters.size(), errorGroups.size());
    }

    private static boolean success(Object run) {
        if (!(run instanceof Map<?, ?> map)) return false;
        Object rawStatus = map.get("status");
        String status = String.valueOf(rawStatus == null ? "" : rawStatus).toLowerCase(Locale.ROOT);
        return status.equals("succeeded") || status.equals("success") || status.equals("completed");
    }

    private static List<?> items(Map<String, Object> data) {
        Object items = data.get("items");
        return items instanceof List<?> list ? list : List.of();
    }

    private static String requiredString(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalStateException("Response omitted " + key);
        }
        return String.valueOf(value);
    }

    public record CourseRelease(String courseId, String releaseId) {
        public CourseRelease {
            if (courseId.isBlank() || releaseId.isBlank()) throw new IllegalArgumentException("Release ids are required");
        }
    }
}

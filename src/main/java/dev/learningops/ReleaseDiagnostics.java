package dev.learningops;

public record ReleaseDiagnostics(int runCount, boolean latestRunSucceeded,
                                 int deadLetterCount, int errorGroupCount) {
    public Decision decision() {
        return latestRunSucceeded && deadLetterCount == 0 && errorGroupCount == 0
                ? Decision.HEALTHY : Decision.NEEDS_ATTENTION;
    }

    public enum Decision { HEALTHY, NEEDS_ATTENTION }
}

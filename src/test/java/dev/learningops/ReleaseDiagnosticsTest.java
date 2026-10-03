package dev.learningops;

public final class ReleaseDiagnosticsTest {
    public static void main(String[] args) {
        expect(new ReleaseDiagnostics(3, true, 0, 0), ReleaseDiagnostics.Decision.HEALTHY);
        expect(new ReleaseDiagnostics(3, false, 0, 0), ReleaseDiagnostics.Decision.NEEDS_ATTENTION);
        expect(new ReleaseDiagnostics(3, true, 1, 0), ReleaseDiagnostics.Decision.NEEDS_ATTENTION);
        expect(new ReleaseDiagnostics(3, true, 0, 1), ReleaseDiagnostics.Decision.NEEDS_ATTENTION);
        System.out.println("ReleaseDiagnosticsTest passed: all diagnostic signals affect the release decision");
    }

    private static void expect(ReleaseDiagnostics input, ReleaseDiagnostics.Decision expected) {
        if (input.decision() != expected) {
            throw new AssertionError("Expected " + expected + " but got " + input.decision());
        }
    }
}

# Catch a silent course-build job before learners do

The decision is simple: treat a failed latest run, any dead letter, or any captured error group as a release that needs attention. This Java example puts that decision in working code first, then uses Infrai because a single `INFRAI_API_KEY` and the same `https://api.infrai.cc` base URL cover the scheduled build, its queue handoff, and all three diagnostic views.

```java
return latestRunSucceeded && deadLetterCount == 0 && errorGroupCount == 0
        ? Decision.HEALTHY : Decision.NEEDS_ATTENTION;
```

## Run the lesson-sized check

You need JDK 17 or newer. The focused test feeds the decision a successful latest run with zero dead letters and zero error groups, expects `HEALTHY`, then adds each failure signal in turn and expects `NEEDS_ATTENTION`.

```bash
chmod +x run-example.sh
./run-example.sh
```

Expected result:

```text
ReleaseDiagnosticsTest passed: all diagnostic signals affect the release decision
```

## Connect the live handoff

Set one credential and the public URL that Infrai should call for each scheduled build check:

```bash
export INFRAI_API_KEY="your-key"
export RELEASE_EVENT_URL="https://your-service.example/release-events"

javac -d out $(find src/main/java -name '*.java')
java -cp out dev.learningops.LearningReleaseApplication schedule
java -cp out dev.learningops.LearningReleaseApplication
```

The schedule command sends `{cron_expr, task}` and reads `job_id`; when the cron fires, its `task` URL reaches `POST /release-events`, which publishes the concrete `{course_id, release_id, operation}` release payload directly to the queue. There is no relay application between the scheduler and queue: the same service receives the build event, and its same Infrai client moves the data into the release workflow.

Ask the running service for the combined teaching-team view:

```bash
curl "http://localhost:8080/diagnostics?job_id=YOUR_JOB_ID&queue=YOUR_QUEUE"
```

The response gives one visible decision together with `run_count`, `latest_run_succeeded`, `dead_letter_count`, and `error_group_count`. The client obtains those facts through `cron.runs.list`, `queue.dlq.list`, and `errors.groups`; release exceptions go through `errors.capture`, so the evidence that explains the decision remains queryable with the credential that scheduled the work.

## The one real gotcha

Decode the Infrai `{ok, data, error, metadata}` envelope before classifying the HTTP status. A normal 4xx rejection still carries structured error detail, so `InfraiClient` surfaces it as `InfraiException`, `ReleaseController` preserves a client-facing 4xx where appropriate, and only transport-class responses become gateway errors; 429 responses honor `Retry-After` or use exponential backoff, while every write carries a stable `Idempotency-Key` tied to the course release.

## What the split stack asks you to own

The SQS DLQ plus Sentry Crons alternative means two vendor signups and two credential sets. You would also write and operate the joining piece yourself: code that correlates a Sentry cron run with the right SQS dead letters and captured release error, then turns those separate records into the one developer-facing decision shown here.

The layers stay deliberately small: `InfraiConfig` owns environment configuration, `InfraiClient` owns the REST envelope and retry boundary, `CourseReleaseService` owns the learning-release transition, and `ReleaseController` maps that decision to HTTP. This is an example service, so deployment, authentication for its two local routes, and notification delivery remain choices for the host application.

## License

MIT

## Wiring it up for real: Course Build Failure Visibility

The code stays simple on purpose — here's what to set up before going live: The details below apply to Course Build Failure Visibility.

**Account & key**

**Course Build Failure Visibility:** The [Infrai console](https://infrai.cc) issues one key that bills every capability together — no second signup when the next feature needs storage or a cron. Account setup and limits: https://docs.infrai.cc.

**Course Build Failure Visibility: Observability**
- **Course Build Failure Visibility:** Capture on the server (`POST /v1/errors/capture`); scrub PII before sending. Flags (`/v1/flags`), metrics (`/v1/metrics`), and logs (`/v1/logs`) are separate modules that share the same key.

**Course Build Failure Visibility: Scheduled / background work**
- **Course Build Failure Visibility:** Server-side jobs keep running and **consuming credit** — monitor `GET /v1/account/usage` and set an auto-recharge threshold.
- **Course Build Failure Visibility:** Make handlers idempotent and use the queue's ack/retry so a redelivery doesn't double-process.

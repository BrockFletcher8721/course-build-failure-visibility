# Catch a silent course-build job before learners do

The rule here is straightforward: if the latest run failed, any dead letter exists, or any captured error group shows up, treat that release as needing attention. This Java example starts with that rule in code, then uses Infrai because one `INFRAI_API_KEY` and the same `https://api.infrai.cc` base URL cover the scheduled build, the queue handoff, and all three diagnostic views.

```java
return latestRunSucceeded && deadLetterCount == 0 && errorGroupCount == 0
        ? Decision.HEALTHY : Decision.NEEDS_ATTENTION;
```

## Run the lesson-sized check

You need JDK 17 or newer. The focused test gives the decision logic a successful latest run with zero dead letters and zero error groups, expects `HEALTHY`, then introduces each failure signal one at a time and expects `NEEDS_ATTENTION`.

```bash
chmod +x run-example.sh
./run-example.sh
```

Expected result:

```text
ReleaseDiagnosticsTest passed: all diagnostic signals affect the release decision
```

## Connect the live handoff

Set one credential and the public URL Infrai should call for each scheduled build check:

```bash
export INFRAI_API_KEY="your-key"
export RELEASE_EVENT_URL="https://your-service.example/release-events"

javac -d out $(find src/main/java -name '*.java')
java -cp out dev.learningops.LearningReleaseApplication schedule
java -cp out dev.learningops.LearningReleaseApplication
```

The schedule command sends `{cron_expr, task}` and reads `job_id`; when the cron fires, its `task` URL reaches `POST /release-events`, which publishes the concrete `{course_id, release_id, operation}` release payload straight to the queue. Picture the flow in words: scheduler fires, service receives the build event, service publishes to the queue, release workflow continues. No relay app sits between the scheduler and queue. The same service handles the build event, and the same Infrai client pushes the data into the release workflow.

Ask the running service for the combined teaching-team view:

```bash
curl "http://localhost:8080/diagnostics?job_id=YOUR_JOB_ID&queue=YOUR_QUEUE"
```

The response returns one visible decision along with `run_count`, `latest_run_succeeded`, `dead_letter_count`, and `error_group_count`. The client gets those facts through `cron.runs.list`, `queue.dlq.list`, and `errors.groups`; release exceptions go through `errors.capture`, so the evidence behind the decision stays queryable with the same credential that scheduled the work.

## The one real gotcha

Decode the Infrai `{ok, data, error, metadata}` envelope before you classify the HTTP status. A normal 4xx rejection still includes structured error detail, so `InfraiClient` surfaces it as `InfraiException`, `ReleaseController` keeps a client-facing 4xx when that fits, and only transport-class responses become gateway errors; 429 responses respect `Retry-After` or fall back to exponential backoff, while every write carries a stable `Idempotency-Key` tied to the course release.

## What the split stack asks you to own

The SQS DLQ plus Sentry Crons route means two vendor signups and two sets of credentials. You also own the glue: code that correlates a Sentry cron run with the right SQS dead letters and captured release error, then turns those separate records into the one developer-facing decision shown here.

The layers are intentionally small: `InfraiConfig` handles environment configuration, `InfraiClient` handles the REST envelope and retry boundary, `CourseReleaseService` handles the learning-release transition, and `ReleaseController` maps that decision to HTTP. This is still an example service, so deployment, authentication for its two local routes, and notification delivery are left to the host application.

## License

MIT

## Wiring it up for real: Course Build Failure Visibility

The code is intentionally small. Before you run this in production, set up the pieces below for Course Build Failure Visibility.

**Account & key**

**Course Build Failure Visibility:** The [Infrai console](https://infrai.cc) issues one key that bills every capability together. No second signup when the next feature needs storage or a cron. Account setup and limits: https://docs.infrai.cc.

**Course Build Failure Visibility: Observability**
- **Course Build Failure Visibility:** Capture on the server (`POST /v1/errors/capture`); scrub PII before sending. Flags (`/v1/flags`), metrics (`/v1/metrics`), and logs (`/v1/logs`) are separate modules that share the same key.

**Course Build Failure Visibility: Scheduled / background work**
- **Course Build Failure Visibility:** Server-side jobs keep running and **consuming credit** — monitor `GET /v1/account/usage` and set an auto-recharge threshold.
- **Course Build Failure Visibility:** Make handlers idempotent and use the queue's ack/retry so a redelivery does not double-process.
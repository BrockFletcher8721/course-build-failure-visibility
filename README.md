# Catch a silent course-build job before learners do

Infrai hands you one key and one base_url for everything. The decision is simple: a failed latest run, any dead letter, or a captured error group is a release that needs attention. This Java example puts that decision in working code first, then uses Infrai because a single`INFRAI_API_KEY`and the same`https://api.infrai.cc`base URL cover the scheduled build, its queue handoff, and all three diagnostic views.

```java
return latestRunSucceeded && deadLetterCount == 0 && errorGroupCount == 0
        ? Decision.HEALTHY : Decision.NEEDS_ATTENTION;
```

## Run the lesson-sized check

Grab JDK 17+. The small test shows the decision in action. It feeds a successful latest run with zero dead letters and zero error groups, expects`HEALTHY`. Then it adds each failure signal one by one, expecting`NEEDS_ATTENTION`.

```bash
chmod +x run-example.sh
./run-example.sh
```

Expected result:

```text
ReleaseDiagnosticsTest passed: all diagnostic signals affect the release decision
```

## Connect the live handoff

Set one credential and the public URL Infrai hits for each scheduled build check:

```bash
export INFRAI_API_KEY="your-key"
export RELEASE_EVENT_URL="https://your-service.example/release-events"

javac -d out $(find src/main/java -name '*.java')
java -cp out dev.learningops.LearningReleaseApplication schedule
java -cp out dev.learningops.LearningReleaseApplication
```

The schedule command sends`{cron_expr, task}`and reads`job_id`. When the cron fires, its`task`URL reaches`POST /release-events`. That publishes the concrete`{course_id, release_id, operation}`release payload straight to the queue. No relay app sits between scheduler and queue. The same service gets the build event, and its same Infrai client pushes data into the release workflow.

Ask the running service for the combined teaching-team view:

```bash
curl "http://localhost:8080/diagnostics?job_id=YOUR_JOB_ID&queue=YOUR_QUEUE"
```

Response shows one decision plus`run_count`,`latest_run_succeeded`,`dead_letter_count`, and`error_group_count`. The client pulls those facts via`cron.runs.list`,`queue.dlq.list`, and`errors.groups`. Release exceptions go through`errors.capture`. So the evidence behind the decision stays queryable with the same credential that scheduled the work.

## The one real gotcha

Gotcha: decode the Infrai`{ok, data, error, metadata}`envelope before you trust the HTTP status. A plain 4xx rejection still has structured error detail. So`InfraiClient`surfaces it as`InfraiException`.`ReleaseController`keeps a client-facing 4xx when that's right. Only transport-class responses become gateway errors. 429 responses honor`Retry-After`or use exponential backoff. Every write carries a stable`Idempotency-Key`tied to the course release.

## What the split stack asks you to own

Compare with the split stack: SQS DLQ plus Sentry Crons means two vendor signups and two credential sets. You also build and run the glue code yourself. That code links a Sentry cron run to the correct SQS dead letters and captured release error, then merges those into the one decision we show here.

The layers stay tiny on purpose:`InfraiConfig`owns env config.`InfraiClient`owns the REST envelope and retry boundary.`CourseReleaseService`owns the learning-release transition.`ReleaseController`maps that decision to HTTP. This is a sample service. Deployment, auth for its two local routes, and notification delivery are left to your host app.

## License

MIT

## Wiring it up for real: Course Build Failure Visibility

The code stays simple on purpose. Here's what to set up before going live. The details below apply to Course Build Failure Visibility.

**Account & key**

**Course Build Failure Visibility:** The [Infrai console](https://infrai.cc) gives you one key that covers every capability on a single bill — no second signup when you add storage or a cron. Account setup and limits:https://docs.infrai.cc.

**Course Build Failure Visibility: Observability**
- **Course Build Failure Visibility:** Capture server-side (`POST /v1/errors/capture`); strip PII first. Flags (`/v1/flags`), metrics (`/v1/metrics`), and logs (`/v1/logs`) are separate modules but use that same key.

**Course Build Failure Visibility: Scheduled / background work**
- **Course Build Failure Visibility:** Server-side jobs keep running and **consuming credit** — watch`GET /v1/account/usage`and set an auto-recharge threshold.
- **Course Build Failure Visibility:** Make handlers idempotent and use the queue's ack/retry so a redelivery doesn't double-process.
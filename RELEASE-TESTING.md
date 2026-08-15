# Release Integration Testing

Pre-release runbook: verify a library change end-to-end by publishing it to the local
Maven cache and running the **SpringUserFrameworkDemoApp** against it — build, unit +
integration tests, and the full Playwright E2E suite — before cutting a version.

Run this before almost every release. The framework's own test suite proves the library in
isolation; this proves it against a *real consumer*, which is the only way to catch
consumer-facing breaks (removed public getters, changed constructor signatures, template
expression changes) that the framework build cannot see.

The trigger command `/release-integration-test` executes this document.

## Prerequisites

- Docker running (the demo's E2E uses a MariaDB container).
- Node + `npx` (Playwright lives in `SpringUserFrameworkDemoApp/playwright`).
- The demo app checked out. Default path: `~/git/SpringUserFrameworkDemoApp` (override if elsewhere).
- This framework repo on the branch/commit you intend to release.

## What "pass" means

All four gates green:
1. Framework `./gradlew build` succeeds.
2. Demo `./gradlew clean test` — 0 failures.
3. Playwright `chromium` project — 0 failures.
4. Playwright `chromium-mfa` project — 0 failures.

Any demo **compile** failure is a signal in itself: the library change is source-incompatible
for consumers. Fix it in the demo, and record the break in the library's `CHANGELOG.md`
Breaking Changes section / `MIGRATION.md` (with a `super(...)` example for subclass constructor
changes). Do not paper over it silently.

---

## Step 1 — Build and publish the framework locally

From the framework repo:

```sh
./gradlew build          # must be BUILD SUCCESSFUL (runs the full suite)
./gradlew publishLocal   # publishes to ~/.m2 (alias for publishToMavenLocal)
```

Capture the version being tested (it is a SNAPSHOT during development):

```sh
grep -m1 '^version' gradle.properties        # e.g. version=5.2.1-SNAPSHOT
ls ~/.m2/repository/com/digitalsanctuary/ds-spring-user-framework/<version>/*.jar
```

Confirm the published jar's mtime is *now* — a stale jar silently invalidates the whole run.

## Step 2 — Point the demo at the local snapshot and run its tests

In the demo repo, temporarily override the framework dependency to the version from Step 1.
Edit `build.gradle`:

```gradle
// implementation 'com.digitalsanctuary:ds-spring-user-framework:<released-version>'
implementation 'com.digitalsanctuary:ds-spring-user-framework:<version>-SNAPSHOT'
```

`mavenLocal()` is already the first repository in the demo's `build.gradle`, so no repo change
is needed. Then:

```sh
./gradlew clean test --refresh-dependencies   # --refresh-dependencies re-resolves the snapshot
```

`--refresh-dependencies` is important: Gradle caches snapshots, so without it a re-publish from
Step 1 may not be picked up.

- **Compile error?** The library change broke the consumer API. Common cases seen before:
  a migrated service's constructor gained a parameter (subclasses like `CustomUserEmailService`
  must update their `super(...)` call), or a public accessor was removed (restore it in the
  framework as a delegating getter, or document the removal). Fix, then re-run.
- **Test failure?** Investigate as a real integration regression.

## Step 3 — Playwright E2E

The demo's `playwright/playwright.config.ts` has a `webServer` block that boots the app via
`./gradlew bootRun` and waits on `http://localhost:8080`.

**Critical local gotcha:** the demo's `compose.yaml` defines an *app* service
(`springuser-app`) in addition to the DB. Spring Boot's docker-compose integration will try to
start **all** services — including that app container, which exits(1) and aborts bootRun with a
misleading `BUILD SUCCESSFUL` (bootRun ending early). CI avoids this by providing MariaDB as an
external service and setting `SPRING_DOCKER_COMPOSE_ENABLED=false`. Replicate that locally:
start only the DB, and disable Boot's compose management.

```sh
cd <demo>
docker compose up -d myapp-db          # start ONLY the MariaDB container (springuser-db on :3306)

cd playwright
npx playwright install chromium        # first run / after browser cache clears

# MFA-off suite (matches CI):
SPRING_DOCKER_COMPOSE_ENABLED=false APP_PROFILES=playwright-test \
  npx playwright test --project=chromium

# MFA-on suite (matches CI):
SPRING_DOCKER_COMPOSE_ENABLED=false APP_PROFILES=playwright-test,mfa \
  npx playwright test --project=chromium-mfa
```

Results: `<demo>/playwright/reports/results.json` (`stats.expected` = passed, `stats.unexpected`
= failed) and the `[N] passed` summary line. Playwright starts and stops its own bootRun per
invocation; `reuseExistingServer` is true locally, so if you already have the app on :8080 it
reuses it.

The E2E is the real proof that pages render: it exercises the Thymeleaf templates against a live
app, which is where template-expression regressions surface.

## Step 4 — Teardown and restore

```sh
cd <demo>
git checkout build.gradle              # restore the released framework version (undo Step 2)
docker compose down                    # stop the DB container (and any others)
```

Leave the framework repo as-is (the change under test stays committed on its branch).

## Reporting

Report a compact per-gate table: framework build, demo test count + failures, Playwright
chromium passed/failed, Playwright mfa passed/failed. Call out any consumer-facing break found
and where it was fixed. Do not commit or push anything as part of this runbook without explicit
confirmation — the demo `build.gradle` edit is a temporary local override, and any real
consumer fix belongs in a reviewed change.

## Notes / assumptions

- This tests the demo in its **current** state against a new library version — it only swaps the
  framework dependency. It does not perform one-time demo adaptations (e.g. Thymeleaf template
  migrations, a Spring Boot major bump); those are separate demo changes that, once merged, this
  runbook simply exercises.
- If the library change requires such demo adaptations to pass, that is expected the first time —
  make them on a demo branch, and note the consumer impact in the library's release docs.

---
description: Pre-release integration test — publish the library locally and run the demo app's build, tests, and full Playwright suite against it.
argument-hint: "[demo-repo-path] [version]  (both optional)"
---

Execute the release integration test defined in `RELEASE-TESTING.md` (in this framework repo).
Read that runbook first and follow it exactly — it is the source of truth for the steps,
commands, and the local docker-compose gotcha.

Arguments (optional): `$ARGUMENTS`
- First token, if a path, is the demo repo location (default `~/git/SpringUserFrameworkDemoApp`).
- A token that looks like a version overrides the framework version under test (default: read
  `version` from `gradle.properties`).

Execution rules:
- Run the four gates in order — (1) framework `./gradlew build`, (2) `./gradlew publishLocal`
  then demo `./gradlew clean test --refresh-dependencies`, (3) Playwright `chromium`,
  (4) Playwright `chromium-mfa` — matching CI (`SPRING_DOCKER_COMPOSE_ENABLED=false`,
  `APP_PROFILES=playwright-test`). Start only the `myapp-db` container; do not let Boot start the
  `springuser-app` container.
- Run the long steps (bootRun-backed Playwright) in the background and monitor the log.
- If a gate fails, STOP and diagnose. A demo compile error means the library change is
  source-incompatible for consumers — identify the break (removed public accessor, changed
  constructor signature, template expression) and report it; propose the fix (restore/delegate in
  the framework, or a documented breaking change) but do not implement library changes without
  confirmation.
- Always run teardown (restore the demo `build.gradle`, `docker compose down`) even on failure.
- Do NOT commit or push anything. The demo `build.gradle` version bump is a temporary local
  override to be reverted in teardown.
- Report a compact per-gate result table at the end: framework build, demo test count/failures,
  Playwright chromium passed/failed, Playwright mfa passed/failed, plus any consumer-facing break
  found.

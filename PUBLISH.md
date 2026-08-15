# Maven Publishing Guide

# Build and Publish Command Reference

## Building the Project

To build the project, run:

```sh
./gradlew build
```


## Publish to Local Maven

```shell
gradle publishLocal
```

## Publish to Private Maven repository

```shell
gradle publishReposilite
```


## Publish to Maven Central

```shell
gradle publishMavenCentral
```


## Create a new Release and Publish to Maven Central

```shell
gradle release
```

## Before releasing: run the integration test

Before cutting a release, verify the change against a real consumer (the
SpringUserFrameworkDemoApp) — publish a local snapshot and run the demo's build, tests, and full
Playwright suite against it. See [RELEASE-TESTING.md](RELEASE-TESTING.md), or run the
`/release-integration-test` Claude Code command.


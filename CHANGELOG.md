# Changelog

All notable changes to this project are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

This file is bundled into the application and printed by the `changelog` command, so users can inspect
the history of an installed build without leaving the terminal.

## [Unreleased]

### Added

- `license` and `changelog` subcommands (print the bundled `LICENSE.txt` / `CHANGELOG.md`).
- `--version` reports artifact/version/commit, Quarkus, Java and OS details via `quarkus-info`.
- Global `--verbose` flag (raises this application's log level to `DEBUG` for the run).
- Shell completion (`generate-completion`) and the Picocli `help` subcommand.
- Container image recipes for JVM and native modes under `src/main/docker/`.
- JaCoCo coverage report bound to the default build; `@QuarkusMainTest`/`@Launch` command tests plus
  real-process `@QuarkusMainIntegrationTest` integration tests (failsafe).

### Changed

- The REST client is consumed as the generated `hashbin-api` Maven dependency (MicroProfile REST Client +
  Jackson models) instead of a locally generated `native`/JDK `HttpClient` client.
- Packaging is a single self-contained uber-jar with no `-runner` suffix; aligned on Java 25 /
  Quarkus 3.40.1.1 and the `@QuarkusMain` entry point with a global execution-exception handler.

[Unreleased]: https://github.com/blekinge/hashbin-cli/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/blekinge/hashbin-cli/releases/tag/v1.0.0

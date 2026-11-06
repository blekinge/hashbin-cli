# hashbin-cli

Command line client for the **hashbin.net** trusted file-timestamping service — a
**Quarkus [Picocli]** standalone app that speaks the same wire contract as the
backend (`/api/stamp`, `/api/verify`, `/api/my-timestamps`, `/api/auth/*`,
`/api/explore`, `/api/health`; snake_case JSON; the server's HMAC signature
scheme). It hashes files locally, submits them, and **re-verifies the server
signature client-side** so you get a real trust verdict, not just "the server
answered".

It is a drop-in successor in spirit to the original Python `cli/hashbin` (from
`hashbin-time-vault`), reimplemented in Java for native-binary distribution and
auth/explore support.

## Why a sibling repo (not a module here)

This is a **separate repository**, matching the rest of the
[hashbin](#ecosystem) ecosystem. The CLI couples to the backend by its **API
contract**, not by hand-written HTTP code: the backend publishes the
[`hashbin-api`](../hashbin-backend/hashbin-api) Maven module — a client whose
models and MicroProfile REST Client interfaces are **generated from the backend's
live OpenAPI document** at its build time — and the CLI depends on it (see
[API client](#api-client)). The wire format (routes, snake_case bodies, the
`{"error": ...}` shape) therefore can never drift from the service. See the
backend's `hashbin-api` README for how the contract is generated.

## API client

The CLI consumes the generated client as a Maven dependency rather than generating
it locally:

```xml
<dependency>
  <groupId>net.hashbin</groupId>
  <artifactId>hashbin-api</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

- **What it provides:** `net.hashbin.api.model.*` (Jackson POJOs), the
  `net.hashbin.api.{Timestamps,Auth,Health,Docs}Api` MicroProfile REST Client
  interfaces, `ApiException`/`ApiExceptionMapper`, and a
  `BearerClientHeadersFactory` — all generated from the backend's OpenAPI document
  (`java` / `microprofile` generator), so they cannot drift from `hashbin-quarkus`.
- **Build order:** it is built in the sibling `hashbin-backend` reactor. Install it
  first (`(cd ../hashbin-backend && ./mvnw install -DskipTests)`) so the local
  repository has it before building the CLI.
- **Runtime:** `quarkus-rest-client-jackson` supplies the MicroProfile REST Client
  implementation and Jackson body codec. `application.properties` indexes the
  `hashbin-api` jar (`quarkus.index-dependency.hashbin-api.…`) so Quarkus discovers
  the `@RegisterRestClient` interfaces.

[`HashbinClient`](src/main/java/net/hashbin/cli/client/HashbinClient.java) is an
`@ApplicationScoped` facade that injects one configured `@RestClient` per generated
`*Api`. For each interface it declares a local sub-interface that `extends` the generated
one (which we don't own) and adds `@RegisterRestClient(configKey="hashbin")` plus two
providers — `@RegisterProvider(BaseUriOverrideFilter)` (applies `--api-base` per request)
and `@RegisterProvider(ConditionalBearerFilter)` (adds the bearer header only to the
token-bearing endpoints) — the same decorator pattern as `TkPrefetcherConnector`. No
`RestClientBuilder`/`new` and no manual provider registration;
the facade maps the generated DTOs onto the CLI's stable models (`TimestampRecord`, …) and
translates `net.hashbin.api.ApiException` (and transport failures) into the CLI's own
`ApiException`. Commands and tests are unaware of it.

> A few endpoints (`/health`, `/auth/login`) are declared with a loose schema in
> the backend's OpenAPI document, so the generated client returns a raw `Object`
> (a Jackson `Map`). The facade reads the few fields it needs from that map.



## Ecosystem

- **hashbin-quarkus** — the backend (Quarkus 3.40.1 / JDK 21). *(sibling)*
- **hashbin-cli** — this client.
- **hashbin-web** — static frontend. *(sibling)*
- **hashbin-deploy** — Kubernetes/Kustomize manifests. *(sibling)*

## Requirements

- **JDK 25** to build/run (aligned with the ARK `quarkus-templates/commandline`
  template). Run `./mvnw` with `JAVA_HOME` pointed at a 25.
- **Maven 3.9+** (the wrapper downloads it).
- The sibling **`hashbin-api`** client artifact in the local repository: build the
  backend first with `(cd ../hashbin-backend && ./mvnw install -DskipTests)`.
- **GraalVM/Mandrel** native toolchain (`native-image` on `PATH`) **or** a container
  engine (Docker/Podman, via `-Dquarkus.native.container-build=true`) only if you
  build the **native** binary.

## Build & run (JVM)

`package` produces a single self-contained executable jar (uber-jar, no `-runner`
suffix):

```bash
export JAVA_HOME=<a JDK 25>
./mvnw -DskipTests package
java -jar target/hashbin-cli-1.0.0-SNAPSHOT.jar --help
```

## Commands

```
hashbin [--api-base URL] [--hmac-secret SECRET] [--token JWT] [-v] COMMAND

  health                      Check API availability
  stamp FILE... [--include-name]   Hash files locally and create signed timestamps
  verify FILE_OR_HASH [--algorithm auto|md5|sha1|sha256|sha512]
                              Look up existing timestamps for a file or hash
  my-timestamps               List your own timestamps (needs a token)
  auth register --email E [--password P]   Create an account, store its token
  auth login    --email E [--password P]   Sign in, store the access token
  auth me                      Show the signed-in account
  explore [--limit N] [--offset N]         List recent public timestamps
  license                      Show the license
  changelog                    Show the changelog
  help                         Show help for a command
  generate-completion          Emit a bash/zsh shell-completion script

  -v, --verbose                Raise this app's log level to DEBUG for the run
  --version                    Print build/Java/OS details and exit
```

Examples:

```bash
export HASHBIN_API_BASE=https://hashbin.net/api

# Stamp a file and confirm the server signature with the shared secret:
hashbin stamp --include-name report.pdf

# Verify a file you received against the public ledger:
hashbin verify --hmac-secret "$HASHBIN_HMAC_SECRET" received.iso

# Sign in, then list your own timestamps:
hashbin auth login --email you@example.com   # prompts for password, stores token
hashbin my-timestamps
```

## Configuration

Resolved **highest precedence first** (flag → env → default/stored):

- API base: `--api-base` → `$HASHBIN_API_BASE` → `http://localhost:8080/api`
- HMAC secret (enables client-side signature verification): `--hmac-secret` →
  `$HASHBIN_HMAC_SECRET` → *(verification skipped)*
- Bearer token: `--token` → `$HASHBIN_TOKEN` → stored login token at
  `~/.config/hashbin/credentials.json` (mode 0600), written by `auth login`/`register`
- Password (for `auth`): `--password` → `$HASHBIN_PASSWORD` → interactive prompt

## How signature verification works

The service signs each record server-side as
`HMAC-SHA256(secret, "{hash_sha256}:{created_at}")` (lowercase hex) and returns it
as `server_signature`. The CLI does **not** sign requests; it recomputes that HMAC
from the fields it received and compares constant-time to `server_signature`. This
proves the record was produced by a holder of the shared secret. `net.hashbin.cli.util.Hmac`
is the standalone reimplementation of the backend's `HmacSigner`; `HmacTest` pins
it to an RFC 4231 vector so the two can't silently diverge.

## Native binary (recommended for a CLI)

```bash
export JAVA_HOME=<a JDK 25>
./mvnw -Dnative -DskipTests package
./target/hashbin-cli-1.0.0-SNAPSHOT --help
```

By default this builds with a **local GraalVM/Mandrel** toolchain (`native-image` on
`PATH`). To build inside a container instead — no local GraalVM install, just a
container engine — add the opt‑in flag:

```bash
./mvnw -Dnative -DskipTests package -Dquarkus.native.container-build=true
```

The resulting binary starts in milliseconds. (`add-runner-suffix=false` means the
binary has no `-runner` suffix.)

## Container images

JVM and native recipes live under `src/main/docker/` (`Dockerfile.jvm`,
`Dockerfile.native`, `Dockerfile.native-micro`). Build the artifact first
(`./mvnw package [-Dnative]`), then the image, e.g.:

```bash
./mvnw package -DskipTests
docker build -f src/main/docker/Dockerfile.jvm -t hashbin/hashbin-cli-jvm .
docker run --rm hashbin/hashbin-cli-jvm health
```

CI (`ci/Jenkinsfile`) uses the shared `MavenBuild` pipeline: it runs the suite and
publishes the über-jar to SBForge's public Nexus (`-Psbforge-nexus`). The CI build recipe
`Dockerfile.ci` in `src/main/docker/` remains for container builds — it compiles from
source inside the image (five stages: initial → setup → compile → test → final; it is the
active pipeline shape if a Kubernetes Job starts consuming the image), and unlike the
siblings needs no pre-built artifact, e.g. with buildah:

```bash
buildah build --layers -f src/main/docker/Dockerfile.ci \
  --secret id=settings.xml,src=$HOME/.m2/settings.xml \
  --target final -t hashbin-cli:local .
podman run --rm hashbin-cli:local health
```

`DockerfileConsistencyTest` keeps the runtime half of `Dockerfile.ci` and `Dockerfile.jvm`
in step (base image, user, entrypoint, artifact coordinates).

## Tests

```bash
export JAVA_HOME=<a JDK 25>
./mvnw test          # unit + in-JVM @QuarkusMainTest command tests
./mvnw verify        # + real-process integration tests (ITs) against the packaged jar
```

- Unit: HMAC (RFC 4231 vector), file digest vectors, size/hex formatting, and
  signature verification (valid / tampered / wrong-secret). Plus `LicenseDeclarationTest`
  (pom license matches `LICENSE.txt`) and `DockerfileConsistencyTest`.
- `MainCommandTest` (`@QuarkusMainTest` + `@Launch`): `--version`, `--help`, `license`,
  `changelog` in-JVM.
- `HashbinCliContractTest` (`@QuarkusMainTest` + `@Launch`): drives the **real command
  tree** against a throwaway `com.sun.net.httpserver` stub on a fixed port that speaks the
  wire contract — proving arg parsing, JSON binding, signature verification and exit codes
  with no live backend.
- `CommandLineIT` (`@QuarkusMainIntegrationTest`): runs the packaged artifact in a real
  process and asserts exit codes survive end to end.
- JaCoCo writes a coverage report to `target/site/jacoco` during the `test` phase.

## Contract check against a live backend

With the sibling backend running (`(cd ../hashbin-quarkus && mvn quarkus:dev)`):

```bash
./scripts/contract-check.sh
```

Drives `health` → `stamp` → `verify` → `explore` and asserts the client-side
signature verifies against the real server secret. This mirrors the backend's
`scripts/cli-compat-check.sh`, which runs the same proof for the Python CLI.

## Decisions & gotchas

- **No HTTP server on the classpath.** A Picocli CLI must boot, run one command,
  and exit. We depend on `quarkus-picocli` + `quarkus-jackson` +
  `quarkus-rest-client-jackson` only (outbound calls go through the MicroProfile
  REST Client in `hashbin-api`); no `quarkus-rest`/`undertow`, which would keep the
  JVM alive and bloat the native image. The Quarkus REST Client is GraalVM-native
  supported.
- **`@RegisterForReflection` on the models bound by Jackson.** The `hashbin-api`
  DTOs are (de)serialized by the REST Client's Jackson provider; the native image
  needs them registered or they bind to empty objects. They live in a dependency
  jar and are never hand-edited, so a holder
  ([`GeneratedModelsForReflection`](src/main/java/net/hashbin/cli/client/GeneratedModelsForReflection.java))
  registers them via `@RegisterForReflection(targets = …)`. Same gotcha the
  backend documents.
- **Config resolution is plain Java, via the CDI bridge.** `CliConfigProducer` is an
  `@ApplicationScoped` `@Produces` method that reads the global options from Quarkus Picocli's
  injected `CommandLine.ParseResult` and surfaces them as an injectable `CliConfig` bean
  (flag→env→default/stored). Commands `@Inject CliConfig` instead of walking the command tree.
  `CliConfig` is a lombok `@Value @Builder` bean (fluent accessors via `lombok.config`), kept
  `@ApplicationScoped` (a **lazy proxy**) so the producer runs only when a command reads a value
  at execution time, after `ParseResult` is resolved — a `@Singleton`/eager read would cycle with
  the Picocli command build. The `token`/`hmacSecret` fields are `@ToString.Exclude`d so they
  never leak into a log line.
- **The REST clients are injected, configured `@RestClient` beans.** `HashbinClient` is
  `@ApplicationScoped` and injects one `@RestClient` per generated interface. Each is a local
  sub-interface that `extends` the generated `*Api` (we don't own it) and decorates it with
  `@RegisterRestClient(configKey="hashbin")` and two `@RegisterProvider`s —
  `BaseUriOverrideFilter` and `ConditionalBearerFilter`. No `RestClientBuilder`/`new`
  anywhere. This is the same pattern as `TkPrefetcherConnector`.
- **`--api-base` and the bearer token are applied per request, not at startup.**
  `@RegisterRestClient` binds `quarkus.rest-client.hashbin.url` at boot (a placeholder),
  *before* picocli parses args — so `BaseUriOverrideFilter` rewrites the origin from
  `CliConfig` on every call, and `ConditionalBearerFilter` adds `Authorization: Bearer` only
  to the token-bearing endpoints (`/stamp`, `/my-timestamps`, `/auth/me`), leaving anonymous
  calls unauthenticated. Both filters read `CliConfig` lazily (a normal-scoped proxy), so
  nothing is resolved during the Picocli command build — which would otherwise cycle with
  `ParseResult` production.
- **A global `GlobalExceptionHandler` is the safety net**, wired via `setExecutionExceptionHandler`
  in `Main.run`. The commands still catch `ApiException` themselves (to print the server's
  `{"error": ...}` in the CLI's `✗` style); the handler covers anything that escapes.

## License

Apache-2.0 — see [LICENSE.txt](LICENSE.txt).

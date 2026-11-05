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
contract**, not by its Java classes — exactly how `hashbin-web` already works.
Re-implementing the wire format (a few snake_case records + one HMAC-SHA256) is
cheaper than maintaining a shared Maven module, and keeps the backend's
deployable (one Quarkus app, one native image) untouched. If non-trivial
signing/validation logic ever needs sharing, that is the trigger to factor a
`hashbin-core` module across a new parent reactor. See the backend README's
"Ecosystem" section.

## Ecosystem

- **hashbin-quarkus** — the backend (Quarkus 3.40.1 / JDK 21). *(sibling)*
- **hashbin-cli** — this client.
- **hashbin-web** — static frontend. *(sibling)*
- **hashbin-deploy** — Kubernetes/Kustomize manifests. *(sibling)*

## Requirements

- **JDK 21** to build/run the JVM jar (Quarkus's bundled toolchain rejects JDK 25
  class files). Run `./mvnw` with `JAVA_HOME` pointed at a 21.
- **Maven 3.9+** (the wrapper downloads it).
- A **container engine** (Docker/Podman) only if you build the **native** binary.

## Build & run (JVM)

```bash
export JAVA_HOME=<a JDK 21>
./mvnw -DskipTests package
java -jar target/quarkus-app/quarkus-run.jar --help
```

## Commands

```
hashbin [--api-base URL] [--hmac-secret SECRET] [--token JWT] COMMAND

  health                      Check API availability
  stamp FILE... [--include-name]   Hash files locally and create signed timestamps
  verify FILE_OR_HASH [--algorithm auto|md5|sha1|sha256|sha512]
                              Look up existing timestamps for a file or hash
  my-timestamps               List your own timestamps (needs a token)
  auth register --email E [--password P]   Create an account, store its token
  auth login    --email E [--password P]   Sign in, store the access token
  auth me                      Show the signed-in account
  explore [--limit N] [--offset N]         List recent public timestamps
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
export JAVA_HOME=<a JDK 21>
./mvnw -Dnative -DskipTests package
./target/hashbin-cli-1.0.0-SNAPSHOT-runner --help
```

Compiles inside a Mandrel container (no local GraalVM/Mandrel install needed), so
only a container engine is required. The resulting binary starts in milliseconds.

## Tests

```bash
export JAVA_HOME=<a JDK 21>
./mvnw test
```

- Unit: HMAC (RFC 4231 vector), file digest vectors, size/hex formatting, and
  signature verification (valid / tampered / wrong-secret).
- `HashbinCliContractTest`: drives the **real command tree** against a throwaway
  `com.sun.net.httpserver` stub that speaks the wire contract — proving arg
  parsing, JSON binding, signature verification and exit codes with no live
  backend.

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
  and exit. We depend on `quarkus-picocli` + `quarkus-jackson` only; outbound calls
  use the JDK `java.net.http.HttpClient` (GraalVM-native supported). Adding
  `quarkus-rest`/`undertow` would keep the JVM alive and bloat the native image.
- **`@RegisterForReflection` on every model.** The models are bound from
  `JsonNode`; without the annotation the JVM tests pass but the **native** image
  deserializes to empty objects. Same gotcha the backend documents.
- **Config resolution is plain Java, not MicroProfile.** `HashbinCommand` resolves
  flag→env→default itself so the whole tree runs under a bare
  `new CommandLine(new HashbinCommand())` in tests, with no CDI container.
- **Subcommands use `@ParentCommand`**, not CDI injection — they're instantiated by
  Picocli's reflection factory, which is why the leaf commands are plain classes.

## License

Apache-2.0 — see [LICENSE](LICENSE).

# Zolt Usage Guide

Zolt is a Java build tool, not just a project generator. The smallest demo is
`zolt init && zolt test && zolt package`, but the current command surface also
covers dependency editing, reproducible resolution, workspaces, framework
packaging, generated sources, migration audits, quality gates, publishing, and
native release workflows.

## Start a Project

```sh
zolt init hello --group com.example --java 21
cd hello
zolt add com.google.guava:guava:33.4.0-jre
zolt add test org.junit.platform:junit-platform-console-standalone:1.11.4
zolt resolve
zolt test
zolt package
zolt run-package
```

Zolt writes a small `zolt.toml`, resolves a deterministic `zolt.lock`, compiles
sources, runs tests, and packages artifacts from the project model.

## Command Map

Common project commands:

```sh
zolt init NAME
zolt resolve
zolt build
zolt run -- ARGS
zolt test
zolt package
zolt run-package -- ARGS
zolt clean
zolt doctor
```

Dependency and lockfile commands:

```sh
zolt add GROUP:ARTIFACT:VERSION
zolt add test GROUP:ARTIFACT:VERSION
zolt add runtime GROUP:ARTIFACT:VERSION
zolt add provided GROUP:ARTIFACT:VERSION
zolt add processor GROUP:ARTIFACT:VERSION
zolt remove GROUP:ARTIFACT
zolt platform add GROUP:ARTIFACT:VERSION
zolt version set ALIAS VERSION
zolt version remove ALIAS
zolt resolve --locked
zolt resolve --offline
zolt tree
zolt why GROUP:ARTIFACT
zolt conflicts
zolt policy --format json
zolt classpath audit --format json
```

Build, test, and evidence commands:

```sh
zolt plan --target package
zolt plan --target test --format json
zolt test --test com.example.MainTest
zolt test --tests '*IntegrationTest'
zolt test --include-tag fast --exclude-tag slow
zolt test --suite smoke
zolt test --shard 1/4
zolt test --reports-dir target/test-reports
zolt test --profile-tests --profile-dir target/test-profiles
zolt coverage
zolt coverage --suite smoke --xml-report target/coverage/jacoco.xml
zolt test plan --shard-count 4 --format json
```

Toolchain commands:

```sh
zolt toolchain status
zolt toolchain status --json
zolt toolchain sync
zolt toolchain install java 21 --graalvm --native-image
zolt toolchain list
zolt toolchain available
zolt exec -- java -version
zolt toolchain global use java 21 --temurin
zolt toolchain global use java 21 --graalvm --native-image
zolt toolchain sync --global
zolt toolchain status --global
zolt toolchain global unset
zolt shims install
zolt shims status
zolt shims uninstall
```

Workspace commands:

```sh
zolt init platform --workspace
zolt resolve --workspace
zolt build --workspace --all
zolt test --workspace --member apps/api
zolt package --workspace --members apps/api,tools
zolt run --workspace --member tools -- release-notes
zolt coverage --workspace --all
zolt check --workspace --context ci --all
zolt clean --workspace --all
```

Packaging, publishing, and release commands:

```sh
zolt package --mode thin
zolt package --mode spring-boot
zolt package --mode spring-boot-war
zolt package --mode quarkus
zolt package --mode uber
zolt package --plan --format json
zolt publish --dry-run
zolt publish --context release
zolt native
zolt native --workspace --member apps/zolt
zolt release-archive --target linux-x64 --binary target/native/zolt
zolt release-index --channel-manifest channels/zap.json --output index.json
zolt release-verify dist/zolt-0.1.0-linux-x64.tar.gz
```

Uber-jar packaging merges runtime dependency classes into one archive. Duplicate
class entries fail the build by default; set `[package] uberDuplicates =
"first-wins"` to keep the first occurrence in the deterministic classpath order
instead (application output wins, then earlier dependencies) and report a per-jar
count of overridden entries. Uber jars that contain `META-INF/versions/` entries
are stamped `Multi-Release: true` automatically unless a `[package.manifest]`
entry sets that attribute explicitly.

```toml
[package]
mode = "uber"
uberDuplicates = "first-wins"
```

### Publishing to Maven repositories

`zolt publish` uploads the packaged artifact, a generated POM, and any
configured supplemental artifacts to a Maven-compatible repository. Configure
targets under `[publish]`, referencing `[repositoryCredentials]` for
authenticated repositories:

```toml
[publish]
releaseRepository = "company-releases"
snapshotRepository = "company-snapshots"

[publish.repositories.company-releases]
url = "https://repo.example.com/releases"
credentials = "company"

[publish.repositories.company-snapshots]
url = "https://repo.example.com/snapshots"
credentials = "company"
```

`zolt publish --dry-run` previews target routing, artifact evidence, the
generated POM, checksum sidecars, and any blockers without uploading anything.

Every uploaded file — the main artifact, each supplemental artifact, and the
POM — is accompanied by `.md5`, `.sha1`, and `.sha256` checksum sidecars, the
Maven repository standard for integrity verification. The sidecars contain the
bare lowercase hex digest and are listed in the `--dry-run` output.

Sources and Javadoc jars are produced by `zolt package` when enabled and are
published as supplemental artifacts with the standard `-sources.jar` and
`-javadoc.jar` classifiers:

```toml
[package]
sources = true
javadoc = true
```

The generated POM is enriched from `[package.metadata]`. Alongside `name`,
`description`, `url`, `license`, `developers`, `scm`, and `issues`, publish emits
`<packaging>` (derived from the package mode), a license `<url>`, richer SCM
elements, and structured developers with `<id>`, `<email>`, and `<organization>`:

```toml
[package.metadata]
name = "Example Library"
description = "A reusable Java library."
url = "https://example.com/library"
license = "Apache-2.0"
licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0.txt"
scm = "https://github.com/example/library"
scmConnection = "scm:git:https://github.com/example/library.git"
scmDeveloperConnection = "scm:git:ssh://git@github.com/example/library.git"
scmTag = "v1.0.0"

[package.metadata.developer.ada]
name = "Ada Lovelace"
email = "ada@example.com"
organization = "Example Inc"
url = "https://example.com/ada"
```

`zolt publish --dry-run --central` reports Maven Central readiness: it checks the
release version, the required POM metadata (name, description, url, license
name+url, an identifiable developer, scm url+connection), the sources and Javadoc
jars, GPG signatures, and checksums, printing an actionable next step for each
unmet requirement. The check is opt-in, so it never blocks publishing to internal
repositories.

Enable `[publish.signing]` to attach a detached GPG signature (`.asc`) to every
uploaded artifact and to the POM. Publish shells out to the `gpg` binary
(`--batch --detach-sign --armor`); `keyId` selects the signing key and, when a key
requires a passphrase, `passphraseEnv` names an environment variable holding it —
the passphrase is fed to gpg over stdin, never the command line. Without
`passphraseEnv`, signing relies on `gpg-agent`. Secrets are referenced by
environment-variable name only:

```toml
[publish.signing]
enabled = true
keyId = "3AB1C2D3E4F5A6B7"
passphraseEnv = "ZOLT_SIGNING_PASSPHRASE"
```

Migration and integration commands:

```sh
zolt explain --source auto
zolt explain --source gradle --scorecard
zolt explain --source maven --blockers
zolt explain --emit-toml
zolt explain --emit-toml-output target/zolt-migration
zolt ide model --format json
zolt ide model --workspace --format json
zolt quarkus plan
zolt quarkus test-plan
```

Self-management commands for installer-managed native Zolt versions:

```sh
zolt self releases --channel zap
zolt self install VERSION --channel zap
zolt self exec VERSION -- version
zolt self prune --keep 3 --dry-run
zolt update
```

## Project Configuration

A minimal project declares a Java package model, repositories, dependencies, and
source roots:

```toml
[project]
name = "hello-zolt"
version = "0.1.0"
group = "com.example"
java = "21"
main = "com.example.Main"

[repositories]
central = "https://repo.maven.apache.org/maven2"

[dependencies]
"com.google.guava:guava" = "33.4.0-jre"

[test.dependencies]
"org.junit.platform:junit-platform-console-standalone" = "1.11.4"

[build]
source = "src/main/java"
test = "src/test/java"
output = "target/classes"
testOutput = "target/test-classes"
```

Dependencies can be split by usage:

```toml
[dependencies]
"org.springframework.boot:spring-boot-starter-webmvc" = {}

[runtime.dependencies]
"com.h2database:h2" = {}

[provided.dependencies]
"jakarta.servlet:jakarta.servlet-api" = {}

[dev.dependencies]
"org.springframework.boot:spring-boot-devtools" = {}

[annotationProcessors]
"org.projectlombok:lombok" = {}

[test.dependencies]
"org.junit.jupiter:junit-jupiter" = "5.11.4"

[test.annotationProcessors]
"org.projectlombok:lombok" = {}
```

Version aliases, BOM/platform imports, exclusions, and constraints are first
class:

```toml
[versions]
spring = "4.0.6"

[platforms]
"org.springframework.boot:spring-boot-dependencies" = { versionRef = "spring" }

[dependencies]
"org.springframework.boot:spring-boot-starter-webmvc" = { exclusions = [
  { group = "org.apache.tomcat.embed", artifact = "tomcat-embed-core" }
] }
# classifier and type select a specific published artifact for a coordinate and
# are independent; both work in every dependency scope, with or without a
# version or platform-managed version.
"io.netty:netty-transport-native-epoll" = { version = "4.1.100.Final", classifier = "linux-x86_64" }

[dependencyPolicy]
exclude = [
  { group = "commons-logging", artifact = "commons-logging", reason = "Use Spring JCL and SLF4J bridges" }
]

[dependencyConstraints]
"org.apache.tomcat.embed:tomcat-embed-core" = { version = "11.0.21", kind = "strict", reason = "Servlet baseline" }
```

## Resolution and Lockfile Contracts

Zolt deliberately documents the parts of resolution that Maven leaves
ambient or historically shaped:

- Version conflicts use direct-preference plus newest-wins mediation, not
  Maven nearest-wins. Every mediation is recorded in `zolt.lock` as a
  `[[conflict]]` entry.
- Dynamic versions, version ranges, external SNAPSHOT dependencies, system
  scope, and uninterpolated `${...}` versions at point of use are rejected
  instead of resolved dynamically.
- Repositories are tried in alphabetical order of repository id because TOML
  table order is not a contract. The `source` recorded in `zolt.lock` makes
  the selected repository id part of the lockfile contract.
- Integrity is lockfile-pinned. Zolt records the SHA-256 of each fetched POM
  and JAR and verifies cached/build inputs against those lockfile hashes.
  Repository checksum sidecars are not fetched; the first download trusts the
  repository TLS channel.
- `zolt.lock` version 1 is deterministic: no timestamps, no absolute paths,
  stable ordering, and LF line endings. Older lockfile versions should be
  regenerated with the current Zolt; newer lockfile versions require a newer
  Zolt before `zolt resolve --locked` can verify them.
- `zolt add`, `zolt remove`, and `zolt platform` rewrite `zolt.toml`. They
  warn before rewriting a file that contains comments because comments and
  custom formatting may be removed.
- `[publish]` is active project configuration for `zolt publish --dry-run`
  and uploads, and can reference `[repositoryCredentials]` entries for
  authenticated repositories.

### SNAPSHOT dependencies

A directly declared `-SNAPSHOT` dependency version — in `[dependencies]` and
every other dependency section, including `runtime`/`provided`/`dev`/`test`
dependencies and `annotationProcessors` — parses in `zolt.toml` and is decided
at resolve time rather than rejected while parsing, so the resolve layer is the
single authority on whether a SNAPSHOT is supported (`zolt add
group:artifact:1.0-SNAPSHOT` writes the declaration and lets resolve decide).
SNAPSHOTs are rejected by default because a moving version breaks the
reproducibility that `zolt.lock` promises. Two narrow exceptions are allowed
because they name artifacts that already exist on this machine rather than a
remote moving target: workspace-member coordinates a sibling depends on with
`{ workspace = "path" }` (built from source and locked with `source =
"workspace"`), and artifacts present in an enabled maven-local repository
overlay (`zolt resolve --repository-overlay maven-local`), which are SHA-256
pinned in `zolt.lock` with `source = "local-overlay:maven-local"` just like any
materialized artifact. Everything else — remote SNAPSHOT feeds,
`maven-metadata.xml`, and timestamped-snapshot resolution — stays unsupported by
design; a SNAPSHOT that is neither a workspace member nor present in an enabled
overlay is rejected at resolve time with guidance (from `zolt resolve`, `zolt
resolve --locked`, and `zolt explain verify`) rather than fetched from the
network. `[platforms]` and `[dependencyConstraints]` continue to reject
SNAPSHOTs while parsing.

## Java Toolchains

Zolt can run `java`, `javac`, `jar`, and GraalVM `native-image` from a concrete
managed JDK instead of whatever happens to be on `PATH`.

Project-pinned toolchains are declared in `zolt.toml` and locked in `zolt.lock`:

```toml
[project]
java = "21"

[toolchain.java]
version = "21"
distribution = "graalvm-community"
features = ["native-image"]
policy = "prefer-managed"
```

Use `temurin` for JVM-only work, or `graalvm-community` with
`features = ["native-image"]` for native builds. Supported policies are:

- `prefer-managed`: use the Zolt-managed JDK when installed, but allow a
  matching system JDK.
- `require-managed`: never use ambient `JAVA_HOME` or `PATH`.
- `allow-system`: prefer a matching ambient JDK.

Sync installs the managed JDK into the default user store and writes stable
multi-platform toolchain metadata:

```sh
zolt toolchain install java 21 --graalvm --native-image
zolt toolchain sync
zolt toolchain list
zolt toolchain available
zolt toolchain status
zolt toolchain status --json
```

Use `zolt toolchain install java ...` when you want a managed Java version
available without first creating a project config. It installs into the same
shared user store and writes reusable global lock metadata, but it does not
change the global default. `zolt toolchain list` shows the active project or
global selection, lock entries, and installed catalog entries. `zolt toolchain
available` shows the bundled Temurin and GraalVM catalog matrix.

`zolt.lock` records the supported target matrix, so a macOS developer and Linux
CI do not rewrite the lock back and forth. The install itself stays local to the
machine under `~/.zolt/toolchains`; only the current host's archive is downloaded
when it is missing. Managed downloads are pinned to exact artifact URLs and
verified with SHA-256 checksums from the lock.

Build, run, test, package, and native commands use the resolved toolchain
automatically. `zolt exec -- ...` runs an arbitrary command with `JAVA_HOME` and
`PATH` pointed at the resolved JDK:

```sh
zolt exec -- java -version
zolt exec -- native-image --version
```

User global Java is a default, not a project override. Store it in
`~/.zolt/config.toml` with `[defaults.toolchain.java]`, or use the CLI:

```sh
zolt toolchain global use java 21 --temurin
zolt toolchain sync --global
zolt toolchain status --global
zolt exec --global -- java -version
```

When a project declares `[toolchain.java]`, project config wins. Outside a Zolt
project, `zolt exec -- java -version` can use the global default. Global lock
metadata lives beside the user config in `~/.zolt/global-toolchains.lock`, while
installed JDKs are shared in `~/.zolt/toolchains`.

Shell-global Java is opt-in through shims:

```sh
zolt shims install
export PATH="$HOME/.zolt/shims:$PATH"
java -version
```

The shims are small wrapper scripts for `java`, `javac`, `jar`, `javadoc`,
`jshell`, and `native-image`. Each wrapper calls `zolt exec -- <tool> ...`, so a
project toolchain still wins inside a Zolt project and the global default is
used outside one. Zolt does not edit shell profiles automatically.

## Enterprise Networks

Zolt runs behind corporate proxies, TLS-intercepting firewalls, and internal
mirrors. All of this is *transport* configuration: it changes how bytes are
fetched, never what is resolved, so it never affects `zolt.lock` or build
fingerprints. Machine-level settings live in `~/.zolt/config.toml` and
environment variables, never in the committed `zolt.toml`.

### HTTP(S) proxies

Zolt honors the standard proxy environment variables for both artifact
downloads and Java toolchain downloads:

```sh
export HTTPS_PROXY=http://proxy.corp.example:8080
export HTTP_PROXY=http://proxy.corp.example:8080
export NO_PROXY=.internal.example,localhost,127.0.0.1
```

Lowercase names (`https_proxy`, `http_proxy`, `no_proxy`) are also read and take
precedence over the uppercase forms. Java's built-in HTTP client ignores these
variables, so Zolt bridges them itself. The conventional Java system properties
are honored as a fallback when the matching environment variable is unset:

```sh
zolt resolve -Dhttps.proxyHost=proxy.corp.example -Dhttps.proxyPort=8080 \
  -Dhttp.nonProxyHosts='*.internal.example|localhost'
```

Precedence, per setting: environment variable first, then the Java system
property. `NO_PROXY`/`no_proxy` (falling back to `http.nonProxyHosts`) supports
exact hosts and domain-suffix matches — `example.com` and `.example.com` both
match `example.com` and `repo.example.com` — and `*` bypasses the proxy for
every host.

### Custom CA trust

To trust a corporate TLS-interception root (or any private CA), point Zolt at a
PEM bundle of one or more certificates. Zolt *augments* the JDK default trust
store with these anchors, so public TLS keeps working alongside the corporate
root. Configure it in user-global config:

```toml
# ~/.zolt/config.toml
version = 1

[network]
caBundle = "~/.zolt/corp-ca.pem"
```

or override per invocation with an environment variable, which wins over the
config file:

```sh
export ZOLT_CA_BUNDLE=/etc/pki/tls/certs/corp-ca.pem
```

Machine-level trust belongs in user-global config or the environment, not in a
project's `zolt.toml`.

### Bearer / personal access token authentication

Repository credentials are referenced by environment-variable *name* only;
secret values are never stored in or read from config, and never written to
`zolt.lock` or command output. Alongside HTTP Basic (`usernameEnv` +
`passwordEnv`), a credential may use a bearer token / PAT via `tokenEnv`, which
sends `Authorization: Bearer <token>` on both downloads and publish uploads:

```toml
# zolt.toml
[repositories]
company = { url = "https://nexus.example.com/repository/maven", credentials = "company" }

[repositoryCredentials.company]
tokenEnv = "COMPANY_ARTIFACT_TOKEN"
```

`tokenEnv` is mutually exclusive with `usernameEnv`/`passwordEnv`; setting both
is a configuration error. Credentialed remote repositories must use HTTPS.

### Java toolchain mirrors

Zolt's bundled JDK catalog downloads from `github.com`. To route those downloads
through an internal mirror, set a mirror base that replaces the `github.com`
prefix. Configure it in user-global config:

```toml
# ~/.zolt/config.toml
[network]
toolchainMirror = "https://nexus.example.com/github"
```

or override with an environment variable, which wins over the config file:

```sh
export ZOLT_TOOLCHAIN_MIRROR=https://nexus.example.com/github
```

A GraalVM archive normally fetched from
`https://github.com/graalvm/graalvm-ce-builds/releases/download/...` is then
fetched from `https://nexus.example.com/github/graalvm/graalvm-ce-builds/...`.
The pinned SHA-256 from the toolchain lock is still verified against the
mirrored bytes, and the lock keeps the canonical upstream URL, so mirroring is
transparent to reproducibility. When a toolchain download fails with a network
error, the remediation names the proxy, CA, and mirror options above.

## Workspaces

Workspace roots describe member projects and default selections:

```toml
[workspace]
name = "workspace-app"
members = ["apps/api", "modules/core", "tools"]
defaultMembers = ["apps/api"]

[repositories]
central = "https://repo.maven.apache.org/maven2"

[commands.tasks.release-notes]
description = "Run the Java tools member release-notes command"
cmd = ["zolt", "run", "--workspace", "--member", "tools", "--", "release-notes"]
```

Workspace commands resolve one root lockfile and run selected members in
dependency order.

## Tests and Coverage

Zolt runs JUnit Platform based tests and can compile Java and Groovy test
sources when configured. That covers examples such as JUnit Jupiter, JUnit
Vintage, and Spock:

```toml
[test.dependencies]
"org.apache.groovy:groovy" = "4.0.22"
"org.spockframework:spock-core" = "2.3-groovy-4.0"
"org.junit.platform:junit-platform-console-standalone" = "1.11.4"

[test.sources]
groovy = ["src/test/groovy"]
```

Test commands support class/method selection, glob patterns, JUnit tags, JVM
arguments, XML reports, deterministic shards, named suites, and optional profile
history used for shard balancing.

## Frameworks and Generated Sources

Zolt has project-model support for common Java application shapes:

- Spring Boot web applications, executable jars, WAR-style packaging, build
  metadata, resources, and Spring Boot package modes.
- Quarkus fast-jar packaging and Quarkus augmentation/test plan inspection when
  `[framework.quarkus]` is enabled.
- Micronaut-style annotation processor workflows.
- Vert.x applications with platform BOMs and dependency exclusions.
- OpenAPI generated Java sources with tool versioning and presets.
- Protobuf/gRPC generated Java sources.
- Library-style canaries such as Commons CLI, HikariCP, and SLF4J workspace
  modules.

Generated-source configuration can look like this:

```toml
[generated.openapiTool]
coordinate = "org.openapitools:openapi-generator-cli"
version = "7.11.0"

[generated.openapiPresets.spring-api]
generator = "spring"
library = "spring-boot"
apiPackage = "com.example.generated.api"
modelPackage = "com.example.generated.model"
invokerPackage = "com.example.generated"

[generated.main.public-api]
kind = "openapi"
language = "java"
input = "src/main/openapi/public-api.yaml"
output = "target/generated/sources/openapi/public-api"
preset = "spring-api"
```

Protobuf/gRPC configuration can look like this:

```toml
[versions]
protobuf = "4.28.3"
grpc = "1.68.1"

[generated.protobufTool]
protocCoordinate = "com.google.protobuf:protobuf-java"
protocVersionRef = "protobuf"
grpcPluginCoordinate = "io.grpc:grpc-api"
grpcPluginVersionRef = "grpc"

[generated.main.greeter]
kind = "protobuf"
language = "java"
inputs = ["src/main/proto/greeter.proto"]
output = "target/generated/sources/protobuf"
```

## Quality and CI

`zolt check` is the umbrella for Zolt-owned quality checks. It can run local or
CI-oriented contexts, validate reports and coverage outputs, require package
evidence, require publish dry-run evidence, and check offline readiness.

```sh
zolt check --context local
zolt check --context ci --reports-dir target/test-reports --coverage-dir target/coverage
zolt check --context ci --require-package --require-publish-dry-run
zolt check --context ci --require-offline-ready
zolt check --format json
```

The CLI also has global output controls for automation:

```sh
zolt --quiet test
zolt --color never --progress never check --format json
zolt --timings --timings-format json package
```

## Migration Explain

`zolt explain` statically audits Maven and Gradle projects. It can print a text
or JSON report, a migration readiness scorecard, a blocker report, or draft
`zolt.toml` files. The fixtures under `examples/migration-explain` include
Maven simple/multimodule and Gradle simple/multiproject/enterprise-style inputs.

Useful prompts for migration work:

```sh
zolt explain --source auto
zolt explain --source gradle --scorecard
zolt explain --source maven --blockers
zolt explain --emit-toml
zolt explain --emit-toml-output target/zolt-draft
zolt explain --emit-toml --resolve-external-parents
```

By default the audit is fully offline, so a Maven project that inherits an
external parent (for example `spring-boot-starter-parent`) reports its inherited
dependency versions as unknown. Pass `--resolve-external-parents` to opt in to
fetching those external parent POMs and their imported BOMs over the network —
from Maven Central plus any HTTPS `<repositories>` declared in the POM chain,
cached under `~/.zolt/cache` for offline re-runs. Recovered versions are fixed
literals, so inherited `[dependencies]` and `[platforms]` resolve in the report
and in `--emit-toml`; anything dynamic (ranges, SNAPSHOT parents, unresolved
`${...}`) is surfaced as a review item instead of being guessed, and every
fetched coordinate is recorded in the audit.

### Verify a migration

`zolt explain verify` gives a factual, per-module comparison between what the incumbent
build (Maven or Gradle) resolves and what Zolt resolves, so a migration can be verified
instead of trusted. The incumbent is auto-detected — a `pom.xml` selects Maven, a Gradle
settings/build script selects Gradle — and `--source maven|gradle` overrides. For Maven it
runs `./mvnw` (else `mvn` on `PATH`) once as `dependency:tree`; for Gradle it runs
`./gradlew` (else `gradle` on `PATH`) once, requesting every project's `dependencies`
report in a single invocation. It then resolves the Zolt project/workspace with Zolt's own
resolver and reports — per module and per scope — what matched, what drifted in version,
and what appears on only one side.

```sh
# Compare the incumbent build and a Zolt project rooted in the same directory.
zolt explain verify

# Verify a Gradle migration (auto-detected when a settings/build script is present).
zolt explain verify --source gradle

# Point at a draft emitted by --emit-toml-output, and emit machine-readable JSON.
zolt explain verify --zolt-dir target/zolt-draft --format json
```

- Scopes compared: `compile`, `runtime`, `test`, `provided`. Maven and Zolt scope
  names map one to one. Scopes with no counterpart (Maven `system`; Zolt `dev`,
  `processor`, `test-processor`, `quarkus-deployment`, `tool-*`) are reported as
  per-module notes rather than counted as differences.
- Gradle configuration mapping: Gradle's classpaths are cumulative (`runtimeClasspath` and
  `testRuntimeClasspath` include the compile dependencies) whereas Maven and Zolt place each
  dependency in exactly one scope, so the compared scopes are recovered as set operations
  over the three resolvable classpaths — `compile` = `compileClasspath` ∩ `runtimeClasspath`,
  `runtime` = `runtimeClasspath` \ `compileClasspath`, `provided` = `compileClasspath` \
  `runtimeClasspath` (Gradle `compileOnly`, the Maven `provided` equivalent), and `test` =
  `testRuntimeClasspath` \ (`compileClasspath` ∪ `runtimeClasspath`). BOM/platform nodes and
  dependency constraints (`(c)`) are excluded so the resolved set stays jar-for-jar
  comparable with Maven's tree; `(*)` repeated subtrees are de-duplicated, `a:b:req ->
  resolved` conflict resolutions take the resolved version, and `project(":x")` dependencies
  are compared as ordinary resolved artifacts (mirroring how Maven lists reactor siblings).
  Annotation-processor and other non-classpath configurations are not compared, matching
  Maven's `dependency:tree` (which likewise omits processors).
- Categories per module × scope: matched (same `group:artifact[:classifier]` and
  version), version drift (same coordinate, different version — both reported),
  only-in-Maven, and only-in-Zolt. Zolt uses highest-version-wins mediation and Maven
  uses nearest-wins, so some drift is expected; the report states facts and counts and
  does not editorialize about equivalence.
- `--zolt-dir <path>` selects the Zolt project/workspace to resolve (default: the Maven
  project root when it has a `zolt.toml`). `--offline` resolves the Zolt side from the
  local cache only.
- `--repository-overlay maven-local` resolves the Zolt side through a user-local maven
  repository overlay, mirroring `zolt resolve --repository-overlay` — needed to verify
  overlay-backed projects, including directly declared `-SNAPSHOT` dependencies that
  resolve only from the overlay.
- Workspace members resolve with the workspace-root `[repositories]` and `[platforms]`
  merged in, matching `zolt resolve --workspace`, so shared root configuration is not
  reported as drift.
- Exit code is `0` when the resolved sets are identical across every module and scope,
  and non-zero when any module is one-sided or any scope shows drift or a one-sided
  artifact — so it can gate a migration in CI. `--format json` emits a stable schema
  (`schemaVersion` 1) with an additive `buildTool` field (`maven` or `gradle`); the JSON
  field names stay `maven*` across both tools for schema stability.

Both Maven and Gradle projects are supported. The comparison model, scopes, categories,
and JSON schema are shared; a Gradle build with a dynamic version, a missing included
build, or another unresolvable configuration fails the extraction with an actionable error
rather than a partial comparison, because a build that does not resolve cannot be verified.

## Examples in This Repository

The `examples/` directory is deliberately broad. It includes:

- `hello-zolt`: minimal project with an external dependency.
- `adoption-plain-app`: plain Java app with resources and a small test.
- `junit-basic`: JUnit Platform console runner.
- `junit-vintage`: JUnit Vintage test support.
- `spock-basic`: Groovy test sources and Spock.
- `workspace-app`: app/module/tools workspace with a configured task.
- `large-workspace`: larger workspace selection fixture.
- `spring-boot-webmvc`: Spring Boot web application.
- `spring-boot-petclinic-lite`: Spring Boot app with web, validation, data JPA,
  Thymeleaf, H2, resources, and tests.
- `spring-boot-enterprise-canary`: dependency policy, strict constraints,
  provided/dev scopes, annotation processors, OpenAPI generation, filtered
  resources, test runtime settings, WAR packaging, package metadata, and publish
  repositories.
- `quarkus-http`: Quarkus REST with fast-jar packaging.
- `micronaut-http`: Micronaut HTTP with annotation processors.
- `vertx-http` and `vertx-postgres-crud`: Vert.x application shapes.
- `protobuf-grpc-canary`: Protobuf/gRPC generated sources.
- `provided-container-api`: provided dependency/container API shape.
- `commons-cli-canary`, `hikaricp-canary`, `slf4j-canary`: library-style
  canaries and workspace/library packaging exercises.
- `migration-explain/*`: Maven and Gradle projects used by `zolt explain`.

## Good Fit

Zolt is strongest when you want:

- a project-owned Java build model in `zolt.toml`;
- deterministic dependency resolution in `zolt.lock`;
- fast local commands from one native binary;
- workspace-aware build/test/package flows;
- typed plans and JSON output for tooling;
- CI checks that understand the package and dependency model;
- migration analysis for Maven or Gradle inputs;
- native distribution and release verification for Zolt-produced artifacts.

It is still Java-focused and intentionally opinionated. For unusual build
plugins, custom Gradle logic, or framework behavior outside the implemented
lanes, use `zolt explain`, `zolt plan`, and the examples above to check the
current fit before assuming full drop-in replacement coverage.

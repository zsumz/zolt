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

[dependencyPolicy]
exclude = [
  { group = "commons-logging", artifact = "commons-logging", reason = "Use Spring JCL and SLF4J bridges" }
]

[dependencyConstraints]
"org.apache.tomcat.embed:tomcat-embed-core" = { version = "11.0.21", kind = "strict", reason = "Servlet baseline" }
```

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
```

## Examples in This Repository

The `examples/` directory is deliberately broad. It includes:

- `hello-zolt`: minimal project with an external dependency.
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

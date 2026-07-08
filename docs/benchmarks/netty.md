# Netty Benchmark Lane

Netty should be the first large real-codebase benchmark lane. It is recognizable
to Java developers, large enough to expose build-tool overhead, and already used
publicly as a Maven comparison target by Mill.

## Why Netty

Netty is a better flagship real-project target than a generic large repository:

- it is a real Maven multi-module Java project;
- it is large enough to make build-tool overhead visible;
- it includes native/JNI pieces, code generation, tests, and packaging details;
- it has a central module, `common`, that is useful for single-module and
  incremental workflows;
- another JVM build tool has already shown that a reviewable adapter can make
  Netty a credible benchmark target without pretending the adapter replaces the
  upstream build.

Reference material:

- Mill performance write-up: `https://mill-build.org/mill/comparisons/performance.html`
- Netty repository: `https://github.com/netty/netty`

## Mill Reference Shape

The Mill benchmark is useful because it is specific about the workload and the
limits of the adapter. Their Netty comparison modeled the Maven reactor with an
alternate build definition, covered the major compile/test/codegen/native pieces,
and still stated that the adapter was not a complete replacement for Netty's
upstream build.

Use that as the standard for Zolt:

- benchmark a pinned Netty checkout, not a toy clone;
- keep the Zolt adapter reviewable;
- report adapter coverage next to every result;
- compare clean-all, single-module, incremental, and no-op workflows separately;
- avoid claiming full Netty support until the adapter really covers the native
  build surface.

## Status

Netty starts as `native-baseline`. It becomes `comparison-ready` only after the
Zolt adapter is checked in and proves the covered source set.

Current native baseline pin:

- branch: `4.1`;
- commit: `bb2ff68a1fb71cb4b0eb9a9e17b66c52aff680c6`;
- pinned date: `2026-07-08`.

Required pinned metadata:

- Netty upstream URL;
- immutable commit SHA or release tag;
- JDK version;
- Maven wrapper or Maven version;
- Zolt release version;
- runner OS, architecture, CPU, and memory;
- exact commands for each workflow;
- adapter coverage notes.

## Adapter Policy

The Zolt adapter should be an overlay, not a fork of Netty:

- keep the upstream checkout unchanged where possible;
- store adapter files under `benchmarks/adapters/netty/`;
- start from `benchmarks/adapters/netty/coverage.json` and keep the coverage
  record current as modules and workflows are added;
- copy or overlay `zolt.toml` and workspace metadata during the run;
- record every module included and excluded;
- record whether JNI, Groovy code generation, tests, integration tests, and
  packaging are covered;
- never call the result a full Netty comparison if any major Maven work is
  omitted.

The first adapter does not need to be 100% complete. It does need to be honest:
if it covers Java compile for the core modules only, the summary must say that.

## Workflows

Mirror the shape of the Mill comparison, then add Zolt-specific evidence:

- dependency setup;
- sequential clean compile all;
- parallel clean compile all;
- clean compile single module, starting with `common`;
- no-op compile single module;
- implementation-only edit in `common`;
- public API edit in `common`;
- package or install workflow when the native Maven build needs packaged reactor
  artifacts;
- optional test compile and focused test run after compile parity is stable.

Do not mix these lanes. A clean-all win, single-module win, and no-op win should
be reported as separate claims with separate samples.

## Maven Baseline

The native Maven baseline uses the runner's configured Maven executable for now.
The first command set is intentionally conservative:

```sh
mvn -q -DskipTests -Dcheckstyle.skip -DskipAutobahnTestsuite -DskipHttp2Testsuite compile
mvn -q -T 1C -DskipTests -Dcheckstyle.skip -DskipAutobahnTestsuite -DskipHttp2Testsuite compile
mvn -q -pl common -am -DskipTests -Dcheckstyle.skip -DskipAutobahnTestsuite -DskipHttp2Testsuite compile
```

Treat the first Netty run as command validation as well as timing evidence.
After that run, keep the exact command lines and any required skip flags in the
artifact.

For Netty-like Maven reactors, `compile` may not be equivalent to the real
workflow if modules depend on packaged artifacts from sibling modules. If the
baseline must use `install`, say so in the result and keep Zolt's measured task
semantically comparable.

## Publication Rules

Publish Netty results only when the artifact includes:

- raw samples;
- command logs;
- deterministic summary;
- optional OpenAI summary;
- pinned upstream ref;
- adapter files;
- module coverage table;
- list of omitted native build features;
- exact Maven and Zolt commands.

The public summary should be boringly precise:

```text
On runner X, for Netty commit Y, Zolt adapter coverage Z, median workflow A was
N ms with Zolt and M ms with Maven.
```

That is the standard. Anything looser belongs in exploratory notes, not the
README.

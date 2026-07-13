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

Netty now has two deliberately separate lanes:

- `scripts/benchmark-real-project --project netty` is the large upstream Maven
  baseline for the selected core Java reactor. It is not a Zolt comparison.
- `scripts/benchmark-netty-compare` is a Zolt-versus-Maven comparison for the
  pinned `common` main-source subset. It is not a full Netty comparison.

The `common` runner copies upstream Java sources into generated Zolt and Maven
overlays, removes Graal substitution sources that require native-image
processing, and writes the exact dependency set and coverage attributes into
the artifact. Both overlays use the same filtered sources, dependencies, and
Java 21 level. Their compiler outputs and thin jars are not asserted to be
byte-identical, so package rows remain separate.

The generated overlays intentionally avoid Netty's unreleased optional test
tooling and upstream Maven plugin lifecycle. Those remain part of the separate
upstream baseline/full-adapter problem, not hidden setup in this subset.

Current upstream Maven baseline:

- branch: `4.1`;
- commit: `bb2ff68a1fb71cb4b0eb9a9e17b66c52aff680c6`;
- pinned date: `2026-07-08`;
- validated lane: Netty core Java Maven reactor, excluding platform-native
  modules and testsuites;
- first local validation artifact:
  `target/benchmarks/netty-core-upstream-baseline-r1`.

Required pinned metadata:

- Netty upstream URL;
- immutable commit SHA or release tag;
- JDK version;
- Maven wrapper or Maven version;
- Zolt release version;
- runner OS, architecture, CPU, and memory;
- exact commands for each workflow;
- adapter coverage notes.

Run the subset comparison against a checkout at the pinned commit:

```sh
scripts/benchmark-netty-compare \
  --netty-dir /path/to/netty \
  --zolt ~/.zolt/bin/zolt \
  --repeat 3
```

Tests are not copied into either overlay. Add a separate test lane only after its
dependency model and lifecycle are meaningfully equivalent.

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

The adapter does not need to be 100% complete. It does need to be honest: the
current coverage record says `common` main sources only and lists the omitted
reactor, native, generated-source, test, and package behavior.

## Workflows

Mirror the shape of the Mill comparison, then add Zolt-specific evidence:

- clean package Netty core Java reactor;
- parallel clean package Netty core Java reactor;
- clean compile `common`;
- no-op compile `common`;
- implementation-only edit in `common`;
- public API edit in `common`, once that mutation lane is added;
- optional test compile and focused test run after compile parity is stable.

Do not mix these lanes. A clean-all baseline, single-module comparison, no-op
comparison, and non-equivalent package row should be reported separately.

## Maven Baseline

The upstream Maven baseline uses the runner's configured Maven executable for
now. The large Netty workflow uses `package`, not `compile`, because Netty
modules depend on packaged artifacts from sibling modules.

```sh
mvn -q -pl common,buffer,transport,resolver,codec,codec-dns,codec-haproxy,codec-http,codec-http2,codec-memcache,codec-mqtt,codec-redis,codec-smtp,codec-socks,codec-stomp,codec-xml,handler,handler-proxy,handler-ssl-ocsp -am -DskipTests -Dcheckstyle.skip -DskipAutobahnTestsuite -DskipHttp2Testsuite package
mvn -q -T 1C -pl common,buffer,transport,resolver,codec,codec-dns,codec-haproxy,codec-http,codec-http2,codec-memcache,codec-mqtt,codec-redis,codec-smtp,codec-socks,codec-stomp,codec-xml,handler,handler-proxy,handler-ssl-ocsp -am -DskipTests -Dcheckstyle.skip -DskipAutobahnTestsuite -DskipHttp2Testsuite package
mvn -q -pl common -am -DskipTests -Dcheckstyle.skip -DskipAutobahnTestsuite -DskipHttp2Testsuite compile
```

The full all-module package lane is intentionally separate from this baseline:
the pinned Netty 4.1 snapshot can require platform-native `native-src` artifacts
that are not available from public Maven repositories. Do not fold that failure
into the core Java reactor result.

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

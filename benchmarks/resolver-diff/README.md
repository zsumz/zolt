# resolver-diff

Tracked differential dependency-resolution harness: zolt vs Maven, over
real Maven Central coordinates.

For each root `G:A:V` it resolves the same single-dependency project both
ways — a generated `pom.xml` through `mvn dependency:list`, and a real
`zolt init` + `zolt add` flow through the native binary — then diffs the
resolved `(group:artifact -> version, scope)` maps and classifies every
divergence as *intended semantics* (newest-wins mediation, no ranges, no
external SNAPSHOTs, four-scope model) or `INVESTIGATE`.

## Run

```sh
# requires: mvn on PATH, a built native binary at apps/zolt/target/native/zolt
python3 harness.py roots-smoke.txt out-smoke     # 3 roots, ~1 min warm
python3 harness.py roots-full.txt out-full       # 25 roots

# or run against the JVM bootstrap from this directory
ZOLT_BIN=../../scripts/bootstrap-zolt-jvm python3 harness.py roots-smoke.txt out-smoke
```

Override tool locations with `ZOLT_BIN` / `MVN_BIN`. Output:
`<out>/results/<root>.json` per root plus `results/summary.md`. Network
access to Maven Central is required; `<out>/work/` holds the generated
probe projects.

Use `roots-smoke.txt` for quick semantic checks while changing the resolver.
Use `roots-full.txt` as a manual/networked regression gate before updating
the checked-in baseline artifacts.

## Tracked artifacts

- `roots-smoke.txt`: small corpus for quick local checks.
- `roots-full.txt`: broader 25-root sweep corpus.
- `results/*.json`: baseline evidence for notable divergences or defects.
- `results/summary.md`: baseline sweep summary.
- `divergences-vs-maven.md`: reference note for the documented resolver
  contract.

## Divergence classes

| class | meaning |
| --- | --- |
| `match` | same version, same compile/runtime bucket |
| `version-diff/expected-newest-wins` | zolt strictly newer — intended mediation model |
| `version-diff/INVESTIGATE` | zolt older or uncomparable — newest-wins can't explain it |
| `scope-diff` | same version, different effective scope bucket |
| `only-maven/INVESTIGATE` | Maven resolved it, zolt.lock lacks it |
| `only-zolt/*` | in zolt.lock only (`framework-injection` when a recorded policy explains it) |
| `zolt-hard-fail/intended-*` | zolt refused for a documented-in-code reason |
| `zolt-hard-fail/INVESTIGATE` | zolt refused for an unrecognized reason |

Classifier verdicts are cross-checkable against the `[[conflict]]` entries
zolt itself records in `zolt.lock` (the harness captures them per root).

## Baseline: 2026-07-10 sweep (zolt 0.1.0-zap.20260706, Maven 3.9.16)

21/25 roots resolved identically to Maven, including netty-all,
grpc-netty-shaded, elasticsearch-rest-high-level-client, and
eureka-client. 5 version diffs were confirmed intended newest-wins
(kotlin-stdlib via okhttp ×3, slf4j-api via spring-boot-starter-web,
one via hibernate-core). Two real defects surfaced in the pre-fix
baseline (`results/*.json` hold the evidence):

1. **Parent POM `<dependencies>` are not inherited.**
   `software.amazon.awssdk:s3` loses 16 runtime packages (both default
   HTTP clients and their closures) that every AWS SDK v2 service module
   inherits from the `services` parent POM. Silent under-resolution —
   builds succeed, runtime classpath is broken.
   Root cause: `EffectivePomInheritanceBuilder` merges only properties
   and dependencyManagement from the parent chain.
2. **Unused dependencyManagement entries are interpolated eagerly.**
   `org.apache.hadoop:hadoop-client` hard-fails on `${hbase.version}`,
   which hadoop-project defines only inside profiles; Maven leaves the
   entries literal and succeeds because nothing in the graph consults
   them. Validation should defer to point-of-use.

Both defects are now covered by targeted regression tests in the main test
suite. Rerun the smoke or full harness before replacing these baseline
artifacts with post-fix results.

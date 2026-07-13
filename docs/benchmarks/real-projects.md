# Real-Project Benchmarks

Generated benchmarks are controlled and repeatable, but public performance
claims also need real Java projects. A real-project benchmark is publishable
only when the source, ref, commands, environment, and Zolt adapter are pinned and
included with the result.

## Rules

- Pin every upstream project to a commit SHA or immutable release tag.
- Record the exact JDK, OS, architecture, tool versions, command lines, raw
  samples, logs, and artifact names.
- Benchmark the same meaningful task across tools: compile/package/test must be
  stated explicitly.
- Keep first clean build separate from repeated no-op and source-change lanes.
- Label a project as an upstream-tool baseline only until Zolt has an adapter
  that builds the same source set.
- Keep Zolt adapters in the benchmark artifact or repo so the comparison is
  reviewable.
- Do not publish LLM-generated prose as evidence; publish the raw data and the
  deterministic summary.

## Candidate Suite

The machine-readable suite manifest is [projects.json](./projects.json). Keep
this table and that manifest in sync when adding or promoting real-project
lanes.

| Project | Upstream | Upstream tool | Why it matters | Zolt status |
| --- | --- | --- | --- | --- |
| Spring PetClinic | `https://github.com/spring-projects/spring-petclinic` | Maven | Real Spring Boot web/data app with resources and tests | Upstream Maven baseline ready; adapter planned |
| Apache Commons CLI | `https://github.com/apache/commons-cli` | Maven | Small Java library with classic release metadata | Upstream Maven baseline ready; adapter planned |
| HikariCP | `https://github.com/brettwooldridge/HikariCP` | Gradle | Popular Java library with optional dependencies | Needs pinned adapter |
| Netty | `https://github.com/netty/netty` | Maven | Large Maven reactor used by Mill for public build-tool benchmarks | Core Java upstream baseline ready; `common` subset comparison ready |
| JUnit 5 | `https://github.com/junit-team/junit-framework` | Gradle | Large multi-module test framework | Later; likely post-beta |

Current pinned upstream baselines:

| Project | Branch | Commit |
| --- | --- | --- |
| Spring PetClinic | `main` | `51045d1648dad955df586150c1a1a6e22ef400c2` |
| Apache Commons CLI | `master` | `5ee80a0592c3126c7d54afe0e743717bdbc54057` |
| Netty | `4.1` | `bb2ff68a1fb71cb4b0eb9a9e17b66c52aff680c6` |

## Adapter Policy

A Zolt adapter is a checked-in benchmark overlay, not a hidden migration. It
should include:

- `zolt.toml` and any workspace metadata needed to build the project;
- a coverage record under `benchmarks/adapters/<project>/`;
- notes for dependency or task differences from upstream;
- the source paths covered by the benchmark;
- the exact Zolt command used for each lane.

If an adapter drops modules, generated sources, tests, or packaging work, the
summary must say so. That result can still be useful, but it is not a full
project-to-project comparison.

## First Run Target

Start with Spring PetClinic and Apache Commons CLI:

- they cover application and library shapes;
- they are familiar enough for readers to trust;
- their Maven builds make the upstream baseline straightforward;
- they are smaller than a Gradle mega-repo, which keeps the first public run
  practical.

Once those are stable, add HikariCP as the first Gradle-library benchmark.

Upstream baseline runs can start before the adapters exist:

```sh
scripts/benchmark-suite --real-projects spring-petclinic,apache-commons-cli --repeat 5
scripts/benchmark-suite --skip-generated --real-project netty --repeat 3
```

Those commands produce timing evidence for the upstream Maven build only. They
remain upstream baselines even though a separate, smaller Netty `common` subset
comparison now exists.

## Large Baselines

Netty is the first large upstream-tool baseline. It is large, recognizable,
Maven-based, and already used by Mill as a build-tool benchmark target. The
current lane measures Netty's core Java reactor and intentionally excludes
native platform modules and testsuites. That upstream Maven run shows the scale
of the comparison target before Zolt can build the same source set.

For a scoped Zolt comparison, run:

```sh
scripts/benchmark-netty-compare \
  --netty-dir /path/to/pinned/netty \
  --zolt ~/.zolt/bin/zolt \
  --repeat 3
```

That runner covers `common` main sources and records its omissions. Do not use
its timings as evidence for the full core reactor, tests, native transports,
generated native sources, or equivalent release packaging.

See [netty.md](./netty.md) for the dedicated Netty lane plan.

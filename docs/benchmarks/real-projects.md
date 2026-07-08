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
- Label a project as native-tool baseline only until Zolt has an adapter that
  builds the same source set.
- Keep Zolt adapters in the benchmark artifact or repo so the comparison is
  reviewable.
- Do not publish LLM-generated prose as evidence; publish the raw data and the
  deterministic summary.

## Candidate Suite

| Project | Upstream | Native tool | Why it matters | Zolt status |
| --- | --- | --- | --- | --- |
| Spring PetClinic | `https://github.com/spring-projects/spring-petclinic` | Maven | Real Spring Boot web/data app with resources and tests | Needs pinned adapter |
| Apache Commons CLI | `https://github.com/apache/commons-cli` | Maven | Small Java library with classic release metadata | Needs pinned adapter |
| HikariCP | `https://github.com/brettwooldridge/HikariCP` | Gradle | Popular Java library with optional dependencies | Needs pinned adapter |
| Netty | `https://github.com/netty/netty` | Maven | Large Maven reactor used by Mill for public build-tool benchmarks | Native baseline first; adapter planned |
| JUnit 5 | `https://github.com/junit-team/junit-framework` | Gradle | Large multi-module test framework | Later; likely post-beta |

## Adapter Policy

A Zolt adapter is a checked-in benchmark overlay, not a hidden migration. It
should include:

- `zolt.toml` and any workspace metadata needed to build the project;
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
- their Maven builds make the native baseline straightforward;
- they are smaller than a Gradle mega-repo, which keeps the first public run
  practical.

Once those are stable, add HikariCP as the first Gradle-library benchmark.

## Large Baselines

For a more enterprise-looking public story, add Netty as the first large
native-tool baseline after the adapter flow is proven. Netty is large,
recognizable, Maven-based, and already used by Mill as a build-tool benchmark
target. Its native Maven run shows the scale of the comparison target even
before Zolt can build the same source set.

Do not present those as Zolt comparisons until the adapter is reviewable and the
summary names exactly which modules, tests, and packaging work are covered.

See [netty.md](./netty.md) for the dedicated Netty lane plan.

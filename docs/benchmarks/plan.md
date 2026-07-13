# Benchmark Plan

The benchmark suite should feel like product evidence, not a timing script. The
goal is to publish repeatable runs that show how Zolt behaves on a large
Java-native workload, then keep the raw data available so humans and LLMs can
summarize the result without inventing claims.

## Target Shape

The flagship run should have three lanes:

1. `enterprise-generated`: a large deterministic Java workspace generated from a
   workload spec.
2. `real-projects`: pinned upstream projects with checked-in Zolt adapters.
3. `zolt-self-host`: Zolt building Zolt, kept separate from competitor claims.

Only the first lane is suitable for broad scaling claims at first. Real-project
lanes become publishable comparisons only after the adapter builds the same
meaningful source set as the native build.

## Enterprise Workload

The generated fixture now supports `wide`, `layered`, and `chain` graphs plus
configurable classes and methods per module. That fixes the old serial-chain
benchmark as the only generated shape, but it is still only the foundation of
an enterprise-like workload. The target fixture should eventually look more
like a large internal Java platform:

- 200 to 500 modules plus multiple apps;
- layered modules instead of a single chain;
- shared platform modules, feature modules, adapters, clients, and test fixtures;
- Java 21 sources with many classes per module, not one class per module;
- compile dependencies, runtime dependencies, provided dependencies, BOMs, and
  annotation processors;
- resources, generated sources, and test sources;
- at least one Spring Boot style app and one plain Java service app;
- jar packaging for libraries and runnable app packaging for apps.

The generator should be deterministic from a small JSON or TOML spec:

```text
enterprise-large:
  modules: 300
  apps: 4
  classesPerModule: 16
  testsPerModule: 4
  dependencyFanout: 3
  annotationProcessors: true
  generatedSources: true
  resources: true
  seed: 20260708
```

That gives us a benchmark we can scale up and down without committing huge
source trees to the repository.

## Workflows

Each lane should report these workflows independently:

- dependency setup or resolve;
- first clean build;
- warm no-op build;
- leaf source change;
- shared API change with broad fanout;
- resource-only change;
- test compile;
- test run;
- package.

The first clean build stays separate. The repeated workflows should use medians,
p95, min, max, and raw samples.

## Automation

The suite runner sits on top of the generated-workspace
`scripts/benchmark-competitors` script:

- `scripts/benchmark-suite` runs selected lanes and writes the overall suite
  summary;
- `scripts/benchmark-competitors` generates identical `wide`, `layered`, or
  `chain` source trees for Zolt, Maven, and Gradle;
- `scripts/benchmark-large-source` preserves the roughly 500,000-line source
  volume and ABI-change comparison as a specialist manual lane;
- `scripts/benchmark-netty-compare` creates an explicitly scoped Netty `common`
  specialist comparison while the full core-reactor adapter remains incomplete;
- `docs/benchmarks/projects.json` records five pinned, comparison-ready
  real-project lanes;
- `scripts/benchmark-enterprise-fixture` generates Zolt, Maven, and Gradle
  versions of the enterprise workload;
- `scripts/benchmark-real-project` checks out pinned upstream refs, applies
  adapters, and records upstream-tool and Zolt commands;
- `scripts/benchmark-openai-summary` turns the structured benchmark result into
  optional model-generated prose when `OPENAI_API_KEY` is configured.

The GitHub workflow should expose simple modes:

- `smoke`: small generated workload, one repeat, runs on push to this branch;
- `enterprise`: large generated workload, five to seven repeats, manual;
- `real-projects`: pinned real projects, required on pushes to the benchmark branch;
- `publishable`: enterprise plus stable real-project adapters, manual or
  scheduled after the workflow is proven.

Use released native Zolt for publishable runs. Building Zolt in the same job is
useful for branch validation, but it should not be mixed into public comparison
setup time.

## OpenAI Summary

The benchmark result remains the evidence. The OpenAI step is a summarizer, not
the source of truth.

The OpenAI step should:

- run only when `OPENAI_API_KEY` is present;
- use the Responses API;
- read `suite-summary.json`, not command logs unless explicitly requested;
- use structured outputs to produce `summary-ai.json`;
- write a human Markdown version to `summary-ai.md`;
- be non-blocking by default so benchmark evidence still publishes if the API is
  unavailable;
- record the model, reasoning effort, request id when available, and prompt
  version in the artifact;
- never include secrets, environment dumps, or full command logs in the prompt.

Suggested environment variables:

```text
OPENAI_API_KEY
OPENAI_MODEL=gpt-5.5
OPENAI_REASONING_EFFORT=high
BENCHMARK_AI_SUMMARY=true
```

Keep the model and reasoning effort configurable. Use `high` by default for
public benchmark prose where better evidence handling is worth the extra latency
and cost.

The first useful summary schema:

```json
{
  "headline": "string",
  "whatRan": "string",
  "bottomLine": "string",
  "resultBullets": ["string"],
  "methodologyNotes": ["string"],
  "limitations": ["string"]
}
```

The prompt should be strict: summarize only the supplied JSON, keep first clean
build separate, mention missing competitors only when data is actually missing,
avoid labels like evidence grade or publishable claims, and never include
follow-ups, recommendations, roadmap items, or backlog prose.

## Real Projects

Real projects should be split into two groups:

- `comparison-ready`: Zolt adapter covers the same meaningful source set;
- `upstream-baseline`: upstream Maven or Gradle timings only while the Zolt
  adapter is not ready.

Start with small and medium projects to prove the adapter flow, then add one
large enterprise-grade baseline:

- Spring PetClinic for familiar Spring application shape;
- Apache Commons CLI for a compact Maven library;
- HikariCP for a library with optional compile dependencies;
- Netty as the large Maven codebase benchmark, following the public Mill
  comparison shape: clean-all, single-module, incremental, and no-op workflows.

Large real projects are valuable, but they should not block the enterprise
fixture. The generated enterprise workload is the controllable centerpiece.

## Milestones

1. Add the suite runner and suite-level JSON contract for generated-workspace
   runs. Done for the first generated lane.
2. Add topology and source-volume controls to the generated lane. Done for
   `wide`, `layered`, and `chain`; the full enterprise fixture features listed
   above remain future work.
3. Add OpenAI summary script and wire it into CI behind `OPENAI_API_KEY`. Done
   for the current suite summary.
4. Add real-project checkout/adapters for Spring PetClinic, Commons CLI,
   HikariCP, Netty `common`, and JUnit Platform Commons. Done.
5. Add Netty as a conservative Zolt `common` subset comparison. Done; full
   core-reactor parity remains future work.
6. Publish a dated benchmark result under `docs/benchmarks/results/`.

Done means a manual GitHub run produces one clean artifact with raw samples,
report, deterministic summary, optional AI summary, and enough metadata for a
reader or LLM to reconstruct exactly what was measured.

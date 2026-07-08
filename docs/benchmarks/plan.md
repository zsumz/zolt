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

The current generated fixture is too simple: a chain of library modules plus one
app. The target fixture should look more like a large internal Java platform:

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

Add a suite runner on top of the current `scripts/benchmark-competitors` script:

- `scripts/benchmark-suite` reads a benchmark spec and runs all selected lanes;
- `scripts/benchmark-enterprise-fixture` generates Zolt, Maven, and Gradle
  versions of the enterprise workload;
- `scripts/benchmark-real-project` checks out pinned upstream refs, applies
  adapters, and records native-tool and Zolt commands;
- `scripts/benchmark-openai-summary` turns the structured benchmark result into
  optional model-generated prose when `OPENAI_API_KEY` is configured.

The GitHub workflow should expose simple modes:

- `smoke`: small generated workload, one repeat, runs on push to this branch;
- `enterprise`: large generated workload, five to seven repeats, manual;
- `real-projects`: pinned real projects, manual at first;
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
  "evidenceGrade": "string",
  "overall": "string",
  "laneSummaries": [
    {
      "lane": "string",
      "winner": "string",
      "scope": "string",
      "plainEnglish": "string",
      "evidence": "string",
      "methodologyNotes": ["string"]
    }
  ],
  "publishableClaims": ["string"],
  "limitations": ["string"]
}
```

The prompt should be strict: summarize only the supplied JSON, keep first clean
build separate, mention missing competitors only when data is actually missing,
and never include follow-ups, recommendations, roadmap items, or backlog prose.

## Real Projects

Real projects should be split into two groups:

- `comparison-ready`: Zolt adapter covers the same meaningful source set;
- `native-baseline`: native Maven or Gradle timings only while the Zolt adapter
  is not ready.

Start with small and medium projects to prove the adapter flow, then add one
large enterprise-grade baseline:

- Spring PetClinic for familiar Spring application shape;
- Apache Commons CLI for a compact Maven library;
- HikariCP for a Gradle library;
- Netty as the large Maven codebase benchmark, following the public Mill
  comparison shape: clean-all, single-module, incremental, and no-op workflows.

Large real projects are valuable, but they should not block the enterprise
fixture. The generated enterprise workload is the controllable centerpiece.

## Milestones

1. Add the enterprise fixture generator and make `smoke`/`enterprise` modes use
   it.
2. Add suite-level JSON: one file with run metadata, lane metadata, summaries,
   samples, and artifact paths.
3. Add OpenAI summary script and wire it into CI behind `OPENAI_API_KEY`.
4. Add real-project checkout/adapters for Spring PetClinic and Commons CLI.
5. Add Netty as the large native-baseline project, then work toward a Zolt
   adapter.
6. Publish a dated benchmark result under `docs/benchmarks/results/`.

Done means a manual GitHub run produces one clean artifact with raw samples,
report, deterministic summary, optional AI summary, and enough metadata for a
reader or LLM to reconstruct exactly what was measured.

# Netty Adapter Groundwork

Status: upstream Maven baseline only.

This directory is the reviewable home for the future Zolt overlay used by the
Netty benchmark lane. The current benchmark suite times Netty with its upstream
Maven build only; it must not be described as a Zolt comparison until this
directory contains a working adapter and coverage record for the same meaningful
source set.

Current upstream Maven baseline:

- upstream: `https://github.com/netty/netty`
- branch: `4.1`
- commit: `bb2ff68a1fb71cb4b0eb9a9e17b66c52aff680c6`
- upstream tool: Maven
- manifest: `docs/benchmarks/projects.json`

The first comparison adapter should be an overlay, not a fork:

- copy adapter files into the pinned upstream checkout during the benchmark run;
- leave upstream source untouched except for explicit benchmark source-change
  markers;
- record every included module and every omitted module;
- record whether JNI, generated sources, tests, integration tests, and packaging
  are covered;
- keep the Zolt commands and Maven commands side by side in the result artifact.

Initial target:

- match the upstream core Java reactor package lane in
  `docs/benchmarks/projects.json`;
- clean package for the core Java reactor;
- clean compile, warm no-op, and implementation-change workflows for `common`;
- no claims about tests, native/JNI packaging, or full Netty release packaging
  until those workflows are represented in the adapter.

Publication rule: every Netty result must ship this adapter directory, the
upstream Maven logs, raw samples, deterministic summary, and module coverage
notes.

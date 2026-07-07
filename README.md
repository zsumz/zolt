<p align="center">
  <img src="./logo.svg" alt="zolt" width="720">
</p>

<p align="center">
  <strong>A fast, self-hosted Java build tool.</strong>
</p>

<p align="center">
  A Cargo-like workflow for Java: one native binary, project-native builds, and
  reproducible dependency resolution.
</p>

<p align="center">
  <a href="#install">Install</a>
  <span> · </span>
  <a href="#example">Example</a>
  <span> · </span>
  <a href="#model">Model</a>
  <span> · </span>
  <a href="#features">Features</a>
</p>

<br />

## Install

```sh
curl -fsSL https://dist.zolt.sh/install.sh | sh
```

## Example

```sh
zolt init hello
cd hello

zolt add test org.junit.jupiter:junit-jupiter-api:5.11.4
zolt test
zolt package
```

This creates a small `zolt.toml`, resolves a reproducible `zolt.lock`, runs the
test suite, and packages the project.

## Model

Most Java builds start with a build-tool installation.

Zolt starts with a project and a single binary.

```txt
zolt.toml      project model
zolt.lock      resolved packages
zolt           build, test, package, release
```

Use it for Java projects that want fast local commands, workspace-aware builds,
and fewer moving parts between source and artifact.

## Features

```txt
single native binary
fast startup
Cargo-like project workflow
reproducible lockfile
workspace builds
JUnit test execution
jar packaging
native-image packaging
release archive verification
self-hosted builds
```

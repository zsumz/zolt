import { expect, smoke, type CommandOptions, type CommandResult, type PathRef, type SmokeContext } from "smoque";
import { chmod, cp, mkdir, readdir, readFile, rm, writeFile } from "node:fs/promises";
import { dirname, join } from "node:path";

const CACHE_ROOT = ".zolt/cache";
const CLI_MEMBER = "apps/zolt";
const JUNIT_WORKER_MEMBER = "apps/zolt-junit-worker";

interface ZoltRuntime {
  cacheRoot: string;
  command: string;
  env: Record<string, string>;
  root: PathRef;
}

smoke.suite("zolt packaged JVM smoke", { tags: ["jvm"] }, async (t: SmokeContext) => {
  const root = t.repoRoot();
  const work = await t.tempDir("zolt-jvm-smoke");
  let zolt: ZoltRuntime;

  await t.step("required tools are available", async () => {
    await t.tools.node({ minVersion: 22 });
    await t.tools.npm({ minVersion: 10 });
  });

  await t.step("package JUnit worker and CLI", async () => {
    zolt = await buildPackagedZolt(t, root);
  });

  await t.step("built CLI starts outside a project", async () => {
    const version = await runZolt(t, zolt, ["--no-progress", "--version"], { timeout: "30s" });
    expect.value(version.stdout.trim()).toMatch(/\S/u);

    const help = await runZolt(t, zolt, ["--no-progress"], { timeout: "30s" });
    expect.value(help.stdout).toContain("Usage: zolt");
  });

  await t.step("plain app resolves, tests, packages, and runs", async () => {
    const app = await copyFixture(root, work, "adoption-plain-app");

    await runZolt(t, zolt, ["--no-progress", "resolve", "--cwd", app, "--cache-root", zolt.cacheRoot]);
    await runZolt(t, zolt, ["--no-progress", "test", "--cwd", app, "--cache-root", zolt.cacheRoot]);
    await runZolt(t, zolt, ["--no-progress", "package", "--cwd", app, "--cache-root", zolt.cacheRoot]);
    const packagedApp = await singleJar(join(app, "target"));
    await expect.file(packagedApp).toExist();

    const result = await runZolt(t, zolt, [
      "--no-progress",
      "run-package",
      "--cwd",
      app,
      "--cache-root",
      zolt.cacheRoot,
      "--",
      "adoption",
    ]);
    expect.value(result.stdout).toContain("Hello from adoption!");
  });

  await t.step("stale lockfile failure explains changed repository inputs", async () => {
    const app = await copyFixture(root, work, "adoption-plain-app", "adoption-plain-app-stale");

    await runZolt(t, zolt, ["--no-progress", "resolve", "--cwd", app, "--cache-root", zolt.cacheRoot]);
    const toml = join(app, "zolt.toml");
    const original = await readFile(toml, "utf8");
    await writeFile(
      toml,
      original.replace("https://repo.maven.apache.org/maven2", "https://repo.maven.apache.org/maven2-alt"),
      "utf8",
    );

    await expectCommandFailureContains(
      t,
      zolt,
      ["--no-progress", "build", "--cwd", app, "--cache-root", zolt.cacheRoot],
      "Changed inputs: repositories.",
    );
  });

  await t.step("workspace root and nested member builds work", async () => {
    const workspace = await copyFixture(root, work, "workspace-app");

    await runZolt(t, zolt, [
      "--no-progress",
      "resolve",
      "--workspace",
      "--cwd",
      workspace,
      "--cache-root",
      zolt.cacheRoot,
    ]);
    await runZolt(t, zolt, [
      "--no-progress",
      "build",
      "--workspace",
      "--member",
      "apps/api",
      "--cwd",
      workspace,
      "--cache-root",
      zolt.cacheRoot,
    ]);
    await runZolt(t, zolt, [
      "--no-progress",
      "build",
      "--workspace",
      "--member",
      "apps/api",
      "--cwd",
      join(workspace, "apps/api"),
      "--cache-root",
      zolt.cacheRoot,
    ]);

    await expect.file(join(workspace, "apps/api/target/classes/com/example/workspace/api/ApiApplication.class")).toExist();
  });

  await t.step("workspace clean respects selected member boundaries", async () => {
    const workspace = await copyFixture(root, work, "workspace-app", "workspace-clean-app");

    await writeFile(
      join(workspace, "zolt.toml"),
      `[workspace]
name = "workspace-app"
members = ["apps/api", "modules/core", "apps/worker"]
defaultMembers = ["apps/api"]

[repositories]
central = "https://repo.maven.apache.org/maven2"
`,
      "utf8",
    );
    await mkdir(join(workspace, "apps/worker/src/main/java/com/example/workspace/worker"), { recursive: true });
    await writeFile(
      join(workspace, "apps/worker/zolt.toml"),
      `[project]
name = "worker"
version = "0.1.0"
group = "com.example.workspace"
java = "21"

[dependencies]

[build]
source = "src/main/java"
test = "src/test/java"
output = "target/classes"
testOutput = "target/test-classes"
`,
      "utf8",
    );
    await writeFile(
      join(workspace, "apps/worker/src/main/java/com/example/workspace/worker/Worker.java"),
      "package com.example.workspace.worker;\n\npublic final class Worker {\n}\n",
      "utf8",
    );
    await writeOutput(join(workspace, "modules/core/target/classes/com/example/workspace/core/Greeting.class"));
    await writeOutput(join(workspace, "apps/api/target/classes/com/example/workspace/api/ApiApplication.class"));
    await writeOutput(join(workspace, "apps/worker/target/classes/com/example/workspace/worker/Worker.class"));
    await writeOutput(join(workspace, "target/root-report/report.txt"));
    await writeOutput(join(workspace, ".zolt/cache/artifact.jar"));
    await writeOutput(join(workspace, "apps/api/.zolt/cache/artifact.jar"));
    await writeFile(join(workspace, "zolt.lock"), "# smoke lockfile\n", "utf8");

    const result = await runZolt(t, zolt, [
      "--no-progress",
      "clean",
      "--workspace",
      "--member",
      "apps/api",
      "--cwd",
      workspace,
    ]);
    expect.value(result.stdout).toContain("Deleted 2 workspace build output paths across 2 members");
    expect.value(result.stdout).toContain("Deleted modules/core ");
    expect.value(result.stdout).toContain("Deleted apps/api ");

    await expect.file(join(workspace, "modules/core/target")).notToExist();
    await expect.file(join(workspace, "apps/api/target")).notToExist();
    await expect.file(join(workspace, "apps/worker/target/classes/com/example/workspace/worker/Worker.class")).toExist();
    await expect.file(join(workspace, "target/root-report/report.txt")).toExist();
    await expect.file(join(workspace, "zolt.lock")).toExist();
    await expect.file(join(workspace, ".zolt/cache/artifact.jar")).toExist();
    await expect.file(join(workspace, "apps/api/.zolt/cache/artifact.jar")).toExist();
    await expect.file(join(workspace, "apps/api/src/main/java/com/example/workspace/api/ApiApplication.java")).toExist();
    await expect.file(join(workspace, "modules/core/src/main/java/com/example/workspace/core/Greeting.java")).toExist();
  });

  await t.step("JUnit worker selectors run through the packaged worker", async () => {
    const project = await copyFixture(root, work, "junit-basic");

    await runZolt(t, zolt, ["--no-progress", "resolve", "--cwd", project, "--cache-root", zolt.cacheRoot]);
    await expectTestsFound(t, zolt, 2, [
      "--no-progress",
      "test",
      "--cwd",
      project,
      "--cache-root",
      zolt.cacheRoot,
      "--test",
      "com.example.MainTest",
    ]);
    await expectTestsFound(t, zolt, 1, [
      "--no-progress",
      "test",
      "--cwd",
      project,
      "--cache-root",
      zolt.cacheRoot,
      "--test",
      "com.example.MainTest#addsNumbers",
    ]);
    await expectTestsFound(t, zolt, 1, [
      "--no-progress",
      "test",
      "--cwd",
      project,
      "--cache-root",
      zolt.cacheRoot,
      "--tests",
      "*GreetingTest",
    ]);
    await expectTestsFound(t, zolt, 2, [
      "--no-progress",
      "test",
      "--cwd",
      project,
      "--cache-root",
      zolt.cacheRoot,
      "--include-tag",
      "fast",
    ]);
    await expectTestsFound(t, zolt, 3, [
      "--no-progress",
      "test",
      "--cwd",
      project,
      "--cache-root",
      zolt.cacheRoot,
      "--exclude-tag",
      "slow",
    ]);
  });

  await t.step("missing config failure names the expected zolt.toml", async () => {
    const missingConfig = work.path("missing-config");
    await mkdir(missingConfig, { recursive: true });

    await expectCommandFailureContains(
      t,
      zolt,
      ["--no-progress", "build", "--cwd", missingConfig, "--cache-root", zolt.cacheRoot],
      `Could not read zolt.toml at ${missingConfig}/zolt.toml`,
    );
  });

  await t.step("release archive version override stamps artifacts", async () => {
    const project = work.path("release-project");
    const target = "linux-x64";
    const version = "0.1.0-nightly.20260704.0123456789ab";
    const rootDirectory = `zolt-${version}-${target}`;
    const archivePath = join(project, "dist", `${rootDirectory}.tar.gz`);

    await mkdir(join(project, "bin"), { recursive: true });
    await mkdir(join(project, "target/libexec"), { recursive: true });
    await writeFile(
      join(project, "zolt.toml"),
      `[project]
name = "zolt"
version = "0.1.0-SNAPSHOT"
group = "sh.zolt"
java = "21"
`,
      "utf8",
    );
    await writeFile(join(project, "bin/zolt-stub"), "#!/usr/bin/env bash\nexit 0\n", "utf8");
    await chmod(join(project, "bin/zolt-stub"), 0o755);
    await writeFile(join(project, "target/libexec/zolt-junit-worker.jar"), "worker\n", "utf8");

    await runZolt(t, zolt, [
      "--no-progress",
      "release-archive",
      "--directory",
      project,
      "--target",
      target,
      "--binary",
      "bin/zolt-stub",
      "--output",
      "dist",
    ], {
      env: {
        ZOLT_VERSION_OVERRIDE: version,
      },
    });

    await expect.file(archivePath).toExist();
    await expect.archive(archivePath).toContainEntries([
      `${rootDirectory}/bin/zolt`,
      `${rootDirectory}/libexec/zolt-junit-worker.jar`,
      `${rootDirectory}/VERSION`,
    ]);

    const embeddedVersion = await t.cmd("tar", ["-xzOf", archivePath, `${rootDirectory}/VERSION`], { cwd: root });
    expect.value(embeddedVersion.stdout.trim()).toBe(version);

    const manifest = JSON.parse(await readFile(join(project, "dist/release-manifest.json"), "utf8"));
    expect.value(manifest.version).toBe(version);

    const reportedVersion = await runZolt(t, zolt, ["--version"], {
      timeout: "30s",
      env: {
        ZOLT_VERSION_OVERRIDE: version,
      },
    });
    expect.value(reportedVersion.stdout.trim()).toBe(version);
  });
});

async function buildPackagedZolt(t: SmokeContext, root: PathRef): Promise<ZoltRuntime> {
  const cacheRoot = root.path(CACHE_ROOT);

  await t.cmd(script(root, "bootstrap-zolt-jvm"), [
    "--no-progress",
    "package",
    "--workspace",
    "--member",
    JUNIT_WORKER_MEMBER,
    "--cache-root",
    cacheRoot,
  ], { cwd: root, timeout: "10m" });
  await t.cmd(script(root, "bootstrap-zolt-jvm"), [
    "--no-progress",
    "package",
    "--workspace",
    "--member",
    CLI_MEMBER,
    "--cache-root",
    cacheRoot,
  ], { cwd: root, timeout: "10m" });

  const jar = await singleJar(root.path(CLI_MEMBER, "target"));
  const workerJar = await singleJar(root.path(JUNIT_WORKER_MEMBER, "target"));
  const runtimeClasspathFile = jar.replace(/\.jar$/u, ".runtime-classpath");
  await expect.file(runtimeClasspathFile).toExist();

  return {
    cacheRoot,
    command: script(root, "run-zolt-built"),
    env: {
      ZOLT_JAR: jar,
      ZOLT_RUNTIME_CLASSPATH_FILE: runtimeClasspathFile,
      ZOLT_JUNIT_WORKER_JAR: workerJar,
    },
    root,
  };
}

async function copyFixture(
  root: PathRef,
  work: PathRef,
  fixtureName: string,
  destinationName = fixtureName,
): Promise<string> {
  const destination = work.path(destinationName);
  await rm(destination, { recursive: true, force: true });
  await cp(root.path("examples", fixtureName), destination, { recursive: true });
  await pruneGeneratedState(destination);
  return destination;
}

async function pruneGeneratedState(directory: string): Promise<void> {
  let entries;
  try {
    entries = await readdir(directory, { withFileTypes: true });
  } catch (error) {
    if (hasErrorCode(error, "ENOENT")) {
      return;
    }
    throw error;
  }

  for (const entry of entries) {
    const path = join(directory, entry.name);
    if ((entry.name === "target" || entry.name === ".zolt") && entry.isDirectory()) {
      await rm(path, { recursive: true, force: true });
    } else if (entry.name === "zolt.lock" && entry.isFile()) {
      await rm(path, { force: true });
    } else if (entry.isDirectory()) {
      await pruneGeneratedState(path);
    }
  }
}

async function expectTestsFound(
  t: SmokeContext,
  zolt: ZoltRuntime,
  expected: number,
  args: string[],
): Promise<void> {
  const result = await runZolt(t, zolt, args);
  const output = `${result.stdout}\n${result.stderr}`;
  expect.value(output).toMatch(new RegExp(`(Tests found: ${expected}|${expected} tests found)`, "u"));
  expect.value(output).toMatch(/(Tests succeeded:|Tests passed)/u);
}

async function expectCommandFailureContains(
  t: SmokeContext,
  zolt: ZoltRuntime,
  args: string[],
  expected: string,
): Promise<void> {
  const result = await runZolt(t, zolt, args, { check: false });
  if (result.exitCode === 0) {
    t.fail(`Expected command to fail: ${[zolt.command, ...args].join(" ")}`);
  }
  expect.value(`${result.stdout}\n${result.stderr}`).toContain(expected);
}

async function runZolt(
  t: SmokeContext,
  zolt: ZoltRuntime,
  args: string[],
  options: CommandOptions = {},
): Promise<CommandResult> {
  const { env, timeout, ...rest } = options;
  return await t.cmd(zolt.command, args, {
    cwd: zolt.root,
    env: {
      ...zolt.env,
      ...env,
    },
    timeout: timeout ?? "3m",
    ...rest,
  });
}

async function singleJar(directory: string): Promise<string> {
  const jars = (await readdir(directory))
    .filter((name) => name.endsWith(".jar"))
    .filter((name) => !name.endsWith("-sources.jar") && !name.endsWith("-javadoc.jar"))
    .sort();
  if (jars.length !== 1) {
    throw new Error(`Expected exactly one jar under ${directory}, found ${jars.length}: ${jars.join(", ")}`);
  }
  return join(directory, jars[0]);
}

async function writeOutput(path: string): Promise<void> {
  await mkdir(dirname(path), { recursive: true });
  await writeFile(path, "output\n", "utf8");
}

function script(root: PathRef, name: string): string {
  return root.path("scripts", name);
}

function hasErrorCode(error: unknown, code: string): boolean {
  return typeof error === "object"
    && error !== null
    && "code" in error
    && (error as { code?: unknown }).code === code;
}

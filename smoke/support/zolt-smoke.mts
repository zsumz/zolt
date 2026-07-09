import { expect, type CommandOptions, type CommandResult, type PathRef, type SmokeContext } from "smoque";
import { cp, mkdir, readdir, rm, writeFile } from "node:fs/promises";
import { dirname, join } from "node:path";

const CACHE_ROOT = ".zolt/cache";
const CLI_MEMBER = "apps/zolt";
const JUNIT_WORKER_MEMBER = "apps/zolt-junit-worker";
const PREBUILT_ENV = "ZOLT_SMOKE_PREBUILT";

export interface ZoltRuntime {
  cacheRoot: string;
  command: string;
  env: Record<string, string>;
  root: PathRef;
}

let packagedRuntime: Promise<ZoltRuntime> | undefined;

export async function packagedZolt(t: SmokeContext): Promise<ZoltRuntime> {
  const cached = packagedRuntime;
  if (cached) {
    return await t.step("reuse packaged JUnit worker and CLI", async () => await cached);
  }

  const usePrebuilt = process.env[PREBUILT_ENV] === "1";
  packagedRuntime = t.step(usePrebuilt ? "use prebuilt JUnit worker and CLI" : "package JUnit worker and CLI", async () => {
    await t.tools.node({ minVersion: 22 });
    await t.tools.npm({ minVersion: 10 });
    if (usePrebuilt) {
      return await usePackagedZolt(t, t.repoRoot());
    }
    return await buildPackagedZolt(t, t.repoRoot());
  });
  return await packagedRuntime;
}

export async function runZolt(
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

export async function copyFixture(
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

export async function expectTestsFound(
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

export async function expectCommandFailureContains(
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

export async function singleJar(directory: string): Promise<string> {
  const jars = (await readdir(directory))
    .filter((name) => name.endsWith(".jar"))
    .filter((name) => !name.endsWith("-sources.jar") && !name.endsWith("-javadoc.jar"))
    .sort();
  if (jars.length !== 1) {
    throw new Error(`Expected exactly one jar under ${directory}, found ${jars.length}: ${jars.join(", ")}`);
  }
  return join(directory, jars[0]);
}

export async function writeOutput(path: string): Promise<void> {
  await mkdir(dirname(path), { recursive: true });
  await writeFile(path, "output\n", "utf8");
}

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

  return await usePackagedZolt(t, root);
}

async function usePackagedZolt(t: SmokeContext, root: PathRef): Promise<ZoltRuntime> {
  const cacheRoot = root.path(CACHE_ROOT);
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

function script(root: PathRef, name: string): string {
  return root.path("scripts", name);
}

function hasErrorCode(error: unknown, code: string): boolean {
  return typeof error === "object"
    && error !== null
    && "code" in error
    && (error as { code?: unknown }).code === code;
}

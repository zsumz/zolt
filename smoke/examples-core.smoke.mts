import { expect, smoke, type SmokeContext } from "smoque";
import { readdir } from "node:fs/promises";
import { join } from "node:path";

import {
  copyFixture,
  expectTestsFound,
  packagedZolt,
  runZolt,
} from "./support/zolt-smoke.mts";

interface ProjectExample {
  readonly fixture: string;
  readonly expectedTests?: number;
  readonly package?: boolean;
  readonly runPackageOutput?: RegExp;
}

interface WorkspaceExample {
  readonly fixture: string;
  readonly member: string;
  readonly expectedTests?: number;
  readonly package?: boolean;
  readonly runPackageOutput?: RegExp;
}

const PROJECT_EXAMPLES: ProjectExample[] = [
  {
    fixture: "hello-zolt",
    package: true,
    runPackageOutput: /Hello from Zolt/u,
  },
  {
    fixture: "provided-container-api",
    package: true,
    runPackageOutput: /provided-container-api fixture ran/u,
  },
  {
    fixture: "commons-cli-canary",
    expectedTests: 3,
    package: true,
  },
  {
    fixture: "hikaricp-canary",
    expectedTests: 3,
    package: true,
  },
  {
    fixture: "junit-vintage",
    expectedTests: 1,
    package: true,
  },
  {
    fixture: "exec-jvm-canary",
    expectedTests: 1,
    package: true,
    runPackageOutput: /exec-jvm-canary version 1\.4\.2 \(exec-project\)/u,
  },
  {
    fixture: "exec-process-canary",
    expectedTests: 1,
    package: true,
    runPackageOutput: /Hello from exec-process-canary :: exec-process/u,
  },
];

const WORKSPACE_EXAMPLES: WorkspaceExample[] = [
  {
    fixture: "large-workspace",
    member: "apps/app",
    expectedTests: 1,
    package: true,
    runPackageOutput: /large-workspace:/u,
  },
  {
    fixture: "slf4j-canary",
    member: "slf4j-simple",
    expectedTests: 3,
    package: true,
  },
];

smoke.suite("zolt core examples smoke", { tags: ["examples", "examples-core"] }, async (t: SmokeContext) => {
  const root = t.repoRoot();
  const work = await t.tempDir("zolt-examples-core");
  const zolt = await packagedZolt(t);

  for (const example of PROJECT_EXAMPLES) {
    await t.step(`${example.fixture} resolves and runs its expected lifecycle`, async () => {
      const project = await copyFixture(root, work, example.fixture);

      await runZolt(t, zolt, ["--no-progress", "resolve", "--cwd", project, "--cache-root", zolt.cacheRoot]);
      if (example.expectedTests !== undefined) {
        await expectTestsFound(t, zolt, example.expectedTests, [
          "--no-progress",
          "test",
          "--cwd",
          project,
          "--cache-root",
          zolt.cacheRoot,
        ]);
      }
      await runZolt(t, zolt, ["--no-progress", "build", "--cwd", project, "--cache-root", zolt.cacheRoot]);
      if (example.package) {
        await runZolt(t, zolt, ["--no-progress", "package", "--cwd", project, "--cache-root", zolt.cacheRoot]);
        await expectPrimaryJar(t, join(project, "target"));
      }
      if (example.runPackageOutput !== undefined) {
        const result = await runZolt(t, zolt, [
          "--no-progress",
          "run-package",
          "--cwd",
          project,
          "--cache-root",
          zolt.cacheRoot,
        ]);
        expect.value(result.stdout).toMatch(example.runPackageOutput);
      }
    });
  }

  for (const example of WORKSPACE_EXAMPLES) {
    await t.step(`${example.fixture} workspace member resolves and runs its expected lifecycle`, async () => {
      const workspace = await copyFixture(root, work, example.fixture);

      await runZolt(t, zolt, [
        "--no-progress",
        "resolve",
        "--workspace",
        "--cwd",
        workspace,
        "--cache-root",
        zolt.cacheRoot,
      ]);
      if (example.expectedTests !== undefined) {
        await expectTestsFound(t, zolt, example.expectedTests, [
          "--no-progress",
          "test",
          "--workspace",
          "--member",
          example.member,
          "--cwd",
          workspace,
          "--cache-root",
          zolt.cacheRoot,
        ]);
      }
      await runZolt(t, zolt, [
        "--no-progress",
        "build",
        "--workspace",
        "--member",
        example.member,
        "--cwd",
        workspace,
        "--cache-root",
        zolt.cacheRoot,
      ]);
      if (example.package) {
        await runZolt(t, zolt, [
          "--no-progress",
          "package",
          "--workspace",
          "--member",
          example.member,
          "--cwd",
          workspace,
          "--cache-root",
          zolt.cacheRoot,
        ]);
        await expectPrimaryJar(t, join(workspace, example.member, "target"));
      }
      if (example.runPackageOutput !== undefined) {
        const result = await runZolt(t, zolt, [
          "--no-progress",
          "run-package",
          "--workspace",
          "--member",
          example.member,
          "--cwd",
          workspace,
          "--cache-root",
          zolt.cacheRoot,
        ]);
        expect.value(result.stdout).toMatch(example.runPackageOutput);
      }
    });
  }
});

async function expectPrimaryJar(t: SmokeContext, directory: string): Promise<void> {
  const jars = (await readdir(directory))
    .filter((name) => name.endsWith(".jar"))
    .filter((name) => !name.endsWith("-sources.jar") && !name.endsWith("-javadoc.jar") && !name.endsWith("-tests.jar"))
    .sort();
  if (jars.length !== 1) {
    t.fail(`Expected exactly one primary jar under ${directory}, found ${jars.length}: ${jars.join(", ")}`);
  }
  await expect.file(join(directory, jars[0])).toExist();
}

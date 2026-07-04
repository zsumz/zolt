import { smoke, type SmokeContext } from "smoque";

import { copyFixture, expectTestsFound, packagedZolt, runZolt } from "./support/zolt-smoke.mts";

smoke.suite("zolt JUnit worker smoke", { tags: ["jvm", "junit"] }, async (t: SmokeContext) => {
  const root = t.repoRoot();
  const work = await t.tempDir("zolt-junit-smoke");
  const zolt = await packagedZolt(t);

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
});

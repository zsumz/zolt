import { expect, smoke, type CommandOptions, type CommandResult, type SmokeContext } from "smoque";
import { chmod, mkdir, readFile, writeFile } from "node:fs/promises";
import { join, resolve } from "node:path";
import { pathToFileURL } from "node:url";

interface ChannelManifest {
  channel: string;
  version: string;
}

smoke.suite("zolt published release update smoke", { tags: ["release", "release-update"] }, async (t: SmokeContext) => {
  const work = await t.tempDir("zolt-release-update-smoke");
  const installRoot = work.path("install");
  const currentInstallRoot = work.path("current-install");
  const target = process.env.ZOLT_RELEASE_UPDATE_TARGET ?? "linux-x64";
  const origin = requiredEnv("ZOLT_DISTRIBUTION_ORIGIN").replace(/\/+$/u, "");
  const expectedVersion = requiredEnv("ZOLT_RELEASE_UPDATE_EXPECTED_VERSION");
  const previousChannelPath = requiredEnv("ZOLT_RELEASE_UPDATE_PREVIOUS_CHANNEL");
  const currentChannelUrl = process.env.ZOLT_RELEASE_UPDATE_CHANNEL_URL ?? `${origin}/channels/zap.json`;
  const installScriptUrl = process.env.ZOLT_RELEASE_UPDATE_INSTALL_SCRIPT_URL ?? `${origin}/install.sh`;
  const installScript = work.path("install-zolt");

  let previousManifest: ChannelManifest | undefined;
  let currentManifest: ChannelManifest | undefined;

  await t.step("download public installer and verify published channel", async () => {
    await downloadTextToFile(installScriptUrl, installScript);
    await chmod(installScript, 0o755);

    previousManifest = JSON.parse(await readFile(previousChannelPath, "utf8")) as ChannelManifest;
    currentManifest = await waitForPublishedChannel(currentChannelUrl, expectedVersion);
    const currentSignature = await readUrl(`${currentChannelUrl}.sig`);

    expect.value(previousManifest.channel).toBe("zap");
    expect.value(currentManifest.channel).toBe("zap");
    expect.value(currentManifest.version).toBe(expectedVersion);
    expect.value(currentSignature).toContain("version: zolt-ed25519-v1");
    expect.value(currentSignature).toContain("keyId: zolt-release-2026");
    expect.value(currentSignature).toContain("signature:");
  });

  await t.step("install previous public zap build", async () => {
    const previous = requireManifest(previousManifest, "previous zap channel");
    if (previous.version === expectedVersion) {
      await installFromChannel(t, installScript, currentInstallRoot, currentChannelUrl, target);
      const currentVersion = await zolt(t, currentInstallRoot, ["--version"]);
      expect.value(currentVersion.stdout.trim()).toBe(expectedVersion);
      return;
    }

    await installFromChannel(t, installScript, installRoot, pathToFileURL(resolve(previousChannelPath)).href, target);
    const previousVersion = await zolt(t, installRoot, ["--version"]);
    expect.value(previousVersion.stdout.trim()).toBe(previous.version);
  });

  await t.step("self-update to the newly published zap build", async () => {
    const previous = requireManifest(previousManifest, "previous zap channel");
    if (previous.version === expectedVersion) {
      const versions = await zoltSelf(t, currentInstallRoot, ["versions"]);
      expect.value(versions.stdout).toContain(expectedVersion);
      return;
    }

    const selfHelp = await zolt(t, installRoot, ["self", "--help"], { check: false });
    if (selfHelp.exitCode !== 0) {
      await installFromChannel(t, installScript, currentInstallRoot, currentChannelUrl, target);
      const currentVersion = await zolt(t, currentInstallRoot, ["--version"]);
      expect.value(currentVersion.stdout.trim()).toBe(expectedVersion);
      const versions = await zoltSelf(t, currentInstallRoot, ["versions"]);
      expect.value(versions.stdout).toContain(expectedVersion);
      return;
    }

    await writeFile(join(installRoot, "channel-url"), `${currentChannelUrl}\n`, "utf8");
    await zoltSelf(t, installRoot, ["update"]);

    const updatedVersion = await zolt(t, installRoot, ["--version"]);
    expect.value(updatedVersion.stdout.trim()).toBe(expectedVersion);

    const versions = await zoltSelf(t, installRoot, ["versions"]);
    expect.value(versions.stdout).toContain(previous.version);
    expect.value(versions.stdout).toContain(expectedVersion);
    expect.value(versions.stdout).toContain(`* ${expectedVersion} current`);
  });

  await t.step("rollback and reselect the published build", async () => {
    const previous = requireManifest(previousManifest, "previous zap channel");
    if (previous.version === expectedVersion) {
      return;
    }

    const selfHelp = await zolt(t, installRoot, ["self", "--help"], { check: false });
    if (selfHelp.exitCode !== 0) {
      return;
    }

    await zoltSelf(t, installRoot, ["rollback"]);
    const rolledBackVersion = await zolt(t, installRoot, ["--version"]);
    expect.value(rolledBackVersion.stdout.trim()).toBe(previous.version);

    await zoltSelf(t, installRoot, ["use", expectedVersion]);
    const restoredVersion = await zolt(t, installRoot, ["--version"]);
    expect.value(restoredVersion.stdout.trim()).toBe(expectedVersion);
  });
});

function requireManifest(manifest: ChannelManifest | undefined, label: string): ChannelManifest {
  if (manifest === undefined) {
    throw new Error(`${label} manifest was not loaded.`);
  }
  return manifest;
}

async function installFromChannel(
  t: SmokeContext,
  installScript: string,
  installRoot: string,
  channelUrl: string,
  target: string,
): Promise<void> {
  await mkdir(installRoot, { recursive: true });
  await t.cmd(installScript, [], {
    env: {
      ZOLT_INSTALL_ROOT: installRoot,
      ZOLT_INSTALL_CHANNEL_URL: channelUrl,
      ZOLT_INSTALL_TARGET: target,
    },
    timeout: "5m",
  });
}

async function zolt(
  t: SmokeContext,
  installRoot: string,
  args: string[],
  options: CommandOptions = {},
): Promise<CommandResult> {
  const binary = join(installRoot, "bin", "zolt");
  return await t.cmd(binary, args, {
    timeout: "2m",
    ...options,
  });
}

async function zoltSelf(t: SmokeContext, installRoot: string, args: string[]): Promise<CommandResult> {
  return await zolt(t, installRoot, ["self", ...args, "--install-root", installRoot]);
}

async function waitForPublishedChannel(url: string, expectedVersion: string): Promise<ChannelManifest> {
  let lastVersion = "";
  for (let attempt = 0; attempt < 12; attempt++) {
    const manifest = JSON.parse(await readUrl(url)) as ChannelManifest;
    lastVersion = manifest.version;
    if (manifest.version === expectedVersion) {
      return manifest;
    }
    await sleep(10_000);
  }
  throw new Error(`Published channel ${url} did not reach ${expectedVersion}; last version was ${lastVersion}.`);
}

async function downloadTextToFile(url: string, output: string): Promise<void> {
  await writeFile(output, await readUrl(url), "utf8");
}

async function readUrl(url: string): Promise<string> {
  if (url.startsWith("file:")) {
    return await readFile(new URL(url), "utf8");
  }
  const response = await fetch(url, { cache: "no-store" });
  if (!response.ok) {
    throw new Error(`Could not download ${url}: HTTP ${response.status}.`);
  }
  return await response.text();
}

function requiredEnv(name: string): string {
  const value = process.env[name];
  if (value === undefined || value.trim() === "") {
    throw new Error(`${name} is required for release update smoke.`);
  }
  return value;
}

async function sleep(milliseconds: number): Promise<void> {
  await new Promise((resolve) => {
    setTimeout(resolve, milliseconds);
  });
}

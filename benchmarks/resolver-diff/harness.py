#!/usr/bin/env python3
"""Differential dependency-resolution harness: zolt vs Maven.

For each root coordinate G:A:V:
  1. Maven side: a single-dependency pom.xml -> `mvn dependency:list`
  2. Zolt side:  `zolt init` + `zolt add G:A:V` (auto-resolve) -> parse zolt.lock
  3. Diff the resolved (group:artifact -> version/scope) maps and classify each
     divergence against zolt's *intended* semantics.

Divergence classes:
  match                          same version, same scope bucket
  version-diff/expected-newest-wins  zolt strictly newer (intended mediation model)
  version-diff/INVESTIGATE       zolt older or uncomparable (newest-wins can't explain)
  scope-diff                     same version, different compile/runtime bucket
  only-maven/INVESTIGATE         Maven resolved it, zolt.lock lacks it
  only-zolt/framework-injection  zolt-only package explained by a recorded policy
  only-zolt/INVESTIGATE          zolt-only package with no recorded explanation
  zolt-hard-fail/intended-*      zolt refused for a documented-in-code reason
  zolt-hard-fail/INVESTIGATE     zolt refused for an unrecognized reason
  maven-fail                     Maven itself could not resolve the root

Stdlib only. Uses tomllib when available (py>=3.11) with a regex fallback
tuned to zolt's machine-generated lockfile shape.
"""

import json
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

ZOLT = os.environ.get(
    "ZOLT_BIN", str(Path(__file__).resolve().parents[2] / "apps/zolt/target/native/zolt"))
MVN = os.environ.get("MVN_BIN") or shutil.which("mvn") or "/opt/homebrew/bin/mvn"
SUBPROC_TIMEOUT = 300

POM_TEMPLATE = """<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>diff.harness</groupId>
  <artifactId>probe</artifactId>
  <version>0.0.1</version>
  <packaging>jar</packaging>
  <properties>
    <maven.compiler.release>21</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>
  <dependencies>
    <dependency>
      <groupId>{group}</groupId>
      <artifactId>{artifact}</artifactId>
      <version>{version}</version>
    </dependency>
  </dependencies>
</project>
"""

# stderr/stdout patterns -> intended-divergence tags (each backed by zolt code+tests)
HARD_FAIL_PATTERNS = [
    (r"[Vv]ersion ranges are not supported", "intended-no-version-ranges"),
    (r"SNAPSHOT", "intended-no-external-snapshots"),
    (r"Supported scopes are", "intended-scope-model"),
    (r"[Ww]ildcard dependency exclusions", "intended-no-wildcard-policy-exclusions"),
    (r"[Ii]nterpolat|\$\{", "intended-no-uninterpolated-versions"),
]

QUALIFIER_RANK = {
    "alpha": 1, "a": 1, "beta": 2, "b": 2, "milestone": 3, "m": 3,
    "rc": 4, "cr": 4, "snapshot": 5,
    "": 6, "ga": 6, "final": 6, "release": 6, "sp": 7,
}


def tokenize_version(version):
    parts = re.split(r"[.\-_]", version.lower())
    tokens = []
    for part in parts:
        for chunk in re.findall(r"\d+|[a-z]+", part):
            tokens.append(int(chunk) if chunk.isdigit() else chunk)
    return tokens


def compare_versions(left, right):
    """Return (cmp, certain). cmp: -1/0/1 for left vs right; certain=False when
    an unknown qualifier makes Maven-order ambiguous (caller -> INVESTIGATE)."""
    lt, rt = tokenize_version(left), tokenize_version(right)
    certain = True
    for i in range(max(len(lt), len(rt))):
        a = lt[i] if i < len(lt) else ""
        b = rt[i] if i < len(rt) else ""
        if a == b:
            continue
        a_num, b_num = isinstance(a, int), isinstance(b, int)
        if a_num and b_num:
            return (1 if a > b else -1), certain
        if a_num != b_num:
            # numeric beats qualifier ("1.0.1" > "1.0-rc")
            if not (a_num or a in QUALIFIER_RANK) or not (b_num or b in QUALIFIER_RANK):
                certain = False
            return (1 if a_num else -1), certain
        ra, rb = QUALIFIER_RANK.get(a), QUALIFIER_RANK.get(b)
        if ra is None or rb is None:
            return (1 if str(a) > str(b) else -1), False
        if ra != rb:
            return (1 if ra > rb else -1), certain
    return 0, certain


def slugify(coordinate):
    return coordinate.replace(":", "__").replace("/", "_")


def run_command(cmd, cwd, timeout=SUBPROC_TIMEOUT):
    try:
        proc = subprocess.run(
            cmd, cwd=str(cwd), capture_output=True, text=True, timeout=timeout)
        return proc.returncode, proc.stdout, proc.stderr
    except subprocess.TimeoutExpired:
        return -1, "", f"TIMEOUT after {timeout}s: {' '.join(map(str, cmd))}"


# ---------------------------------------------------------------- Maven side

def maven_resolve(root, workdir):
    """Return (packages, error). packages: {(g,a): {version, scope}}"""
    mdir = workdir / "maven"
    mdir.mkdir(parents=True, exist_ok=True)
    group, artifact, version = root.split(":")
    (mdir / "pom.xml").write_text(
        POM_TEMPLATE.format(group=group, artifact=artifact, version=version))
    out_file = mdir / "deps.txt"
    rc, stdout, stderr = run_command(
        [MVN, "-B", "-q", f"-DoutputFile={out_file}", "dependency:list"], mdir)
    if rc != 0:
        return None, f"mvn exit {rc}: {(stdout + stderr)[-2000:]}"
    if not out_file.is_file():
        return None, "mvn produced no deps.txt"
    packages = {}
    for line in out_file.read_text().splitlines():
        line = line.strip().split(" -- ")[0].strip()
        fields = line.split(":")
        if len(fields) == 5:
            g, a, _type, v, scope = fields
        elif len(fields) == 6:
            g, a, _type, _classifier, v, scope = fields
        else:
            continue
        if not g or " " in g:
            continue
        # keep strongest scope if an artifact shows up twice (classifiers)
        prior = packages.get((g, a))
        if prior is None or (prior["scope"] != "compile" and scope == "compile"):
            packages[(g, a)] = {"version": v, "scope": scope}
    return packages, None


# ----------------------------------------------------------------- Zolt side

def parse_lock_fallback(text):
    """Minimal parser for zolt's machine-generated lockfile TOML."""
    tables = {"package": [], "conflict": [], "policy": []}
    current = None
    for line in text.splitlines():
        line = line.strip()
        header = re.fullmatch(r"\[\[(\w+)\]\]", line)
        if header:
            current = {}
            tables.setdefault(header.group(1), []).append(current)
            continue
        m = re.fullmatch(r'(\w+) = (.+)', line)
        if not m or current is None:
            continue
        key, raw = m.groups()
        if raw.startswith("["):
            current[key] = re.findall(r'"((?:[^"\\]|\\.)*)"', raw)
        elif raw.startswith('"'):
            current[key] = raw[1:-1].encode().decode("unicode_escape")
        elif raw in ("true", "false"):
            current[key] = raw == "true"
        else:
            try:
                current[key] = int(raw)
            except ValueError:
                current[key] = raw
    return tables


def parse_lockfile(path):
    text = path.read_text()
    try:
        import tomllib
        data = tomllib.loads(text)
    except ModuleNotFoundError:
        data = parse_lock_fallback(text)
    return (data.get("package") or [], data.get("conflict") or [],
            data.get("policy") or [])


def zolt_resolve(root, workdir):
    """Return (packages, conflicts, policies, error).
    packages: {(g,a): {version, scopes, direct, policies}}"""
    zdir = workdir / "zolt"
    zdir.mkdir(parents=True, exist_ok=True)
    rc, stdout, stderr = run_command(
        [ZOLT, "init", "proj", "--group", "diff.harness", "-q", "--no-progress"], zdir)
    if rc != 0:
        return None, None, None, f"zolt init exit {rc}: {(stdout + stderr)[-2000:]}"
    proj = zdir / "proj"
    rc, stdout, stderr = run_command(
        [ZOLT, "add", root, "--directory", str(proj), "-q", "--no-progress"], zdir)
    if rc != 0:
        return None, None, None, f"zolt add exit {rc}: {(stdout + stderr)[-2000:]}"
    lock_path = proj / "zolt.lock"
    if not lock_path.is_file():
        return None, None, None, "zolt add succeeded but wrote no zolt.lock"
    raw_packages, conflicts, policies = parse_lockfile(lock_path)
    packages = {}
    for pkg in raw_packages:
        group, _, artifact = pkg["id"].partition(":")
        entry = packages.setdefault(
            (group, artifact),
            {"version": pkg["version"], "scopes": [], "direct": False, "policies": []})
        entry["scopes"].append(pkg.get("scope", "compile"))
        entry["direct"] = entry["direct"] or bool(pkg.get("direct"))
        entry["policies"].extend(pkg.get("policies") or [])
        if pkg["version"] != entry["version"]:
            entry["policies"].append(f"MULTIPLE-VERSIONS: {entry['version']} vs {pkg['version']}")
    return packages, conflicts, policies, None


def classify_hard_fail(error_text):
    for pattern, tag in HARD_FAIL_PATTERNS:
        if re.search(pattern, error_text):
            return tag
    return "INVESTIGATE"


# ------------------------------------------------------------------- Diffing

def scope_bucket(scope):
    return scope if scope in ("compile", "runtime") else None


def zolt_bucket(scopes):
    buckets = {scope_bucket(s) for s in scopes} - {None}
    if "compile" in buckets:
        return "compile"
    if "runtime" in buckets:
        return "runtime"
    return None


def diff_root(maven_packages, zolt_packages):
    diffs = []
    maven_cmp = {key: value for key, value in maven_packages.items()
                 if scope_bucket(value["scope"])}
    zolt_cmp = {key: value for key, value in zolt_packages.items()
                if zolt_bucket(value["scopes"])}
    for key in sorted(set(maven_cmp) | set(zolt_cmp)):
        ga = f"{key[0]}:{key[1]}"
        m, z = maven_cmp.get(key), zolt_cmp.get(key)
        if m and not z:
            diffs.append({"package": ga, "class": "only-maven/INVESTIGATE",
                          "maven": m["version"], "maven_scope": m["scope"]})
        elif z and not m:
            tag = ("framework-injection" if z["policies"] else "INVESTIGATE")
            diffs.append({"package": ga, "class": f"only-zolt/{tag}",
                          "zolt": z["version"], "zolt_scopes": z["scopes"],
                          "policies": z["policies"]})
        elif m["version"] != z["version"]:
            cmp_result, certain = compare_versions(z["version"], m["version"])
            if cmp_result > 0 and certain:
                tag = "expected-newest-wins"
            else:
                tag = "INVESTIGATE"
            diffs.append({"package": ga, "class": f"version-diff/{tag}",
                          "maven": m["version"], "zolt": z["version"],
                          "policies": z["policies"]})
        elif scope_bucket(m["scope"]) != zolt_bucket(z["scopes"]):
            diffs.append({"package": ga, "class": "scope-diff",
                          "maven_scope": m["scope"], "zolt_scopes": z["scopes"],
                          "version": m["version"]})
        else:
            diffs.append({"package": ga, "class": "match", "version": m["version"]})
    return diffs


# -------------------------------------------------------------------- Runner

def process_root(root, work_root, results_dir, index, total):
    slug = slugify(root)
    workdir = work_root / slug
    if workdir.exists():
        shutil.rmtree(workdir)
    result = {"root": root}

    maven_packages, maven_error = maven_resolve(root, workdir)
    if maven_error:
        result["verdict"] = "maven-fail"
        result["maven_error"] = maven_error
    else:
        result["maven_count"] = len(maven_packages)

    zolt_packages = None
    if not maven_error:
        zolt_packages, conflicts, policies, zolt_error = zolt_resolve(root, workdir)
        if zolt_error:
            tag = classify_hard_fail(zolt_error)
            result["verdict"] = f"zolt-hard-fail/{tag}"
            result["zolt_error"] = zolt_error
        else:
            result["zolt_count"] = len(zolt_packages)
            result["zolt_conflicts"] = conflicts
            result["zolt_policies"] = policies

    if maven_packages is not None and zolt_packages is not None:
        diffs = diff_root(maven_packages, zolt_packages)
        result["diffs"] = [d for d in diffs if d["class"] != "match"]
        counts = {}
        for diff in diffs:
            counts[diff["class"]] = counts.get(diff["class"], 0) + 1
        result["counts"] = counts
        divergent = sum(v for k, v in counts.items() if k != "match")
        result["verdict"] = "clean" if divergent == 0 else f"{divergent} divergences"

    (results_dir / f"{slug}.json").write_text(json.dumps(result, indent=2, default=str))
    print(f"[{index}/{total}] {root}: {result['verdict']}", flush=True)
    return result


def write_summary(results, results_dir):
    class_totals = {}
    lines = ["# zolt vs Maven — differential resolution summary", ""]
    lines.append("| root | verdict | match | divergences |")
    lines.append("| --- | --- | --- | --- |")
    for result in results:
        counts = result.get("counts", {})
        match_count = counts.get("match", 0)
        divergences = {k: v for k, v in counts.items() if k != "match"}
        for key, value in divergences.items():
            class_totals[key] = class_totals.get(key, 0) + value
        if "counts" not in result:
            class_totals[result["verdict"]] = class_totals.get(result["verdict"], 0) + 1
        div_text = ", ".join(f"{k}: {v}" for k, v in sorted(divergences.items())) or "-"
        lines.append(f"| {result['root']} | {result['verdict']} | {match_count} | {div_text} |")
    lines += ["", "## Divergence class totals", ""]
    for key, value in sorted(class_totals.items()):
        lines.append(f"- `{key}`: {value}")
    (results_dir / "summary.md").write_text("\n".join(lines) + "\n")


def main():
    if len(sys.argv) != 3:
        print("usage: harness.py <roots-file> <output-dir>", file=sys.stderr)
        sys.exit(2)
    roots = [line.strip() for line in Path(sys.argv[1]).read_text().splitlines()
             if line.strip() and not line.startswith("#")]
    output_dir = Path(sys.argv[2]).resolve()
    work_root = output_dir / "work"
    results_dir = output_dir / "results"
    results_dir.mkdir(parents=True, exist_ok=True)
    results = [process_root(root, work_root, results_dir, i + 1, len(roots))
               for i, root in enumerate(roots)]
    write_summary(results, results_dir)
    print(f"\nSummary: {results_dir / 'summary.md'}", flush=True)


if __name__ == "__main__":
    main()

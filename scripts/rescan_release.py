#!/usr/bin/env python3
"""Rescan the latest completed main CI release by verified immutable digest."""

import argparse
import json
import os
from pathlib import Path
import re
import subprocess

from publish_images import DIGEST, command
from trivy_policy import MODULES


def validate_manifest(manifest, repository, sha):
    identity = f"https://github.com/{repository}/.github/workflows/backend-ci.yml@refs/heads/main"
    if (manifest.get("kind") != "ci-release-candidate" or manifest.get("schema_version") != 1
            or manifest.get("repository") != repository or manifest.get("source_sha") != sha
            or not re.fullmatch(r"[0-9a-f]{40}", sha)
            or manifest.get("identity") != identity
            or manifest.get("issuer") != "https://token.actions.githubusercontent.com"
            or set(manifest.get("images", {})) != set(MODULES)):
        raise ValueError("release manifest does not match the trusted main run")
    for module, image in manifest["images"].items():
        prefix = f"ghcr.io/{repository.lower()}/{module}@"
        ref = image.get("image", "")
        if not ref.startswith(prefix) or not DIGEST.fullmatch(ref[len(prefix):]):
            raise ValueError("image must be a digest in the expected GHCR repository")
        if not DIGEST.fullmatch(image.get("image_id", "")) or image.get("platform") != "linux/amd64":
            raise ValueError("invalid image identity or platform")
    return identity


def main(output):
    repo = os.environ["GITHUB_REPOSITORY"]
    if os.environ.get("GITHUB_REF") != "refs/heads/main":
        raise ValueError("scheduled rescan must run from main")
    output.mkdir(parents=True, exist_ok=True)
    # A missing/expired newest manifest must fail monitoring, not fall back to an
    # older candidate that could make the current published version look clean.
    runs = json.loads(command("gh", "api", f"repos/{repo}/actions/workflows/backend-ci.yml/runs?branch=main&event=push&status=success&per_page=1"))
    selected = None
    for run in runs["workflow_runs"]:
        artifacts = json.loads(command("gh", "api", f"repos/{repo}/actions/runs/{run['id']}/artifacts?per_page=100"))
        expected = f"verified-release-{run['id']}-{run['run_attempt']}"
        artifact = next((a for a in artifacts["artifacts"] if a["name"] == expected), None)
        if artifact:
            if artifact["expired"]:
                raise ValueError("latest verified release evidence expired; do not silently rescan an older release")
            selected = run
            command("gh", "run", "download", str(run["id"]), "--repo", repo, "--name", expected, "--dir", str(output / "release"))
            break
    if selected is None:
        raise ValueError("no retained verified main release found; rescan cannot claim a clean release")
    manifest = json.loads((output / "release/release-manifest.json").read_text())
    identity = validate_manifest(manifest, repo, selected["head_sha"])
    failed = []
    for module, image in manifest["images"].items():
        ref = image["image"]
        command("cosign", "verify", "--certificate-identity", identity,
                "--certificate-oidc-issuer", manifest["issuer"],
                "--certificate-github-workflow-sha", manifest["source_sha"], ref)
        command("docker", "pull", "--platform=linux/amd64", ref)
        actual_id = command("docker", "image", "inspect", "--format={{.Id}}", ref)
        if actual_id != image["image_id"]:
            raise ValueError("registry image no longer matches the manifest")
        id_file, report = output / f"{module}.image-id", output / f"{module}.json"
        id_file.write_text(actual_id + "\n")
        command("trivy", "image", "--image-src", "docker", "--scanners", "vuln", "--list-all-pkgs",
                "--timeout", "20m", "--format", "json", "--output", str(report), ref)
        # Continue through policy failures to retain a report for every image.
        result = subprocess.run(["python3", "scripts/trivy_policy.py", "--stage", "image", "--module", module,
                                 "--expected-image", ref, "--image-id-file", str(id_file), "--mode", "enforce",
                                 "--report", str(report), "--summary", str(output / f"{module}.summary.json")], check=False)
        if result.returncode:
            failed.append(module)
    (output / "trivy-version.json").write_text(command("trivy", "version", "--format", "json") + "\n")
    if failed:
        raise ValueError("published images now fail policy: " + ", ".join(failed))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    main(parser.parse_args().output)

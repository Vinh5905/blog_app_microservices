#!/usr/bin/env python3
"""Publish the current trusted main run's scanned images, then emit a CI manifest.

This is not a deployment approval or a claim of a SLSA assurance level.
Credentials are supplied by the workflow; no image is rebuilt here.
"""

import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess

from trivy_policy import MODULES, evaluate, read_json, validate_exceptions, validate_image_identity
from trivy_inputs import validate_scan_inputs

DIGEST = re.compile(r"sha256:[0-9a-f]{64}\Z")


def command(*args):
    return subprocess.run(args, check=True, text=True, stdout=subprocess.PIPE).stdout.strip()


def write_json(path, value):
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def preflight(evidence, source, env):
    """Validate every input before permitting the first registry mutation."""
    if env.get("GITHUB_EVENT_NAME") != "push" or env.get("GITHUB_REF") != "refs/heads/main":
        raise ValueError("publishing requires a push to main")
    validate_scan_inputs()
    sha = env["GITHUB_SHA"]
    if not re.fullmatch(r"[0-9a-f]{40}", sha):
        raise ValueError("invalid source SHA")
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", env["GITHUB_REPOSITORY"]):
        raise ValueError("invalid repository")
    for field in ("GITHUB_RUN_ID", "GITHUB_RUN_ATTEMPT"):
        if not env[field].isdigit():
            raise ValueError(f"invalid {field}")
    for directory in (evidence, source):
        if (directory / "source-sha.txt").read_text().strip() != sha:
            raise ValueError("evidence belongs to another source SHA")
    exceptions = validate_exceptions(read_json("security/trivy/exceptions.json"), dt.datetime.now(dt.timezone.utc).date())
    for stage in ("source", "config"):
        findings, _, _ = evaluate(read_json(source / f"{stage}.json"), stage, None, exceptions)
        if findings:
            raise ValueError(f"{stage} gate did not pass")
    images = {}
    for module in MODULES:
        ref = f"blogapp-ci/{module}:{sha}"
        image_id = (evidence / f"{module}.image-id").read_text().strip()
        if not DIGEST.fullmatch(image_id):
            raise ValueError("invalid image ID")
        report = read_json(evidence / f"{module}.json")
        validate_image_identity(report, ref, image_id)
        findings, _, _ = evaluate(report, "image", module, exceptions)
        if findings:
            raise ValueError(f"image gate did not pass: {module}")
        sbom = read_json(evidence / f"{module}.cdx.json")
        if sbom.get("bomFormat") != "CycloneDX" or not sbom.get("components"):
            raise ValueError(f"missing SBOM components: {module}")
        if command("docker", "image", "inspect", "--format={{.Id}}", ref) != image_id:
            raise ValueError(f"loaded image differs from scanned image: {module}")
        images[module] = {"local_ref": ref, "image_id": image_id}
    return images


def publish(evidence, source, output, env):
    manifest_path = output / "release-manifest.json"
    if manifest_path.exists():
        raise ValueError("output already contains a release manifest")
    images = preflight(evidence, source, env)
    output.mkdir(parents=True, exist_ok=True)
    sha, repo = env["GITHUB_SHA"], env["GITHUB_REPOSITORY"]
    run_url = f"https://github.com/{repo}/actions/runs/{env['GITHUB_RUN_ID']}/attempts/{env['GITHUB_RUN_ATTEMPT']}"
    identity = f"https://github.com/{repo}/.github/workflows/backend-ci.yml@refs/heads/main"
    issuer = "https://token.actions.githubusercontent.com"
    verification = ("--certificate-identity", identity, "--certificate-oidc-issuer", issuer,
                    "--certificate-github-workflow-sha", sha)
    entries = {}
    for module, image in images.items():
        repository = f"ghcr.io/{repo.lower()}/{module}"
        tag = f"{repository}:{sha}-{env['GITHUB_RUN_ID']}-{env['GITHUB_RUN_ATTEMPT']}"
        command("docker", "tag", image["local_ref"], tag)
        command("docker", "push", tag)
        digest = command("docker", "buildx", "imagetools", "inspect", tag, "--format={{.Manifest.Digest}}")
        if not DIGEST.fullmatch(digest):
            raise ValueError("registry returned an invalid manifest digest")
        ref = f"{repository}@{digest}"
        command("docker", "pull", "--platform=linux/amd64", ref)
        if command("docker", "image", "inspect", "--format={{.Id}}", ref) != image["image_id"]:
            raise ValueError("registry image differs from the scanned image")
        provenance = {
            "buildDefinition": {
                "buildType": f"https://github.com/{repo}/blob/{sha}/docs/TRIVY-PRODUCTION-CI.md#release-contract",
                "externalParameters": {"repository": f"https://github.com/{repo}", "ref": "refs/heads/main", "module": module},
                "internalParameters": {"platform": "linux/amd64"},
                "resolvedDependencies": [{"uri": f"git+https://github.com/{repo}@refs/heads/main", "digest": {"gitCommit": sha}}],
            },
            "runDetails": {"builder": {"id": identity}, "metadata": {"invocationId": run_url}},
        }
        predicate = output / f"{module}.provenance.json"
        write_json(predicate, provenance)
        command("cosign", "sign", "--yes", ref)
        command("cosign", "attest", "--yes", "--type", "cyclonedx", "--predicate", str(evidence / f"{module}.cdx.json"), ref)
        command("cosign", "attest", "--yes", "--type", "slsaprovenance1", "--predicate", str(predicate), ref)
        for kind in ("signature", "cyclonedx", "slsaprovenance1"):
            args = ("verify",) if kind == "signature" else ("verify-attestation", "--type", kind)
            verified = command("cosign", *args, *verification, ref)
            if not verified:
                raise ValueError(f"empty verification evidence: {module}/{kind}")
            (output / f"{module}.{kind}.verification.json").write_text(verified + "\n", encoding="utf-8")
        entries[module] = {
            "image": ref, "image_id": image["image_id"], "platform": "linux/amd64",
            "sbom_sha256": hashlib.sha256((evidence / f"{module}.cdx.json").read_bytes()).hexdigest(),
            "signature_verified": True, "sbom_verified": True, "provenance_verified": True,
        }
    # Atomic completion marker. Partial registry pushes never constitute a release.
    manifest = {"schema_version": 1, "kind": "ci-release-candidate", "source_sha": sha,
                "repository": repo, "run_url": run_url, "issuer": issuer, "identity": identity,
                "images": entries}
    pending = output / "release-manifest.pending"
    write_json(pending, manifest)
    pending.replace(manifest_path)
    return manifest


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--images", type=Path, required=True)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    publish(args.images, args.source, args.output, os.environ)

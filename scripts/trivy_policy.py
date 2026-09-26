#!/usr/bin/env python3
"""Evaluate Trivy JSON against BlogApp's time-bound exception policy."""

import argparse
import datetime as dt
import json
import sys
from collections import Counter
from pathlib import Path


MODULES = (
    "api-gateway-server",
    "userauthservice",
    "postservice",
    "commentservice",
    "blog-client",
)
EXPECTED_SOURCE = {f"{name}/pom.xml" for name in MODULES if name != "blog-client"}
EXPECTED_SOURCE.add("blog-client/package-lock.json")
EXPECTED_CONFIG = {f"{name}/Dockerfile" for name in MODULES}
SEVERITIES = {"HIGH", "CRITICAL"}


def read_json(path):
    with Path(path).open(encoding="utf-8") as stream:
        return json.load(stream)


def module_for(target, stage, image_module):
    if stage == "image":
        return image_module
    prefix = target.split("/", 1)[0]
    return prefix if prefix in MODULES else "repository"


def validate_exceptions(document, today):
    if not isinstance(document, dict) or document.get("version") != 1:
        raise ValueError("exceptions must have version 1")
    entries = document.get("exceptions")
    if not isinstance(entries, list):
        raise ValueError("exceptions must be a list")
    seen = set()
    for entry in entries:
        if not isinstance(entry, dict):
            raise ValueError("each exception must be an object")
        stage = entry.get("stage")
        if stage not in {"source", "config", "image"}:
            raise ValueError("exception stage must be source, config, or image")
        allowed_modules = set(MODULES) | ({"repository"} if stage == "config" else set())
        if entry.get("module") not in allowed_modules:
            raise ValueError("exception module is outside the scan scope")
        for field in ("id", "owner", "issue", "reason", "expires"):
            if not isinstance(entry.get(field), str) or not entry[field].strip():
                raise ValueError(f"exception {field} must be nonempty")
        if not entry["owner"].startswith("@"):
            raise ValueError("exception owner must be a GitHub handle")
        if not entry["issue"].startswith("https://github.com/"):
            raise ValueError("exception issue must be a GitHub URL")
        if len(entry["reason"].strip()) < 10:
            raise ValueError("exception reason must explain the risk")
        try:
            expiry = dt.date.fromisoformat(entry["expires"])
        except ValueError as exc:
            raise ValueError("exception expires must be YYYY-MM-DD") from exc
        if not today < expiry <= today + dt.timedelta(days=30):
            raise ValueError(f"exception {entry['id']} is expired or exceeds 30 days")
        scope_field = "target" if stage == "config" else "package"
        if not isinstance(entry.get(scope_field), str) or not entry[scope_field].strip():
            raise ValueError(f"exception {scope_field} must be nonempty")
        key = (stage, entry["module"], entry["id"], entry[scope_field])
        if key in seen:
            raise ValueError(f"duplicate exception: {key}")
        seen.add(key)
    return entries


def evaluate(report, stage, image_module, exceptions):
    if not isinstance(report, dict) or not isinstance(report.get("Results"), list):
        raise ValueError("Trivy report has no Results array")
    targets = {
        result.get("Target", "").replace("\\", "/")
        for result in report["Results"]
        if isinstance(result, dict)
    }
    expected = EXPECTED_SOURCE if stage == "source" else EXPECTED_CONFIG if stage == "config" else set()
    missing = expected - targets
    if missing:
        raise ValueError("Trivy omitted required targets: " + ", ".join(sorted(missing)))
    if stage == "image" and (not image_module or not targets):
        raise ValueError("image report has no scanned targets or module")
    if stage == "image":
        validate_image_inventory(report, image_module)

    applicable = [
        entry for entry in exceptions
        if entry["stage"] == stage and (stage != "image" or entry["module"] == image_module)
    ]
    used = set()
    findings = []
    suppressed = []
    for result in report["Results"]:
        target = result.get("Target", "").replace("\\", "/")
        module = module_for(target, stage, image_module)
        field = "Misconfigurations" if stage == "config" else "Vulnerabilities"
        for finding in result.get(field) or []:
            if finding.get("Severity") not in SEVERITIES:
                continue
            if stage != "config" and not (
                finding.get("Status") == "fixed" or finding.get("FixedVersion")
            ):
                continue
            finding_id = finding.get("ID") if stage == "config" else finding.get("VulnerabilityID")
            scoped_value = target if stage == "config" else finding.get("PkgName")
            item = {"module": module, "target": target, "id": finding_id,
                    "package": finding.get("PkgName"), "severity": finding["Severity"]}
            match = next((entry for entry in applicable if
                          entry["module"] == module and entry["id"] == finding_id and
                          entry["target" if stage == "config" else "package"] == scoped_value), None)
            if match is None:
                findings.append(item)
            else:
                suppressed.append(item)
                used.add(id(match))
    stale = [entry for entry in applicable if id(entry) not in used]
    if stale:
        raise ValueError("exceptions no longer match findings: " +
                         ", ".join(f"{entry['module']}:{entry['id']}" for entry in stale))
    return findings, suppressed, targets


def validate_image_inventory(report, module):
    """An OS-only scan is insufficient for a Spring Boot runtime image."""
    def packages(kind, package_type=None):
        return [p for result in report["Results"]
                if result.get("Class") == kind and (package_type is None or result.get("Type") == package_type)
                for p in (result.get("Packages") or [])
                if isinstance(p, dict) and p.get("Name") and p.get("Version")]

    if not packages("os-pkgs"):
        raise ValueError("image report is missing OS package inventory")
    if module == "blog-client":
        return
    java = packages("lang-pkgs", "jar")
    app = any(p["Name"].endswith(":" + module) and
              p.get("FilePath", "").lstrip("/") == "app/app.jar" for p in java)
    boot = any(p["Name"] == "org.springframework.boot:spring-boot" and
               p.get("FilePath", "").lstrip("/").startswith("app/app.jar/BOOT-INF/lib/") for p in java)
    if not app or not boot:
        raise ValueError("image report is missing application Java package inventory")


def validate_image_identity(report, expected_ref, expected_id):
    if report.get("ArtifactType") != "container_image":
        raise ValueError("Trivy report is not a container image scan")
    if report.get("ArtifactName") != expected_ref:
        raise ValueError("Trivy scanned a different image reference")
    if report.get("Metadata", {}).get("ImageID") != expected_id:
        raise ValueError("Trivy scanned a different local image ID")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", required=True)
    parser.add_argument("--exceptions", default="security/trivy/exceptions.json")
    parser.add_argument("--stage", choices=("source", "config", "image"), required=True)
    parser.add_argument("--module", choices=MODULES)
    parser.add_argument("--expected-image")
    parser.add_argument("--image-id-file")
    parser.add_argument("--mode", choices=("audit", "enforce"), required=True)
    parser.add_argument("--summary")
    args = parser.parse_args()
    if args.stage == "image" and not args.module:
        parser.error("--module is required for image reports")
    if args.stage == "image" and (not args.expected_image or not args.image_id_file):
        parser.error("--expected-image and --image-id-file are required for image reports")
    try:
        exceptions = validate_exceptions(read_json(args.exceptions), dt.datetime.now(dt.timezone.utc).date())
        report = read_json(args.report)
        if args.stage == "image":
            validate_image_identity(report, args.expected_image, Path(args.image_id_file).read_text(encoding="utf-8").strip())
        findings, suppressed, targets = evaluate(report, args.stage, args.module, exceptions)
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        print(f"Trivy policy error: {exc}", file=sys.stderr)
        return 2
    counts = Counter(item["module"] for item in findings)
    summary = {
        "stage": args.stage, "mode": args.mode, "targets": sorted(targets),
        "blocking_findings": len(findings), "suppressed_findings": len(suppressed),
        "by_module": dict(sorted(counts.items())),
    }
    if args.summary:
        Path(args.summary).write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(summary, indent=2))
    for item in findings[:10]:
        print(f"  {item['module']}: {item['severity']} {item['id']} {item['package'] or item['target']}")
    if len(findings) > 10:
        print(f"  ... and {len(findings) - 10} more in the JSON report")
    if args.mode == "audit" and findings:
        print("::warning::Trivy baseline has unresolved HIGH/CRITICAL findings; this is not a passing gate")
    return 1 if args.mode == "enforce" and findings else 0


if __name__ == "__main__":
    sys.exit(main())

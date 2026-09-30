#!/usr/bin/env python3
"""Validate Gitleaks policy and turn redacted reports into release evidence."""

import argparse
import json
from pathlib import Path
import subprocess
import tomllib


ALLOWED_CONFIG_KEYS = {"title", "extend"}
ALLOWED_EXTEND_KEYS = {"useDefault"}


def tracked_paths(root):
    output = subprocess.run(
        ["git", "-C", str(root), "ls-files", "-z"],
        check=True,
        stdout=subprocess.PIPE,
    ).stdout
    return [Path(value.decode("utf-8")) for value in output.split(b"\0") if value]


def validate_config(config, paths):
    data = tomllib.loads(config.read_text(encoding="utf-8"))
    unexpected = set(data) - ALLOWED_CONFIG_KEYS
    if unexpected:
        raise ValueError(f"unsupported Gitleaks config keys: {sorted(unexpected)}")
    extend = data.get("extend")
    if not isinstance(extend, dict) or extend.get("useDefault") is not True:
        raise ValueError("Gitleaks config must extend the complete default ruleset")
    unexpected_extend = set(extend) - ALLOWED_EXTEND_KEYS
    if unexpected_extend:
        raise ValueError(f"Gitleaks rules cannot be disabled or replaced: {sorted(unexpected_extend)}")
    ignored = [str(path) for path in paths if path.name == ".gitleaksignore"]
    if ignored:
        raise ValueError(f"fingerprint suppression files are not allowed: {ignored}")


def read_report(path):
    if not path.is_file():
        raise ValueError(f"missing Gitleaks report: {path}")
    try:
        report = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        raise ValueError(f"invalid Gitleaks JSON report: {path}") from error
    if not isinstance(report, list):
        raise ValueError(f"Gitleaks report must be a JSON array: {path}")
    for finding in report:
        if not isinstance(finding, dict) or finding.get("Secret") != "REDACTED":
            raise ValueError(f"Gitleaks report is not fully redacted: {path}")
    return report


def build_summary(scans, source_sha, scanner_version):
    results = {}
    total = 0
    failed = False
    for name, exit_code, path in scans:
        findings = read_report(path)
        count = len(findings)
        total += count
        scanner_error = exit_code not in (0, 1) or (exit_code == 1 and count == 0) or (exit_code == 0 and count != 0)
        failed = failed or scanner_error or count != 0
        results[name] = {
            "exit_code": exit_code,
            "findings": count,
            "rules": sorted({finding.get("RuleID", "unknown") for finding in findings}),
            "files": sorted({finding.get("File", "unknown") for finding in findings}),
            "scanner_error": scanner_error,
        }
    return {
        "schema_version": 1,
        "source_sha": source_sha,
        "scanner_version": scanner_version,
        "status": "fail" if failed else "pass",
        "total_findings": total,
        "scans": results,
    }


def command_preflight(args):
    root = args.root.resolve()
    validate_config(args.config.resolve(), tracked_paths(root))


def command_summarize(args):
    scans = [(name, int(exit_code), Path(path)) for name, exit_code, path in args.scan]
    args.output.parent.mkdir(parents=True, exist_ok=True)
    try:
        summary = build_summary(scans, args.source_sha, args.scanner_version)
    except (OSError, ValueError):
        for _, _, path in scans:
            path.unlink(missing_ok=True)
        summary = {
            "schema_version": 1,
            "source_sha": args.source_sha,
            "scanner_version": args.scanner_version,
            "status": "error",
            "total_findings": None,
            "scans": {},
            "error": "report validation failed; unsafe reports removed",
        }
        args.output.write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
        raise SystemExit(1)
    args.output.write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    if summary["status"] != "pass":
        raise SystemExit(1)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)

    preflight = subparsers.add_parser("preflight")
    preflight.add_argument("--root", type=Path, required=True)
    preflight.add_argument("--config", type=Path, required=True)
    preflight.set_defaults(func=command_preflight)

    summarize = subparsers.add_parser("summarize")
    summarize.add_argument("--scan", action="append", nargs=3, metavar=("NAME", "EXIT_CODE", "REPORT"), required=True)
    summarize.add_argument("--source-sha", required=True)
    summarize.add_argument("--scanner-version", required=True)
    summarize.add_argument("--output", type=Path, required=True)
    summarize.set_defaults(func=command_summarize)

    args = parser.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()

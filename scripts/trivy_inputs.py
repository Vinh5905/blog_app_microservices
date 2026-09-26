#!/usr/bin/env python3
"""Reject scanner-native suppressions; exceptions.json is the only waiver path."""

import os
from pathlib import Path
import re
import sys

SKIP_DIRS = {".git", ".cache", "target", "node_modules"}
CONFIG_SUFFIXES = {".yaml", ".yml", ".json", ".tf", ".tfvars", ".hcl", ".tpl"}
INLINE_IGNORE = re.compile(r"(?:trivy|tfsec)\s*:\s*ignore", re.IGNORECASE)


def validate_scan_inputs(root=Path(".")):
    root = Path(root)
    for directory, dirs, files in os.walk(root):
        dirs[:] = [d for d in dirs if d not in SKIP_DIRS]
        for name in files:
            path = Path(directory) / name
            relative = path.relative_to(root)
            if name.lower().startswith(".trivyignore"):
                raise ValueError(f"native ignore file is forbidden: {relative}; use security/trivy/exceptions.json")
            if "dockerfile" not in name.lower() and path.suffix.lower() not in CONFIG_SUFFIXES:
                continue
            if path.is_symlink():
                raise ValueError(f"scan configuration must not be a symlink: {relative}")
            if INLINE_IGNORE.search(path.read_text(encoding="utf-8-sig")):
                raise ValueError(f"inline scanner suppression is forbidden: {relative}; use security/trivy/exceptions.json")


if __name__ == "__main__":
    try:
        validate_scan_inputs()
    except (OSError, ValueError) as exc:
        print(f"Trivy input policy error: {exc}", file=sys.stderr)
        sys.exit(2)

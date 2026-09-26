#!/usr/bin/env python3
"""Require explicit producer artifact IDs before download-artifact can run."""

import os
import re

ARTIFACT_KEYS = ("SOURCE_EVIDENCE_ID", "IMAGE_EVIDENCE_ID", "IMAGE_CANDIDATES_ID")


def require_artifact_ids(env):
    ids = [env.get(key, "") for key in ARTIFACT_KEYS]
    if any(not re.fullmatch(r"[1-9][0-9]*", value) for value in ids):
        raise ValueError("missing/invalid producer artifact ID; rerun the producing jobs if evidence expired")
    if len(set(ids)) != len(ids):
        raise ValueError("source evidence, image evidence and candidates must be distinct artifacts")
    return dict(zip(ARTIFACT_KEYS, ids))


if __name__ == "__main__":
    require_artifact_ids(os.environ)

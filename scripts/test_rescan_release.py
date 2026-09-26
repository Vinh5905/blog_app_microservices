"""Only the expected registry digests from a trusted main run may be rescanned."""

import copy
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from rescan_release import main, validate_manifest
from trivy_policy import MODULES


class RescanManifestTests(unittest.TestCase):
    def setUp(self):
        self.sha = "a" * 40
        self.manifest = {
            "kind": "ci-release-candidate", "schema_version": 1, "source_sha": self.sha,
            "repository": "owner/repo", "issuer": "https://token.actions.githubusercontent.com",
            "identity": "https://github.com/owner/repo/.github/workflows/backend-ci.yml@refs/heads/main",
            "images": {m: {"image": f"ghcr.io/owner/repo/{m}@sha256:" + "b" * 64,
                           "image_id": "sha256:" + "c" * 64, "platform": "linux/amd64"} for m in MODULES},
        }

    def test_exact_complete_manifest_is_accepted(self):
        self.assertEqual(validate_manifest(self.manifest, "owner/repo", self.sha), self.manifest["identity"])

    def test_missing_latest_release_evidence_fails_before_image_access(self):
        with tempfile.TemporaryDirectory() as directory:
            responses = [json.dumps({"workflow_runs": [{"id": 123, "run_attempt": 1}]}),
                         json.dumps({"artifacts": []})]
            with patch.dict("os.environ", {"GITHUB_REPOSITORY": "owner/repo", "GITHUB_REF": "refs/heads/main"}), \
                    patch("rescan_release.command", side_effect=responses) as run, \
                    self.assertRaisesRegex(ValueError, "no retained verified main release"):
                main(Path(directory))
            self.assertEqual(run.call_count, 2)
            self.assertIn("per_page=1", run.call_args_list[0].args[-1])

    def test_missing_module_mutable_tag_wrong_owner_and_sha_are_rejected(self):
        for mutation in ("missing", "tag", "owner", "sha", "identity"):
            manifest = copy.deepcopy(self.manifest)
            with self.subTest(mutation=mutation):
                if mutation == "missing":
                    manifest["images"].pop(MODULES[0])
                elif mutation == "tag":
                    manifest["images"][MODULES[0]]["image"] = f"ghcr.io/owner/repo/{MODULES[0]}:latest"
                elif mutation == "owner":
                    manifest["images"][MODULES[0]]["image"] = f"ghcr.io/attacker/repo/{MODULES[0]}@sha256:" + "b" * 64
                elif mutation == "sha":
                    manifest["source_sha"] = "d" * 40
                else:
                    manifest["identity"] = manifest["identity"].replace("main", "feature")
                with self.assertRaises(ValueError):
                    validate_manifest(manifest, "owner/repo", self.sha)


if __name__ == "__main__":
    unittest.main()

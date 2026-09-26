"""Release failures must never yield a deployable completion manifest."""

import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

from publish_images import publish, write_json
from trivy_policy import EXPECTED_CONFIG, EXPECTED_SOURCE, MODULES


class ReleaseTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        root = Path(self.temp.name)
        self.images, self.source, self.output = [root / p for p in ("images", "source", "release")]
        self.images.mkdir()
        self.source.mkdir()
        self.sha = "a" * 40
        self.image_id, self.digest = "sha256:" + "b" * 64, "sha256:" + "c" * 64
        self.env = {"GITHUB_SHA": self.sha, "GITHUB_REF": "refs/heads/main", "GITHUB_EVENT_NAME": "push",
                    "GITHUB_REPOSITORY": "owner/repo", "GITHUB_RUN_ID": "123", "GITHUB_RUN_ATTEMPT": "1"}
        for directory in (self.source, self.images):
            (directory / "source-sha.txt").write_text(self.sha)
        for stage, targets in (("source", EXPECTED_SOURCE), ("config", EXPECTED_CONFIG)):
            write_json(self.source / f"{stage}.json", {"Results": [{"Target": t} for t in targets]})
        for module in MODULES:
            (self.images / f"{module}.image-id").write_text(self.image_id)
            write_json(self.images / f"{module}.json", {"ArtifactType": "container_image",
                       "ArtifactName": f"blogapp-ci/{module}:{self.sha}", "Metadata": {"ImageID": self.image_id},
                       "Results": [{"Target": "alpine", "Vulnerabilities": []}]})
            write_json(self.images / f"{module}.cdx.json", {"bomFormat": "CycloneDX", "components": [{"name": "example"}]})
        self.calls = []

    def run_command(self, *args):
        self.calls.append(args)
        if args[:3] == ("docker", "image", "inspect"):
            return self.image_id
        if args[:3] == ("docker", "buildx", "imagetools"):
            return self.digest
        return "{}"

    def execute(self):
        return publish(self.images, self.source, self.output, self.env)

    def assert_no_manifest(self):
        self.assertFalse((self.output / "release-manifest.json").exists())

    def assert_no_push(self):
        self.assertFalse(any(call[:2] == ("docker", "push") for call in self.calls))
        self.assert_no_manifest()

    def test_pr_and_manual_runs_cannot_publish(self):
        with patch("publish_images.command", side_effect=self.run_command):
            for event in ("pull_request", "workflow_dispatch"):
                self.env["GITHUB_EVENT_NAME"] = event
                with self.assertRaisesRegex(ValueError, "push to main"):
                    self.execute()
        self.assertEqual(self.calls, [])

    def test_sha_mismatch_blocks_before_registry_mutation(self):
        (self.source / "source-sha.txt").write_text("d" * 40)
        with patch("publish_images.command", side_effect=self.run_command), self.assertRaisesRegex(ValueError, "another source"):
            self.execute()
        self.assert_no_push()

    def test_last_image_with_cve_blocks_all_pushes(self):
        path = self.images / f"{MODULES[-1]}.json"
        report = json.loads(path.read_text())
        report["Results"][0]["Vulnerabilities"] = [{"VulnerabilityID": "CVE-TEST", "PkgName": "test",
                                                    "Severity": "CRITICAL", "FixedVersion": "2"}]
        write_json(path, report)
        with patch("publish_images.command", side_effect=self.run_command), self.assertRaisesRegex(ValueError, "image gate"):
            self.execute()
        self.assert_no_push()

    def test_source_config_and_missing_sbom_block_release(self):
        for stage, field in (("source", "Vulnerabilities"), ("config", "Misconfigurations")):
            with self.subTest(stage=stage):
                path = self.source / f"{stage}.json"
                report = json.loads(path.read_text())
                report["Results"][0][field] = [{"Severity": "HIGH", "FixedVersion": "2"}]
                write_json(path, report)
                with patch("publish_images.command", side_effect=self.run_command), self.assertRaisesRegex(ValueError, "gate did not pass"):
                    self.execute()
                report["Results"][0].pop(field)
                write_json(path, report)
        (self.images / f"{MODULES[-1]}.cdx.json").unlink()
        with patch("publish_images.command", side_effect=self.run_command), self.assertRaises(FileNotFoundError):
            self.execute()
        self.assert_no_push()

    def test_loaded_image_mismatch_blocks_all_pushes(self):
        with patch("publish_images.command", return_value="sha256:" + "d" * 64), self.assertRaisesRegex(ValueError, "loaded image differs"):
            self.execute()
        self.assert_no_manifest()

    def test_registry_mismatch_cannot_create_manifest(self):
        def changed_image(*args):
            if args[:3] == ("docker", "image", "inspect") and "@sha256:" in args[-1]:
                return "sha256:" + "d" * 64
            return self.run_command(*args)
        with patch("publish_images.command", side_effect=changed_image), self.assertRaisesRegex(ValueError, "registry image differs"):
            self.execute()
        self.assert_no_manifest()
        self.assertFalse(any(call[0] == "cosign" for call in self.calls))

    def test_failed_signature_verification_cannot_create_manifest(self):
        def fail_verify(*args):
            if args[:2] == ("cosign", "verify"):
                raise subprocess.CalledProcessError(1, args)
            return self.run_command(*args)
        with patch("publish_images.command", side_effect=fail_verify), self.assertRaises(subprocess.CalledProcessError):
            self.execute()
        self.assert_no_manifest()

    def test_complete_release_requires_all_five_verified_digests(self):
        with patch("publish_images.command", side_effect=self.run_command):
            manifest = self.execute()
        self.assertEqual(set(manifest["images"]), set(MODULES))
        self.assertEqual(len([c for c in self.calls if c[:2] == ("docker", "push")]), 5)
        self.assertEqual(len([c for c in self.calls if c[0] == "cosign" and c[1].startswith("verify")]), 15)
        self.assertTrue(all(v["image"].endswith("@" + self.digest) for v in manifest["images"].values()))
        self.assertTrue((self.output / "release-manifest.json").is_file())


if __name__ == "__main__":
    unittest.main()

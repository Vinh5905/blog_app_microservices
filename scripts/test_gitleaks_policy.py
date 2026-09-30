"""Regression tests for fail-closed Gitleaks policy and evidence handling."""

import json
from pathlib import Path
import tempfile
import unittest
from types import SimpleNamespace

from gitleaks_policy import build_summary, command_summarize, validate_config


class GitleaksPolicyTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.config = self.root / "gitleaks.toml"
        self.config.write_text('title = "test"\n[extend]\nuseDefault = true\n', encoding="utf-8")

    def report(self, name, findings):
        path = self.root / name
        path.write_text(json.dumps(findings), encoding="utf-8")
        return path

    def test_complete_default_ruleset_without_suppressions_passes(self):
        validate_config(self.config, [Path("README.md")])

    def test_disabled_rules_allowlists_and_fingerprint_files_fail(self):
        invalid = (
            '[extend]\nuseDefault = true\ndisabledRules = ["generic-api-key"]\n',
            '[extend]\nuseDefault = true\n[[allowlists]]\npaths = ["example"]\n',
        )
        for config in invalid:
            with self.subTest(config=config):
                self.config.write_text(config, encoding="utf-8")
                with self.assertRaises(ValueError):
                    validate_config(self.config, [])
        self.config.write_text('[extend]\nuseDefault = true\n', encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "suppression"):
            validate_config(self.config, [Path("nested/.gitleaksignore")])

    def test_two_clean_scans_create_passing_summary(self):
        scans = [("history", 0, self.report("history.json", [])), ("current", 0, self.report("current.json", []))]
        summary = build_summary(scans, "a" * 40, "8.30.1")
        self.assertEqual(summary["status"], "pass")
        self.assertEqual(summary["total_findings"], 0)

    def test_redacted_finding_fails_without_copying_secret(self):
        finding = {"Secret": "REDACTED", "RuleID": "generic-api-key", "File": "example.env"}
        summary = build_summary([("history", 1, self.report("history.json", [finding]))], "a" * 40, "8.30.1")
        self.assertEqual(summary["status"], "fail")
        self.assertEqual(summary["total_findings"], 1)
        self.assertNotIn("Secret", json.dumps(summary))

    def test_unredacted_or_inconsistent_scanner_output_fails_closed(self):
        with self.assertRaisesRegex(ValueError, "not fully redacted"):
            build_summary(
                [("history", 1, self.report("unsafe.json", [{"Secret": "example-value"}]))],
                "a" * 40,
                "8.30.1",
            )
        summary = build_summary([("history", 1, self.report("error.json", []))], "a" * 40, "8.30.1")
        self.assertEqual(summary["status"], "fail")
        self.assertTrue(summary["scans"]["history"]["scanner_error"])

    def test_unredacted_report_is_deleted_before_artifact_upload(self):
        unsafe = self.report("unsafe.json", [{"Secret": "example-value"}])
        output = self.root / "summary.json"
        args = SimpleNamespace(
            scan=[["history", "1", str(unsafe)]],
            source_sha="a" * 40,
            scanner_version="8.30.1",
            output=output,
        )
        with self.assertRaises(SystemExit):
            command_summarize(args)
        self.assertFalse(unsafe.exists())
        self.assertEqual(json.loads(output.read_text())["status"], "error")


if __name__ == "__main__":
    unittest.main()

"""Regression tests for the security gate's scope and exception rules."""

import datetime as dt
import unittest

from trivy_policy import (
    EXPECTED_CONFIG,
    EXPECTED_SOURCE,
    evaluate,
    validate_exceptions,
    validate_image_identity,
)


TODAY = dt.date(2026, 9, 25)


def source_report(finding=None):
    return {"Results": [
        {"Target": target, "Vulnerabilities": [finding] if target == "blog-client/package-lock.json" and finding else []}
        for target in sorted(EXPECTED_SOURCE)
    ]}


def config_report(finding=None):
    return {"Results": [
        {"Target": target, "Misconfigurations": [finding] if target == "blog-client/Dockerfile" and finding else []}
        for target in sorted(EXPECTED_CONFIG)
    ]}


def exception(stage, package=None, target=None, expires="2026-10-01"):
    item = {
        "stage": stage,
        "module": "blog-client",
        "id": "CVE-EXAMPLE" if stage != "config" else "DS-0002",
        "owner": "@Vinh5905",
        "issue": "https://github.com/Vinh5905/blog_app_microservices/issues/1",
        "reason": "Temporarily accepted while dependency is upgraded",
        "expires": expires,
    }
    item["target" if stage == "config" else "package"] = target if stage == "config" else package
    return item


class TrivyPolicyTests(unittest.TestCase):
    def test_fixed_high_is_blocked_and_unfixed_is_report_only(self):
        fixed = {"VulnerabilityID": "CVE-EXAMPLE", "PkgName": "example-lib", "Severity": "HIGH", "Status": "fixed", "FixedVersion": "2.0"}
        findings, _, _ = evaluate(source_report(fixed), "source", None, [])
        self.assertEqual(len(findings), 1)
        fixed["Status"] = "affected"
        fixed["FixedVersion"] = ""
        findings, _, _ = evaluate(source_report(fixed), "source", None, [])
        self.assertEqual(findings, [])

    def test_exception_must_match_exact_package_and_module(self):
        finding = {"VulnerabilityID": "CVE-EXAMPLE", "PkgName": "example-lib", "Severity": "CRITICAL", "FixedVersion": "2.0"}
        approved = validate_exceptions({"version": 1, "exceptions": [exception("source", package="example-lib")]}, TODAY)
        findings, suppressed, _ = evaluate(source_report(finding), "source", None, approved)
        self.assertEqual(findings, [])
        self.assertEqual(len(suppressed), 1)
        approved[0]["package"] = "unrelated-lib"
        with self.assertRaisesRegex(ValueError, "no longer match"):
            evaluate(source_report(finding), "source", None, approved)

    def test_expired_and_long_exceptions_are_rejected(self):
        for expiry in ("2026-09-25", "2026-11-01"):
            with self.subTest(expiry=expiry), self.assertRaisesRegex(ValueError, "expired or exceeds"):
                validate_exceptions({"version": 1, "exceptions": [exception("image", package="example-lib", expires=expiry)]}, TODAY)

    def test_missing_manifest_or_dockerfile_fails_closed(self):
        report = source_report()
        report["Results"].pop()
        with self.assertRaisesRegex(ValueError, "omitted required targets"):
            evaluate(report, "source", None, [])
        report = config_report()
        report["Results"].pop()
        with self.assertRaisesRegex(ValueError, "omitted required targets"):
            evaluate(report, "config", None, [])

    def test_config_high_is_blocked_by_exact_target(self):
        finding = {"ID": "DS-0002", "Severity": "HIGH"}
        findings, _, _ = evaluate(config_report(finding), "config", None, [])
        self.assertEqual(len(findings), 1)
        approved = validate_exceptions({"version": 1, "exceptions": [exception("config", target="blog-client/Dockerfile")]}, TODAY)
        findings, suppressed, _ = evaluate(config_report(finding), "config", None, approved)
        self.assertEqual(findings, [])
        self.assertEqual(len(suppressed), 1)

    def test_image_report_must_match_built_image_id(self):
        report = {"ArtifactType": "container_image", "ArtifactName": "blogapp-ci/blog-client:abc", "Metadata": {"ImageID": "sha256:123"}}
        validate_image_identity(report, "blogapp-ci/blog-client:abc", "sha256:123")
        with self.assertRaisesRegex(ValueError, "different local image ID"):
            validate_image_identity(report, "blogapp-ci/blog-client:abc", "sha256:other")


if __name__ == "__main__":
    unittest.main()

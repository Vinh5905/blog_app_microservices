"""Regression tests for the security gate's scope and exception rules."""

import datetime as dt
import copy
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


def image_inventory(module):
    results = [{"Target": "ubuntu", "Class": "os-pkgs", "Type": "ubuntu",
                "Packages": [{"Name": "libc6", "Version": "2.35"}]}]
    if module != "blog-client":
        results.append({"Target": "Java", "Class": "lang-pkgs", "Type": "jar", "Packages": [
            {"Name": "com.example:" + module, "Version": "1", "FilePath": "app/app.jar"},
            {"Name": "org.springframework.boot:spring-boot", "Version": "3.5.16",
             "FilePath": "app/app.jar/BOOT-INF/lib/spring-boot-3.5.16.jar"}]})
    return results


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
    def test_java_image_requires_os_and_application_inventory(self):
        report = {"Results": image_inventory("api-gateway-server")}
        evaluate(report, "image", "api-gateway-server", [])
        for broken in (report["Results"][:1], report["Results"][1:]):
            with self.subTest(broken=broken), self.assertRaisesRegex(ValueError, "inventory"):
                evaluate({"Results": broken}, "image", "api-gateway-server", [])
        for mutation in ("empty", "missing-version", "wrong-path", "wrong-module"):
            broken = copy.deepcopy(report)
            packages = broken["Results"][1]["Packages"]
            if mutation == "empty":
                packages.clear()
            elif mutation == "missing-version":
                packages[1].pop("Version")
            elif mutation == "wrong-path":
                packages[1]["FilePath"] = "opt/jdk/lib/spring-boot.jar"
            else:
                packages[0]["Name"] = "com.example:unrelated"
            with self.subTest(mutation=mutation), self.assertRaisesRegex(ValueError, "Java package inventory"):
                evaluate(broken, "image", "api-gateway-server", [])

    def test_frontend_requires_os_inventory_but_not_java(self):
        report = {"Results": image_inventory("blog-client")}
        evaluate(report, "image", "blog-client", [])
        report["Results"][0]["Packages"] = []
        with self.assertRaisesRegex(ValueError, "OS package inventory"):
            evaluate(report, "image", "blog-client", [])

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

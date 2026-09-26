"""The central exception policy cannot be bypassed by native scanner ignores."""

from pathlib import Path
import tempfile
import unittest

from trivy_inputs import validate_scan_inputs


class TrivyInputTests(unittest.TestCase):
    def test_native_ignore_files_are_rejected_even_when_empty_or_nested(self):
        for filename in (".trivyignore", "nested/.trivyignore.yaml", "nested/.trivyignore.yml"):
            with self.subTest(filename=filename), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                path = root / filename
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text("")
                with self.assertRaisesRegex(ValueError, "native ignore file"):
                    validate_scan_inputs(root)

    def test_inline_ignores_in_dockerfiles_and_iac_are_rejected(self):
        for filename, text in (("blog-client/Dockerfile", "FROM alpine\n# trivy:ignore:DS002\nUSER root\n"),
                               ("infra/main.tf", "#tfsec:ignore:aws-s3-no-public-access-with-acl"),
                               ("chart/templates/pod.yaml", "# trivy:ignore:KSV001"),
                               ("chart/templates/_helpers.tpl", "{{/* trivy:ignore:KSV001 */}}")):
            with self.subTest(filename=filename), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                path = root / filename
                path.parent.mkdir(parents=True)
                path.write_text(text)
                with self.assertRaisesRegex(ValueError, "inline scanner suppression"):
                    validate_scan_inputs(root)

    def test_documentation_and_excluded_generated_files_are_not_scan_inputs(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "README.md").write_text("Document trivy:ignore syntax here")
            (root / "Dockerfile").write_text("FROM alpine\nUSER 101\n")
            generated = root / "service/target/.trivyignore"
            generated.parent.mkdir(parents=True)
            generated.write_text("DS002")
            validate_scan_inputs(root)


if __name__ == "__main__":
    unittest.main()

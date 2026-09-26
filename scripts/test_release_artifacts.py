"""A retry consumes producer IDs; empty IDs must not trigger download-all."""

import unittest

from release_artifacts import ARTIFACT_KEYS, require_artifact_ids


class ArtifactInputTests(unittest.TestCase):
    def test_retry_keeps_original_producer_ids(self):
        original = dict(zip(ARTIFACT_KEYS, ("101", "102", "103")))
        retry = {**original, "GITHUB_RUN_ATTEMPT": "2"}
        self.assertEqual(require_artifact_ids(retry), original)

    def test_empty_invalid_duplicate_ids_fail_closed(self):
        for invalid in ("", "0", "1,2", "*", " 101", "102"):
            with self.subTest(invalid=invalid):
                env = dict(zip(ARTIFACT_KEYS, (invalid, "102", "103")))
                with self.assertRaises(ValueError):
                    require_artifact_ids(env)


if __name__ == "__main__":
    unittest.main()

#!/usr/bin/env python3
"""Unit tests for scripts/bump_version.py. Run from the repo root:

    python3 -m unittest discover -s scripts -p 'test_bump_version.py' -v
"""

from __future__ import annotations

import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import bump_version  # noqa: E402


REPO_VERSION = Path(__file__).resolve().parent.parent / "version.properties"
SCRIPT = Path(__file__).resolve().parent / "bump_version.py"


class BumpTests(unittest.TestCase):
    def test_patch_increments_the_last_component_and_the_code(self) -> None:
        name, code, changed = bump_version.bump("1.0.1", 67, "PATCH")
        self.assertEqual((name, code, changed), ("1.0.2", 68, True))

    def test_minor_resets_patch(self) -> None:
        name, code, changed = bump_version.bump("1.2.9", 7, "MINOR")
        self.assertEqual((name, code, changed), ("1.3.0", 8, True))

    def test_major_resets_minor_and_patch(self) -> None:
        name, code, changed = bump_version.bump("1.2.3", 41, "MAJOR")
        self.assertEqual((name, code, changed), ("2.0.0", 42, True))

    def test_major_from_zero(self) -> None:
        name, code, changed = bump_version.bump("0.9.4", 3, "MAJOR")
        self.assertEqual((name, code, changed), ("1.0.0", 4, True))

    def test_patch_grows_past_nine(self) -> None:
        name, code, changed = bump_version.bump("9.9.9", 1, "PATCH")
        self.assertEqual((name, code, changed), ("9.9.10", 2, True))

    def test_none_and_empty_do_not_change_anything(self) -> None:
        for release_type in ("", "none", "NONE", " None "):
            with self.subTest(release_type=release_type):
                name, code, changed = bump_version.bump("1.0.1", 67, release_type)
                self.assertEqual((name, code, changed), ("1.0.1", 67, False))

    def test_sequential_patches_each_add_one(self) -> None:
        name, code, _ = bump_version.bump("1.0.1", 67, "PATCH")
        name, code, changed = bump_version.bump(name, code, "PATCH")
        self.assertEqual((name, code, changed), ("1.0.3", 69, True))

    def test_minor_then_patch(self) -> None:
        name, code, _ = bump_version.bump("1.0.1", 67, "MINOR")
        name, code, changed = bump_version.bump(name, code, "PATCH")
        self.assertEqual((name, code, changed), ("1.1.1", 69, True))

    def test_unknown_release_type_is_rejected(self) -> None:
        for release_type in ("patch", "HOTFIX", "deploy", "1"):
            with self.subTest(release_type=release_type):
                with self.assertRaises(bump_version.VersionError):
                    bump_version.bump("1.0.1", 67, release_type)

    def test_version_name_must_be_three_components(self) -> None:
        for name in ("1.0", "1", "1.0.1-debug", "01.2.3", "1.0.01", "v1.0.1", ""):
            with self.subTest(name=name):
                with self.assertRaises(bump_version.VersionError):
                    bump_version.bump(name, 1, "PATCH")

    def test_version_code_must_be_a_non_negative_integer(self) -> None:
        for code in (-1, "x", "01", "1.5", ""):
            with self.subTest(code=code):
                with self.assertRaises(bump_version.VersionError):
                    bump_version.bump("1.0.0", code, "PATCH")

    def test_zero_code_is_allowed_and_release_makes_it_one(self) -> None:
        name, code, changed = bump_version.bump("1.0.0", 0, "PATCH")
        self.assertEqual((name, code, changed), ("1.0.1", 1, True))
        name, code, changed = bump_version.bump("1.0.0", 0, "none")
        self.assertEqual((name, code, changed), ("1.0.0", 0, False))


class FileTests(unittest.TestCase):
    def test_round_trip_and_none_does_not_write(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "version.properties"
            bump_version.write_version_file(path, "1.0.1", 67)
            before = path.read_text(encoding="utf-8")
            name, code, changed = bump_version.apply_release(path, "none", write=True)
            self.assertEqual((name, code, changed), ("1.0.1", 67, False))
            self.assertEqual(path.read_text(encoding="utf-8"), before)
            name, code, changed = bump_version.apply_release(path, "PATCH", write=True)
            self.assertEqual((name, code, changed), ("1.0.2", 68, True))
            self.assertEqual(bump_version.read_version_file(path), ("1.0.2", 68))

    def test_dry_run_via_cli_does_not_write(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "version.properties"
            bump_version.write_version_file(path, "1.4.2", 10)
            result = subprocess.run(
                [sys.executable, str(SCRIPT), "MINOR", "--file", str(path), "--dry-run", "--github-env"],
                check=False,
                capture_output=True,
                text=True,
            )
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn("APP_VERSION_NAME=1.5.0", result.stdout)
            self.assertIn("APP_VERSION_CODE=11", result.stdout)
            self.assertIn("APP_VERSION_PREVIOUS_NAME=1.4.2", result.stdout)
            self.assertIn("APP_VERSION_PREVIOUS_CODE=10", result.stdout)
            self.assertIn("APP_VERSION_BUMPED=true", result.stdout)
            self.assertEqual(bump_version.read_version_file(path), ("1.4.2", 10))

    def test_cli_write_and_rejects_a_bad_type(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "version.properties"
            bump_version.write_version_file(path, "1.0.1", 67)
            ok = subprocess.run(
                [sys.executable, str(SCRIPT), "PATCH", "--file", str(path)],
                check=False,
                capture_output=True,
                text=True,
            )
            self.assertEqual(ok.returncode, 0, ok.stderr)
            self.assertEqual(bump_version.read_version_file(path), ("1.0.2", 68))
            bad = subprocess.run(
                [sys.executable, str(SCRIPT), "patch", "--file", str(path)],
                check=False,
                capture_output=True,
                text=True,
            )
            self.assertEqual(bad.returncode, 1)
            self.assertEqual(bump_version.read_version_file(path), ("1.0.2", 68))

    def test_committed_version_file_parses_and_none_is_a_no_op(self) -> None:
        name, code = bump_version.read_version_file(REPO_VERSION)
        self.assertRegex(name, r"^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$")
        self.assertGreaterEqual(code, 1)
        self.assertEqual(bump_version.bump(name, code, ""), (name, code, False))
        self.assertEqual(bump_version.bump(name, code, "none"), (name, code, False))


if __name__ == "__main__":
    unittest.main()

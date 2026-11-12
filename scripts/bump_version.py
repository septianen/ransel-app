#!/usr/bin/env python3
"""Bump the Android app version in version.properties.

version.properties at the repo root is the only source of versionName and versionCode.
The Release workflow is the only writer.

    MAJOR  1.2.3 -> 2.0.0
    MINOR  1.2.3 -> 1.3.0
    PATCH  1.0.1 -> 1.0.2
    none   unchanged, and the file is not written

versionCode increases by 1 on MAJOR, MINOR and PATCH.
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

RELEASE_TYPES = ("MAJOR", "MINOR", "PATCH")
# "none" and "" are the no-release values. Anything else is an error.
NO_RELEASE = ("", "none")

_VERSION_NAME = re.compile(r"^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$")
_VERSION_CODE = re.compile(r"^(0|[1-9]\d*)$")

HEADER = (
    "# Android versionName and versionCode. Single source of truth.\n"
    "# The Release workflow is the only writer (MAJOR / MINOR / PATCH).\n"
)

DEFAULT_FILE = Path(__file__).resolve().parent.parent / "version.properties"


class VersionError(ValueError):
    """version.properties or RELEASE_TYPE is not usable."""


def parse_version_name(name: str) -> tuple[int, int, int]:
    text = name.strip()
    if not _VERSION_NAME.fullmatch(text):
        raise VersionError(f"versionName must be X.Y.Z (for example 1.0.1), got {name!r}")
    major, minor, patch = text.split(".")
    return int(major), int(minor), int(patch)


def format_version_name(major: int, minor: int, patch: int) -> str:
    return f"{major}.{minor}.{patch}"


def parse_version_code(code: str | int) -> int:
    text = str(code).strip()
    if not _VERSION_CODE.fullmatch(text):
        raise VersionError(f"versionCode must be a non-negative integer, got {code!r}")
    return int(text)


def bump(version_name: str, version_code: int, release_type: str) -> tuple[str, int, bool]:
    """Return (versionName, versionCode, changed).

    Empty and "none" (any case) do not change either value.
    """
    kind = (release_type or "").strip()
    if kind.lower() in NO_RELEASE:
        parse_version_name(version_name)
        if version_code < 0:
            raise VersionError(f"versionCode must be a non-negative integer, got {version_code!r}")
        return version_name.strip(), version_code, False
    if kind not in RELEASE_TYPES:
        raise VersionError(
            f"RELEASE_TYPE must be none, MAJOR, MINOR or PATCH, got {release_type!r}"
        )
    major, minor, patch = parse_version_name(version_name)
    code = parse_version_code(version_code)
    if kind == "MAJOR":
        major, minor, patch = major + 1, 0, 0
    elif kind == "MINOR":
        minor, patch = minor + 1, 0
    else:
        patch += 1
    return format_version_name(major, minor, patch), code + 1, True


def read_version_file(path: Path) -> tuple[str, int]:
    if not path.is_file():
        raise VersionError(f"missing {path}")
    name: str | None = None
    code: str | None = None
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        key, value = key.strip(), value.strip()
        if key == "versionName":
            name = value
        elif key == "versionCode":
            code = value
    if name is None or code is None:
        raise VersionError(f"{path} must set versionName and versionCode")
    return name, parse_version_code(code)


def format_version_file(version_name: str, version_code: int) -> str:
    parse_version_name(version_name)
    parse_version_code(version_code)
    return f"{HEADER}versionName={version_name.strip()}\nversionCode={version_code}\n"


def write_version_file(path: Path, version_name: str, version_code: int) -> None:
    path.write_text(format_version_file(version_name, version_code), encoding="utf-8")


def github_env_lines(
    version_name: str,
    version_code: int,
    previous_name: str,
    previous_code: int,
    changed: bool,
) -> str:
    bumped = "true" if changed else "false"
    return (
        f"APP_VERSION_NAME={version_name}\n"
        f"APP_VERSION_CODE={version_code}\n"
        f"APP_VERSION_PREVIOUS_NAME={previous_name}\n"
        f"APP_VERSION_PREVIOUS_CODE={previous_code}\n"
        f"APP_VERSION_BUMPED={bumped}\n"
    )


def apply_release(path: Path, release_type: str, *, write: bool) -> tuple[str, int, bool]:
    previous_name, previous_code = read_version_file(path)
    name, code, changed = bump(previous_name, previous_code, release_type)
    if changed and write:
        write_version_file(path, name, code)
    return name, code, changed


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Read or bump version.properties.")
    parser.add_argument(
        "release_type",
        nargs="?",
        default="",
        help="MAJOR, MINOR, PATCH, or none/empty to only print the current version",
    )
    parser.add_argument("--file", type=Path, default=DEFAULT_FILE, help="version.properties path")
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Compute the bump but do not write the file",
    )
    parser.add_argument(
        "--github-env",
        action="store_true",
        help="Print APP_VERSION_* lines for $GITHUB_ENV",
    )
    args = parser.parse_args(argv)
    try:
        previous_name, previous_code = read_version_file(args.file)
        name, code, changed = apply_release(args.file, args.release_type, write=not args.dry_run)
    except VersionError as error:
        print(f"error: {error}", file=sys.stderr)
        return 1
    if args.github_env:
        print(
            github_env_lines(name, code, previous_name, previous_code, changed),
            end="",
        )
    else:
        print(f"versionName={name}")
        print(f"versionCode={code}")
        print(f"bumped={'true' if changed else 'false'}")
    return 0


if __name__ == "__main__":
    sys.exit(main())

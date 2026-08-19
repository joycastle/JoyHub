#!/usr/bin/env python3
"""Deterministically validate JoyHub's checked-in official agent skills."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SKILLS_DIR = ROOT / "official-agent-skills"
CONTRACT_PATH = ROOT / "scripts/contracts/joyhub-cli-api.json"
PACKAGE_JSON = ROOT / "cli/package.json"
EXPECTED_PACKAGE = "@joycastle/joyhub-cli"
EXPECTED_VERSION = "0.2.0"
EXPECTED_BIN = "joyhub"
NPX_PREFIX = (
    f"npx --yes --package={EXPECTED_PACKAGE}@{EXPECTED_VERSION} {EXPECTED_BIN}"
)
EXPECTED_COMMANDS = [
    "auth ensure --json",
    "search --query <query> --limit <n> --json",
    "namespaces --publishable --json",
    "install <coordinate> --agent <agent> --scope <scope> --json",
    "publish <directory> --namespace <slug> --dry-run --json",
    "publish <directory> --namespace <slug> --json",
]

REQUIRED_SKILLS = {
    "find-skills": {
        "commands": ("auth ensure --json", "search", "install"),
        "gates": ("explicit user choice", "Do not run an install command until"),
    },
    "share-skill": {
        "commands": (
            "auth ensure --json",
            "namespaces --publishable --json",
            "publish",
            "--dry-run",
        ),
        "gates": ("explicit confirmation", "Only after confirmation"),
    },
}

BANNED_PATTERNS = {
    "unbounded latest CLI": re.compile(r"@(?:latest|\*)\b"),
    "credential file read": re.compile(
        r"\b(?:cat|less|more|head|tail|jq|python\S*|node)\b[^\n]*credentials\.json",
        re.IGNORECASE,
    ),
    "token argument": re.compile(r"--token(?:\s|=)"),
    "direct HTTP fallback": re.compile(r"\b(?:curl|wget)\s+https?://", re.IGNORECASE),
    "old npm package": re.compile(r"@astron-team/skillhub"),
}


def fail(message: str) -> None:
    print(f"ERROR: {message}", file=sys.stderr)


def parse_frontmatter(text: str, path: Path) -> dict[str, str]:
    match = re.match(r"\A---\n(.*?)\n---\n", text, re.DOTALL)
    if not match:
        raise ValueError(f"{path}: missing YAML frontmatter")
    values: dict[str, str] = {}
    for line in match.group(1).splitlines():
        if ":" not in line:
            raise ValueError(f"{path}: invalid frontmatter line: {line!r}")
        key, value = line.split(":", 1)
        values[key.strip()] = value.strip()
    return values


def main() -> int:
    errors: list[str] = []

    try:
        contract = json.loads(CONTRACT_PATH.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        fail(f"cannot load {CONTRACT_PATH}: {exc}")
        return 1

    expected_package = {
        "name": EXPECTED_PACKAGE,
        "version": EXPECTED_VERSION,
        "bin": EXPECTED_BIN,
    }
    if contract.get("schemaVersion") != 1:
        errors.append("contract schemaVersion must be 1")
    if contract.get("package") != expected_package:
        errors.append(f"contract package must equal {expected_package}")
    if contract.get("commands") != EXPECTED_COMMANDS:
        errors.append("contract commands do not match the official skill command surface")

    api = contract.get("api")
    if not isinstance(api, list) or not api:
        errors.append("contract api must be a non-empty array")
    else:
        ids = [entry.get("id") for entry in api if isinstance(entry, dict)]
        keys = [
            (entry.get("method"), entry.get("path"))
            for entry in api
            if isinstance(entry, dict)
        ]
        if len(ids) != len(set(ids)) or len(keys) != len(set(keys)):
            errors.append("contract API ids and method/path pairs must be unique")
        for method, path in keys:
            if method not in {"GET", "POST", "PUT", "PATCH", "DELETE"}:
                errors.append(f"invalid contract method: {method!r}")
            if not isinstance(path, str) or not path.startswith("/api/"):
                errors.append(f"invalid contract path: {path!r}")

    try:
        package = json.loads(PACKAGE_JSON.read_text(encoding="utf-8"))
        actual_bin = package.get("bin", {})
        if package.get("name") != EXPECTED_PACKAGE:
            errors.append(f"cli/package.json name must be {EXPECTED_PACKAGE}")
        if list(actual_bin) != [EXPECTED_BIN]:
            errors.append(f"cli/package.json must expose only bin {EXPECTED_BIN}")
    except (OSError, json.JSONDecodeError) as exc:
        errors.append(f"cannot load cli/package.json: {exc}")

    actual_dirs = sorted(
        path.name for path in SKILLS_DIR.iterdir() if path.is_dir()
    ) if SKILLS_DIR.is_dir() else []
    if actual_dirs != sorted(REQUIRED_SKILLS):
        errors.append(
            f"official skills must be exactly {sorted(REQUIRED_SKILLS)}, got {actual_dirs}"
        )

    for skill_name, requirements in REQUIRED_SKILLS.items():
        path = SKILLS_DIR / skill_name / "SKILL.md"
        try:
            text = path.read_text(encoding="utf-8")
            frontmatter = parse_frontmatter(text, path)
        except (OSError, ValueError) as exc:
            errors.append(str(exc))
            continue

        if frontmatter.get("name") != skill_name:
            errors.append(f"{path}: frontmatter name must be {skill_name}")
        if not frontmatter.get("description"):
            errors.append(f"{path}: frontmatter description is required")
        if NPX_PREFIX not in re.sub(r"\\\n\s*", "", text):
            errors.append(f"{path}: missing pinned npx invocation")
        for command in requirements["commands"]:
            if command not in text:
                errors.append(f"{path}: missing command contract {command!r}")
        for gate in requirements["gates"]:
            if gate not in text:
                errors.append(f"{path}: missing user gate {gate!r}")
        if "stdout as JSON" not in text:
            errors.append(f"{path}: must require JSON stdout parsing")
        if "Never read" not in text or "credentials.json" not in text:
            errors.append(f"{path}: must forbid credential reads")
        for label, pattern in BANNED_PATTERNS.items():
            if pattern.search(text):
                errors.append(f"{path}: contains banned pattern ({label})")

    for error in errors:
        fail(error)
    if errors:
        return 1

    print(
        f"validated {len(REQUIRED_SKILLS)} official skills against "
        f"{EXPECTED_PACKAGE}@{EXPECTED_VERSION}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

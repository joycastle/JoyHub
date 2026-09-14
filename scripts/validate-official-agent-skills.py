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
EXPECTED_PACKAGE = "@toolnets/joyhub-cli"
EXPECTED_VERSION = "0.2.0"
EXPECTED_BIN = "joyhub"
EXPECTED_REGISTRY = "https://joyhub.toolnets.net"
NPX_PREFIX = (
    f"npx --yes --package={EXPECTED_PACKAGE}@{EXPECTED_VERSION} {EXPECTED_BIN}"
)
EXPECTED_COMMANDS = [
    "auth ensure --json",
    "search --query <query> --limit <n> --json",
    "namespaces --publishable --json",
    "install <coordinate> --agent <agent> --scope <scope> --json",
    "publish <directory> --namespace <slug> --visibility <visibility> --dry-run --json",
    "publish <directory> --namespace <slug> --visibility <visibility> --json",
]

REQUIRED_SKILLS = {
    "find-skills-joyhub": {
        "commands": ("auth ensure", "search", "install"),
        "gates": ("明确选择", "在用户明确确认这两项选择前，禁止执行安装命令"),
    },
    "share-skill-joyhub": {
        "commands": (
            "auth ensure",
            "namespaces --publishable",
            "publish",
            "--dry-run",
        ),
        "gates": ("明确确认", "只有在用户确认后"),
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
    if contract.get("defaultRegistry") != EXPECTED_REGISTRY:
        errors.append(f"contract defaultRegistry must be {EXPECTED_REGISTRY}")
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
        metadata_path = SKILLS_DIR / skill_name / "agents" / "openai.yaml"
        try:
            text = path.read_text(encoding="utf-8")
            frontmatter = parse_frontmatter(text, path)
        except (OSError, ValueError) as exc:
            errors.append(str(exc))
            continue

        if frontmatter.get("name") != skill_name:
            errors.append(f"{path}: frontmatter name must be {skill_name}")
        description = frontmatter.get("description")
        if not description:
            errors.append(f"{path}: frontmatter description is required")
        elif not description.isascii():
            errors.append(f"{path}: frontmatter description must be English")
        normalized_text = re.sub(r"[ \t]*\\\n[ \t]*", " ", text)
        if NPX_PREFIX not in normalized_text:
            errors.append(f"{path}: missing pinned npx invocation")
        command_lines = re.findall(
            rf"{re.escape(NPX_PREFIX)}[^\n]*",
            normalized_text,
        )
        for command_line in command_lines:
            registry_arg = f"--registry {EXPECTED_REGISTRY}"
            if registry_arg not in command_line:
                errors.append(
                    f"{path}: every pinned npx command must include "
                    f"{registry_arg!r}: {command_line}"
                )
        for command in requirements["commands"]:
            if command not in text:
                errors.append(f"{path}: missing command contract {command!r}")
        for gate in requirements["gates"]:
            if gate not in text:
                errors.append(f"{path}: missing user gate {gate!r}")
        if "将 CLI 标准输出解析为 JSON" not in text:
            errors.append(f"{path}: must require JSON stdout parsing")
        if "禁止读取" not in text or "credentials.json" not in text:
            errors.append(f"{path}: must forbid credential reads")
        for label, pattern in BANNED_PATTERNS.items():
            if pattern.search(text):
                errors.append(f"{path}: contains banned pattern ({label})")

        try:
            metadata = metadata_path.read_text(encoding="utf-8")
        except OSError as exc:
            errors.append(f"cannot load {metadata_path}: {exc}")
            continue
        if "interface:" not in metadata:
            errors.append(f"{metadata_path}: missing interface metadata")
        if not re.search(r"^\s+display_name:\s+.+$", metadata, re.MULTILINE):
            errors.append(f"{metadata_path}: display_name is required")
        if not re.search(r"^\s+short_description:\s+.+$", metadata, re.MULTILINE):
            errors.append(f"{metadata_path}: short_description is required")
        prompt_match = re.search(
            r"^\s+default_prompt:\s+[\"']?(.*?)[\"']?\s*$",
            metadata,
            re.MULTILINE,
        )
        if not prompt_match or f"${skill_name}" not in prompt_match.group(1):
            errors.append(
                f"{metadata_path}: default_prompt must mention ${skill_name}"
            )

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

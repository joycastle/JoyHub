#!/usr/bin/env python3
"""Require explicit contract acknowledgement for CLI/API boundary changes."""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MANIFEST = Path("scripts/contracts/joyhub-cli-api.json")
SERVER_API_SOURCES = (
    ROOT
    / "server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/DeviceAuthController.java",
    ROOT
    / "server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/cli",
)


def join_route(base: str, suffix: str) -> str:
    return "/" + "/".join(part for part in f"{base}/{suffix}".split("/") if part)


def discover_server_routes() -> set[tuple[str, str]]:
    routes: set[tuple[str, str]] = set()
    class_mapping = re.compile(
        r'@RequestMapping\(\s*(?:value\s*=\s*)?"([^"]+)"'
    )
    method_mapping = re.compile(
        r'@(Get|Post|Put|Patch|Delete)Mapping'
        r'\(\s*(?:value\s*=\s*)?"([^"]*)"'
    )
    source_paths: list[Path] = []
    for source in SERVER_API_SOURCES:
        source_paths.extend(source.glob("**/*.java") if source.is_dir() else [source])
    for source_path in source_paths:
        source = source_path.read_text(encoding="utf-8")
        base_match = class_mapping.search(source)
        if not base_match:
            continue
        base = base_match.group(1)
        for method, suffix in method_mapping.findall(source):
            routes.add((method.upper(), join_route(base, suffix)))
    return routes


def validate_contract(contract: object) -> list[str]:
    if not isinstance(contract, dict):
        return ["contract root must be an object"]

    errors: list[str] = []
    api = contract.get("api")
    if not isinstance(api, list) or not api:
        return ["contract api must be a non-empty array"]

    discovered_routes = discover_server_routes()
    seen_ids: set[str] = set()
    seen_routes: set[tuple[str, str]] = set()
    for entry in api:
        if not isinstance(entry, dict):
            errors.append("contract API entries must be objects")
            continue
        endpoint_id = entry.get("id")
        method = entry.get("method")
        path = entry.get("path")
        if not isinstance(endpoint_id, str) or not endpoint_id:
            errors.append("contract API id must be a non-empty string")
            continue
        if endpoint_id in seen_ids:
            errors.append(f"duplicate contract API id: {endpoint_id}")
        seen_ids.add(endpoint_id)
        if not isinstance(method, str) or not isinstance(path, str):
            errors.append(f"contract API {endpoint_id} must declare method and path")
            continue
        route = (method, path)
        if route in seen_routes:
            errors.append(f"duplicate contract route: {method} {path}")
        seen_routes.add(route)
        if route not in discovered_routes:
            errors.append(
                f"contract API route does not exist in server controllers: {method} {path}"
            )
    untracked_routes = discovered_routes - seen_routes
    for method, path in sorted(untracked_routes):
        errors.append(f"server CLI route is missing from contract: {method} {path}")
    return errors


def git(*args: str) -> str:
    result = subprocess.run(
        ["git", *args],
        cwd=ROOT,
        check=True,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    return result.stdout


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--base",
        help="Git base revision used to enforce manifest acknowledgement",
    )
    args = parser.parse_args()

    try:
        contract = json.loads((ROOT / MANIFEST).read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        print(f"ERROR: cannot load {MANIFEST}: {exc}", file=sys.stderr)
        return 1

    contract_errors = validate_contract(contract)
    if contract_errors:
        for error in contract_errors:
            print(f"ERROR: {error}", file=sys.stderr)
        return 1

    drift_paths = contract.get("driftPaths")
    if not isinstance(drift_paths, list) or not drift_paths:
        print("ERROR: contract driftPaths must be a non-empty array", file=sys.stderr)
        return 1
    if drift_paths != sorted(set(drift_paths)):
        print("ERROR: contract driftPaths must be sorted and unique", file=sys.stderr)
        return 1

    if not args.base:
        print(
            f"validated contract manifest against server controllers "
            f"({len(contract.get('api', []))} endpoints)"
        )
        return 0

    try:
        merge_base = git("merge-base", args.base, "HEAD").strip()
        changed = {
            line
            for line in git("diff", "--name-only", f"{merge_base}...HEAD").splitlines()
            if line
        }
    except subprocess.CalledProcessError as exc:
        print(f"ERROR: cannot calculate contract diff: {exc.stderr.strip()}", file=sys.stderr)
        return 1

    boundary_changes = sorted(
        path
        for path in changed
        if any(
            path == watched.rstrip("/") or path.startswith(watched)
            for watched in drift_paths
        )
    )
    if boundary_changes and str(MANIFEST) not in changed:
        print(
            "ERROR: CLI/API boundary files changed without updating "
            f"{MANIFEST}:",
            file=sys.stderr,
        )
        for path in boundary_changes:
            print(f"  - {path}", file=sys.stderr)
        print(
            "Review the endpoint/command contract and update the manifest in the same PR, "
            "even when the reviewed contract remains compatible.",
            file=sys.stderr,
        )
        return 1

    if boundary_changes:
        print(
            f"contract acknowledgement present for {len(boundary_changes)} boundary change(s)"
        )
    else:
        print("no CLI/API boundary changes detected")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

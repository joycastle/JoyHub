#!/usr/bin/env python3
"""Require explicit contract acknowledgement for CLI/API boundary changes."""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MANIFEST = Path("scripts/contracts/joyhub-cli-api.json")


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

    drift_paths = contract.get("driftPaths")
    if not isinstance(drift_paths, list) or not drift_paths:
        print("ERROR: contract driftPaths must be a non-empty array", file=sys.stderr)
        return 1
    if drift_paths != sorted(set(drift_paths)):
        print("ERROR: contract driftPaths must be sorted and unique", file=sys.stderr)
        return 1

    if not args.base:
        print(f"validated contract manifest ({len(contract.get('api', []))} endpoints)")
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

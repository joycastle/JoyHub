#!/usr/bin/env python3
"""Exercise the official Skill workflows through the packed npm CLI artifact."""

from __future__ import annotations

import json
import os
import subprocess
import tempfile
import threading
import zipfile
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from io import BytesIO
from pathlib import Path
from urllib.parse import urlparse


ROOT = Path(__file__).resolve().parents[1]
CLI_DIR = ROOT / "cli"
TOKEN = "jh_semantic_test"


def skill_zip() -> bytes:
    buffer = BytesIO()
    with zipfile.ZipFile(buffer, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr(
            "SKILL.md",
            "---\nname: ad-insights\ndescription: Analyze advertising data.\n---\n",
        )
    return buffer.getvalue()


SKILL_ZIP = skill_zip()


class RegistryHandler(BaseHTTPRequestHandler):
    publish_count = 0
    validate_count = 0

    def log_message(self, _format: str, *_args: object) -> None:
        return

    def send_json(self, data: object, status: int = 200) -> None:
        body = json.dumps({"code": 0, "data": data}).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def require_auth(self) -> bool:
        if self.headers.get("Authorization") == f"Bearer {TOKEN}":
            return True
        self.send_json({"message": "unauthorized"}, 401)
        return False

    def read_body(self) -> bytes:
        length = int(self.headers.get("Content-Length", "0"))
        return self.rfile.read(length)

    def do_GET(self) -> None:  # noqa: N802 - BaseHTTPRequestHandler API
        path = urlparse(self.path).path
        if not self.require_auth():
            return

        if path == "/api/cli/v1/auth/whoami":
            self.send_json({
                "handle": "semantic-user",
                "displayName": "Semantic User",
                "email": "semantic@example.com",
            })
            return
        if path == "/api/cli/v1/skills/search":
            self.send_json({
                "items": [{
                    "namespace": "global",
                    "slug": "ad-insights",
                    "latestVersion": "1.4.0",
                    "summary": "Analyze advertising performance and attribution.",
                }],
                "total": 1,
                "limit": 10,
            })
            return
        if path == "/api/cli/v1/namespaces/publish-targets":
            self.send_json([{
                "id": 7,
                "slug": "data-team",
                "displayName": "Data Team",
                "currentUserRole": "MEMBER",
                "supportedResourceTypes": ["SKILL"],
            }])
            return
        if path == "/api/cli/v1/skills/global/ad-insights/resolve":
            self.send_json({
                "namespace": "global",
                "slug": "ad-insights",
                "version": "1.4.0",
                "versionId": 14,
                "fingerprint": "semantic-fixture",
                "downloadUrl": (
                    f"http://127.0.0.1:{self.server.server_port}"
                    "/api/cli/v1/skills/global/ad-insights/versions/1.4.0/download"
                ),
            })
            return
        if path == "/api/cli/v1/skills/global/ad-insights/versions/1.4.0/download":
            self.send_response(200)
            self.send_header("Content-Type", "application/zip")
            self.send_header("Content-Length", str(len(SKILL_ZIP)))
            self.end_headers()
            self.wfile.write(SKILL_ZIP)
            return
        self.send_json({"message": "not found"}, 404)

    def do_POST(self) -> None:  # noqa: N802 - BaseHTTPRequestHandler API
        path = urlparse(self.path).path
        if not self.require_auth():
            return
        body = self.read_body()
        if b"SKILL.md" not in body:
            self.send_json({"message": "missing skill package"}, 400)
            return

        if path == "/api/cli/v1/skills/data-team/publish/validate":
            type(self).validate_count += 1
            self.send_json({
                "valid": True,
                "errors": [],
                "warnings": ["semantic fixture warning"],
                "resolvedSlug": "campaign-helper",
                "resolvedVersion": "2.0.0",
            })
            return
        if path == "/api/cli/v1/skills/data-team/publish":
            type(self).publish_count += 1
            self.send_json({
                "namespace": "data-team",
                "slug": "campaign-helper",
                "version": "2.0.0",
                "visibility": "PRIVATE",
                "status": "PENDING_REVIEW",
            })
            return
        self.send_json({"message": "not found"}, 404)


def run(command: list[str], *, env: dict[str, str], cwd: Path) -> dict[str, object]:
    result = subprocess.run(
        command,
        cwd=cwd,
        env=env,
        check=False,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    if result.returncode != 0:
        raise AssertionError(
            f"command failed ({result.returncode}): {' '.join(command)}\n"
            f"stdout:\n{result.stdout}\nstderr:\n{result.stderr}"
        )
    try:
        return json.loads(result.stdout)
    except json.JSONDecodeError as exc:
        raise AssertionError(
            f"command did not return JSON: {' '.join(command)}\n{result.stdout}"
        ) from exc


def main() -> int:
    RegistryHandler.publish_count = 0
    RegistryHandler.validate_count = 0
    server = ThreadingHTTPServer(("127.0.0.1", 0), RegistryHandler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    registry = f"http://127.0.0.1:{server.server_port}"

    try:
        with tempfile.TemporaryDirectory(prefix="joyhub-pack-") as pack_dir_name, \
                tempfile.TemporaryDirectory(prefix="joyhub-home-") as home_name, \
                tempfile.TemporaryDirectory(prefix="joyhub-project-") as project_name:
            pack_dir = Path(pack_dir_name)
            home = Path(home_name)
            project = Path(project_name)

            subprocess.run(["bun", "run", "build"], cwd=CLI_DIR, check=True)
            packed = subprocess.run(
                ["npm", "pack", "--json", "--pack-destination", str(pack_dir)],
                cwd=CLI_DIR,
                check=True,
                text=True,
                stdout=subprocess.PIPE,
            )
            package_metadata = json.loads(packed.stdout)
            package_file = pack_dir / package_metadata[0]["filename"]
            if not package_file.is_file():
                raise AssertionError(f"npm package was not created: {package_file}")

            env = {
                **os.environ,
                "HOME": str(home),
                "USERPROFILE": str(home),
                "JOYHUB_NO_BROWSER": "1",
            }
            prefix = ["npx", "--yes", f"--package={package_file}", "joyhub"]

            login = run(
                [*prefix, "login", "--registry", registry, "--token", TOKEN, "--json"],
                env=env,
                cwd=project,
            )
            assert login["ok"] is True

            auth = run(
                [*prefix, "auth", "ensure", "--registry", registry, "--json"],
                env=env,
                cwd=project,
            )
            assert auth["authenticated"] is True
            assert auth["reauthenticated"] is False

            search = run(
                [*prefix, "search", "--query", "advertising analysis", "--limit", "10",
                 "--registry", registry, "--json"],
                env=env,
                cwd=project,
            )
            assert search["items"][0]["slug"] == "ad-insights"

            install = run(
                [*prefix, "install", "@global/ad-insights", "--agent", "codex",
                 "--scope", "project", "--registry", registry, "--json"],
                env=env,
                cwd=project,
            )
            assert install["coordinate"] == "@global/ad-insights"
            assert install["version"] == "1.4.0"
            installed_skill = project / ".codex" / "skills" / "ad-insights" / "SKILL.md"
            assert installed_skill.is_file()

            namespaces = run(
                [*prefix, "namespaces", "--publishable", "--registry", registry, "--json"],
                env=env,
                cwd=project,
            )
            assert namespaces["items"][0]["slug"] == "data-team"

            shared_skill = project / "campaign-helper"
            shared_skill.mkdir()
            (shared_skill / "SKILL.md").write_text(
                "---\nname: campaign-helper\ndescription: Build campaign reports.\n---\n",
                encoding="utf-8",
            )
            dry_run = run(
                [*prefix, "publish", str(shared_skill), "--namespace", "data-team",
                 "--visibility", "private", "--dry-run", "--registry", registry, "--json"],
                env=env,
                cwd=project,
            )
            assert dry_run["valid"] is True
            assert dry_run["visibility"] == "private"
            assert dry_run["resolvedVersion"] == "2.0.0"
            assert RegistryHandler.publish_count == 0

            publish = run(
                [*prefix, "publish", str(shared_skill), "--namespace", "data-team",
                 "--visibility", "private", "--registry", registry, "--json"],
                env=env,
                cwd=project,
            )
            assert publish["coordinate"] == "@data-team/campaign-helper"
            assert publish["version"] == "2.0.0"
            assert publish["status"] == "PENDING_REVIEW"
            assert RegistryHandler.validate_count == 1
            assert RegistryHandler.publish_count == 1

        print("validated packed npm CLI: auth, find/install, namespace, dry-run, and share")
        return 0
    finally:
        server.shutdown()
        server.server_close()
        thread.join(timeout=5)


if __name__ == "__main__":
    raise SystemExit(main())

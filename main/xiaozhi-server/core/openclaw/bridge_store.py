"""Persistent store for OpenClaw bridge tokens and metadata."""

from __future__ import annotations

import asyncio
import hashlib
import json
import os
import secrets
import uuid
from datetime import datetime, timezone
from typing import Any

from config.config_loader import get_project_dir
from config.logger import setup_logging

TAG = __name__


def _utc_now() -> str:
    return (
        datetime.now(timezone.utc)
        .replace(microsecond=0)
        .isoformat()
        .replace("+00:00", "Z")
    )


class OpenClawBridgeStore:
    """Stores issued bridge tokens and connection metadata on disk."""

    def __init__(self, config: dict):
        self.config = config
        self.logger = setup_logging()
        self.hub_config = config.get("openclaw_hub", {}) or {}
        self.file_path = self._resolve_store_path(
            self.hub_config.get("store_file", "data/openclaw/bridges.json")
        )
        self._lock = asyncio.Lock()
        self._loaded = False
        self._bridges: dict[str, dict[str, Any]] = {}

    def _resolve_store_path(self, path: str) -> str:
        if os.path.isabs(path):
            return path
        return os.path.join(get_project_dir(), path)

    def _hash_token(self, token: str) -> str:
        return hashlib.sha256(token.encode("utf-8")).hexdigest()

    async def _ensure_loaded(self):
        async with self._lock:
            if self._loaded:
                return
            await self._load_locked()
            self._loaded = True

    async def _load_locked(self):
        if not os.path.exists(self.file_path):
            self._bridges = {}
            return

        try:
            payload = await asyncio.to_thread(self._read_payload)
            bridges = payload.get("bridges", {})
            if isinstance(bridges, dict):
                self._bridges = bridges
            else:
                self._bridges = {}
        except Exception as e:
            self.logger.bind(tag=TAG).error(f"读取 OpenClaw bridge store 失败: {e}")
            self._bridges = {}

    def _read_payload(self) -> dict[str, Any]:
        with open(self.file_path, "r", encoding="utf-8") as fp:
            payload = json.load(fp)
        return payload if isinstance(payload, dict) else {}

    async def _save_locked(self):
        await asyncio.to_thread(self._write_payload)

    def _write_payload(self):
        os.makedirs(os.path.dirname(self.file_path), exist_ok=True)
        payload = {"bridges": self._bridges}
        with open(self.file_path, "w", encoding="utf-8") as fp:
            json.dump(payload, fp, ensure_ascii=False, indent=2, sort_keys=True)

    def _sanitize_record(self, record: dict[str, Any]) -> dict[str, Any]:
        sanitized = dict(record)
        sanitized.pop("tokenHash", None)
        return sanitized

    async def list_bridges(self) -> list[dict[str, Any]]:
        await self._ensure_loaded()
        async with self._lock:
            values = [
                self._sanitize_record(item)
                for item in self._bridges.values()
                if not item.get("revokedAt")
            ]
        return sorted(
            values,
            key=lambda item: (
                item.get("isDefault", False),
                item.get("createdAt", ""),
            ),
            reverse=True,
        )

    async def get_bridge(self, bridge_id: str) -> dict[str, Any] | None:
        await self._ensure_loaded()
        async with self._lock:
            record = self._bridges.get(bridge_id)
            if record is None:
                return None
            return self._sanitize_record(record)

    async def issue_bridge(
        self,
        *,
        name: str | None = None,
        bridge_id: str | None = None,
        account: str | None = None,
        peer_id_mode: str | None = None,
        default_agent_id: str | None = None,
        is_default: bool | None = None,
    ) -> dict[str, Any]:
        await self._ensure_loaded()
        async with self._lock:
            final_bridge_id = (bridge_id or "").strip() or f"bridge-{uuid.uuid4().hex[:12]}"
            existing = self._bridges.get(final_bridge_id)
            if existing and not existing.get("revokedAt"):
                raise ValueError(f"bridgeId 已存在: {final_bridge_id}")

            final_account = (account or "").strip() or self.hub_config.get(
                "default_account", "default"
            )
            final_peer_id_mode = (peer_id_mode or "").strip() or self.hub_config.get(
                "default_peer_id_mode", "device"
            )
            final_default_agent_id = (default_agent_id or "").strip()
            now = _utc_now()
            token = secrets.token_urlsafe(32)
            final_is_default = bool(is_default)
            active_bridges = [
                bridge for bridge in self._bridges.values() if not bridge.get("revokedAt")
            ]
            has_default_bridge = any(bridge.get("isDefault") for bridge in active_bridges)
            if not active_bridges or not has_default_bridge:
                final_is_default = True
            elif final_is_default:
                for bridge in self._bridges.values():
                    bridge["isDefault"] = False

            record = {
                "bridgeId": final_bridge_id,
                "name": (name or "").strip() or f"xiaozhi-{final_bridge_id[-6:]}",
                "tokenHash": self._hash_token(token),
                "account": final_account,
                "peerIdMode": final_peer_id_mode,
                "defaultAgentId": final_default_agent_id,
                "isDefault": final_is_default,
                "createdAt": now,
                "lastConnectedAt": None,
                "lastDisconnectedAt": None,
                "revokedAt": None,
            }
            self._bridges[final_bridge_id] = record
            await self._save_locked()

        return {
            "token": token,
            "bridge": self._sanitize_record(record),
        }

    async def revoke_bridge(self, bridge_id: str) -> dict[str, Any]:
        await self._ensure_loaded()
        async with self._lock:
            record = self._bridges.get(bridge_id)
            if record is None:
                raise ValueError(f"bridgeId 不存在: {bridge_id}")
            if not record.get("revokedAt"):
                revoked_was_default = bool(record.get("isDefault"))
                record["revokedAt"] = _utc_now()
                record["isDefault"] = False
                if revoked_was_default:
                    remaining = [
                        bridge
                        for bridge in self._bridges.values()
                        if bridge is not record and not bridge.get("revokedAt")
                    ]
                    if remaining:
                        newest = sorted(
                            remaining,
                            key=lambda item: item.get("createdAt", ""),
                            reverse=True,
                        )[0]
                        newest["isDefault"] = True
                await self._save_locked()
            return self._sanitize_record(record)

    async def verify_token(self, token: str) -> dict[str, Any] | None:
        if not token:
            return None

        await self._ensure_loaded()
        token_hash = self._hash_token(token)
        async with self._lock:
            for record in self._bridges.values():
                if record.get("revokedAt"):
                    continue
                if record.get("tokenHash") == token_hash:
                    return dict(record)
        return None

    async def touch_connected(self, bridge_id: str):
        await self._touch(bridge_id, "lastConnectedAt")

    async def touch_disconnected(self, bridge_id: str):
        await self._touch(bridge_id, "lastDisconnectedAt")

    async def _touch(self, bridge_id: str, key: str):
        await self._ensure_loaded()
        async with self._lock:
            record = self._bridges.get(bridge_id)
            if record is None:
                return
            record[key] = _utc_now()
            await self._save_locked()

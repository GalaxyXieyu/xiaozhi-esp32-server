"""Persist device-level voice interrupt overrides for runtime reconnects."""

from __future__ import annotations

import asyncio
import json
import os
import time
from typing import Any

from config.logger import setup_logging

TAG = __name__


class VoiceInterruptSettingsStore:
    def __init__(self, config: dict):
        self.logger = setup_logging()
        self._lock = asyncio.Lock()
        self._settings: dict[str, dict[str, Any]] = {}
        self.file_path = self._resolve_store_file(config)
        self._load_from_disk()

    def _resolve_store_file(self, config: dict) -> str:
        hub_config = config.get("openclaw_hub", {}) or {}
        explicit_path = str(hub_config.get("voice_interrupt_store_file") or "").strip()
        if explicit_path:
            return explicit_path

        bridge_store_file = str(hub_config.get("store_file") or "").strip()
        if bridge_store_file:
            return os.path.join(
                os.path.dirname(bridge_store_file), "voice_interrupt_settings.json"
            )

        return os.path.join("data", "openclaw", "voice_interrupt_settings.json")

    def _load_from_disk(self) -> None:
        if not self.file_path or not os.path.exists(self.file_path):
            return

        try:
            with open(self.file_path, "r", encoding="utf-8") as f:
                raw = json.load(f)
        except Exception as e:
            self.logger.bind(tag=TAG).warning(f"加载语音打断持久化配置失败: {e}")
            return

        if not isinstance(raw, dict):
            return

        loaded: dict[str, dict[str, Any]] = {}
        for device_id, value in raw.items():
            normalized = self._normalize_entry(value)
            if not device_id or normalized is None:
                continue
            loaded[device_id] = normalized
        self._settings = loaded

    def _normalize_entry(self, value: Any) -> dict[str, Any] | None:
        if isinstance(value, bool):
            return {
                "enable_voice_interrupt": value,
                "updatedAt": int(time.time() * 1000),
            }

        if not isinstance(value, dict):
            return None

        enabled = value.get("enable_voice_interrupt")
        if not isinstance(enabled, bool):
            return None

        updated_at = value.get("updatedAt")
        if not isinstance(updated_at, (int, float)):
            updated_at = int(time.time() * 1000)

        return {
            "enable_voice_interrupt": enabled,
            "updatedAt": int(updated_at),
        }

    def get_device_voice_interrupt(self, device_id: str | None) -> bool | None:
        normalized_device_id = (device_id or "").strip()
        if not normalized_device_id:
            return None
        entry = self._settings.get(normalized_device_id)
        if not isinstance(entry, dict):
            return None
        enabled = entry.get("enable_voice_interrupt")
        return enabled if isinstance(enabled, bool) else None

    def list_device_ids(self) -> set[str]:
        return set(self._settings.keys())

    async def set_device_voice_interrupt(
        self, device_id: str, enabled: bool
    ) -> dict[str, Any]:
        normalized_device_id = (device_id or "").strip()
        if not normalized_device_id:
            raise RuntimeError("deviceId 不能为空")

        async with self._lock:
            self._settings[normalized_device_id] = {
                "enable_voice_interrupt": bool(enabled),
                "updatedAt": int(time.time() * 1000),
            }
            self._save_to_disk()

        self.logger.bind(tag=TAG).info(
            f"持久化设备语音打断配置: device={normalized_device_id}, enabled={enabled}"
        )
        return {
            "deviceId": normalized_device_id,
            "enabled": bool(enabled),
            "persisted": True,
        }

    def _save_to_disk(self) -> None:
        directory = os.path.dirname(self.file_path)
        if directory:
            os.makedirs(directory, exist_ok=True)

        tmp_path = f"{self.file_path}.tmp"
        with open(tmp_path, "w", encoding="utf-8") as f:
            json.dump(self._settings, f, ensure_ascii=False, indent=2, sort_keys=True)
        os.replace(tmp_path, self.file_path)

"""Track active xiaozhi device connections for OpenClaw push delivery."""

from __future__ import annotations

import asyncio
import time
from typing import Any

from config.logger import setup_logging

TAG = __name__


class XiaozhiActiveConnectionRegistry:
    def __init__(self):
        self.logger = setup_logging()
        self._lock = asyncio.Lock()
        self._by_session_id: dict[str, Any] = {}
        self._by_device_id: dict[str, Any] = {}
        self._registered_at: dict[str, float] = {}
        self._latest_session_id: str | None = None

    async def register(self, conn: Any):
        session_id = (getattr(conn, "session_id", "") or "").strip()
        device_id = (getattr(conn, "device_id", "") or "").strip()
        if not session_id:
            return

        async with self._lock:
            self._by_session_id[session_id] = conn
            if device_id:
                self._by_device_id[device_id] = conn
            self._registered_at[session_id] = time.time()
            self._latest_session_id = session_id

        self.logger.bind(tag=TAG).info(
            f"注册小智在线连接: session={session_id}, device={device_id or 'unknown'}"
        )

    async def unregister(self, conn: Any):
        session_id = (getattr(conn, "session_id", "") or "").strip()
        device_id = (getattr(conn, "device_id", "") or "").strip()

        async with self._lock:
            if session_id and self._by_session_id.get(session_id) is conn:
                self._by_session_id.pop(session_id, None)
                self._registered_at.pop(session_id, None)
                if self._latest_session_id == session_id:
                    self._latest_session_id = None
            if device_id and self._by_device_id.get(device_id) is conn:
                self._by_device_id.pop(device_id, None)

        if session_id or device_id:
            self.logger.bind(tag=TAG).info(
                f"移除小智在线连接: session={session_id or 'unknown'}, device={device_id or 'unknown'}"
            )

    async def push_text(
        self,
        text: str,
        *,
        session_id: str | None = None,
        device_id: str | None = None,
        peer_id: str | None = None,
        allow_latest: bool = False,
    ) -> dict[str, Any]:
        message = (text or "").strip()
        if not message:
            raise RuntimeError("pushText 缺少 text")

        conn = await self._resolve_connection(
            session_id=session_id,
            device_id=device_id,
            peer_id=peer_id,
            allow_latest=allow_latest,
        )
        if conn is None:
            raise RuntimeError(
                "没有找到在线的小智设备连接"
                f"(sessionId={session_id or ''}, deviceId={device_id or ''}, peerId={peer_id or ''})"
            )

        await conn.push_text_from_openclaw(message)
        return {
            "ok": True,
            "delivered": True,
            "sessionId": getattr(conn, "session_id", None),
            "deviceId": getattr(conn, "device_id", None),
        }

    async def relay_chat(
        self,
        text: str,
        *,
        session_id: str | None = None,
        device_id: str | None = None,
        peer_id: str | None = None,
        allow_latest: bool = False,
    ) -> dict[str, Any]:
        message = (text or "").strip()
        if not message:
            raise RuntimeError("chat 缺少 text")

        conn = await self._resolve_connection(
            session_id=session_id,
            device_id=device_id,
            peer_id=peer_id,
            allow_latest=allow_latest,
        )
        if conn is None:
            raise RuntimeError(
                "没有找到在线的小智设备连接"
                f"(sessionId={session_id or ''}, deviceId={device_id or ''}, peerId={peer_id or ''})"
            )

        relayed = await conn.relay_chat_to_openclaw(message)
        return {
            "ok": True,
            "relayed": bool(relayed),
            "sessionId": getattr(conn, "session_id", None),
            "deviceId": getattr(conn, "device_id", None),
        }

    async def set_openclaw_async_waiting(
        self,
        enabled: bool,
        *,
        session_id: str | None = None,
        device_id: str | None = None,
        peer_id: str | None = None,
        allow_latest: bool = False,
        source: str | None = None,
        reason: str | None = None,
    ) -> dict[str, Any]:
        conn = await self._resolve_connection(
            session_id=session_id,
            device_id=device_id,
            peer_id=peer_id,
            allow_latest=allow_latest,
        )
        if conn is None:
            return {
                "enabled": bool(enabled),
                "updatedCount": 0,
                "sessionId": session_id,
                "deviceId": device_id,
            }

        conn.set_openclaw_async_waiting(
            bool(enabled),
            source=source,
            reason=reason,
        )
        resolved_session_id = getattr(conn, "session_id", None)
        resolved_device_id = getattr(conn, "device_id", None)
        self.logger.bind(tag=TAG).info(
            "定向更新 OpenClaw 异步等待态: "
            f"enabled={bool(enabled)}, session={resolved_session_id or ''}, "
            f"device={resolved_device_id or ''}, source={str(source or '').strip() or '-'}"
        )
        return {
            "enabled": bool(enabled),
            "updatedCount": 1,
            "sessionId": resolved_session_id,
            "deviceId": resolved_device_id,
        }

    async def list_connections(self) -> list[dict[str, Any]]:
        async with self._lock:
            items: list[dict[str, Any]] = []
            for session_id, conn in self._by_session_id.items():
                if not self._is_active(conn):
                    continue
                items.append(
                    {
                        "sessionId": session_id,
                        "deviceId": getattr(conn, "device_id", None),
                        "clientIp": getattr(conn, "client_ip", None),
                        "registeredAt": self._registered_at.get(session_id),
                        "isLatest": session_id == self._latest_session_id,
                        "voiceInterruptEnabled": bool(
                            getattr(conn, "config", {}).get(
                                "enable_voice_interrupt", True
                            )
                        ),
                    }
                )

        items.sort(key=lambda item: item.get("registeredAt") or 0, reverse=True)
        return items

    def _apply_voice_interrupt(
        self, conn: Any, enabled: bool, *, update_common_config: bool
    ) -> None:
        conn.config["enable_voice_interrupt"] = enabled
        common_config = getattr(conn, "common_config", None)
        if update_common_config and isinstance(common_config, dict):
            common_config["enable_voice_interrupt"] = enabled

    async def set_voice_interrupt_enabled(
        self, enabled: bool, *, skip_device_ids: set[str] | None = None
    ) -> dict[str, Any]:
        updated_count = 0
        skipped_count = 0
        normalized_skip_ids = {
            (device_id or "").strip()
            for device_id in (skip_device_ids or set())
            if device_id
        }

        async with self._lock:
            for session_id, conn in list(self._by_session_id.items()):
                if not self._is_active(conn):
                    self._by_session_id.pop(session_id, None)
                    self._registered_at.pop(session_id, None)
                    device_id = (getattr(conn, "device_id", "") or "").strip()
                    if device_id and self._by_device_id.get(device_id) is conn:
                        self._by_device_id.pop(device_id, None)
                    continue

                device_id = (getattr(conn, "device_id", "") or "").strip()
                if device_id and device_id in normalized_skip_ids:
                    skipped_count += 1
                    continue

                self._apply_voice_interrupt(
                    conn, enabled, update_common_config=True
                )
                updated_count += 1

        self.logger.bind(tag=TAG).info(
            f"批量更新语音打断开关: enabled={enabled}, updated={updated_count}, skipped={skipped_count}"
        )
        return {
            "enabled": enabled,
            "updatedCount": updated_count,
            "skippedCount": skipped_count,
        }

    async def set_voice_interrupt_for_connection(
        self,
        enabled: bool,
        *,
        session_id: str | None = None,
        device_id: str | None = None,
        peer_id: str | None = None,
        allow_latest: bool = False,
    ) -> dict[str, Any]:
        conn = await self._resolve_connection(
            session_id=session_id,
            device_id=device_id,
            peer_id=peer_id,
            allow_latest=allow_latest,
        )
        if conn is None:
            return {
                "enabled": enabled,
                "updatedCount": 0,
                "sessionId": session_id,
                "deviceId": device_id,
            }

        self._apply_voice_interrupt(conn, enabled, update_common_config=False)
        resolved_session_id = getattr(conn, "session_id", None)
        resolved_device_id = getattr(conn, "device_id", None)
        self.logger.bind(tag=TAG).info(
            f"定向更新语音打断开关: enabled={enabled}, session={resolved_session_id or ''}, device={resolved_device_id or ''}"
        )
        return {
            "enabled": enabled,
            "updatedCount": 1,
            "sessionId": resolved_session_id,
            "deviceId": resolved_device_id,
        }

    async def get_voice_interrupt_state(
        self,
        *,
        session_id: str | None = None,
        device_id: str | None = None,
        peer_id: str | None = None,
        allow_latest: bool = False,
    ) -> dict[str, Any] | None:
        conn = await self._resolve_connection(
            session_id=session_id,
            device_id=device_id,
            peer_id=peer_id,
            allow_latest=allow_latest,
        )
        if conn is None:
            return None

        return {
            "enabled": bool(conn.config.get("enable_voice_interrupt", True)),
            "sessionId": getattr(conn, "session_id", None),
            "deviceId": getattr(conn, "device_id", None),
        }

    async def _resolve_connection(
        self,
        *,
        session_id: str | None = None,
        device_id: str | None = None,
        peer_id: str | None = None,
        allow_latest: bool = False,
    ):
        candidate_device_id = (
            device_id or self._device_id_from_peer_id(peer_id) or ""
        ).strip()

        async with self._lock:
            if session_id:
                conn = self._by_session_id.get(session_id)
                if self._is_active(conn):
                    return conn
                self._by_session_id.pop(session_id, None)

            if candidate_device_id:
                conn = self._by_device_id.get(candidate_device_id)
                if self._is_active(conn):
                    return conn
                self._by_device_id.pop(candidate_device_id, None)

            if allow_latest:
                latest_conn = self._resolve_latest_connection_locked()
                if latest_conn is not None:
                    return latest_conn

        return None

    def _resolve_latest_connection_locked(self):
        latest_session_id = self._latest_session_id
        if latest_session_id:
            conn = self._by_session_id.get(latest_session_id)
            if self._is_active(conn):
                return conn

        active_candidates = [
            (self._registered_at.get(session_id, 0), conn)
            for session_id, conn in self._by_session_id.items()
            if self._is_active(conn)
        ]
        if not active_candidates:
            return None
        active_candidates.sort(key=lambda item: item[0], reverse=True)
        return active_candidates[0][1]

    def _device_id_from_peer_id(self, peer_id: str | None) -> str:
        value = (peer_id or "").strip()
        if not value:
            return ""
        return value.split(":", 1)[0].strip()

    def _is_active(self, conn: Any) -> bool:
        if conn is None:
            return False
        websocket = getattr(conn, "websocket", None)
        if websocket is None:
            return False
        if getattr(websocket, "closed", False):
            return False
        state = getattr(getattr(websocket, "state", None), "name", "")
        return state != "CLOSED"

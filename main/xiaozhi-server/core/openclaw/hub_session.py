"""Connection-scoped adapter for the inbound OpenClaw bridge hub."""

from __future__ import annotations

from typing import Any, Optional

from config.logger import setup_logging

TAG = __name__


class OpenClawHubSession:
    def __init__(self, conn):
        self.conn = conn
        self.logger = setup_logging()
        self.config = conn.config.get("openclaw_hub", {}) or {}
        self.hub = getattr(getattr(conn, "server", None), "openclaw_hub", None)
        self.enabled = bool(self.config.get("enabled", False) and self.hub)
        self._session_started = False

    def relay_chat_enabled(self) -> bool:
        return self.enabled and bool(self.config.get("relay_chat", False))

    def fallback_to_local_on_error(self) -> bool:
        return bool(self.config.get("fallback_to_local_on_error", True))

    async def connect(self) -> bool:
        if not self.enabled:
            self.logger.bind(tag=TAG).warning("OpenClaw hub session 未启用，跳过 relay")
            return False
        params = self._build_peer_context()
        available = await self.hub.has_available_bridge(
            bridge_id=self._resolve_bridge_id(),
            account=self._resolve_account(),
        )
        self.logger.bind(tag=TAG).info(
            "OpenClaw hub 可用性检查: "
            f"available={available}, account={params['account']}, "
            f"peer={params['peerId']}, bridge={self._resolve_bridge_id() or 'default'}"
        )
        return available

    async def close(self):
        if (
            not self.enabled
            or not self._session_started
            or not self.config.get("session_events_enabled", True)
        ):
            return

        try:
            await self.hub.request(
                self.config.get("session_ended_method", "xiaozhi.sessionEnded"),
                self._build_peer_context(),
                bridge_id=self._resolve_bridge_id(),
                account=self._resolve_account(),
            )
        except Exception as e:
            self.logger.bind(tag=TAG).warning(f"OpenClaw sessionEnded 调用失败: {e}")
        finally:
            self._session_started = False

    async def chat(self, text: str) -> Any:
        await self._ensure_session_started()
        params = self._build_peer_context()
        params["text"] = text
        self.logger.bind(tag=TAG).info(
            "OpenClaw hub chat 请求: "
            f"account={params['account']}, peer={params['peerId']}, "
            f"bridge={self._resolve_bridge_id() or 'default'}, method={self.config.get('chat_method', 'xiaozhi.chat')}"
        )
        return await self.hub.request(
            self.config.get("chat_method", "xiaozhi.chat"),
            params,
            bridge_id=self._resolve_bridge_id(),
            account=self._resolve_account(),
        )

    async def bind_peer_agent(
        self,
        agent_id: str,
        peer_id: Optional[str] = None,
        agent_name: Optional[str] = None,
    ) -> dict[str, Any]:
        await self._ensure_session_started()
        params = self._build_peer_context()
        params["peerId"] = peer_id or params["peerId"]
        params["agentId"] = agent_id
        if agent_name:
            params["agentName"] = agent_name

        self.logger.bind(tag=TAG).info(
            "OpenClaw hub 绑定 peer agent: "
            f"account={params['account']}, peer={params['peerId']}, agent={agent_id}, "
            f"bridge={self._resolve_bridge_id() or 'default'}"
        )

        result = await self.hub.request(
            self.config.get("bind_method", "xiaozhi.bindPeerAgent"),
            params,
            bridge_id=self._resolve_bridge_id(),
            account=self._resolve_account(),
        )
        if isinstance(result, dict):
            return result
        return {"ok": True, "result": result, "agentId": agent_id}

    async def _ensure_session_started(self):
        if self._session_started or not self.config.get("session_events_enabled", True):
            return

        params = self._build_peer_context()
        self.logger.bind(tag=TAG).info(
            "OpenClaw hub sessionStarted: "
            f"account={params['account']}, peer={params['peerId']}, "
            f"bridge={self._resolve_bridge_id() or 'default'}"
        )
        await self.hub.request(
            self.config.get("session_started_method", "xiaozhi.sessionStarted"),
            params,
            bridge_id=self._resolve_bridge_id(),
            account=self._resolve_account(),
        )
        self._session_started = True

    def _resolve_bridge_id(self) -> str | None:
        bridge_id = (self.config.get("bridge_id") or "").strip()
        return bridge_id or None

    def _resolve_account(self) -> str:
        return (self.config.get("default_account") or "default").strip()

    def _build_peer_context(self) -> dict[str, Any]:
        device_id = (
            self.conn.device_id
            or self.conn.headers.get("device-id")
            or self.conn.session_id
        )
        client_id = self.conn.headers.get("client-id", device_id)
        speaker = self.conn.current_speaker
        peer_id = self._resolve_peer_id(device_id, client_id, speaker)

        params = {
            "account": self._resolve_account(),
            "sessionId": self.conn.session_id,
            "deviceId": device_id,
            "clientId": client_id,
            "peerId": peer_id,
        }
        if speaker:
            params["speaker"] = speaker
        return params

    def _resolve_peer_id(
        self,
        device_id: str,
        client_id: Optional[str],
        speaker: Optional[str],
    ) -> str:
        mode = (
            self.config.get("peer_id_mode")
            or self.config.get("default_peer_id_mode")
            or "device"
        ).strip()

        if mode == "device_client" and client_id and client_id != device_id:
            return f"{device_id}:{client_id}"

        if mode == "device_speaker" and speaker:
            return f"{device_id}:{speaker}"

        return device_id

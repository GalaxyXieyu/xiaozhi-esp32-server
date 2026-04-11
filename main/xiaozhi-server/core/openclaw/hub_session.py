"""Connection-scoped adapter for the inbound OpenClaw bridge hub."""

from __future__ import annotations

import uuid
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
        self._bound_agent_id: str | None = None

    def relay_chat_enabled(self) -> bool:
        return self.enabled and bool(self.config.get("relay_chat", False))

    def fallback_to_local_on_error(self) -> bool:
        return bool(self.config.get("fallback_to_local_on_error", True))

    async def connect(self) -> bool:
        if not self.enabled:
            self.logger.bind(tag=TAG).warning("OpenClaw hub session 未启用，跳过 relay")
            self._record_event(
                "hub_connect_skipped",
                summary_text="OpenClaw hub session 未启用，跳过 relay",
                status="error",
            )
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
        self._record_event(
            "hub_availability_checked",
            summary_text=f"OpenClaw hub 可用性检查: {available}",
            payload={
                "available": available,
                "bridgeId": self._resolve_bridge_id(),
                "params": params,
            },
            status="ok" if available else "error",
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
            await self._request(
                self.config.get("session_ended_method", "xiaozhi.sessionEnded"),
                self._build_peer_context(),
            )
        except Exception as e:
            self.logger.bind(tag=TAG).warning(f"OpenClaw sessionEnded 调用失败: {e}")
        finally:
            self._session_started = False
            self._bound_agent_id = None

    async def chat(self, text: str) -> Any:
        await self._ensure_session_started()
        await self._ensure_agent_bound()
        params = self._build_peer_context()
        params["text"] = text
        self.logger.bind(tag=TAG).info(
            "OpenClaw hub chat 请求: "
            f"account={params['account']}, peer={params['peerId']}, "
            f"bridge={self._resolve_bridge_id() or 'default'}, method={self.config.get('chat_method', 'xiaozhi.chat')}"
        )
        return await self._request(
            self.config.get("chat_method", "xiaozhi.chat"),
            params,
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

        result = await self._request(
            self.config.get("bind_method", "xiaozhi.bindPeerAgent"),
            params,
        )
        if isinstance(result, dict):
            return result
        return {"ok": True, "result": result, "agentId": agent_id}

    async def _ensure_agent_bound(self):
        binding = self._get_configured_agent_binding()
        if binding is None:
            return
        agent_id = binding["agentId"]
        if self._bound_agent_id == agent_id:
            return

        result = await self.bind_peer_agent(
            agent_id=agent_id,
            agent_name=binding.get("agentName"),
        )
        self._bound_agent_id = agent_id
        self.logger.bind(tag=TAG).info(
            "OpenClaw hub 自动绑定 agent 完成: "
            f"account={self._resolve_account()}, peer={self._build_peer_context()['peerId']}, "
            f"agent={agent_id}, result={result}"
        )

    async def _ensure_session_started(self):
        if self._session_started or not self.config.get("session_events_enabled", True):
            return

        params = self._build_peer_context()
        self.logger.bind(tag=TAG).info(
            "OpenClaw hub sessionStarted: "
            f"account={params['account']}, peer={params['peerId']}, "
            f"bridge={self._resolve_bridge_id() or 'default'}"
        )
        await self._request(
            self.config.get("session_started_method", "xiaozhi.sessionStarted"),
            params,
        )
        self._session_started = True

    def _record_event(
        self,
        event_type: str,
        *,
        direction: str = "internal",
        summary_text: str | None = None,
        payload: dict | None = None,
        request_id: str | None = None,
        status: str = "ok",
    ):
        self.conn.record_debug_event(
            event_source="openclaw",
            event_type=event_type,
            direction=direction,
            origin="openclaw",
            summary_text=summary_text,
            payload=payload,
            request_id=request_id,
            status=status,
        )

    async def _request(self, method: str, params: dict[str, Any]):
        request_id = uuid.uuid4().hex
        self._record_event(
            "hub_request_sent",
            direction="outbound",
            summary_text=f"OpenClaw hub 请求: {method}",
            payload={
                "method": method,
                "params": params,
                "bridgeId": self._resolve_bridge_id(),
                "account": self._resolve_account(),
            },
            request_id=request_id,
        )
        try:
            result = await self.hub.request(
                method,
                params,
                bridge_id=self._resolve_bridge_id(),
                account=self._resolve_account(),
            )
            self._record_event(
                "hub_result_received",
                direction="inbound",
                summary_text=f"OpenClaw hub 返回结果: {method}",
                payload={"method": method, "result": result},
                request_id=request_id,
            )
            return result
        except Exception as exc:
            self._record_event(
                "hub_request_error",
                direction="internal",
                summary_text=f"OpenClaw hub 请求失败: {method}",
                payload={"method": method, "error": str(exc)},
                request_id=request_id,
                status="error",
            )
            raise

    def _get_configured_agent_binding(self) -> dict[str, str] | None:
        binding = self.conn.config.get("openclaw_binding") or {}
        if not isinstance(binding, dict):
            return None
        agent_type = str(binding.get("agentType") or "").strip()
        agent_id = str(binding.get("openclawAgentId") or "").strip()
        if agent_type and agent_type != "openclaw":
            return None
        if not agent_id:
            return None
        agent_name = str(binding.get("openclawAgentName") or "").strip()
        return {
            "agentId": agent_id,
            "agentName": agent_name,
        }

    def _resolve_bridge_id(self) -> str | None:
        bridge_id = (self.config.get("bridge_id") or "").strip()
        return bridge_id or None

    def _resolve_bound_runtime_account(self) -> str | None:
        binding = self.conn.config.get("openclaw_binding") or {}
        if not isinstance(binding, dict):
            return None
        runtime_account = str(binding.get("runtimeAccount") or "").strip()
        return runtime_account or None

    def _resolve_account(self) -> str:
        bound_account = self._resolve_bound_runtime_account()
        if bound_account:
            return bound_account
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
        delivery_binding = self._build_delivery_binding_snapshot()
        if delivery_binding:
            params["deliveryBinding"] = delivery_binding
        return params

    def _build_delivery_binding_snapshot(self) -> dict[str, Any] | None:
        binding = self.conn.config.get("openclaw_binding") or {}
        if not isinstance(binding, dict):
            return None
        delivery_binding = binding.get("deliveryBinding") or {}
        if not isinstance(delivery_binding, dict):
            return None

        enabled = bool(delivery_binding.get("enabled"))
        if not enabled:
            return None

        delivery_channel = str(delivery_binding.get("deliveryChannel") or "").strip()
        account_id = str(delivery_binding.get("accountId") or "").strip()
        target = str(delivery_binding.get("target") or "").strip()
        thread_id = str(delivery_binding.get("threadId") or "").strip()
        fmt = str(delivery_binding.get("format") or "text").strip() or "text"

        if not delivery_channel or not target:
            return None

        snapshot: dict[str, Any] = {
            "enabled": True,
            "deliveryChannel": delivery_channel,
            "target": target,
            "format": fmt,
        }
        if account_id:
            snapshot["accountId"] = account_id
        if thread_id:
            snapshot["threadId"] = thread_id
        return snapshot

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

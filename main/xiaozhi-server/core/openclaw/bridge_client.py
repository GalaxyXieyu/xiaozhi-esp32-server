"""OpenClaw bridge client for xiaozhi-server."""

import asyncio
import json
from typing import Any, Dict, Optional
from urllib.parse import parse_qsl, urlencode, urlsplit, urlunsplit

import websockets
from websockets.exceptions import ConnectionClosed

from config.logger import setup_logging

TAG = __name__


class OpenClawBridgeClient:
    """Persistent JSON-RPC client used to relay xiaozhi traffic to OpenClaw."""

    def __init__(self, conn):
        self.conn = conn
        self.logger = setup_logging()
        self.config = conn.config.get("openclaw_bridge", {}) or {}
        self.enabled = bool(self.config.get("enabled", False))
        self.websocket = None
        self.listener_task: Optional[asyncio.Task] = None
        self.ready = False
        self.lock = asyncio.Lock()
        self.pending_results: Dict[int, asyncio.Future] = {}
        self.next_id = 1
        self._bound_agent_id: Optional[str] = None

    def relay_chat_enabled(self) -> bool:
        return self.enabled and bool(self.config.get("relay_chat", False))

    def fallback_to_local_on_error(self) -> bool:
        return bool(self.config.get("fallback_to_local_on_error", True))

    async def is_ready(self) -> bool:
        async with self.lock:
            return self.ready and self.websocket is not None

    async def connect(self) -> bool:
        if not self.enabled:
            return False

        if await self.is_ready():
            return True

        url = self._build_url()
        if not url:
            self.logger.bind(tag=TAG).warning("OpenClaw bridge 已启用，但未配置 url")
            return False

        timeout = float(self.config.get("connect_timeout_seconds", 10))
        try:
            self.logger.bind(tag=TAG).info(f"正在连接 OpenClaw bridge: {url}")
            websocket = await asyncio.wait_for(
                websockets.connect(url, ping_interval=20, ping_timeout=20),
                timeout=timeout,
            )
            async with self.lock:
                self.websocket = websocket
                self.ready = True

            self.listener_task = asyncio.create_task(self._listen())
            await self._notify_session_started()
            self.logger.bind(tag=TAG).info("OpenClaw bridge 连接成功")
            return True
        except Exception as e:
            async with self.lock:
                self.ready = False
                self.websocket = None
            self.logger.bind(tag=TAG).error(f"OpenClaw bridge 连接失败: {e}")
            return False

    async def close(self):
        if self.enabled:
            await self._notify_session_ended()

        async with self.lock:
            websocket = self.websocket
            self.websocket = None
            self.ready = False
            self._bound_agent_id = None

            pending_results = list(self.pending_results.values())
            self.pending_results.clear()

        for future in pending_results:
            if not future.done():
                future.set_exception(RuntimeError("OpenClaw bridge 已关闭"))

        if self.listener_task and not self.listener_task.done():
            self.listener_task.cancel()
            try:
                await self.listener_task
            except asyncio.CancelledError:
                pass
            self.listener_task = None

        if websocket is not None:
            try:
                await websocket.close()
            except Exception:
                pass

    async def chat(self, text: str) -> Any:
        await self._ensure_agent_bound()
        params = self._build_peer_context()
        params["text"] = text
        return await self.call(
            self.config.get("chat_method", "xiaozhi.chat"),
            params,
        )

    async def bind_peer_agent(
        self,
        agent_id: str,
        peer_id: Optional[str] = None,
        agent_name: Optional[str] = None,
    ) -> Dict[str, Any]:
        params = self._build_peer_context()
        params["peerId"] = peer_id or params["peerId"]
        params["agentId"] = agent_id
        if agent_name:
            params["agentName"] = agent_name

        result = await self.call(
            self.config.get("bind_method", "xiaozhi.bindPeerAgent"),
            params,
        )
        if isinstance(result, dict):
            return result
        return {"ok": True, "result": result, "agentId": agent_id}

    async def _ensure_agent_bound(self) -> None:
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
            f"OpenClaw bridge 自动绑定 agent 完成: agent={agent_id}, result={result}"
        )

    async def call(self, method: str, params: Optional[Dict[str, Any]] = None) -> Any:
        if not method:
            raise RuntimeError("OpenClaw bridge RPC method 未配置")

        if not await self.connect():
            raise RuntimeError("OpenClaw bridge 未连接")

        request_id = await self._get_next_id()
        payload = {
            "jsonrpc": "2.0",
            "id": request_id,
            "method": method,
            "params": params or {},
        }

        future = asyncio.get_running_loop().create_future()
        async with self.lock:
            self.pending_results[request_id] = future
            websocket = self.websocket

        if websocket is None:
            async with self.lock:
                self.pending_results.pop(request_id, None)
            raise RuntimeError("OpenClaw bridge websocket 不可用")

        try:
            await websocket.send(json.dumps(payload, ensure_ascii=False))
            timeout = float(self.config.get("request_timeout_seconds", 60))
            return await asyncio.wait_for(future, timeout=timeout)
        except Exception:
            async with self.lock:
                self.pending_results.pop(request_id, None)
            raise

    async def _listen(self):
        try:
            while self.websocket is not None:
                message = await self.websocket.recv()
                if isinstance(message, bytes):
                    message = message.decode("utf-8")

                payload = json.loads(message)
                await self._handle_message(payload)
        except asyncio.CancelledError:
            raise
        except ConnectionClosed as e:
            self.logger.bind(tag=TAG).warning(
                f"OpenClaw bridge 连接关闭: code={e.code}, reason={e.reason}"
            )
        except Exception as e:
            self.logger.bind(tag=TAG).error(f"OpenClaw bridge 监听异常: {e}")
        finally:
            await self._mark_closed(RuntimeError("OpenClaw bridge 连接已断开"))

    async def _handle_message(self, payload: Dict[str, Any]):
        if "id" in payload and ("result" in payload or "error" in payload):
            request_id = int(payload["id"])
            async with self.lock:
                future = self.pending_results.pop(request_id, None)

            if future is None or future.done():
                return

            if "error" in payload:
                error_data = payload["error"]
                if isinstance(error_data, dict):
                    message = error_data.get("message", "未知错误")
                else:
                    message = str(error_data)
                future.set_exception(RuntimeError(message))
            else:
                future.set_result(payload.get("result"))
            return

        if "method" in payload:
            method = payload.get("method", "")
            params = payload.get("params", {})
            self.logger.bind(tag=TAG).debug(
                f"收到 OpenClaw bridge 通知: {method}, params={params}"
            )

    async def _mark_closed(self, exc: Exception):
        async with self.lock:
            if self.websocket is not None:
                try:
                    await self.websocket.close()
                except Exception:
                    pass
            self.websocket = None
            self.ready = False
            self._bound_agent_id = None
            pending_results = list(self.pending_results.values())
            self.pending_results.clear()

        for future in pending_results:
            if not future.done():
                future.set_exception(exc)

    async def _get_next_id(self) -> int:
        async with self.lock:
            current_id = self.next_id
            self.next_id += 1
            return current_id

    def _build_url(self) -> str:
        url = (self.config.get("url") or "").strip()
        if not url:
            return ""

        token = (self.config.get("token") or "").strip()
        if not token:
            return url

        parsed = urlsplit(url)
        query = dict(parse_qsl(parsed.query, keep_blank_values=True))
        query.setdefault("token", token)
        return urlunsplit(
            (parsed.scheme, parsed.netloc, parsed.path, urlencode(query), parsed.fragment)
        )

    def _build_peer_context(self) -> Dict[str, Any]:
        device_id = (
            self.conn.device_id
            or self.conn.headers.get("device-id")
            or self.conn.session_id
        )
        client_id = self.conn.headers.get("client-id", device_id)
        speaker = self.conn.current_speaker
        peer_id = self._resolve_peer_id(device_id, client_id, speaker)

        params = {
            "account": self.config.get("account", "default"),
            "sessionId": self.conn.session_id,
            "deviceId": device_id,
            "clientId": client_id,
            "peerId": peer_id,
        }
        if speaker:
            params["speaker"] = speaker
        return params

    def _get_configured_agent_binding(self) -> Optional[Dict[str, str]]:
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

    def _resolve_peer_id(
        self,
        device_id: str,
        client_id: Optional[str],
        speaker: Optional[str],
    ) -> str:
        mode = (self.config.get("peer_id_mode") or "device").strip()

        if mode == "device_client" and client_id and client_id != device_id:
            return f"{device_id}:{client_id}"

        if mode == "device_speaker" and speaker:
            return f"{device_id}:{speaker}"

        return device_id

    async def _notify_session_started(self):
        if not self.config.get("session_events_enabled", True):
            return

        try:
            await self.call(
                self.config.get("session_started_method", "xiaozhi.sessionStarted"),
                self._build_peer_context(),
            )
        except Exception as e:
            self.logger.bind(tag=TAG).warning(f"OpenClaw sessionStarted 调用失败: {e}")

    async def _notify_session_ended(self):
        if not self.config.get("session_events_enabled", True):
            return

        if self.websocket is None:
            return

        try:
            await self.call(
                self.config.get("session_ended_method", "xiaozhi.sessionEnded"),
                self._build_peer_context(),
            )
        except Exception as e:
            self.logger.bind(tag=TAG).warning(f"OpenClaw sessionEnded 调用失败: {e}")

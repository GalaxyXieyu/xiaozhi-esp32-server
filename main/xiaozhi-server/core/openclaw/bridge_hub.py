"""Bridge hub that accepts OpenClaw plugin websocket connections."""

from __future__ import annotations

import asyncio
import json
from typing import Any
from urllib.parse import parse_qs, urlparse

from aiohttp import web

from config.logger import setup_logging
from .bridge_store import OpenClawBridgeStore

TAG = __name__


class OpenClawHubConnection:
    def __init__(self, bridge_record: dict[str, Any], websocket: web.WebSocketResponse):
        self.bridge_record = bridge_record
        self.bridge_id = bridge_record["bridgeId"]
        self.account = bridge_record.get("account", "default")
        self.websocket = websocket
        self.logger = setup_logging()
        self._lock = asyncio.Lock()
        self._pending_results: dict[int, asyncio.Future] = {}
        self._next_id = 1
        self.closed = False

    async def call(self, method: str, params: dict[str, Any], timeout_seconds: float):
        if not method:
            raise RuntimeError("OpenClaw hub RPC method 未配置")

        async with self._lock:
            if self.closed or self.websocket.closed:
                raise RuntimeError("OpenClaw bridge 未连接")
            request_id = self._next_id
            self._next_id += 1
            future = asyncio.get_running_loop().create_future()
            self._pending_results[request_id] = future
            payload = {
                "jsonrpc": "2.0",
                "id": request_id,
                "method": method,
                "params": params or {},
            }
            try:
                await self._send_locked(payload)
            except Exception:
                self._pending_results.pop(request_id, None)
                raise

        try:
            return await asyncio.wait_for(future, timeout=timeout_seconds)
        except Exception:
            async with self._lock:
                self._pending_results.pop(request_id, None)
            raise

    async def handle_payload(self, payload: dict[str, Any]):
        if "id" not in payload or ("result" not in payload and "error" not in payload):
            method = payload.get("method", "")
            if method:
                self.logger.bind(tag=TAG).debug(
                    f"收到 OpenClaw bridge 通知: bridge={self.bridge_id}, method={method}"
                )
            return

        try:
            request_id = int(payload["id"])
        except (TypeError, ValueError):
            return
        async with self._lock:
            future = self._pending_results.pop(request_id, None)

        if future is None or future.done():
            return

        if "error" in payload:
            error = payload["error"]
            if isinstance(error, dict):
                message = error.get("message", "未知错误")
            else:
                message = str(error)
            future.set_exception(RuntimeError(message))
            return

        future.set_result(payload.get("result"))

    async def mark_closed(self, exc: Exception):
        async with self._lock:
            if self.closed:
                return
            self.closed = True
            pending_results = list(self._pending_results.values())
            self._pending_results.clear()

        for future in pending_results:
            if not future.done():
                future.set_exception(exc)

    async def close(self, *, code: int = 1000, message: bytes = b""):
        await self.mark_closed(RuntimeError("OpenClaw bridge 连接已关闭"))
        if not self.websocket.closed:
            try:
                await self.websocket.close(code=code, message=message)
            except Exception:
                pass

    async def send(self, payload: dict[str, Any]):
        async with self._lock:
            if self.closed or self.websocket.closed:
                raise RuntimeError("OpenClaw bridge 未连接")
            await self._send_locked(payload)

    async def _send_locked(self, payload: dict[str, Any]):
        await self.websocket.send_str(json.dumps(payload, ensure_ascii=False))


class OpenClawBridgeHub:
    def __init__(self, config: dict, connection_registry=None):
        self.config = config
        self.hub_config = config.get("openclaw_hub", {}) or {}
        self.logger = setup_logging()
        self.enabled = bool(self.hub_config.get("enabled", False))
        self.store = OpenClawBridgeStore(config)
        self.connection_registry = connection_registry
        self._connections_lock = asyncio.Lock()
        self._connections: dict[str, OpenClawHubConnection] = {}

    async def issue_bridge_token(self, **kwargs) -> dict[str, Any]:
        return await self.store.issue_bridge(**kwargs)

    async def revoke_bridge(self, bridge_id: str) -> dict[str, Any]:
        record = await self.store.revoke_bridge(bridge_id)
        connection = await self._get_connection(bridge_id)
        if connection is not None:
            await connection.close(code=1008, message="revoked".encode("utf-8"))
        return record

    async def list_bridges(self) -> list[dict[str, Any]]:
        records = await self.store.list_bridges()
        async with self._connections_lock:
            active_bridge_ids = {
                bridge_id
                for bridge_id, conn in self._connections.items()
                if not conn.closed and not conn.websocket.closed
            }
        for record in records:
            record["connected"] = record["bridgeId"] in active_bridge_ids
        return records

    async def has_available_bridge(
        self,
        *,
        bridge_id: str | None = None,
        account: str | None = None,
    ) -> bool:
        connection = await self._select_connection(bridge_id=bridge_id, account=account)
        return connection is not None

    async def request(
        self,
        method: str,
        params: dict[str, Any],
        *,
        bridge_id: str | None = None,
        account: str | None = None,
    ):
        connection = await self._select_connection(bridge_id=bridge_id, account=account)
        if connection is None:
            target = bridge_id or account or "default"
            raise RuntimeError(f"没有可用的 OpenClaw bridge 连接: {target}")
        timeout = float(self.hub_config.get("request_timeout_seconds", 60))
        return await connection.call(method, params, timeout)

    async def handle_websocket(self, request: web.Request) -> web.StreamResponse:
        if not self.enabled:
            return web.json_response(
                {"ok": False, "message": "OpenClaw hub 未启用"},
                status=404,
            )

        token = self._extract_token(request)
        bridge_record = await self.store.verify_token(token)
        if bridge_record is None:
            return web.json_response(
                {"ok": False, "message": "无效的 bridge token"},
                status=401,
            )

        websocket = web.WebSocketResponse(heartbeat=30)
        await websocket.prepare(request)
        connection = OpenClawHubConnection(bridge_record, websocket)
        await self._register_connection(connection)
        await self.store.touch_connected(connection.bridge_id)
        self.logger.bind(tag=TAG).info(
            f"OpenClaw bridge 已连接: bridge={connection.bridge_id}, account={connection.account}"
        )

        try:
            async for msg in websocket:
                if msg.type == web.WSMsgType.TEXT:
                    try:
                        payload = json.loads(msg.data)
                    except json.JSONDecodeError:
                        await websocket.send_str(
                            json.dumps(
                                {
                                    "jsonrpc": "2.0",
                                    "error": {"code": -32700, "message": "Parse error"},
                                },
                                ensure_ascii=False,
                            )
                        )
                        continue
                    if self._is_result_payload(payload):
                        await connection.handle_payload(payload)
                    else:
                        await self._handle_inbound_rpc(connection, payload)
                elif msg.type == web.WSMsgType.ERROR:
                    self.logger.bind(tag=TAG).warning(
                        f"OpenClaw bridge websocket 异常: {websocket.exception()}"
                    )
        finally:
            await self._unregister_connection(connection)
            await connection.mark_closed(RuntimeError("OpenClaw bridge 连接已断开"))
            await self.store.touch_disconnected(connection.bridge_id)
            self.logger.bind(tag=TAG).info(
                f"OpenClaw bridge 已断开: bridge={connection.bridge_id}"
            )

        return websocket

    async def close(self):
        async with self._connections_lock:
            connections = list(self._connections.values())
            self._connections.clear()

        for connection in connections:
            await connection.close(code=1001, message="server-shutdown".encode("utf-8"))

    def build_bridge_ws_url(self, request: web.Request) -> str:
        path = self.hub_config.get("bridge_ws_path", "/openclaw/bridge/ws")
        forwarded_proto = self._first_forwarded_value(
            request.headers.get("X-Forwarded-Proto", "")
        )
        forwarded_host = self._first_forwarded_value(
            request.headers.get("X-Forwarded-Host", "")
        )
        envoy_original_host = self._first_forwarded_value(
            request.headers.get("X-Envoy-Original-Host", "")
        )
        forwarded_port = self._first_forwarded_value(
            request.headers.get("X-Forwarded-Port", "")
        )
        host = forwarded_host or envoy_original_host or request.host
        scheme = forwarded_proto or request.scheme
        if forwarded_port == "443":
            scheme = "https"
        ws_scheme = "wss" if scheme == "https" else "ws"
        return f"{ws_scheme}://{host}{path}"

    def _first_forwarded_value(self, header_value: str) -> str:
        if not header_value:
            return ""
        return header_value.split(",")[0].strip()

    def _extract_token(self, request: web.Request) -> str:
        auth_header = request.headers.get("Authorization", "")
        if auth_header.startswith("Bearer "):
            return auth_header[7:].strip()

        query = parse_qs(urlparse(str(request.rel_url)).query)
        return (query.get("token", [""])[0] or "").strip()

    async def _register_connection(self, connection: OpenClawHubConnection):
        async with self._connections_lock:
            old_connection = self._connections.get(connection.bridge_id)
            self._connections[connection.bridge_id] = connection

        if old_connection is not None and old_connection is not connection:
            await old_connection.close(
                code=1008,
                message="replaced-by-new-connection".encode("utf-8"),
            )

    async def _unregister_connection(self, connection: OpenClawHubConnection):
        async with self._connections_lock:
            current = self._connections.get(connection.bridge_id)
            if current is connection:
                self._connections.pop(connection.bridge_id, None)

    async def _get_connection(self, bridge_id: str) -> OpenClawHubConnection | None:
        async with self._connections_lock:
            connection = self._connections.get(bridge_id)
            if connection is None or connection.closed or connection.websocket.closed:
                return None
            return connection

    async def _select_connection(
        self,
        *,
        bridge_id: str | None = None,
        account: str | None = None,
    ) -> OpenClawHubConnection | None:
        records = await self.store.list_bridges()
        async with self._connections_lock:
            active_connections = {
                bridge_id_: conn
                for bridge_id_, conn in self._connections.items()
                if not conn.closed and not conn.websocket.closed
            }

        if bridge_id:
            return active_connections.get(bridge_id)

        candidates = [
            record
            for record in records
            if record["bridgeId"] in active_connections and not record.get("revokedAt")
        ]
        if account:
            account_candidates = [
                record for record in candidates if record.get("account") == account
            ]
            if account_candidates:
                candidates = account_candidates

        if not candidates:
            return None

        default_candidates = [record for record in candidates if record.get("isDefault")]
        selected = default_candidates[0] if default_candidates else candidates[0]
        return active_connections.get(selected["bridgeId"])

    def _is_result_payload(self, payload: dict[str, Any]) -> bool:
        return "id" in payload and ("result" in payload or "error" in payload)

    async def _handle_inbound_rpc(
        self,
        connection: OpenClawHubConnection,
        payload: dict[str, Any],
    ):
        method = str(payload.get("method", "") or "").strip()
        request_id = payload.get("id")
        params = payload.get("params")
        if not isinstance(params, dict):
            params = {}

        if not method:
            if request_id is not None:
                await connection.send(
                    {
                        "jsonrpc": "2.0",
                        "id": request_id,
                        "error": {"code": -32600, "message": "Invalid Request"},
                    }
                )
            return

        try:
            result = await self._dispatch_method(
                connection=connection,
                method=method,
                params=params,
            )
            if request_id is not None:
                await connection.send(
                    {"jsonrpc": "2.0", "id": request_id, "result": result}
                )
        except Exception as exc:
            self.logger.bind(tag=TAG).error(
                f"OpenClaw bridge 入站 RPC 失败: bridge={connection.bridge_id}, method={method}, error={exc}"
            )
            if request_id is not None:
                await connection.send(
                    {
                        "jsonrpc": "2.0",
                        "id": request_id,
                        "error": {"code": -32000, "message": str(exc)},
                    }
                )

    async def _dispatch_method(
        self,
        *,
        connection: OpenClawHubConnection,
        method: str,
        params: dict[str, Any],
    ):
        push_text_method = self.hub_config.get("push_text_method", "xiaozhi.pushText")
        set_async_waiting_method = self.hub_config.get(
            "set_async_waiting_method",
            "xiaozhi.setAsyncWaiting",
        )
        if method == push_text_method:
            return await self._push_text_to_xiaozhi(connection, params)
        if method == set_async_waiting_method:
            return await self._set_async_waiting(connection, params)
        raise RuntimeError(f"Unsupported method: {method}")

    async def _push_text_to_xiaozhi(
        self,
        connection: OpenClawHubConnection,
        params: dict[str, Any],
    ) -> dict[str, Any]:
        if self.connection_registry is None:
            raise RuntimeError("xiaozhi 在线连接注册表未初始化")

        text = params.get("text")
        session_id = params.get("sessionId")
        device_id = params.get("deviceId")
        peer_id = params.get("peerId")

        self.logger.bind(tag=TAG).info(
            "OpenClaw bridge 主动推送请求: "
            f"bridge={connection.bridge_id}, session={session_id or ''}, "
            f"device={device_id or ''}, peer={peer_id or ''}, textLength={len(str(text or ''))}"
        )
        return await self.connection_registry.push_text(
            text=text,
            session_id=session_id,
            device_id=device_id,
            peer_id=peer_id,
        )

    async def _set_async_waiting(
        self,
        connection: OpenClawHubConnection,
        params: dict[str, Any],
    ) -> dict[str, Any]:
        if self.connection_registry is None:
            raise RuntimeError("xiaozhi 在线连接注册表未初始化")

        enabled = bool(params.get("enabled"))
        session_id = params.get("sessionId")
        device_id = params.get("deviceId")
        peer_id = params.get("peerId")
        source = params.get("source")
        reason = params.get("reason")

        self.logger.bind(tag=TAG).info(
            "OpenClaw bridge 更新异步等待态: "
            f"bridge={connection.bridge_id}, enabled={enabled}, "
            f"session={session_id or ''}, device={device_id or ''}, "
            f"peer={peer_id or ''}, source={str(source or '').strip() or '-'}"
        )
        return await self.connection_registry.set_openclaw_async_waiting(
            enabled=enabled,
            session_id=session_id,
            device_id=device_id,
            peer_id=peer_id,
            source=source,
            reason=reason,
        )

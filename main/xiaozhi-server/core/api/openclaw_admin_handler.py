from __future__ import annotations

import uuid

from aiohttp import web

from core.api.base_handler import BaseHandler

TAG = __name__


class OpenClawAdminHandler(BaseHandler):
    def __init__(
        self,
        config: dict,
        openclaw_hub=None,
        connection_registry=None,
        websocket_server=None,
        voice_interrupt_store=None,
    ):
        super().__init__(config)
        self.openclaw_hub = openclaw_hub
        self.connection_registry = connection_registry
        self.websocket_server = websocket_server
        self.voice_interrupt_store = voice_interrupt_store
        self.hub_config = config.get("openclaw_hub", {}) or {}

    def _get_admin_key(self) -> str:
        admin_key = (self.hub_config.get("admin_key") or "").strip()
        if admin_key:
            return admin_key
        return (self.config.get("server", {}).get("auth_key") or "").strip()

    def _is_authorized(self, request: web.Request) -> bool:
        auth_header = request.headers.get("Authorization", "")
        if not auth_header.startswith("Bearer "):
            return False
        return auth_header[7:].strip() == self._get_admin_key()

    def _get_connection_registry(self):
        if self.connection_registry is not None:
            return self.connection_registry
        if self.openclaw_hub is None:
            return None
        return getattr(self.openclaw_hub, "connection_registry", None)

    def _get_voice_interrupt_enabled(self) -> bool:
        if self.websocket_server is not None:
            return bool(self.websocket_server.config.get("enable_voice_interrupt", True))
        return bool(self.config.get("enable_voice_interrupt", True))

    def _parse_target_ref(self, value):
        normalized = (value or "").strip()
        return normalized or None

    def _get_voice_interrupt_scope(
        self, *, session_id: str | None = None, device_id: str | None = None
    ) -> str:
        if session_id:
            return "session"
        if device_id:
            return "device"
        return "global"

    def _parse_bool(self, value):
        if isinstance(value, bool):
            return value
        if isinstance(value, str):
            normalized = value.strip().lower()
            if normalized in {"1", "true", "yes", "on", "enabled"}:
                return True
            if normalized in {"0", "false", "no", "off", "disabled"}:
                return False
        if isinstance(value, (int, float)):
            return bool(value)
        return None

    def _normalize_option(self, value, label=None):
        final_value = (value or "").strip()
        final_label = (label or value or "").strip()
        if not final_value:
            return None
        return {"value": final_value, "label": final_label or final_value}

    def _normalize_runtime_option(self, bridge: dict, inventory: dict | None = None):
        runtime_option = None
        if isinstance(inventory, dict):
            runtime_account = inventory.get("runtimeAccount")
            if isinstance(runtime_account, dict):
                runtime_option = self._normalize_option(
                    str(runtime_account.get("value") or runtime_account.get("accountId") or ""),
                    str(runtime_account.get("label") or runtime_account.get("name") or ""),
                )

        if runtime_option is not None:
            return runtime_option

        account_id = str(bridge.get("account") or "").strip()
        label = (
            str(bridge.get("name") or "").strip()
            or str(bridge.get("bridgeId") or "").strip()
            or account_id
        )
        return self._normalize_option(account_id, label)

    def _normalize_agent_options(self, agents) -> list[dict]:
        if not isinstance(agents, list):
            return []

        normalized: list[dict] = []
        seen: set[str] = set()
        for item in agents:
            option = None
            if isinstance(item, dict):
                option = self._normalize_option(
                    str(
                        item.get("value")
                        or item.get("id")
                        or item.get("agentId")
                        or item.get("key")
                        or ""
                    ),
                    str(
                        item.get("label")
                        or item.get("name")
                        or item.get("agentName")
                        or item.get("title")
                        or item.get("id")
                        or item.get("agentId")
                        or ""
                    ),
                )
            elif isinstance(item, str):
                option = self._normalize_option(item, item)

            if option is None or option["value"] in seen:
                continue
            seen.add(option["value"])
            normalized.append(option)
        return normalized

    def _normalize_debug_key(self, value: str | None) -> str:
        raw = (value or "").strip()
        if not raw:
            raw = f"web-debug-{uuid.uuid4().hex[:12]}"
        normalized = "".join(
            ch if ch.isalnum() or ch in {"-", "_", ".", ":"} else "_"
            for ch in raw
        )
        normalized = normalized.strip("._:-")
        return normalized[:96] or f"web-debug-{uuid.uuid4().hex[:8]}"

    async def _require_auth(self, request: web.Request):
        if self._is_authorized(request):
            return None
        response = web.json_response(
            {"ok": False, "message": "unauthorized"},
            status=401,
        )
        self._add_cors_headers(response)
        return response

    async def get_voice_interrupt(self, request: web.Request):
        unauthorized = await self._require_auth(request)
        if unauthorized:
            return unauthorized

        session_id = self._parse_target_ref(request.query.get("sessionId"))
        device_id = self._parse_target_ref(request.query.get("deviceId"))
        peer_id = self._parse_target_ref(request.query.get("peerId"))
        allow_latest = bool(self._parse_bool(request.query.get("allowLatest")))

        if session_id or device_id or peer_id:
            registry = self._get_connection_registry()
            if registry is not None:
                state = await registry.get_voice_interrupt_state(
                    session_id=session_id,
                    device_id=device_id,
                    peer_id=peer_id,
                    allow_latest=allow_latest,
                )
                if state is not None:
                    response = web.json_response(
                        {
                            "ok": True,
                            "enabled": state["enabled"],
                            "scope": self._get_voice_interrupt_scope(
                                session_id=state.get("sessionId"),
                                device_id=state.get("deviceId") or device_id,
                            ),
                            "source": "connection",
                            "sessionId": state.get("sessionId"),
                            "deviceId": state.get("deviceId"),
                        }
                    )
                    self._add_cors_headers(response)
                    return response

            if device_id and self.voice_interrupt_store is not None:
                persisted_enabled = self.voice_interrupt_store.get_device_voice_interrupt(
                    device_id
                )
                if persisted_enabled is not None:
                    response = web.json_response(
                        {
                            "ok": True,
                            "enabled": persisted_enabled,
                            "scope": "device",
                            "source": "persisted",
                            "online": False,
                            "deviceId": device_id,
                        }
                    )
                    self._add_cors_headers(response)
                    return response

            response = web.json_response(
                {"ok": False, "message": "没有找到对应的语音打断状态"},
                status=404,
            )
            self._add_cors_headers(response)
            return response

        response = web.json_response(
            {
                "ok": True,
                "enabled": self._get_voice_interrupt_enabled(),
                "scope": "global",
                "source": "runtime-default",
            }
        )
        self._add_cors_headers(response)
        return response

    async def set_voice_interrupt(self, request: web.Request):
        unauthorized = await self._require_auth(request)
        if unauthorized:
            return unauthorized

        try:
            data = await request.json()
        except Exception:
            data = {}

        enabled = self._parse_bool(data.get("enabled"))
        if enabled is None:
            response = web.json_response(
                {"ok": False, "message": "enabled 必须是布尔值"},
                status=400,
            )
            self._add_cors_headers(response)
            return response

        session_id = self._parse_target_ref(data.get("sessionId"))
        device_id = self._parse_target_ref(data.get("deviceId"))
        peer_id = self._parse_target_ref(data.get("peerId"))
        scope = self._get_voice_interrupt_scope(
            session_id=session_id, device_id=device_id or peer_id
        )
        persist_requested = self._parse_bool(data.get("persist"))
        if persist_requested is None:
            persist_requested = False

        registry = self._get_connection_registry()
        updated = {"enabled": enabled, "updatedCount": 0, "skippedCount": 0}
        persisted = False
        persisted_device_id = device_id

        if scope == "global":
            if persist_requested:
                response = web.json_response(
                    {"ok": False, "message": "全局语音打断开关不支持 persist"},
                    status=400,
                )
                self._add_cors_headers(response)
                return response

            self.config["enable_voice_interrupt"] = enabled
            if self.websocket_server is not None:
                self.websocket_server.config["enable_voice_interrupt"] = enabled

            skip_device_ids = (
                self.voice_interrupt_store.list_device_ids()
                if self.voice_interrupt_store is not None
                else set()
            )
            if registry is not None:
                updated = await registry.set_voice_interrupt_enabled(
                    enabled, skip_device_ids=skip_device_ids
                )
        else:
            allow_latest = bool(self._parse_bool(data.get("allowLatest")))
            if registry is not None:
                updated = await registry.set_voice_interrupt_for_connection(
                    enabled,
                    session_id=session_id,
                    device_id=device_id,
                    peer_id=peer_id,
                    allow_latest=allow_latest,
                )

            if persist_requested:
                persisted_device_id = (
                    device_id
                    or updated.get("deviceId")
                    or self._parse_target_ref(data.get("persistDeviceId"))
                )
                if not persisted_device_id:
                    response = web.json_response(
                        {
                            "ok": False,
                            "message": "persist=true 时必须提供 deviceId，或命中带 deviceId 的在线连接",
                        },
                        status=400,
                    )
                    self._add_cors_headers(response)
                    return response
                if self.voice_interrupt_store is None:
                    response = web.json_response(
                        {"ok": False, "message": "语音打断持久化能力未启用"},
                        status=400,
                    )
                    self._add_cors_headers(response)
                    return response
                await self.voice_interrupt_store.set_device_voice_interrupt(
                    persisted_device_id, enabled
                )
                persisted = True

            if updated.get("updatedCount", 0) == 0 and not persisted:
                response = web.json_response(
                    {
                        "ok": False,
                        "message": "没有找到在线的小智设备连接",
                        "scope": scope,
                        "sessionId": session_id,
                        "deviceId": device_id,
                    },
                    status=404,
                )
                self._add_cors_headers(response)
                return response

        response = web.json_response(
            {
                "ok": True,
                "enabled": enabled,
                "scope": scope,
                "updatedConnections": updated.get("updatedCount", 0),
                "skippedConnections": updated.get("skippedCount", 0),
                "persisted": persisted,
                "sessionId": updated.get("sessionId") or session_id,
                "deviceId": persisted_device_id or updated.get("deviceId") or device_id,
            }
        )
        self._add_cors_headers(response)
        return response

    async def issue_bridge_token(self, request: web.Request):
        unauthorized = await self._require_auth(request)
        if unauthorized:
            return unauthorized

        if self.openclaw_hub is None:
            response = web.json_response(
                {"ok": False, "message": "openclaw hub 未启用"},
                status=400,
            )
            self._add_cors_headers(response)
            return response

        try:
            data = await request.json()
        except Exception:
            data = {}

        try:
            issued = await self.openclaw_hub.issue_bridge_token(
                name=data.get("name"),
                bridge_id=data.get("bridgeId"),
                account=data.get("account"),
                peer_id_mode=data.get("peerIdMode"),
                default_agent_id=data.get("defaultAgentId"),
                is_default=data.get("isDefault"),
            )
            response = web.json_response(
                {
                    "ok": True,
                    "bridge": issued["bridge"],
                    "token": issued["token"],
                    "bridgeWebSocketUrl": self.openclaw_hub.build_bridge_ws_url(request),
                }
            )
        except Exception as e:
            self.logger.bind(tag=TAG).error(f"签发 OpenClaw bridge token 失败: {e}")
            response = web.json_response(
                {"ok": False, "message": str(e)},
                status=400,
            )

        self._add_cors_headers(response)
        return response

    async def revoke_bridge_token(self, request: web.Request):
        unauthorized = await self._require_auth(request)
        if unauthorized:
            return unauthorized

        if self.openclaw_hub is None:
            response = web.json_response(
                {"ok": False, "message": "openclaw hub 未启用"},
                status=400,
            )
            self._add_cors_headers(response)
            return response

        try:
            data = await request.json()
        except Exception:
            data = {}

        bridge_id = (data.get("bridgeId") or "").strip()
        if not bridge_id:
            response = web.json_response(
                {"ok": False, "message": "bridgeId 不能为空"},
                status=400,
            )
            self._add_cors_headers(response)
            return response

        try:
            bridge = await self.openclaw_hub.revoke_bridge(bridge_id)
            response = web.json_response({"ok": True, "bridge": bridge})
        except Exception as e:
            self.logger.bind(tag=TAG).error(f"撤销 OpenClaw bridge token 失败: {e}")
            response = web.json_response(
                {"ok": False, "message": str(e)},
                status=400,
            )

        self._add_cors_headers(response)
        return response

    async def list_bridges(self, request: web.Request):
        unauthorized = await self._require_auth(request)
        if unauthorized:
            return unauthorized

        if self.openclaw_hub is None:
            response = web.json_response(
                {"ok": False, "message": "openclaw hub 未启用"},
                status=400,
            )
            self._add_cors_headers(response)
            return response

        response = web.json_response(
            {
                "ok": True,
                "bridges": await self.openclaw_hub.list_bridges(),
                "bridgeWebSocketPath": self.hub_config.get(
                    "bridge_ws_path", "/openclaw/bridge/ws"
                ),
            }
        )
        self._add_cors_headers(response)
        return response

    async def get_inventory(self, request: web.Request):
        unauthorized = await self._require_auth(request)
        if unauthorized:
            return unauthorized

        if self.openclaw_hub is None:
            response = web.json_response(
                {
                    "ok": False,
                    "healthy": False,
                    "message": "openclaw hub 未启用",
                    "errorMessage": "openclaw hub 未启用",
                    "runtimeAccounts": [],
                    "agents": [],
                    "bridges": [],
                    "accountAgents": {},
                },
                status=400,
            )
            self._add_cors_headers(response)
            return response

        account = (request.query.get("account") or "").strip() or None
        bridge_id = (request.query.get("bridgeId") or "").strip() or None
        inventory_method = self.hub_config.get("inventory_method", "xiaozhi.inventory")

        try:
            bridges = await self.openclaw_hub.list_bridges()
            if bridge_id:
                bridges = [item for item in bridges if item.get("bridgeId") == bridge_id]
            elif account:
                bridges = [item for item in bridges if item.get("account") == account]

            runtime_accounts: list[dict] = []
            account_agents: dict[str, list[dict]] = {}
            merged_agents: list[dict] = []
            seen_runtime_values: set[str] = set()
            seen_agent_values: set[str] = set()
            errors: list[str] = []
            success_count = 0

            for bridge in bridges:
                base_runtime = self._normalize_runtime_option(bridge)
                if base_runtime and base_runtime["value"] not in seen_runtime_values:
                    seen_runtime_values.add(base_runtime["value"])
                    runtime_accounts.append(base_runtime)

                if not bridge.get("connected"):
                    continue

                try:
                    result = await self.openclaw_hub.request(
                        inventory_method,
                        {
                            "account": bridge.get("account"),
                            "bridgeId": bridge.get("bridgeId"),
                        },
                        bridge_id=bridge.get("bridgeId"),
                        account=bridge.get("account"),
                    )
                    if not isinstance(result, dict):
                        raise RuntimeError("inventory 响应格式无效")

                    runtime_option = self._normalize_runtime_option(bridge, result)
                    if runtime_option and runtime_option["value"] not in seen_runtime_values:
                        seen_runtime_values.add(runtime_option["value"])
                        runtime_accounts.append(runtime_option)

                    account_key = runtime_option["value"] if runtime_option else str(
                        bridge.get("account") or ""
                    ).strip()
                    account_agent_list = self._normalize_agent_options(result.get("agents"))
                    if account_key:
                        account_agents[account_key] = account_agent_list

                    for option in account_agent_list:
                        if option["value"] in seen_agent_values:
                            continue
                        seen_agent_values.add(option["value"])
                        merged_agents.append(option)

                    success_count += 1
                except Exception as e:
                    self.logger.bind(tag=TAG).error(
                        f"获取 OpenClaw inventory 失败: bridge={bridge.get('bridgeId')}, error={e}"
                    )
                    errors.append(
                        f"{bridge.get('name') or bridge.get('bridgeId')}: {e}"
                    )

            if not bridges:
                error_message = "未找到可用的 OpenClaw bridge"
            elif success_count == 0 and errors:
                error_message = "；".join(errors)
            elif success_count == 0:
                error_message = "当前没有在线的 OpenClaw bridge"
            else:
                error_message = "；".join(errors)

            response = web.json_response(
                {
                    "ok": True,
                    "healthy": success_count > 0,
                    "runtimeAccounts": runtime_accounts,
                    "agents": merged_agents,
                    "bridges": bridges,
                    "accountAgents": account_agents,
                    "errorMessage": error_message,
                }
            )
        except Exception as e:
            self.logger.bind(tag=TAG).error(f"聚合 OpenClaw inventory 失败: {e}")
            response = web.json_response(
                {
                    "ok": False,
                    "healthy": False,
                    "message": str(e),
                    "errorMessage": str(e),
                    "runtimeAccounts": [],
                    "agents": [],
                    "bridges": [],
                    "accountAgents": {},
                },
                status=400,
            )

        self._add_cors_headers(response)
        return response

    async def list_connections(self, request: web.Request):
        unauthorized = await self._require_auth(request)
        if unauthorized:
            return unauthorized

        registry = self._get_connection_registry()
        if registry is None:
            response = web.json_response(
                {"ok": False, "message": "xiaozhi 连接注册表未初始化"},
                status=400,
            )
            self._add_cors_headers(response)
            return response

        response = web.json_response(
            {
                "ok": True,
                "connections": await registry.list_connections(),
            }
        )
        self._add_cors_headers(response)
        return response

    async def push_text(self, request: web.Request):
        unauthorized = await self._require_auth(request)
        if unauthorized:
            return unauthorized

        try:
            data = await request.json()
        except Exception:
            data = {}

        registry = self._get_connection_registry()
        if registry is None:
            response = web.json_response(
                {"ok": False, "message": "xiaozhi 连接注册表未初始化"},
                status=400,
            )
            self._add_cors_headers(response)
            return response

        text = (data.get("text") or "").strip()
        if not text:
            response = web.json_response(
                {"ok": False, "message": "text 不能为空"},
                status=400,
            )
            self._add_cors_headers(response)
            return response

        try:
            result = await registry.push_text(
                text=text,
                session_id=(data.get("sessionId") or "").strip() or None,
                device_id=(data.get("deviceId") or "").strip() or None,
                peer_id=(data.get("peerId") or "").strip() or None,
                allow_latest=bool(data.get("allowLatest", True)),
            )
            response = web.json_response({"ok": True, "result": result})
        except Exception as e:
            self.logger.bind(tag=TAG).error(f"管理员手动推送小智语音失败: {e}")
            response = web.json_response(
                {"ok": False, "message": str(e)},
                status=400,
            )

        self._add_cors_headers(response)
        return response

    async def chat(self, request: web.Request):
        unauthorized = await self._require_auth(request)
        if unauthorized:
            return unauthorized

        try:
            data = await request.json()
        except Exception:
            data = {}

        registry = self._get_connection_registry()
        if registry is None:
            response = web.json_response(
                {"ok": False, "message": "xiaozhi 连接注册表未初始化"},
                status=400,
            )
            self._add_cors_headers(response)
            return response

        text = (data.get("text") or "").strip()
        if not text:
            response = web.json_response(
                {"ok": False, "message": "text 不能为空"},
                status=400,
            )
            self._add_cors_headers(response)
            return response

        try:
            result = await registry.relay_chat(
                text=text,
                session_id=(data.get("sessionId") or "").strip() or None,
                device_id=(data.get("deviceId") or "").strip() or None,
                peer_id=(data.get("peerId") or "").strip() or None,
                allow_latest=bool(data.get("allowLatest", True)),
            )
            response = web.json_response({"ok": True, "result": result})
        except Exception as e:
            self.logger.bind(tag=TAG).error(f"管理员代发小智聊天失败: {e}")
            response = web.json_response(
                {"ok": False, "message": str(e)},
                status=400,
            )

        self._add_cors_headers(response)
        return response

    async def direct_chat(self, request: web.Request):
        unauthorized = await self._require_auth(request)
        if unauthorized:
            return unauthorized

        if self.openclaw_hub is None:
            response = web.json_response(
                {"ok": False, "message": "openclaw hub 未启用"},
                status=400,
            )
            self._add_cors_headers(response)
            return response

        try:
            data = await request.json()
        except Exception:
            data = {}

        text = (data.get("text") or "").strip()
        if not text:
            response = web.json_response(
                {"ok": False, "message": "text 不能为空"},
                status=400,
            )
            self._add_cors_headers(response)
            return response

        account = (data.get("account") or "default").strip() or "default"
        bridge_id = (data.get("bridgeId") or "").strip() or None
        debug_session_id = self._normalize_debug_key(
            data.get("debugSessionId") or data.get("sessionId")
        )
        peer_id = (data.get("peerId") or "").strip() or f"web-debug:{debug_session_id}"
        device_id = (data.get("deviceId") or "").strip() or peer_id
        client_id = (data.get("clientId") or "manager-web").strip() or "manager-web"
        speaker = (data.get("speaker") or "管理后台调试").strip() or "管理后台调试"
        agent_id = (data.get("agentId") or "").strip() or None
        agent_name = (data.get("agentName") or "").strip() or None

        bind_result = None
        try:
            if agent_id:
                bind_result = await self.openclaw_hub.request(
                    self.hub_config.get("bind_method", "xiaozhi.bindPeerAgent"),
                    {
                        "account": account,
                        "peerId": peer_id,
                        "agentId": agent_id,
                        "agentName": agent_name,
                    },
                    bridge_id=bridge_id,
                    account=account,
                )

            result = await self.openclaw_hub.request(
                self.hub_config.get("chat_method", "xiaozhi.chat"),
                {
                    "account": account,
                    "sessionId": debug_session_id,
                    "deviceId": device_id,
                    "clientId": client_id,
                    "peerId": peer_id,
                    "speaker": speaker,
                    "text": text,
                },
                bridge_id=bridge_id,
                account=account,
            )
            response = web.json_response(
                {
                    "ok": True,
                    "debugSessionId": debug_session_id,
                    "peerId": peer_id,
                    "account": account,
                    "bridgeId": bridge_id,
                    "bound": bind_result,
                    "result": result,
                }
            )
        except Exception as e:
            self.logger.bind(tag=TAG).error(f"管理员直连 OpenClaw 调试聊天失败: {e}")
            response = web.json_response(
                {"ok": False, "message": str(e)},
                status=400,
            )

        self._add_cors_headers(response)
        return response

    async def clear_session(self, request: web.Request):
        unauthorized = await self._require_auth(request)
        if unauthorized:
            return unauthorized

        if self.openclaw_hub is None:
            response = web.json_response(
                {"ok": False, "message": "openclaw hub 未启用"},
                status=400,
            )
            self._add_cors_headers(response)
            return response

        try:
            data = await request.json()
        except Exception:
            data = {}

        account = (data.get("account") or "default").strip() or "default"
        session_id = (data.get("sessionId") or "").strip() or None
        device_id = (data.get("deviceId") or "").strip() or None
        peer_id = (data.get("peerId") or "").strip() or None
        bridge_id = (data.get("bridgeId") or "").strip() or None

        if not any((session_id, device_id, peer_id)) and bool(
            data.get("allowLatest", True)
        ):
            registry = self._get_connection_registry()
            if registry is not None:
                connections = await registry.list_connections()
                if connections:
                    session_id = session_id or connections[0].get("sessionId")
                    device_id = device_id or connections[0].get("deviceId")

        if not any((session_id, device_id, peer_id)):
            response = web.json_response(
                {
                    "ok": False,
                    "message": "sessionId、deviceId、peerId 至少需要提供一个",
                },
                status=400,
            )
            self._add_cors_headers(response)
            return response

        try:
            result = await self.openclaw_hub.request(
                self.hub_config.get("clear_session_method", "xiaozhi.clearPeerSession"),
                {
                    "account": account,
                    "sessionId": session_id,
                    "deviceId": device_id,
                    "peerId": peer_id,
                },
                bridge_id=bridge_id,
                account=account,
            )
            response = web.json_response({"ok": True, "result": result})
        except Exception as e:
            self.logger.bind(tag=TAG).error(f"管理员清理 OpenClaw 会话失败: {e}")
            response = web.json_response(
                {"ok": False, "message": str(e)},
                status=400,
            )

        self._add_cors_headers(response)
        return response

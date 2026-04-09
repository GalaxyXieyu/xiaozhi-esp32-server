import asyncio
from aiohttp import web
from config.logger import setup_logging
from core.api.openclaw_admin_handler import OpenClawAdminHandler
from core.api.ota_handler import OTAHandler
from core.api.vision_handler import VisionHandler

TAG = __name__


class SimpleHttpServer:
    def __init__(
        self,
        config: dict,
        openclaw_hub=None,
        connection_registry=None,
        websocket_server=None,
        voice_interrupt_store=None,
    ):
        self.config = config
        self.openclaw_hub = openclaw_hub
        self.connection_registry = connection_registry
        self.websocket_server = websocket_server
        self.voice_interrupt_store = voice_interrupt_store
        self.logger = setup_logging()
        self.ota_handler = OTAHandler(config)
        self.vision_handler = VisionHandler(config)
        self.openclaw_admin_handler = (
            OpenClawAdminHandler(
                config,
                openclaw_hub=openclaw_hub,
                connection_registry=connection_registry,
                websocket_server=websocket_server,
                voice_interrupt_store=voice_interrupt_store,
            )
            if openclaw_hub or connection_registry or websocket_server
            else None
        )

    def _get_websocket_url(self, local_ip: str, port: int) -> str:
        """获取websocket地址

        Args:
            local_ip: 本地IP地址
            port: 端口号

        Returns:
            str: websocket地址
        """
        server_config = self.config["server"]
        websocket_config = server_config.get("websocket")

        if websocket_config and "你" not in websocket_config:
            return websocket_config
        else:
            return f"ws://{local_ip}:{port}/xiaozhi/v1/"

    async def start(self):
        try:
            server_config = self.config["server"]
            read_config_from_api = self.config.get("read_config_from_api", False)
            host = server_config.get("ip", "0.0.0.0")
            port = int(server_config.get("http_port", 8003))

            if port:
                app = web.Application()

                if not read_config_from_api:
                    # 如果没有开启智控台，只是单模块运行，就需要再添加简单OTA接口，用于下发websocket接口
                    app.add_routes(
                        [
                            web.get("/xiaozhi/ota/", self.ota_handler.handle_get),
                            web.post("/xiaozhi/ota/", self.ota_handler.handle_post),
                            web.options(
                                "/xiaozhi/ota/", self.ota_handler.handle_options
                            ),
                            # 下载接口，仅提供 data/bin/*.bin 下载
                            web.get(
                                "/xiaozhi/ota/download/{filename}",
                                self.ota_handler.handle_download,
                            ),
                            web.options(
                                "/xiaozhi/ota/download/{filename}",
                                self.ota_handler.handle_options,
                            ),
                        ]
                    )
                # 添加路由
                app.add_routes(
                    [
                        web.get("/mcp/vision/explain", self.vision_handler.handle_get),
                        web.post(
                            "/mcp/vision/explain", self.vision_handler.handle_post
                        ),
                        web.options(
                            "/mcp/vision/explain", self.vision_handler.handle_options
                        ),
                    ]
                )
                if self.openclaw_admin_handler:
                    app.add_routes(
                        [
                            web.get(
                                "/admin/openclaw/voice-interrupt",
                                self.openclaw_admin_handler.get_voice_interrupt,
                            ),
                            web.post(
                                "/admin/openclaw/voice-interrupt",
                                self.openclaw_admin_handler.set_voice_interrupt,
                            ),
                            web.get(
                                "/admin/openclaw/inventory",
                                self.openclaw_admin_handler.get_inventory,
                            ),
                            web.options(
                                "/admin/openclaw/voice-interrupt",
                                self.openclaw_admin_handler.handle_options,
                            ),
                            web.options(
                                "/admin/openclaw/inventory",
                                self.openclaw_admin_handler.handle_options,
                            ),
                        ]
                    )
                if self.openclaw_hub and self.openclaw_hub.enabled:
                    bridge_ws_path = (
                        self.config.get("openclaw_hub", {}) or {}
                    ).get("bridge_ws_path", "/openclaw/bridge/ws")
                    app.add_routes(
                        [
                            web.get(
                                bridge_ws_path,
                                self.openclaw_hub.handle_websocket,
                            ),
                            web.post(
                                "/admin/openclaw/issue-bridge-token",
                                self.openclaw_admin_handler.issue_bridge_token,
                            ),
                            web.post(
                                "/admin/openclaw/revoke-bridge-token",
                                self.openclaw_admin_handler.revoke_bridge_token,
                            ),
                            web.get(
                                "/admin/openclaw/bridges",
                                self.openclaw_admin_handler.list_bridges,
                            ),
                            web.get(
                                "/admin/openclaw/connections",
                                self.openclaw_admin_handler.list_connections,
                            ),
                            web.post(
                                "/admin/openclaw/push-text",
                                self.openclaw_admin_handler.push_text,
                            ),
                            web.post(
                                "/admin/openclaw/chat",
                                self.openclaw_admin_handler.chat,
                            ),
                            web.post(
                                "/admin/openclaw/direct-chat",
                                self.openclaw_admin_handler.direct_chat,
                            ),
                            web.get(
                                "/admin/openclaw/debug-session",
                                self.openclaw_admin_handler.get_debug_session,
                            ),
                            web.post(
                                "/admin/openclaw/clear-session",
                                self.openclaw_admin_handler.clear_session,
                            ),
                            web.options(
                                "/admin/openclaw/issue-bridge-token",
                                self.openclaw_admin_handler.handle_options,
                            ),
                            web.options(
                                "/admin/openclaw/revoke-bridge-token",
                                self.openclaw_admin_handler.handle_options,
                            ),
                            web.options(
                                "/admin/openclaw/bridges",
                                self.openclaw_admin_handler.handle_options,
                            ),
                            web.options(
                                "/admin/openclaw/connections",
                                self.openclaw_admin_handler.handle_options,
                            ),
                            web.options(
                                "/admin/openclaw/push-text",
                                self.openclaw_admin_handler.handle_options,
                            ),
                            web.options(
                                "/admin/openclaw/chat",
                                self.openclaw_admin_handler.handle_options,
                            ),
                            web.options(
                                "/admin/openclaw/direct-chat",
                                self.openclaw_admin_handler.handle_options,
                            ),
                            web.options(
                                "/admin/openclaw/debug-session",
                                self.openclaw_admin_handler.handle_options,
                            ),
                            web.options(
                                "/admin/openclaw/clear-session",
                                self.openclaw_admin_handler.handle_options,
                            ),
                        ]
                    )

                # 运行服务
                runner = web.AppRunner(app)
                await runner.setup()
                site = web.TCPSite(runner, host, port)
                await site.start()

                # 保持服务运行
                while True:
                    await asyncio.sleep(3600)  # 每隔 1 小时检查一次
        except Exception as e:
            self.logger.bind(tag=TAG).error(f"HTTP服务器启动失败: {e}")
            import traceback

            self.logger.bind(tag=TAG).error(f"错误堆栈: {traceback.format_exc()}")
            raise

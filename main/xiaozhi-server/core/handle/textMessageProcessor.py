import json
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from core.connection import ConnectionHandler
from core.handle.textMessageHandlerRegistry import TextMessageHandlerRegistry

TAG = __name__


class TextMessageProcessor:
    """消息处理器主类"""

    def __init__(self, registry: TextMessageHandlerRegistry):
        self.registry = registry

    async def process_message(self, conn: "ConnectionHandler", message: str) -> None:
        """处理消息的主入口"""
        try:
            # 解析JSON消息
            msg_json = json.loads(message)

            # 处理JSON消息
            if isinstance(msg_json, dict):
                message_type = msg_json.get("type")

                # 记录日志
                conn.logger.bind(tag=TAG).info(f"收到{message_type}消息：{message}")
                conn.record_debug_event(
                    event_source="device",
                    event_type="json_message_received",
                    direction="inbound",
                    origin="unknown",
                    summary_text=f"收到设备消息: {message_type or 'unknown'}",
                    payload=msg_json,
                    status="ok" if message_type else "error",
                )

                # 获取并执行处理器
                handler = self.registry.get_handler(message_type)
                if handler:
                    await handler.handle(conn, msg_json)
                else:
                    conn.logger.bind(tag=TAG).error(f"收到未知类型消息：{message}")
                    conn.record_debug_event(
                        event_source="device",
                        event_type="unknown_message_type",
                        direction="inbound",
                        origin="unknown",
                        summary_text=f"收到未知设备消息类型: {message_type or 'unknown'}",
                        payload=msg_json,
                        status="error",
                    )
            # 处理纯数字消息
            elif isinstance(msg_json, int):
                conn.logger.bind(tag=TAG).info(f"收到数字消息：{message}")
                conn.record_debug_event(
                    event_source="device",
                    event_type="numeric_message_received",
                    direction="inbound",
                    origin="unknown",
                    summary_text=f"收到数字消息: {message}",
                    payload={"value": msg_json},
                )
                await conn.websocket.send(message)

        except json.JSONDecodeError:
            # 非JSON消息直接转发
            conn.logger.bind(tag=TAG).error(f"解析到错误的消息：{message}")
            conn.record_debug_event(
                event_source="device",
                event_type="invalid_message_received",
                direction="inbound",
                origin="unknown",
                summary_text=f"收到无法解析的文本消息: {conn._trim_debug_string(message, 160)}",
                payload={"rawMessage": message},
                status="error",
            )
            await conn.websocket.send(message)

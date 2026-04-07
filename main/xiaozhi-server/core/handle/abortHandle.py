import json
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from core.connection import ConnectionHandler
TAG = __name__


def is_voice_interrupt_enabled(conn: "ConnectionHandler") -> bool:
    return bool(conn.config.get("enable_voice_interrupt", True))


async def handleAbortMessage(conn: "ConnectionHandler"):
    if not is_voice_interrupt_enabled(conn):
        conn.logger.bind(tag=TAG).info("Abort ignored: voice interrupt is disabled")
        return False

    conn.logger.bind(tag=TAG).info("Abort message received")
    # 设置成打断状态，会自动打断llm、tts任务
    conn.client_abort = True
    conn.clear_queues()
    # 打断客户端说话状态
    await conn.websocket.send(
        json.dumps({"type": "tts", "state": "stop", "session_id": conn.session_id})
    )
    conn.clearSpeakStatus()
    conn.logger.bind(tag=TAG).info("Abort message received-end")
    return True

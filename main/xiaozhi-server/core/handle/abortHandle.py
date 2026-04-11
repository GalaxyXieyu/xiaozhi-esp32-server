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
        conn.record_debug_event(
            event_source="device",
            event_type="abort_ignored",
            direction="inbound",
            origin="unknown",
            summary_text="设备请求打断，但语音打断已关闭",
            payload={"voiceInterruptEnabled": False},
            status="dropped",
        )
        return False

    conn.ensure_turn("abort")
    conn.logger.bind(tag=TAG).info("Abort message received")
    conn.record_debug_event(
        event_source="device",
        event_type="abort_received",
        direction="inbound",
        origin="unknown",
        summary_text="设备请求中止当前播报",
        payload={
            "voiceInterruptEnabled": True,
            "listenMode": conn.client_listen_mode,
            "clientIsSpeaking": conn.client_is_speaking,
        },
    )
    # 设置成打断状态，会自动打断llm、tts任务
    conn.client_abort = True
    conn.clear_queues()
    # 打断客户端说话状态
    stop_message = {"type": "tts", "state": "stop", "session_id": conn.session_id}
    await conn.websocket.send(
        json.dumps(stop_message)
    )
    conn.record_debug_event(
        event_source="tts",
        event_type="tts_stop_sent",
        direction="outbound",
        origin=conn.current_response_origin,
        summary_text="因设备 Abort 通知停止播放",
        payload={"reason": "abort", "message": stop_message},
    )
    conn.clearSpeakStatus()
    conn.logger.bind(tag=TAG).info("Abort message received-end")
    return True

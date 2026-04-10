import json
import sys
import time
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import AsyncMock, patch

sys.modules.setdefault("opuslib_next", SimpleNamespace())
sys.modules.setdefault("pydub", SimpleNamespace(AudioSegment=object))
sys.modules.setdefault(
    "portalocker",
    SimpleNamespace(
        LOCK_EX=1,
        LOCK_NB=2,
        LockException=RuntimeError,
        lock=lambda *args, **kwargs: None,
        unlock=lambda *args, **kwargs: None,
    ),
)

project_dir = Path(__file__).resolve().parents[1]
data_dir = project_dir / "data"
data_dir.mkdir(exist_ok=True)
config_file = data_dir / ".config.yaml"
if not config_file.exists():
    config_file.write_text("{}\n", encoding="utf-8")


class DummyMCPClient:
    pass


async def _dummy_send_mcp_initialize_message(conn):
    return None


sys.modules.setdefault(
    "core.providers.tools.device_mcp",
    SimpleNamespace(
        MCPClient=DummyMCPClient,
        send_mcp_initialize_message=_dummy_send_mcp_initialize_message,
    ),
)

from core.handle.helloHandle import handleHelloMessage
from core.handle.receiveAudioHandle import no_voice_close_connect
from core.handle.textHandler.pingMessageHandler import PingMessageHandler


class DummyLogger:
    def bind(self, **kwargs):
        return self

    def debug(self, *args, **kwargs):
        return None

    def info(self, *args, **kwargs):
        return None

    def warning(self, *args, **kwargs):
        return None

    def error(self, *args, **kwargs):
        return None


class HelloConn:
    def __init__(self):
        self.logger = DummyLogger()
        self.features = None
        self.standby_online = False
        self.last_activity_time = 0.0
        self.welcome_msg = {"type": "hello", "audio_params": {"sample_rate": 24000}}
        self.websocket = SimpleNamespace(send=AsyncMock())


class PingConn:
    def __init__(self):
        self.logger = DummyLogger()
        self.config = {"enable_websocket_ping": False}
        self.standby_online = True
        self.last_activity_time = 0.0
        self.websocket = SimpleNamespace(send=AsyncMock())


class NoVoiceConn:
    def __init__(self):
        self.standby_online = True
        self.need_bind = False
        self.close_after_chat = False
        self.last_activity_time = time.time() * 1000 - 5000
        self.config = {"close_connection_no_voice_time": 1}
        self.logger = DummyLogger()

    def is_openclaw_async_waiting_active(self):
        return False


class StandbyOnlineRuntimeTests(unittest.IsolatedAsyncioTestCase):
    async def test_hello_enables_standby_online_and_refreshes_activity(self):
        conn = HelloConn()

        await handleHelloMessage(
            conn,
            {
                "type": "hello",
                "features": {"standby_online": True, "mcp": False},
                "audio_params": {"format": "opus", "sample_rate": 16000},
            },
        )

        self.assertTrue(conn.standby_online)
        self.assertEqual(conn.features["standby_online"], True)
        self.assertGreater(conn.last_activity_time, 0.0)
        conn.websocket.send.assert_awaited_once()
        payload = json.loads(conn.websocket.send.await_args.args[0])
        self.assertEqual(payload["type"], "hello")
        self.assertEqual(payload["audio_params"]["sample_rate"], 16000)

    async def test_ping_handler_accepts_standby_connection_without_global_switch(self):
        conn = PingConn()

        await PingMessageHandler().handle(conn, {"type": "ping"})

        self.assertGreater(conn.last_activity_time, 0.0)
        conn.websocket.send.assert_awaited_once()
        payload = json.loads(conn.websocket.send.await_args.args[0])
        self.assertEqual(payload["type"], "pong")

    async def test_no_voice_close_connect_skips_goodbye_for_standby_online(self):
        conn = NoVoiceConn()

        with patch(
            "core.handle.receiveAudioHandle.startToChat",
            new_callable=AsyncMock,
        ) as start_to_chat:
            await no_voice_close_connect(conn, False)

        self.assertFalse(conn.close_after_chat)
        start_to_chat.assert_not_awaited()


if __name__ == "__main__":
    unittest.main()

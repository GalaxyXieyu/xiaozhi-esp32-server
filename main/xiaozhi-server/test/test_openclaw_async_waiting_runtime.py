import sys
import unittest
from types import SimpleNamespace
from unittest.mock import patch

sys.modules.setdefault("opuslib_next", SimpleNamespace())
sys.modules.setdefault("pydub", SimpleNamespace(AudioSegment=object))

from core.openclaw.active_connections import XiaozhiActiveConnectionRegistry


class DummyWebSocket:
    def __init__(self):
        self.closed = False
        self.state = SimpleNamespace(name="OPEN")


class DummyLogger:
    def bind(self, **kwargs):
        return self

    def info(self, *args, **kwargs):
        return None

    def warning(self, *args, **kwargs):
        return None

    def error(self, *args, **kwargs):
        return None


class DummyConnection:
    def __init__(self, session_id: str, device_id: str):
        self.session_id = session_id
        self.device_id = device_id
        self.websocket = DummyWebSocket()
        self.waiting_updates = []

    def set_openclaw_async_waiting(self, enabled, *, source=None, reason=None):
        self.waiting_updates.append(
            {
                "enabled": bool(enabled),
                "source": source,
                "reason": reason,
            }
        )


class OpenClawAsyncWaitingRuntimeTests(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self._patchers = [
            patch("core.openclaw.active_connections.setup_logging", return_value=DummyLogger()),
        ]
        for patcher in self._patchers:
            patcher.start()

    def tearDown(self):
        for patcher in reversed(self._patchers):
            patcher.stop()

    async def test_registry_updates_target_connection_async_waiting(self):
        registry = XiaozhiActiveConnectionRegistry()
        conn = DummyConnection("session-a", "device-a")
        await registry.register(conn)

        updated = await registry.set_openclaw_async_waiting(
            True,
            device_id="device-a",
            source="subagent_spawned",
            reason="waiting-child-result",
        )

        self.assertEqual(updated["updatedCount"], 1)
        self.assertEqual(
            conn.waiting_updates,
            [
                {
                    "enabled": True,
                    "source": "subagent_spawned",
                    "reason": "waiting-child-result",
                }
            ],
        )

if __name__ == "__main__":
    unittest.main()

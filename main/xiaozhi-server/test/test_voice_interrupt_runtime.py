import json
import os
import tempfile
import unittest
from types import SimpleNamespace
from unittest.mock import patch

from aiohttp.test_utils import make_mocked_request

from core.api.openclaw_admin_handler import OpenClawAdminHandler
from core.openclaw.active_connections import XiaozhiActiveConnectionRegistry
from core.openclaw.voice_interrupt_store import VoiceInterruptSettingsStore


class DummyWebSocket:
    def __init__(self):
        self.closed = False
        self.state = SimpleNamespace(name="OPEN")


class DummyConnection:
    def __init__(self, session_id: str, device_id: str, enabled: bool = True):
        self.session_id = session_id
        self.device_id = device_id
        self.config = {"enable_voice_interrupt": enabled}
        self.common_config = {"enable_voice_interrupt": enabled}
        self.websocket = DummyWebSocket()


class DummyJsonRequest:
    def __init__(self, data: dict):
        self._data = data
        self.headers = {"Authorization": "Bearer secret"}
        self.query = {}

    async def json(self):
        return self._data


class DummyLogger:
    def bind(self, **kwargs):
        return self

    def info(self, *args, **kwargs):
        return None

    def warning(self, *args, **kwargs):
        return None

    def error(self, *args, **kwargs):
        return None


class VoiceInterruptRuntimeTests(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self._patchers = [
            patch("core.openclaw.active_connections.setup_logging", return_value=DummyLogger()),
            patch("core.openclaw.voice_interrupt_store.setup_logging", return_value=DummyLogger()),
            patch("core.api.base_handler.setup_logging", return_value=DummyLogger()),
        ]
        for patcher in self._patchers:
            patcher.start()

    def tearDown(self):
        for patcher in reversed(self._patchers):
            patcher.stop()

    async def test_registry_updates_target_connection_only(self):
        registry = XiaozhiActiveConnectionRegistry()
        conn_a = DummyConnection("session-a", "device-a", enabled=True)
        conn_b = DummyConnection("session-b", "device-b", enabled=True)
        await registry.register(conn_a)
        await registry.register(conn_b)

        updated = await registry.set_voice_interrupt_for_connection(
            False, device_id="device-b"
        )

        self.assertEqual(updated["updatedCount"], 1)
        self.assertTrue(conn_a.config["enable_voice_interrupt"])
        self.assertFalse(conn_b.config["enable_voice_interrupt"])
        self.assertTrue(conn_b.common_config["enable_voice_interrupt"])

    async def test_admin_handler_persists_offline_device_override(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            store_file = os.path.join(tmp_dir, "voice_interrupt_settings.json")
            config = {
                "server": {"auth_key": "secret"},
                "openclaw_hub": {
                    "admin_key": "secret",
                    "voice_interrupt_store_file": store_file,
                },
            }
            registry = XiaozhiActiveConnectionRegistry()
            websocket_server = SimpleNamespace(config={"enable_voice_interrupt": True})
            store = VoiceInterruptSettingsStore(config)
            handler = OpenClawAdminHandler(
                config,
                connection_registry=registry,
                websocket_server=websocket_server,
                voice_interrupt_store=store,
            )

            response = await handler.set_voice_interrupt(
                DummyJsonRequest(
                    {"enabled": False, "deviceId": "device-offline", "persist": True}
                )
            )
            body = json.loads(response.text)

            self.assertEqual(response.status, 200)
            self.assertEqual(body["scope"], "device")
            self.assertEqual(body["updatedConnections"], 0)
            self.assertTrue(body["persisted"])
            self.assertFalse(store.get_device_voice_interrupt("device-offline"))

    async def test_get_voice_interrupt_reads_persisted_offline_device_state(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            store_file = os.path.join(tmp_dir, "voice_interrupt_settings.json")
            config = {
                "server": {"auth_key": "secret"},
                "openclaw_hub": {
                    "admin_key": "secret",
                    "voice_interrupt_store_file": store_file,
                },
            }
            store = VoiceInterruptSettingsStore(config)
            await store.set_device_voice_interrupt("device-offline", False)
            handler = OpenClawAdminHandler(
                config,
                websocket_server=SimpleNamespace(config={"enable_voice_interrupt": True}),
                voice_interrupt_store=store,
            )

            request = make_mocked_request(
                "GET",
                "/admin/openclaw/voice-interrupt?deviceId=device-offline",
                headers={"Authorization": "Bearer secret"},
            )
            request._rel_url = request._rel_url.with_query({"deviceId": "device-offline"})
            response = await handler.get_voice_interrupt(request)
            body = json.loads(response.text)

            self.assertEqual(response.status, 200)
            self.assertFalse(body["enabled"])
            self.assertEqual(body["scope"], "device")
            self.assertEqual(body["source"], "persisted")


if __name__ == "__main__":
    unittest.main()

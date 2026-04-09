import unittest
from types import SimpleNamespace
from unittest.mock import patch

from core.openclaw.hub_session import OpenClawHubSession


class DummyLogger:
    def bind(self, **kwargs):
        return self

    def info(self, *args, **kwargs):
        return None

    def warning(self, *args, **kwargs):
        return None

    def error(self, *args, **kwargs):
        return None


class FakeHub:
    def __init__(self):
        self.calls = []

    async def has_available_bridge(self, bridge_id=None, account=None):
        self.calls.append(("has_available_bridge", {"bridge_id": bridge_id, "account": account}))
        return True

    async def request(self, method, params, bridge_id=None, account=None):
        self.calls.append(
            (
                method,
                {
                    "params": params,
                    "bridge_id": bridge_id,
                    "account": account,
                },
            )
        )
        if method == "xiaozhi.bindPeerAgent":
            return {
                "ok": True,
                "agentId": params.get("agentId"),
                "agentName": params.get("agentName"),
            }
        if method == "xiaozhi.chat":
            return {"reply": "来自 OpenClaw 的回复"}
        return {"ok": True}


class FakeConn:
    def __init__(self, binding=None):
        self.session_id = "session-1"
        self.device_id = "device-1"
        self.headers = {"client-id": "client-1"}
        self.current_speaker = None
        self.config = {
            "openclaw_hub": {
                "enabled": True,
                "relay_chat": True,
                "default_account": "default",
                "session_events_enabled": True,
                "session_started_method": "xiaozhi.sessionStarted",
                "session_ended_method": "xiaozhi.sessionEnded",
                "chat_method": "xiaozhi.chat",
                "bind_method": "xiaozhi.bindPeerAgent",
            },
            "openclaw_binding": binding or {},
        }
        self.server = SimpleNamespace(openclaw_hub=FakeHub())


class OpenClawAutoBindTests(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.patcher = patch(
            "core.openclaw.hub_session.setup_logging",
            return_value=DummyLogger(),
        )
        self.patcher.start()

    def tearDown(self):
        self.patcher.stop()

    async def test_chat_binds_configured_agent_before_forwarding(self):
        conn = FakeConn(
            {
                "agentType": "openclaw",
                "openclawAgentId": "enterprise-news-host",
                "openclawAgentName": "企业资讯助手",
            }
        )
        session = OpenClawHubSession(conn)

        result = await session.chat("你是谁？")

        self.assertEqual(result, {"reply": "来自 OpenClaw 的回复"})
        methods = [item[0] for item in conn.server.openclaw_hub.calls]
        self.assertEqual(
            methods,
            [
                "xiaozhi.sessionStarted",
                "xiaozhi.bindPeerAgent",
                "xiaozhi.chat",
            ],
        )
        bind_call = conn.server.openclaw_hub.calls[1][1]["params"]
        self.assertEqual(bind_call["agentId"], "enterprise-news-host")
        self.assertEqual(bind_call["agentName"], "企业资讯助手")

    async def test_chat_does_not_rebind_same_agent_in_same_session(self):
        conn = FakeConn(
            {
                "agentType": "openclaw",
                "openclawAgentId": "enterprise-news-host",
                "openclawAgentName": "企业资讯助手",
            }
        )
        session = OpenClawHubSession(conn)

        await session.chat("第一句")
        await session.chat("第二句")

        methods = [item[0] for item in conn.server.openclaw_hub.calls]
        self.assertEqual(methods.count("xiaozhi.bindPeerAgent"), 1)
        self.assertEqual(methods.count("xiaozhi.chat"), 2)


if __name__ == "__main__":
    unittest.main()

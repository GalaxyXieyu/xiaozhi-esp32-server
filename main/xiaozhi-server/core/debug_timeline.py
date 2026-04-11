import asyncio
import json
import queue
import time
import uuid

from config.manage_api_client import report_debug_event


class ConnectionDebugTimeline:
    def __init__(self, conn):
        self.conn = conn

    def start_turn(self, trigger: str, user_text: str | None = None):
        self.conn.current_turn_id = str(uuid.uuid4().hex)
        payload = {"trigger": trigger}
        if user_text:
            payload["textPreview"] = self.trim_string(user_text, 160)
            payload["textLength"] = len(user_text)
        self.record_event(
            event_source="system",
            event_type="turn_started",
            direction="internal",
            summary_text=f"开始新轮次: {trigger}",
            payload=payload,
            turn_id=self.conn.current_turn_id,
            status="ok",
        )
        return self.conn.current_turn_id

    def ensure_turn(self, trigger: str = "implicit"):
        if self.conn.current_turn_id:
            return self.conn.current_turn_id
        return self.start_turn(trigger)

    def set_response_origin(self, origin: str | None):
        normalized = (origin or "unknown").strip() or "unknown"
        self.conn.current_response_origin = normalized

    def get_runtime_account(self) -> str | None:
        binding = self.conn.config.get("openclaw_binding") or {}
        if isinstance(binding, dict):
            runtime_account = str(binding.get("runtimeAccount") or "").strip()
            if runtime_account:
                return runtime_account

        bridge_config = self.conn.config.get("openclaw_bridge", {}) or {}
        bridge_account = str(bridge_config.get("account") or "").strip()
        if bridge_account:
            return bridge_account

        hub_config = self.conn.config.get("openclaw_hub", {}) or {}
        hub_account = str(hub_config.get("default_account") or "").strip()
        return hub_account or None

    def record_event(
        self,
        event_source: str,
        event_type: str,
        *,
        direction: str = "internal",
        origin: str | None = None,
        summary_text: str | None = None,
        payload: dict | list | str | None = None,
        sentence_id: str | None = None,
        request_id: str | None = None,
        speaker: str | None = None,
        runtime_account: str | None = None,
        status: str = "ok",
        turn_id: str | None = None,
        attach_current_turn: bool = True,
    ):
        if not event_source or not event_type:
            return

        resolved_turn_id = turn_id
        if resolved_turn_id is None and attach_current_turn:
            resolved_turn_id = self.conn.current_turn_id

        sanitized_payload = self.sanitize_payload(payload)
        payload_json = None
        if sanitized_payload not in (None, "", {}, []):
            payload_json = json.dumps(
                sanitized_payload,
                ensure_ascii=False,
                default=str,
            )

        device_id = self.conn.device_id or (self.conn.headers or {}).get("device-id")
        mac_address = device_id
        event = {
            "macAddress": mac_address,
            "deviceId": device_id,
            "sessionId": self.conn.session_id,
            "turnId": resolved_turn_id,
            "eventSource": event_source,
            "eventType": event_type,
            "direction": direction,
            "origin": (origin or self.conn.current_response_origin or "unknown"),
            "summaryText": self.trim_string(summary_text or event_type, 512),
            "payloadJson": payload_json,
            "sentenceId": sentence_id or self.conn.sentence_id,
            "requestId": request_id,
            "speaker": speaker or self.conn.current_speaker,
            "runtimeAccount": runtime_account or self.get_runtime_account(),
            "status": status or "ok",
            "eventAt": int(time.time()),
        }
        self.conn.debug_event_queue.put(event)

    def sanitize_payload(self, payload):
        if payload is None:
            return None

        sensitive_keys = (
            "authorization",
            "token",
            "secret",
            "api_key",
            "access_key",
            "cookie",
        )

        def _sanitize(value, parent_key: str = ""):
            if isinstance(value, dict):
                sanitized = {}
                for key, item in value.items():
                    key_str = str(key)
                    lower_key = key_str.lower()
                    if any(token in lower_key for token in sensitive_keys):
                        sanitized[key_str] = "***"
                    else:
                        sanitized[key_str] = _sanitize(item, lower_key)
                return sanitized
            if isinstance(value, list):
                return [_sanitize(item, parent_key) for item in value[:20]]
            if isinstance(value, bytes):
                return f"<bytes:{len(value)}>"
            if isinstance(value, str):
                return self.trim_string(value, 400)
            if isinstance(value, (int, float, bool)) or value is None:
                return value
            return self.trim_string(str(value), 400)

        return _sanitize(payload)

    def trim_string(self, value: str | None, max_length: int = 256):
        if value is None:
            return None
        normalized = " ".join(str(value).split())
        if len(normalized) <= max_length:
            return normalized
        return normalized[: max_length - 3] + "..."

    def run_worker(self, tag: str):
        while not self.conn.stop_event.is_set() or not self.conn.debug_event_queue.empty():
            try:
                event = self.conn.debug_event_queue.get(timeout=1)
            except queue.Empty:
                continue

            try:
                if event is None:
                    continue
                asyncio.run(report_debug_event(event))
            except Exception as e:
                self.conn.logger.bind(tag=tag).error(f"调试事件上报线程异常: {e}")
            finally:
                self.conn.debug_event_queue.task_done()

        self.conn.logger.bind(tag=tag).info("调试事件上报线程已退出")

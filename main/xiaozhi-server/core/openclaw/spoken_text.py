"""Extract human-facing spoken text from OpenClaw responses."""

from __future__ import annotations

import json
import re
from typing import Any


def _is_object(value: Any) -> bool:
    return isinstance(value, dict)


def _unique_strings(values: list[str]) -> list[str]:
    seen: set[str] = set()
    result: list[str] = []
    for value in values:
        if not value or value in seen:
            continue
        seen.add(value)
        result.append(value)
    return result


def _try_parse_json(text: str) -> Any | None:
    value = (text or "").strip()
    if not value:
        return None
    if not (
        (value.startswith("{") and value.endswith("}"))
        or (value.startswith("[") and value.endswith("]"))
    ):
        return None
    try:
        return json.loads(value)
    except Exception:
        return None


def _extract_structured_text(text: str) -> str:
    parsed = _try_parse_json(text)
    if parsed is None:
        return ""
    return extract_spoken_text(parsed)


def _replace_fence_blocks(text: str) -> str:
    pattern = re.compile(r"```([a-zA-Z0-9_-]+)?\s*\n([\s\S]*?)```")

    def _replace(match: re.Match[str]) -> str:
        lang = (match.group(1) or "").strip().lower()
        body = match.group(2) or ""
        structured = _extract_structured_text(body if lang == "json" else body.strip())
        if structured:
            return f"\n{structured}\n"
        if lang == "json":
            return "\n"
        return f"\n{body.strip()}\n"

    return pattern.sub(_replace, text)


def _sanitize_plain_text(text: str) -> str:
    value = _replace_fence_blocks(text or "")
    value = re.sub(r"\[\[reply_to_current\]\]\s*", "", value)
    value = re.sub(r"^MEDIA:\s+.*$", "", value, flags=re.MULTILINE)
    value = re.sub(
        r"^Conversation info \(untrusted metadata\):[\s\S]*?^Sender \(untrusted metadata\):[\s\S]*?(?:\n\n|$)",
        "",
        value,
        flags=re.MULTILINE,
    )
    value = re.sub(
        r"\[Queued messages while agent was busy\][\s\S]*?Queued #\d+\n",
        "",
        value,
        flags=re.MULTILINE,
    )
    value = re.sub(r"\[([^\]]+)\]\((https?://[^)]+)\)", r"\1", value)
    value = re.sub(r"\*\*([^*]+)\*\*", r"\1", value)
    value = re.sub(r"`([^`]+)`", r"\1", value)
    value = re.sub(r"^#{1,6}\s+", "", value, flags=re.MULTILINE)
    value = re.sub(r"^\s*[-*]\s+", "", value, flags=re.MULTILINE)
    value = re.sub(r"\n{3,}", "\n\n", value).strip()

    if not value or re.fullmatch(r"NO_REPLY", value, flags=re.IGNORECASE):
        return ""

    if re.fullmatch(r"(\{[\s\S]*\}|\[[\s\S]*\])", value):
        return ""

    return value


def _looks_like_tool_envelope(value: dict[str, Any]) -> bool:
    return any(
        key in value
        for key in (
            "jsonrpc",
            "method",
            "params",
            "toolCallId",
            "toolName",
            "childSessionKey",
            "runId",
            "function_call",
            "function_calls",
            "tool_calls",
        )
    )


def _extract_from_messages(messages: Any) -> str:
    if not isinstance(messages, list):
        return ""
    parts = [extract_spoken_text(item) for item in messages]
    return "\n".join(part for part in parts if part).strip()


def _extract_from_object(value: dict[str, Any]) -> str:
    preferred_keys = (
        "host_conclusion",
        "spoken_text",
        "speech",
        "tts",
        "text",
        "reply",
        "response",
        "message",
        "finalText",
        "outputText",
        "query_summary",
        "summary",
        "preview_markdown",
        "content",
    )
    for key in preferred_keys:
        candidate = extract_spoken_text(value.get(key))
        if candidate:
            return candidate

    nested_messages = _extract_from_messages(value.get("messages"))
    if nested_messages:
        return nested_messages

    if _looks_like_tool_envelope(value):
        return ""

    return ""


def extract_spoken_text(value: Any) -> str:
    if value is None:
        return ""

    if isinstance(value, str):
        structured = _extract_structured_text(value)
        if structured:
            return structured
        return _sanitize_plain_text(value)

    if isinstance(value, list):
        return "\n".join(
            _unique_strings([extract_spoken_text(item) for item in value])
        ).strip()

    if not _is_object(value):
        return _sanitize_plain_text(str(value))

    if value.get("type") == "text" and isinstance(value.get("text"), str):
        return extract_spoken_text(value["text"])

    return _extract_from_object(value)

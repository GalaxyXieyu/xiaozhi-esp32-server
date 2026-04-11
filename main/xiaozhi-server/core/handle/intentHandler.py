import json
import uuid
import asyncio
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from core.connection import ConnectionHandler
from core.utils.dialogue import Message
from core.providers.tts.dto.dto import ContentType
from core.handle.helloHandle import checkWakeupWords
from plugins_func.register import Action, ActionResponse
from core.handle.sendAudioHandle import send_stt_message
from core.handle.reportHandle import enqueue_tool_report
from core.utils.util import remove_punctuation_and_length
from core.providers.tts.dto.dto import TTSMessageDTO, SentenceType

TAG = __name__
DEFAULT_SWITCH_PREFIXES = (
    "切换到",
    "切换",
    "切到",
    "切换成",
    "换成",
    "换",
    "换到",
    "绑定到",
    "转到",
    "使用",
)


async def handle_user_intent(conn: "ConnectionHandler", text):
    # 预处理输入文本，处理可能的JSON格式
    try:
        if text.strip().startswith("{") and text.strip().endswith("}"):
            parsed_data = json.loads(text)
            if isinstance(parsed_data, dict) and "content" in parsed_data:
                text = parsed_data["content"]  # 提取content用于意图分析
                conn.current_speaker = parsed_data.get("speaker")  # 保留说话人信息
    except (json.JSONDecodeError, TypeError):
        pass

    # 检查是否有明确的退出命令
    _, filtered_text = remove_punctuation_and_length(text)
    if await check_direct_exit(conn, filtered_text):
        return True

    # 明确再见不被打断
    if conn.is_exiting:
        return True

    # 检查是否是唤醒词
    if await checkWakeupWords(conn, filtered_text):
        return True

    if await try_handle_openclaw_switch_intent(conn, text):
        return True

    if conn.intent_type == "function_call":
        # 使用支持function calling的聊天方法,不再进行意图分析
        return False
    # 使用LLM进行意图分析
    intent_result = await analyze_intent_with_llm(conn, text)
    if not intent_result:
        return False
    # 会话开始时生成sentence_id
    conn.sentence_id = str(uuid.uuid4().hex)
    # 处理各种意图
    return await process_intent_result(conn, intent_result, text)


async def check_direct_exit(conn: "ConnectionHandler", text):
    """检查是否有明确的退出命令"""
    _, text = remove_punctuation_and_length(text)
    cmd_exit = conn.cmd_exit
    for cmd in cmd_exit:
        if text == cmd:
            conn.logger.bind(tag=TAG).info(f"识别到明确的退出命令: {text}")
            await send_stt_message(conn, text)
            await conn.close()
            return True
    return False


async def analyze_intent_with_llm(conn: "ConnectionHandler", text):
    """使用LLM分析用户意图"""
    if not hasattr(conn, "intent") or not conn.intent:
        conn.logger.bind(tag=TAG).warning("意图识别服务未初始化")
        return None

    # 对话历史记录
    dialogue = conn.dialogue
    try:
        intent_result = await conn.intent.detect_intent(conn, dialogue.dialogue, text)
        return intent_result
    except Exception as e:
        conn.logger.bind(tag=TAG).error(f"意图识别失败: {str(e)}")

    return None


async def process_intent_result(
    conn: "ConnectionHandler", intent_result, original_text
):
    """处理意图识别结果"""
    try:
        # 尝试将结果解析为JSON
        intent_data = json.loads(intent_result)

        # 检查是否有function_call
        if "function_call" in intent_data:
            # 直接从意图识别获取了function_call
            conn.logger.bind(tag=TAG).debug(
                f"检测到function_call格式的意图结果: {intent_data['function_call']['name']}"
            )
            function_name = intent_data["function_call"]["name"]
            if function_name == "continue_chat":
                return False

            if function_name == "result_for_context":
                await send_stt_message(conn, original_text)
                conn.client_abort = False

                def process_context_result():
                    conn.dialogue.put(Message(role="user", content=original_text))

                    from core.utils.current_time import get_current_time_info

                    current_time, today_date, today_weekday, lunar_date = (
                        get_current_time_info()
                    )

                    # 构建带上下文的基础提示
                    context_prompt = f"""当前时间：{current_time}
                                        今天日期：{today_date} ({today_weekday})
                                        今天农历：{lunar_date}

                                        请根据以上信息回答用户的问题：{original_text}"""

                    response = conn.intent.replyResult(context_prompt, original_text)
                    speak_txt(conn, response)

                conn.executor.submit(process_context_result)
                return True

            function_args = {}
            if "arguments" in intent_data["function_call"]:
                function_args = intent_data["function_call"]["arguments"]
                if function_args is None:
                    function_args = {}
            # 确保参数是字符串格式的JSON
            if isinstance(function_args, dict):
                function_args = json.dumps(function_args)

            function_call_data = {
                "name": function_name,
                "id": str(uuid.uuid4().hex),
                "arguments": function_args,
            }

            await send_stt_message(conn, original_text)
            conn.client_abort = False

            # 准备工具调用参数
            tool_input = {}
            if function_args:
                if isinstance(function_args, str):
                    tool_input = json.loads(function_args) if function_args else {}
                elif isinstance(function_args, dict):
                    tool_input = function_args

            # 上报工具调用
            enqueue_tool_report(conn, function_name, tool_input)

            # 使用executor执行函数调用和结果处理
            def process_function_call():
                conn.dialogue.put(Message(role="user", content=original_text))
                
                # 工具调用超时时间
                tool_call_timeout = int(conn.config.get("tool_call_timeout", 30))
                # 使用统一工具处理器处理所有工具调用
                try:
                    result = asyncio.run_coroutine_threadsafe(
                        conn.func_handler.handle_llm_function_call(
                            conn, function_call_data
                        ),
                        conn.loop,
                    ).result(timeout=tool_call_timeout)
                except Exception as e:
                    conn.logger.bind(tag=TAG).error(f"工具调用失败: {e}")
                    result = ActionResponse(
                        action=Action.ERROR, result="工具调用超时，请一会再试下哈", response="工具调用超时，请一会再试下哈"
                    )

                # 上报工具调用结果
                if result:
                    enqueue_tool_report(conn, function_name, tool_input, str(result.result) if result.result else None, report_tool_call=False)

                    if result.action == Action.RESPONSE:  # 直接回复前端
                        text = result.response
                        if text is not None:
                            speak_txt(conn, text)
                    elif result.action == Action.REQLLM:  # 调用函数后再请求llm生成回复
                        text = result.result
                        conn.dialogue.put(Message(role="tool", content=text))
                        llm_result = conn.intent.replyResult(text, original_text)
                        if llm_result is None:
                            llm_result = text
                        speak_txt(conn, llm_result)
                    elif (
                        result.action == Action.NOTFOUND
                        or result.action == Action.ERROR
                    ):
                        text = result.response if result.response else result.result
                        if text is not None:
                            speak_txt(conn, text)
                    elif function_name != "play_music":
                        # For backward compatibility with original code
                        # 获取当前最新的文本索引
                        text = result.response
                        if text is None:
                            text = result.result
                        if text is not None:
                            speak_txt(conn, text)

            # 将函数执行放在线程池中
            conn.executor.submit(process_function_call)
            return True
        return False
    except json.JSONDecodeError as e:
        conn.logger.bind(tag=TAG).error(f"处理意图结果时出错: {e}")
        return False


def speak_txt(conn: "ConnectionHandler", text, origin: str = "local_agent"):
    # 记录文本
    conn.tts_MessageText = text
    conn.set_response_origin(origin)
    conn.record_debug_event(
        event_source="server",
        event_type="assistant_text_prepared",
        direction="internal",
        origin=origin,
        summary_text=f"准备播报文本: {text}",
        payload={"textLength": len(text or "")},
    )

    conn.tts.tts_text_queue.put(
        TTSMessageDTO(
            sentence_id=conn.sentence_id,
            sentence_type=SentenceType.FIRST,
            content_type=ContentType.ACTION,
        )
    )
    conn.tts.tts_one_sentence(conn, ContentType.TEXT, content_detail=text)
    conn.tts.tts_text_queue.put(
        TTSMessageDTO(
            sentence_id=conn.sentence_id,
            sentence_type=SentenceType.LAST,
            content_type=ContentType.ACTION,
        )
    )
    conn.dialogue.put(Message(role="assistant", content=text))


async def try_handle_openclaw_switch_intent(
    conn: "ConnectionHandler", original_text: str
) -> bool:
    hub_config = conn.config.get("openclaw_hub", {}) or {}
    bridge_config = conn.config.get("openclaw_bridge", {}) or {}
    hub = getattr(getattr(conn, "server", None), "openclaw_hub", None)
    bridge = getattr(conn, "openclaw_bridge", None)

    if hub_config.get("enabled", False):
        if hub is None or not getattr(hub, "enabled", False):
            return False
    elif bridge_config.get("enabled", False):
        if bridge is None or not getattr(bridge, "enabled", False):
            return False
    else:
        return False

    switch_config = _get_openclaw_switch_config(conn)
    aliases = switch_config.get("aliases", {})
    if not isinstance(aliases, dict) or not aliases:
        return False

    _, normalized_text = remove_punctuation_and_length(original_text)
    if not normalized_text:
        return False

    matched_alias, agent_id = _match_openclaw_agent_alias(
        normalized_text,
        aliases,
        switch_config,
    )
    if not matched_alias or not agent_id:
        return False

    await send_stt_message(conn, original_text)
    conn.client_abort = False
    conn.sentence_id = str(uuid.uuid4().hex)

    try:
        result = await bridge.bind_peer_agent(
            agent_id=agent_id,
            agent_name=matched_alias,
        )
        confirmation = result.get("confirmation")
        if not confirmation:
            template = switch_config.get(
                "confirmation_template",
                "好的，已切换到{agent_name}",
            )
            confirmation = template.format(
                agent_name=matched_alias,
                agent_id=result.get("agentId", agent_id),
            )
        speak_txt(conn, confirmation)
    except Exception as e:
        conn.logger.bind(tag=TAG).error(f"OpenClaw 切换 agent 失败: {e}")
        speak_txt(conn, "切换助理失败了，请稍后再试")

    return True


def _match_openclaw_agent_alias(
    normalized_text: str,
    aliases: dict,
    switch_config: dict,
):
    prefixes = switch_config.get("prefixes") or list(DEFAULT_SWITCH_PREFIXES)
    allow_alias_only = bool(switch_config.get("allow_alias_only", False))

    for alias, agent_id in aliases.items():
        _, normalized_alias = remove_punctuation_and_length(str(alias))
        if not normalized_alias:
            continue

        if allow_alias_only and normalized_text == normalized_alias:
            return alias, str(agent_id)

        for prefix in prefixes:
            candidate = f"{prefix}{normalized_alias}"
            if normalized_text == candidate:
                return alias, str(agent_id)

    return None, None


def _get_openclaw_switch_config(conn: "ConnectionHandler") -> dict:
    hub_config = conn.config.get("openclaw_hub", {}) or {}
    if hub_config.get("enabled", False):
        return hub_config.get("switch_agent", {}) or {}
    return conn.config.get("openclaw_bridge", {}).get("switch_agent", {}) or {}

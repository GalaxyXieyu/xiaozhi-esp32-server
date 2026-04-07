from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from core.connection import ConnectionHandler

from config.logger import setup_logging
from plugins_func.register import Action, ActionResponse, ToolType, register_function

TAG = __name__
logger = setup_logging()

openclaw_bind_peer_agent_desc = {
    "type": "function",
    "function": {
        "name": "openclaw_bind_peer_agent",
        "description": "当用户明确要求切换到另一个助理、工作助理、生活助理或指定 OpenClaw agent 时调用。",
        "parameters": {
            "type": "object",
            "properties": {
                "agent_id": {
                    "type": "string",
                    "description": "目标 agent 的唯一标识",
                },
                "agent_name": {
                    "type": "string",
                    "description": "给用户确认播报时使用的友好名称，例如 工作助理",
                },
                "peer_id": {
                    "type": "string",
                    "description": "可选，覆盖当前会话自动推导出的 peer_id",
                },
            },
            "required": ["agent_id"],
        },
    },
}


@register_function(
    "openclaw_bind_peer_agent",
    openclaw_bind_peer_agent_desc,
    ToolType.SYSTEM_CTL,
)
async def openclaw_bind_peer_agent(
    conn: "ConnectionHandler",
    agent_id: str,
    agent_name: str | None = None,
    peer_id: str | None = None,
):
    bridge = getattr(conn, "openclaw_bridge", None)
    if bridge is None or not bridge.enabled:
        return ActionResponse(
            action=Action.ERROR,
            result="OpenClaw bridge 未启用",
            response="当前还没有配置 OpenClaw bridge",
        )

    try:
        result = await bridge.bind_peer_agent(
            agent_id=agent_id,
            peer_id=peer_id,
            agent_name=agent_name,
        )
        final_agent_name = agent_name or result.get("agentName") or agent_id
        confirmation = result.get("confirmation") or f"好的，已切换到{final_agent_name}"
        logger.bind(tag=TAG).info(
            f"OpenClaw peer 切换完成: peer={peer_id or 'auto'}, agent={agent_id}"
        )
        return ActionResponse(
            action=Action.RESPONSE,
            result=result,
            response=confirmation,
        )
    except Exception as e:
        logger.bind(tag=TAG).error(f"OpenClaw peer 切换失败: {e}")
        return ActionResponse(
            action=Action.ERROR,
            result=str(e),
            response="切换助理失败了，请稍后再试",
        )

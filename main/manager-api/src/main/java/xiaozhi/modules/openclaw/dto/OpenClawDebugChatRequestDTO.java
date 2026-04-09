package xiaozhi.modules.openclaw.dto;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "OpenClaw 在线调试请求")
public class OpenClawDebugChatRequestDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "runtime/account")
    private String account;

    @Schema(description = "指定 bridgeId，可选")
    private String bridgeId;

    @Schema(description = "调试目标 agentId，可选")
    private String agentId;

    @Schema(description = "调试目标 agentName，可选")
    private String agentName;

    @Schema(description = "调试会话 ID")
    private String debugSessionId;

    @Schema(description = "目标小智 sessionId，可选；用于让主动推送命中真实设备")
    private String sessionId;

    @Schema(description = "目标小智 deviceId，可选；用于让主动推送命中真实设备")
    private String deviceId;

    @Schema(description = "目标 OpenClaw peerId，可选")
    private String peerId;

    @Schema(description = "调试输入文本")
    private String text;

    @Schema(description = "调试说话人标签")
    private String speaker;
}

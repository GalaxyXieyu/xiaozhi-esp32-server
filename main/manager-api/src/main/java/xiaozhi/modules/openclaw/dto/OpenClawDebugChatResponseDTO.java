package xiaozhi.modules.openclaw.dto;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "OpenClaw 在线调试响应")
public class OpenClawDebugChatResponseDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "channel id")
    private String channelId;

    @Schema(description = "请求来源地址")
    private String sourceUrl;

    @Schema(description = "runtime/account")
    private String account;

    @Schema(description = "bridge id")
    private String bridgeId;

    @Schema(description = "调试会话 ID")
    private String debugSessionId;

    @Schema(description = "OpenClaw peerId")
    private String peerId;

    @Schema(description = "最终命中的 agentId")
    private String agentId;

    @Schema(description = "最终命中的 agentName")
    private String agentName;

    @Schema(description = "当前请求是否已受理")
    private Boolean accepted;

    @Schema(description = "当前调试状态")
    private String status;

    @Schema(description = "是否同步推送到设备")
    private Boolean pushToDevice;

    @Schema(description = "是否为浏览器准备语音")
    private Boolean browserAudio;

    @Schema(description = "回复文本")
    private String replyText;

    @Schema(description = "原始响应")
    private Map<String, Object> rawResult = new LinkedHashMap<>();
}

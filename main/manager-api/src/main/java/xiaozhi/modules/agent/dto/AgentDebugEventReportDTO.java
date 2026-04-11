package xiaozhi.modules.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "智能体调试事件上报请求")
public class AgentDebugEventReportDTO {
    @Schema(description = "MAC地址", example = "00:11:22:33:44:55")
    @NotBlank
    private String macAddress;

    @Schema(description = "设备ID", example = "00:11:22:33:44:55")
    private String deviceId;

    @Schema(description = "会话ID", example = "79578c31-f1fb-426a-900e-1e934215f05a")
    @NotBlank
    private String sessionId;

    @Schema(description = "轮次ID", example = "turn_123")
    private String turnId;

    @Schema(description = "事件来源", example = "openclaw")
    @NotBlank
    private String eventSource;

    @Schema(description = "事件类型", example = "rpc_request")
    @NotBlank
    private String eventType;

    @Schema(description = "事件方向", example = "outbound")
    private String direction;

    @Schema(description = "内容来源归因", example = "local_agent")
    private String origin;

    @Schema(description = "摘要文本", example = "OpenClaw 请求 xiaozhi.chat")
    private String summaryText;

    @Schema(description = "结构化载荷(JSON字符串)", example = "{\"method\":\"xiaozhi.chat\"}")
    private String payloadJson;

    @Schema(description = "句子ID", example = "sentence_123")
    private String sentenceId;

    @Schema(description = "请求ID", example = "request_123")
    private String requestId;

    @Schema(description = "说话人", example = "后台调试")
    private String speaker;

    @Schema(description = "运行时账号", example = "default")
    private String runtimeAccount;

    @Schema(description = "状态", example = "ok")
    private String status;

    @Schema(description = "上报时间，十位时间戳，空时默认使用当前时间", example = "1745657732")
    private Long eventAt;
}

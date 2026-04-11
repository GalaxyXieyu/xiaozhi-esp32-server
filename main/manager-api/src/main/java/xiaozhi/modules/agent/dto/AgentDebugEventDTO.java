package xiaozhi.modules.agent.dto;

import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "智能体调试事件")
public class AgentDebugEventDTO {
    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "MAC地址")
    private String macAddress;

    @Schema(description = "设备ID")
    private String deviceId;

    @Schema(description = "会话ID")
    private String sessionId;

    @Schema(description = "轮次ID")
    private String turnId;

    @Schema(description = "事件来源")
    private String eventSource;

    @Schema(description = "事件类型")
    private String eventType;

    @Schema(description = "方向")
    private String direction;

    @Schema(description = "来源归因")
    private String origin;

    @Schema(description = "摘要文本")
    private String summaryText;

    @Schema(description = "结构化载荷")
    private String payloadJson;

    @Schema(description = "句子ID")
    private String sentenceId;

    @Schema(description = "请求ID")
    private String requestId;

    @Schema(description = "说话人")
    private String speaker;

    @Schema(description = "运行时账号")
    private String runtimeAccount;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "创建时间")
    private Date createdAt;
}

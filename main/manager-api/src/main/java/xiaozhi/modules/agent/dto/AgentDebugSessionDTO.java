package xiaozhi.modules.agent.dto;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "智能体调试会话")
public class AgentDebugSessionDTO {
    @Schema(description = "会话ID")
    private String sessionId;

    @Schema(description = "MAC地址")
    private String macAddress;

    @Schema(description = "设备ID")
    private String deviceId;

    @Schema(description = "最近事件时间")
    private LocalDateTime createdAt;

    @Schema(description = "事件数量")
    private Integer eventCount;
}

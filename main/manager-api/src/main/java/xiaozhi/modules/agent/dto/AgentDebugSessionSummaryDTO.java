package xiaozhi.modules.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "智能体调试会话统计")
public class AgentDebugSessionSummaryDTO {
    @Schema(description = "总事件数")
    private Long totalCount;

    @Schema(description = "Abort 次数")
    private Long abortCount;

    @Schema(description = "OpenClaw 事件数")
    private Long openclawCount;

    @Schema(description = "Fallback 次数")
    private Long fallbackCount;

    @Schema(description = "TTS Stop 次数")
    private Long ttsStopCount;
}

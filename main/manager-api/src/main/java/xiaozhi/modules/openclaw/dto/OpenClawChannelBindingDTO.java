package xiaozhi.modules.openclaw.dto;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "OpenClaw channel 下的业务智能体绑定视图")
public class OpenClawChannelBindingDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "业务智能体 ID")
    private String agentId;

    @Schema(description = "业务智能体名称")
    private String agentName;

    @Schema(description = "智能体类型，native / openclaw")
    private String agentType;

    @Schema(description = "绑定的 channel id")
    private String channelId;

    @Schema(description = "OpenClaw runtime/account 标识")
    private String runtimeAccount;

    @Schema(description = "OpenClaw runtime/account 展示名")
    private String runtimeAccountLabel;

    @Schema(description = "OpenClaw 侧 agent 标识")
    private String openclawAgentId;

    @Schema(description = "OpenClaw 侧 agent 展示名")
    private String openclawAgentName;

    @Schema(description = "最近同步状态")
    private String syncStatus;

    @Schema(description = "最近错误信息")
    private String errorMessage;
}

package xiaozhi.modules.openclaw.dto;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "OpenClaw channel 安装引导")
public class OpenClawChannelSetupGuideDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "channel id / OpenClaw account id")
    private String channelId;

    @Schema(description = "channel 展示名")
    private String channelName;

    @Schema(description = "xiaozhi-server 对外地址")
    private String serverUrl;

    @Schema(description = "管理接口基础地址")
    private String baseUrl;

    @Schema(description = "inventory 路径")
    private String inventoryPath;

    @Schema(description = "默认 agent id")
    private String defaultAgentId;

    @Schema(description = "是否已配置 access token")
    private Boolean accessTokenConfigured;

    @Schema(description = "可直接复制执行的安装命令")
    private String installCommand;
}

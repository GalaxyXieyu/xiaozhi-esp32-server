package xiaozhi.modules.openclaw.dto;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "OpenClaw channel 配置")
public class OpenClawChannelDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "channel 唯一标识")
    private String id;

    @Schema(description = "channel 展示名称")
    private String name;

    @Schema(description = "OpenClaw 管理接口基础地址，例如 https://example.com/admin/openclaw")
    private String baseUrl;

    @Schema(description = "inventory 路径，默认 /inventory")
    private String inventoryPath;

    @Schema(description = "访问 token")
    private String accessToken;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "备注")
    private String remark;
}

package xiaozhi.modules.openclaw.dto;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "详细稿投递绑定配置")
public class OpenClawDeliveryBindingDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "是否启用详细稿投递")
    private Boolean enabled;

    @Schema(description = "OpenClaw outbound channel，例如 openclaw-lark / feishu / slack")
    private String deliveryChannel;

    @Schema(description = "OpenClaw outbound accountId")
    private String accountId;

    @Schema(description = "OpenClaw outbound account 展示名")
    private String accountLabel;

    @Schema(description = "OpenClaw outbound target")
    private String target;

    @Schema(description = "OpenClaw outbound target 展示名")
    private String targetLabel;

    @Schema(description = "OpenClaw outbound threadId")
    private String threadId;

    @Schema(description = "投递格式，默认 text，可选 text / card")
    private String format;
}

package xiaozhi.modules.openclaw.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "OpenClaw channel inventory 响应")
public class OpenClawChannelInventoryDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "channel id")
    private String channelId;

    @Schema(description = "请求来源地址")
    private String sourceUrl;

    @Schema(description = "是否健康")
    private Boolean healthy = false;

    @Schema(description = "错误信息")
    private String errorMessage;

    @Schema(description = "可选 runtime/account 列表")
    private List<OptionItem> runtimeAccounts = new ArrayList<>();

    @Schema(description = "可选 OpenClaw agent 列表")
    private List<OptionItem> agents = new ArrayList<>();

    @Data
    @Schema(description = "下拉选项")
    public static class OptionItem implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "选项值")
        private String value;

        @Schema(description = "选项标签")
        private String label;
    }
}

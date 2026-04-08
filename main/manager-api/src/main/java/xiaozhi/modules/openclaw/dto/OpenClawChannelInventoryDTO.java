package xiaozhi.modules.openclaw.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    @Schema(description = "Bridge 连接状态")
    private List<BridgeItem> bridges = new ArrayList<>();

    @Schema(description = "按 runtime/account 分组的 agent 列表")
    private Map<String, List<OptionItem>> accountAgents = new LinkedHashMap<>();

    @Schema(description = "按 bridge 分组的 agent 列表")
    private Map<String, List<OptionItem>> bridgeAgents = new LinkedHashMap<>();

    @Schema(description = "在线 bridge 数量")
    private Integer connectedBridgeCount = 0;

    @Data
    @Schema(description = "下拉选项")
    public static class OptionItem implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "选项值")
        private String value;

        @Schema(description = "选项标签")
        private String label;
    }

    @Data
    @Schema(description = "Bridge 状态")
    public static class BridgeItem implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "bridge id")
        private String bridgeId;

        @Schema(description = "bridge 名称")
        private String name;

        @Schema(description = "所属 account")
        private String account;

        @Schema(description = "是否在线")
        private Boolean connected = false;

        @Schema(description = "是否默认")
        private Boolean isDefault = false;

        @Schema(description = "最近连接时间")
        private String lastConnectedAt;

        @Schema(description = "最近断开时间")
        private String lastDisconnectedAt;
    }
}

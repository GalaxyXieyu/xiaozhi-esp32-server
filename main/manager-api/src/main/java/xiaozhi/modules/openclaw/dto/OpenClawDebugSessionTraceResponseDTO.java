package xiaozhi.modules.openclaw.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "OpenClaw 调试时间线响应")
public class OpenClawDebugSessionTraceResponseDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "channel id")
    private String channelId;

    @Schema(description = "请求来源地址")
    private String sourceUrl;

    @Schema(description = "调试会话 ID")
    private String debugSessionId;

    @Schema(description = "runtime/account")
    private String account;

    @Schema(description = "bridge id")
    private String bridgeId;

    @Schema(description = "OpenClaw peerId")
    private String peerId;

    @Schema(description = "命中的 agentId")
    private String agentId;

    @Schema(description = "命中的 agentName")
    private String agentName;

    @Schema(description = "当前状态")
    private String status;

    @Schema(description = "是否仍在处理中")
    private Boolean pending;

    @Schema(description = "下一个事件序号")
    private Integer nextSeq;

    @Schema(description = "最新回复文本")
    private String latestReplyText;

    @Schema(description = "最新错误信息")
    private String latestError;

    @Schema(description = "浏览器语音")
    private BrowserAudio browserAudio = new BrowserAudio();

    @Schema(description = "设备投递状态")
    private DeviceDelivery deviceDelivery = new DeviceDelivery();

    @Schema(description = "最近更新时间")
    private Long updatedAt;

    @Schema(description = "创建时间")
    private Long createdAt;

    @Schema(description = "增量事件")
    private List<TraceEvent> events = new ArrayList<>();

    @Schema(description = "原始响应")
    private Map<String, Object> rawResult = new LinkedHashMap<>();

    @Data
    public static class BrowserAudio implements Serializable {
        private static final long serialVersionUID = 1L;

        private Boolean enabled;
        private Boolean ready;
        private String kind;
        private String text;
    }

    @Data
    public static class DeviceDelivery implements Serializable {
        private static final long serialVersionUID = 1L;

        private Boolean enabled;
        private String status;
        private String message;
    }

    @Data
    public static class TraceEvent implements Serializable {
        private static final long serialVersionUID = 1L;

        private Integer seq;
        private String type;
        private Long timestamp;
        private String title;
        private String message;
        private String status;
        private String agentId;
        private String agentName;
        private String sessionKey;
        private Map<String, Object> payload = new LinkedHashMap<>();
    }
}

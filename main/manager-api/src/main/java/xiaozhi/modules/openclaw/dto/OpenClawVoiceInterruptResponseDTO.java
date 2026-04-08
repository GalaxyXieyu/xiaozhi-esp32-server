package xiaozhi.modules.openclaw.dto;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "OpenClaw 语音打断响应")
public class OpenClawVoiceInterruptResponseDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "channel id")
    private String channelId;

    @Schema(description = "请求来源地址")
    private String sourceUrl;

    @Schema(description = "当前语音打断是否开启")
    private Boolean enabled;

    @Schema(description = "作用域：global/device/session")
    private String scope;

    @Schema(description = "状态来源：runtime-default/connection/persisted")
    private String source;

    @Schema(description = "sessionId")
    private String sessionId;

    @Schema(description = "deviceId")
    private String deviceId;

    @Schema(description = "在线更新连接数")
    private Integer updatedConnections = 0;

    @Schema(description = "跳过连接数")
    private Integer skippedConnections = 0;

    @Schema(description = "是否已持久化")
    private Boolean persisted = false;

    @Schema(description = "设备当前是否在线")
    private Boolean online;

    @Schema(description = "原始响应")
    private Map<String, Object> rawResult = new LinkedHashMap<>();
}

package xiaozhi.modules.openclaw.dto;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "OpenClaw 清理会话响应")
public class OpenClawClearSessionResponseDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "channel id")
    private String channelId;

    @Schema(description = "请求来源地址")
    private String sourceUrl;

    @Schema(description = "runtime/account")
    private String account;

    @Schema(description = "bridge id")
    private String bridgeId;

    @Schema(description = "sessionId")
    private String sessionId;

    @Schema(description = "deviceId")
    private String deviceId;

    @Schema(description = "peerId")
    private String peerId;

    @Schema(description = "原始响应")
    private Map<String, Object> rawResult = new LinkedHashMap<>();
}

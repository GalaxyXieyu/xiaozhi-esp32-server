package xiaozhi.modules.openclaw.dto;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "OpenClaw 清理会话请求")
public class OpenClawClearSessionRequestDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "runtime/account")
    private String account;

    @Schema(description = "指定 bridgeId，可选")
    private String bridgeId;

    @Schema(description = "调试会话 ID")
    private String sessionId;

    @Schema(description = "deviceId，可选")
    private String deviceId;

    @Schema(description = "peerId，可选")
    private String peerId;

    @Schema(description = "允许 fallback 到 latest")
    private Boolean allowLatest = false;
}

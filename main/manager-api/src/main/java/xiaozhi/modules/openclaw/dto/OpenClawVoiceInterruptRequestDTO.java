package xiaozhi.modules.openclaw.dto;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "OpenClaw 语音打断请求")
public class OpenClawVoiceInterruptRequestDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "是否开启语音打断")
    private Boolean enabled;

    @Schema(description = "sessionId，可选")
    private String sessionId;

    @Schema(description = "deviceId，可选")
    private String deviceId;

    @Schema(description = "peerId，可选")
    private String peerId;

    @Schema(description = "允许 fallback 到最新连接")
    private Boolean allowLatest = false;

    @Schema(description = "是否持久化到设备维度")
    private Boolean persist = false;
}

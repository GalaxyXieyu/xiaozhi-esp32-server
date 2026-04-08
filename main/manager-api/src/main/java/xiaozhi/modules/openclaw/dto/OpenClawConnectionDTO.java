package xiaozhi.modules.openclaw.dto;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "OpenClaw 在线设备连接")
public class OpenClawConnectionDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "sessionId")
    private String sessionId;

    @Schema(description = "deviceId")
    private String deviceId;

    @Schema(description = "客户端 IP")
    private String clientIp;

    @Schema(description = "注册时间戳，秒")
    private Double registeredAt;

    @Schema(description = "是否最新连接")
    private Boolean isLatest = false;

    @Schema(description = "当前连接上的语音打断开关")
    private Boolean voiceInterruptEnabled = true;
}

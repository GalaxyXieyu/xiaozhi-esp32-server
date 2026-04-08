package xiaozhi.modules.openclaw.controller;

import java.util.List;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import xiaozhi.common.user.UserDetail;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.agent.service.AgentService;
import xiaozhi.modules.openclaw.dto.OpenClawAgentBindingDTO;
import xiaozhi.modules.openclaw.dto.OpenClawChannelDTO;
import xiaozhi.modules.openclaw.dto.OpenClawChannelInventoryDTO;
import xiaozhi.modules.openclaw.dto.OpenClawChannelSetupGuideDTO;
import xiaozhi.modules.openclaw.dto.OpenClawClearSessionRequestDTO;
import xiaozhi.modules.openclaw.dto.OpenClawClearSessionResponseDTO;
import xiaozhi.modules.openclaw.dto.OpenClawConnectionDTO;
import xiaozhi.modules.openclaw.dto.OpenClawDebugChatRequestDTO;
import xiaozhi.modules.openclaw.dto.OpenClawDebugChatResponseDTO;
import xiaozhi.modules.openclaw.dto.OpenClawVoiceInterruptRequestDTO;
import xiaozhi.modules.openclaw.dto.OpenClawVoiceInterruptResponseDTO;
import xiaozhi.modules.openclaw.service.OpenClawConfigService;
import xiaozhi.modules.security.user.SecurityUser;

@RestController
@RequestMapping("/openclaw-config")
@Tag(name = "OpenClaw 配置")
@AllArgsConstructor
public class OpenClawConfigController {
    private final OpenClawConfigService openClawConfigService;
    private final AgentService agentService;

    @GetMapping("/channels")
    @Operation(summary = "获取 OpenClaw channels")
    @RequiresPermissions("sys:role:normal")
    public Result<List<OpenClawChannelDTO>> getChannels() {
        return new Result<List<OpenClawChannelDTO>>().ok(openClawConfigService.getChannels());
    }

    @PutMapping("/channels")
    @Operation(summary = "保存 OpenClaw channels")
    @RequiresPermissions("sys:role:normal")
    public Result<List<OpenClawChannelDTO>> saveChannels(@RequestBody List<OpenClawChannelDTO> channels,
                                                         HttpServletRequest request) {
        return new Result<List<OpenClawChannelDTO>>().ok(openClawConfigService.saveChannels(channels, resolveServerOrigin(request)));
    }

    @GetMapping("/channels/{channelId}/inventory")
    @Operation(summary = "获取 OpenClaw channel inventory")
    @RequiresPermissions("sys:role:normal")
    public Result<OpenClawChannelInventoryDTO> getChannelInventory(@PathVariable String channelId) {
        return new Result<OpenClawChannelInventoryDTO>().ok(openClawConfigService.getChannelInventory(channelId));
    }

    @GetMapping("/channels/{channelId}/setup-guide")
    @Operation(summary = "获取 OpenClaw channel 安装命令")
    @RequiresPermissions("sys:role:normal")
    public Result<OpenClawChannelSetupGuideDTO> getChannelSetupGuide(@PathVariable String channelId,
                                                                     HttpServletRequest request) {
        OpenClawChannelSetupGuideDTO guide = openClawConfigService.getChannelSetupGuide(
                channelId,
                "",
                resolveServerOrigin(request)
        );
        return new Result<OpenClawChannelSetupGuideDTO>().ok(guide);
    }

    @PostMapping("/channels/{channelId}/direct-chat")
    @Operation(summary = "OpenClaw 在线调试对话")
    @RequiresPermissions("sys:role:normal")
    public Result<OpenClawDebugChatResponseDTO> directChat(@PathVariable String channelId,
                                                           @RequestBody OpenClawDebugChatRequestDTO request) {
        try {
            return new Result<OpenClawDebugChatResponseDTO>().ok(openClawConfigService.directChat(channelId, request));
        } catch (Exception e) {
            return new Result<OpenClawDebugChatResponseDTO>().error("OpenClaw 在线调试失败: " + e.getMessage());
        }
    }

    @PostMapping("/channels/{channelId}/clear-session")
    @Operation(summary = "清理 OpenClaw 调试会话")
    @RequiresPermissions("sys:role:normal")
    public Result<OpenClawClearSessionResponseDTO> clearSession(@PathVariable String channelId,
                                                                @RequestBody OpenClawClearSessionRequestDTO request) {
        try {
            return new Result<OpenClawClearSessionResponseDTO>().ok(openClawConfigService.clearSession(channelId, request));
        } catch (Exception e) {
            return new Result<OpenClawClearSessionResponseDTO>().error("清理 OpenClaw 会话失败: " + e.getMessage());
        }
    }

    @GetMapping("/channels/{channelId}/connections")
    @Operation(summary = "获取 OpenClaw 在线设备连接")
    @RequiresPermissions("sys:role:normal")
    public Result<List<OpenClawConnectionDTO>> listConnections(@PathVariable String channelId) {
        try {
            return new Result<List<OpenClawConnectionDTO>>().ok(openClawConfigService.listConnections(channelId));
        } catch (Exception e) {
            return new Result<List<OpenClawConnectionDTO>>().error("获取 OpenClaw 在线设备失败: " + e.getMessage());
        }
    }

    @GetMapping("/channels/{channelId}/voice-interrupt")
    @Operation(summary = "获取 OpenClaw 运行时语音打断状态")
    @RequiresPermissions("sys:role:normal")
    public Result<OpenClawVoiceInterruptResponseDTO> getVoiceInterrupt(
            @PathVariable String channelId,
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String peerId,
            @RequestParam(required = false) Boolean allowLatest) {
        try {
            OpenClawVoiceInterruptRequestDTO request = new OpenClawVoiceInterruptRequestDTO();
            request.setSessionId(sessionId);
            request.setDeviceId(deviceId);
            request.setPeerId(peerId);
            request.setAllowLatest(allowLatest);
            return new Result<OpenClawVoiceInterruptResponseDTO>().ok(openClawConfigService.getVoiceInterrupt(channelId, request));
        } catch (Exception e) {
            return new Result<OpenClawVoiceInterruptResponseDTO>().error("获取 OpenClaw 语音打断状态失败: " + e.getMessage());
        }
    }

    @PostMapping("/channels/{channelId}/voice-interrupt")
    @Operation(summary = "设置 OpenClaw 运行时语音打断")
    @RequiresPermissions("sys:role:normal")
    public Result<OpenClawVoiceInterruptResponseDTO> setVoiceInterrupt(
            @PathVariable String channelId,
            @RequestBody OpenClawVoiceInterruptRequestDTO request) {
        try {
            return new Result<OpenClawVoiceInterruptResponseDTO>().ok(openClawConfigService.setVoiceInterrupt(channelId, request));
        } catch (Exception e) {
            return new Result<OpenClawVoiceInterruptResponseDTO>().error("设置 OpenClaw 语音打断失败: " + e.getMessage());
        }
    }

    @GetMapping("/agents/{agentId}")
    @Operation(summary = "获取智能体 OpenClaw 扩展配置")
    @RequiresPermissions("sys:role:normal")
    public Result<OpenClawAgentBindingDTO> getAgentBinding(@PathVariable String agentId) {
        if (!hasAgentPermission(agentId)) {
            return new Result<OpenClawAgentBindingDTO>().error("没有权限查看该智能体的 OpenClaw 配置");
        }
        return new Result<OpenClawAgentBindingDTO>().ok(openClawConfigService.getAgentBinding(agentId));
    }

    @PutMapping("/agents/{agentId}")
    @Operation(summary = "保存智能体 OpenClaw 扩展配置")
    @RequiresPermissions("sys:role:normal")
    public Result<OpenClawAgentBindingDTO> saveAgentBinding(@PathVariable String agentId,
                                                            @RequestBody OpenClawAgentBindingDTO binding) {
        if (!hasAgentPermission(agentId)) {
            return new Result<OpenClawAgentBindingDTO>().error("没有权限修改该智能体的 OpenClaw 配置");
        }
        return new Result<OpenClawAgentBindingDTO>().ok(openClawConfigService.saveAgentBinding(agentId, binding));
    }

    private boolean hasAgentPermission(String agentId) {
        UserDetail user = SecurityUser.getUser();
        return user != null && agentService.checkAgentPermission(agentId, user.getId());
    }

    private String resolveServerOrigin(HttpServletRequest request) {
        String forwardedProto = firstForwardedValue(request.getHeader("X-Forwarded-Proto"));
        String forwardedHost = firstForwardedValue(request.getHeader("X-Forwarded-Host"));
        String forwardedPort = firstForwardedValue(request.getHeader("X-Forwarded-Port"));

        String scheme = isBlank(forwardedProto) ? request.getScheme() : forwardedProto;
        String host = isBlank(forwardedHost) ? request.getHeader("Host") : forwardedHost;
        if (isBlank(host)) {
            host = request.getServerName();
            int port = request.getServerPort();
            if (port > 0 && !isDefaultPort(scheme, port)) {
                host = host + ":" + port;
            }
        } else if (!host.contains(":") && !isBlank(forwardedPort)) {
            try {
                int port = Integer.parseInt(forwardedPort);
                if (!isDefaultPort(scheme, port)) {
                    host = host + ":" + port;
                }
            } catch (NumberFormatException ignore) {
                // ignore invalid forwarded port
            }
        }
        return scheme + "://" + host;
    }

    private String firstForwardedValue(String headerValue) {
        if (headerValue == null) {
            return "";
        }
        String[] parts = headerValue.split(",");
        return parts.length == 0 ? "" : parts[0].trim();
    }

    private boolean isDefaultPort(String scheme, int port) {
        return ("http".equalsIgnoreCase(scheme) && port == 80)
                || ("https".equalsIgnoreCase(scheme) && port == 443);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

package xiaozhi.modules.agent.controller;

import java.util.List;
import java.util.Map;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import xiaozhi.common.constant.Constant;
import xiaozhi.common.page.PageData;
import xiaozhi.common.user.UserDetail;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.agent.dto.AgentDebugEventDTO;
import xiaozhi.modules.agent.dto.AgentDebugEventReportDTO;
import xiaozhi.modules.agent.dto.AgentDebugSessionDTO;
import xiaozhi.modules.agent.dto.AgentDebugSessionSummaryDTO;
import xiaozhi.modules.agent.service.AgentDebugEventService;
import xiaozhi.modules.agent.service.AgentService;
import xiaozhi.modules.agent.service.biz.AgentDebugEventBizService;
import xiaozhi.modules.security.user.SecurityUser;

@Tag(name = "智能体调试时间线")
@RequiredArgsConstructor
@RestController
@RequestMapping("/agent/debug-timeline")
public class AgentDebugTimelineController {
    private final AgentDebugEventBizService agentDebugEventBizService;
    private final AgentDebugEventService agentDebugEventService;
    private final AgentService agentService;

    @Operation(summary = "小智服务调试事件上报")
    @PostMapping("/report")
    public Result<Boolean> report(@Valid @RequestBody AgentDebugEventReportDTO request) {
        Boolean result = agentDebugEventBizService.report(request);
        return new Result<Boolean>().ok(result);
    }

    @GetMapping("/{agentId}/sessions")
    @Operation(summary = "获取调试时间线会话列表")
    @RequiresPermissions("sys:role:normal")
    @Parameters({
            @Parameter(name = Constant.PAGE, description = "当前页码，从1开始", required = true),
            @Parameter(name = Constant.LIMIT, description = "每页显示记录数", required = true),
            @Parameter(name = "deviceId", description = "设备ID", required = false),
    })
    public Result<PageData<AgentDebugSessionDTO>> getSessions(
            @PathVariable("agentId") String agentId,
            @Parameter(hidden = true) @RequestParam Map<String, Object> params) {
        if (!hasPermission(agentId)) {
            return new Result<PageData<AgentDebugSessionDTO>>().error("没有权限查看该智能体的调试时间线");
        }

        params.put("agentId", agentId);
        PageData<AgentDebugSessionDTO> page = agentDebugEventService.getSessionListByAgentId(params);
        return new Result<PageData<AgentDebugSessionDTO>>().ok(page);
    }

    @GetMapping("/{agentId}/{sessionId}")
    @Operation(summary = "获取会话调试时间线")
    @RequiresPermissions("sys:role:normal")
    public Result<List<AgentDebugEventDTO>> getTimeline(
            @PathVariable("agentId") String agentId,
            @PathVariable("sessionId") String sessionId,
            @Parameter(hidden = true) @RequestParam Map<String, Object> params) {
        if (!hasPermission(agentId)) {
            return new Result<List<AgentDebugEventDTO>>().error("没有权限查看该智能体的调试时间线");
        }

        List<AgentDebugEventDTO> data = agentDebugEventService.getTimelineBySessionId(agentId, sessionId, params);
        return new Result<List<AgentDebugEventDTO>>().ok(data);
    }

    @GetMapping("/{agentId}/{sessionId}/summary")
    @Operation(summary = "获取会话调试时间线统计")
    @RequiresPermissions("sys:role:normal")
    public Result<AgentDebugSessionSummaryDTO> getSummary(
            @PathVariable("agentId") String agentId,
            @PathVariable("sessionId") String sessionId,
            @RequestParam(value = "deviceId", required = false) String deviceId) {
        if (!hasPermission(agentId)) {
            return new Result<AgentDebugSessionSummaryDTO>().error("没有权限查看该智能体的调试时间线");
        }

        AgentDebugSessionSummaryDTO data = agentDebugEventService.getSessionSummary(agentId, sessionId, deviceId);
        return new Result<AgentDebugSessionSummaryDTO>().ok(data);
    }

    private boolean hasPermission(String agentId) {
        UserDetail user = SecurityUser.getUser();
        return user != null && agentService.checkAgentPermission(agentId, user.getId());
    }
}

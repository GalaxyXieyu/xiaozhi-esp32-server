package xiaozhi.modules.agent.service;

import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.extension.service.IService;

import xiaozhi.common.page.PageData;
import xiaozhi.modules.agent.dto.AgentDebugEventDTO;
import xiaozhi.modules.agent.dto.AgentDebugSessionDTO;
import xiaozhi.modules.agent.dto.AgentDebugSessionSummaryDTO;
import xiaozhi.modules.agent.entity.AgentDebugEventEntity;

public interface AgentDebugEventService extends IService<AgentDebugEventEntity> {
    PageData<AgentDebugSessionDTO> getSessionListByAgentId(Map<String, Object> params);

    List<AgentDebugEventDTO> getTimelineBySessionId(String agentId, String sessionId, Map<String, Object> params);

    AgentDebugSessionSummaryDTO getSessionSummary(String agentId, String sessionId, String deviceId);

    void deleteByAgentId(String agentId);
}

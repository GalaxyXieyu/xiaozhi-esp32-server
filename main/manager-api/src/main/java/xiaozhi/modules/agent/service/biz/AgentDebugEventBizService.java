package xiaozhi.modules.agent.service.biz;

import xiaozhi.modules.agent.dto.AgentDebugEventReportDTO;

public interface AgentDebugEventBizService {
    Boolean report(AgentDebugEventReportDTO report);
}

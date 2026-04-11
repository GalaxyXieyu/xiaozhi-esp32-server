package xiaozhi.modules.agent.service.biz.impl;

import java.util.Date;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import xiaozhi.modules.agent.dto.AgentDebugEventReportDTO;
import xiaozhi.modules.agent.entity.AgentDebugEventEntity;
import xiaozhi.modules.agent.entity.AgentEntity;
import xiaozhi.modules.agent.service.AgentDebugEventService;
import xiaozhi.modules.agent.service.AgentService;
import xiaozhi.modules.agent.service.biz.AgentDebugEventBizService;

@Service
@Slf4j
@RequiredArgsConstructor
public class AgentDebugEventBizServiceImpl implements AgentDebugEventBizService {
    private final AgentService agentService;
    private final AgentDebugEventService agentDebugEventService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean report(AgentDebugEventReportDTO report) {
        String macAddress = report.getMacAddress();
        AgentEntity agentEntity = agentService.getDefaultAgentByMacAddress(macAddress);
        if (agentEntity == null) {
            return Boolean.FALSE;
        }

        Long createdAtMillis = report.getEventAt() != null
                ? report.getEventAt() * 1000
                : System.currentTimeMillis();

        AgentDebugEventEntity entity = AgentDebugEventEntity.builder()
                .macAddress(macAddress)
                .deviceId(StringUtils.defaultIfBlank(report.getDeviceId(), macAddress))
                .agentId(agentEntity.getId())
                .sessionId(report.getSessionId())
                .turnId(report.getTurnId())
                .eventSource(report.getEventSource())
                .eventType(report.getEventType())
                .direction(report.getDirection())
                .origin(report.getOrigin())
                .summaryText(StringUtils.defaultIfBlank(report.getSummaryText(), report.getEventType()))
                .payloadJson(normalizePayload(report.getPayloadJson()))
                .sentenceId(report.getSentenceId())
                .requestId(report.getRequestId())
                .speaker(report.getSpeaker())
                .runtimeAccount(report.getRuntimeAccount())
                .status(StringUtils.defaultIfBlank(report.getStatus(), "ok"))
                .createdAt(new Date(createdAtMillis))
                .build();

        agentDebugEventService.save(entity);
        log.info("调试事件上报成功: macAddress={}, agentId={}, sessionId={}, eventType={}",
                macAddress, agentEntity.getId(), report.getSessionId(), report.getEventType());
        return Boolean.TRUE;
    }

    private String normalizePayload(String payloadJson) {
        if (StringUtils.isBlank(payloadJson)) {
            return null;
        }
        String trimmed = payloadJson.trim();
        if (trimmed.length() <= 4000) {
            return trimmed;
        }
        return trimmed.substring(0, 4000);
    }
}

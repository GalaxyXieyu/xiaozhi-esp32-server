package xiaozhi.modules.agent.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import xiaozhi.common.constant.Constant;
import xiaozhi.common.page.PageData;
import xiaozhi.common.utils.ConvertUtils;
import xiaozhi.modules.agent.dao.AiAgentDebugEventDao;
import xiaozhi.modules.agent.dto.AgentDebugEventDTO;
import xiaozhi.modules.agent.dto.AgentDebugSessionDTO;
import xiaozhi.modules.agent.dto.AgentDebugSessionSummaryDTO;
import xiaozhi.modules.agent.entity.AgentDebugEventEntity;
import xiaozhi.modules.agent.service.AgentDebugEventService;

@Service
public class AgentDebugEventServiceImpl extends ServiceImpl<AiAgentDebugEventDao, AgentDebugEventEntity>
        implements AgentDebugEventService {

    @Override
    public PageData<AgentDebugSessionDTO> getSessionListByAgentId(Map<String, Object> params) {
        String agentId = (String) params.get("agentId");
        String deviceId = (String) params.get("deviceId");
        int page = Integer.parseInt(params.getOrDefault(Constant.PAGE, "1").toString());
        int limit = Integer.parseInt(params.getOrDefault(Constant.LIMIT, "20").toString());

        QueryWrapper<AgentDebugEventEntity> wrapper = new QueryWrapper<>();
        wrapper.select(
                        "session_id",
                        "MIN(mac_address) AS mac_address",
                        "MIN(device_id) AS device_id",
                        "MAX(created_at) AS created_at",
                        "COUNT(*) AS event_count")
                .eq("agent_id", agentId);

        if (StringUtils.isNotBlank(deviceId)) {
            wrapper.eq("device_id", deviceId);
        }

        wrapper.groupBy("session_id").orderByDesc("created_at");

        Page<Map<String, Object>> pageParam = new Page<>(page, limit);
        IPage<Map<String, Object>> result = this.baseMapper.selectMapsPage(pageParam, wrapper);

        List<AgentDebugSessionDTO> records = result.getRecords().stream().map(map -> {
            AgentDebugSessionDTO dto = new AgentDebugSessionDTO();
            dto.setSessionId((String) map.get("session_id"));
            dto.setMacAddress((String) map.get("mac_address"));
            dto.setDeviceId((String) map.get("device_id"));
            dto.setCreatedAt((LocalDateTime) map.get("created_at"));
            dto.setEventCount(intValue(map.get("event_count")));
            return dto;
        }).toList();

        return new PageData<>(records, result.getTotal());
    }

    @Override
    public List<AgentDebugEventDTO> getTimelineBySessionId(String agentId, String sessionId, Map<String, Object> params) {
        QueryWrapper<AgentDebugEventEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("agent_id", agentId)
                .eq("session_id", sessionId);

        addOptionalEquals(wrapper, "device_id", params.get("deviceId"));
        addOptionalEquals(wrapper, "turn_id", params.get("turnId"));
        addOptionalEquals(wrapper, "event_source", params.get("eventSource"));
        addOptionalEquals(wrapper, "origin", params.get("origin"));
        addOptionalEquals(wrapper, "status", params.get("status"));

        wrapper.orderByAsc("created_at", "id");

        List<AgentDebugEventEntity> events = list(wrapper);
        return ConvertUtils.sourceToTarget(events, AgentDebugEventDTO.class);
    }

    @Override
    public AgentDebugSessionSummaryDTO getSessionSummary(String agentId, String sessionId, String deviceId) {
        QueryWrapper<AgentDebugEventEntity> wrapper = new QueryWrapper<>();
        wrapper.select(
                        "COUNT(*) AS total_count",
                        "SUM(CASE WHEN event_type = 'abort_received' THEN 1 ELSE 0 END) AS abort_count",
                        "SUM(CASE WHEN event_source = 'openclaw' THEN 1 ELSE 0 END) AS openclaw_count",
                        "SUM(CASE WHEN status = 'fallback' THEN 1 ELSE 0 END) AS fallback_count",
                        "SUM(CASE WHEN event_type = 'tts_stop_sent' THEN 1 ELSE 0 END) AS tts_stop_count")
                .eq("agent_id", agentId)
                .eq("session_id", sessionId);

        if (StringUtils.isNotBlank(deviceId)) {
            wrapper.eq("device_id", deviceId);
        }

        List<Map<String, Object>> rows = this.baseMapper.selectMaps(wrapper);
        Map<String, Object> row = rows.isEmpty() ? Map.of() : rows.get(0);

        AgentDebugSessionSummaryDTO dto = new AgentDebugSessionSummaryDTO();
        dto.setTotalCount(longValue(row.get("total_count")));
        dto.setAbortCount(longValue(row.get("abort_count")));
        dto.setOpenclawCount(longValue(row.get("openclaw_count")));
        dto.setFallbackCount(longValue(row.get("fallback_count")));
        dto.setTtsStopCount(longValue(row.get("tts_stop_count")));
        return dto;
    }

    @Override
    public void deleteByAgentId(String agentId) {
        remove(new QueryWrapper<AgentDebugEventEntity>().eq("agent_id", agentId));
    }

    private void addOptionalEquals(QueryWrapper<AgentDebugEventEntity> wrapper, String column, Object value) {
        if (value != null && StringUtils.isNotBlank(value.toString())) {
            wrapper.eq(column, value.toString());
        }
    }

    private int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    private long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (Objects.nonNull(value)) {
            try {
                return Long.parseLong(value.toString());
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }
}

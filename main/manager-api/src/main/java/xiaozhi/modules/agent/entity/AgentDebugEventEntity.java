package xiaozhi.modules.agent.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName(value = "ai_agent_debug_event")
public class AgentDebugEventEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(value = "mac_address")
    private String macAddress;

    @TableField(value = "device_id")
    private String deviceId;

    @TableField(value = "agent_id")
    private String agentId;

    @TableField(value = "session_id")
    private String sessionId;

    @TableField(value = "turn_id")
    private String turnId;

    @TableField(value = "event_source")
    private String eventSource;

    @TableField(value = "event_type")
    private String eventType;

    @TableField(value = "direction")
    private String direction;

    @TableField(value = "origin")
    private String origin;

    @TableField(value = "summary_text")
    private String summaryText;

    @TableField(value = "payload_json")
    private String payloadJson;

    @TableField(value = "sentence_id")
    private String sentenceId;

    @TableField(value = "request_id")
    private String requestId;

    @TableField(value = "speaker")
    private String speaker;

    @TableField(value = "runtime_account")
    private String runtimeAccount;

    @TableField(value = "status")
    private String status;

    @TableField(value = "created_at")
    private Date createdAt;

    @TableField(value = "updated_at")
    private Date updatedAt;
}

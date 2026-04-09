<template>
  <aside class="debug-sidebar">
    <div class="sidebar-card">
      <div class="sidebar-head">
        <div>
          <div class="sidebar-eyebrow">Controls</div>
          <h3 class="sidebar-title">调试目标</h3>
        </div>
      </div>
      <div class="sidebar-summary">支持切换 agent、runtime 和真实设备上下文。</div>

      <div v-if="showRuntimeSelector" class="field-block">
        <label class="field-label">Runtime / Account</label>
        <el-select
          v-model="selectedAccount"
          class="field-select"
          filterable
          placeholder="选择 runtime/account"
        >
          <el-option
            v-for="item in runtimeAccounts"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </div>

      <div class="field-block">
        <label class="field-label">OpenClaw Agent</label>
        <el-select
          v-model="selectedAgentId"
          class="field-select"
          filterable
          :disabled="!currentDebugAgentOptions.length"
          placeholder="选择 OpenClaw Agent"
        >
          <el-option
            v-for="item in currentDebugAgentOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <div class="field-hint">
          支持在调试时直接切换 agent，消息会发送到当前所选的 OpenClaw Agent。
        </div>
        <div v-if="selectedAgentNeedsInventorySync" class="field-hint warning">
          当前 Agent 来自业务绑定，inventory 还没回传它。可先保留该选择，但要等对应 runtime 有在线 bridge 后才能真正调试。
        </div>
      </div>

      <div class="sidebar-divider" />

      <div class="field-block slim">
        <label class="field-label">结果落点</label>
        <div class="switch-row">
          <span class="switch-label">同步推设备</span>
          <el-switch v-model="localPushToDevice" />
        </div>
        <div class="field-hint">调试默认回到界面；开启后会在结果完成时同步推送到当前选中的真实设备。</div>
        <div class="switch-row compact">
          <span class="switch-label">浏览器语音</span>
          <el-switch v-model="localBrowserAudio" />
        </div>
        <div class="field-hint">开启后会在结果完成时准备浏览器侧手动播放。</div>
      </div>

      <div class="sidebar-divider" />

      <div class="field-block slim">
        <label class="field-label">在线连接</label>
        <el-select
          v-model="selectedConnectionKey"
          class="field-select"
          filterable
          clearable
          :loading="connectionsLoading"
          :disabled="!connectionItems.length"
          placeholder="选择在线连接，让 subagent 主动推送命中真实设备"
        >
          <el-option
            v-for="item in connectionItems"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <div class="field-hint">
          {{
            hasConnectionContext
              ? `当前会把 sessionId/deviceId 透传给调试链路。若 subagent 调用 xiaozhi_push_text，会优先命中 ${currentConnectionLabel}。`
              : "未选择真实设备时，当前调试只在后台链路里流转；subagent 的主动推送不会落到真实设备。"
          }}
        </div>
      </div>

      <div v-if="showBridgeSelector" class="field-block slim">
        <label class="field-label">Bridge</label>
        <el-select
          v-model="selectedBridgeId"
          class="field-select"
          clearable
          filterable
          placeholder="指定 bridge（可选）"
        >
          <el-option
            v-for="item in bridgeOptions"
            :key="item.bridgeId"
            :label="`${item.name || item.bridgeId} · ${item.connected ? '在线' : '离线'}`"
            :value="item.bridgeId"
          />
        </el-select>
        <div class="field-hint">默认会自动选择当前 runtime 下在线的 bridge，仅在需要定向排查时手动指定。</div>
      </div>
    </div>
  </aside>
</template>

<script>
export default {
  name: "OpenClawDebugControlPanel",
  props: {
    showRuntimeSelector: {
      type: Boolean,
      default: false,
    },
    runtimeAccounts: {
      type: Array,
      default: () => [],
    },
    account: {
      type: String,
      default: "",
    },
    currentDebugAgentOptions: {
      type: Array,
      default: () => [],
    },
    agentId: {
      type: String,
      default: "",
    },
    selectedAgentNeedsInventorySync: {
      type: Boolean,
      default: false,
    },
    connectionsLoading: {
      type: Boolean,
      default: false,
    },
    connectionItems: {
      type: Array,
      default: () => [],
    },
    connectionKey: {
      type: String,
      default: "",
    },
    hasConnectionContext: {
      type: Boolean,
      default: false,
    },
    currentConnectionLabel: {
      type: String,
      default: "",
    },
    showBridgeSelector: {
      type: Boolean,
      default: false,
    },
    bridgeOptions: {
      type: Array,
      default: () => [],
    },
    bridgeId: {
      type: String,
      default: "",
    },
    pushToDevice: {
      type: Boolean,
      default: false,
    },
    browserAudio: {
      type: Boolean,
      default: false,
    },
  },
  computed: {
    selectedAccount: {
      get() {
        return this.account;
      },
      set(value) {
        this.$emit("update:account", value);
      },
    },
    selectedAgentId: {
      get() {
        return this.agentId;
      },
      set(value) {
        this.$emit("update:agent-id", value);
      },
    },
    selectedConnectionKey: {
      get() {
        return this.connectionKey;
      },
      set(value) {
        this.$emit("update:connection-key", value);
      },
    },
    selectedBridgeId: {
      get() {
        return this.bridgeId;
      },
      set(value) {
        this.$emit("update:bridge-id", value);
      },
    },
    localPushToDevice: {
      get() {
        return this.pushToDevice;
      },
      set(value) {
        this.$emit("update:push-to-device", value);
      },
    },
    localBrowserAudio: {
      get() {
        return this.browserAudio;
      },
      set(value) {
        this.$emit("update:browser-audio", value);
      },
    },
  },
};
</script>

<style scoped>
.debug-sidebar {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.sidebar-card {
  padding: 16px;
  border-radius: 20px;
  background: #ffffff;
  border: 1px solid #e4e9f4;
  box-shadow: 0 8px 24px rgba(87, 104, 142, 0.08);
}

.sidebar-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.sidebar-eyebrow {
  font-size: 11px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: #7c8ca7;
}

.sidebar-title {
  margin: 0;
  color: #18243d;
  font-size: 20px;
}

.sidebar-summary {
  margin-top: 8px;
  color: #66758f;
  line-height: 1.6;
  font-size: 13px;
}

.field-block {
  margin-top: 14px;
}

.field-block.slim {
  margin-top: 12px;
}

.switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.switch-row.compact {
  margin-top: 10px;
}

.switch-label {
  color: #24344d;
  font-weight: 600;
}

.field-label {
  display: block;
  margin-bottom: 8px;
  color: #52627b;
  font-weight: 700;
}

.field-select {
  width: 100%;
}

.field-hint {
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.6;
  color: #6f7f99;
}

.field-hint.warning {
  color: #b26a19;
}

.sidebar-divider {
  height: 1px;
  margin-top: 14px;
  background: #eef2f7;
}

@media (max-width: 760px) {
  .sidebar-card {
    padding: 16px;
  }
}
</style>

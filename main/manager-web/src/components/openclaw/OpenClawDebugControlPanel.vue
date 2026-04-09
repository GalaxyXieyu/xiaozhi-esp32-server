<template>
  <aside class="debug-sidebar">
    <div class="sidebar-head">
      <h3 class="sidebar-title">调试设置</h3>
    </div>

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
      <div v-if="selectedAgentNeedsInventorySync" class="field-warning">
        当前 Agent 未出现在 inventory 中
      </div>
    </div>

    <div class="field-block slim">
      <label class="field-label">结果</label>
      <div class="switch-row">
        <span class="switch-label">推送到设备</span>
        <el-switch v-model="localPushToDevice" />
      </div>
      <div class="switch-row compact">
        <span class="switch-label">浏览器语音</span>
        <el-switch v-model="localBrowserAudio" />
      </div>
    </div>

    <div class="field-block slim">
      <label class="field-label">在线连接</label>
      <el-select
        v-model="selectedConnectionKey"
        class="field-select"
        filterable
        clearable
        :loading="connectionsLoading"
        :disabled="!connectionItems.length"
        placeholder="选择在线连接"
      >
        <el-option
          v-for="item in connectionItems"
          :key="item.value"
          :label="item.label"
          :value="item.value"
        />
      </el-select>
    </div>

    <div v-if="showBridgeSelector" class="field-block slim">
      <label class="field-label">Bridge</label>
      <el-select
        v-model="selectedBridgeId"
        class="field-select"
        clearable
        filterable
        placeholder="选择 bridge（可选）"
      >
        <el-option
          v-for="item in bridgeOptions"
          :key="item.bridgeId"
          :label="`${item.name || item.bridgeId} · ${item.connected ? '在线' : '离线'}`"
          :value="item.bridgeId"
        />
      </el-select>
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
  min-height: 0;
  overflow-y: auto;
  padding: 10px 0 0 6px;
}

.sidebar-head {
  display: flex;
  align-items: center;
}

.sidebar-title {
  margin: 0;
  color: #18243d;
  font-size: 18px;
}

.field-block {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #edf1f7;
}

.field-block.slim {
  margin-top: 14px;
  padding-top: 14px;
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

.field-warning {
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.6;
  color: #b26a19;
}

@media (max-width: 760px) {
  .debug-sidebar {
    padding: 8px 0 0;
  }
}
</style>

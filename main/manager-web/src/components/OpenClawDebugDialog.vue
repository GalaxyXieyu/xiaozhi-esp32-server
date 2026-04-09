<template>
  <el-dialog
    :visible.sync="dialogVisible"
    :title="dialogTitle"
    width="86%"
    top="4vh"
    custom-class="openclaw-debug-dialog"
    :before-close="handleClose"
  >
    <div class="debug-shell">
      <OpenClawDebugChatPane
        :channel-name="channelName"
        :has-available-bridge="hasAvailableBridge"
        :current-runtime-label="currentRuntimeLabel"
        :agent-label="debugForm.agentName || debugForm.agentId || '未选择'"
        :debug-session-id="debugForm.debugSessionId"
        :disable-clear-session="!debugForm.account || !debugForm.debugSessionId"
        :debug-clearing="debugClearing"
        :debug-messages="debugMessages"
        :debug-status-events="debugStatusEvents"
        :debug-history-sessions="debugHistorySessions"
        :input-text="debugForm.inputText"
        :debug-sending="debugSending"
        :can-send-direct-chat="canSendDirectChat"
        :debug-pending="debugPending"
        @create-session="createDebugSession"
        @clear-session="clearDebugSession"
        @restore-history="restoreDebugHistory"
        @delete-history="deleteDebugHistory"
        @update:input-text="updateDebugInputText"
        @send="sendDirectChat"
        @play-message="playDebugMessageAudio"
      />

      <OpenClawDebugControlPanel
        :show-runtime-selector="showRuntimeSelector"
        :runtime-accounts="runtimeAccounts"
        :account="debugForm.account"
        :current-debug-agent-options="currentDebugAgentOptions"
        :agent-id="debugForm.agentId"
        :selected-agent-needs-inventory-sync="selectedAgentNeedsInventorySync"
        :connections-loading="connectionsLoading"
        :connection-items="connectionItems"
        :connection-key="debugForm.connectionKey"
        :show-bridge-selector="showBridgeSelector"
        :bridge-options="bridgeOptions"
        :bridge-id="debugForm.bridgeId"
        :push-to-device="debugForm.pushToDevice"
        :browser-audio="debugForm.browserAudio"
        @update:account="handleDebugAccountChange"
        @update:agent-id="handleDebugAgentChange"
        @update:connection-key="handleConnectionChange"
        @update:bridge-id="handleDebugBridgeChange"
        @update:push-to-device="handlePushToDeviceChange"
        @update:browser-audio="handleBrowserAudioChange"
      />
    </div>
  </el-dialog>
</template>

<script>
import Api from "@/apis/api";
import OpenClawDebugChatPane from "@/components/openclaw/OpenClawDebugChatPane.vue";
import OpenClawDebugControlPanel from "@/components/openclaw/OpenClawDebugControlPanel.vue";

const createDebugSessionId = () => `web-debug-${Date.now()}`;
const DEBUG_HISTORY_PREFIX = "openclaw-debug-history:";
const MAX_DEBUG_HISTORY_SESSIONS = 6;
const MAX_DEBUG_HISTORY_MESSAGES = 30;
const MAX_DEBUG_STATUS_EVENTS = 24;
const buildConnectionKey = (item = {}) => `${item.sessionId || ""}::${item.deviceId || ""}`;
const STATUS_EVENT_TYPES = new Set([
  "accepted",
  "agent_bound",
  "subagent_spawned",
  "subagent_completed",
  "browser_audio_ready",
  "device_push_started",
  "device_push_succeeded",
  "device_push_failed",
  "failed",
]);

const createEmptyDebugForm = () => ({
  account: "",
  bridgeId: "",
  agentId: "",
  agentName: "",
  connectionKey: "",
  sessionId: "",
  deviceId: "",
  speaker: "后台调试",
  pushToDevice: false,
  browserAudio: true,
  inputText: "",
  debugSessionId: createDebugSessionId(),
});

const safeParseHistory = (raw) => {
  if (!raw) {
    return [];
  }
  try {
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : [];
  } catch (error) {
    return [];
  }
};

const formatHistoryTime = (timestamp) => {
  if (!timestamp) {
    return "";
  }
  const date = new Date(timestamp);
  if (Number.isNaN(date.getTime())) {
    return "";
  }
  return `${date.getMonth() + 1}/${date.getDate()} ${date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}`;
};

const normalizeHistoryEntry = (item = {}) => ({
  sessionId: typeof item.sessionId === "string" ? item.sessionId : "",
  account: typeof item.account === "string" ? item.account : "",
  bridgeId: typeof item.bridgeId === "string" ? item.bridgeId : "",
  connectionKey: typeof item.connectionKey === "string" ? item.connectionKey : "",
  targetSessionId: typeof item.targetSessionId === "string" ? item.targetSessionId : "",
  targetDeviceId: typeof item.targetDeviceId === "string" ? item.targetDeviceId : "",
  agentId: typeof item.agentId === "string" ? item.agentId : "",
  agentName: typeof item.agentName === "string" ? item.agentName : "",
  pushToDevice: Boolean(item.pushToDevice),
  browserAudio: item.browserAudio !== false,
  traceNextSeq: Number.isInteger(item.traceNextSeq) ? item.traceNextSeq : 0,
  latestBrowserAudioText: typeof item.latestBrowserAudioText === "string" ? item.latestBrowserAudioText : "",
  updatedAt: Number.isFinite(item.updatedAt) ? item.updatedAt : Date.now(),
  messages: Array.isArray(item.messages) ? item.messages.slice(-MAX_DEBUG_HISTORY_MESSAGES) : [],
  statusEvents: Array.isArray(item.statusEvents) ? item.statusEvents.slice(-MAX_DEBUG_STATUS_EVENTS) : [],
});

export default {
  name: "OpenClawDebugDialog",
  components: {
    OpenClawDebugChatPane,
    OpenClawDebugControlPanel,
  },
  props: {
    visible: {
      type: Boolean,
      default: false,
    },
    channel: {
      type: Object,
      default: () => ({}),
    },
    inventory: {
      type: Object,
      default: () => ({}),
    },
    connections: {
      type: Array,
      default: () => [],
    },
    connectionsLoading: {
      type: Boolean,
      default: false,
    },
    routePrefill: {
      type: Object,
      default: () => ({}),
    },
  },
  data() {
    return {
      dialogVisible: false,
      debugForm: createEmptyDebugForm(),
      debugMessages: [],
      debugStatusEvents: [],
      debugHistorySessions: [],
      debugSending: false,
      debugClearing: false,
      debugPending: false,
      routePrefillApplied: false,
      debugTraceSeq: 0,
      debugPollingTimer: null,
      latestBrowserAudioText: "",
    };
  },
  computed: {
    channelId() {
      return this.channel && this.channel.id ? this.channel.id : "";
    },
    channelName() {
      return this.channel && this.channel.name ? this.channel.name : "未选择 Channel";
    },
    dialogTitle() {
      return `OpenClaw 在线调试${this.channelId ? ` · ${this.channelName}` : ""}`;
    },
    runtimeAccounts() {
      return Array.isArray(this.inventory.runtimeAccounts) ? this.inventory.runtimeAccounts : [];
    },
    bridgeItems() {
      return Array.isArray(this.inventory.bridges) ? this.inventory.bridges : [];
    },
    agentItems() {
      return Array.isArray(this.inventory.agents) ? this.inventory.agents : [];
    },
    showRuntimeSelector() {
      return this.runtimeAccounts.length > 1;
    },
    bridgeOptions() {
      if (!this.debugForm.account) {
        return this.bridgeItems;
      }
      return this.bridgeItems.filter((item) => item.account === this.debugForm.account);
    },
    showBridgeSelector() {
      return this.bridgeOptions.length > 1;
    },
    connectionItems() {
      return (Array.isArray(this.connections) ? this.connections : []).map((item) => ({
        ...item,
        value: buildConnectionKey(item),
        label: `${item.deviceId || "未知设备"}${item.isLatest ? " · 最新" : ""}${item.sessionId ? ` · ${item.sessionId}` : ""}`,
      }));
    },
    currentRuntimeLabel() {
      const matched = this.runtimeAccounts.find((item) => item.value === this.debugForm.account);
      return matched ? matched.label : (this.debugForm.account || "自动选择");
    },
    currentDebugAgentOptions() {
      const bridgeKey = this.debugForm.bridgeId;
      const bridgeAgents = (this.inventory.bridgeAgents && this.inventory.bridgeAgents[bridgeKey]) || [];
      let options = [];
      if (Array.isArray(bridgeAgents) && bridgeAgents.length) {
        options = bridgeAgents;
      } else {
        const accountKey = this.debugForm.account;
        const accountAgents = (this.inventory.accountAgents && this.inventory.accountAgents[accountKey]) || [];
        if (Array.isArray(accountAgents) && accountAgents.length) {
          options = accountAgents;
        } else {
          options = this.agentItems;
        }
      }

      const normalized = Array.isArray(options) ? [...options] : [];
      const routedAgentId = (this.routePrefill && this.routePrefill.openclawAgentId) || "";
      if (routedAgentId && !normalized.some((item) => item.value === routedAgentId)) {
        normalized.unshift({
          value: routedAgentId,
          label: (this.routePrefill && this.routePrefill.openclawAgentName) || routedAgentId,
          ghost: true,
        });
      }
      return normalized;
    },
    hasAvailableBridge() {
      if (!this.debugForm.account) {
        return this.bridgeItems.some((item) => item.connected);
      }
      return this.bridgeOptions.some((item) => item.connected);
    },
    selectedAgentNeedsInventorySync() {
      if (!this.debugForm.agentId) {
        return false;
      }
      const matched = this.currentDebugAgentOptions.find((item) => item.value === this.debugForm.agentId);
      return Boolean(matched && matched.ghost);
    },
    canSendDirectChat() {
      return Boolean(
        this.channelId &&
        this.debugForm.account &&
        this.debugForm.agentId &&
        this.hasAvailableBridge &&
        this.debugForm.inputText &&
        this.debugForm.inputText.trim()
      );
    },
  },
  watch: {
    visible(val) {
      this.dialogVisible = val;
      if (val) {
        this.routePrefillApplied = false;
        this.loadDebugHistory();
        this.applyDebugDefaults();
      }
    },
    dialogVisible(val) {
      if (!val) {
        this.stopDebugPolling();
        this.stopBrowserAudio();
        this.$emit("update:visible", false);
      }
    },
    channelId(next, prev) {
      if (next === prev) {
        return;
      }
      this.resetDebugState();
      this.loadDebugHistory();
      if (this.dialogVisible) {
        this.applyDebugDefaults();
      }
    },
    inventory: {
      deep: true,
      handler() {
        if (this.dialogVisible) {
          this.applyDebugDefaults();
        }
      },
    },
    routePrefill: {
      deep: true,
      handler() {
        this.routePrefillApplied = false;
        if (this.dialogVisible) {
          this.applyDebugDefaults();
        }
      },
    },
    connections: {
      deep: true,
      handler() {
        if (this.dialogVisible) {
          this.syncDebugConnection();
        }
      },
    },
  },
  methods: {
    handleClose() {
      this.stopDebugPolling();
      this.stopBrowserAudio();
      this.dialogVisible = false;
    },
    updateDebugInputText(value) {
      this.debugForm.inputText = value;
    },
    resetDebugState() {
      this.stopDebugPolling();
      this.stopBrowserAudio();
      this.debugForm = createEmptyDebugForm();
      this.debugMessages = [];
      this.debugStatusEvents = [];
      this.debugHistorySessions = [];
      this.debugSending = false;
      this.debugClearing = false;
      this.debugPending = false;
      this.routePrefillApplied = false;
      this.debugTraceSeq = 0;
      this.latestBrowserAudioText = "";
    },
    getHistoryStorageKey() {
      return `${DEBUG_HISTORY_PREFIX}${this.channelId || "unknown"}`;
    },
    loadDebugHistory() {
      if (!this.channelId || typeof window === "undefined") {
        this.debugHistorySessions = [];
        return;
      }
      this.debugHistorySessions = safeParseHistory(window.localStorage.getItem(this.getHistoryStorageKey()))
        .map((item) => {
          const normalized = normalizeHistoryEntry(item);
          return {
            ...normalized,
            updatedAtText: formatHistoryTime(normalized.updatedAt),
          };
        })
        .filter((item) => item && item.sessionId);
    },
    persistDebugHistory() {
      if (!this.channelId || typeof window === "undefined") {
        return;
      }
      const serialized = this.debugHistorySessions.map((item) => normalizeHistoryEntry(item));
      window.localStorage.setItem(this.getHistoryStorageKey(), JSON.stringify(serialized));
    },
    syncCurrentHistoryEntry() {
      if (!this.channelId || !this.debugForm.debugSessionId) {
        return;
      }
      const sessionId = this.debugForm.debugSessionId;
      if (!this.debugMessages.length && !this.debugStatusEvents.length) {
        this.debugHistorySessions = this.debugHistorySessions.filter((item) => item.sessionId !== sessionId);
        this.persistDebugHistory();
        return;
      }
      const now = Date.now();
      const latestMessage = this.debugMessages[this.debugMessages.length - 1];
      const latestStatus = this.debugStatusEvents[this.debugStatusEvents.length - 1];
      const entry = {
        sessionId,
        account: this.debugForm.account,
        bridgeId: this.debugForm.bridgeId,
        connectionKey: this.debugForm.connectionKey,
        targetSessionId: this.debugForm.sessionId,
        targetDeviceId: this.debugForm.deviceId,
        agentId: this.debugForm.agentId,
        agentName: this.debugForm.agentName,
        pushToDevice: this.debugForm.pushToDevice,
        browserAudio: this.debugForm.browserAudio,
        traceNextSeq: this.debugTraceSeq,
        latestBrowserAudioText: this.latestBrowserAudioText,
        updatedAt: now,
        messages: this.debugMessages.slice(-MAX_DEBUG_HISTORY_MESSAGES),
        statusEvents: this.debugStatusEvents.slice(-MAX_DEBUG_STATUS_EVENTS),
        preview: (latestMessage && latestMessage.text) || (latestStatus && latestStatus.text) || "",
      };
      this.debugHistorySessions = [
        {
          ...entry,
          updatedAtText: formatHistoryTime(now),
        },
        ...this.debugHistorySessions.filter((item) => item.sessionId !== sessionId),
      ].slice(0, MAX_DEBUG_HISTORY_SESSIONS);
      this.persistDebugHistory();
    },
    restoreLatestHistory() {
      if (this.routePrefill && this.routePrefill.entry === "debug") {
        return;
      }
      if (!this.debugHistorySessions.length || this.debugMessages.length) {
        return;
      }
      this.restoreDebugHistory(this.debugHistorySessions[0], false);
    },
    applyDebugDefaults() {
      if (!this.channelId || !this.runtimeAccounts.length) {
        this.debugForm.account = "";
        this.debugForm.bridgeId = "";
        this.debugForm.agentId = "";
        this.debugForm.agentName = "";
        return;
      }

      if (!this.routePrefillApplied) {
        const routedAccount = this.runtimeAccounts.find((item) => item.value === this.routePrefill.runtimeAccount);
        if (routedAccount) {
          this.debugForm.account = routedAccount.value;
        }
      }

      if (!this.runtimeAccounts.some((item) => item.value === this.debugForm.account)) {
        this.debugForm.account = this.runtimeAccounts[0].value;
      }

      this.syncDebugBridge();
      this.syncDebugConnection();

      if (!this.routePrefillApplied) {
        const matchedAgent = this.currentDebugAgentOptions.find((item) => item.value === this.routePrefill.openclawAgentId);
        if (matchedAgent) {
          this.debugForm.agentId = matchedAgent.value;
          this.debugForm.agentName = matchedAgent.label;
        }
      }

      this.syncDebugAgent();
      if (!this.routePrefillApplied) {
        this.routePrefillApplied = true;
      }
      this.restoreLatestHistory();
    },
    syncDebugBridge() {
      if (!this.bridgeOptions.length) {
        this.debugForm.bridgeId = "";
        return;
      }
      const current = this.bridgeOptions.find((item) => item.bridgeId === this.debugForm.bridgeId);
      const preferred = this.bridgeOptions.find((item) => item.connected) || this.bridgeOptions[0];
      if (current && (current.connected || !preferred.connected)) {
        return;
      }
      this.debugForm.bridgeId = preferred.bridgeId;
    },
    syncDebugConnection() {
      if (!this.connectionItems.length) {
        this.debugForm.connectionKey = "";
        this.debugForm.sessionId = "";
        this.debugForm.deviceId = "";
        return;
      }
      const current = this.connectionItems.find((item) => item.value === this.debugForm.connectionKey)
        || this.connectionItems.find((item) =>
          item.sessionId === this.debugForm.sessionId && item.deviceId === this.debugForm.deviceId
        );
      const preferred = this.connectionItems.find((item) => item.isLatest) || this.connectionItems[0];
      const next = current || preferred;
      this.debugForm.connectionKey = next ? next.value : "";
      this.debugForm.sessionId = next ? (next.sessionId || "") : "";
      this.debugForm.deviceId = next ? (next.deviceId || "") : "";
    },
    syncDebugAgent() {
      const options = this.currentDebugAgentOptions;
      if (!options.some((item) => item.value === this.debugForm.agentId)) {
        const firstAgent = options[0];
        this.debugForm.agentId = firstAgent ? firstAgent.value : "";
        this.debugForm.agentName = firstAgent ? firstAgent.label : "";
        return;
      }
      this.debugForm.agentName = this.findOptionLabel(options, this.debugForm.agentId, this.debugForm.agentName || this.debugForm.agentId);
    },
    handleDebugAccountChange(value) {
      this.debugForm.account = value;
      this.syncDebugBridge();
      this.syncDebugConnection();
      this.syncDebugAgent();
    },
    handleConnectionChange(value) {
      const matched = this.connectionItems.find((item) => item.value === value);
      this.debugForm.connectionKey = matched ? matched.value : "";
      this.debugForm.sessionId = matched ? (matched.sessionId || "") : "";
      this.debugForm.deviceId = matched ? (matched.deviceId || "") : "";
    },
    handleDebugAgentChange(value) {
      this.debugForm.agentId = value;
      this.debugForm.agentName = this.findOptionLabel(this.currentDebugAgentOptions, value, this.debugForm.agentName || value);
    },
    handleDebugBridgeChange(value) {
      this.debugForm.bridgeId = value || "";
      this.syncDebugAgent();
    },
    handlePushToDeviceChange(value) {
      this.debugForm.pushToDevice = Boolean(value);
    },
    handleBrowserAudioChange(value) {
      this.debugForm.browserAudio = Boolean(value);
      if (!this.debugForm.browserAudio) {
        this.latestBrowserAudioText = "";
        this.stopBrowserAudio();
      }
    },
    findOptionLabel(list, value, fallback = "") {
      const matched = (Array.isArray(list) ? list : []).find((item) => item.value === value);
      return matched ? matched.label : fallback;
    },
    createDebugSession() {
      this.stopDebugPolling();
      this.latestBrowserAudioText = "";
      this.debugForm.debugSessionId = createDebugSessionId();
      this.debugMessages = [];
      this.debugStatusEvents = [];
      this.debugPending = false;
      this.debugTraceSeq = 0;
      this.syncCurrentHistoryEntry();
      this.$message.success("已创建新的 OpenClaw 调试会话");
    },
    rotateDebugSession(preserveTranscript = false) {
      this.stopDebugPolling();
      this.latestBrowserAudioText = "";
      this.debugForm.debugSessionId = createDebugSessionId();
      if (!preserveTranscript) {
        this.debugMessages = [];
      }
      this.debugStatusEvents = [];
      this.debugPending = false;
      this.debugTraceSeq = 0;
      this.syncCurrentHistoryEntry();
    },
    appendDebugMessage(role, text, extra = {}) {
      const customId = extra.id;
      if (customId && this.debugMessages.some((item) => item.id === customId)) {
        return;
      }
      this.debugMessages.push({
        id: customId || `${role}-${Date.now()}-${Math.random().toString(16).slice(2, 8)}`,
        role,
        text,
        meta: extra.meta || "",
      });
      this.syncCurrentHistoryEntry();
    },
    appendDebugStatus(text, extra = {}) {
      const customId = extra.id;
      if (customId && this.debugStatusEvents.some((item) => item.id === customId)) {
        return;
      }
      this.debugStatusEvents.push({
        id: customId || `status-${Date.now()}-${Math.random().toString(16).slice(2, 8)}`,
        text,
        meta: extra.meta || "",
        tone: extra.tone || "info",
        eventType: extra.eventType || "system",
      });
      this.debugStatusEvents = this.debugStatusEvents.slice(-MAX_DEBUG_STATUS_EVENTS);
      this.syncCurrentHistoryEntry();
    },
    restoreDebugHistory(item, showMessage = true) {
      if (!item || !item.sessionId) {
        return;
      }
      this.debugForm.account = item.account || this.debugForm.account;
      if (!this.runtimeAccounts.some((option) => option.value === this.debugForm.account)) {
        this.debugForm.account = this.runtimeAccounts.length ? this.runtimeAccounts[0].value : "";
      }
      this.debugForm.bridgeId = item.bridgeId || "";
      this.debugForm.connectionKey = item.connectionKey || "";
      this.debugForm.sessionId = item.targetSessionId || "";
      this.debugForm.deviceId = item.targetDeviceId || "";
      this.debugForm.agentId = item.agentId || "";
      this.debugForm.agentName = item.agentName || item.agentId || "";
      this.debugForm.pushToDevice = Boolean(item.pushToDevice);
      this.debugForm.browserAudio = item.browserAudio !== false;
      this.syncDebugBridge();
      this.syncDebugConnection();
      this.syncDebugAgent();
      this.debugForm.debugSessionId = item.sessionId;
      this.debugMessages = Array.isArray(item.messages) ? item.messages : [];
      this.debugStatusEvents = Array.isArray(item.statusEvents) ? item.statusEvents : [];
      this.debugTraceSeq = Number.isInteger(item.traceNextSeq) ? item.traceNextSeq : 0;
      this.latestBrowserAudioText = item.latestBrowserAudioText || "";
      if (showMessage) {
        this.$message.success("已恢复本地调试历史");
      }
    },
    deleteDebugHistory(sessionId) {
      if (!sessionId) {
        return;
      }
      const isCurrentSession = sessionId === this.debugForm.debugSessionId;
      this.debugHistorySessions = this.debugHistorySessions.filter((item) => item.sessionId !== sessionId);
      this.persistDebugHistory();
      if (isCurrentSession) {
        this.stopDebugPolling();
        this.stopBrowserAudio();
        this.latestBrowserAudioText = "";
        this.debugMessages = [];
        this.debugStatusEvents = [];
        this.debugTraceSeq = 0;
        this.debugForm.debugSessionId = createDebugSessionId();
      }
      this.$message.success("历史会话已删除");
    },
    clearDebugSession() {
      if (!this.channelId || !this.debugForm.account || !this.debugForm.debugSessionId) {
        this.$message.warning("当前没有可清理的调试会话");
        return;
      }
      this.$confirm("清空当前调试会话后，将开始新的会话。是否继续？", "提示", {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning",
      }).then(() => {
        this.debugClearing = true;
        Api.openclaw.clearSession(this.channelId, {
          account: this.debugForm.account,
          bridgeId: this.debugForm.bridgeId,
          sessionId: this.debugForm.debugSessionId,
          allowLatest: false,
        }, ({ data }) => {
          this.debugClearing = false;
          if (data.code === 0) {
            this.rotateDebugSession(true);
            this.$message.success("OpenClaw 调试会话已清空");
            return;
          }
          const message = data.msg || "清空 OpenClaw 调试会话失败";
          this.appendDebugStatus(message, {
            tone: "danger",
            eventType: "clear_session_failed",
          });
          this.$message.error(message);
        }, ({ data }) => {
          this.debugClearing = false;
          const message = (data && data.msg) || "清空 OpenClaw 调试会话失败";
          this.appendDebugStatus(message, {
            tone: "danger",
            eventType: "clear_session_failed",
          });
          this.$message.error(message);
        });
      }).catch(() => {});
    },
    sendDirectChat() {
      if (!this.canSendDirectChat) {
        if (!this.hasAvailableBridge) {
          this.$message.warning("当前 runtime 没有在线的 OpenClaw bridge，暂时不能调试");
          return;
        }
        this.$message.warning("请先选择 OpenClaw Agent 并填写测试消息");
        return;
      }
      const text = this.debugForm.inputText.trim();
      const payload = {
          account: this.debugForm.account,
          bridgeId: this.debugForm.bridgeId,
          agentId: this.debugForm.agentId,
          agentName: this.debugForm.agentName,
          debugSessionId: this.debugForm.debugSessionId,
          sessionId: this.debugForm.sessionId,
          deviceId: this.debugForm.deviceId,
          speaker: this.debugForm.speaker,
          pushToDevice: this.debugForm.pushToDevice,
          browserAudio: this.debugForm.browserAudio,
          text,
        };

      this.appendDebugMessage("user", text, {
        meta: `${this.currentRuntimeLabel} / ${this.debugForm.agentName || this.debugForm.agentId}`,
      });
      this.debugSending = true;
      this.debugForm.inputText = "";

      Api.openclaw.directChat(this.channelId, payload, ({ data }) => {
        this.debugSending = false;
        if (data.code === 0) {
          const response = data.data || {};
          if (response.debugSessionId) {
            this.debugForm.debugSessionId = response.debugSessionId;
          }
          this.debugPending = Boolean(response.accepted);
          this.startDebugPolling(true);
          if (!response.accepted && response.replyText) {
            this.appendDebugMessage("assistant", response.replyText, {
              meta: [response.account, response.agentName || response.agentId].filter(Boolean).join(" / "),
            });
          }
          return;
        }
        this.appendDebugStatus(data.msg || "OpenClaw 在线调试失败", {
          tone: "danger",
          eventType: "send_failed",
        });
        this.$message.error(data.msg || "OpenClaw 在线调试失败");
      }, ({ data }) => {
        this.debugSending = false;
        const message = (data && data.msg) || "OpenClaw 在线调试失败";
        this.appendDebugStatus(message, {
          tone: "danger",
          eventType: "send_failed",
        });
        this.$message.error(message);
      });
    },
    startDebugPolling(immediate = false) {
      this.stopDebugPolling();
      this.debugPending = true;
      if (immediate) {
        this.fetchDebugSessionTrace();
        return;
      }
      this.debugPollingTimer = window.setTimeout(() => {
        this.fetchDebugSessionTrace();
      }, 0);
    },
    scheduleDebugPolling() {
      this.stopDebugPolling();
      this.debugPending = true;
      this.debugPollingTimer = window.setTimeout(() => {
        this.fetchDebugSessionTrace();
      }, 1000);
    },
    stopDebugPolling() {
      if (this.debugPollingTimer) {
        window.clearTimeout(this.debugPollingTimer);
        this.debugPollingTimer = null;
      }
      this.debugPending = false;
    },
    fetchDebugSessionTrace() {
      if (!this.channelId || !this.debugForm.debugSessionId) {
        this.stopDebugPolling();
        return;
      }
      Api.openclaw.getDebugSession(this.channelId, this.debugForm.debugSessionId, {
        account: this.debugForm.account,
        bridgeId: this.debugForm.bridgeId,
        sinceSeq: this.debugTraceSeq,
      }, ({ data }) => {
        if (data.code !== 0) {
          this.appendDebugStatus(data.msg || "获取调试时间线失败", {
            tone: "danger",
            eventType: "trace_failed",
          });
          this.stopDebugPolling();
          return;
        }
        const snapshot = data.data || {};
        const events = Array.isArray(snapshot.events) ? snapshot.events : [];
        events.forEach((event) => this.consumeTraceEvent(event));
        const maxEventSeq = events.reduce((maxSeq, event) => {
          const seq = Number(event && event.seq);
          return Number.isFinite(seq) && seq > maxSeq ? seq : maxSeq;
        }, this.debugTraceSeq);
        this.debugTraceSeq = maxEventSeq;
        if (snapshot.latestReplyText) {
          this.syncReplyFromSnapshot(snapshot);
        }
        if (snapshot.browserAudio && snapshot.browserAudio.ready && snapshot.browserAudio.text) {
          this.latestBrowserAudioText = snapshot.browserAudio.text;
        }
        if (snapshot.pending) {
          this.debugPending = true;
          this.scheduleDebugPolling();
        } else {
          this.stopDebugPolling();
        }
      }, ({ data }) => {
        this.appendDebugStatus((data && data.msg) || "获取调试时间线失败", {
          tone: "danger",
          eventType: "trace_failed",
        });
        this.stopDebugPolling();
      });
    },
    syncReplyFromSnapshot(snapshot = {}) {
      const replyText = typeof snapshot.latestReplyText === "string" ? snapshot.latestReplyText.trim() : "";
      if (!replyText) {
        return;
      }
      const messageId = `snapshot-reply-${this.debugForm.debugSessionId}-${snapshot.updatedAt || replyText}`;
      this.appendDebugMessage("assistant", replyText, {
        id: messageId,
        meta: [snapshot.agentName || snapshot.agentId, snapshot.status].filter(Boolean).join(" / "),
      });
    },
    formatTraceStatus(event) {
      const agentLabel = event.agentName || event.agentId || "";
      const meta = [agentLabel, event.status].filter(Boolean).join(" / ");
      const fallbackText = event.title
        ? `${event.title}${event.message ? `：${event.message}` : ""}`
        : (event.message || event.type);

      if (event.type === "accepted") {
        return {
          text: "调试请求已提交，等待 OpenClaw 处理",
          meta,
          tone: "info",
        };
      }
      if (event.type === "agent_bound") {
        return {
          text: `已绑定 Agent：${agentLabel || "未知 Agent"}`,
          meta,
          tone: "primary",
        };
      }
      if (event.type === "subagent_spawned") {
        return {
          text: `子 Agent 已启动：${agentLabel || event.message || "后台任务"}`,
          meta,
          tone: "warning",
        };
      }
      if (event.type === "subagent_completed") {
        return {
          text: `子 Agent 已完成：${agentLabel || event.message || "后台任务"}`,
          meta,
          tone: "success",
        };
      }
      if (event.type === "browser_audio_ready") {
        return {
          text: "浏览器语音已就绪",
          meta,
          tone: "success",
        };
      }
      if (event.type === "device_push_started") {
        return {
          text: "正在推送结果到设备",
          meta,
          tone: "info",
        };
      }
      if (event.type === "device_push_succeeded") {
        return {
          text: event.message || "结果已推送到设备",
          meta,
          tone: "success",
        };
      }
      if (event.type === "device_push_failed" || event.type === "failed") {
        return {
          text: fallbackText,
          meta,
          tone: "danger",
        };
      }

      return {
        text: fallbackText,
        meta,
        tone: "info",
      };
    },
    consumeTraceEvent(event) {
      if (!event || !event.type) {
        return;
      }
      const eventId = `trace-${event.seq || Date.now()}-${event.type}`;
      if (event.type === "reply_ready") {
        this.appendDebugMessage("assistant", event.message || "OpenClaw 已生成最终回复", {
          id: eventId,
          meta: [event.agentName || event.agentId, event.status].filter(Boolean).join(" / "),
        });
        return;
      }
      if (event.type === "browser_audio_ready") {
        this.latestBrowserAudioText = (event.payload && event.payload.text) || this.latestBrowserAudioText;
      }
      const statusEvent = this.formatTraceStatus(event);
      if (!STATUS_EVENT_TYPES.has(event.type) && !statusEvent.text) {
        return;
      }
      this.appendDebugStatus(statusEvent.text, {
        id: eventId,
        meta: statusEvent.meta,
        tone: statusEvent.tone,
        eventType: event.type,
      });
    },
    playDebugMessageAudio(text) {
      const playbackText = typeof text === "string" ? text.trim() : "";
      if (!playbackText) {
        this.$message.warning("当前消息没有可播放的内容");
        return;
      }
      if (typeof window === "undefined" || !window.speechSynthesis || !window.SpeechSynthesisUtterance) {
        this.$message.warning("当前浏览器不支持语音播放");
        return;
      }
      this.stopBrowserAudio();
      const utterance = new window.SpeechSynthesisUtterance(playbackText);
      utterance.lang = "zh-CN";
      window.speechSynthesis.speak(utterance);
    },
    stopBrowserAudio() {
      if (typeof window !== "undefined" && window.speechSynthesis) {
        window.speechSynthesis.cancel();
      }
    },
  },
};
</script>

<style scoped>
::v-deep .openclaw-debug-dialog {
  width: min(1240px, calc(100vw - 32px)) !important;
  max-width: calc(100vw - 32px);
  max-height: calc(100vh - 24px);
  margin: 12px auto !important;
  display: flex;
  flex-direction: column;
  border-radius: 30px;
  overflow: hidden;
}

::v-deep .openclaw-debug-dialog .el-dialog__header {
  padding: 24px 28px 0;
}

::v-deep .openclaw-debug-dialog .el-dialog__title {
  color: #1b2740;
  font-weight: 700;
}

::v-deep .openclaw-debug-dialog .el-dialog__body {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  padding: 18px 22px 22px;
  background: #ffffff;
}

.debug-shell {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 10px;
  height: min(78vh, 820px);
  min-height: 0;
  overflow: hidden;
}

@media (max-width: 1180px) {
  .debug-shell {
    grid-template-columns: 1fr;
    height: min(78vh, 880px);
  }
}

@media (max-width: 760px) {
  ::v-deep .openclaw-debug-dialog {
    width: calc(100vw - 16px) !important;
    max-width: calc(100vw - 16px);
    max-height: calc(100vh - 12px);
    margin: 6px auto !important;
  }

  ::v-deep .openclaw-debug-dialog .el-dialog__header {
    padding: 18px 18px 0;
  }

  ::v-deep .openclaw-debug-dialog .el-dialog__body {
    padding: 14px;
  }

  .debug-shell {
    gap: 12px;
    height: min(82vh, 920px);
  }
}
</style>

<template>
  <el-dialog
    :visible.sync="dialogVisible"
    :width="dialogWidth"
    :top="dialogTop"
    :custom-class="dialogClass"
    :before-close="handleClose"
  >
    <template slot="title">
      <div class="debug-dialog-titlebar">
        <span class="debug-dialog-title">{{ dialogTitle }}</span>
        <el-button
          size="mini"
          plain
          class="debug-dialog-fullscreen-toggle"
          :class="{ 'is-active': isFullscreen }"
          :title="isFullscreen ? '退出全屏' : '进入全屏'"
          :aria-label="isFullscreen ? '退出全屏' : '进入全屏'"
          @click.stop="toggleFullscreen"
        >
          <i class="el-icon-full-screen debug-dialog-fullscreen-glyph" aria-hidden="true"></i>
        </el-button>
      </div>
    </template>

    <div class="debug-shell">
      <OpenClawDebugChatPaneModern
        :channel-name="channelName"
        :runtime-label="currentRuntimeLabel"
        :bridge-label="currentBridgeLabel"
        :device-label="currentDeviceLabel"
        :queue-summary="queueSummary"
        :has-active-connection="hasActiveConnection"
        :debug-ready="debugReady"
        :debug-disabled-reason="debugDisabledReason"
        :show-runtime-selector="showRuntimeSelector"
        :runtime-accounts="runtimeAccounts"
        :account="debugForm.account"
        :agent-label="debugForm.agentName || debugForm.agentId || '未选择'"
        :current-debug-agent-options="currentDebugAgentOptions"
        :agent-id="debugForm.agentId"
        :selected-agent-needs-inventory-sync="selectedAgentNeedsInventorySync"
        :push-to-device="debugForm.pushToDevice"
        :browser-audio="debugForm.browserAudio"
        :delivery-summary="deliverySummary"
        :debug-session-id="debugForm.debugSessionId"
        :disable-clear-session="!debugForm.account || !debugForm.debugSessionId"
        :debug-clearing="debugClearing"
        :tasks="debugTasks"
        :selected-task-id="selectedTaskId"
        :selected-task-trace-events="selectedTaskTraceEvents"
        :playback-jobs="playbackJobs"
        :debug-history-sessions="debugHistorySessions"
        :input-text="debugForm.inputText"
        :debug-sending="debugSending"
        :can-send-direct-chat="canSendDirectChat"
        :debug-pending="debugPending"
        @create-session="createDebugSession"
        @clear-session="clearDebugSession"
        @restore-history="restoreDebugHistory"
        @delete-history="deleteDebugHistory"
        @select-task="handleTaskSelection"
        @update:account="handleDebugAccountChange"
        @update:agent-id="handleDebugAgentChange"
        @update:input-text="updateDebugInputText"
        @update:push-to-device="handlePushToDeviceChange"
        @update:browser-audio="handleBrowserAudioChange"
        @open-settings="openSettingsDialog"
        @send="sendDirectChat"
        @play-message="playDebugMessageAudio"
      />
    </div>

    <el-dialog
      title="调试设置"
      :visible.sync="settingsDialogVisible"
      width="620px"
      append-to-body
      custom-class="openclaw-debug-settings-dialog"
    >
      <div class="debug-settings-form">
        <div class="settings-row">
          <span class="settings-label">详细稿投递</span>
          <el-switch
            v-model="debugForm.deliveryBinding.enabled"
            :disabled="!debugReady"
            @change="handleDeliveryEnabledChange"
          />
        </div>

        <template v-if="debugForm.deliveryBinding.enabled">
          <div class="settings-row settings-field">
            <span class="settings-label">渠道</span>
            <el-select
              v-model="debugForm.deliveryBinding.deliveryChannel"
              filterable
              allow-create
              default-first-option
              placeholder="选择详细稿投递渠道"
              @change="handleDeliveryChannelChange"
            >
              <el-option
                v-for="item in deliveryChannelOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </div>

          <div class="settings-row settings-field">
            <span class="settings-label">目标</span>
            <el-select
              v-model="debugForm.deliveryBinding.target"
              filterable
              allow-create
              default-first-option
              placeholder="选择或填写 IM 目标"
              @change="handleDeliveryTargetChange"
            >
              <el-option
                v-for="item in deliveryTargetOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </div>

          <div v-if="showDeliveryAccountField" class="settings-row settings-field">
            <span class="settings-label">账号</span>
            <el-select
              v-model="debugForm.deliveryBinding.accountId"
              filterable
              allow-create
              default-first-option
              clearable
              placeholder="可选：选择 IM 账号"
              @change="handleDeliveryAccountChange"
            >
              <el-option
                v-for="item in deliveryAccountOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
            <div class="settings-hint">只有同一渠道下存在多个可用账号时才需要区分。</div>
          </div>
        </template>
      </div>
    </el-dialog>
  </el-dialog>
</template>

<script>
import Api from "@/apis/api";
import OpenClawDebugChatPaneModern from "@/components/openclaw/OpenClawDebugChatPaneModern.vue";
import {
  buildConnectionKey,
  createDebugSessionId,
  createEmptyDeliveryBinding,
  createEmptyDebugForm,
  DEBUG_HISTORY_PREFIX,
  formatHistoryTime,
  MAX_DEBUG_HISTORY_MESSAGES,
  MAX_DEBUG_PLAYBACK_JOBS,
  MAX_DEBUG_HISTORY_SESSIONS,
  MAX_DEBUG_STATUS_EVENTS,
  MAX_DEBUG_TASKS,
  MAX_DEBUG_TRACE_EVENTS,
  normalizeHistoryEntry,
  safeParseHistory,
  sanitizeDebugMessages,
  sanitizeDebugPlaybackJobs,
  sanitizeDebugStatuses,
  sanitizeDebugTasks,
  sanitizeDebugTraceEvents,
  STATUS_EVENT_TYPES,
} from "@/components/openclaw/debug-dialog-utils";

const TASK_ACTIVE_STATUSES = new Set(["submitted", "accepted", "running"]);
const PLAYBACK_ACTIVE_STATUSES = new Set(["queued", "speaking"]);

const createLocalTask = (payload = {}) => ({
  taskId: payload.taskId || "",
  text: payload.text || "",
  agentId: payload.agentId || "",
  agentName: payload.agentName || payload.agentId || "",
  account: payload.account || "",
  bridgeId: payload.bridgeId || "",
  submittedAt: payload.submittedAt || Date.now(),
  acceptedAt: 0,
  completedAt: 0,
  failedAt: 0,
  status: "submitted",
  replyReady: false,
  replyText: "",
  browserAudioReady: false,
  browserAudioText: "",
  pushToDevice: Boolean(payload.pushToDevice),
  playbackJobIds: [],
});

const createTraceEventRecord = (payload = {}) => ({
  id: payload.id || `trace-${payload.seq || Date.now()}-${payload.type || "system"}`,
  seq: Number.isFinite(payload.seq) ? payload.seq : 0,
  type: payload.type || "system",
  taskId: payload.taskId || "",
  title: payload.title || "",
  message: payload.message || "",
  status: payload.status || "",
  tone: payload.tone || "info",
  agentId: payload.agentId || "",
  agentName: payload.agentName || "",
  createdAt: payload.createdAt || Date.now(),
  payload: payload.payload && typeof payload.payload === "object" ? payload.payload : {},
});

const createPlaybackJob = (payload = {}) => ({
  playbackJobId: payload.playbackJobId || `playback-${Date.now()}`,
  taskId: payload.taskId || "",
  text: payload.text || "",
  status: payload.status || "queued",
  source: payload.source || "main-agent",
  queuePosition: Number.isFinite(payload.queuePosition) ? payload.queuePosition : 0,
  createdAt: payload.createdAt || Date.now(),
  startedAt: payload.startedAt || 0,
  finishedAt: payload.finishedAt || 0,
  interruptReason: payload.interruptReason || "",
  agentId: payload.agentId || "",
  agentName: payload.agentName || "",
});

export default {
  name: "OpenClawDebugDialog",
  components: {
    OpenClawDebugChatPaneModern,
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
      debugTraceEvents: [],
      debugTasks: [],
      playbackJobs: [],
      debugHistorySessions: [],
      debugTurnSeed: 0,
      activeDebugTurnId: "",
      selectedTaskId: "",
      debugSending: false,
      debugClearing: false,
      debugPending: false,
      routePrefillApplied: false,
      debugTraceSeq: 0,
      debugPollingTimer: null,
      latestBrowserAudioText: "",
      isFullscreen: false,
      settingsDialogVisible: false,
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
      return "OpenClaw 调试";
    },
    dialogWidth() {
      return this.isFullscreen ? "calc(100vw - 12px)" : "86%";
    },
    dialogTop() {
      return this.isFullscreen ? "0" : "4vh";
    },
    dialogClass() {
      return `openclaw-debug-dialog${this.isFullscreen ? " is-fullscreen" : ""}`;
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
    deliveryChannelOptions() {
      const source = Array.isArray(this.inventory.deliveryChannels) ? this.inventory.deliveryChannels : [];
      return this.appendCurrentBindingOption(
        source,
        this.debugForm.deliveryBinding.deliveryChannel,
        this.resolveDeliveryChannelLabel(this.debugForm.deliveryBinding.deliveryChannel)
      );
    },
    selectedDeliveryChannelMeta() {
      if (!this.debugForm.deliveryBinding.deliveryChannel) {
        return null;
      }
      return this.deliveryChannelOptions.find(
        (item) => item.value === this.debugForm.deliveryBinding.deliveryChannel
      ) || null;
    },
    deliveryAccountOptions() {
      const accounts = Array.isArray(this.selectedDeliveryChannelMeta?.accountOptions)
        ? this.selectedDeliveryChannelMeta.accountOptions
        : [];
      return this.appendCurrentBindingOption(
        accounts,
        this.debugForm.deliveryBinding.accountId,
        this.debugForm.deliveryBinding.accountLabel
      );
    },
    deliveryTargetOptions() {
      const targets = Array.isArray(this.selectedDeliveryChannelMeta?.targetOptions)
        ? this.selectedDeliveryChannelMeta.targetOptions
        : [];
      return this.appendCurrentBindingOption(
        targets,
        this.debugForm.deliveryBinding.target,
        this.debugForm.deliveryBinding.targetLabel
      );
    },
    showDeliveryAccountField() {
      return this.deliveryAccountOptions.length > 1 || Boolean(this.debugForm.deliveryBinding.accountId);
    },
    deliverySummary() {
      const binding = this.debugForm.deliveryBinding || {};
      if (!binding.enabled) {
        return "";
      }
      const channel = this.resolveDeliveryChannelLabel(binding.deliveryChannel) || "未选择渠道";
      const target = binding.targetLabel || binding.target || "未选择目标";
      const account = this.showDeliveryAccountField
        ? (binding.accountLabel || binding.accountId || "未选择账号")
        : "";
      return account
        ? `${channel} / ${account} / ${target}`
        : `${channel} / ${target}`;
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
    currentConnectionLabel() {
      const current = this.connectionItems.find((item) => item.value === this.debugForm.connectionKey);
      const fallback = current || this.connectionItems[0];
      return fallback ? fallback.label : "";
    },
    connectedBridgeCount() {
      const source = this.debugForm.account ? this.bridgeOptions : this.bridgeItems;
      return source.filter((item) => item && item.connected).length;
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
    hasActiveConnection() {
      return this.connectionItems.length > 0;
    },
    connectionCount() {
      return this.connectionItems.length;
    },
    debugReady() {
      return this.hasAvailableBridge;
    },
    debugDisabledReason() {
      if (!this.hasAvailableBridge) {
        return "当前 runtime 没有在线 Bridge，暂时不能调试。";
      }
      return "";
    },
    selectedAgentNeedsInventorySync() {
      if (!this.debugForm.agentId) {
        return false;
      }
      const matched = this.currentDebugAgentOptions.find((item) => item.value === this.debugForm.agentId);
      return Boolean(matched && matched.ghost);
    },
    canSendDirectChat() {
      const deliveryBinding = this.debugForm.deliveryBinding || {};
      return Boolean(
        this.channelId &&
        this.debugForm.account &&
        this.debugForm.agentId &&
        this.debugReady &&
        this.debugForm.inputText &&
        this.debugForm.inputText.trim() &&
        (
          !deliveryBinding.enabled ||
          (deliveryBinding.deliveryChannel && deliveryBinding.target)
        )
      );
    },
    selectedTask() {
      return this.debugTasks.find((item) => item.taskId === this.selectedTaskId) || null;
    },
    selectedTaskTraceEvents() {
      if (this.selectedTaskId) {
        return this.debugTraceEvents.filter((item) => item.taskId === this.selectedTaskId);
      }
      return this.debugTraceEvents;
    },
    playbackActiveJob() {
      return this.playbackJobs.find((item) => item.status === "speaking") || null;
    },
    playbackQueuedJobs() {
      return this.playbackJobs
        .filter((item) => item.status === "queued")
        .map((item, index) => ({
          ...item,
          queuePosition: index + 1,
        }));
    },
    playbackCompletedJobs() {
      return this.playbackJobs
        .filter((item) => item.status === "completed")
        .slice(-6)
        .reverse();
    },
    playbackInterruptedJobs() {
      return this.playbackJobs
        .filter((item) => item.status === "interrupted")
        .slice(-4)
        .reverse();
    },
    queueSummary() {
      return {
        speakingCount: this.playbackActiveJob ? 1 : 0,
        queuedCount: this.playbackQueuedJobs.length,
        interruptedCount: this.playbackInterruptedJobs.length,
      };
    },
    browserPreview() {
      const selectedTask = this.selectedTask;
      if (selectedTask && selectedTask.browserAudioReady && selectedTask.browserAudioText) {
        return {
          ready: true,
          text: selectedTask.browserAudioText,
        };
      }
      return {
        ready: Boolean(this.latestBrowserAudioText),
        text: this.latestBrowserAudioText,
      };
    },
    currentBridgeLabel() {
      const matched = this.bridgeOptions.find((item) => item.bridgeId === this.debugForm.bridgeId)
        || this.bridgeItems.find((item) => item.bridgeId === this.debugForm.bridgeId);
      return matched ? (matched.name || matched.bridgeId) : (this.debugForm.bridgeId || "自动 Bridge");
    },
    currentDeviceLabel() {
      const current = this.connectionItems.find((item) => item.value === this.debugForm.connectionKey)
        || this.connectionItems[0];
      if (!current) {
        return "未命中设备";
      }
      return current.deviceId || current.sessionId || "未命中设备";
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
        this.isFullscreen = false;
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
      this.isFullscreen = false;
      this.dialogVisible = false;
    },
    toggleFullscreen() {
      this.isFullscreen = !this.isFullscreen;
    },
    updateDebugInputText(value) {
      this.debugForm.inputText = value;
    },
    handleTaskSelection(taskId) {
      this.selectedTaskId = taskId || "";
      this.syncCurrentHistoryEntry();
    },
    findTaskIndex(taskId) {
      return this.debugTasks.findIndex((item) => item.taskId === taskId);
    },
    resolveActiveTaskId() {
      const activeTask = this.debugTasks.find((item) => TASK_ACTIVE_STATUSES.has(item.status));
      if (activeTask) {
        return activeTask.taskId;
      }
      if (this.selectedTaskId && this.findTaskIndex(this.selectedTaskId) >= 0) {
        return this.selectedTaskId;
      }
      const latestTask = this.debugTasks[this.debugTasks.length - 1];
      return latestTask ? latestTask.taskId : "";
    },
    ensureTask(taskId, payload = {}) {
      const normalizedTaskId = String(taskId || "").trim();
      if (!normalizedTaskId) {
        return "";
      }
      const index = this.findTaskIndex(normalizedTaskId);
      if (index >= 0) {
        const current = this.debugTasks[index];
        this.$set(this.debugTasks, index, {
          ...current,
          ...payload,
          taskId: normalizedTaskId,
          playbackJobIds: Array.isArray(payload.playbackJobIds)
            ? payload.playbackJobIds
            : current.playbackJobIds,
        });
      } else {
        this.debugTasks.push(createLocalTask({
          taskId: normalizedTaskId,
          text: payload.text || "未命名任务",
          agentId: payload.agentId || this.debugForm.agentId,
          agentName: payload.agentName || this.debugForm.agentName,
          account: payload.account || this.debugForm.account,
          bridgeId: payload.bridgeId || this.debugForm.bridgeId,
          submittedAt: payload.submittedAt || Date.now(),
          pushToDevice: payload.pushToDevice !== undefined ? payload.pushToDevice : this.debugForm.pushToDevice,
        }));
      }
      if (!this.selectedTaskId) {
        this.selectedTaskId = normalizedTaskId;
      }
      this.refreshDebugPending();
      this.syncCurrentHistoryEntry();
      return normalizedTaskId;
    },
    patchTask(taskId, patch = {}) {
      const index = this.findTaskIndex(taskId);
      if (index < 0) {
        return;
      }
      const current = this.debugTasks[index];
      const next = {
        ...current,
        ...patch,
      };
      this.$set(this.debugTasks, index, next);
      this.refreshDebugPending();
      this.syncCurrentHistoryEntry();
    },
    isTaskInFlight(task = {}) {
      const status = typeof task.status === "string" ? task.status.trim() : "";
      return ["submitted", "accepted", "running"].includes(status);
    },
    refreshDebugPending() {
      this.debugPending = this.debugTasks.some((item) => this.isTaskInFlight(item));
    },
    resolveTaskIdForEvent(event = {}) {
      const topLevelTaskId = typeof event.taskId === "string" ? event.taskId.trim() : "";
      if (topLevelTaskId && this.findTaskIndex(topLevelTaskId) >= 0) {
        return topLevelTaskId;
      }
      const payloadTaskId = event?.payload && typeof event.payload.taskId === "string"
        ? event.payload.taskId
        : "";
      if (payloadTaskId && this.findTaskIndex(payloadTaskId) >= 0) {
        return payloadTaskId;
      }
      return this.resolveActiveTaskId();
    },
    appendTraceEventRecord(event = {}, taskId = "", tone = "") {
      const record = createTraceEventRecord({
        id: `trace-${event.seq || Date.now()}-${event.type || "system"}`,
        seq: event.seq || 0,
        type: event.type || "system",
        taskId,
        title: event.title || "",
        message: event.message || "",
        status: event.status || "",
        tone: tone || "info",
        agentId: event.agentId || "",
        agentName: event.agentName || "",
        createdAt: event.timestamp || Date.now(),
        payload: event.payload || {},
      });
      if (this.debugTraceEvents.some((item) => item.id === record.id)) {
        return;
      }
      this.debugTraceEvents.push(record);
      this.debugTraceEvents = this.debugTraceEvents.slice(-MAX_DEBUG_TRACE_EVENTS);
      this.syncCurrentHistoryEntry();
    },
    ensurePlaybackJob(taskId, text, extra = {}) {
      if (!taskId || !text) {
        return null;
      }
      const taskIndex = this.findTaskIndex(taskId);
      if (taskIndex < 0) {
        return null;
      }
      const task = this.debugTasks[taskIndex];
      const existingId = (task.playbackJobIds || []).find((playbackJobId) => {
        const matched = this.playbackJobs.find((item) => item.playbackJobId === playbackJobId);
        return matched && matched.text === text;
      });
      if (existingId) {
        return this.playbackJobs.find((item) => item.playbackJobId === existingId) || null;
      }
      const playbackJob = createPlaybackJob({
        playbackJobId: `playback-${taskId}-${Date.now()}`,
        taskId,
        text,
        status: "queued",
        createdAt: Date.now(),
        agentId: extra.agentId || task.agentId,
        agentName: extra.agentName || task.agentName,
      });
      this.playbackJobs.push(playbackJob);
      this.playbackJobs = this.playbackJobs.slice(-MAX_DEBUG_PLAYBACK_JOBS);
      this.patchTask(taskId, {
        playbackJobIds: [...task.playbackJobIds, playbackJob.playbackJobId],
      });
      return playbackJob;
    },
    findPlaybackJobIndex(taskId = "") {
      return this.playbackJobs.findIndex((item) =>
        item.taskId === taskId && PLAYBACK_ACTIVE_STATUSES.has(item.status)
      );
    },
    updatePlaybackState(taskId, status, extra = {}) {
      let index = this.findPlaybackJobIndex(taskId);
      if (index < 0) {
        index = this.playbackJobs.findIndex((item) => PLAYBACK_ACTIVE_STATUSES.has(item.status));
      }
      if (index < 0) {
        return;
      }
      const current = this.playbackJobs[index];
      const now = Date.now();
      this.$set(this.playbackJobs, index, {
        ...current,
        ...extra,
        status,
        startedAt: status === "speaking" ? (current.startedAt || now) : current.startedAt,
        finishedAt: ["completed", "failed", "interrupted"].includes(status) ? now : current.finishedAt,
      });
      this.syncCurrentHistoryEntry();
    },
    createSubmittedTask(text) {
      const taskId = `task-${Date.now()}-${this.debugTurnSeed}`;
      this.ensureTask(taskId, {
        text,
        agentId: this.debugForm.agentId,
        agentName: this.debugForm.agentName,
        account: this.debugForm.account,
        bridgeId: this.debugForm.bridgeId,
        submittedAt: Date.now(),
        pushToDevice: this.debugForm.pushToDevice,
      });
      this.selectedTaskId = taskId;
      return taskId;
    },
    resetDebugState() {
      this.stopDebugPolling();
      this.stopBrowserAudio();
      this.debugForm = createEmptyDebugForm();
      this.debugMessages = [];
      this.debugStatusEvents = [];
      this.debugTraceEvents = [];
      this.debugTasks = [];
      this.playbackJobs = [];
      this.debugHistorySessions = [];
      this.debugTurnSeed = 0;
      this.activeDebugTurnId = "";
      this.selectedTaskId = "";
      this.debugSending = false;
      this.debugClearing = false;
      this.debugPending = false;
      this.routePrefillApplied = false;
      this.debugTraceSeq = 0;
      this.latestBrowserAudioText = "";
      this.isFullscreen = false;
      this.settingsDialogVisible = false;
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
      if (!this.debugMessages.length && !this.debugStatusEvents.length && !this.debugTasks.length) {
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
        deliveryBinding: {
          ...createEmptyDeliveryBinding(),
          ...(this.debugForm.deliveryBinding || {}),
        },
        traceNextSeq: this.debugTraceSeq,
        latestBrowserAudioText: this.latestBrowserAudioText,
        updatedAt: now,
        messages: this.debugMessages.slice(-MAX_DEBUG_HISTORY_MESSAGES),
        statusEvents: this.debugStatusEvents.slice(-MAX_DEBUG_STATUS_EVENTS),
        selectedTaskId: this.selectedTaskId,
        tasks: this.debugTasks.slice(-MAX_DEBUG_TASKS),
        traceEvents: this.debugTraceEvents.slice(-MAX_DEBUG_TRACE_EVENTS),
        playbackJobs: this.playbackJobs.slice(-MAX_DEBUG_PLAYBACK_JOBS),
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
      this.syncDeliveryBindingLabels();
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
        this.debugForm.pushToDevice = false;
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
    handleDebugAgentChange(value) {
      this.debugForm.agentId = value;
      this.debugForm.agentName = this.findOptionLabel(this.currentDebugAgentOptions, value, this.debugForm.agentName || value);
    },
    appendCurrentBindingOption(options, currentValue, currentLabel) {
      const list = Array.isArray(options) ? options.slice() : [];
      if (!currentValue || list.some((item) => item.value === currentValue)) {
        return list;
      }
      list.unshift({
        value: currentValue,
        label: currentLabel ? `${currentLabel}（当前绑定）` : `${currentValue}（当前绑定）`,
      });
      return list;
    },
    resolveDeliveryChannelMeta(channelId) {
      return (
        (Array.isArray(this.inventory.deliveryChannels) ? this.inventory.deliveryChannels : [])
          .find((item) => item.value === channelId) || null
      );
    },
    resolveDeliveryChannelLabel(channelId) {
      const matched = this.resolveDeliveryChannelMeta(channelId);
      return matched ? matched.label : channelId;
    },
    handleDeliveryEnabledChange(value) {
      this.debugForm.deliveryBinding.enabled = Boolean(value);
      if (!this.debugForm.deliveryBinding.enabled) {
        this.debugForm.deliveryBinding = {
          ...createEmptyDeliveryBinding(),
        };
        return;
      }
      if (
        !this.debugForm.deliveryBinding.accountId &&
        this.deliveryAccountOptions.length === 1
      ) {
        this.handleDeliveryAccountChange(this.deliveryAccountOptions[0].value);
      }
      if (
        !this.debugForm.deliveryBinding.target &&
        this.deliveryTargetOptions.length === 1
      ) {
        this.handleDeliveryTargetChange(this.deliveryTargetOptions[0].value);
      }
    },
    handleDeliveryChannelChange(value) {
      const previousChannel = this.debugForm.deliveryBinding.deliveryChannel;
      this.debugForm.deliveryBinding.deliveryChannel = value;
      if (previousChannel !== value) {
        this.debugForm.deliveryBinding.accountId = "";
        this.debugForm.deliveryBinding.accountLabel = "";
        this.debugForm.deliveryBinding.target = "";
        this.debugForm.deliveryBinding.targetLabel = "";
        this.debugForm.deliveryBinding.threadId = "";
      }
      this.syncDeliveryBindingLabels();
      if (
        !this.debugForm.deliveryBinding.accountId &&
        this.deliveryAccountOptions.length === 1
      ) {
        this.handleDeliveryAccountChange(this.deliveryAccountOptions[0].value);
      }
      if (
        !this.debugForm.deliveryBinding.target &&
        this.deliveryTargetOptions.length === 1
      ) {
        this.handleDeliveryTargetChange(this.deliveryTargetOptions[0].value);
      }
    },
    handleDeliveryAccountChange(value) {
      this.debugForm.deliveryBinding.accountId = value;
      this.debugForm.deliveryBinding.accountLabel = this.findOptionLabel(
        this.deliveryAccountOptions,
        value,
        this.debugForm.deliveryBinding.accountLabel || value
      );
    },
    handleDeliveryTargetChange(value) {
      this.debugForm.deliveryBinding.target = value;
      this.debugForm.deliveryBinding.targetLabel = this.findOptionLabel(
        this.deliveryTargetOptions,
        value,
        this.debugForm.deliveryBinding.targetLabel || value
      );
    },
    syncDeliveryBindingLabels() {
      const deliveryBinding = this.debugForm.deliveryBinding || {};
      if (deliveryBinding.accountId) {
        deliveryBinding.accountLabel = this.findOptionLabel(
          this.deliveryAccountOptions,
          deliveryBinding.accountId,
          deliveryBinding.accountLabel || deliveryBinding.accountId
        );
      }
      if (deliveryBinding.target) {
        deliveryBinding.targetLabel = this.findOptionLabel(
          this.deliveryTargetOptions,
          deliveryBinding.target,
          deliveryBinding.targetLabel || deliveryBinding.target
        );
      }
    },
    handlePushToDeviceChange(value) {
      this.debugForm.pushToDevice = this.hasActiveConnection && Boolean(value);
    },
    handleBrowserAudioChange(value) {
      this.debugForm.browserAudio = Boolean(value);
      if (!this.debugForm.browserAudio) {
        this.latestBrowserAudioText = "";
        this.stopBrowserAudio();
      }
    },
    openSettingsDialog() {
      this.settingsDialogVisible = true;
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
      this.debugTraceEvents = [];
      this.debugTasks = [];
      this.playbackJobs = [];
      this.activeDebugTurnId = "";
      this.selectedTaskId = "";
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
      this.debugTraceEvents = [];
      this.debugTasks = [];
      this.playbackJobs = [];
      this.activeDebugTurnId = "";
      this.selectedTaskId = "";
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
        turnId: extra.turnId || "",
      });
      this.syncCurrentHistoryEntry();
    },
    upsertAssistantMessage(text, extra = {}) {
      const turnId = extra.turnId || this.activeDebugTurnId || this.getLatestTurnId();
      const existingIndex = this.debugMessages.findIndex((item) =>
        item &&
        item.role === "assistant" &&
        (
          (turnId && item.turnId === turnId) ||
          (extra.id && item.id === extra.id)
        )
      );
      if (existingIndex >= 0) {
        const current = this.debugMessages[existingIndex];
        this.$set(this.debugMessages, existingIndex, {
          ...current,
          text,
          meta: extra.meta || current.meta || "",
          turnId: turnId || current.turnId || "",
        });
        this.syncCurrentHistoryEntry();
        return;
      }
      this.appendDebugMessage("assistant", text, {
        ...extra,
        turnId,
      });
    },
    getLatestTurnId() {
      for (let index = this.debugMessages.length - 1; index >= 0; index -= 1) {
        const item = this.debugMessages[index];
        if (item && item.role === "user" && item.turnId) {
          return item.turnId;
        }
      }
      return "";
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
      this.debugForm.deliveryBinding = {
        ...createEmptyDeliveryBinding(),
        ...((item && item.deliveryBinding) || {}),
      };
      this.syncDebugBridge();
      this.syncDebugConnection();
      this.syncDebugAgent();
      this.syncDeliveryBindingLabels();
      this.debugForm.debugSessionId = item.sessionId;
      this.debugMessages = sanitizeDebugMessages(item.messages);
      this.debugStatusEvents = sanitizeDebugStatuses(item.statusEvents);
      this.debugTasks = sanitizeDebugTasks(item.tasks);
      this.debugTraceEvents = sanitizeDebugTraceEvents(item.traceEvents);
      this.playbackJobs = sanitizeDebugPlaybackJobs(item.playbackJobs);
      this.activeDebugTurnId = "";
      this.selectedTaskId = item.selectedTaskId || (this.debugTasks.length ? this.debugTasks[this.debugTasks.length - 1].taskId : "");
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
        this.debugTraceEvents = [];
        this.debugTasks = [];
        this.playbackJobs = [];
        this.activeDebugTurnId = "";
        this.selectedTaskId = "";
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
        if (!this.debugReady) {
          this.$message.warning(this.debugDisabledReason || "当前调试条件未满足");
          return;
        }
        this.$message.warning("请先选择 OpenClaw Agent 并填写测试消息");
        return;
      }
      const text = this.debugForm.inputText.trim();
      const deliveryBinding = this.debugForm.deliveryBinding || {};
      if (deliveryBinding.enabled) {
        if (!deliveryBinding.deliveryChannel) {
          this.$message.warning("启用详细稿投递后，必须选择投递渠道");
          return;
        }
        if (!deliveryBinding.target) {
          this.$message.warning("启用详细稿投递后，必须选择或填写 IM 目标");
          return;
        }
      }
      const turnId = `turn-${Date.now()}-${this.debugTurnSeed}`;
      const taskId = this.createSubmittedTask(text);
      const payload = {
          account: this.debugForm.account,
          bridgeId: this.debugForm.bridgeId,
          agentId: this.debugForm.agentId,
          agentName: this.debugForm.agentName,
          taskId,
          debugSessionId: this.debugForm.debugSessionId,
          sessionId: this.debugForm.sessionId,
          deviceId: this.debugForm.deviceId,
          speaker: this.debugForm.speaker,
          pushToDevice: this.debugForm.pushToDevice,
          browserAudio: this.debugForm.browserAudio,
          deliveryBinding: deliveryBinding.enabled ? {
            enabled: true,
            deliveryChannel: deliveryBinding.deliveryChannel,
            accountId: deliveryBinding.accountId,
            accountLabel: deliveryBinding.accountLabel,
            target: deliveryBinding.target,
            targetLabel: deliveryBinding.targetLabel,
            format: deliveryBinding.format || "text",
          } : {
            enabled: false,
          },
          text,
        };
      this.debugTurnSeed += 1;
      this.activeDebugTurnId = turnId;

      this.appendDebugMessage("user", text, {
        meta: `${this.currentRuntimeLabel} / ${this.debugForm.agentName || this.debugForm.agentId}`,
        turnId,
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
          if (response.accepted) {
            this.patchTask(taskId, {
              status: "accepted",
              acceptedAt: Date.now(),
            });
          }
          this.startDebugPolling(true);
          if (!response.accepted && response.replyText) {
            this.upsertAssistantMessage(response.replyText, {
              meta: [response.account, response.agentName || response.agentId].filter(Boolean).join(" / "),
              turnId,
            });
            this.patchTask(taskId, {
              status: "completed",
              acceptedAt: Date.now(),
              completedAt: Date.now(),
              replyReady: true,
              replyText: response.replyText,
            });
            if (this.debugForm.pushToDevice) {
              this.ensurePlaybackJob(taskId, response.replyText, {
                agentId: response.agentId,
                agentName: response.agentName,
              });
            }
          }
          return;
        }
        this.patchTask(taskId, {
          status: "failed",
          failedAt: Date.now(),
        });
        this.appendDebugStatus(data.msg || "OpenClaw 在线调试失败", {
          tone: "danger",
          eventType: "send_failed",
        });
        this.$message.error(data.msg || "OpenClaw 在线调试失败");
      }, ({ data }) => {
        this.debugSending = false;
        const message = (data && data.msg) || "OpenClaw 在线调试失败";
        this.patchTask(taskId, {
          status: "failed",
          failedAt: Date.now(),
        });
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
      this.refreshDebugPending();
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
        const hasReplyReadyEvent = events.some((event) => event && event.type === "reply_ready");
        if (snapshot.latestReplyText && !snapshot.pending && !hasReplyReadyEvent) {
          this.syncReplyFromSnapshot(snapshot, {
            turnId: this.activeDebugTurnId || this.getLatestTurnId(),
          });
        }
        if (snapshot.browserAudio && snapshot.browserAudio.ready && snapshot.browserAudio.text) {
          this.latestBrowserAudioText = snapshot.browserAudio.text;
        }
        this.refreshDebugPending();
        if (snapshot.pending && this.debugPending) {
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
    syncReplyFromSnapshot(snapshot = {}, extra = {}) {
      const replyText = typeof snapshot.latestReplyText === "string" ? snapshot.latestReplyText.trim() : "";
      if (!replyText) {
        return;
      }
      const taskId = this.resolveActiveTaskId();
      const snapshotEvent = {
        type: "reply_ready",
        agentId: snapshot.agentId,
        agentName: snapshot.agentName,
        status: snapshot.status,
      };
      this.upsertAssistantMessage(replyText, {
        meta: this.buildTraceMessageMeta(snapshotEvent, {
          statusLabel: this.resolveTraceMessageStage(snapshotEvent),
        }),
        turnId: extra.turnId || this.activeDebugTurnId || this.getLatestTurnId(),
      });
      this.appendTraceEventRecord({
        seq: this.debugTraceSeq + 1,
        type: "reply_ready",
        title: "最终回复已生成",
        message: replyText,
        status: snapshot.status,
        agentId: snapshot.agentId,
        agentName: snapshot.agentName,
        timestamp: Date.now(),
      }, taskId, "success");
      if (taskId) {
        this.patchTask(taskId, {
          status: "completed",
          completedAt: Date.now(),
          replyReady: true,
          replyText,
        });
        const task = this.debugTasks[this.findTaskIndex(taskId)];
        if (task && task.pushToDevice) {
          this.ensurePlaybackJob(taskId, replyText, {
            agentId: snapshot.agentId,
            agentName: snapshot.agentName,
          });
        }
      }
    },
    resolveTraceMessageText(event = {}) {
      if (event.type === "reply_ready") {
        return typeof event.message === "string" ? event.message.trim() : "";
      }
      if (event.type === "progress") {
        return typeof event.message === "string" ? event.message.trim() : "";
      }
      if (event.type === "subagent_completed") {
        return typeof event.message === "string" ? event.message.trim() : "";
      }
      if (event.type === "failed") {
        return typeof event.message === "string" ? event.message.trim() : "";
      }
      return "";
    },
    resolveTraceMessageStage(event = {}) {
      if (event.type === "reply_ready") {
        return "最终回复";
      }
      if (event.type === "progress") {
        return "处理中";
      }
      if (event.type === "subagent_completed") {
        return "任务完成";
      }
      if (event.type === "failed") {
        return "执行失败";
      }
      return typeof event.status === "string" ? event.status.trim() : "";
    },
    isPrimaryAgentEvent(event = {}) {
      if (event.type === "subagent_spawned" || event.type === "subagent_completed") {
        return false;
      }
      const selectedAgentId = String(this.debugForm.agentId || "").trim();
      const selectedAgentName = String(this.debugForm.agentName || "").trim();
      const eventAgentId = String(event.agentId || event.payload?.agentId || "").trim();
      const eventAgentName = String(event.agentName || event.payload?.agentName || "").trim();
      if (selectedAgentId && eventAgentId) {
        return selectedAgentId === eventAgentId;
      }
      if (selectedAgentName && eventAgentName) {
        return selectedAgentName === eventAgentName;
      }
      return true;
    },
    buildTraceMessageMeta(event = {}, extra = {}) {
      const sourceLabel = this.isPrimaryAgentEvent(event) ? "主 Agent" : "子 Agent";
      const agentLabel = String(
        event.agentName || event.agentId || event.payload?.agentName || event.payload?.agentId || ""
      ).trim();
      const stageLabel = extra.statusLabel || this.resolveTraceMessageStage(event);
      return [sourceLabel, agentLabel || "未命名 Agent", stageLabel].filter(Boolean).join(" · ");
    },
    shouldAppendTraceMessage(event = {}) {
      if (!event || !event.type) {
        return false;
      }
      if (!["progress", "subagent_completed", "failed"].includes(event.type)) {
        return false;
      }
      return Boolean(this.resolveTraceMessageText(event));
    },
    appendTraceMessage(event = {}, extra = {}) {
      const messageText = this.resolveTraceMessageText(event);
      if (!messageText) {
        return;
      }
      this.appendDebugMessage("assistant", messageText, {
        id: extra.id || `trace-message-${event.seq || Date.now()}-${event.type}`,
        meta: this.buildTraceMessageMeta(event, extra),
        turnId: extra.turnId || `trace-${event.seq || Date.now()}-${event.type}`,
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
      if (event.type === "progress") {
        return {
          text: event.message || "OpenClaw 正在处理",
          meta,
          tone: "info",
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
      if (event.type === "reply_ready") {
        return {
          text: "最终回复已生成",
          meta,
          tone: "success",
        };
      }
      if (event.type === "device_push_started") {
        return {
          text: "正在推送结果到 ESP32",
          meta,
          tone: "info",
        };
      }
      if (event.type === "device_push_enqueued") {
        return {
          text: event.message || "结果已进入桥接推送队列",
          meta,
          tone: "warning",
        };
      }
      if (event.type === "device_push_succeeded" || event.type === "device_push_completed") {
        return {
          text: event.message || "结果已推送到 ESP32",
          meta,
          tone: "success",
        };
      }
      if (event.type === "device_push_interrupted") {
        return {
          text: event.message || "设备播报已被打断",
          meta,
          tone: "warning",
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
      const taskId = this.resolveTaskIdForEvent(event);
      const eventId = `trace-${event.seq || Date.now()}-${event.type}`;
      const statusEvent = this.formatTraceStatus(event);
      this.appendTraceEventRecord(event, taskId, statusEvent.tone);

      if (event.type === "accepted" && taskId) {
        this.patchTask(taskId, {
          status: "accepted",
          acceptedAt: Date.now(),
        });
      }
      if (event.type === "agent_bound" && taskId) {
        this.patchTask(taskId, {
          agentId: event.agentId || this.debugForm.agentId,
          agentName: event.agentName || this.debugForm.agentName,
        });
      }
      if (["progress", "subagent_spawned", "subagent_completed"].includes(event.type) && taskId) {
        const taskIndex = this.findTaskIndex(taskId);
        const currentTask = taskIndex >= 0 ? this.debugTasks[taskIndex] : null;
        this.patchTask(taskId, {
          status: "running",
          acceptedAt: currentTask && currentTask.acceptedAt ? currentTask.acceptedAt : Date.now(),
        });
      }
      if (event.type === "reply_ready") {
        const replyText = event.message || "OpenClaw 已生成最终回复";
        this.upsertAssistantMessage(replyText, {
          meta: this.buildTraceMessageMeta(event, {
            statusLabel: this.resolveTraceMessageStage(event),
          }),
          turnId: this.activeDebugTurnId || this.getLatestTurnId(),
        });
        if (taskId) {
          this.patchTask(taskId, {
            status: "completed",
            completedAt: Date.now(),
            replyReady: true,
            replyText,
          });
          const task = this.debugTasks[this.findTaskIndex(taskId)];
          if (task && task.pushToDevice) {
            this.ensurePlaybackJob(taskId, replyText, {
              agentId: event.agentId,
              agentName: event.agentName,
            });
          }
        }
      }
      if (event.type === "browser_audio_ready") {
        const browserText = (event.payload && event.payload.text) || this.latestBrowserAudioText;
        this.latestBrowserAudioText = browserText;
        if (taskId) {
          this.patchTask(taskId, {
            browserAudioReady: Boolean(browserText),
            browserAudioText: browserText,
          });
        }
      }
      if (event.type === "device_push_started") {
        this.updatePlaybackState(taskId, "speaking");
      }
      if (event.type === "device_push_enqueued") {
        this.updatePlaybackState(taskId, "queued");
      }
      if (event.type === "device_push_succeeded" || event.type === "device_push_completed") {
        this.updatePlaybackState(taskId, "completed");
      }
      if (event.type === "device_push_failed") {
        this.updatePlaybackState(taskId, "failed");
      }
      if (event.type === "device_push_interrupted") {
        this.updatePlaybackState(taskId, "interrupted", {
          interruptReason: (event.payload && event.payload.reason) || "interrupt",
        });
      }
      if (event.type === "failed" && taskId) {
        this.patchTask(taskId, {
          status: "failed",
          failedAt: Date.now(),
        });
      }
      if (this.shouldAppendTraceMessage(event)) {
        this.appendTraceMessage(event, {
          id: `${eventId}-message`,
          statusLabel: this.resolveTraceMessageStage(event),
        });
      }
      if (!STATUS_EVENT_TYPES.has(event.type) && !["reply_ready", "device_push_completed", "device_push_interrupted"].includes(event.type)) {
        return;
      }
      if (!statusEvent.text) {
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

<style scoped lang="scss">
@import "@/components/openclaw/styles/openclaw-debug-dialog.scss";
</style>

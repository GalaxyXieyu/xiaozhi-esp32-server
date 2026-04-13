<template>
  <section class="modern-stage">
    <header class="runtime-bar">
      <div class="runtime-context">
        <span class="context-badge channel">{{ channelName }}</span>
        <span class="context-pill">Runtime {{ runtimeLabel || "自动" }}</span>
        <span class="context-pill">Bridge {{ bridgeLabel || "自动" }}</span>
        <span class="context-pill">Agent {{ agentLabel || "未选择" }}</span>
        <span class="context-pill subtle">Device {{ deviceLabel || "未命中设备" }}</span>
      </div>

      <div class="runtime-summary">
        <span class="summary-label">结果队列</span>
        <span class="summary-pill speaking">播报 {{ queueSummary.speakingCount || 0 }}</span>
        <span class="summary-pill queued">排队 {{ queueSummary.queuedCount || 0 }}</span>
        <span class="summary-pill interrupted">中断 {{ queueSummary.interruptedCount || 0 }}</span>
      </div>

      <div class="runtime-actions">
        <el-button class="oc-button oc-button-neutral" @click="$emit('create-session')">新会话</el-button>
        <el-button
          class="oc-button oc-button-warning"
          :disabled="disableClearSession"
          :loading="debugClearing"
          @click="$emit('clear-session')"
        >
          清空会话
        </el-button>
      </div>
    </header>

    <div class="workspace-grid">
      <aside class="lane session-lane">
        <div class="lane-header compact">
          <h3 class="lane-title compact">会话</h3>
          <span class="lane-count">{{ sessionItems.length }}</span>
        </div>

        <div v-if="sessionItems.length" class="session-list">
          <button
            v-for="session in sessionItems"
            :key="session.sessionId"
            type="button"
            class="session-card"
            :class="{ active: session.sessionId === debugSessionId, passive: session.isSyntheticCurrent }"
            @click="handleSessionClick(session)"
          >
            <div class="session-card-top">
              <div class="session-card-main">
                <span class="session-id">{{ historyLabel(session.sessionId) }}</span>
                <span v-if="session.sessionId === debugSessionId" class="session-state">当前</span>
              </div>
              <span class="session-time">{{ session.updatedAtText || "当前" }}</span>
            </div>
            <div class="session-preview">{{ session.preview || "暂无摘要" }}</div>
            <div class="session-meta">
              <span>{{ sessionTaskCount(session) }} 条任务</span>
              <span v-if="session.agentName || session.agentId">{{ session.agentName || session.agentId }}</span>
            </div>
          </button>
        </div>

        <div v-else class="lane-empty compact-empty">
          <div class="empty-mark">SE</div>
          <div class="empty-title">暂无会话</div>
        </div>
      </aside>

      <section class="lane chat-lane">
        <div class="chat-header compact">
          <h3 class="lane-title compact">当前会话</h3>
          <div class="chat-header-meta">
            <span class="chat-count">{{ conversationTasks.length }} 轮</span>
          </div>
        </div>

        <div v-if="conversationTasks.length" class="chat-stream">
          <div v-for="task in conversationTasks" :key="task.taskId" class="chat-turn">
            <div class="message-row user">
              <div class="message-meta user">
                <span>你</span>
                <span>{{ formatTime(task.submittedAt) }}</span>
              </div>
              <div class="message-bubble user">
                <div class="message-text">{{ task.text }}</div>
              </div>
            </div>

            <div class="message-row assistant" :class="{ selected: task.taskId === selectedTaskId }">
              <div class="message-meta assistant">
                <div class="assistant-meta-left">
                  <span class="assistant-name">{{ task.agentName || task.agentId || "AI" }}</span>
                  <span class="assistant-status" :class="`tone-${resolveAssistantState(task).tone}`">
                    {{ resolveAssistantState(task).label }}
                  </span>
                </div>
                <span>{{ formatTime(task.completedAt || task.acceptedAt || task.submittedAt) }}</span>
              </div>

              <div
                class="message-bubble assistant"
                role="button"
                tabindex="0"
                @click="openTaskDetail(task.taskId)"
                @keydown.enter.prevent="openTaskDetail(task.taskId)"
                @keydown.space.prevent="openTaskDetail(task.taskId)"
              >
                <div class="message-text-row">
                  <div class="message-text assistant">{{ resolveAssistantReply(task) }}</div>
                  <button
                    v-if="task.browserAudioReady && task.browserAudioText"
                    type="button"
                    class="message-audio-icon"
                    aria-label="播放当前回复"
                    @click.stop="$emit('play-message', task.browserAudioText)"
                  >
                    <i class="el-icon-video-play"></i>
                  </button>
                </div>
                <div class="message-note">{{ resolveAssistantState(task).note }}</div>
              </div>
            </div>
          </div>
        </div>

        <div v-else class="lane-empty chat-empty">
          <div class="empty-mark">CH</div>
          <div class="empty-title">暂无消息</div>
        </div>
      </section>
    </div>

    <div class="composer-shell">
      <div class="composer-controls">
        <div v-if="showRuntimeSelector" class="control-field">
          <span class="control-label">Runtime</span>
          <el-select
            v-model="selectedAccount"
            class="control-select"
            size="small"
            filterable
            :popper-append-to-body="false"
            :disabled="!debugReady"
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

        <div class="control-field agent">
          <span class="control-label">Agent</span>
          <el-select
            v-model="selectedAgentId"
            class="control-select"
            size="small"
            filterable
            :popper-append-to-body="false"
            :disabled="!debugReady || !currentDebugAgentOptions.length"
            placeholder="选择 OpenClaw Agent"
          >
            <el-option
              v-for="item in currentDebugAgentOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </div>

        <label class="control-switch">
          <span class="control-label">推送到设备</span>
          <el-switch v-model="localPushToDevice" :disabled="!debugReady || !hasActiveConnection" />
        </label>

        <label class="control-switch">
          <span class="control-label">浏览器语音</span>
          <el-switch v-model="localBrowserAudio" :disabled="!debugReady" />
        </label>

        <div class="control-action">
          <el-button
            class="oc-button oc-button-icon control-settings-button"
            title="调试设置"
            aria-label="调试设置"
            :disabled="!debugReady"
            @click="$emit('open-settings')"
          >
            <i class="el-icon-setting"></i>
          </el-button>
        </div>
      </div>

      <div v-if="!debugReady" class="composer-note danger">
        {{ debugDisabledReason }}
      </div>

      <div v-if="selectedAgentNeedsInventorySync" class="composer-note warning">
        当前 Agent 未出现在已发现列表中，建议先刷新状态。
      </div>

      <div v-if="deliverySummary" class="composer-inline-summary" :title="deliverySummary">
        <span class="composer-inline-label">详细稿投递</span>
        <span class="composer-inline-value">{{ deliverySummary }}</span>
      </div>

      <el-input
        v-model="localInputText"
        class="composer-input"
        type="textarea"
        :rows="3"
        resize="none"
        placeholder="输入调试消息，Ctrl + Enter 发送"
        @keyup.ctrl.enter.native="$emit('send')"
      />

      <div class="composer-footer">
        <el-button
          class="oc-button oc-button-primary send-button"
          :loading="debugSending"
          :disabled="!canSendDirectChat"
          @click="$emit('send')"
        >
          <i class="el-icon-position"></i>
          <span>发送</span>
        </el-button>
      </div>
    </div>

    <el-drawer
      :visible.sync="detailDrawerVisible"
      direction="rtl"
      size="44%"
      append-to-body
      custom-class="openclaw-debug-detail-drawer"
      :wrapperClosable="true"
      :withHeader="false"
    >
      <div class="detail-drawer" v-if="selectedTask">
        <div class="detail-drawer-header">
          <h3 class="detail-drawer-title">处理过程</h3>
          <button type="button" class="detail-drawer-close" @click="detailDrawerVisible = false">×</button>
        </div>

        <section class="detail-drawer-section">
          <div class="process-list">
            <article
              v-for="(step, index) in detailProcessSteps"
              :key="step.key"
              class="process-step"
              :class="`tone-${step.tone}`"
            >
              <div class="process-rail">
                <span class="process-dot" :class="`tone-${step.tone}`"></span>
                <span v-if="index < detailProcessSteps.length - 1" class="process-line"></span>
              </div>
              <div class="process-card">
                <div class="process-head">
                  <div class="process-title-row">
                    <span class="process-title">{{ step.title }}</span>
                    <span class="trace-time">{{ step.timeText }}</span>
                  </div>
                  <span class="assistant-status" :class="`tone-${step.tone}`">{{ step.stateLabel }}</span>
                </div>
                <div class="process-note">{{ step.note }}</div>
                <div v-if="step.meta" class="process-meta">{{ step.meta }}</div>
                <div v-if="step.action === 'play'" class="process-actions">
                  <el-button class="oc-button process-action-button" @click="$emit('play-message', selectedTask.browserAudioText)">
                    <span class="process-action-icon">
                      <i class="el-icon-video-play"></i>
                    </span>
                    <span>播放当前回复</span>
                  </el-button>
                </div>
              </div>
            </article>
          </div>
        </section>
      </div>
    </el-drawer>
  </section>
</template>

<script>
const TASK_SORT_FALLBACK = 0;

export default {
  name: "OpenClawDebugChatPaneModern",
  props: {
    channelName: {
      type: String,
      default: "",
    },
    runtimeLabel: {
      type: String,
      default: "",
    },
    bridgeLabel: {
      type: String,
      default: "",
    },
    deviceLabel: {
      type: String,
      default: "",
    },
    agentLabel: {
      type: String,
      default: "",
    },
    queueSummary: {
      type: Object,
      default: () => ({
        speakingCount: 0,
        queuedCount: 0,
        interruptedCount: 0,
      }),
    },
    debugReady: {
      type: Boolean,
      default: false,
    },
    debugDisabledReason: {
      type: String,
      default: "",
    },
    disableClearSession: {
      type: Boolean,
      default: false,
    },
    debugClearing: {
      type: Boolean,
      default: false,
    },
    tasks: {
      type: Array,
      default: () => [],
    },
    selectedTaskId: {
      type: String,
      default: "",
    },
    selectedTaskTraceEvents: {
      type: Array,
      default: () => [],
    },
    playbackJobs: {
      type: Array,
      default: () => [],
    },
    debugHistorySessions: {
      type: Array,
      default: () => [],
    },
    debugSessionId: {
      type: String,
      default: "",
    },
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
    pushToDevice: {
      type: Boolean,
      default: false,
    },
    browserAudio: {
      type: Boolean,
      default: false,
    },
    deliverySummary: {
      type: String,
      default: "",
    },
    inputText: {
      type: String,
      default: "",
    },
    debugSending: {
      type: Boolean,
      default: false,
    },
    canSendDirectChat: {
      type: Boolean,
      default: false,
    },
    debugPending: {
      type: Boolean,
      default: false,
    },
    hasActiveConnection: {
      type: Boolean,
      default: false,
    },
  },
  data() {
    return {
      detailDrawerVisible: false,
    };
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
    localInputText: {
      get() {
        return this.inputText;
      },
      set(value) {
        this.$emit("update:input-text", value);
      },
    },
    conversationTasks() {
      return [...this.tasks].sort((left, right) => {
        const leftTime = Number(left && left.submittedAt) || TASK_SORT_FALLBACK;
        const rightTime = Number(right && right.submittedAt) || TASK_SORT_FALLBACK;
        return leftTime - rightTime;
      });
    },
    sessionItems() {
      const sessions = Array.isArray(this.debugHistorySessions) ? this.debugHistorySessions.slice() : [];
      const hasCurrent = sessions.some((item) => item && item.sessionId === this.debugSessionId);
      if (!hasCurrent && this.debugSessionId) {
        const lastTask = this.conversationTasks[this.conversationTasks.length - 1] || null;
        sessions.unshift({
          sessionId: this.debugSessionId,
          preview: (lastTask && (lastTask.replyText || lastTask.text)) || "",
          updatedAtText: "当前",
          agentId: this.agentId,
          agentName: this.agentLabel,
          tasks: this.conversationTasks,
          isSyntheticCurrent: true,
        });
      }
      return sessions;
    },
    selectedTask() {
      return this.tasks.find((item) => item.taskId === this.selectedTaskId) || null;
    },
    selectedTaskState() {
      return this.resolveAssistantState(this.selectedTask);
    },
    sortedSelectedTaskTraceEvents() {
      return [...(Array.isArray(this.selectedTaskTraceEvents) ? this.selectedTaskTraceEvents : [])].sort((left, right) => {
        const leftTime = this.normalizeTimestamp(left && left.createdAt) || TASK_SORT_FALLBACK;
        const rightTime = this.normalizeTimestamp(right && right.createdAt) || TASK_SORT_FALLBACK;
        return leftTime - rightTime;
      });
    },
    selectedTaskPlaybackJobs() {
      return this.selectedTask ? this.taskPlaybackJobs(this.selectedTask.taskId) : [];
    },
    detailProcessSteps() {
      const task = this.selectedTask;
      if (!task || !task.taskId) {
        return [];
      }

      const acceptedEvent = this.findTraceEvent(["accepted"], "first");
      const replyReadyEvent = this.findTraceEvent(["reply_ready"], "last");
      const browserAudioEvent = this.findTraceEvent(["browser_audio_ready"], "last");
      const enqueuedEvent = this.findTraceEvent(["device_push_enqueued"], "last");
      const deviceStartEvent = this.findTraceEvent(["device_push_started"], "last");
      const deviceDoneEvent = this.findTraceEvent(["device_push_succeeded", "device_push_completed"], "last");
      const deviceFailedEvent = this.findTraceEvent(["device_push_failed", "device_push_interrupted"], "last");
      const failedEvent = this.findTraceEvent(["failed"], "last");
      const latestPlayback = this.selectedTaskPlaybackJobs[this.selectedTaskPlaybackJobs.length - 1] || null;

      return [
        {
          key: "submitted",
          title: "用户发送消息",
          stateLabel: "已发送",
          tone: "success",
          note: task.text || "消息已发送到当前调试会话。",
          timeText: this.formatTime(task.submittedAt),
        },
        {
          key: "accepted",
          title: "进入 OpenClaw",
          stateLabel: acceptedEvent || task.status === "accepted" || task.status === "running" || task.replyReady || task.status === "failed" ? "已受理" : "待受理",
          tone: acceptedEvent || task.status === "accepted" || task.status === "running" || task.replyReady || task.status === "failed" ? "success" : "muted",
          note: acceptedEvent || task.status === "accepted" || task.status === "running" || task.replyReady || task.status === "failed"
            ? `请求已进入 OpenClaw${task.agentName || task.agentId ? `，当前由 ${task.agentName || task.agentId} 处理。` : "。"}`
            : "消息尚未进入 OpenClaw 处理阶段。",
          timeText: this.formatTime((acceptedEvent && acceptedEvent.createdAt) || task.acceptedAt || task.submittedAt),
        },
        {
          key: "reply",
          title: "AI 生成回复",
          stateLabel: task.status === "failed" ? "生成失败" : task.replyReady ? "已生成" : task.status === "accepted" || task.status === "running" ? "生成中" : "待生成",
          tone: task.status === "failed" ? "danger" : task.replyReady ? "success" : task.status === "accepted" || task.status === "running" ? "warning" : "muted",
          note: task.status === "failed"
            ? (failedEvent && (failedEvent.message || failedEvent.title)) || "AI 没有完成这条回复。"
            : task.replyReady
              ? (replyReadyEvent && (replyReadyEvent.message || replyReadyEvent.title)) || "回复文本已经生成。"
              : this.selectedTaskState.note,
          timeText: this.formatTime((replyReadyEvent && replyReadyEvent.createdAt) || task.completedAt || task.acceptedAt),
        },
        {
          key: "browser-audio",
          title: "浏览器试听",
          stateLabel: task.browserAudioReady ? "可试听" : task.replyReady ? "未生成" : "待生成",
          tone: task.browserAudioReady ? "success" : task.replyReady ? "muted" : "warning",
          note: task.browserAudioReady
            ? (browserAudioEvent && (browserAudioEvent.message || browserAudioEvent.title)) || "可以在调试面板手动播放当前回复。"
            : task.replyReady
              ? "当前没有可用的浏览器试听版本。"
              : "等待 AI 先生成回复，再提供浏览器试听。",
          timeText: this.formatTime((browserAudioEvent && browserAudioEvent.createdAt) || task.completedAt || task.acceptedAt),
          action: task.browserAudioReady && task.browserAudioText ? "play" : "",
        },
        {
          key: "device-push",
          title: "设备播报",
          stateLabel: !task.pushToDevice
            ? "未推送"
            : latestPlayback
              ? this.playbackStatusLabel(latestPlayback.status)
              : enqueuedEvent
                ? "排队中"
                : task.replyReady
                  ? "待播报"
                  : "待推送",
          tone: !task.pushToDevice
            ? "muted"
            : latestPlayback && (latestPlayback.status === "completed" || latestPlayback.status === "speaking")
              ? "success"
              : latestPlayback && (latestPlayback.status === "queued")
                ? "warning"
                : latestPlayback && (latestPlayback.status === "failed" || latestPlayback.status === "interrupted")
                  ? "danger"
                  : enqueuedEvent || task.replyReady
                    ? "warning"
                    : "muted",
          note: !task.pushToDevice
            ? "当前消息没有推送到设备。"
            : latestPlayback && latestPlayback.status === "speaking"
              ? "设备正在播报这条回复。"
              : latestPlayback && latestPlayback.status === "completed"
                ? "设备已经完成播报。"
                : latestPlayback && latestPlayback.status === "queued"
                  ? "回复已进入设备语音队列，等待前序播报完成。"
                  : latestPlayback && latestPlayback.status === "interrupted"
                    ? latestPlayback.interruptReason || "设备主动打断了当前播报。"
                    : latestPlayback && latestPlayback.status === "failed"
                      ? latestPlayback.interruptReason || "设备播报失败。"
                      : enqueuedEvent
                        ? (enqueuedEvent.message || enqueuedEvent.title || "已进入设备语音队列。")
                        : task.replyReady
                          ? "回复已经生成，等待设备侧返回进一步状态。"
                          : "等待 AI 先生成回复。",
          timeText: this.formatTime(
            (latestPlayback && (latestPlayback.startedAt || latestPlayback.finishedAt || latestPlayback.createdAt)) ||
              (deviceDoneEvent && deviceDoneEvent.createdAt) ||
              (deviceStartEvent && deviceStartEvent.createdAt) ||
              (deviceFailedEvent && deviceFailedEvent.createdAt) ||
              (enqueuedEvent && enqueuedEvent.createdAt) ||
              task.completedAt
          ),
          meta: latestPlayback && latestPlayback.playbackJobId ? `playbackJob: ${latestPlayback.playbackJobId}` : "",
        },
      ];
    },
  },
  watch: {
    debugSessionId() {
      this.detailDrawerVisible = false;
    },
    selectedTaskId(next) {
      if (!next) {
        this.detailDrawerVisible = false;
      }
    },
  },
  methods: {
    normalizeTimestamp(timestamp) {
      if (!timestamp && timestamp !== 0) {
        return null;
      }
      if (typeof timestamp === "string" && /^\d+$/.test(timestamp)) {
        const numeric = Number(timestamp);
        return timestamp.length <= 10 ? numeric * 1000 : numeric;
      }
      if (typeof timestamp === "number" && timestamp > 0 && timestamp < 1e11) {
        return timestamp * 1000;
      }
      return timestamp;
    },
    formatTime(timestamp) {
      const normalized = this.normalizeTimestamp(timestamp);
      if (!normalized) {
        return "--:--";
      }
      const date = new Date(normalized);
      if (Number.isNaN(date.getTime())) {
        return "--:--";
      }
      return date.toLocaleTimeString([], {
        hour: "2-digit",
        minute: "2-digit",
      });
    },
    historyLabel(sessionId) {
      if (!sessionId) {
        return "未命名";
      }
      const normalized = String(sessionId);
      if (normalized.startsWith("web-debug-")) {
        return `#${normalized.slice(-6)}`;
      }
      return normalized.length > 10 ? `${normalized.slice(0, 10)}...` : normalized;
    },
    taskLabel(taskId) {
      if (!taskId) {
        return "任务";
      }
      return this.historyLabel(taskId);
    },
    sessionTaskCount(session = {}) {
      return Array.isArray(session.tasks) ? session.tasks.length : 0;
    },
    handleSessionClick(session) {
      if (!session || !session.sessionId || session.isSyntheticCurrent) {
        return;
      }
      this.$emit("restore-history", session);
    },
    taskPlaybackJobs(taskId = "") {
      return (Array.isArray(this.playbackJobs) ? this.playbackJobs : [])
        .filter((item) => item && item.taskId === taskId)
        .sort((left, right) => {
          const leftTime = Number(left.startedAt || left.finishedAt || left.createdAt) || TASK_SORT_FALLBACK;
          const rightTime = Number(right.startedAt || right.finishedAt || right.createdAt) || TASK_SORT_FALLBACK;
          return leftTime - rightTime;
        });
    },
    findTraceEvent(types = [], strategy = "first") {
      const normalizedTypes = Array.isArray(types) ? types : [types];
      const matched = this.sortedSelectedTaskTraceEvents.filter((item) => item && normalizedTypes.includes(item.type));
      if (!matched.length) {
        return null;
      }
      return strategy === "last" ? matched[matched.length - 1] : matched[0];
    },
    resolveAssistantReply(task = {}) {
      if (task.replyText) {
        return task.replyText;
      }
      if (task.status === "failed") {
        return "这条消息执行失败，请点开查看轨迹。";
      }
      return "正在等待 AI 生成回复...";
    },
    resolveAssistantState(task = null) {
      if (!task || !task.taskId) {
        return {
          label: "等待任务",
          tone: "muted",
          note: "先发送一条消息后，这里才会出现 AI 回复状态。",
        };
      }

      const playbackJobs = this.taskPlaybackJobs(task.taskId);
      const latestPlayback = playbackJobs[playbackJobs.length - 1] || null;

      if (task.status === "failed") {
        return {
          label: "执行失败",
          tone: "danger",
          note: "AI 没有完成这条回复，请查看明细轨迹。",
        };
      }

      if (!task.replyReady) {
        if (task.status === "accepted") {
          return {
            label: "已受理",
            tone: "warning",
            note: "请求已进入 OpenClaw，正在等待生成回复。",
          };
        }
        if (task.status === "running") {
          return {
            label: "执行中",
            tone: "warning",
            note: "AI 正在处理这条消息。",
          };
        }
        return {
          label: "等待回复",
          tone: "info",
          note: "消息已经发出，尚未收到 AI 输出。",
        };
      }

      if (!task.pushToDevice) {
        return task.browserAudioReady
          ? {
            label: "可试听",
            tone: "success",
            note: "不推送设备，可直接在浏览器试听这条回复。",
          }
          : {
            label: "未推送设备",
            tone: "muted",
            note: "这条回复只保留在文本调试链路。",
          };
      }

      if (latestPlayback && latestPlayback.status === "speaking") {
        return {
          label: "播报中",
          tone: "success",
          note: "设备正在播报这条回复。",
        };
      }
      if (latestPlayback && latestPlayback.status === "queued") {
        return {
          label: "等待播报",
          tone: "warning",
          note: "回复已生成，正在等待前序语音完成。",
        };
      }
      if (latestPlayback && latestPlayback.status === "interrupted") {
        return {
          label: "已打断",
          tone: "danger",
          note: latestPlayback.interruptReason || "设备主动语音打断了当前播报。",
        };
      }
      if (latestPlayback && latestPlayback.status === "completed") {
        return {
          label: "已播报",
          tone: "success",
          note: "设备已经完成这条回复的播报。",
        };
      }

      return {
        label: task.browserAudioReady ? "文本已就绪" : "已完成",
        tone: task.browserAudioReady ? "info" : "success",
        note: task.browserAudioReady
          ? "文本和浏览器试听已就绪，等待设备侧进一步状态。"
          : "回复已经生成。",
      };
    },
    openTaskDetail(taskId) {
      if (!taskId) {
        return;
      }
      this.$emit("select-task", taskId);
      this.detailDrawerVisible = true;
    },
    playbackStatusLabel(status = "") {
      const labels = {
        queued: "等待播报",
        speaking: "播报中",
        completed: "已播报",
        interrupted: "已打断",
        failed: "播报失败",
      };
      return labels[status] || (status || "状态未知");
    },
    traceTypeLabel(type = "") {
      const labels = {
        accepted: "已受理",
        agent_bound: "绑定 Agent",
        progress: "处理中",
        subagent_spawned: "启动子 Agent",
        subagent_completed: "子 Agent 完成",
        reply_ready: "回复生成",
        browser_audio_ready: "浏览器试听",
        device_push_enqueued: "等待播报",
        device_push_started: "设备播报",
        device_push_succeeded: "播报完成",
        device_push_completed: "播报完成",
        device_push_failed: "播报失败",
        device_push_interrupted: "播报打断",
        failed: "执行失败",
      };
      return labels[type] || type;
    },
    traceTone(type) {
      if (type === "reply_ready" || type === "device_push_succeeded" || type === "device_push_completed") {
        return "success";
      }
      if (type === "device_push_interrupted" || type === "failed" || type === "device_push_failed") {
        return "danger";
      }
      if (type === "accepted" || type === "progress" || type === "device_push_enqueued") {
        return "warning";
      }
      return "info";
    },
    shouldShowTraceAgent(item) {
      const agent = item && (item.agentName || item.agentId);
      const message = item && (item.message || item.title || item.type);
      return Boolean(agent && agent !== message);
    },
  },
};
</script>

<style scoped lang="scss">
@import "@/components/openclaw/styles/openclaw-debug-chat-pane-modern.scss";
</style>

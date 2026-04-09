<template>
  <section class="modern-stage">
    <header class="modern-header">
      <div class="modern-header-main">
        <p class="modern-eyebrow">OpenClaw Debug Console</p>
        <div class="modern-title-row">
          <h3 class="modern-title">{{ channelName }}</h3>
          <span class="modern-status" :class="{ offline: !debugReady }">
            {{ debugReady ? "调试就绪" : (hasAvailableBridge ? "等待连接" : "等待 Bridge") }}
          </span>
        </div>
        <p class="modern-summary">{{ currentRuntimeLabel }} · {{ agentLabel || "未选择 Agent" }}</p>
      </div>
      <div class="modern-header-actions">
        <el-button size="small" plain @click="$emit('create-session')">新会话</el-button>
        <el-button
          size="small"
          type="warning"
          plain
          :disabled="disableClearSession"
          :loading="debugClearing"
          @click="$emit('clear-session')"
        >
          清空会话
        </el-button>
      </div>
    </header>

    <div class="modern-layout">
      <aside class="sessions-panel">
        <div class="panel-title-row">
          <div>
            <p class="panel-kicker">Sessions</p>
            <h4 class="panel-title">会话历史</h4>
          </div>
          <span class="panel-count">{{ debugHistorySessions.length }}</span>
        </div>

        <div v-if="debugHistorySessions.length" class="sessions-list">
          <div
            v-for="item in debugHistorySessions"
            :key="item.sessionId"
            class="session-item"
            :class="{ active: item.sessionId === debugSessionId }"
            role="button"
            tabindex="0"
            @click="$emit('restore-history', item)"
            @keydown.enter.prevent="$emit('restore-history', item)"
          >
            <div class="session-item-main">
              <span class="session-id" :title="item.sessionId">{{ historyLabel(item.sessionId) }}</span>
              <span class="session-time">{{ item.updatedAtText }}</span>
            </div>
            <el-button
              class="session-delete"
              type="text"
              icon="el-icon-close"
              @click.stop="$emit('delete-history', item.sessionId)"
            />
          </div>
        </div>
        <div v-else class="sessions-empty">
          <div class="sessions-empty-icon">··</div>
          <p class="sessions-empty-title">还没有历史会话</p>
          <p class="sessions-empty-text">新建一条调试消息后，这里会自动保留最近会话。</p>
        </div>
      </aside>

      <div class="conversation-panel">
        <div class="conversation-shell">
          <div class="conversation-topbar">
            <div class="conversation-badges">
              <span class="topbar-chip primary">{{ agentLabel || "未选择 Agent" }}</span>
              <span class="topbar-chip subtle">{{ currentRuntimeLabel }}</span>
              <span v-if="connectionLabel" class="topbar-chip subtle">设备 {{ connectionLabel }}</span>
              <span v-else class="topbar-chip subtle muted">仅页面回显</span>
            </div>
            <el-button
              v-if="latestAssistantText"
              size="mini"
              type="text"
              class="topbar-action"
              @click="playLatestMessage"
            >
              播放最新回复
            </el-button>
          </div>

          <div v-if="activeStatusEvent" class="status-banner" :class="statusToneClass(activeStatusEvent.tone)">
            <span class="status-banner-pill">{{ statusToneLabel(activeStatusEvent.tone) }}</span>
            <div class="status-banner-body">
              <strong>{{ activeStatusEvent.text }}</strong>
              <span v-if="activeStatusEvent.meta">{{ activeStatusEvent.meta }}</span>
            </div>
          </div>

          <div ref="transcript" class="transcript-shell">
            <div v-if="visibleMessages.length" class="message-list">
              <article
                v-for="item in visibleMessages"
                :key="item.id"
                class="message-row"
                :class="`role-${item.role}`"
              >
                <div class="message-avatar">{{ messageAvatar(item.role) }}</div>
                <div class="message-stack">
                  <div class="message-meta-line">
                    <span class="message-role">{{ messageRoleLabel(item.role) }}</span>
                    <span v-if="item.meta" class="message-meta">{{ item.meta }}</span>
                  </div>
                  <div class="message-card">
                    <div class="message-text">{{ item.text }}</div>
                  </div>
                </div>
              </article>
            </div>
            <div v-else class="transcript-empty">
              <div class="transcript-empty-mark">OC</div>
              <h4 class="transcript-empty-title">开始一条新的在线调试</h4>
              <p class="transcript-empty-text">
                先选择 Runtime 和 Agent，再发送测试消息。新版界面保留原调试链路，只重做信息层级与布局。
              </p>
            </div>
          </div>
        </div>

        <div class="composer-shell">
          <div v-if="!debugReady" class="composer-note danger">
            {{ debugDisabledReason }}
          </div>
          <div v-else-if="connectionLabel" class="composer-note">
            已自动复用在线连接：{{ connectionLabel }}
          </div>
          <div v-else class="composer-note muted">
            当前没有在线设备连接，本次调试只返回到页面，不会推送到设备。
          </div>

          <div v-if="selectedAgentNeedsInventorySync" class="composer-note warning">
            当前 Agent 未出现在 inventory 中，建议先同步 OpenClaw inventory。
          </div>

          <el-input
            v-model="localInputText"
            class="composer-input"
            type="textarea"
            :rows="5"
            resize="none"
            placeholder="输入调试消息，Ctrl + Enter 发送"
            @keyup.ctrl.enter.native="$emit('send')"
          />

          <div class="composer-footer">
            <div class="composer-tip">Ctrl + Enter 发送，沿用原有接口与轮询逻辑</div>
            <el-button type="primary" :loading="debugSending" :disabled="!canSendDirectChat" @click="$emit('send')">
              发送调试消息
            </el-button>
          </div>
        </div>
      </div>

      <aside class="settings-panel">
        <section class="settings-card">
          <div class="panel-title-row slim">
            <div>
              <p class="panel-kicker">Routing</p>
              <h4 class="panel-title">调试设置</h4>
            </div>
          </div>

          <div v-if="showRuntimeSelector" class="setting-field">
            <label class="setting-label">Runtime / Account</label>
            <el-select
              v-model="selectedAccount"
              class="setting-select"
              filterable
              :disabled="!debugReady"
              placeholder="连接成功后选择 runtime/account"
            >
              <el-option
                v-for="item in runtimeAccounts"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </div>

          <div class="setting-field">
            <label class="setting-label">OpenClaw Agent</label>
            <el-select
              v-model="selectedAgentId"
              class="setting-select"
              filterable
              :disabled="!debugReady || !currentDebugAgentOptions.length"
              placeholder="连接成功后选择 OpenClaw Agent"
            >
              <el-option
                v-for="item in currentDebugAgentOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </div>
        </section>

        <section class="settings-card">
          <div class="panel-title-row slim">
            <div>
              <p class="panel-kicker">Output</p>
              <h4 class="panel-title">结果设置</h4>
            </div>
          </div>

          <label class="toggle-row">
            <div>
              <span class="toggle-title">推送到设备</span>
              <span class="toggle-desc">有在线设备时，把本次回复同步推送到设备端。</span>
            </div>
            <el-switch v-model="localPushToDevice" :disabled="!debugReady || !hasActiveConnection" />
          </label>

          <label class="toggle-row">
            <div>
              <span class="toggle-title">浏览器语音</span>
              <span class="toggle-desc">回复完成后允许在浏览器内直接试听语音。</span>
            </div>
            <el-switch v-model="localBrowserAudio" :disabled="!debugReady" />
          </label>
        </section>

        <section class="settings-card timeline-card">
          <div class="panel-title-row slim">
            <div>
              <p class="panel-kicker">Timeline</p>
              <h4 class="panel-title">调试时间线</h4>
            </div>
          </div>

          <div v-if="visibleStatusEvents.length" class="timeline-list">
            <div
              v-for="item in visibleStatusEvents"
              :key="item.id || `${item.eventType}-${item.text}`"
              class="timeline-item"
            >
              <span class="timeline-dot" :class="statusToneClass(item.tone)"></span>
              <div class="timeline-body">
                <div class="timeline-head">
                  <span class="timeline-label">{{ statusToneLabel(item.tone) }}</span>
                  <span v-if="item.eventType" class="timeline-type">{{ item.eventType }}</span>
                </div>
                <div class="timeline-text">{{ item.text }}</div>
                <div v-if="item.meta" class="timeline-meta">{{ item.meta }}</div>
              </div>
            </div>
          </div>
          <div v-else class="timeline-empty">调试状态会在发送后按时间顺序展示。</div>
        </section>
      </aside>
    </div>
  </section>
</template>

<script>
export default {
  name: "OpenClawDebugChatPaneModern",
  props: {
    channelName: {
      type: String,
      default: "",
    },
    hasAvailableBridge: {
      type: Boolean,
      default: false,
    },
    hasActiveConnection: {
      type: Boolean,
      default: false,
    },
    debugReady: {
      type: Boolean,
      default: false,
    },
    debugDisabledReason: {
      type: String,
      default: "",
    },
    connectionLabel: {
      type: String,
      default: "",
    },
    currentRuntimeLabel: {
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
    agentLabel: {
      type: String,
      default: "未选择",
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
    debugSessionId: {
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
    debugMessages: {
      type: Array,
      default: () => [],
    },
    debugStatusEvents: {
      type: Array,
      default: () => [],
    },
    debugHistorySessions: {
      type: Array,
      default: () => [],
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
    visibleStatusEvents() {
      return Array.isArray(this.debugStatusEvents) ? this.debugStatusEvents.slice(-8) : [];
    },
    activeStatusEvent() {
      const latest = this.visibleStatusEvents.length
        ? this.visibleStatusEvents[this.visibleStatusEvents.length - 1]
        : null;
      if (this.debugPending) {
        return latest || {
          id: "pending-generic",
          text: "调试请求已提交，等待 OpenClaw 处理",
          meta: "",
          tone: "warning",
        };
      }
      if (latest && latest.tone === "danger") {
        return latest;
      }
      return null;
    },
    visibleMessages() {
      const source = Array.isArray(this.debugMessages)
        ? this.debugMessages.filter((item) => item && item.role !== "system")
        : [];
      return source.reduce((list, item) => {
        const last = list[list.length - 1];
        const sameAssistantTurn = Boolean(
          last &&
          item.role === "assistant" &&
          last.role === "assistant" &&
          String(last.text || "").trim() === String(item.text || "").trim() &&
          (
            (last.turnId && item.turnId && last.turnId === item.turnId) ||
            (!last.turnId && !item.turnId)
          )
        );
        if (sameAssistantTurn) {
          list[list.length - 1] = item;
          return list;
        }
        list.push(item);
        return list;
      }, []);
    },
    latestAssistantText() {
      for (let index = this.visibleMessages.length - 1; index >= 0; index -= 1) {
        const item = this.visibleMessages[index];
        if (item.role === "assistant" && item.text) {
          return item.text;
        }
      }
      return "";
    },
  },
  watch: {
    visibleMessages() {
      this.scrollToBottom();
    },
  },
  mounted() {
    this.scrollToBottom();
  },
  methods: {
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
    messageRoleLabel(role) {
      if (role === "user") {
        return "后台输入";
      }
      if (role === "assistant") {
        return "OpenClaw 返回";
      }
      return "系统信息";
    },
    messageAvatar(role) {
      if (role === "user") {
        return "ME";
      }
      if (role === "assistant") {
        return "AI";
      }
      return "SYS";
    },
    statusToneClass(tone) {
      return `tone-${tone || "info"}`;
    },
    statusToneLabel(tone) {
      if (tone === "success") {
        return "完成";
      }
      if (tone === "warning") {
        return "运行中";
      }
      if (tone === "danger") {
        return "异常";
      }
      if (tone === "primary") {
        return "已路由";
      }
      return "状态";
    },
    playLatestMessage() {
      if (this.latestAssistantText) {
        this.$emit("play-message", this.latestAssistantText);
      }
    },
    scrollToBottom() {
      this.$nextTick(() => {
        const container = this.$refs.transcript;
        if (container) {
          container.scrollTop = container.scrollHeight;
        }
      });
    },
  },
};
</script>

<style scoped lang="scss">
@import "@/components/openclaw/styles/openclaw-debug-chat-pane-modern.scss";
</style>

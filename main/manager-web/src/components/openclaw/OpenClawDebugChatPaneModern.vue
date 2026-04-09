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
        <p class="modern-summary">
          {{ currentRuntimeLabel }} · {{ agentLabel || "未选择 Agent" }}
        </p>
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
              <span class="topbar-chip">{{ agentLabel || "未选择 Agent" }}</span>
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

          <div class="deep-chat-shell">
            <deep-chat ref="deepChatEl" class="deep-chat-host"></deep-chat>
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
            <div class="composer-tip">Ctrl + Enter 发送，保留现有接口与轮询逻辑</div>
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
const CHAT_STYLE = {
  background: "linear-gradient(180deg, #f8fbff 0%, #f3f6fb 100%)",
  borderRadius: "24px",
  border: "1px solid #d9e2f2",
  boxShadow: "0 18px 45px rgba(28, 58, 109, 0.08)",
  height: "100%",
};

const MESSAGE_STYLES = {
  default: {
    user: {
      bubble: {
        backgroundColor: "#2f6fed",
        color: "#ffffff",
        borderRadius: "18px 18px 6px 18px",
        boxShadow: "0 10px 24px rgba(47, 111, 237, 0.22)",
      },
      innerContainer: {
        marginLeft: "56px",
      },
    },
    ai: {
      bubble: {
        backgroundColor: "#ffffff",
        color: "#18304a",
        border: "1px solid #d6e0f0",
        borderRadius: "18px 18px 18px 6px",
        boxShadow: "0 10px 30px rgba(31, 54, 88, 0.08)",
      },
      innerContainer: {
        marginRight: "56px",
      },
    },
  },
};

const CHAT_NAMES = {
  user: {
    text: "后台输入",
    position: "end",
    style: {
      color: "#5b6b82",
      fontSize: "12px",
      fontWeight: "600",
    },
  },
  ai: {
    text: "OpenClaw",
    position: "start",
    style: {
      color: "#5b6b82",
      fontSize: "12px",
      fontWeight: "600",
    },
  },
};

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
    deepChatHistory() {
      return this.visibleMessages.map((item) => ({
        role: item.role === "user" ? "user" : "ai",
        text: item.text,
      }));
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
    introMessage() {
      return {
        html: `
          <div style="padding: 24px 18px; text-align: left;">
            <div style="font-size: 12px; font-weight: 700; letter-spacing: .08em; color: #7a8aa4; text-transform: uppercase;">
              OpenClaw
            </div>
            <div style="margin-top: 10px; font-size: 22px; font-weight: 700; color: #18304a;">
              开始一条新的在线调试
            </div>
            <div style="margin-top: 10px; font-size: 14px; line-height: 1.7; color: #5e6f88;">
              先选择 Runtime 和 Agent，再从下方输入调试消息。新版只替换界面，不改动现有 API 与轮询逻辑。
            </div>
          </div>
        `,
      };
    },
  },
  watch: {
    deepChatHistory: {
      handler() {
        this.syncDeepChat();
      },
      deep: true,
    },
  },
  mounted() {
    this.syncDeepChat();
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
    syncDeepChat() {
      this.$nextTick(() => {
        const deepChatEl = this.$refs.deepChatEl;
        if (!deepChatEl) {
          return;
        }
        deepChatEl.chatStyle = CHAT_STYLE;
        deepChatEl.messageStyles = MESSAGE_STYLES;
        deepChatEl.names = CHAT_NAMES;
        deepChatEl.avatars = true;
        deepChatEl.displayLoadingBubble = false;
        deepChatEl.textInput = {
          disabled: true,
        };
        deepChatEl.inputAreaStyle = {
          display: "none",
        };
        deepChatEl.introMessage = this.introMessage;
        deepChatEl.history = this.deepChatHistory;
        if (typeof deepChatEl.refreshMessages === "function") {
          deepChatEl.refreshMessages();
        }
        if (typeof deepChatEl.scrollToBottom === "function") {
          window.requestAnimationFrame(() => {
            deepChatEl.scrollToBottom();
          });
        }
      });
    },
  },
};
</script>

<style scoped>
.modern-stage {
  display: flex;
  flex-direction: column;
  min-height: 0;
  height: 100%;
  padding: 12px 8px 8px;
  color: #18304a;
}

.modern-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  padding: 8px 10px 16px;
}

.modern-header-main {
  min-width: 0;
}

.modern-eyebrow,
.panel-kicker {
  margin: 0 0 8px;
  color: #7a8aa4;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.modern-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.modern-title {
  margin: 0;
  font-size: 28px;
  line-height: 1.1;
  font-weight: 700;
}

.modern-status {
  display: inline-flex;
  align-items: center;
  padding: 7px 12px;
  border-radius: 999px;
  background: #e9f7ee;
  color: #207349;
  font-size: 12px;
  font-weight: 700;
}

.modern-status.offline {
  background: #fff3e3;
  color: #b06a17;
}

.modern-summary {
  margin: 10px 0 0;
  color: #61728c;
  font-size: 14px;
}

.modern-header-actions {
  display: flex;
  gap: 10px;
  flex-shrink: 0;
}

.modern-layout {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr) 320px;
  gap: 14px;
  flex: 1;
  min-height: 0;
}

.sessions-panel,
.conversation-shell,
.composer-shell,
.settings-card {
  border: 1px solid #dce5f3;
  border-radius: 24px;
  background: #ffffff;
  box-shadow: 0 18px 44px rgba(31, 54, 88, 0.06);
}

.sessions-panel,
.settings-panel {
  min-height: 0;
}

.sessions-panel {
  display: flex;
  flex-direction: column;
  padding: 18px;
}

.panel-title-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.panel-title-row.slim {
  margin-bottom: 16px;
}

.panel-title {
  margin: 0;
  font-size: 18px;
  line-height: 1.2;
}

.panel-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 32px;
  height: 32px;
  padding: 0 10px;
  border-radius: 999px;
  background: #eef3fb;
  color: #51627b;
  font-size: 13px;
  font-weight: 700;
}

.sessions-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 0;
  margin-top: 14px;
  overflow-y: auto;
}

.session-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
  padding: 12px 14px;
  border: 1px solid transparent;
  border-radius: 16px;
  background: #f7faff;
  text-align: left;
  cursor: pointer;
  transition: all 0.18s ease;
}

.session-item:hover {
  border-color: #cbd8ec;
  transform: translateY(-1px);
}

.session-item.active {
  background: linear-gradient(135deg, #edf4ff 0%, #e4eeff 100%);
  border-color: #bdd0f3;
  box-shadow: inset 0 0 0 1px rgba(47, 111, 237, 0.08);
}

.session-item-main {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.session-id {
  color: #17314b;
  font-size: 14px;
  font-weight: 700;
}

.session-time {
  color: #72839d;
  font-size: 12px;
}

.session-delete {
  color: #9cadc4;
}

.sessions-empty,
.timeline-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 220px;
  text-align: center;
  color: #6f8098;
}

.sessions-empty-icon {
  margin-bottom: 10px;
  font-size: 32px;
  letter-spacing: 0.12em;
  color: #adc0de;
}

.sessions-empty-title {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
  color: #243852;
}

.sessions-empty-text {
  margin: 8px 0 0;
  font-size: 13px;
  line-height: 1.7;
}

.conversation-panel {
  display: grid;
  grid-template-rows: minmax(0, 1fr) auto;
  gap: 14px;
  min-height: 0;
}

.conversation-shell {
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding: 18px;
  background: linear-gradient(180deg, #fdfefe 0%, #f6f9ff 100%);
}

.conversation-topbar {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  margin-bottom: 14px;
}

.conversation-badges {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.topbar-chip {
  display: inline-flex;
  align-items: center;
  padding: 6px 10px;
  border-radius: 999px;
  background: #17314b;
  color: #ffffff;
  font-size: 12px;
  font-weight: 600;
}

.topbar-chip.subtle {
  background: #edf3fc;
  color: #4e627d;
}

.topbar-chip.muted {
  background: #f5f7fb;
  color: #8393aa;
}

.topbar-action {
  color: #3f6fd8;
}

.status-banner {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  padding: 12px 14px;
  border-radius: 18px;
  margin-bottom: 14px;
  background: #eff5ff;
  color: #204572;
}

.status-banner.tone-danger {
  background: #fff2f0;
  color: #a13f34;
}

.status-banner.tone-warning {
  background: #fff8e7;
  color: #8d640d;
}

.status-banner.tone-success {
  background: #edf9f1;
  color: #216842;
}

.status-banner-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 58px;
  height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.7);
  font-size: 12px;
  font-weight: 700;
}

.status-banner-body {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.status-banner-body strong {
  font-size: 14px;
}

.status-banner-body span {
  color: inherit;
  opacity: 0.78;
  font-size: 12px;
}

.deep-chat-shell {
  flex: 1;
  min-height: 0;
}

.deep-chat-host {
  display: block;
  height: 100%;
  min-height: 360px;
}

.composer-shell {
  padding: 18px;
}

.composer-note {
  margin-bottom: 12px;
  padding: 10px 12px;
  border-radius: 14px;
  background: #edf4ff;
  color: #31517a;
  font-size: 13px;
  line-height: 1.6;
}

.composer-note.muted {
  background: #f5f7fb;
  color: #71819a;
}

.composer-note.warning {
  background: #fff8e7;
  color: #8b6413;
}

.composer-note.danger {
  background: #fff1ee;
  color: #a3453b;
}

.composer-input ::v-deep .el-textarea__inner {
  min-height: 132px !important;
  border-radius: 18px;
  border-color: #d5dfef;
  background: #f8fbff;
  box-shadow: inset 0 1px 2px rgba(20, 42, 79, 0.04);
}

.composer-footer {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  margin-top: 14px;
}

.composer-tip {
  color: #7a8aa4;
  font-size: 12px;
}

.settings-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 0;
}

.settings-card {
  padding: 18px;
}

.setting-field + .setting-field {
  margin-top: 14px;
}

.setting-label {
  display: block;
  margin-bottom: 8px;
  color: #4f617d;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.setting-select {
  width: 100%;
}

.toggle-row {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  align-items: flex-start;
}

.toggle-row + .toggle-row {
  margin-top: 18px;
}

.toggle-title {
  display: block;
  color: #1e344f;
  font-size: 14px;
  font-weight: 700;
}

.toggle-desc {
  display: block;
  margin-top: 4px;
  color: #72839d;
  font-size: 12px;
  line-height: 1.6;
}

.timeline-card {
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.timeline-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 0;
  overflow-y: auto;
}

.timeline-item {
  display: flex;
  gap: 12px;
}

.timeline-dot {
  flex: 0 0 10px;
  width: 10px;
  height: 10px;
  margin-top: 6px;
  border-radius: 50%;
  background: #7a8aa4;
}

.timeline-dot.tone-primary {
  background: #2f6fed;
}

.timeline-dot.tone-success {
  background: #1e9a63;
}

.timeline-dot.tone-warning {
  background: #d58d15;
}

.timeline-dot.tone-danger {
  background: #d45845;
}

.timeline-body {
  min-width: 0;
}

.timeline-head {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}

.timeline-label {
  color: #20344d;
  font-size: 12px;
  font-weight: 700;
}

.timeline-type,
.timeline-meta {
  color: #7a8aa4;
  font-size: 12px;
}

.timeline-text {
  margin-top: 4px;
  color: #30465f;
  font-size: 13px;
  line-height: 1.6;
}

@media (max-width: 1360px) {
  .modern-layout {
    grid-template-columns: 220px minmax(0, 1fr);
  }

  .settings-panel {
    grid-column: 1 / -1;
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 980px) {
  .modern-header,
  .composer-footer,
  .toggle-row {
    flex-direction: column;
  }

  .modern-layout,
  .settings-panel {
    display: flex;
    flex-direction: column;
  }

  .sessions-panel,
  .conversation-shell,
  .composer-shell,
  .settings-card {
    min-height: auto;
  }

  .deep-chat-host {
    min-height: 420px;
  }
}
</style>

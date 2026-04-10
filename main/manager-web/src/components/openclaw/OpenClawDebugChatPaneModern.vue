<template>
  <section class="modern-stage">
    <header class="modern-header">
      <div class="modern-toolbar-meta">
        <span class="toolbar-chip toolbar-chip-strong">{{ channelName }}</span>
        <span v-if="runtimeChipLabel" class="toolbar-chip">{{ runtimeChipLabel }}</span>
        <span class="toolbar-chip subtle">{{ availabilityChipLabel }}</span>
        <span v-if="!debugReady" class="toolbar-chip offline">
          {{ hasAvailableBridge ? "等待连接" : "等待 Bridge" }}
        </span>
      </div>
      <div class="modern-header-actions">
        <el-button
          v-if="latestAssistantText"
          size="small"
          type="text"
          class="topbar-action toolbar-action"
          @click="playLatestMessage"
        >
          播放最新回复
        </el-button>
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
          <div v-if="activeStatusEvent" class="conversation-status-bar">
            <span class="conversation-status-dot" :class="statusToneClass(activeStatusEvent.tone)"></span>
            <span class="conversation-status-text">{{ activeStatusEvent.text }}</span>
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
          <div class="composer-controls">
            <div v-if="showRuntimeSelector" class="control-field control-field-runtime">
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

            <div class="control-field control-field-agent">
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
              <span class="control-switch-text">推送到设备</span>
              <el-switch v-model="localPushToDevice" :disabled="!debugReady || !hasActiveConnection" />
            </label>

            <label class="control-switch">
              <span class="control-switch-text">浏览器语音</span>
              <el-switch v-model="localBrowserAudio" :disabled="!debugReady" />
            </label>

            <el-button
              class="control-settings-button"
              plain
              :disabled="!debugReady"
              @click="$emit('open-settings')"
            >
              <i class="el-icon-setting"></i>
              <span>设置</span>
            </el-button>
          </div>

          <div v-if="!debugReady" class="composer-note danger">
            {{ debugDisabledReason }}
          </div>

          <div v-if="selectedAgentNeedsInventorySync" class="composer-note warning">
            当前 Agent 未出现在 inventory 中，建议先同步 OpenClaw inventory。
          </div>

          <div v-if="deliverySummary" class="composer-inline-summary" :title="deliverySummary">
            <span class="composer-inline-label">详细稿投递</span>
            <span class="composer-inline-value">{{ deliverySummary }}</span>
          </div>

          <el-input
            v-model="localInputText"
            class="composer-input"
            type="textarea"
            :rows="4"
            resize="none"
            placeholder="输入调试消息，Ctrl + Enter 发送"
            @keyup.ctrl.enter.native="$emit('send')"
          />

          <div class="composer-footer">
            <div class="composer-tip">Ctrl + Enter 发送，沿用原有接口与轮询逻辑</div>
            <el-button
              type="primary"
              icon="el-icon-position"
              :loading="debugSending"
              :disabled="!canSendDirectChat"
              @click="$emit('send')"
            >
              发送
            </el-button>
          </div>
        </div>
      </div>
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
    connectedBridgeCount: {
      type: Number,
      default: 0,
    },
    connectionCount: {
      type: Number,
      default: 0,
    },
    connectionsLoading: {
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
    deliverySummary: {
      type: String,
      default: "",
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
      const source = Array.isArray(this.debugStatusEvents) ? this.debugStatusEvents.slice(-12) : [];
      const normalized = source.reduce((list, item) => {
        const last = list[list.length - 1];
        const isSameStatus = Boolean(
          last &&
          last.text === item.text &&
          last.meta === item.meta &&
          last.tone === item.tone &&
          last.eventType === item.eventType
        );
        if (isSameStatus) {
          list[list.length - 1] = item;
          return list;
        }
        list.push(item);
        return list;
      }, []);

      if (this.debugPending) {
        const pendingItem = {
          id: "pending-generic",
          text: "调试请求已提交，等待 OpenClaw 处理",
          meta: "",
          tone: "warning",
          eventType: "pending",
        };
        const latest = normalized[normalized.length - 1];
        if (!latest || latest.eventType !== "accepted") {
          normalized.push(pendingItem);
        }
      }

      return normalized;
    },
    activeStatusEvent() {
      if (!this.visibleStatusEvents.length) {
        return null;
      }
      return this.visibleStatusEvents[this.visibleStatusEvents.length - 1];
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
    runtimeChipLabel() {
      const label = String(this.currentRuntimeLabel || "").trim();
      if (!label) {
        return "";
      }
      return label === this.channelName ? "" : label;
    },
    availabilityChipLabel() {
      const bridgeLabel = `服务 ${this.connectedBridgeCount}`;
      if (this.connectionsLoading && this.connectionCount <= 0) {
        return `${bridgeLabel} · 设备同步中`;
      }
      if (this.connectionCount > 0) {
        return `${bridgeLabel} · 设备 ${this.connectionCount}`;
      }
      return `${bridgeLabel} · 暂无设备`;
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

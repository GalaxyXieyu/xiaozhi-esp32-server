<template>
  <section class="debug-stage">
    <div class="stage-header">
      <div class="stage-header-main">
        <div class="stage-title-row">
          <h3 class="stage-title">{{ channelName }}</h3>
          <span class="stage-status" :class="{ offline: !debugReady }">
            {{ debugReady ? "调试就绪" : (hasAvailableBridge ? "等待连接" : "等待 Bridge") }}
          </span>
        </div>
      </div>
      <div class="stage-toolbar">
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
    </div>

    <div class="stage-body">
      <aside class="history-rail">
        <div class="history-rail-header">
          <h4 class="history-rail-title">会话</h4>
          <el-button size="mini" plain @click="$emit('create-session')">新建</el-button>
        </div>
        <div class="history-rail-body">
          <div v-if="debugHistorySessions.length" class="history-list">
            <div
              v-for="item in debugHistorySessions"
              :key="item.sessionId"
              class="history-item"
              :class="{ active: item.sessionId === debugSessionId }"
              role="button"
              tabindex="0"
              @click="$emit('restore-history', item)"
              @keydown.enter.prevent="$emit('restore-history', item)"
            >
              <div class="history-main">
                <div class="history-head">
                  <span class="history-session" :title="item.sessionId">{{ historyLabel(item.sessionId) }}</span>
                  <span class="history-time">{{ item.updatedAtText }}</span>
                </div>
              </div>
              <el-button
                class="history-delete"
                type="text"
                icon="el-icon-close"
                @click.stop="$emit('delete-history', item.sessionId)"
              />
            </div>
          </div>
          <div v-else class="history-empty">暂无会话</div>
        </div>
      </aside>

      <div class="chat-column">
        <div ref="transcript" class="debug-transcript">
          <div v-if="visibleMessages.length" class="message-list">
            <article
              v-for="item in visibleMessages"
              :key="item.id"
              class="message-row"
              :class="`role-${item.role}`"
            >
              <div class="message-avatar">{{ messageAvatar(item.role) }}</div>
              <div class="message-card">
                <div class="message-head">
                  <div class="message-head-main">
                    <span class="message-role">{{ messageRoleLabel(item.role) }}</span>
                    <span v-if="item.meta" class="message-meta">{{ item.meta }}</span>
                  </div>
                  <el-button
                    v-if="item.role === 'assistant' && item.text"
                    class="message-audio-btn"
                    type="text"
                    size="mini"
                    @click="$emit('play-message', item.text)"
                  >
                    播放
                  </el-button>
                </div>
                <div class="message-body">{{ item.text }}</div>
              </div>
            </article>

            <article v-if="activeStatusEvent" class="message-row role-system inline-status-row">
              <div class="message-avatar">SYS</div>
              <div class="inline-status-card">
                <div class="inline-status-head">
                  <span class="status-pill" :class="statusToneClass(activeStatusEvent.tone)">
                    {{ statusToneLabel(activeStatusEvent.tone) }}
                  </span>
                  <span class="inline-status-label">执行中</span>
                </div>
                <div class="status-text">{{ activeStatusEvent.text }}</div>
                <div v-if="activeStatusEvent.meta" class="status-meta">{{ activeStatusEvent.meta }}</div>
              </div>
            </article>
          </div>
          <div v-else class="empty-state">
            <div class="empty-icon">AI</div>
            <h4 class="empty-title">开始调试</h4>
          </div>
        </div>

        <div class="composer-panel">
          <div class="composer-head">
            <div class="composer-target">{{ agentLabel }}</div>
            <div class="composer-runtime">{{ currentRuntimeLabel }}</div>
          </div>

          <div class="composer-settings">
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
              <div v-if="selectedAgentNeedsInventorySync" class="setting-warning">
                当前 Agent 未出现在 inventory 中
              </div>
            </div>

            <div class="setting-field">
              <label class="setting-label">结果</label>
              <div class="setting-switches">
                <label class="setting-switch">
                  <span>推送到设备</span>
                  <el-switch v-model="localPushToDevice" :disabled="!debugReady || !hasActiveConnection" />
                </label>
                <label class="setting-switch">
                  <span>浏览器语音</span>
                  <el-switch v-model="localBrowserAudio" :disabled="!debugReady" />
                </label>
              </div>
            </div>
          </div>

          <div v-if="connectionLabel" class="connection-note">
            已自动复用在线连接：{{ connectionLabel }}
          </div>
          <div v-else-if="debugReady" class="connection-note is-muted">
            当前没有在线设备连接，本次调试仅返回到页面，不会推送到设备。
          </div>

          <div v-if="!debugReady" class="runtime-warning">
            {{ debugDisabledReason }}
          </div>

          <el-input
            v-model="localInputText"
            class="composer-input"
            type="textarea"
            :rows="5"
            resize="none"
            placeholder="输入测试消息，Ctrl + Enter 发送"
            @keyup.ctrl.enter.native="$emit('send')"
          />

          <div class="composer-footer">
            <el-button type="primary" :loading="debugSending" :disabled="!canSendDirectChat" @click="$emit('send')">
              发送测试消息
            </el-button>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<script>
export default {
  name: "OpenClawDebugChatPane",
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
      return Array.isArray(this.debugStatusEvents) ? this.debugStatusEvents.slice(-6) : [];
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
        if (
          sameAssistantTurn
        ) {
          list[list.length - 1] = item;
          return list;
        }
        list.push(item);
        return list;
      }, []);
    },
  },
  watch: {
    debugMessages() {
      this.scrollToBottom();
    },
  },
  mounted() {
    this.scrollToBottom();
  },
  methods: {
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
@import "@/components/openclaw/styles/openclaw-debug-chat-pane-classic.scss";
</style>

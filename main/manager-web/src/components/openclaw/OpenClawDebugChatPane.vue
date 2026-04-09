<template>
  <section class="debug-stage">
    <div class="stage-header">
      <div class="stage-header-main">
        <div class="stage-title-row">
          <h3 class="stage-title">{{ channelName }}</h3>
          <span class="stage-status" :class="{ offline: !hasAvailableBridge }">
            {{ hasAvailableBridge ? "Bridge 在线" : "等待 Bridge" }}
          </span>
        </div>
      </div>
      <div class="stage-toolbar">
        <el-button
          v-if="hasBrowserAudio"
          size="small"
          plain
          @click="$emit('play-audio')"
        >
          播放语音
        </el-button>
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
        <div v-if="debugPending || visibleStatusEvents.length" class="status-strip">
          <div class="status-strip-head">
            <span class="status-strip-title">执行状态</span>
            <span v-if="debugPending" class="status-pill tone-warning">处理中</span>
          </div>
          <div class="status-event-list">
            <div
              v-for="item in visibleStatusEvents"
              :key="item.id"
              class="status-event"
            >
              <span class="status-pill" :class="statusToneClass(item.tone)">{{ statusToneLabel(item.tone) }}</span>
              <div class="status-copy">
                <div class="status-text">{{ item.text }}</div>
                <div v-if="item.meta" class="status-meta">{{ item.meta }}</div>
              </div>
            </div>
          </div>
        </div>

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
                  <span class="message-role">{{ messageRoleLabel(item.role) }}</span>
                  <span v-if="item.meta" class="message-meta">{{ item.meta }}</span>
                </div>
                <div class="message-body">{{ item.text }}</div>
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

          <div v-if="!hasAvailableBridge" class="runtime-warning">
            当前 runtime 没有在线的 OpenClaw bridge，暂时不能发送调试消息。
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
    currentRuntimeLabel: {
      type: String,
      default: "",
    },
    agentLabel: {
      type: String,
      default: "未选择",
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
    hasBrowserAudio: {
      type: Boolean,
      default: false,
    },
    debugPending: {
      type: Boolean,
      default: false,
    },
  },
  computed: {
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
    visibleMessages() {
      return Array.isArray(this.debugMessages)
        ? this.debugMessages.filter((item) => item && item.role !== "system")
        : [];
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

<style scoped>
.debug-stage {
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding: 10px 8px 8px;
  background: transparent;
}

.stage-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.stage-header-main {
  min-width: 0;
}

.stage-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.stage-title {
  margin: 0;
  color: #18243d;
  font-size: 24px;
  line-height: 1.2;
}

.stage-status {
  display: inline-flex;
  align-items: center;
  padding: 6px 10px;
  border-radius: 999px;
  background: #edf7f1;
  color: #1f7a49;
  font-size: 11px;
  font-weight: 700;
}

.stage-status.offline {
  background: #fff2df;
  color: #b26a19;
}

.stage-toolbar {
  display: flex;
  justify-content: flex-end;
}

.stage-body {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: 12px;
  flex: 1;
  min-height: 0;
  margin-top: 10px;
}

.history-rail {
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding: 0;
  background: transparent;
}

.history-rail-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.history-rail-title {
  margin: 0;
  color: #22314f;
  font-size: 16px;
}

.history-rail-body {
  min-height: 0;
  margin-top: 10px;
  overflow-y: auto;
}

.history-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.history-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
  padding: 8px 10px;
  border: 0;
  border-radius: 12px;
  background: transparent;
  text-align: left;
  cursor: pointer;
  transition: background-color 0.18s ease, color 0.18s ease;
}

.history-item:hover {
  background: rgba(227, 233, 244, 0.55);
}

.history-item.active {
  background: rgba(221, 230, 252, 0.9);
}

.history-main {
  min-width: 0;
  flex: 1;
}

.history-head {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: center;
}

.history-session {
  color: #1c2842;
  font-weight: 700;
}

.history-time,
.history-empty {
  color: #70809a;
  line-height: 1.6;
}

.history-time {
  font-size: 12px;
  white-space: nowrap;
}

.history-delete {
  flex: 0 0 auto;
  padding: 0;
  color: #97a4ba;
}

.history-delete:hover,
.history-delete:focus {
  color: #d14f4f;
}

.history-empty {
  padding: 10px 2px;
}

.chat-column {
  display: flex;
  flex-direction: column;
  min-height: 0;
  gap: 10px;
}

.status-strip {
  padding: 14px 16px;
  border-radius: 18px;
  border: 1px solid #e5ebf4;
  background: #f8fafc;
}

.status-strip-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
}

.status-strip-title {
  color: #22314f;
  font-size: 13px;
  font-weight: 700;
}

.status-event-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 12px;
}

.status-event {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  flex: 0 0 auto;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
  line-height: 1.2;
  background: #edf2fb;
  color: #3e5278;
}

.status-pill.tone-primary {
  background: #eef4ff;
  color: #315ca8;
}

.status-pill.tone-info {
  background: #edf2fb;
  color: #3e5278;
}

.status-pill.tone-warning {
  background: #fff4de;
  color: #b26a19;
}

.status-pill.tone-success {
  background: #edf7f1;
  color: #1f7a49;
}

.status-pill.tone-danger {
  background: #fff0ee;
  color: #c24c45;
}

.status-copy {
  min-width: 0;
}

.status-text {
  color: #24324d;
  font-size: 13px;
  line-height: 1.6;
  word-break: break-word;
}

.status-meta {
  margin-top: 2px;
  color: #7a88a0;
  font-size: 12px;
  line-height: 1.5;
}

.debug-transcript {
  flex: 1;
  min-height: 0;
  padding: 4px 0 0;
  overflow-y: auto;
  background: transparent;
}

.message-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 4px 2px 8px;
}

.message-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.message-row.role-user {
  flex-direction: row-reverse;
}

.message-avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  flex: 0 0 34px;
  border-radius: 50%;
  background: #e9eef8;
  color: #30446f;
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.08em;
}

.message-row.role-assistant .message-avatar {
  background: #eef8eb;
  color: #2b6a38;
}

.message-row.role-system .message-avatar {
  background: #fff2df;
  color: #a2601c;
}

.message-card {
  max-width: min(78%, 720px);
  padding: 12px 14px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.88);
  border: 0;
}

.message-row.role-user .message-card {
  background: #eef4ff;
  border-color: #dbe5fb;
}

.message-row.role-assistant .message-card {
  background: #f6faf4;
  border-color: #e1ecd9;
}

.message-row.role-system .message-card {
  background: #fff8ef;
  border-color: #f2e0bc;
}

.message-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.message-row.role-user .message-head {
  flex-direction: row-reverse;
}

.message-role {
  color: #1d2942;
  font-weight: 700;
}

.message-meta {
  color: #7a88a0;
  font-size: 13px;
}

.message-body {
  margin-top: 8px;
  color: #344158;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 100%;
  padding: 48px 24px;
  text-align: center;
}

.empty-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 60px;
  height: 60px;
  border-radius: 18px;
  background: #e9effa;
  color: #2f4270;
  font-size: 18px;
  font-weight: 800;
  letter-spacing: 0.08em;
}

.empty-title {
  margin: 14px 0 0;
  color: #1c2843;
  font-size: 20px;
}

.composer-panel {
  padding: 12px 0 0;
  border-top: 1px solid #e4eaf4;
  background: transparent;
}

.composer-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.composer-target {
  color: #1c2842;
  font-size: 16px;
  font-weight: 700;
}

.composer-runtime {
  display: inline-flex;
  align-items: center;
  height: fit-content;
  padding: 6px 10px;
  border-radius: 999px;
  background: #f3f6ff;
  color: #30446f;
  font-size: 11px;
  font-weight: 700;
}

.runtime-warning {
  margin-top: 14px;
  padding: 10px 12px;
  border-radius: 14px;
  border: 1px solid #f2d29d;
  background: #fff8ea;
  color: #b26a19;
  font-size: 12px;
  line-height: 1.6;
}

.composer-input {
  margin-top: 14px;
}

.composer-input ::v-deep textarea.el-textarea__inner {
  min-height: 116px;
  border-radius: 14px;
  padding: 12px 14px;
  border-color: #e1e7f1;
  line-height: 1.7;
  background: rgba(255, 255, 255, 0.9);
}

.composer-footer {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 10px;
  margin-top: 16px;
}

@media (max-width: 1180px) {
  .stage-body {
    grid-template-columns: 1fr;
  }

  .message-card {
    max-width: 88%;
  }
}

@media (max-width: 760px) {
  .stage-header,
  .composer-head,
  .composer-footer {
    flex-direction: column;
  }

  .debug-stage {
    padding: 8px 0 0;
  }

  .stage-title {
    font-size: 24px;
  }

  .message-card {
    max-width: 100%;
  }
}
</style>

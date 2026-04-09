<template>
  <section class="debug-stage">
    <div class="stage-header">
      <div class="stage-header-main">
        <div class="stage-eyebrow">在线调试</div>
        <div class="stage-title-row">
          <h3 class="stage-title">{{ channelName }}</h3>
          <span class="stage-status" :class="{ offline: !hasAvailableBridge }">
            {{ hasAvailableBridge ? "Bridge 在线" : "等待 Bridge" }}
          </span>
        </div>
        <div class="stage-subtitle">当前 Agent：{{ agentLabel }}</div>
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

    <div class="stage-meta">
      <span class="meta-item">Runtime：{{ currentRuntimeLabel }}</span>
      <span class="meta-item">Agent：{{ agentLabel }}</span>
      <span class="meta-item">会话：{{ debugSessionId }}</span>
    </div>

    <div class="stage-body">
      <aside class="history-rail">
        <div class="history-rail-header">
          <div>
            <div class="sidebar-eyebrow">Sessions</div>
            <h4 class="history-rail-title">会话</h4>
          </div>
          <el-button size="mini" plain @click="$emit('create-session')">新建</el-button>
        </div>
        <div class="history-rail-body">
          <div v-if="debugHistorySessions.length" class="history-list">
            <button
              v-for="item in debugHistorySessions"
              :key="item.sessionId"
              class="history-item"
              :class="{ active: item.sessionId === debugSessionId }"
              @click="$emit('restore-history', item)"
            >
              <div class="history-head">
                <span class="history-session">{{ item.sessionId }}</span>
                <span class="history-time">{{ item.updatedAtText }}</span>
              </div>
              <div class="history-meta">{{ item.agentName || item.agentId || "-" }}</div>
              <div class="history-preview">{{ item.preview || "暂无预览" }}</div>
            </button>
          </div>
          <div v-else class="history-empty">当前 channel 还没有本地调试历史。</div>
        </div>
      </aside>

      <div class="chat-column">
        <div ref="transcript" class="debug-transcript">
          <div v-if="debugMessages.length" class="message-list">
            <article
              v-for="item in debugMessages"
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
            <h4 class="empty-title">开始一轮调试对话</h4>
            <p class="empty-description">先在右侧确认 Agent 和上下文，再在底部输入消息，直接验证当前 OpenClaw Agent 的回复。</p>
          </div>
        </div>

        <div class="composer-panel">
          <div class="composer-head">
            <div>
              <div class="composer-eyebrow">Current Target</div>
              <div class="composer-target">{{ agentLabel }}</div>
            </div>
            <div class="composer-runtime">{{ currentRuntimeLabel }}</div>
          </div>

          <div v-if="!hasAvailableBridge" class="runtime-warning">
            当前 runtime 没有在线的 OpenClaw bridge，暂时不能发送调试消息。
          </div>

          <div v-if="selectedAgentNeedsInventorySync" class="field-hint warning composer-warning">
            当前 Agent 来自业务绑定，inventory 还没回传它。可先保留该选择，但要等对应 runtime 有在线 bridge 后才能真正调试。
          </div>

          <el-input
            v-model="localInputText"
            class="composer-input"
            type="textarea"
            :rows="5"
            resize="none"
            placeholder="输入要发送给 OpenClaw 的测试消息。按 Ctrl + Enter 快速发送。"
            @keyup.ctrl.enter.native="$emit('send')"
          />

          <div class="composer-footer">
            <span class="composer-note">{{ composerNote }}</span>
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
    debugHistorySessions: {
      type: Array,
      default: () => [],
    },
    selectedAgentNeedsInventorySync: {
      type: Boolean,
      default: false,
    },
    inputText: {
      type: String,
      default: "",
    },
    composerNote: {
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
  padding: 22px;
  border-radius: 20px;
  background: #ffffff;
  border: 1px solid #e4e9f4;
  box-shadow: 0 8px 24px rgba(87, 104, 142, 0.08);
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

.stage-eyebrow,
.sidebar-eyebrow,
.composer-eyebrow {
  font-size: 11px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: #7c8ca7;
}

.stage-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 6px;
}

.stage-title {
  margin: 0;
  color: #18243d;
  font-size: 24px;
  line-height: 1.2;
}

.stage-subtitle {
  margin-top: 8px;
  color: #66758f;
  line-height: 1.5;
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

.stage-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 14px;
  margin-top: 14px;
  color: #6d7c96;
  font-size: 13px;
}

.meta-item {
  white-space: nowrap;
}

.stage-body {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: 16px;
  flex: 1;
  min-height: 0;
  margin-top: 14px;
}

.history-rail {
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding: 14px;
  border-radius: 18px;
  border: 1px solid #e6ebf5;
  background: #fbfcfe;
}

.history-rail-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.history-rail-title {
  margin: 6px 0 0;
  color: #22314f;
  font-size: 16px;
}

.history-rail-body {
  min-height: 0;
  margin-top: 14px;
  overflow-y: auto;
}

.history-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.history-item {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid #e6ebf4;
  border-radius: 14px;
  background: #ffffff;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.18s ease, background-color 0.18s ease;
}

.history-item:hover {
  border-color: #d6dff0;
  background: #f8fafc;
}

.history-item.active {
  border-color: #cfdcf8;
  background: #f1f5ff;
}

.history-head {
  display: flex;
  justify-content: space-between;
  gap: 10px;
}

.history-session {
  color: #1c2842;
  font-weight: 700;
}

.history-time,
.history-meta,
.history-preview,
.history-empty {
  color: #70809a;
  line-height: 1.6;
}

.history-empty {
  padding: 10px 2px;
}

.history-meta {
  margin-top: 6px;
}

.history-preview {
  margin-top: 4px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.chat-column {
  display: flex;
  flex-direction: column;
  min-height: 0;
  gap: 14px;
}

.debug-transcript {
  flex: 1;
  min-height: 0;
  padding: 8px 6px 6px;
  overflow-y: auto;
  border-radius: 18px;
  background: #f8fafc;
  border: 1px solid #e5ebf4;
}

.message-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 8px;
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
  padding: 14px 16px;
  border-radius: 18px;
  background: #ffffff;
  border: 1px solid #e5ebf4;
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

.empty-description {
  max-width: 420px;
  margin: 10px 0 0;
  color: #6b7a95;
  line-height: 1.8;
}

.composer-panel {
  padding: 16px;
  border-radius: 18px;
  border: 1px solid #e4eaf4;
  background: #ffffff;
}

.composer-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.composer-target {
  margin-top: 6px;
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

.composer-warning,
.field-hint {
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.6;
  color: #6f7f99;
}

.field-hint.warning {
  color: #b26a19;
}

.composer-input {
  margin-top: 14px;
}

.composer-input ::v-deep textarea.el-textarea__inner {
  min-height: 116px;
  border-radius: 14px;
  padding: 12px 14px;
  border-color: #dde5f2;
  line-height: 1.7;
  background: #fbfcfe;
}

.composer-footer {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 10px;
  margin-top: 16px;
}

.composer-note {
  flex: 1;
  color: #71809c;
  line-height: 1.7;
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
    padding: 16px;
  }

  .stage-title {
    font-size: 24px;
  }

  .message-card {
    max-width: 100%;
  }
}
</style>

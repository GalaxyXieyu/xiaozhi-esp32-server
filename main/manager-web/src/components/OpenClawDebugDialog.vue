<template>
  <el-dialog
    :visible.sync="dialogVisible"
    :title="dialogTitle"
    width="78%"
    custom-class="openclaw-debug-dialog"
    :before-close="handleClose"
  >
    <div class="debug-shell">
      <section class="debug-stage">
        <div class="stage-header">
          <div>
            <div class="stage-eyebrow">Debug Context</div>
            <h3 class="stage-title">{{ channelName }}</h3>
            <p class="stage-description">确认当前 Agent 是否真的回到了目标 OpenClaw 运行时，不再让 bridge 和步骤提示抢走注意力。</p>
          </div>
          <div class="stage-pills">
            <span class="context-pill">
              <span>Runtime</span>
              <strong>{{ currentRuntimeLabel }}</strong>
            </span>
            <span class="context-pill">
              <span>Agent</span>
              <strong>{{ debugForm.agentName || debugForm.agentId || "未选择" }}</strong>
            </span>
            <span class="context-pill session">
              <span>会话</span>
              <strong>{{ debugForm.debugSessionId }}</strong>
            </span>
          </div>
        </div>

        <div ref="transcript" class="debug-transcript">
          <div v-if="debugMessages.length" class="message-list">
            <article
              v-for="item in debugMessages"
              :key="item.id"
              class="message-card"
              :class="`role-${item.role}`"
            >
              <div class="message-head">
                <span class="message-role">
                  {{
                    item.role === "user"
                      ? "后台输入"
                      : item.role === "assistant"
                        ? "OpenClaw 返回"
                        : "系统信息"
                  }}
                </span>
                <span v-if="item.meta" class="message-meta">{{ item.meta }}</span>
              </div>
              <div class="message-body">{{ item.text }}</div>
            </article>
          </div>
          <el-empty v-else description="输入一条消息，直接验证当前 OpenClaw Agent 的回复。" :image-size="88" />
        </div>
      </section>

      <aside class="debug-sidebar">
        <div class="sidebar-card">
          <div class="sidebar-head">
            <div>
              <div class="sidebar-eyebrow">Composer</div>
              <h3 class="sidebar-title">开始调试</h3>
            </div>
            <div class="sidebar-actions">
              <el-button size="small" @click="createDebugSession">新建会话</el-button>
              <el-button
                size="small"
                type="warning"
                plain
                :disabled="!debugForm.account || !debugForm.debugSessionId"
                :loading="debugClearing"
                @click="clearDebugSession"
              >
                清空会话
              </el-button>
            </div>
          </div>

          <div v-if="showRuntimeSelector" class="field-block">
            <label class="field-label">Runtime / Account</label>
            <el-select
              v-model="debugForm.account"
              class="field-select"
              filterable
              placeholder="选择 runtime/account"
              @change="handleDebugAccountChange"
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
              v-model="debugForm.agentId"
              class="field-select"
              filterable
              :disabled="!currentDebugAgentOptions.length"
              placeholder="选择 OpenClaw Agent"
              @change="handleDebugAgentChange"
            >
              <el-option
                v-for="item in currentDebugAgentOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </div>

          <div class="field-block">
            <label class="field-label">测试输入</label>
            <el-input
              v-model="debugForm.inputText"
              type="textarea"
              :rows="9"
              resize="none"
              placeholder="输入要发送给 OpenClaw 的测试消息。按 Ctrl + Enter 快速发送。"
              @keyup.ctrl.enter.native="sendDirectChat"
            />
          </div>

          <div class="composer-footer">
            <span class="composer-note">消息只在后台调试链路中流转，不会推送给真实设备。</span>
            <el-button type="primary" :loading="debugSending" :disabled="!canSendDirectChat" @click="sendDirectChat">
              发送测试消息
            </el-button>
          </div>
        </div>

        <div class="sidebar-card compact">
          <button type="button" class="toggle-row" @click="showAdvanced = !showAdvanced">
            <span>高级上下文</span>
            <i :class="showAdvanced ? 'el-icon-arrow-up' : 'el-icon-arrow-down'" />
          </button>
          <el-collapse-transition>
            <div v-if="showAdvanced" class="advanced-body">
              <div class="context-row">
                <span class="context-key">当前 Bridge</span>
                <span class="context-value">{{ currentBridgeLabel }}</span>
              </div>
              <div v-if="showBridgeSelector" class="field-block slim">
                <label class="field-label">Bridge</label>
                <el-select
                  v-model="debugForm.bridgeId"
                  class="field-select"
                  clearable
                  filterable
                  placeholder="指定 bridge（可选）"
                >
                  <el-option
                    v-for="item in bridgeOptions"
                    :key="item.bridgeId"
                    :label="`${item.name || item.bridgeId} · ${item.connected ? '在线' : '离线'}`"
                    :value="item.bridgeId"
                  />
                </el-select>
              </div>
              <div class="field-block slim">
                <label class="field-label">说话人标签</label>
                <el-input v-model="debugForm.speaker" maxlength="40" placeholder="后台调试" />
              </div>
            </div>
          </el-collapse-transition>
        </div>

        <div class="sidebar-card compact">
          <button type="button" class="toggle-row" @click="showHistory = !showHistory">
            <span>历史会话</span>
            <i :class="showHistory ? 'el-icon-arrow-up' : 'el-icon-arrow-down'" />
          </button>
          <el-collapse-transition>
            <div v-if="showHistory" class="history-body">
              <div v-if="debugHistorySessions.length" class="history-list">
                <button
                  v-for="item in debugHistorySessions"
                  :key="item.sessionId"
                  class="history-item"
                  :class="{ active: item.sessionId === debugForm.debugSessionId }"
                  @click="restoreDebugHistory(item)"
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
          </el-collapse-transition>
        </div>
      </aside>
    </div>
  </el-dialog>
</template>

<script>
import Api from "@/apis/api";

const createDebugSessionId = () => `web-debug-${Date.now()}`;
const DEBUG_HISTORY_PREFIX = "openclaw-debug-history:";

const createEmptyDebugForm = () => ({
  account: "",
  bridgeId: "",
  agentId: "",
  agentName: "",
  speaker: "后台调试",
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
  return `${date.toLocaleDateString()} ${date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}`;
};

export default {
  name: "OpenClawDebugDialog",
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
      debugHistorySessions: [],
      debugSending: false,
      debugClearing: false,
      routePrefillApplied: false,
      showAdvanced: false,
      showHistory: false,
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
    currentRuntimeLabel() {
      const matched = this.runtimeAccounts.find((item) => item.value === this.debugForm.account);
      return matched ? matched.label : (this.debugForm.account || "自动选择");
    },
    currentBridgeLabel() {
      const matched = this.bridgeItems.find((item) => item.bridgeId === this.debugForm.bridgeId);
      if (matched) {
        return `${matched.name || matched.bridgeId}${matched.connected ? " · 在线" : " · 离线"}`;
      }
      if (!this.bridgeItems.length) {
        return "暂无 bridge";
      }
      return "自动选择在线 bridge";
    },
    currentDebugAgentOptions() {
      const bridgeKey = this.debugForm.bridgeId;
      const bridgeAgents = (this.inventory.bridgeAgents && this.inventory.bridgeAgents[bridgeKey]) || [];
      if (Array.isArray(bridgeAgents) && bridgeAgents.length) {
        return bridgeAgents;
      }
      const accountKey = this.debugForm.account;
      const accountAgents = (this.inventory.accountAgents && this.inventory.accountAgents[accountKey]) || [];
      if (Array.isArray(accountAgents) && accountAgents.length) {
        return accountAgents;
      }
      return this.agentItems;
    },
    canSendDirectChat() {
      return Boolean(
        this.channelId &&
        this.debugForm.account &&
        this.debugForm.agentId &&
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
  },
  methods: {
    handleClose() {
      this.dialogVisible = false;
    },
    resetDebugState() {
      this.debugForm = createEmptyDebugForm();
      this.debugMessages = [];
      this.debugHistorySessions = [];
      this.debugSending = false;
      this.debugClearing = false;
      this.routePrefillApplied = false;
      this.showAdvanced = false;
      this.showHistory = false;
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
        .map((item) => ({
          ...item,
          updatedAtText: formatHistoryTime(item.updatedAt),
        }))
        .filter((item) => item && item.sessionId);
    },
    persistDebugHistory() {
      if (!this.channelId || typeof window === "undefined") {
        return;
      }
      window.localStorage.setItem(this.getHistoryStorageKey(), JSON.stringify(this.debugHistorySessions));
    },
    syncCurrentHistoryEntry() {
      if (!this.channelId || !this.debugForm.debugSessionId) {
        return;
      }
      const sessionId = this.debugForm.debugSessionId;
      if (!this.debugMessages.length) {
        this.debugHistorySessions = this.debugHistorySessions.filter((item) => item.sessionId !== sessionId);
        this.persistDebugHistory();
        return;
      }
      const lastMessage = this.debugMessages[this.debugMessages.length - 1] || {};
      const now = Date.now();
      const entry = {
        sessionId,
        account: this.debugForm.account,
        bridgeId: this.debugForm.bridgeId,
        agentId: this.debugForm.agentId,
        agentName: this.debugForm.agentName,
        preview: (lastMessage.text || "").slice(0, 72),
        updatedAt: now,
        updatedAtText: formatHistoryTime(now),
        messages: this.debugMessages,
      };
      this.debugHistorySessions = [
        entry,
        ...this.debugHistorySessions.filter((item) => item.sessionId !== sessionId),
      ].slice(0, 12);
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
      this.syncDebugAgent();
    },
    handleDebugAgentChange(value) {
      this.debugForm.agentId = value;
      this.debugForm.agentName = this.findOptionLabel(this.currentDebugAgentOptions, value, this.debugForm.agentName || value);
    },
    findOptionLabel(list, value, fallback = "") {
      const matched = (Array.isArray(list) ? list : []).find((item) => item.value === value);
      return matched ? matched.label : fallback;
    },
    createDebugSession() {
      this.debugForm.debugSessionId = createDebugSessionId();
      this.debugMessages = [];
      this.syncCurrentHistoryEntry();
      this.$message.success("已创建新的 OpenClaw 调试会话");
    },
    rotateDebugSession(preserveTranscript = false) {
      this.debugForm.debugSessionId = createDebugSessionId();
      if (!preserveTranscript) {
        this.debugMessages = [];
      }
      this.syncCurrentHistoryEntry();
    },
    appendDebugMessage(role, text, extra = {}) {
      this.debugMessages.push({
        id: `${role}-${Date.now()}-${Math.random().toString(16).slice(2, 8)}`,
        role,
        text,
        meta: extra.meta || "",
      });
      this.$nextTick(() => {
        const container = this.$refs.transcript;
        if (container) {
          container.scrollTop = container.scrollHeight;
        }
      });
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
      this.debugForm.agentId = item.agentId || "";
      this.debugForm.agentName = item.agentName || item.agentId || "";
      this.syncDebugBridge();
      this.syncDebugAgent();
      this.debugForm.debugSessionId = item.sessionId;
      this.debugMessages = Array.isArray(item.messages) ? item.messages : [];
      if (showMessage) {
        this.$message.success("已恢复本地调试历史");
      }
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
            const clearedSessionId = this.debugForm.debugSessionId;
            this.appendDebugMessage("system", `已清空 OpenClaw 调试会话：${clearedSessionId}`);
            this.rotateDebugSession(true);
            this.$message.success("OpenClaw 调试会话已清空");
            return;
          }
          const message = data.msg || "清空 OpenClaw 调试会话失败";
          this.appendDebugMessage("system", message);
          this.$message.error(message);
        }, ({ data }) => {
          this.debugClearing = false;
          const message = (data && data.msg) || "清空 OpenClaw 调试会话失败";
          this.appendDebugMessage("system", message);
          this.$message.error(message);
        });
      }).catch(() => {});
    },
    sendDirectChat() {
      if (!this.canSendDirectChat) {
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
        speaker: this.debugForm.speaker,
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
          this.appendDebugMessage("assistant", response.replyText || "OpenClaw 已处理，但没有返回文本", {
            meta: [response.account, response.agentName || response.agentId].filter(Boolean).join(" / "),
          });
          return;
        }
        this.appendDebugMessage("system", data.msg || "OpenClaw 在线调试失败");
        this.$message.error(data.msg || "OpenClaw 在线调试失败");
      }, ({ data }) => {
        this.debugSending = false;
        const message = (data && data.msg) || "OpenClaw 在线调试失败";
        this.appendDebugMessage("system", message);
        this.$message.error(message);
      });
    },
  },
};
</script>

<style scoped>
.debug-shell {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) 380px;
  gap: 18px;
  min-height: 70vh;
  background:
    radial-gradient(circle at top left, rgba(122, 157, 255, 0.12), transparent 20%),
    #f6f9ff;
}

.debug-stage,
.sidebar-card {
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.96);
  border: 1px solid #e2e9f7;
  box-shadow: 0 20px 46px rgba(124, 140, 179, 0.08);
}

.debug-stage {
  display: flex;
  flex-direction: column;
  padding: 22px;
}

.stage-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}

.stage-eyebrow,
.sidebar-eyebrow {
  font-size: 12px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: #7c8ca7;
}

.stage-title,
.sidebar-title {
  margin: 10px 0 0;
  color: #18243d;
}

.stage-title {
  font-size: 28px;
}

.sidebar-title {
  font-size: 22px;
}

.stage-description {
  margin: 10px 0 0;
  color: #6b7a95;
  line-height: 1.7;
}

.stage-pills {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
}

.context-pill {
  min-width: 124px;
  padding: 12px 14px;
  border-radius: 18px;
  background: #f3f7ff;
}

.context-pill span {
  display: block;
  color: #7d8da9;
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.context-pill strong {
  display: block;
  margin-top: 6px;
  color: #1b2740;
  line-height: 1.4;
  word-break: break-word;
}

.context-pill.session {
  background: #edf2ff;
}

.debug-transcript {
  flex: 1;
  margin-top: 18px;
  padding: 6px 4px 4px;
  overflow-y: auto;
}

.message-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.message-card {
  padding: 16px 18px;
  border-radius: 20px;
  background: #f7faff;
  border: 1px solid #e6edf9;
}

.message-card.role-user {
  background: #eef4ff;
}

.message-card.role-assistant {
  background: #f8fbf2;
}

.message-card.role-system {
  background: #fff8ed;
}

.message-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
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

.debug-sidebar {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.sidebar-card {
  padding: 18px;
}

.sidebar-card.compact {
  padding-top: 14px;
}

.sidebar-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.sidebar-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.field-block {
  margin-top: 14px;
}

.field-block.slim {
  margin-top: 12px;
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

.composer-footer {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 10px;
  margin-top: 16px;
}

.composer-note {
  width: 100%;
  color: #71809c;
  line-height: 1.7;
}

.toggle-row {
  width: 100%;
  border: none;
  background: transparent;
  padding: 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: #22314f;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
}

.advanced-body,
.history-body {
  margin-top: 14px;
}

.context-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid #edf1f8;
}

.context-key {
  color: #75849d;
}

.context-value {
  color: #25314d;
  text-align: right;
}

.history-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.history-item {
  width: 100%;
  padding: 12px 14px;
  border: 1px solid #e4ebf7;
  border-radius: 18px;
  background: #f8fbff;
  text-align: left;
  cursor: pointer;
}

.history-item.active {
  border-color: #5c7ce2;
  background: #edf3ff;
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

.history-meta {
  margin-top: 6px;
}

.history-preview {
  margin-top: 4px;
}

@media (max-width: 1180px) {
  .debug-shell {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .stage-header,
  .sidebar-head {
    flex-direction: column;
  }

  .stage-pills,
  .sidebar-actions {
    justify-content: flex-start;
  }

  .debug-stage,
  .sidebar-card {
    padding: 16px;
  }

  .stage-title {
    font-size: 24px;
  }
}
</style>

<template>
  <el-dialog
    :title="dialogTitle"
    :visible.sync="dialogVisible"
    width="86%"
    :before-close="handleClose"
    custom-class="openclaw-debug-dialog"
  >
    <div class="debug-dialog-shell">
      <aside class="debug-sidebar">
        <div class="sidebar-card primary">
          <div class="sidebar-eyebrow">Debug Context</div>
          <div class="sidebar-title">{{ channelName }}</div>
          <div class="sidebar-meta">Channel ID：{{ channelId || "未选择" }}</div>
        </div>

        <div class="sidebar-card">
          <div class="sidebar-section-title">当前状态</div>
          <div class="sidebar-stat-list">
            <div class="sidebar-stat">
              <span class="stat-label">Runtime / Account</span>
              <span class="stat-value">{{ runtimeAccountCount }}</span>
            </div>
            <div class="sidebar-stat">
              <span class="stat-label">Bridge</span>
              <span class="stat-value">{{ bridgeCount }}</span>
            </div>
            <div class="sidebar-stat">
              <span class="stat-label">OpenClaw Agent</span>
              <span class="stat-value">{{ agentCount }}</span>
            </div>
          </div>
        </div>

        <div class="sidebar-card">
          <div class="sidebar-section-title">调试步骤</div>
          <div class="sidebar-step-list">
            <div class="sidebar-step">1. 选择 runtime/account 与 agent</div>
            <div class="sidebar-step">2. 按需固定 bridge 或新建会话</div>
            <div class="sidebar-step">3. 发送测试消息验证路由与回复</div>
          </div>
        </div>

        <div class="sidebar-card">
          <div class="sidebar-section-title">调试历史</div>
          <div v-if="debugHistorySessions.length" class="history-list">
            <button
              v-for="item in debugHistorySessions"
              :key="item.sessionId"
              class="history-item"
              :class="{ active: item.sessionId === debugForm.debugSessionId }"
              @click="restoreDebugHistory(item)"
            >
              <div class="history-item-head">
                <span class="history-session">{{ item.sessionId }}</span>
                <span class="history-time">{{ item.updatedAtText }}</span>
              </div>
              <div class="history-item-meta">{{ item.account || "-" }} / {{ item.agentName || item.agentId || "-" }}</div>
              <div class="history-item-preview">{{ item.preview || "暂无预览" }}</div>
            </button>
          </div>
          <div v-else class="sidebar-hint">当前 channel 还没有调试历史。发送一次消息后会自动保存在本地。</div>
        </div>

        <div class="sidebar-card hint">
          <div class="sidebar-section-title">说明</div>
          <div class="sidebar-hint">
            这里直接走后台到 OpenClaw bridge 的 RPC，不依赖在线设备。适合验证 channel 路由、agent 选择和回复文本。
          </div>
        </div>
      </aside>

      <section class="debug-main">
        <el-alert
          v-if="!channelId"
          title="请先保存并选择一个 Channel，再开始在线调试。"
          type="info"
          :closable="false"
          show-icon
          class="top-alert"
        />
        <el-alert
          v-else-if="!runtimeAccountCount"
          title="当前还没有可用的 runtime/account，请先执行安装命令并同步 inventory。"
          type="warning"
          :closable="false"
          show-icon
          class="top-alert"
        />

        <div class="debug-toolbar">
          <el-select
            v-model="debugForm.account"
            class="debug-select"
            filterable
            :disabled="!runtimeAccountCount"
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
          <el-select
            v-model="debugForm.bridgeId"
            class="debug-select"
            clearable
            filterable
            :disabled="!bridgeOptions.length"
            placeholder="指定 bridge（可选）"
          >
            <el-option
              v-for="item in bridgeOptions"
              :key="item.bridgeId"
              :label="`${item.name || item.bridgeId} · ${item.connected ? '在线' : '离线'}`"
              :value="item.bridgeId"
            />
          </el-select>
          <el-select
            v-model="debugForm.agentId"
            class="debug-select"
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
          <el-input
            v-model="debugForm.speaker"
            class="debug-speaker-input"
            maxlength="40"
            placeholder="说话人标签，可选"
          />
        </div>

        <div class="debug-session-bar">
          <div class="debug-session-meta">
            <span>会话：{{ debugForm.debugSessionId }}</span>
            <span v-if="debugForm.account">Account：{{ debugForm.account }}</span>
            <span v-if="debugForm.agentName || debugForm.agentId">Agent：{{ debugForm.agentName || debugForm.agentId }}</span>
          </div>
          <div class="session-actions">
            <el-button size="small" @click="clearDebugTranscript">清空记录</el-button>
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
            <el-button size="small" type="primary" plain @click="createDebugSession">新建会话</el-button>
          </div>
        </div>

        <div class="debug-chat-shell">
          <div ref="transcript" class="debug-transcript">
            <div v-if="debugMessages.length" class="debug-message-list">
              <div
                v-for="item in debugMessages"
                :key="item.id"
                class="debug-message"
                :class="`role-${item.role}`"
              >
                <div class="debug-message-head">
                  <span class="debug-role">
                    {{
                      item.role === "user"
                        ? "后台输入"
                        : item.role === "assistant"
                          ? "OpenClaw 返回"
                          : "系统信息"
                    }}
                  </span>
                  <span v-if="item.meta" class="debug-meta">{{ item.meta }}</span>
                </div>
                <div class="debug-message-body">{{ item.text }}</div>
              </div>
            </div>
            <el-empty v-else description="发送一条消息，直接测试当前 OpenClaw agent。" :image-size="90" />
          </div>

          <div class="debug-composer">
            <div class="composer-head">
              <div class="composer-title">测试输入</div>
              <div class="composer-hint">按 Ctrl + Enter 可快速发送</div>
            </div>
            <el-input
              v-model="debugForm.inputText"
              type="textarea"
              :rows="8"
              resize="none"
              placeholder="输入要发送给 OpenClaw 的测试消息。"
              @keyup.ctrl.enter.native="sendDirectChat"
            />
            <div class="debug-composer-actions">
              <span class="composer-note">消息只在后台调试链路内流转，不会推给 ESP32 设备。</span>
              <el-button type="primary" :loading="debugSending" :disabled="!canSendDirectChat" @click="sendDirectChat">
                发送测试消息
              </el-button>
            </div>
          </div>
        </div>
      </section>
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
    };
  },
  computed: {
    channelId() {
      return this.channel && this.channel.id ? this.channel.id : "";
    },
    channelName() {
      if (this.channel && this.channel.name) {
        return this.channel.name;
      }
      return "未选择 Channel";
    },
    dialogTitle() {
      return `OpenClaw 在线调试台${this.channelId ? ` · ${this.channelName}` : ""}`;
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
    runtimeAccountCount() {
      return this.runtimeAccounts.length;
    },
    bridgeCount() {
      return this.bridgeItems.length;
    },
    agentCount() {
      return this.agentItems.length;
    },
    bridgeOptions() {
      if (!this.debugForm.account) {
        return this.bridgeItems;
      }
      return this.bridgeItems.filter((item) => item.account === this.debugForm.account);
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
    },
    getHistoryStorageKey() {
      return `${DEBUG_HISTORY_PREFIX}${this.channelId || "unknown"}`;
    },
    loadDebugHistory() {
      if (!this.channelId || typeof window === "undefined") {
        this.debugHistorySessions = [];
        return;
      }
      const items = safeParseHistory(window.localStorage.getItem(this.getHistoryStorageKey()))
        .map((item) => ({
          ...item,
          updatedAtText: formatHistoryTime(item.updatedAt),
        }))
        .filter((item) => item && item.sessionId);
      this.debugHistorySessions = items;
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
      const entry = {
        sessionId,
        account: this.debugForm.account,
        bridgeId: this.debugForm.bridgeId,
        agentId: this.debugForm.agentId,
        agentName: this.debugForm.agentName,
        preview: (lastMessage.text || "").slice(0, 72),
        updatedAt: Date.now(),
        updatedAtText: formatHistoryTime(Date.now()),
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
    syncDebugAgent() {
      const agentOptions = this.currentDebugAgentOptions;
      if (!agentOptions.some((item) => item.value === this.debugForm.agentId)) {
        const firstAgent = agentOptions[0];
        this.debugForm.agentId = firstAgent ? firstAgent.value : "";
        this.debugForm.agentName = firstAgent ? firstAgent.label : "";
        return;
      }
      this.debugForm.agentName = this.findOptionLabel(
        agentOptions,
        this.debugForm.agentId,
        this.debugForm.agentName || this.debugForm.agentId
      );
    },
    applyDebugDefaults() {
      if (!this.channelId) {
        this.debugForm.account = "";
        this.debugForm.bridgeId = "";
        this.debugForm.agentId = "";
        this.debugForm.agentName = "";
        return;
      }
      if (!this.runtimeAccounts.length) {
        this.debugForm.account = "";
        this.debugForm.bridgeId = "";
        this.debugForm.agentId = "";
        this.debugForm.agentName = "";
        return;
      }

      if (!this.routePrefillApplied) {
        const matchedAccount = this.runtimeAccounts.find((item) => item.value === this.routePrefill.runtimeAccount);
        if (matchedAccount) {
          this.debugForm.account = matchedAccount.value;
        }
      }

      if (!this.runtimeAccounts.some((item) => item.value === this.debugForm.account)) {
        this.debugForm.account = this.runtimeAccounts[0].value;
      }

      this.syncDebugBridge();
      const currentAgents = this.currentDebugAgentOptions;
      if (!this.routePrefillApplied) {
        const matchedAgent = currentAgents.find((item) => item.value === this.routePrefill.openclawAgentId);
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
      const currentBridge = this.bridgeOptions.find((item) => item.bridgeId === this.debugForm.bridgeId);
      const preferredBridge = this.bridgeOptions.find((item) => item.connected) || this.bridgeOptions[0];
      if (!preferredBridge) {
        this.debugForm.bridgeId = "";
        return;
      }
      if (currentBridge && (currentBridge.connected || !preferredBridge.connected)) {
        return;
      }
      this.debugForm.bridgeId = preferredBridge.bridgeId;
    },
    handleDebugAccountChange(value) {
      this.debugForm.account = value;
      this.syncDebugBridge();
      this.syncDebugAgent();
    },
    handleDebugAgentChange(value) {
      this.debugForm.agentId = value;
      this.debugForm.agentName = this.findOptionLabel(
        this.currentDebugAgentOptions,
        value,
        this.debugForm.agentName || value
      );
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
    clearDebugTranscript() {
      this.debugMessages = [];
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
        this.$message.warning("当前没有可清理的 OpenClaw 调试会话");
        return;
      }
      this.$confirm("清空当前 OpenClaw 调试会话后，将移除本次会话上下文并开始新的会话。是否继续？", "提示", {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning",
      }).then(() => {
        this.debugClearing = true;
        const payload = {
          account: this.debugForm.account,
          bridgeId: this.debugForm.bridgeId,
          sessionId: this.debugForm.debugSessionId,
          allowLatest: false,
        };
        Api.openclaw.clearSession(this.channelId, payload, ({ data }) => {
          this.debugClearing = false;
          if (data.code === 0) {
            const clearedSessionId = this.debugForm.debugSessionId;
            this.appendDebugMessage("system", `已清空 OpenClaw 调试会话：${clearedSessionId}`);
            this.rotateDebugSession(true);
            this.$message.success("OpenClaw 调试会话已清空");
            return;
          }
          const errorMessage = data.msg || "清空 OpenClaw 调试会话失败";
          this.appendDebugMessage("system", errorMessage);
          this.$message.error(errorMessage);
        }, ({ data }) => {
          this.debugClearing = false;
          const errorMessage = (data && data.msg) || "清空 OpenClaw 调试会话失败";
          this.appendDebugMessage("system", errorMessage);
          this.$message.error(errorMessage);
        });
      }).catch(() => {});
    },
    sendDirectChat() {
      if (!this.canSendDirectChat) {
        this.$message.warning("请先选择 runtime/account、OpenClaw Agent，并填写测试消息");
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
        meta: `${this.debugForm.account} / ${this.debugForm.agentName || this.debugForm.agentId}`,
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
          const replyText = response.replyText || "OpenClaw 已处理，但没有返回文本";
          const metaParts = [response.account, response.agentName || response.agentId].filter(Boolean);
          this.appendDebugMessage("assistant", replyText, {
            meta: metaParts.join(" / "),
          });
          return;
        }
        this.appendDebugMessage("system", data.msg || "OpenClaw 在线调试失败");
        this.$message.error(data.msg || "OpenClaw 在线调试失败");
      }, ({ data }) => {
        this.debugSending = false;
        const errorMessage = (data && data.msg) || "OpenClaw 在线调试失败";
        this.appendDebugMessage("system", errorMessage);
        this.$message.error(errorMessage);
      });
    },
  },
};
</script>

<style scoped>
.debug-dialog-shell {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: 0;
  height: 100%;
  background: #f7f9fd;
}

.debug-sidebar {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 20px;
  border-right: 1px solid #e7ebf4;
  background: linear-gradient(180deg, #f2f5fd 0%, #eef3fb 100%);
}

.sidebar-card {
  padding: 18px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid #e4e9f5;
}

.sidebar-card.primary {
  background: linear-gradient(135deg, #22304f 0%, #395a9a 100%);
  border: none;
  color: #fff;
}

.sidebar-card.hint {
  background: #fffaf0;
  border-color: #f1dfb2;
}

.sidebar-eyebrow {
  font-size: 12px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  opacity: 0.72;
}

.sidebar-title {
  margin-top: 10px;
  font-size: 22px;
  font-weight: 700;
  line-height: 1.35;
}

.sidebar-meta {
  margin-top: 8px;
  font-size: 13px;
  opacity: 0.78;
  word-break: break-all;
}

.sidebar-section-title {
  font-size: 14px;
  font-weight: 700;
  color: #24324a;
}

.sidebar-stat-list,
.sidebar-step-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 14px;
}

.sidebar-stat,
.sidebar-step {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 12px;
  background: #f8faff;
  color: #44506a;
}

.sidebar-step {
  display: block;
  line-height: 1.6;
}

.stat-label {
  color: #6a7590;
}

.stat-value {
  font-weight: 700;
  color: #24324a;
}

.sidebar-hint {
  margin-top: 12px;
  color: #6d5c33;
  line-height: 1.7;
}

.history-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 14px;
}

.history-item {
  width: 100%;
  padding: 12px 14px;
  border: 1px solid #e2e8f4;
  border-radius: 14px;
  background: #f8fbff;
  text-align: left;
  transition: border-color 0.2s ease, transform 0.2s ease, box-shadow 0.2s ease;
}

.history-item:hover {
  border-color: #b8c8ef;
  transform: translateY(-1px);
}

.history-item.active {
  border-color: #5c7ce2;
  box-shadow: 0 8px 18px rgba(92, 124, 226, 0.12);
  background: #eef3ff;
}

.history-item-head {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: center;
}

.history-session {
  font-size: 12px;
  font-weight: 700;
  color: #29407d;
}

.history-time,
.history-item-meta,
.history-item-preview {
  font-size: 12px;
  line-height: 1.5;
  color: #6d7892;
}

.history-item-meta,
.history-item-preview {
  margin-top: 6px;
}

.history-item-preview {
  color: #33425f;
}

.debug-main {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: 20px;
  background: #fbfcfe;
}

.debug-toolbar {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.debug-select,
.debug-speaker-input {
  width: 100%;
}

.debug-session-bar {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  margin-top: 16px;
  padding: 14px 16px;
  border-radius: 18px;
  background: #f1f5fd;
  border: 1px solid #e3e9f6;
}

.debug-session-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  color: #55627c;
  font-size: 13px;
}

.session-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.debug-chat-shell {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(320px, 0.85fr);
  gap: 18px;
  margin-top: 16px;
  min-height: 0;
  flex: 1;
}

.debug-transcript,
.debug-composer {
  min-height: 480px;
  padding: 18px;
  border-radius: 22px;
  background: #ffffff;
  border: 1px solid #e6ecf8;
}

.debug-transcript {
  overflow-y: auto;
  background:
    radial-gradient(circle at top right, rgba(91, 114, 255, 0.08), transparent 30%),
    #ffffff;
}

.debug-message-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.debug-message {
  padding: 14px 16px;
  border-radius: 18px;
  background: #ffffff;
  border: 1px solid #e6ecfa;
  box-shadow: 0 8px 20px rgba(35, 49, 83, 0.05);
}

.debug-message.role-user {
  background: #eef4ff;
}

.debug-message.role-system {
  background: #fff7eb;
  border-color: #f8dfb4;
}

.debug-message-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  margin-bottom: 8px;
}

.debug-role {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: #385a9a;
}

.debug-meta {
  font-size: 12px;
  color: #7c88a2;
}

.debug-message-body {
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.7;
  color: #25324d;
}

.debug-composer {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 16px;
  background:
    linear-gradient(180deg, #ffffff 0%, #f9fbff 100%);
}

.composer-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: baseline;
}

.composer-title {
  font-size: 18px;
  font-weight: 700;
  color: #24324a;
}

.composer-hint,
.composer-note {
  color: #6f7c95;
  font-size: 13px;
}

.debug-composer-actions {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.top-alert {
  margin-bottom: 16px;
}

@media (max-width: 1280px) {
  .debug-dialog-shell,
  .debug-chat-shell,
  .debug-toolbar {
    grid-template-columns: 1fr;
  }

  .debug-sidebar {
    border-right: none;
    border-bottom: 1px solid #e7ebf4;
  }

  .debug-session-bar,
  .debug-composer-actions {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>

<style>
.openclaw-debug-dialog {
  display: flex;
  flex-direction: column;
  min-width: 900px;
  margin: 0 !important;
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  height: 94vh;
  max-width: 92vw;
  border-radius: 18px;
  overflow: hidden;
}

.openclaw-debug-dialog .el-dialog__header {
  background: linear-gradient(180deg, #eef3ff 0%, #f6f8fd 100%);
  padding: 18px 22px;
  border-bottom: 1px solid #e5eaf5;
}

.openclaw-debug-dialog .el-dialog__title {
  font-size: 18px;
  font-weight: 700;
  color: #24324a;
}

.openclaw-debug-dialog .el-dialog__body {
  padding: 0;
  overflow: hidden;
  height: calc(94vh - 60px);
}
</style>

<template>
  <section class="detail-shell">
    <div class="detail-topbar">
      <div class="detail-header">
        <div class="detail-title-block">
          <el-button class="back-btn" type="text" @click="$emit('back')">
            <i class="el-icon-arrow-left" />
            返回 Channel 列表
          </el-button>
          <div class="detail-eyebrow">当前渠道</div>
          <h2 class="detail-title">{{ channel.name || "未命名 Channel" }}</h2>
          <p class="detail-description">
            先从下方选择 Agent，再调试。
          </p>
        </div>

        <div class="detail-pills">
          <div class="detail-pill">
            <span>可调试 Agent</span>
            <strong>{{ agentCards.length }}</strong>
          </div>
          <div class="detail-pill">
            <span>业务绑定</span>
            <strong>{{ filteredBindings.length }}</strong>
          </div>
          <div class="detail-pill">
            <span>当前 Runtime</span>
            <strong>{{ selectedRuntimeLabel || "自动选择" }}</strong>
          </div>
          <el-tag size="medium" :type="inventoryTagType" effect="plain">
            {{ inventoryStatusText }}
          </el-tag>
        </div>
      </div>
    </div>

    <el-alert
      v-if="inventory.errorMessage"
      class="detail-alert"
      type="warning"
      :closable="false"
      show-icon
      :title="inventory.errorMessage"
    />

    <div class="workspace-grid">
      <div class="agents-panel" v-loading="inventoryLoading || bindingsLoading">
        <div class="panel-head">
          <div>
            <div class="panel-eyebrow">OpenClaw Agents</div>
            <h3 class="panel-title">可调试 Agent</h3>
          </div>
          <el-select
            v-if="showRuntimeSelector"
            :value="selectedRuntimeAccount"
            class="runtime-select"
            filterable
            placeholder="选择 runtime/account"
            @input="$emit('change-runtime', $event)"
          >
            <el-option
              v-for="item in runtimeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </div>

        <div v-if="agentCards.length" class="agent-grid">
          <article
            v-for="agent in agentCards"
            :key="agent.value"
            class="agent-card"
            :class="{ ghost: agent.isGhost }"
          >
            <div class="agent-card-head">
              <div>
                <div class="agent-name">{{ agent.label }}</div>
                <div class="agent-id">{{ agent.value }}</div>
              </div>
              <el-tag size="mini" :type="agent.bindingCount ? 'success' : 'info'">
                {{ agent.bindingCount ? `${agent.bindingCount} 个绑定` : "未绑定" }}
              </el-tag>
            </div>

            <p class="agent-copy">
              {{ agent.isGhost ? "inventory 里还没看到这个 Agent。" : "从这里直接调试。" }}
            </p>

            <div class="agent-actions">
              <el-button size="mini" type="primary" @click="emitOpenDebug(agent)">开始调试</el-button>
              <el-button
                size="mini"
                plain
                :disabled="!agent.bindingCount"
                @click="toggleBindings(agent.value)"
              >
                {{ expandedBindings[agent.value] ? "收起绑定" : "查看绑定" }}
              </el-button>
            </div>

            <el-collapse-transition>
              <div v-if="expandedBindings[agent.value]" class="binding-list">
                <div
                  v-for="binding in agent.bindings"
                  :key="`${agent.value}-${binding.agentId}`"
                  class="binding-item"
                >
                  <div>
                    <div class="binding-name">{{ binding.agentName || binding.agentId }}</div>
                    <div class="binding-meta">
                      {{ binding.runtimeAccountLabel || binding.runtimeAccount || "默认 Runtime" }}
                    </div>
                  </div>
                  <div class="binding-item-actions">
                    <el-tag size="mini" :type="binding.syncStatus === 'connected' ? 'success' : 'info'">
                      {{ binding.syncStatus || "configured" }}
                    </el-tag>
                    <el-button size="mini" type="text" @click="$emit('open-binding-agent', binding)">查看 Agent</el-button>
                    <el-button size="mini" type="text" @click="$emit('open-binding-debug', binding)">调试</el-button>
                  </div>
                </div>
              </div>
            </el-collapse-transition>
          </article>
        </div>

        <div v-else class="agent-empty">
          <div class="agent-empty-box">
            <el-empty description="当前没有可调试 Agent" :image-size="72" />
            <p class="agent-empty-copy">先让 OpenClaw 上报 inventory，再回来选 Agent。</p>
          </div>
        </div>

        <div class="panel-footer">
          <el-button @click="$emit('edit', channel)">编辑</el-button>
          <el-button @click="commandDialogVisible = true">接入命令</el-button>
          <el-button plain @click="diagnosticsDialogVisible = true">排障信息</el-button>
          <el-button type="primary" :loading="inventoryLoading" @click="$emit('refresh-inventory')">
            {{ agentCards.length ? "同步 Inventory" : "重新同步" }}
          </el-button>
        </div>
      </div>
    </div>

    <el-dialog
      title="接入命令"
      :visible.sync="commandDialogVisible"
      width="760px"
      custom-class="detail-dialog"
    >
      <div class="dialog-copy">新增或重新接入时，再执行这条命令。</div>
      <div class="command-box">{{ setupGuide.installCommand || "请先保存并生成安装命令。" }}</div>
      <span slot="footer" class="dialog-footer">
        <el-button @click="commandDialogVisible = false">关闭</el-button>
        <el-button type="primary" :disabled="!setupGuide.installCommand" @click="$emit('copy-command')">复制命令</el-button>
      </span>
    </el-dialog>

    <el-dialog
      title="排障信息"
      :visible.sync="diagnosticsDialogVisible"
      width="760px"
      custom-class="detail-dialog"
    >
      <div class="dialog-copy">只有联调失败时再看这些基础信息。</div>
      <div class="diagnostics-body dialog-diagnostics">
        <div class="diagnostic-row">
          <span class="diagnostic-key">Source URL</span>
          <span class="diagnostic-value">{{ inventory.sourceUrl || "尚未同步 inventory" }}</span>
        </div>
        <div class="diagnostic-row">
          <span class="diagnostic-key">Runtime / Account</span>
          <div class="diagnostic-chip-list">
            <span v-for="item in runtimeOptions" :key="item.value" class="diagnostic-chip">
              {{ item.label }}
            </span>
            <span v-if="!runtimeOptions.length" class="diagnostic-empty">暂无 runtime</span>
          </div>
        </div>
        <div class="diagnostic-row bridges">
          <span class="diagnostic-key">Bridge</span>
          <div class="bridge-list">
            <div v-for="bridge in visibleBridges" :key="bridge.bridgeId" class="bridge-item">
              <div class="bridge-name">{{ bridge.name || bridge.bridgeId }}</div>
              <div class="bridge-meta">{{ bridge.bridgeId }}</div>
            </div>
            <span v-if="!visibleBridges.length" class="diagnostic-empty">当前 runtime 下没有可见 bridge</span>
          </div>
        </div>
      </div>
      <span slot="footer" class="dialog-footer">
        <el-button @click="diagnosticsDialogVisible = false">关闭</el-button>
      </span>
    </el-dialog>
  </section>
</template>

<script>
export default {
  name: "OpenClawChannelDetail",
  props: {
    channel: {
      type: Object,
      default: () => ({}),
    },
    inventory: {
      type: Object,
      default: () => ({}),
    },
    bindings: {
      type: Array,
      default: () => [],
    },
    setupGuide: {
      type: Object,
      default: () => ({}),
    },
    inventoryLoading: {
      type: Boolean,
      default: false,
    },
    bindingsLoading: {
      type: Boolean,
      default: false,
    },
    selectedRuntimeAccount: {
      type: String,
      default: "",
    },
  },
  data() {
    return {
      commandDialogVisible: false,
      diagnosticsDialogVisible: false,
      expandedBindings: {},
    };
  },
  computed: {
    runtimeOptions() {
      return Array.isArray(this.inventory.runtimeAccounts) ? this.inventory.runtimeAccounts : [];
    },
    showRuntimeSelector() {
      return this.runtimeOptions.length > 1;
    },
    selectedRuntimeLabel() {
      const matched = this.runtimeOptions.find((item) => item.value === this.selectedRuntimeAccount);
      return matched ? matched.label : "";
    },
    filteredBindings() {
      const list = Array.isArray(this.bindings) ? this.bindings : [];
      if (!this.selectedRuntimeAccount) {
        return list;
      }
      return list.filter((item) => !item.runtimeAccount || item.runtimeAccount === this.selectedRuntimeAccount);
    },
    currentInventoryAgents() {
      const accountKey = this.selectedRuntimeAccount;
      const accountAgents = this.inventory.accountAgents && this.inventory.accountAgents[accountKey];
      if (Array.isArray(accountAgents) && accountAgents.length) {
        return accountAgents;
      }
      return Array.isArray(this.inventory.agents) ? this.inventory.agents : [];
    },
    agentCards() {
      const cards = {};
      this.currentInventoryAgents.forEach((item) => {
        cards[item.value] = {
          value: item.value,
          label: item.label || item.value,
          bindings: [],
          bindingCount: 0,
          isGhost: false,
        };
      });

      this.filteredBindings.forEach((binding) => {
        const key = binding.openclawAgentId || `binding:${binding.agentId}`;
        if (!cards[key]) {
          cards[key] = {
            value: key,
            label: binding.openclawAgentName || binding.openclawAgentId || "未命名 OpenClaw Agent",
            bindings: [],
            bindingCount: 0,
            isGhost: true,
          };
        }
        cards[key].bindings.push(binding);
        cards[key].bindingCount += 1;
      });

      return Object.values(cards).sort((left, right) => {
        if (left.bindingCount !== right.bindingCount) {
          return right.bindingCount - left.bindingCount;
        }
        return String(left.label).localeCompare(String(right.label));
      });
    },
    visibleBridges() {
      const bridges = Array.isArray(this.inventory.bridges) ? this.inventory.bridges : [];
      if (!this.selectedRuntimeAccount) {
        return bridges;
      }
      return bridges.filter((item) => !item.account || item.account === this.selectedRuntimeAccount);
    },
    inventoryStatusText() {
      if (this.inventoryLoading) {
        return "同步中";
      }
      if (this.inventory && this.inventory.healthy) {
        return "已就绪";
      }
      if (this.inventory && this.inventory.errorMessage) {
        return "需检查";
      }
      return "未同步";
    },
    inventoryNoteText() {
      if (this.inventoryLoading) {
        return "正在同步 inventory";
      }
      const connected = this.inventory.connectedBridgeCount || 0;
      if (this.inventory && this.inventory.healthy) {
        return connected > 0 ? `${connected} 个在线设备` : "已连通，但还没有在线设备";
      }
      if (this.inventory && this.inventory.errorMessage) {
        return this.inventory.errorMessage;
      }
      return "还没同步 inventory";
    },
    inventoryStatusClass() {
      if (this.inventoryLoading) {
        return "is-loading";
      }
      if (this.inventory && this.inventory.healthy) {
        return "is-healthy";
      }
      if (this.inventory && this.inventory.errorMessage) {
        return "is-warning";
      }
      return "is-idle";
    },
    inventoryTagType() {
      if (this.inventoryLoading) {
        return "info";
      }
      if (this.inventory && this.inventory.healthy) {
        return "success";
      }
      if (this.inventory && this.inventory.errorMessage) {
        return "warning";
      }
      return "info";
    },
  },
  methods: {
    toggleBindings(agentId) {
      this.$set(this.expandedBindings, agentId, !this.expandedBindings[agentId]);
    },
    emitOpenDebug(agent) {
      this.$emit("open-debug", {
        runtimeAccount: this.selectedRuntimeAccount,
        openclawAgentId: agent.value,
        openclawAgentName: agent.label,
      });
    },
  },
};
</script>

<style scoped>
.detail-shell {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 28px;
  flex-wrap: wrap;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid #e4ebf7;
  box-shadow: 0 18px 40px rgba(124, 140, 177, 0.08);
  border-radius: 30px;
  padding: 28px 24px;
}

.back-btn {
  padding: 0;
  color: #4662a8;
  margin-bottom: 8px;
}

.detail-eyebrow,
.panel-eyebrow {
  font-size: 12px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: #7c8ba7;
}

.detail-title {
  margin: 8px 0 4px;
  font-size: 28px;
  line-height: 1.2;
  color: #18233d;
}

.detail-description {
  max-width: 560px;
  margin: 0;
  color: #66758f;
  line-height: 1.5;
}

.detail-title-block {
  max-width: 560px;
  flex: 1;
  min-width: 280px;
}

.detail-pills {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: flex-end;
  align-items: flex-start;
  flex: 0 0 auto;
}

.detail-pill,
.agents-panel {
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid #e4ebf7;
  box-shadow: 0 18px 40px rgba(124, 140, 177, 0.08);
}

.detail-pill {
  display: flex;
  min-width: 136px;
  flex-direction: column;
  gap: 4px;
  padding: 10px 12px;
  border-radius: 16px;
}

.detail-pill span,
.diagnostic-key {
  display: block;
  color: #7b89a2;
  text-transform: uppercase;
  letter-spacing: 0.12em;
  font-size: 12px;
  font-size: 11px;
}

.detail-pill strong {
  display: block;
  font-size: 16px;
  color: #1a2640;
  line-height: 1.35;
  word-break: break-word;
}

.runtime-select {
  width: 180px;
}

.detail-alert {
  border-radius: 20px;
}

.workspace-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 18px;
  align-items: start;
}

.agents-panel {
  padding: 24px;
  min-height: 360px;
  border-radius: 30px;
  display: flex;
  flex-direction: column;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 18px;
}

.panel-title {
  margin: 8px 0 0;
  font-size: 24px;
  color: #1a2640;
}

.agent-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 16px;
  margin-top: 18px;
}

.agent-card {
  padding: 20px;
  border-radius: 24px;
  background: linear-gradient(180deg, #ffffff, #f8fbff);
  border: 1px solid #e3eaf7;
}

.agent-card.ghost {
  background: linear-gradient(180deg, #fffaf4, #fffdf9);
  border-color: #f0dcc0;
}

.agent-card-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.agent-name {
  font-size: 20px;
  color: #19253e;
  font-weight: 700;
}

.agent-id {
  margin-top: 6px;
  color: #7d8ca8;
  font-size: 13px;
  word-break: break-all;
}

.agent-copy {
  min-height: 48px;
  margin: 14px 0 0;
  color: #64748f;
  line-height: 1.7;
}

.agent-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 16px;
}

.agent-empty {
  display: flex;
  min-height: 180px;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
}

.agent-empty-copy {
  margin: 8px 0 0;
  color: #6f7f9a;
}

.panel-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: auto;
  padding-top: 18px;
}

.binding-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #e8edf7;
}

.binding-item {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 18px;
  background: #f6f9ff;
}

.binding-name {
  color: #1d2843;
  font-weight: 700;
}

.binding-meta {
  margin-top: 4px;
  color: #74839f;
  font-size: 13px;
}

.binding-item-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.command-box {
  padding: 16px;
  border-radius: 20px;
  background: #0f1729;
  color: #e8eefb;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-all;
}

.diagnostics-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.diagnostic-row {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.diagnostic-value,
.diagnostic-empty {
  color: #41506b;
  line-height: 1.7;
  word-break: break-all;
}

.diagnostic-chip-list,
.bridge-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.diagnostic-chip,
.bridge-item {
  padding: 10px 12px;
  border-radius: 16px;
  background: #f3f6fd;
  border: 1px solid #e3e9f6;
}

.bridge-name {
  color: #1e2944;
  font-weight: 700;
}

.bridge-meta {
  margin-top: 4px;
  color: #72819b;
  font-size: 12px;
}

.dialog-copy {
  margin-bottom: 16px;
  color: #6c7b95;
  line-height: 1.7;
}

.dialog-diagnostics {
  margin-top: 0;
}

::v-deep .detail-dialog {
  border-radius: 28px;
}

::v-deep .detail-dialog .el-dialog__body {
  padding-top: 8px;
}

@media (max-width: 1180px) {
  .detail-header {
    flex-direction: column;
  }

  .detail-pills {
    width: 100%;
    justify-content: flex-start;
    padding-top: 8px;
    border-top: 1px solid #f0f4fb;
  }
}

@media (max-width: 720px) {
  .agents-panel {
    padding: 18px;
  }

  .detail-title {
    font-size: 30px;
  }

  .agent-grid {
    grid-template-columns: 1fr;
  }

  .binding-item {
    flex-direction: column;
    align-items: flex-start;
  }

  .binding-item-actions {
    justify-content: flex-start;
  }

  .panel-footer {
    justify-content: flex-start;
  }
}
</style>

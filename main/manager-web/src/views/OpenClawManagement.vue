<template>
  <div class="welcome">
    <HeaderBar />

    <div class="main-wrapper">
      <div class="content-panel">
        <div
          class="content-area"
          v-loading="loading"
          element-loading-text="正在加载 OpenClaw Channel"
          element-loading-spinner="el-icon-loading"
          element-loading-background="rgba(255, 255, 255, 0.72)"
        >
          <OpenClawChannelRegistry
            v-if="!hasSelectedChannel"
            :channels="channels"
            :summaries="channelSummaries"
            :loading="loading"
            @back="goToRoleConfig"
            @create="openCreateChannel"
            @refresh="loadChannels"
            @select="selectChannel"
            @edit="openEditChannel"
            @delete="removeChannel"
          />

          <OpenClawChannelDetail
            v-else
            :channel="selectedChannel"
            :inventory="inventory"
            :bindings="channelBindings"
            :setup-guide="setupGuide"
            :inventory-loading="inventoryLoading"
            :bindings-loading="bindingsLoading"
            :selected-runtime-account="selectedRuntimeAccount"
            @back="clearSelection"
            @edit="openEditChannel"
            @copy-command="copyInstallCommand"
            @refresh-inventory="refreshSelectedChannelData"
            @change-runtime="handleRuntimeChange"
            @open-debug="openDebugForAgent"
            @open-binding-agent="openBoundAgent"
            @open-binding-debug="openDebugForBinding"
          />
        </div>
      </div>
    </div>

    <OpenClawChannelEditorDialog
      :visible.sync="editorVisible"
      :channel="editorForm"
      :saving="saving"
      :is-edit-mode="editorMode === 'edit'"
      @save="saveChannel"
    />

    <OpenClawDebugDialog
      :visible.sync="showDebugDialog"
      :channel="selectedChannel"
      :inventory="inventory"
      :route-prefill="routePrefill"
    />

    <el-footer>
      <VersionFooter />
    </el-footer>
  </div>
</template>

<script>
import Api from "@/apis/api";
import HeaderBar from "@/components/HeaderBar.vue";
import OpenClawDebugDialog from "@/components/OpenClawDebugDialog.vue";
import VersionFooter from "@/components/VersionFooter.vue";
import OpenClawChannelDetail from "@/components/openclaw/OpenClawChannelDetail.vue";
import OpenClawChannelEditorDialog from "@/components/openclaw/OpenClawChannelEditorDialog.vue";
import OpenClawChannelRegistry from "@/components/openclaw/OpenClawChannelRegistry.vue";

const createEmptyChannel = () => ({
  id: "",
  name: "",
  baseUrl: "",
  inventoryPath: "/inventory",
  accessToken: "",
  enabled: true,
  remark: "",
});

const createEmptyInventory = () => ({
  channelId: "",
  sourceUrl: "",
  healthy: false,
  errorMessage: "",
  runtimeAccounts: [],
  agents: [],
  bridges: [],
  accountAgents: {},
  bridgeAgents: {},
  connectedBridgeCount: 0,
});

const createEmptySetupGuide = () => ({
  channelId: "",
  channelName: "",
  serverUrl: "",
  baseUrl: "",
  inventoryPath: "/inventory",
  defaultAgentId: "main",
  accessTokenConfigured: true,
  installCommand: "",
});

const createEmptyRoutePrefill = () => ({
  channelId: "",
  runtimeAccount: "",
  openclawAgentId: "",
  openclawAgentName: "",
  entry: "",
});

const createEmptySummary = () => ({
  loading: false,
  inventoryLabel: "未同步",
  inventoryTone: "idle",
  inventoryNote: "还没连上 OpenClaw",
  bindingCount: 0,
  agentCount: 0,
  runtimeCount: 0,
});

const stripHtml = (value = "") => value.replace(/<[^>]*>/g, " ").replace(/\s+/g, " ").trim();

const compactInventoryMessage = (message = "") => {
  const text = stripHtml(message);
  if (!text) {
    return "还没连上 OpenClaw";
  }
  const lower = text.toLowerCase();
  if (lower.includes("404")) {
    return "OpenClaw inventory 接口不存在";
  }
  if (lower.includes("401") || lower.includes("403")) {
    return "OpenClaw 鉴权失败";
  }
  if (lower.includes("timeout") || lower.includes("timed out")) {
    return "OpenClaw 响应超时";
  }
  if (lower.includes("connection refused")) {
    return "OpenClaw 服务未启动";
  }
  if (lower.includes("failed to connect") || lower.includes("connectexception") || lower.includes("i/o error")) {
    return "无法连接 OpenClaw";
  }
  return text.length > 46 ? `${text.slice(0, 46)}...` : text;
};

const normalizeInventoryPayload = (payload = {}, channelId = "") => ({
  ...createEmptyInventory(),
  ...payload,
  channelId,
  errorMessage: payload && payload.errorMessage
    ? compactInventoryMessage(payload.errorMessage)
    : "",
});

export default {
  name: "OpenClawManagement",
  components: {
    HeaderBar,
    OpenClawChannelDetail,
    OpenClawChannelEditorDialog,
    OpenClawChannelRegistry,
    OpenClawDebugDialog,
    VersionFooter,
  },
  data() {
    return {
      loading: false,
      saving: false,
      guideLoading: false,
      inventoryLoading: false,
      bindingsLoading: false,
      channels: [],
      channelSummaries: {},
      selectedChannelId: "",
      selectedRuntimeAccount: "",
      inventory: createEmptyInventory(),
      setupGuide: createEmptySetupGuide(),
      channelBindings: [],
      editorVisible: false,
      editorMode: "create",
      editorForm: createEmptyChannel(),
      showDebugDialog: false,
      routePrefill: createEmptyRoutePrefill(),
      routePrefillApplied: false,
      agentId: "",
    };
  },
  computed: {
    hasSelectedChannel() {
      return Boolean(this.selectedChannelId);
    },
    selectedChannel() {
      const matched = this.channels.find((item) => item.id === this.selectedChannelId);
      return matched || createEmptyChannel();
    },
  },
  watch: {
    "$route.query": {
      immediate: true,
      handler(query) {
        const routeQuery = query || {};
        this.agentId = routeQuery.agentId || "";
        this.routePrefill = {
          channelId: routeQuery.channelId || "",
          runtimeAccount: routeQuery.runtimeAccount || "",
          openclawAgentId: routeQuery.openclawAgentId || "",
          openclawAgentName: routeQuery.openclawAgentName || "",
          entry: routeQuery.entry || "",
        };
        this.routePrefillApplied = false;
        if (this.channels.length && this.routePrefill.channelId) {
          const matched = this.channels.find((item) => item.id === this.routePrefill.channelId);
          if (matched) {
            this.selectChannel(matched, { force: true });
          }
        }
      },
    },
  },
  created() {
    this.loadChannels();
  },
  methods: {
    loadChannels(preferredChannelId = "") {
      this.loading = true;
      Api.openclaw.getChannels(({ data }) => {
        this.loading = false;
        if (data.code !== 0) {
          this.$message.error(data.msg || "获取 OpenClaw Channel 列表失败");
          return;
        }

        this.channels = Array.isArray(data.data) ? data.data : [];
        this.seedChannelSummaries();
        this.loadChannelSummaries();

        const targetId = preferredChannelId || this.routePrefill.channelId || this.selectedChannelId || "";
        if (targetId) {
          const matched = this.channels.find((item) => item.id === targetId);
          if (matched) {
            this.selectChannel(matched, {
              force: Boolean(preferredChannelId || this.routePrefill.channelId),
            });
            return;
          }
        }

        if (this.selectedChannelId && !this.channels.some((item) => item.id === this.selectedChannelId)) {
          this.clearSelection();
        }
      });
    },
    seedChannelSummaries() {
      const next = {};
      this.channels.forEach((channel) => {
        next[channel.id] = this.channelSummaries[channel.id] || createEmptySummary();
      });
      this.channelSummaries = next;
    },
    loadChannelSummaries() {
      this.channels.forEach((channel) => {
        this.updateChannelSummary(channel.id, { loading: true });
        this.fetchChannelSummary(channel.id);
      });
    },
    async fetchChannelSummary(channelId) {
      const [inventoryResult, bindingsResult] = await Promise.all([
        this.getChannelInventoryResult(channelId),
        this.getChannelBindingsResult(channelId),
      ]);

      const summary = createEmptySummary();
      summary.loading = false;
      summary.bindingCount = bindingsResult.ok ? bindingsResult.items.length : 0;

      if (inventoryResult.ok) {
        summary.runtimeCount = Array.isArray(inventoryResult.payload.runtimeAccounts)
          ? inventoryResult.payload.runtimeAccounts.length
          : 0;
        summary.agentCount = Array.isArray(inventoryResult.payload.agents)
          ? inventoryResult.payload.agents.length
          : 0;
        summary.inventoryLabel = inventoryResult.payload.healthy ? "可调试" : "未连通";
        summary.inventoryTone = inventoryResult.payload.healthy ? "healthy" : "attention";
        const connected = inventoryResult.payload.connectedBridgeCount || 0;
        summary.inventoryNote = inventoryResult.payload.healthy
          ? (connected > 0 ? `${connected} 个在线设备` : "暂无在线设备")
          : compactInventoryMessage(inventoryResult.payload.errorMessage);
      } else {
        summary.inventoryLabel = "未同步";
        summary.inventoryTone = "attention";
        summary.inventoryNote = compactInventoryMessage(inventoryResult.message);
      }

      this.updateChannelSummary(channelId, summary);
    },
    updateChannelSummary(channelId, patch) {
      const current = this.channelSummaries[channelId] || createEmptySummary();
      this.$set(this.channelSummaries, channelId, {
        ...current,
        ...patch,
      });
    },
    getChannelInventoryResult(channelId) {
      return new Promise((resolve) => {
        Api.openclaw.getChannelInventory(channelId, ({ data }) => {
          if (data.code === 0) {
            resolve({
              ok: true,
              payload: data.data || {},
              message: "",
            });
            return;
          }
          resolve({
            ok: false,
            payload: {},
            message: data.msg || "同步 OpenClaw inventory 失败",
          });
        }, ({ data }) => {
          resolve({
            ok: false,
            payload: {},
            message: (data && data.msg) || "同步 OpenClaw inventory 失败",
          });
        });
      });
    },
    getChannelBindingsResult(channelId) {
      return new Promise((resolve) => {
        Api.openclaw.getChannelBindings(channelId, ({ data }) => {
          if (data.code === 0) {
            resolve({
              ok: true,
              items: Array.isArray(data.data) ? data.data : [],
              message: "",
            });
            return;
          }
          resolve({
            ok: false,
            items: [],
            message: data.msg || "获取绑定关系失败",
          });
        }, ({ data }) => {
          resolve({
            ok: false,
            items: [],
            message: (data && data.msg) || "获取绑定关系失败",
          });
        });
      });
    },
    selectChannel(channel, options = {}) {
      if (!channel || !channel.id) {
        return;
      }
      const sameChannel = this.selectedChannelId === channel.id;
      if (sameChannel && !options.force) {
        if (this.editorVisible) {
          this.editorVisible = false;
        }
        return;
      }
      if (sameChannel && this.editorVisible) {
        this.editorVisible = false;
      }
      this.selectedChannelId = channel.id;
      this.refreshSelectedChannelData();
    },
    clearSelection() {
      this.selectedChannelId = "";
      this.selectedRuntimeAccount = "";
      this.inventory = createEmptyInventory();
      this.setupGuide = createEmptySetupGuide();
      this.channelBindings = [];
      this.showDebugDialog = false;
    },
    refreshSelectedChannelData() {
      this.refreshSetupGuide();
      this.syncSelectedInventory();
      this.loadSelectedBindings();
    },
    refreshSetupGuide() {
      if (!this.selectedChannelId) {
        this.setupGuide = createEmptySetupGuide();
        return;
      }
      this.guideLoading = true;
      Api.openclaw.getChannelSetupGuide(this.selectedChannelId, ({ data }) => {
        this.guideLoading = false;
        if (data.code === 0) {
          this.setupGuide = data.data || createEmptySetupGuide();
          return;
        }
        this.setupGuide = createEmptySetupGuide();
        this.$message.error(data.msg || "生成安装命令失败");
      }, ({ data }) => {
        this.guideLoading = false;
        this.setupGuide = createEmptySetupGuide();
        this.$message.error((data && data.msg) || "生成安装命令失败");
      });
    },
    syncSelectedInventory() {
      if (!this.selectedChannelId) {
        this.inventory = createEmptyInventory();
        return;
      }
      this.inventoryLoading = true;
      Api.openclaw.getChannelInventory(this.selectedChannelId, ({ data }) => {
        this.inventoryLoading = false;
        if (data.code === 0) {
          this.inventory = normalizeInventoryPayload(data.data || {}, this.selectedChannelId);
          this.syncRuntimeSelection();
          this.updateSummaryFromSelectedChannel();
          this.maybeOpenRoutedDebug();
          return;
        }
        this.inventory = {
          ...createEmptyInventory(),
          channelId: this.selectedChannelId,
          errorMessage: compactInventoryMessage(data.msg || "同步 OpenClaw inventory 失败"),
        };
        this.updateSummaryFromSelectedChannel();
      }, ({ data }) => {
        this.inventoryLoading = false;
        this.inventory = {
          ...createEmptyInventory(),
          channelId: this.selectedChannelId,
          errorMessage: compactInventoryMessage((data && data.msg) || "同步 OpenClaw inventory 失败"),
        };
        this.updateSummaryFromSelectedChannel();
      });
    },
    loadSelectedBindings() {
      if (!this.selectedChannelId) {
        this.channelBindings = [];
        return;
      }
      this.bindingsLoading = true;
      Api.openclaw.getChannelBindings(this.selectedChannelId, ({ data }) => {
        this.bindingsLoading = false;
        if (data.code === 0) {
          this.channelBindings = Array.isArray(data.data) ? data.data : [];
          this.updateSummaryFromSelectedChannel();
          return;
        }
        this.channelBindings = [];
        this.$message.error(data.msg || "获取绑定关系失败");
      }, ({ data }) => {
        this.bindingsLoading = false;
        this.channelBindings = [];
        this.$message.error((data && data.msg) || "获取绑定关系失败");
      });
    },
    syncRuntimeSelection() {
      const options = Array.isArray(this.inventory.runtimeAccounts) ? this.inventory.runtimeAccounts : [];
      if (!options.length) {
        this.selectedRuntimeAccount = "";
        return;
      }

      if (this.routePrefill.channelId === this.selectedChannelId && this.routePrefill.runtimeAccount) {
        const routed = options.find((item) => item.value === this.routePrefill.runtimeAccount);
        if (routed) {
          this.selectedRuntimeAccount = routed.value;
          return;
        }
      }

      if (options.some((item) => item.value === this.selectedRuntimeAccount)) {
        return;
      }

      this.selectedRuntimeAccount = options[0].value;
    },
    handleRuntimeChange(value) {
      this.selectedRuntimeAccount = value;
    },
    maybeOpenRoutedDebug() {
      if (this.routePrefillApplied) {
        return;
      }
      if (this.routePrefill.entry !== "debug") {
        return;
      }
      if (!this.routePrefill.channelId || this.routePrefill.channelId !== this.selectedChannelId) {
        return;
      }
      if (!this.inventory.runtimeAccounts.length) {
        return;
      }
      this.routePrefillApplied = true;
      this.showDebugDialog = true;
    },
    openCreateChannel() {
      this.editorMode = "create";
      this.editorForm = createEmptyChannel();
      this.editorVisible = true;
    },
    openEditChannel(channel) {
      const source = channel && channel.id ? channel : this.selectedChannel;
      if (!source || !source.id) {
        return;
      }
      this.editorMode = "edit";
      this.editorForm = {
        id: source.id || "",
        name: source.name || "",
        baseUrl: source.baseUrl || "",
        inventoryPath: source.inventoryPath || "/inventory",
        accessToken: source.accessToken || "",
        enabled: source.enabled !== false,
        remark: source.remark || "",
      };
      this.editorVisible = true;
    },
    saveChannel(payload) {
      this.saving = true;
      const onSuccess = ({ data }) => {
        this.saving = false;
        if (data.code !== 0) {
          this.$message.error(data.msg || "保存 OpenClaw Channel 失败");
          return;
        }
        this.editorVisible = false;
        const saved = data.data || {};
        this.$message.success(this.editorMode === "edit" ? "Channel 已更新" : "Channel 已创建，请先完成本地接入");
        this.loadChannels(saved.id || payload.id || "");
      };
      const onFail = ({ data }) => {
        this.saving = false;
        this.$message.error((data && data.msg) || "保存 OpenClaw Channel 失败");
      };

      if (this.editorMode === "edit" && payload.id) {
        Api.openclaw.updateChannel(payload.id, payload, onSuccess, onFail);
        return;
      }
      Api.openclaw.createChannel(payload, onSuccess, onFail);
    },
    removeChannel(channel) {
      const target = channel && channel.id ? channel : this.selectedChannel;
      if (!target || !target.id) {
        return;
      }
      this.$confirm(`删除 Channel「${target.name || target.id}」后，当前绑定和调试入口会失去引用。是否继续？`, "提示", {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning",
      }).then(() => {
        this.saving = true;
        Api.openclaw.deleteChannel(target.id, ({ data }) => {
          this.saving = false;
          if (data.code !== 0) {
            this.$message.error(data.msg || "删除 OpenClaw Channel 失败");
            return;
          }
          if (this.selectedChannelId === target.id) {
            this.clearSelection();
          }
          this.$message.success("OpenClaw Channel 已删除");
          this.loadChannels();
        }, ({ data }) => {
          this.saving = false;
          this.$message.error((data && data.msg) || "删除 OpenClaw Channel 失败");
        });
      }).catch(() => {});
    },
    updateSummaryFromSelectedChannel() {
      if (!this.selectedChannelId) {
        return;
      }
      const summary = createEmptySummary();
      summary.bindingCount = this.channelBindings.length;
      summary.runtimeCount = Array.isArray(this.inventory.runtimeAccounts) ? this.inventory.runtimeAccounts.length : 0;
      summary.agentCount = Array.isArray(this.inventory.agents) ? this.inventory.agents.length : 0;
      summary.inventoryLabel = this.inventory.healthy ? "已就绪" : (this.inventory.errorMessage ? "需检查" : "未同步");
      summary.inventoryTone = this.inventory.healthy ? "healthy" : (this.inventory.errorMessage ? "attention" : "idle");
      summary.inventoryNote = this.inventory.healthy
        ? `${this.inventory.connectedBridgeCount || 0} 个在线设备`
        : (compactInventoryMessage(this.inventory.errorMessage) || "尚未拉取 inventory");
      this.updateChannelSummary(this.selectedChannelId, summary);
    },
    openDebugForAgent(payload) {
      this.routePrefill = {
        channelId: this.selectedChannelId,
        runtimeAccount: payload && payload.runtimeAccount ? payload.runtimeAccount : this.selectedRuntimeAccount,
        openclawAgentId: payload && payload.openclawAgentId ? payload.openclawAgentId : "",
        openclawAgentName: payload && payload.openclawAgentName ? payload.openclawAgentName : "",
        entry: "debug",
      };
      this.routePrefillApplied = false;
      this.showDebugDialog = true;
    },
    openDebugForBinding(binding) {
      this.routePrefill = {
        channelId: this.selectedChannelId,
        runtimeAccount: binding && binding.runtimeAccount ? binding.runtimeAccount : this.selectedRuntimeAccount,
        openclawAgentId: binding && binding.openclawAgentId ? binding.openclawAgentId : "",
        openclawAgentName: binding && binding.openclawAgentName ? binding.openclawAgentName : "",
        entry: "debug",
      };
      this.routePrefillApplied = false;
      this.showDebugDialog = true;
    },
    openBoundAgent(binding) {
      if (!binding || !binding.agentId) {
        return;
      }
      this.$router.push({
        path: "/role-config",
        query: { agentId: binding.agentId },
      });
    },
    copyInstallCommand() {
      if (!this.setupGuide.installCommand) {
        this.$message.warning("请先保存 Channel 并生成安装命令");
        return;
      }
      this.copyText(this.setupGuide.installCommand);
    },
    copyText(text) {
      if (navigator.clipboard && window.isSecureContext) {
        navigator.clipboard.writeText(text).then(() => {
          this.$message.success("安装命令已复制");
        }).catch(() => {
          this.copyTextFallback(text);
        });
        return;
      }
      this.copyTextFallback(text);
    },
    copyTextFallback(text) {
      const textarea = document.createElement("textarea");
      textarea.value = text;
      textarea.style.position = "fixed";
      textarea.style.opacity = "0";
      document.body.appendChild(textarea);
      textarea.select();
      try {
        const copied = document.execCommand("copy");
        if (copied) {
          this.$message.success("安装命令已复制");
        } else {
          this.$message.error("复制失败，请手动复制");
        }
      } catch (error) {
        this.$message.error("复制失败，请手动复制");
      } finally {
        document.body.removeChild(textarea);
      }
    },
    goToRoleConfig() {
      if (this.agentId) {
        this.$router.push({
          path: "/role-config",
          query: { agentId: this.agentId },
        });
        return;
      }
      this.$router.push("/role-config");
    },
  },
};
</script>

<style scoped>
.welcome {
  min-width: 0;
  min-height: 506px;
  height: 100vh;
  display: flex;
  flex-direction: column;
  background:
    radial-gradient(circle at top left, rgba(112, 146, 255, 0.18), transparent 22%),
    linear-gradient(145deg, #eef3ff, #f9fbff);
}

.main-wrapper {
  flex: 1;
  min-height: 0;
  padding: 24px 24px 16px;
}

.content-panel,
.content-area {
  height: 100%;
}

.content-panel {
  min-height: 0;
}

.content-area {
  overflow: auto;
  border-radius: 34px;
  padding: 8px;
}

@media (max-width: 900px) {
  .main-wrapper {
    padding: 16px 16px 12px;
  }

  .content-area {
    border-radius: 24px;
    padding: 0;
  }
}
</style>

<template>
  <div class="welcome">
    <HeaderBar />

    <div class="operation-bar">
      <div class="title-block">
        <h2 class="page-title">OpenClaw Channel 设置</h2>
        <p class="page-subtitle">把 Channel 保存下来，系统生成命令给用户复制到 OpenClaw 目录执行，不再暴露底层接入参数。</p>
      </div>
      <div class="page-actions">
        <el-button class="ghost-btn" @click="goToRoleConfig">返回智能体配置</el-button>
        <el-button class="ghost-btn" @click="resetDraft">新建 Channel</el-button>
        <el-button type="primary" class="refresh-btn" :loading="loading" @click="loadChannels">刷新</el-button>
      </div>
    </div>

    <div class="main-wrapper">
      <div class="content-panel">
        <div
          class="content-area"
          v-loading="loading"
          element-loading-text="正在加载 OpenClaw channel 配置"
          element-loading-spinner="el-icon-loading"
          element-loading-background="rgba(255, 255, 255, 0.72)"
        >
          <div class="hero-card">
            <div class="hero-main">
              <div class="hero-label">Command Driven</div>
              <h3 class="hero-title">保存 Channel，复制命令，回到这里测试</h3>
              <p class="hero-description">
                这页只负责维护 OpenClaw channel 和安装命令。用户不用再理解 `baseUrl / inventoryPath / accessToken`，
                只要在 OpenClaw 目录执行生成好的 `npx` 命令，再回来同步 inventory 即可。
              </p>
            </div>
            <div class="hero-meta">
              <div class="meta-pill">
                <span class="meta-key">已配置 Channel</span>
                <span class="meta-value">{{ channels.length }}</span>
              </div>
              <div class="meta-pill">
                <span class="meta-key">命令状态</span>
                <span class="meta-value">{{ commandStatusText }}</span>
              </div>
              <div class="meta-pill">
                <span class="meta-key">Inventory 状态</span>
                <span class="meta-value">{{ inventoryStatusText }}</span>
              </div>
            </div>
          </div>

          <div class="surface-grid">
            <el-card class="surface-card" shadow="never">
              <div class="section-header">
                <div>
                  <div class="section-eyebrow">Channel Registry</div>
                  <h3>已绑定 Channel</h3>
                </div>
                <span class="tool-count">{{ channels.length }} 个</span>
              </div>
              <p class="section-description">
                智能体表单只会消费这里的 channel。保存后，每个 channel 都有自己的 account id 和接入命令。
              </p>
              <div v-if="channels.length" class="channel-list">
                <button
                  v-for="item in channels"
                  :key="item.id"
                  class="channel-item"
                  :class="{ active: item.id === draft.id }"
                  @click="selectChannel(item)"
                >
                  <div class="channel-item-main">
                    <span class="channel-name">{{ item.name || "未命名 channel" }}</span>
                    <el-tag size="mini" :type="item.enabled ? 'success' : 'info'">{{ item.enabled ? '启用' : '停用' }}</el-tag>
                  </div>
                  <span class="channel-url">Account: {{ item.id }}</span>
                </button>
              </div>
              <el-empty v-else description="尚未绑定 OpenClaw channel" :image-size="88" />
            </el-card>

            <el-card class="surface-card" shadow="never">
              <div class="section-header">
                <div>
                  <div class="section-eyebrow">Channel Editor</div>
                  <h3>{{ draft.id ? "编辑 Channel" : "新增 Channel" }}</h3>
                </div>
                <div class="inline-actions">
                  <el-button size="small" @click="resetDraft">清空</el-button>
                  <el-button size="small" type="danger" plain :disabled="!draft.id" @click="removeDraftChannel">删除</el-button>
                </div>
              </div>
              <p class="section-description">
                主流程只需要填写名称。保存后系统会生成 account id、安装命令和后台接入参数。
              </p>
              <div class="step-list">
                <div class="step-item">
                  <span class="step-index">1</span>
                  <span>填写 Channel 名称并保存</span>
                </div>
                <div class="step-item">
                  <span class="step-index">2</span>
                  <span>复制命令到 OpenClaw 项目目录执行</span>
                </div>
                <div class="step-item">
                  <span class="step-index">3</span>
                  <span>执行完成后回来点击“测试并拉取 Inventory”</span>
                </div>
              </div>
              <el-form label-position="top" class="channel-form">
                <el-form-item label="Channel 名称">
                  <el-input v-model="draft.name" maxlength="64" placeholder="例如：生产 Runtime" />
                </el-form-item>
                <el-form-item label="备注">
                  <el-input v-model="draft.remark" type="textarea" :rows="3" resize="none" maxlength="200" placeholder="可选，用于记录用途或环境说明" />
                </el-form-item>
                <div class="channel-toggle">
                  <span>启用该 channel</span>
                  <el-switch v-model="draft.enabled" />
                </div>
                <div class="channel-actions">
                  <el-button type="primary" @click="saveChannels" :loading="saving">保存 Channel 配置</el-button>
                  <el-button @click="copyInstallCommand" :disabled="!setupGuide.installCommand">复制安装命令</el-button>
                  <el-button @click="syncDraftInventory" :loading="inventoryLoading" :disabled="!draft.id">测试并拉取 Inventory</el-button>
                </div>
              </el-form>

              <div class="command-panel" v-loading="guideLoading">
                <div class="command-header">
                  <div>
                    <div class="section-eyebrow">Setup Command</div>
                    <h4 class="command-title">可复制的 OpenClaw 安装命令</h4>
                  </div>
                  <el-tag size="mini" :type="setupGuide.installCommand ? 'success' : 'info'">{{ commandStatusText }}</el-tag>
                </div>
                <div class="command-meta">
                  <div class="meta-line">
                    <span class="meta-line-key">Account ID</span>
                    <span class="meta-line-value">{{ setupGuide.channelId || '保存后自动生成' }}</span>
                  </div>
                  <div class="meta-line">
                    <span class="meta-line-key">默认 Agent</span>
                    <span class="meta-line-value">{{ setupGuide.defaultAgentId || 'main' }}</span>
                  </div>
                </div>
                <el-alert
                  v-if="!setupGuide.accessTokenConfigured && draft.id"
                  title="系统未检测到 server secret，当前无法生成可执行命令，请先补齐后台 server.secret。"
                  type="warning"
                  :closable="false"
                  show-icon
                  class="top-alert"
                />
                <el-input
                  type="textarea"
                  :rows="6"
                  resize="none"
                  readonly
                  class="command-textarea"
                  :value="setupGuide.installCommand || '先保存 Channel，系统会在这里生成可直接复制的 npx 命令。'"
                />
                <div class="command-hint">
                  命令示例用法：进入 OpenClaw 项目目录后执行即可，例如 `cd ~/openclaw && ...`。命令里已经带好 server、admin key、account 和 channel 名称。
                </div>
              </div>

              <el-collapse v-model="advancedPanels" class="advanced-panel">
                <el-collapse-item name="advanced" title="高级配置（通常不用改）">
                  <el-form label-position="top" class="channel-form advanced-form">
                    <el-form-item label="管理接口基础地址">
                      <el-input v-model="draft.baseUrl" placeholder="默认自动生成，例如：https://example.com/admin/openclaw" />
                    </el-form-item>
                    <el-form-item label="Inventory 路径">
                      <el-input v-model="draft.inventoryPath" placeholder="/inventory" />
                    </el-form-item>
                    <el-form-item label="Access Token">
                      <el-input v-model="draft.accessToken" show-password placeholder="默认自动注入 server secret" />
                    </el-form-item>
                  </el-form>
                </el-collapse-item>
              </el-collapse>
            </el-card>
          </div>

          <el-card class="surface-card wide-card" shadow="never">
            <div class="section-header">
              <div>
                <div class="section-eyebrow">Inventory</div>
                <h3>Channel 可选项</h3>
              </div>
              <el-tag :type="inventory.healthy ? 'success' : 'warning'">{{ inventoryStatusText }}</el-tag>
            </div>
            <p class="section-description">
              这里展示 channel 实际回传的 runtime/account 与 OpenClaw agent 列表。智能体绑定页会直接消费这些下拉项。
            </p>
            <el-alert
              v-if="inventory.errorMessage"
              :title="inventory.errorMessage"
              type="warning"
              :closable="false"
              show-icon
              class="top-alert"
            />
            <div class="inventory-grid">
              <div class="inventory-block">
                <div class="inventory-title">Runtime / Account</div>
                <div v-if="inventory.runtimeAccounts.length" class="inventory-list">
                  <div v-for="item in inventory.runtimeAccounts" :key="item.value" class="inventory-chip">
                    {{ item.label }}
                  </div>
                </div>
                <el-empty v-else description="当前未返回 runtime/account 选项" :image-size="80" />
              </div>
              <div class="inventory-block">
                <div class="inventory-title">OpenClaw Agents</div>
                <div v-if="inventory.agents.length" class="inventory-list">
                  <div v-for="item in inventory.agents" :key="item.value" class="inventory-chip">
                    {{ item.label }}
                  </div>
                </div>
                <el-empty v-else description="当前未返回 OpenClaw agent 选项" :image-size="80" />
              </div>
            </div>
            <div class="runtime-list">
              <div class="runtime-item">
                <span class="runtime-path">Source URL</span>
                <span class="runtime-note">{{ inventory.sourceUrl || '尚未测试 inventory 接口' }}</span>
              </div>
              <div class="runtime-item">
                <span class="runtime-path">绑定策略</span>
                <span class="runtime-note">先绑定 Channel，再让智能体表单按 channel 下拉选择 runtime/account 和 OpenClaw agent。</span>
              </div>
            </div>
          </el-card>
        </div>
      </div>
    </div>

    <el-footer>
      <VersionFooter />
    </el-footer>
  </div>
</template>

<script>
import Api from "@/apis/api";
import HeaderBar from "@/components/HeaderBar.vue";
import VersionFooter from "@/components/VersionFooter.vue";

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

export default {
  name: "OpenClawManagement",
  components: {
    HeaderBar,
    VersionFooter,
  },
  data() {
    return {
      loading: false,
      saving: false,
      guideLoading: false,
      inventoryLoading: false,
      agentId: "",
      channels: [],
      draft: createEmptyChannel(),
      inventory: createEmptyInventory(),
      setupGuide: createEmptySetupGuide(),
      advancedPanels: [],
    };
  },
  computed: {
    inventoryStatusText() {
      if (this.inventoryLoading) {
        return "同步中";
      }
      if (this.inventory.healthy) {
        return "已就绪";
      }
      if (this.inventory.errorMessage) {
        return "需检查";
      }
      return "未同步";
    },
    commandStatusText() {
      if (this.guideLoading) {
        return "生成中";
      }
      if (this.setupGuide.installCommand) {
        return "可复制";
      }
      if (this.draft.id) {
        return "待生成";
      }
      return "待保存";
    },
  },
  watch: {
    "$route.query.agentId": {
      immediate: true,
      handler(agentId) {
        this.agentId = agentId || "";
      },
    },
  },
  created() {
    this.loadChannels();
  },
  methods: {
    loadChannels() {
      this.loading = true;
      Api.openclaw.getChannels(({ data }) => {
        this.loading = false;
        if (data.code === 0) {
          this.channels = Array.isArray(data.data) ? data.data : [];
          if (this.draft.id) {
            const matched = this.channels.find((item) => item.id === this.draft.id);
            if (matched) {
              this.selectChannel(matched);
              return;
            }
          }
          if (!this.draft.id && !this.draft.name && this.channels.length) {
            this.selectChannel(this.channels[0]);
          }
        } else {
          this.$message.error(data.msg || "获取 OpenClaw channel 列表失败");
        }
      });
    },
    selectChannel(channel) {
      this.draft = {
        id: channel.id || "",
        name: channel.name || "",
        baseUrl: channel.baseUrl || "",
        inventoryPath: channel.inventoryPath || "/inventory",
        accessToken: channel.accessToken || "",
        enabled: channel.enabled !== false,
        remark: channel.remark || "",
      };
      this.refreshSetupGuide();
      this.syncDraftInventory();
    },
    resetDraft() {
      this.draft = createEmptyChannel();
      this.inventory = createEmptyInventory();
      this.setupGuide = createEmptySetupGuide();
      this.advancedPanels = [];
    },
    refreshSetupGuide() {
      if (!this.draft.id) {
        this.setupGuide = createEmptySetupGuide();
        return;
      }
      this.guideLoading = true;
      Api.openclaw.getChannelSetupGuide(this.draft.id, ({ data }) => {
        this.guideLoading = false;
        if (data.code === 0) {
          this.setupGuide = data.data || createEmptySetupGuide();
        } else {
          this.setupGuide = createEmptySetupGuide();
          this.$message.error(data.msg || "生成安装命令失败");
        }
      }, ({ data }) => {
        this.guideLoading = false;
        this.setupGuide = createEmptySetupGuide();
        this.$message.error((data && data.msg) || "生成安装命令失败");
      });
    },
    saveChannels() {
      if (!this.draft.name) {
        this.$message.warning("请先填写 Channel 名称");
        return;
      }
      const nextChannels = this.channels.filter((item) => item.id !== this.draft.id);
      const channelToSave = {
        ...this.draft,
        id: this.draft.id || `channel-${Date.now()}`,
      };
      nextChannels.unshift(channelToSave);
      this.saving = true;
      Api.openclaw.saveChannels(nextChannels, ({ data }) => {
        this.saving = false;
        if (data.code === 0) {
          this.channels = Array.isArray(data.data) ? data.data : [];
          const saved = this.channels.find((item) => item.id === channelToSave.id) || this.channels[0];
          if (saved) {
            this.selectChannel(saved);
          }
          this.$message.success("OpenClaw channel 配置已保存");
        } else {
          this.$message.error(data.msg || "保存 OpenClaw channel 失败");
        }
      }, ({ data }) => {
        this.saving = false;
        this.$message.error((data && data.msg) || "保存 OpenClaw channel 失败");
      });
    },
    removeDraftChannel() {
      if (!this.draft.id) {
        return;
      }
      const nextChannels = this.channels.filter((item) => item.id !== this.draft.id);
      this.saving = true;
      Api.openclaw.saveChannels(nextChannels, ({ data }) => {
        this.saving = false;
        if (data.code === 0) {
          this.channels = Array.isArray(data.data) ? data.data : [];
          this.resetDraft();
          if (this.channels.length) {
            this.selectChannel(this.channels[0]);
          }
          this.$message.success("OpenClaw channel 已删除");
        } else {
          this.$message.error(data.msg || "删除 OpenClaw channel 失败");
        }
      }, ({ data }) => {
        this.saving = false;
        this.$message.error((data && data.msg) || "删除 OpenClaw channel 失败");
      });
    },
    syncDraftInventory() {
      if (!this.draft.id) {
        this.inventory = {
          ...createEmptyInventory(),
          errorMessage: "请先保存 Channel，再同步 inventory。",
        };
        return;
      }
      this.inventoryLoading = true;
      Api.openclaw.getChannelInventory(this.draft.id, ({ data }) => {
        this.inventoryLoading = false;
        if (data.code === 0) {
          this.inventory = data.data || createEmptyInventory();
        } else {
          this.inventory = {
            ...createEmptyInventory(),
            errorMessage: data.msg || "同步 OpenClaw inventory 失败",
          };
        }
      }, ({ data }) => {
        this.inventoryLoading = false;
        this.inventory = {
          ...createEmptyInventory(),
          errorMessage: (data && data.msg) || "同步 OpenClaw inventory 失败",
        };
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
        console.error("复制安装命令失败:", error);
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
  min-width: 900px;
  min-height: 506px;
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: linear-gradient(145deg, #eef2ff, #f8fbff);
}

.operation-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24px 28px 12px;
}

.title-block {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.page-title {
  margin: 0;
  font-size: 28px;
  color: #24304a;
}

.page-subtitle {
  margin: 0;
  font-size: 14px;
  color: #7f8aa3;
}

.page-actions {
  display: flex;
  gap: 12px;
}

.ghost-btn,
.refresh-btn {
  border-radius: 999px;
}

.main-wrapper {
  flex: 1;
  padding: 0 28px 28px;
}

.content-panel,
.content-area {
  height: 100%;
}

.hero-card,
.surface-card {
  border-radius: 24px;
  border: none;
}

.hero-card {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  padding: 28px;
  background: linear-gradient(120deg, #22304f, #385a9a);
  color: #fff;
}

.hero-label {
  font-size: 12px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.72);
}

.hero-title {
  margin: 10px 0 8px;
  font-size: 28px;
}

.hero-description {
  max-width: 680px;
  margin: 0;
  color: rgba(255, 255, 255, 0.82);
  line-height: 1.7;
}

.hero-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-content: flex-start;
  justify-content: flex-end;
}

.meta-pill {
  min-width: 150px;
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.12);
  backdrop-filter: blur(10px);
}

.meta-key {
  display: block;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.64);
}

.meta-value {
  display: block;
  margin-top: 6px;
  font-size: 15px;
  font-weight: 600;
}

.surface-grid {
  display: grid;
  grid-template-columns: minmax(280px, 0.88fr) minmax(420px, 1.12fr);
  gap: 18px;
  margin-top: 20px;
}

.surface-card {
  padding: 8px;
}

.wide-card {
  margin-top: 20px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}

.section-eyebrow {
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.14em;
  color: #8d97ab;
}

.section-header h3 {
  margin: 8px 0 0;
  font-size: 22px;
  color: #24304a;
}

.section-description {
  margin: 16px 0 0;
  color: #69758d;
  line-height: 1.7;
}

.tool-count {
  color: #5d6882;
  font-weight: 600;
}

.channel-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 16px;
}

.channel-item {
  padding: 14px 16px;
  border: 1px solid #e4eaf8;
  border-radius: 16px;
  background: #f8faff;
  text-align: left;
  cursor: pointer;
}

.channel-item.active {
  border-color: #5778ff;
  background: #eef2ff;
}

.channel-item-main {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.channel-name {
  font-weight: 600;
  color: #24304a;
}

.channel-url {
  display: block;
  margin-top: 8px;
  color: #7a86a0;
  word-break: break-all;
}

.step-list {
  display: grid;
  gap: 10px;
  margin-top: 18px;
}

.step-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 16px;
  background: #f8faff;
  color: #44506a;
}

.step-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 999px;
  background: #223d7a;
  color: #fff;
  font-size: 12px;
  font-weight: 700;
}

.channel-form {
  margin-top: 14px;
}

.channel-toggle {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 6px;
  color: #44506a;
}

.channel-actions,
.inline-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.channel-actions {
  margin-top: 18px;
}

.command-panel {
  margin-top: 20px;
  padding: 18px;
  border-radius: 20px;
  background: linear-gradient(180deg, #f7f9ff, #eef3ff);
}

.command-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.command-title {
  margin: 8px 0 0;
  font-size: 18px;
  color: #24304a;
}

.command-meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 16px;
}

.meta-line {
  padding: 12px 14px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.72);
}

.meta-line-key {
  display: block;
  font-size: 12px;
  color: #6f7b95;
}

.meta-line-value {
  display: block;
  margin-top: 6px;
  color: #23304d;
  font-weight: 600;
  word-break: break-all;
}

.command-textarea {
  margin-top: 16px;
}

.command-hint {
  margin-top: 12px;
  color: #68758e;
  line-height: 1.7;
}

.advanced-panel {
  margin-top: 18px;
}

.advanced-form {
  margin-top: 0;
}

.inventory-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
  margin-top: 16px;
}

.inventory-block {
  padding: 18px;
  border-radius: 18px;
  background: #f8faff;
}

.inventory-title {
  margin-bottom: 12px;
  font-size: 15px;
  font-weight: 600;
  color: #24304a;
}

.inventory-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.inventory-chip {
  padding: 8px 12px;
  border-radius: 999px;
  background: #e9efff;
  color: #3656a3;
}

.runtime-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 18px;
}

.runtime-item {
  display: flex;
  gap: 14px;
  padding: 12px 14px;
  border-radius: 16px;
  background: #f8faff;
}

.runtime-path {
  min-width: 120px;
  font-weight: 600;
  color: #24304a;
}

.runtime-note {
  color: #68758e;
  word-break: break-word;
}

.top-alert {
  margin-top: 16px;
}

@media (max-width: 1200px) {
  .surface-grid,
  .inventory-grid,
  .command-meta {
    grid-template-columns: 1fr;
  }

  .hero-card {
    flex-direction: column;
  }

  .hero-meta {
    justify-content: flex-start;
  }
}
</style>

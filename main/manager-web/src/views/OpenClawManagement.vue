<template>
  <div class="welcome">
    <HeaderBar />

    <div class="operation-bar">
      <div class="title-block">
        <h2 class="page-title">OpenClaw 控制台</h2>
        <p class="page-subtitle">围绕单个智能体聚合 MCP 接入、工具能力和函数映射。</p>
      </div>
      <div class="page-actions">
        <el-button class="ghost-btn" @click="goToRoleConfig">返回角色配置</el-button>
        <el-button class="ghost-btn" @click="goToFunctionConfig">编辑函数配置</el-button>
        <el-button type="primary" class="refresh-btn" :loading="loading" @click="refreshSurface">
          刷新
        </el-button>
      </div>
    </div>

    <div class="main-wrapper">
      <div class="content-panel">
        <div
          class="content-area"
          v-loading="loading"
          element-loading-text="正在刷新 OpenClaw 控制面"
          element-loading-spinner="el-icon-loading"
          element-loading-background="rgba(255, 255, 255, 0.72)"
        >
          <el-alert
            v-if="!agentId"
            title="缺少 agentId，当前无法加载 OpenClaw 管理页"
            type="warning"
            :closable="false"
            show-icon
            class="top-alert"
          />

          <template v-else>
            <div class="hero-card">
              <div class="hero-main">
                <div class="hero-label">Web 主控制面</div>
                <h3 class="hero-title">{{ agentName }}</h3>
                <p class="hero-description">
                  当前页面把散落在函数配置抽屉里的 MCP 能力收口成一条可见、可达、可操作的 OpenClaw 路径。
                </p>
              </div>
              <div class="hero-meta">
                <div class="meta-pill">
                  <span class="meta-key">Agent ID</span>
                  <span class="meta-value">{{ agentId }}</span>
                </div>
                <div class="meta-pill">
                  <span class="meta-key">状态</span>
                  <span class="meta-value">{{ surfaceStatusText }}</span>
                </div>
                <div class="meta-pill">
                  <span class="meta-key">上次刷新</span>
                  <span class="meta-value">{{ refreshedAtText }}</span>
                </div>
              </div>
            </div>

            <el-alert
              v-if="surface.agentConfigError"
              :title="surface.agentConfigError"
              type="error"
              :closable="false"
              show-icon
              class="top-alert"
            />

            <div class="surface-grid">
              <el-card class="surface-card" shadow="never">
                <div class="section-header">
                  <div>
                    <div class="section-eyebrow">MCP Access</div>
                    <h3>MCP 接入点</h3>
                  </div>
                  <el-tag :type="surfaceStatusTag" effect="dark">{{ surfaceStatusText }}</el-tag>
                </div>
                <p class="section-description">
                  当前 agent 的 MCP 暴露地址。这里提供复制和刷新，不再要求先打开函数配置抽屉。
                </p>
                <el-input :value="surface.mcpAccessAddress || surface.mcpAddressError || '暂未获取到 MCP 地址'" readonly>
                  <template #suffix>
                    <el-button type="text" @click="copyMcpAddress">复制</el-button>
                  </template>
                </el-input>
                <p v-if="surface.mcpAddressError" class="error-text">{{ surface.mcpAddressError }}</p>
                <div class="inline-actions">
                  <el-button size="small" @click="refreshSurface">刷新 MCP</el-button>
                  <el-button size="small" type="text" @click="goToFunctionConfig">去底层函数配置</el-button>
                </div>
              </el-card>

              <el-card class="surface-card" shadow="never">
                <div class="section-header">
                  <div>
                    <div class="section-eyebrow">Tool Surface</div>
                    <h3>工具列表</h3>
                  </div>
                  <span class="tool-count">{{ surface.mcpTools.length }} 个工具</span>
                </div>
                <p class="section-description">
                  当前通过 manager-api 探测到的 MCP tools。空列表和请求异常需要区分对待。
                </p>
                <div v-if="surface.mcpTools.length" class="tool-grid">
                  <div v-for="tool in surface.mcpTools" :key="tool" class="tool-chip">
                    {{ tool }}
                  </div>
                </div>
                <el-empty v-else description="当前未探测到可用工具" :image-size="90" />
                <p v-if="surface.mcpToolsError" class="error-text">{{ surface.mcpToolsError }}</p>
              </el-card>
            </div>

            <el-card class="surface-card wide-card" shadow="never">
              <div class="section-header">
                <div>
                  <div class="section-eyebrow">Function Mapping</div>
                  <h3>函数 / 插件映射</h3>
                </div>
                <span class="tool-count">{{ functionMappings.length }} 条映射</span>
              </div>
              <p class="section-description">
                这里显示当前 agent 保存下来的函数映射摘要；详细参数编辑仍回到角色配置页完成。
              </p>
              <div v-if="functionMappings.length" class="mapping-grid">
                <div v-for="item in functionMappings" :key="item.pluginId" class="mapping-card">
                  <div class="mapping-header">
                    <span class="mapping-name">{{ item.pluginId }}</span>
                    <span class="mapping-count">{{ item.paramCount }} 个参数</span>
                  </div>
                  <pre class="mapping-body">{{ formatParamInfo(item.paramInfo) }}</pre>
                </div>
              </div>
              <el-empty v-else description="当前 agent 还没有函数映射" :image-size="90" />
            </el-card>

            <el-card class="surface-card wide-card" shadow="never">
              <div class="section-header">
                <div>
                  <div class="section-eyebrow">Runtime Notes</div>
                  <h3>运行时说明</h3>
                </div>
              </div>
              <p class="section-description">
                这一版控制面只消费当前已稳定的 manager-api 数据。更高阶的 OpenClaw runtime/admin 状态仍等 Phase 2 契约校准后再收口。
              </p>
              <div class="runtime-list">
                <div class="runtime-item">
                  <span class="runtime-path">/openclaw/bridge/ws</span>
                  <span class="runtime-note">桥接 WebSocket 路径已由 deploy 暴露，当前页面只做说明，不直接管理连接生命周期。</span>
                </div>
                <div class="runtime-item">
                  <span class="runtime-path">/admin/openclaw/</span>
                  <span class="runtime-note">运行时 admin 路径已存在，但还没有稳定沉淀到 manager-api 包装层。</span>
                </div>
                <div class="runtime-item">
                  <span class="runtime-path">Phase 2 Dependency</span>
                  <span class="runtime-note">bridge token、握手状态和 hotfix overlay 一致性仍需后续 phase 校准。</span>
                </div>
              </div>
            </el-card>
          </template>
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

const createEmptySurface = () => ({
  agentConfig: null,
  agentConfigError: "",
  mcpAccessAddress: "",
  mcpAddressError: "",
  mcpTools: [],
  mcpToolsError: "",
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
      agentId: "",
      refreshedAt: "",
      surface: createEmptySurface(),
    };
  },
  computed: {
    agentName() {
      if (this.surface.agentConfig && this.surface.agentConfig.agentName) {
        return this.surface.agentConfig.agentName;
      }
      return "未命名智能体";
    },
    functionMappings() {
      const functions = this.surface.agentConfig && Array.isArray(this.surface.agentConfig.functions)
        ? this.surface.agentConfig.functions
        : [];
      return functions.map((item) => {
        const paramInfo = item && item.paramInfo ? item.paramInfo : {};
        return {
          pluginId: item && item.pluginId ? item.pluginId : "unknown-plugin",
          paramInfo,
          paramCount: Object.keys(paramInfo).length,
        };
      });
    },
    surfaceStatusText() {
      if (this.loading) {
        return "刷新中";
      }
      if (this.surface.mcpAddressError || this.surface.mcpToolsError) {
        return "部分异常";
      }
      if (this.surface.mcpTools.length > 0) {
        return "已连接";
      }
      if (this.surface.mcpAccessAddress) {
        return "已暴露";
      }
      return "未配置";
    },
    surfaceStatusTag() {
      if (this.loading) {
        return "warning";
      }
      if (this.surface.mcpAddressError || this.surface.mcpToolsError) {
        return "danger";
      }
      if (this.surface.mcpTools.length > 0) {
        return "success";
      }
      return "info";
    },
    refreshedAtText() {
      return this.refreshedAt || "尚未刷新";
    },
  },
  watch: {
    "$route.query.agentId": {
      immediate: true,
      handler(agentId) {
        this.agentId = agentId || "";
        if (!this.agentId) {
          this.surface = this.getEmptySurface();
          this.refreshedAt = "";
          this.loading = false;
          return;
        }
        this.loadSurface();
      },
    },
  },
  methods: {
    getEmptySurface() {
      return createEmptySurface();
    },
    loadSurface() {
      if (!this.agentId) {
        return;
      }
      this.loading = true;
      Api.agent.getAgentOpenClawSurface(this.agentId, ({ data }) => {
        if (data && data.code === 0) {
          this.surface = this.normalizeSurface(data.data);
          this.refreshedAt = this.formatNow();
        } else {
          this.surface = {
            ...this.getEmptySurface(),
            agentConfigError: (data && data.msg) || "加载 OpenClaw 控制面失败",
          };
        }
        this.loading = false;
      });
    },
    refreshSurface() {
      this.loadSurface();
    },
    normalizeSurface(surface) {
      return {
        agentConfig: surface && surface.agentConfig ? surface.agentConfig : null,
        agentConfigError: surface && surface.agentConfigError ? surface.agentConfigError : "",
        mcpAccessAddress: surface && surface.mcpAccessAddress ? surface.mcpAccessAddress : "",
        mcpAddressError: surface && surface.mcpAddressError ? surface.mcpAddressError : "",
        mcpTools: surface && Array.isArray(surface.mcpTools) ? surface.mcpTools : [],
        mcpToolsError: surface && surface.mcpToolsError ? surface.mcpToolsError : "",
      };
    },
    formatNow() {
      const now = new Date();
      const pad = (value) => String(value).padStart(2, "0");
      return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} ${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`;
    },
    formatParamInfo(paramInfo) {
      if (!paramInfo || Object.keys(paramInfo).length === 0) {
        return "无参数配置";
      }
      try {
        return JSON.stringify(paramInfo, null, 2);
      } catch (error) {
        console.error("格式化参数失败:", error);
        return "参数配置解析失败";
      }
    },
    copyMcpAddress() {
      if (!this.surface.mcpAccessAddress) {
        this.$message.warning("当前没有可复制的 MCP 地址");
        return;
      }
      const textarea = document.createElement("textarea");
      textarea.value = this.surface.mcpAccessAddress;
      textarea.style.position = "fixed";
      document.body.appendChild(textarea);
      textarea.select();

      try {
        const copied = document.execCommand("copy");
        if (copied) {
          this.$message.success("MCP 地址已复制");
        } else {
          this.$message.error("复制失败，请手动复制");
        }
      } catch (error) {
        console.error("复制 MCP 地址失败:", error);
        this.$message.error("复制失败，请手动复制");
      } finally {
        document.body.removeChild(textarea);
      }
    },
    goToRoleConfig() {
      if (!this.agentId) {
        this.$message.warning("当前没有可跳转的智能体");
        return;
      }
      this.$router.push({
        path: "/role-config",
        query: { agentId: this.agentId },
      });
    },
    goToFunctionConfig() {
      if (!this.agentId) {
        this.$message.warning("当前没有可跳转的智能体");
        return;
      }
      this.$router.push({
        path: "/role-config",
        query: {
          agentId: this.agentId,
          openFunctions: "1",
        },
      });
    },
  },
};
</script>

<style scoped>
.welcome {
  min-width: 900px;
  height: 100vh;
  display: flex;
  flex-direction: column;
  background:
    radial-gradient(circle at top left, rgba(92, 136, 255, 0.18), transparent 30%),
    radial-gradient(circle at top right, rgba(23, 128, 93, 0.14), transparent 28%),
    linear-gradient(145deg, #eef5ff, #f7fbff 52%, #edf7f3);
  overflow: hidden;
}

.operation-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
  padding: 18px 24px;
}

.title-block {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.page-title {
  margin: 0;
  font-size: 28px;
  color: #21304d;
}

.page-subtitle {
  margin: 0;
  font-size: 13px;
  color: #61708e;
}

.page-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.ghost-btn {
  border-radius: 18px;
  border: 1px solid #bfd0f2;
  background: rgba(255, 255, 255, 0.8);
  color: #35507a;
}

.refresh-btn {
  border-radius: 18px;
}

.main-wrapper {
  height: calc(100vh - 63px - 35px - 60px);
  margin: 0 22px;
  border-radius: 18px;
  box-shadow: 0 18px 40px rgba(37, 76, 141, 0.08);
  background: rgba(255, 255, 255, 0.58);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.content-panel {
  flex: 1;
  display: flex;
  border: 1px solid rgba(255, 255, 255, 0.88);
  border-radius: 18px;
  overflow: hidden;
}

.content-area {
  flex: 1;
  overflow: auto;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.92), rgba(247, 250, 255, 0.96));
  padding: 22px;
}

.top-alert {
  margin-bottom: 18px;
}

.hero-card {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  padding: 22px 24px;
  border-radius: 22px;
  background:
    linear-gradient(135deg, rgba(34, 62, 118, 0.96), rgba(62, 107, 196, 0.92)),
    linear-gradient(145deg, #21304d, #4f78d1);
  color: #fff;
  box-shadow: 0 18px 36px rgba(44, 77, 145, 0.24);
  margin-bottom: 18px;
}

.hero-main {
  max-width: 58%;
}

.hero-label {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.12);
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.hero-title {
  margin: 16px 0 10px;
  font-size: 30px;
  line-height: 1.1;
}

.hero-description {
  margin: 0;
  line-height: 1.7;
  color: rgba(255, 255, 255, 0.82);
}

.hero-meta {
  display: grid;
  grid-template-columns: 1fr;
  gap: 10px;
  min-width: 260px;
}

.meta-pill {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px 14px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.1);
}

.meta-key {
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: rgba(255, 255, 255, 0.62);
}

.meta-value {
  font-size: 14px;
  word-break: break-all;
}

.surface-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}

.surface-card {
  border: 1px solid #e6eefc;
  border-radius: 20px;
  box-shadow: none;
}

.wide-card {
  margin-bottom: 16px;
}

.section-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
}

.section-eyebrow {
  font-size: 11px;
  color: #5e77a4;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  margin-bottom: 6px;
}

.section-header h3 {
  margin: 0;
  font-size: 20px;
  color: #21304d;
}

.section-description {
  margin: 0 0 14px;
  color: #6b7690;
  line-height: 1.7;
}

.inline-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 14px;
}

.tool-count {
  color: #5e77a4;
  font-size: 13px;
}

.tool-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.tool-chip {
  padding: 8px 12px;
  border-radius: 999px;
  background: #eef3ff;
  color: #35507a;
  border: 1px solid #d8e3fb;
  font-size: 13px;
}

.mapping-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.mapping-card {
  border: 1px solid #e6eefc;
  border-radius: 16px;
  background: linear-gradient(180deg, #fcfdff, #f4f8ff);
  padding: 14px;
}

.mapping-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;
}

.mapping-name {
  font-weight: 600;
  color: #21304d;
  word-break: break-all;
}

.mapping-count {
  color: #61708e;
  font-size: 12px;
}

.mapping-body {
  margin: 0;
  padding: 12px;
  border-radius: 12px;
  background: #1f2740;
  color: #d8e7ff;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  min-height: 96px;
}

.runtime-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.runtime-item {
  display: grid;
  grid-template-columns: 180px 1fr;
  gap: 12px;
  align-items: flex-start;
  padding: 12px 14px;
  border-radius: 14px;
  background: #f7fbff;
  border: 1px solid #e2ecfb;
}

.runtime-path {
  font-family: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
  color: #1d4067;
  font-size: 13px;
}

.runtime-note {
  color: #66748f;
  line-height: 1.6;
}

.error-text {
  margin: 10px 0 0;
  color: #d3485a;
  font-size: 13px;
}

@media (max-width: 1200px) {
  .hero-card {
    flex-direction: column;
  }

  .hero-main {
    max-width: 100%;
  }

  .surface-grid,
  .mapping-grid {
    grid-template-columns: 1fr;
  }
}
</style>

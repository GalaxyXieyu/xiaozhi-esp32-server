<template>
  <section class="setup-shell">
    <div class="setup-toolbar">
      <el-button type="text" icon="el-icon-arrow-left" @click="$emit('back')">返回 Channel 列表</el-button>
      <div class="setup-toolbar-actions">
        <el-tag size="mini" effect="plain" :type="inventoryTagType">{{ inventoryStatusText }}</el-tag>
        <el-button plain @click="$emit('edit')">编辑 Channel</el-button>
      </div>
    </div>

    <div class="setup-hero">
      <div>
        <div class="setup-eyebrow">Step 1 / 2</div>
        <h2 class="setup-title">先把这个 Channel 接进本地 OpenClaw</h2>
        <p class="setup-subtitle">
          现在只需要完成接入。连接成功后，系统会自动切到详情页，再去看 Agent、绑定和调试。
        </p>
      </div>
      <div class="setup-summary">
        <span class="setup-summary-chip">{{ channel.name || "未命名 Channel" }}</span>
        <span class="setup-summary-chip">{{ connectionReady ? "已接入" : "待接入" }}</span>
      </div>
    </div>

    <el-alert
      v-if="inventory.errorMessage"
      class="setup-alert"
      type="warning"
      :closable="false"
      show-icon
      :title="inventory.errorMessage"
    />

    <div class="setup-card-grid">
      <article class="setup-card done">
        <div class="setup-index">1</div>
        <div>
          <h3>创建 Channel</h3>
          <p>基础信息已经保存，接下来只做本地接入。</p>
        </div>
      </article>

      <article class="setup-card" :class="{ done: Boolean(setupGuide.installCommand) }">
        <div class="setup-index">2</div>
        <div>
          <h3>在本机执行接入命令</h3>
          <p>命令会把本地 OpenClaw 插件绑定到当前 Channel。</p>
        </div>
      </article>
    </div>

    <section class="command-card">
      <div class="command-card-head">
        <div>
          <div class="setup-eyebrow">Local Command</div>
          <h3>复制后到本机终端执行</h3>
        </div>
        <el-button type="primary" :disabled="!setupGuide.installCommand" @click="$emit('copy-command')">复制接入命令</el-button>
      </div>
      <div class="command-preview">{{ setupGuide.installCommand || "接入命令生成中，请稍后再试。" }}</div>
    </section>

    <section class="detect-card">
      <div>
        <div class="setup-eyebrow">Step 2 / 2</div>
        <h3 class="detect-title">接入完成后再检测连接</h3>
        <p class="detect-copy">检测通过后会自动进入详情页，后续再看这个 Channel 自己的 Agent 和绑定。</p>
      </div>
      <div class="detect-actions">
        <el-button :loading="inventoryLoading" @click="$emit('refresh-inventory')">
          {{ connectionReady ? "重新检测" : "检测连接" }}
        </el-button>
      </div>
    </section>

    <div v-if="showConnectionFeedback" class="detect-feedback" :class="`is-${feedbackStatus}`">
      <i class="detect-feedback-icon" :class="feedbackIconClass" />
      <div>
        <div class="detect-feedback-title">{{ feedbackTitle }}</div>
        <div v-if="feedbackDescription" class="detect-feedback-copy">{{ feedbackDescription }}</div>
      </div>
    </div>
  </section>
</template>

<script>
export default {
  name: "OpenClawChannelSetup",
  props: {
    channel: {
      type: Object,
      default: () => ({}),
    },
    inventory: {
      type: Object,
      default: () => ({}),
    },
    setupGuide: {
      type: Object,
      default: () => ({}),
    },
    inventoryLoading: {
      type: Boolean,
      default: false,
    },
    connectionFeedback: {
      type: Object,
      default: () => ({}),
    },
  },
  computed: {
    connectionReady() {
      return Boolean(this.inventory && this.inventory.healthy && (this.inventory.connectedBridgeCount || 0) > 0);
    },
    inventoryStatusText() {
      if (this.inventoryLoading) {
        return "检测中";
      }
      if (this.connectionReady) {
        return "已连接";
      }
      if (this.inventory && this.inventory.errorMessage) {
        return "需检查";
      }
      return "待接入";
    },
    inventoryTagType() {
      if (this.connectionReady) {
        return "success";
      }
      if (this.inventory && this.inventory.errorMessage) {
        return "warning";
      }
      return "info";
    },
    feedbackStatus() {
      if (this.inventoryLoading) {
        return "checking";
      }
      return this.connectionFeedback && this.connectionFeedback.status
        ? this.connectionFeedback.status
        : "idle";
    },
    showConnectionFeedback() {
      return this.feedbackStatus !== "idle";
    },
    feedbackTitle() {
      if (this.feedbackStatus === "checking") {
        return "正在检测连接状态";
      }
      if (this.feedbackStatus === "success") {
        return "连接检测通过";
      }
      if (this.feedbackStatus === "warning") {
        return "连接检测未通过";
      }
      return "";
    },
    feedbackDescription() {
      if (this.feedbackStatus === "checking") {
        return "会向当前 Channel 的探测接口拉取最新状态。";
      }
      const message = this.connectionFeedback && this.connectionFeedback.message
        ? this.connectionFeedback.message
        : "";
      const checkedAt = this.connectionFeedback && this.connectionFeedback.checkedAt
        ? `最近检测时间：${this.formatCheckedAt(this.connectionFeedback.checkedAt)}`
        : "";
      if (message && checkedAt) {
        return `${message} ${checkedAt}`;
      }
      return message || checkedAt;
    },
    feedbackIconClass() {
      if (this.feedbackStatus === "checking") {
        return "el-icon-loading";
      }
      if (this.feedbackStatus === "success") {
        return "el-icon-success";
      }
      if (this.feedbackStatus === "warning") {
        return "el-icon-warning";
      }
      return "";
    },
  },
  methods: {
    formatCheckedAt(value) {
      const date = value ? new Date(value) : null;
      if (!date || Number.isNaN(date.getTime())) {
        return "";
      }
      return date.toLocaleString("zh-CN", {
        hour12: false,
      });
    },
  },
};
</script>

<style scoped>
.setup-shell {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.setup-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.setup-toolbar-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.setup-hero,
.command-card,
.detect-card {
  padding: 24px 26px;
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.96);
  border: 1px solid #e4ebf7;
  box-shadow: 0 18px 40px rgba(124, 140, 177, 0.08);
}

.setup-hero {
  display: flex;
  justify-content: space-between;
  gap: 20px;
}

.setup-eyebrow {
  font-size: 12px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: #8fa0be;
}

.setup-title {
  margin: 10px 0 0;
  font-size: 34px;
  line-height: 1.2;
  color: #1b2742;
}

.setup-subtitle {
  margin: 12px 0 0;
  max-width: 720px;
  color: #61708d;
  line-height: 1.8;
}

.setup-summary {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: flex-end;
  gap: 10px;
}

.setup-summary-chip {
  display: inline-flex;
  align-items: center;
  min-height: 36px;
  padding: 0 14px;
  border-radius: 999px;
  background: #f5f8ff;
  border: 1px solid #dde6f6;
  color: #60708d;
  font-weight: 600;
}

.setup-alert {
  border-radius: 20px;
}

.setup-card-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.setup-card {
  display: flex;
  gap: 14px;
  padding: 20px;
  border-radius: 24px;
  background: linear-gradient(180deg, #fbfcff, #f5f8ff);
  border: 1px solid #e5ebf8;
}

.setup-card.done {
  background: linear-gradient(180deg, #f3fbf4, #eef8f1);
  border-color: #cfe5d2;
}

.setup-card h3,
.command-card-head h3,
.detect-title {
  margin: 0;
  color: #223250;
}

.setup-card p,
.detect-copy {
  margin: 8px 0 0;
  color: #6b7a95;
  line-height: 1.7;
}

.setup-index {
  width: 30px;
  height: 30px;
  flex: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  background: #dde7fb;
  color: #36518f;
  font-weight: 700;
}

.command-card-head,
.detect-card {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}

.command-preview {
  margin-top: 16px;
  padding: 14px 16px;
  border-radius: 18px;
  background: #18233d;
  color: #f7f9ff;
  line-height: 1.7;
  word-break: break-all;
}

.detect-actions {
  display: flex;
  align-items: center;
}

.detect-feedback {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 16px 18px;
  border-radius: 20px;
  border: 1px solid #e4ebf7;
  background: rgba(255, 255, 255, 0.96);
  color: #4f5f7c;
}

.detect-feedback.is-checking {
  background: #f5f8ff;
  border-color: #d9e5fb;
  color: #4266a8;
}

.detect-feedback.is-success {
  background: #f3fbf4;
  border-color: #cfe5d2;
  color: #2f7b45;
}

.detect-feedback.is-warning {
  background: #fff8ed;
  border-color: #f5d6aa;
  color: #9a6424;
}

.detect-feedback-icon {
  margin-top: 2px;
  font-size: 18px;
}

.detect-feedback-title {
  font-weight: 600;
}

.detect-feedback-copy {
  margin-top: 6px;
  line-height: 1.7;
}

@media (max-width: 960px) {
  .setup-toolbar,
  .setup-hero,
  .command-card-head,
  .detect-card {
    flex-direction: column;
    align-items: stretch;
  }

  .setup-card-grid {
    grid-template-columns: 1fr;
  }

  .setup-hero,
  .command-card,
  .detect-card {
    padding: 20px;
  }

  .setup-title {
    font-size: 28px;
  }
}
</style>

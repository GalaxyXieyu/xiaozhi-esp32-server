<template>
  <section class="registry-shell">
    <div class="registry-hero">
      <div>
        <div class="registry-eyebrow">OpenClaw</div>
        <h2 class="registry-title">先选渠道</h2>
        <p class="registry-description">点进详情后，再看 Agent、绑定和调试。</p>
      </div>
      <div class="registry-actions">
        <el-button class="ghost-btn" @click="$emit('back')">返回智能体配置</el-button>
        <el-button class="ghost-btn" @click="$emit('refresh')" :loading="loading">刷新</el-button>
        <el-button type="primary" class="create-btn" @click="$emit('create')">新建 Channel</el-button>
      </div>
    </div>

    <div class="registry-summary">
      <div class="summary-card">
        <span class="summary-label">渠道</span>
        <strong class="summary-value">{{ channels.length }}</strong>
      </div>
      <div class="summary-card">
        <span class="summary-label">可调试</span>
        <strong class="summary-value">{{ healthyCount }}</strong>
      </div>
      <div class="summary-card">
        <span class="summary-label">需处理</span>
        <strong class="summary-value">{{ attentionCount }}</strong>
      </div>
    </div>

    <div v-if="channels.length" class="channel-grid">
      <article
        v-for="channel in channels"
        :key="channel.id"
        class="channel-card"
        :class="{ disabled: channel.enabled === false }"
        @click="$emit('select', channel)"
      >
        <div class="channel-card-head">
          <div>
            <div class="channel-name">{{ channel.name || "未命名 Channel" }}</div>
            <div class="channel-id">{{ channel.id }}</div>
          </div>
          <div class="channel-head-actions" @click.stop>
            <el-tag size="mini" :type="channel.enabled === false ? 'info' : 'success'">
              {{ channel.enabled === false ? "停用" : "启用" }}
            </el-tag>
            <el-dropdown trigger="click" @command="(command) => handleCommand(command, channel)">
              <button type="button" class="menu-btn" aria-label="Channel actions">
                <i class="el-icon-more" />
              </button>
              <el-dropdown-menu slot="dropdown">
                <el-dropdown-item command="edit">编辑</el-dropdown-item>
                <el-dropdown-item command="delete">删除</el-dropdown-item>
              </el-dropdown-menu>
            </el-dropdown>
          </div>
        </div>

        <p class="channel-remark">
          {{ channel.remark || "未填写备注" }}
        </p>

        <div class="channel-metrics">
          <div class="metric-pill">
            <span>状态</span>
            <strong>{{ summaryFor(channel).inventoryLabel }}</strong>
          </div>
          <div class="metric-pill">
            <span>绑定</span>
            <strong>{{ summaryFor(channel).bindingCount }}</strong>
          </div>
          <div class="metric-pill">
            <span>Agent</span>
            <strong>{{ summaryFor(channel).agentCount }}</strong>
          </div>
          <div class="metric-pill">
            <span>设备</span>
            <strong>{{ summaryFor(channel).runtimeCount }}</strong>
          </div>
        </div>

        <div class="channel-card-footer">
          <span class="inventory-hint" :class="summaryFor(channel).inventoryTone">
            {{ summaryFor(channel).inventoryNote }}
          </span>
          <span class="enter-label">进入</span>
        </div>
      </article>
    </div>

    <div v-else class="registry-empty">
      <el-empty description="还没有任何 OpenClaw Channel">
        <el-button type="primary" @click="$emit('create')">创建第一个 Channel</el-button>
      </el-empty>
    </div>
  </section>
</template>

<script>
const createEmptySummary = () => ({
  inventoryLabel: "未同步",
  inventoryTone: "idle",
  inventoryNote: "还没连上 OpenClaw",
  bindingCount: 0,
  agentCount: 0,
  runtimeCount: 0,
});

export default {
  name: "OpenClawChannelRegistry",
  props: {
    channels: {
      type: Array,
      default: () => [],
    },
    summaries: {
      type: Object,
      default: () => ({}),
    },
    loading: {
      type: Boolean,
      default: false,
    },
  },
  computed: {
    healthyCount() {
      return this.channels.filter((channel) => this.summaryFor(channel).inventoryTone === "healthy").length;
    },
    attentionCount() {
      return this.channels.filter((channel) => this.summaryFor(channel).inventoryTone === "attention").length;
    },
  },
  methods: {
    summaryFor(channel) {
      return this.summaries[channel.id] || createEmptySummary();
    },
    handleCommand(command, channel) {
      if (command === "edit") {
        this.$emit("edit", channel);
        return;
      }
      if (command === "delete") {
        this.$emit("delete", channel);
      }
    },
  },
};
</script>

<style scoped>
.registry-shell {
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.registry-hero {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 18px;
  padding: 28px 32px;
  border-radius: 32px;
  background:
    radial-gradient(circle at top right, rgba(127, 168, 255, 0.28), transparent 28%),
    linear-gradient(135deg, rgba(244, 248, 255, 0.98), rgba(255, 255, 255, 0.96));
  border: 1px solid rgba(212, 223, 243, 0.92);
  box-shadow: 0 28px 64px rgba(120, 137, 180, 0.12);
}

.registry-eyebrow {
  font-size: 12px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: #7b8baa;
}

.registry-title {
  margin: 10px 0 6px;
  font-size: 30px;
  line-height: 1.18;
  color: #16213a;
}

.registry-description {
  max-width: 520px;
  margin: 0;
  color: #66758f;
  line-height: 1.6;
}

.registry-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
}

.ghost-btn,
.create-btn {
  height: 40px;
  border-radius: 999px;
  padding: 0 18px;
}

.registry-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.summary-card {
  padding: 16px 20px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid #e4ebf7;
  box-shadow: 0 18px 44px rgba(130, 144, 183, 0.08);
}

.summary-label {
  display: block;
  font-size: 12px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: #7f8ea7;
}

.summary-value {
  display: block;
  margin-top: 6px;
  font-size: 26px;
  color: #16213a;
}

.channel-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 280px));
  justify-content: start;
  gap: 16px;
}

.channel-card {
  padding: 18px;
  border-radius: 24px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(246, 249, 255, 0.94));
  border: 1px solid #dfe7f5;
  box-shadow: 0 24px 56px rgba(125, 141, 181, 0.12);
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
}

.channel-card:hover {
  transform: translateY(-3px);
  border-color: #9cb5ec;
  box-shadow: 0 28px 64px rgba(92, 123, 197, 0.16);
}

.channel-card.disabled {
  opacity: 0.86;
}

.channel-card-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.channel-name {
  font-size: 18px;
  line-height: 1.3;
  color: #14203a;
  font-weight: 700;
}

.channel-id {
  margin-top: 6px;
  color: #7b8ba7;
  font-size: 13px;
  word-break: break-all;
}

.channel-head-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.menu-btn {
  width: 30px;
  height: 30px;
  border: none;
  border-radius: 999px;
  background: #edf3ff;
  color: #4762a7;
  cursor: pointer;
}

.channel-remark {
  min-height: 20px;
  margin: 10px 0 0;
  color: #62738d;
  line-height: 1.5;
  font-size: 13px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.channel-metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin-top: 14px;
}

.metric-pill {
  padding: 8px 10px;
  border-radius: 16px;
  background: #f5f8ff;
  border: 1px solid #e7edf8;
}

.metric-pill span {
  display: block;
  color: #7b89a0;
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  white-space: nowrap;
}

.metric-pill strong {
  display: block;
  margin-top: 4px;
  color: #17233c;
  font-size: 15px;
}

.channel-card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid #e8edf7;
}

.inventory-hint {
  font-size: 12px;
  color: #6d7d97;
  line-height: 1.5;
}

.inventory-hint.healthy {
  color: #2c7a46;
}

.inventory-hint.attention {
  color: #bb7a12;
}

.enter-label {
  color: #4762a7;
  font-weight: 700;
  white-space: nowrap;
}

.registry-empty {
  padding: 48px 0 36px;
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px dashed #d8e1f3;
}

@media (max-width: 960px) {
  .registry-hero {
    flex-direction: column;
    padding: 24px;
  }

  .registry-title {
    font-size: 28px;
  }

  .registry-actions {
    width: 100%;
    justify-content: flex-start;
  }

  .registry-summary {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .channel-grid,
  .channel-metrics {
    grid-template-columns: 1fr;
  }

  .channel-card {
    padding: 16px;
  }

  .channel-card-footer {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>

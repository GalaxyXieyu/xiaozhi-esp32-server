<template>
  <section class="registry-shell">
    <div class="registry-toolbar">
      <div class="toolbar-main">
        <h2 class="toolbar-title">Channels</h2>
        <div class="toolbar-stats">
          <span class="toolbar-stat">{{ channels.length }} 个渠道</span>
          <span class="toolbar-stat">{{ healthyCount }} 个可调试</span>
          <span class="toolbar-stat">{{ attentionCount }} 个需处理</span>
        </div>
      </div>
      <div class="registry-actions">
        <el-button class="ghost-btn" @click="$emit('back')">返回智能体配置</el-button>
        <el-button class="ghost-btn" @click="$emit('refresh')" :loading="loading">刷新</el-button>
        <el-button type="primary" class="create-btn" @click="$emit('create')">新建 Channel</el-button>
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
  gap: 16px;
}

.registry-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 12px 16px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid #e2e9f6;
  box-shadow: 0 14px 32px rgba(123, 140, 179, 0.08);
}

.toolbar-main {
  display: flex;
  align-items: center;
  gap: 18px;
  min-width: 0;
  flex: 1;
}

.toolbar-title {
  margin: 0;
  font-size: 22px;
  line-height: 1;
  color: #16213a;
  white-space: nowrap;
}

.toolbar-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.toolbar-stat {
  display: inline-flex;
  align-items: center;
  min-height: 34px;
  padding: 0 12px;
  border-radius: 999px;
  background: #f4f7fd;
  border: 1px solid #e3eaf7;
  color: #50617c;
  font-size: 13px;
  line-height: 1;
}

.ghost-btn,
.create-btn {
  height: 36px;
  border-radius: 999px;
  padding: 0 16px;
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
  .registry-toolbar,
  .toolbar-main {
    flex-direction: column;
    align-items: flex-start;
  }

  .toolbar-title {
    font-size: 20px;
  }

  .registry-actions {
    width: 100%;
    justify-content: flex-start;
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

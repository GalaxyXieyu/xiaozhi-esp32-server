<template>
  <div class="session-panel" v-loading="loading">
    <div class="session-toolbar">
      <span class="panel-title">会话列表</span>
      <el-button size="mini" plain @click="$emit('refresh')">刷新</el-button>
    </div>
    <div v-if="sessions.length" class="session-list">
      <div
        v-for="session in sessions"
        :key="session.sessionId"
        :class="['session-item', { active: session.sessionId === currentSessionId }]"
        @click="$emit('select', session)"
      >
        <div class="session-header">
          <span class="session-device">{{ session.deviceId || session.macAddress || '未知设备' }}</span>
          <span class="session-count">{{ session.eventCount || 0 }} 条</span>
        </div>
        <div class="session-id">{{ session.sessionId }}</div>
        <div class="session-time">{{ formatTime(session.createdAt) }}</div>
      </div>
    </div>
    <el-empty v-else description="当前还没有调试时间线数据" :image-size="72" />
  </div>
</template>

<script>
import { formatTime } from './constants';

export default {
  name: 'DebugTimelineSessionList',
  props: {
    sessions: {
      type: Array,
      default: () => [],
    },
    currentSessionId: {
      type: String,
      default: '',
    },
    loading: {
      type: Boolean,
      default: false,
    },
  },
  methods: {
    formatTime,
  },
};
</script>

<style scoped>
.session-panel {
  background: #f7f9fc;
  border: 1px solid #e8edf5;
  border-radius: 18px;
  padding: 16px;
  box-sizing: border-box;
}

.session-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}

.panel-title {
  font-size: 16px;
  font-weight: 600;
  color: #2f3a53;
}

.session-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: 560px;
  overflow-y: auto;
}

.session-item {
  padding: 12px;
  border-radius: 14px;
  background: #fff;
  border: 1px solid transparent;
  cursor: pointer;
  transition: all 0.2s ease;
}

.session-item.active {
  border-color: #5778ff;
  box-shadow: 0 10px 24px rgba(87, 120, 255, 0.12);
}

.session-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 6px;
}

.session-device {
  font-size: 13px;
  font-weight: 600;
  color: #33415c;
  word-break: break-all;
}

.session-count,
.session-time,
.session-id {
  font-size: 12px;
  color: #6f7a91;
}

.session-id {
  word-break: break-all;
  margin-bottom: 6px;
}
</style>

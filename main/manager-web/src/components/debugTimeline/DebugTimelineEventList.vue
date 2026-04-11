<template>
  <div class="timeline-list" v-loading="loading">
    <template v-if="groupedEvents.length">
      <div v-for="item in groupedEvents" :key="item.key" class="timeline-wrapper">
        <div v-if="item.type === 'turn-divider'" class="turn-divider">
          <span>Turn {{ item.turnId }}</span>
        </div>
        <div v-else class="timeline-event">
          <div class="event-meta">
            <span class="event-time">{{ formatTime(item.createdAt) }}</span>
            <el-tag size="mini" :type="tagTypeForSource(item.eventSource)">{{ item.eventSource || 'unknown' }}</el-tag>
            <el-tag size="mini" effect="plain">{{ item.eventType }}</el-tag>
            <el-tag v-if="item.origin" size="mini" effect="plain">{{ item.origin }}</el-tag>
            <el-tag v-if="item.status" size="mini" :type="tagTypeForStatus(item.status)">{{ item.status }}</el-tag>
          </div>
          <div class="event-summary">{{ item.summaryText || item.eventType }}</div>
          <div class="event-extra">
            <span v-if="item.deviceId">设备: {{ item.deviceId }}</span>
            <span v-if="item.requestId">请求: {{ item.requestId }}</span>
            <span v-if="item.sentenceId">句子: {{ item.sentenceId }}</span>
            <span v-if="item.runtimeAccount">账号: {{ item.runtimeAccount }}</span>
            <span v-if="item.speaker">说话人: {{ item.speaker }}</span>
          </div>
          <div v-if="item.payloadJson" class="payload-toggle">
            <el-button type="text" size="mini" @click="togglePayload(item.id)">
              {{ expandedPayload[item.id] ? '收起详情' : '展开详情' }}
            </el-button>
          </div>
          <pre v-if="item.payloadJson && expandedPayload[item.id]" class="payload-block">{{ formatPayload(item.payloadJson) }}</pre>
        </div>
      </div>
    </template>
    <el-empty v-else description="当前筛选条件下没有事件" :image-size="72" />
  </div>
</template>

<script>
import {
  buildGroupedEvents,
  formatPayload,
  formatTime,
  tagTypeForSource,
  tagTypeForStatus,
} from './constants';

export default {
  name: 'DebugTimelineEventList',
  props: {
    events: {
      type: Array,
      default: () => [],
    },
    loading: {
      type: Boolean,
      default: false,
    },
  },
  data() {
    return {
      expandedPayload: {},
    };
  },
  computed: {
    groupedEvents() {
      return buildGroupedEvents(this.events);
    },
  },
  watch: {
    events() {
      this.expandedPayload = {};
    },
  },
  methods: {
    formatPayload,
    formatTime,
    tagTypeForSource,
    tagTypeForStatus,
    togglePayload(eventId) {
      this.$set(this.expandedPayload, eventId, !this.expandedPayload[eventId]);
    },
  },
};
</script>

<style scoped>
.timeline-list {
  max-height: 520px;
  overflow-y: auto;
  padding-right: 4px;
}

.timeline-wrapper + .timeline-wrapper {
  margin-top: 10px;
}

.turn-divider {
  display: inline-flex;
  align-items: center;
  padding: 6px 12px;
  border-radius: 999px;
  background: #e9efff;
  color: #4260d3;
  font-size: 12px;
  font-weight: 600;
}

.timeline-event {
  background: #fff;
  border: 1px solid #e8edf5;
  border-radius: 16px;
  padding: 14px;
}

.event-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}

.event-time {
  font-size: 12px;
  color: #7a879d;
}

.event-summary {
  font-size: 14px;
  line-height: 1.6;
  color: #26324a;
  font-weight: 500;
  word-break: break-word;
}

.event-extra {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 10px;
  font-size: 12px;
  color: #72809a;
}

.payload-toggle {
  margin-top: 6px;
}

.payload-block {
  margin: 8px 0 0;
  padding: 12px;
  border-radius: 12px;
  background: #0f172a;
  color: #dbe5ff;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>

<template>
  <el-dialog
    :title="dialogTitle"
    :visible.sync="dialogVisible"
    width="88%"
    :before-close="handleClose"
    custom-class="debug-timeline-dialog"
  >
    <div class="debug-layout">
      <DebugTimelineSessionList
        :sessions="sessions"
        :current-session-id="currentSessionId"
        :loading="loadingSessions"
        @refresh="loadSessions"
        @select="selectSession"
      />

      <div class="timeline-panel">
        <div class="timeline-toolbar">
          <div class="toolbar-main">
            <span class="panel-title">调试时间线</span>
            <span v-if="currentSessionId" class="toolbar-session">Session: {{ currentSessionId }}</span>
          </div>
          <div class="toolbar-actions">
            <el-select
              v-model="filters.eventSource"
              clearable
              size="mini"
              placeholder="来源"
              @change="refreshCurrentSession"
            >
              <el-option v-for="option in eventSourceOptions" :key="option.value" :label="option.label" :value="option.value" />
            </el-select>
            <el-select
              v-model="filters.origin"
              clearable
              size="mini"
              placeholder="归因"
              @change="refreshCurrentSession"
            >
              <el-option v-for="option in originOptions" :key="option.value" :label="option.label" :value="option.value" />
            </el-select>
            <el-select
              v-model="filters.status"
              clearable
              size="mini"
              placeholder="状态"
              @change="refreshCurrentSession"
            >
              <el-option v-for="option in statusOptions" :key="option.value" :label="option.label" :value="option.value" />
            </el-select>
            <el-input
              v-model.trim="filters.turnId"
              size="mini"
              placeholder="Turn ID"
              class="turn-input"
              @keyup.enter.native="refreshCurrentSession"
            />
            <el-button size="mini" type="primary" @click="refreshCurrentSession">筛选</el-button>
          </div>
        </div>

        <DebugTimelineSummaryStrip
          v-if="currentSessionId"
          :summary="summary"
          :loading="loadingSummary"
        />

        <DebugTimelineEventList
          v-if="currentSessionId"
          :events="events"
          :loading="loadingEvents"
        />

        <el-empty v-else description="请选择左侧会话查看调试时间线" :image-size="72" />
      </div>
    </div>
  </el-dialog>
</template>

<script>
import Api from '@/apis/api';
import DebugTimelineEventList from './debugTimeline/DebugTimelineEventList.vue';
import DebugTimelineSessionList from './debugTimeline/DebugTimelineSessionList.vue';
import DebugTimelineSummaryStrip from './debugTimeline/DebugTimelineSummaryStrip.vue';
import {
  createEmptyFilters,
  createEmptySummary,
  EVENT_SOURCE_OPTIONS,
  ORIGIN_OPTIONS,
  STATUS_OPTIONS,
} from './debugTimeline/constants';

export default {
  name: 'DebugTimelineDialog',
  components: {
    DebugTimelineEventList,
    DebugTimelineSessionList,
    DebugTimelineSummaryStrip,
  },
  props: {
    visible: {
      type: Boolean,
      default: false,
    },
    agentId: {
      type: String,
      required: true,
    },
    agentName: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      dialogVisible: false,
      sessions: [],
      events: [],
      currentSessionId: '',
      currentDeviceId: '',
      summary: createEmptySummary(),
      loadingSessions: false,
      loadingEvents: false,
      loadingSummary: false,
      filters: createEmptyFilters(),
      eventSourceOptions: EVENT_SOURCE_OPTIONS,
      originOptions: ORIGIN_OPTIONS,
      statusOptions: STATUS_OPTIONS,
      page: 1,
      limit: 40,
    };
  },
  computed: {
    dialogTitle() {
      return `调试时间线 · ${this.agentName || ''}`;
    },
  },
  watch: {
    visible(val) {
      this.dialogVisible = val;
      if (val) {
        this.loadSessions();
      } else {
        this.resetState();
      }
    },
    dialogVisible(val) {
      if (!val) {
        this.$emit('update:visible', false);
      }
    },
  },
  methods: {
    handleClose() {
      this.dialogVisible = false;
    },
    resetState() {
      this.sessions = [];
      this.events = [];
      this.currentSessionId = '';
      this.currentDeviceId = '';
      this.summary = createEmptySummary();
      this.filters = createEmptyFilters();
    },
    loadSessions() {
      if (!this.agentId) {
        return;
      }

      this.loadingSessions = true;
      Api.agent.getDebugSessions(this.agentId, { page: this.page, limit: this.limit }, (res) => {
        const list = (((res || {}).data || {}).data || {}).list || [];
        this.sessions = Array.isArray(list) ? list : [];
        this.loadingSessions = false;

        if (this.sessions.length && !this.currentSessionId) {
          this.selectSession(this.sessions[0]);
        }
      }, () => {
        this.loadingSessions = false;
        this.$message.error('获取调试会话失败');
      });
    },
    selectSession(session) {
      this.currentSessionId = session.sessionId;
      this.currentDeviceId = session.deviceId || '';
      this.refreshCurrentSession();
    },
    refreshCurrentSession() {
      if (!this.currentSessionId) {
        return;
      }
      this.loadSummary();
      this.loadEvents();
    },
    loadSummary() {
      this.loadingSummary = true;
      Api.agent.getDebugSummary(this.agentId, this.currentSessionId, { deviceId: this.currentDeviceId }, (res) => {
        this.summary = (((res || {}).data || {}).data) || createEmptySummary();
        this.loadingSummary = false;
      }, () => {
        this.summary = createEmptySummary();
        this.loadingSummary = false;
      });
    },
    loadEvents() {
      this.loadingEvents = true;
      const params = {
        deviceId: this.currentDeviceId,
        eventSource: this.filters.eventSource,
        origin: this.filters.origin,
        status: this.filters.status,
        turnId: this.filters.turnId,
      };

      Api.agent.getDebugTimeline(this.agentId, this.currentSessionId, params, (res) => {
        const data = (((res || {}).data || {}).data) || [];
        this.events = Array.isArray(data) ? data : [];
        this.loadingEvents = false;
      }, () => {
        this.events = [];
        this.loadingEvents = false;
        this.$message.error('获取调试时间线失败');
      });
    },
  },
};
</script>

<style scoped>
.debug-layout {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: 18px;
  min-height: 620px;
}

.timeline-panel {
  background: #f7f9fc;
  border: 1px solid #e8edf5;
  border-radius: 18px;
  padding: 16px;
  box-sizing: border-box;
}

.timeline-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}

.toolbar-main {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.toolbar-session {
  font-size: 12px;
  color: #75829b;
  word-break: break-all;
}

.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.turn-input {
  width: 180px;
}

.panel-title {
  font-size: 16px;
  font-weight: 600;
  color: #2f3a53;
}

@media (max-width: 1100px) {
  .debug-layout {
    grid-template-columns: 1fr;
  }
}
</style>

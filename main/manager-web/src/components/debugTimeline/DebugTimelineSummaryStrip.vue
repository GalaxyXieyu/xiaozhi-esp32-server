<template>
  <div class="summary-strip" v-loading="loading">
    <div class="summary-card">
      <div class="summary-label">事件总数</div>
      <div class="summary-value">{{ summary.totalCount || 0 }}</div>
    </div>
    <div class="summary-card">
      <div class="summary-label">Abort</div>
      <div class="summary-value">{{ summary.abortCount || 0 }}</div>
    </div>
    <div class="summary-card">
      <div class="summary-label">OpenClaw</div>
      <div class="summary-value">{{ summary.openclawCount || 0 }}</div>
    </div>
    <div class="summary-card">
      <div class="summary-label">Fallback</div>
      <div class="summary-value">{{ summary.fallbackCount || 0 }}</div>
    </div>
    <div class="summary-card">
      <div class="summary-label">TTS Stop</div>
      <div class="summary-value">{{ summary.ttsStopCount || 0 }}</div>
    </div>
  </div>
</template>

<script>
import { createEmptySummary } from './constants';

export default {
  name: 'DebugTimelineSummaryStrip',
  props: {
    summary: {
      type: Object,
      default: () => createEmptySummary(),
    },
    loading: {
      type: Boolean,
      default: false,
    },
  },
};
</script>

<style scoped>
.summary-strip {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 16px;
}

.summary-card {
  background: #fff;
  border: 1px solid #e8edf5;
  border-radius: 14px;
  padding: 12px;
}

.summary-label {
  font-size: 12px;
  color: #7b879d;
  margin-bottom: 8px;
}

.summary-value {
  font-size: 22px;
  font-weight: 700;
  color: #24324a;
}

@media (max-width: 1100px) {
  .summary-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>

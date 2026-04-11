export const EVENT_SOURCE_OPTIONS = [
  { label: '设备', value: 'device' },
  { label: '服务端', value: 'server' },
  { label: 'ASR', value: 'asr' },
  { label: 'LLM', value: 'llm' },
  { label: 'TTS', value: 'tts' },
  { label: 'OpenClaw', value: 'openclaw' },
  { label: '系统', value: 'system' },
];

export const ORIGIN_OPTIONS = [
  { label: '本地 Agent', value: 'local_agent' },
  { label: 'OpenClaw', value: 'openclaw' },
  { label: '系统', value: 'system' },
  { label: '未知', value: 'unknown' },
];

export const STATUS_OPTIONS = [
  { label: '正常', value: 'ok' },
  { label: '异常', value: 'error' },
  { label: '超时', value: 'timeout' },
  { label: '回退', value: 'fallback' },
  { label: '丢弃', value: 'dropped' },
];

export const createEmptySummary = () => ({
  totalCount: 0,
  abortCount: 0,
  openclawCount: 0,
  fallbackCount: 0,
  ttsStopCount: 0,
});

export const createEmptyFilters = () => ({
  eventSource: '',
  origin: '',
  status: '',
  turnId: '',
});

export function formatTime(value) {
  if (!value) {
    return '--';
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return String(value);
  }

  const year = date.getFullYear();
  const month = `${date.getMonth() + 1}`.padStart(2, '0');
  const day = `${date.getDate()}`.padStart(2, '0');
  const hour = `${date.getHours()}`.padStart(2, '0');
  const minute = `${date.getMinutes()}`.padStart(2, '0');
  const second = `${date.getSeconds()}`.padStart(2, '0');
  return `${year}-${month}-${day} ${hour}:${minute}:${second}`;
}

export function tagTypeForSource(source) {
  const mapping = {
    device: '',
    server: 'info',
    asr: 'success',
    llm: 'warning',
    tts: 'danger',
    openclaw: 'primary',
    system: 'info',
  };
  return mapping[source] || 'info';
}

export function tagTypeForStatus(status) {
  const mapping = {
    ok: 'success',
    error: 'danger',
    timeout: 'warning',
    fallback: 'warning',
    dropped: 'info',
  };
  return mapping[status] || 'info';
}

export function formatPayload(payloadJson) {
  if (!payloadJson) {
    return '';
  }

  try {
    return JSON.stringify(JSON.parse(payloadJson), null, 2);
  } catch (error) {
    return payloadJson;
  }
}

export function buildGroupedEvents(events = []) {
  const result = [];
  let lastTurnId = null;

  events.forEach((event, index) => {
    const turnId = event.turnId || '';
    if (turnId && turnId !== lastTurnId) {
      result.push({
        type: 'turn-divider',
        key: `turn-${turnId}`,
        turnId,
      });
      lastTurnId = turnId;
    }

    result.push({
      ...event,
      type: 'event',
      key: `event-${event.id || index}`,
    });
  });

  return result;
}

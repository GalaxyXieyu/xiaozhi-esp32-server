export const DEBUG_HISTORY_PREFIX = "openclaw-debug-history:";
export const DEBUG_UI_MODE_KEY = "openclaw-debug-ui-mode";
export const MAX_DEBUG_HISTORY_SESSIONS = 6;
export const MAX_DEBUG_HISTORY_MESSAGES = 30;
export const MAX_DEBUG_STATUS_EVENTS = 24;

export const STATUS_EVENT_TYPES = new Set([
  "accepted",
  "agent_bound",
  "progress",
  "subagent_spawned",
  "subagent_completed",
  "browser_audio_ready",
  "device_push_started",
  "device_push_succeeded",
  "device_push_failed",
  "failed",
]);

export const createDebugSessionId = () => `web-debug-${Date.now()}`;

export const buildConnectionKey = (item = {}) => `${item.sessionId || ""}::${item.deviceId || ""}`;

export const createEmptyDeliveryBinding = () => ({
  enabled: false,
  deliveryChannel: "",
  accountId: "",
  accountLabel: "",
  target: "",
  targetLabel: "",
  threadId: "",
  format: "text",
});

export const createEmptyDebugForm = () => ({
  account: "",
  bridgeId: "",
  agentId: "",
  agentName: "",
  connectionKey: "",
  sessionId: "",
  deviceId: "",
  speaker: "后台调试",
  pushToDevice: false,
  browserAudio: true,
  inputText: "",
  debugSessionId: createDebugSessionId(),
  deliveryBinding: createEmptyDeliveryBinding(),
});

export const safeParseHistory = (raw) => {
  if (!raw) {
    return [];
  }
  try {
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : [];
  } catch (error) {
    return [];
  }
};

export const formatHistoryTime = (timestamp) => {
  if (!timestamp) {
    return "";
  }
  const date = new Date(timestamp);
  if (Number.isNaN(date.getTime())) {
    return "";
  }
  return `${date.getMonth() + 1}/${date.getDate()} ${date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}`;
};

export const normalizeDebugMessage = (item = {}) => {
  const role = typeof item.role === "string" ? item.role : "";
  const text = typeof item.text === "string" ? item.text : "";
  if (!["user", "assistant", "system"].includes(role) || !text.trim()) {
    return null;
  }
  return {
    id: typeof item.id === "string" ? item.id : "",
    role,
    text,
    meta: typeof item.meta === "string" ? item.meta : "",
    turnId: typeof item.turnId === "string" ? item.turnId : "",
  };
};

export const sanitizeDebugMessages = (messages = []) => {
  const seenIds = new Set();
  return (Array.isArray(messages) ? messages : []).reduce((list, rawItem) => {
    const item = normalizeDebugMessage(rawItem);
    if (!item) {
      return list;
    }
    if (item.id && seenIds.has(item.id)) {
      return list;
    }
    if (item.id) {
      seenIds.add(item.id);
    }

    const last = list[list.length - 1];
    const sameAssistantTurn = Boolean(
      last &&
      last.role === "assistant" &&
      item.role === "assistant" &&
      String(last.text || "").trim() === String(item.text || "").trim() &&
      (
        (last.turnId && item.turnId && last.turnId === item.turnId) ||
        (!last.turnId && !item.turnId)
      )
    );
    if (sameAssistantTurn) {
      list[list.length - 1] = item;
      return list;
    }

    list.push(item);
    return list;
  }, []);
};

export const normalizeDebugStatus = (item = {}) => {
  const text = typeof item.text === "string" ? item.text : "";
  if (!text.trim()) {
    return null;
  }
  return {
    id: typeof item.id === "string" ? item.id : "",
    text,
    meta: typeof item.meta === "string" ? item.meta : "",
    tone: typeof item.tone === "string" ? item.tone : "info",
    eventType: typeof item.eventType === "string" ? item.eventType : "system",
  };
};

export const sanitizeDebugStatuses = (events = []) => {
  const seenIds = new Set();
  return (Array.isArray(events) ? events : []).reduce((list, rawItem) => {
    const item = normalizeDebugStatus(rawItem);
    if (!item) {
      return list;
    }
    if (item.id && seenIds.has(item.id)) {
      return list;
    }
    if (item.id) {
      seenIds.add(item.id);
    }
    list.push(item);
    return list;
  }, []);
};

export const normalizeHistoryEntry = (item = {}) => ({
  sessionId: typeof item.sessionId === "string" ? item.sessionId : "",
  account: typeof item.account === "string" ? item.account : "",
  bridgeId: typeof item.bridgeId === "string" ? item.bridgeId : "",
  connectionKey: typeof item.connectionKey === "string" ? item.connectionKey : "",
  targetSessionId: typeof item.targetSessionId === "string" ? item.targetSessionId : "",
  targetDeviceId: typeof item.targetDeviceId === "string" ? item.targetDeviceId : "",
  agentId: typeof item.agentId === "string" ? item.agentId : "",
  agentName: typeof item.agentName === "string" ? item.agentName : "",
  pushToDevice: Boolean(item.pushToDevice),
  browserAudio: item.browserAudio !== false,
  deliveryBinding: {
    ...createEmptyDeliveryBinding(),
    ...(((item && item.deliveryBinding) && typeof item.deliveryBinding === "object") ? item.deliveryBinding : {}),
  },
  traceNextSeq: Number.isInteger(item.traceNextSeq) ? item.traceNextSeq : 0,
  latestBrowserAudioText: typeof item.latestBrowserAudioText === "string" ? item.latestBrowserAudioText : "",
  updatedAt: Number.isFinite(item.updatedAt) ? item.updatedAt : Date.now(),
  messages: sanitizeDebugMessages(item.messages).slice(-MAX_DEBUG_HISTORY_MESSAGES),
  statusEvents: sanitizeDebugStatuses(item.statusEvents).slice(-MAX_DEBUG_STATUS_EVENTS),
});

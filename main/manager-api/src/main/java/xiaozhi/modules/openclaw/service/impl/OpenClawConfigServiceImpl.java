package xiaozhi.modules.openclaw.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.type.TypeReference;

import lombok.AllArgsConstructor;
import xiaozhi.common.constant.Constant;
import xiaozhi.common.utils.JsonUtils;
import xiaozhi.modules.openclaw.dto.OpenClawAgentBindingDTO;
import xiaozhi.modules.openclaw.dto.OpenClawChannelDTO;
import xiaozhi.modules.openclaw.dto.OpenClawChannelInventoryDTO;
import xiaozhi.modules.openclaw.dto.OpenClawChannelInventoryDTO.BridgeItem;
import xiaozhi.modules.openclaw.dto.OpenClawChannelInventoryDTO.OptionItem;
import xiaozhi.modules.openclaw.dto.OpenClawChannelSetupGuideDTO;
import xiaozhi.modules.openclaw.dto.OpenClawClearSessionRequestDTO;
import xiaozhi.modules.openclaw.dto.OpenClawClearSessionResponseDTO;
import xiaozhi.modules.openclaw.dto.OpenClawDebugChatRequestDTO;
import xiaozhi.modules.openclaw.dto.OpenClawDebugChatResponseDTO;
import xiaozhi.modules.openclaw.service.OpenClawConfigService;
import xiaozhi.modules.sys.dao.SysParamsDao;
import xiaozhi.modules.sys.entity.SysParamsEntity;
import xiaozhi.modules.sys.service.SysParamsService;

@Service
@AllArgsConstructor
public class OpenClawConfigServiceImpl implements OpenClawConfigService {
    private final SysParamsService sysParamsService;
    private final SysParamsDao sysParamsDao;
    private final RestTemplate restTemplate;

    @Override
    public List<OpenClawChannelDTO> getChannels() {
        return loadChannels();
    }

    @Override
    public List<OpenClawChannelDTO> saveChannels(List<OpenClawChannelDTO> channels, String serverOrigin) {
        List<OpenClawChannelDTO> normalized = new ArrayList<>();
        if (channels != null) {
            for (OpenClawChannelDTO channel : channels) {
                if (channel == null) {
                    continue;
                }
                OpenClawChannelDTO normalizedChannel = normalizeChannel(channel, serverOrigin);
                if (StringUtils.isBlank(normalizedChannel.getName()) || StringUtils.isBlank(normalizedChannel.getBaseUrl())) {
                    continue;
                }
                normalized.add(normalizedChannel);
            }
        }
        persistParam(
                Constant.SERVER_OPENCLAW_CHANNELS,
                JsonUtils.toJsonString(normalized),
                "json",
                "OpenClaw channels"
        );
        return normalized;
    }

    @Override
    public OpenClawChannelSetupGuideDTO getChannelSetupGuide(String channelId, String channelName, String serverOrigin) {
        String normalizedOrigin = trimTrailingSlash(serverOrigin);
        OpenClawChannelDTO channel = loadChannels().stream()
                .filter(item -> StringUtils.equals(item.getId(), channelId))
                .findFirst()
                .map(item -> normalizeChannel(item, normalizedOrigin))
                .orElse(null);
        if (channel == null) {
            channel = normalizeChannel(createTransientChannel(channelId, channelName), normalizedOrigin);
        }

        String serverSecret = StringUtils.trimToEmpty(sysParamsService.getValue(Constant.SERVER_SECRET, true));
        String defaultAgentId = "main";
        OpenClawChannelSetupGuideDTO guide = new OpenClawChannelSetupGuideDTO();
        guide.setChannelId(channel.getId());
        guide.setChannelName(channel.getName());
        guide.setServerUrl(normalizedOrigin);
        guide.setBaseUrl(channel.getBaseUrl());
        guide.setInventoryPath(channel.getInventoryPath());
        guide.setDefaultAgentId(defaultAgentId);
        guide.setAccessTokenConfigured(StringUtils.isNotBlank(serverSecret));
        guide.setInstallCommand(buildInstallCommand(normalizedOrigin, serverSecret, channel.getId(), channel.getName(), defaultAgentId));
        return guide;
    }

    @Override
    public OpenClawChannelInventoryDTO getChannelInventory(String channelId) {
        OpenClawChannelInventoryDTO inventory = new OpenClawChannelInventoryDTO();
        inventory.setChannelId(channelId);

        OpenClawChannelDTO channel = loadChannels().stream()
                .filter(item -> StringUtils.equals(item.getId(), channelId))
                .findFirst()
                .orElse(null);
        if (channel == null) {
            inventory.setErrorMessage("未找到对应的 OpenClaw channel");
            return inventory;
        }
        if (Boolean.FALSE.equals(channel.getEnabled())) {
            inventory.setErrorMessage("当前 OpenClaw channel 已禁用");
            return inventory;
        }

        String sourceUrl = buildInventoryUrl(channel);
        inventory.setSourceUrl(sourceUrl);

        try {
            HttpHeaders headers = new HttpHeaders();
            if (StringUtils.isNotBlank(channel.getAccessToken())) {
                headers.setBearerAuth(channel.getAccessToken());
                headers.add("X-OpenClaw-Token", channel.getAccessToken());
            }
            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(sourceUrl, HttpMethod.GET, requestEntity, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || StringUtils.isBlank(response.getBody())) {
                inventory.setErrorMessage("OpenClaw inventory 接口未返回有效内容");
                return inventory;
            }

            Map<String, Object> payload = JsonUtils.parseObject(response.getBody(), new TypeReference<Map<String, Object>>() {
            });
            if (payload == null) {
                inventory.setErrorMessage("OpenClaw inventory 返回 JSON 为空");
                return inventory;
            }
            Boolean payloadOk = firstBoolean(payload, new String[]{"ok"});
            if (Boolean.FALSE.equals(payloadOk)) {
                inventory.setErrorMessage(StringUtils.defaultIfBlank(
                        firstString(payload, new String[]{"message", "errorMessage", "msg"}),
                        "OpenClaw inventory 接口返回失败"
                ));
                return inventory;
            }
            Map<String, Object> root = unwrapData(payload);
            Boolean rootHealthy = firstBoolean(root, new String[]{"healthy", "ok"});
            if (Boolean.FALSE.equals(rootHealthy)) {
                inventory.setErrorMessage(StringUtils.defaultIfBlank(
                        firstString(root, new String[]{"errorMessage", "message", "msg"}),
                        "OpenClaw inventory 当前不可用"
                ));
            }

            inventory.setRuntimeAccounts(extractOptions(root,
                    new String[]{"runtimeAccounts", "accounts", "runtimeAccountList", "runtimes", "runtime_accounts"},
                    new String[]{"id", "accountId", "runtimeId", "value", "key"},
                    new String[]{"label", "name", "accountName", "displayName", "title", "id"}));
            inventory.setAgents(extractOptions(root,
                    new String[]{"agents", "peerAgents", "agentInventory", "agentList", "peerAgentList"},
                    new String[]{"id", "agentId", "peerAgentId", "value", "key"},
                    new String[]{"label", "name", "agentName", "displayName", "title", "id"}));
            inventory.setBridges(extractBridges(root));
            inventory.setAccountAgents(extractAccountAgents(root));
            inventory.setConnectedBridgeCount((int) inventory.getBridges().stream()
                    .filter(item -> Boolean.TRUE.equals(item.getConnected()))
                    .count());
            if (!Boolean.FALSE.equals(rootHealthy)) {
                inventory.setHealthy(true);
            }
        } catch (Exception e) {
            inventory.setErrorMessage("拉取 OpenClaw inventory 失败: " + e.getMessage());
        }
        return inventory;
    }

    @Override
    public OpenClawDebugChatResponseDTO directChat(String channelId, OpenClawDebugChatRequestDTO request) {
        OpenClawChannelDTO channel = loadChannels().stream()
                .filter(item -> StringUtils.equals(item.getId(), channelId))
                .findFirst()
                .orElse(null);
        if (channel == null) {
            throw new IllegalStateException("未找到对应的 OpenClaw channel");
        }
        if (Boolean.FALSE.equals(channel.getEnabled())) {
            throw new IllegalStateException("当前 OpenClaw channel 已禁用");
        }

        String sourceUrl = buildChannelApiUrl(channel, "/direct-chat");
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("account", StringUtils.trimToEmpty(request.getAccount()));
        requestBody.put("bridgeId", StringUtils.trimToEmpty(request.getBridgeId()));
        requestBody.put("agentId", StringUtils.trimToEmpty(request.getAgentId()));
        requestBody.put("agentName", StringUtils.trimToEmpty(request.getAgentName()));
        requestBody.put("debugSessionId", StringUtils.trimToEmpty(request.getDebugSessionId()));
        requestBody.put("speaker", StringUtils.trimToEmpty(request.getSpeaker()));
        requestBody.put("text", StringUtils.trimToEmpty(request.getText()));

        Map<String, Object> payload = requestChannelApi(channel, "/direct-chat", HttpMethod.POST, requestBody);
        Map<String, Object> root = unwrapData(payload);
        Object rawResult = root.get("result");
        Map<String, Object> result = rawResult instanceof Map<?, ?> map ? castMap(map) : new LinkedHashMap<>();

        OpenClawDebugChatResponseDTO response = new OpenClawDebugChatResponseDTO();
        response.setChannelId(channelId);
        response.setSourceUrl(sourceUrl);
        response.setAccount(StringUtils.defaultIfBlank(firstString(root, new String[]{"account"}), request.getAccount()));
        response.setBridgeId(StringUtils.defaultIfBlank(firstString(root, new String[]{"bridgeId"}), request.getBridgeId()));
        response.setDebugSessionId(firstString(root, new String[]{"debugSessionId", "sessionId"}));
        response.setPeerId(firstString(root, new String[]{"peerId"}));
        response.setAgentId(StringUtils.defaultIfBlank(firstString(result, new String[]{"agentId"}), request.getAgentId()));
        response.setAgentName(StringUtils.defaultIfBlank(firstString(result, new String[]{"agentName"}), request.getAgentName()));
        response.setReplyText(extractReplyText(result, rawResult));
        response.setRawResult(result);
        return response;
    }

    @Override
    public OpenClawClearSessionResponseDTO clearSession(String channelId, OpenClawClearSessionRequestDTO request) {
        OpenClawChannelDTO channel = loadChannels().stream()
                .filter(item -> StringUtils.equals(item.getId(), channelId))
                .findFirst()
                .orElse(null);
        if (channel == null) {
            throw new IllegalStateException("未找到对应的 OpenClaw channel");
        }
        if (Boolean.FALSE.equals(channel.getEnabled())) {
            throw new IllegalStateException("当前 OpenClaw channel 已禁用");
        }

        String sourceUrl = buildChannelApiUrl(channel, "/clear-session");
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("account", StringUtils.trimToEmpty(request.getAccount()));
        requestBody.put("bridgeId", StringUtils.trimToEmpty(request.getBridgeId()));
        requestBody.put("sessionId", StringUtils.trimToEmpty(request.getSessionId()));
        requestBody.put("deviceId", StringUtils.trimToEmpty(request.getDeviceId()));
        requestBody.put("peerId", StringUtils.trimToEmpty(request.getPeerId()));
        requestBody.put("allowLatest", Boolean.TRUE.equals(request.getAllowLatest()));

        Map<String, Object> payload = requestChannelApi(channel, "/clear-session", HttpMethod.POST, requestBody);
        Map<String, Object> root = unwrapData(payload);
        Object rawResult = root.get("result");
        Map<String, Object> result = rawResult instanceof Map<?, ?> map ? castMap(map) : new LinkedHashMap<>();

        OpenClawClearSessionResponseDTO response = new OpenClawClearSessionResponseDTO();
        response.setChannelId(channelId);
        response.setSourceUrl(sourceUrl);
        response.setAccount(StringUtils.defaultIfBlank(firstString(result, new String[]{"account"}), request.getAccount()));
        response.setBridgeId(StringUtils.defaultIfBlank(firstString(root, new String[]{"bridgeId"}), request.getBridgeId()));
        response.setSessionId(StringUtils.defaultIfBlank(firstString(result, new String[]{"sessionId"}), request.getSessionId()));
        response.setDeviceId(StringUtils.defaultIfBlank(firstString(result, new String[]{"deviceId"}), request.getDeviceId()));
        response.setPeerId(StringUtils.defaultIfBlank(firstString(result, new String[]{"peerId"}), request.getPeerId()));
        response.setRawResult(result);
        return response;
    }

    @Override
    public OpenClawAgentBindingDTO getAgentBinding(String agentId) {
        OpenClawAgentBindingDTO binding = loadBindings().get(agentId);
        if (binding == null) {
            binding = new OpenClawAgentBindingDTO();
            binding.setAgentType("native");
        }
        return normalizeBinding(binding);
    }

    @Override
    public OpenClawAgentBindingDTO saveAgentBinding(String agentId, OpenClawAgentBindingDTO binding) {
        Map<String, OpenClawAgentBindingDTO> bindings = loadBindings();
        OpenClawAgentBindingDTO normalized = normalizeBinding(binding);
        bindings.put(agentId, normalized);
        persistParam(
                Constant.SERVER_OPENCLAW_AGENT_BINDINGS,
                JsonUtils.toJsonString(bindings),
                "json",
                "OpenClaw agent bindings"
        );
        return normalized;
    }

    @Override
    public void ensureAgentType(String agentId, String agentType) {
        Map<String, OpenClawAgentBindingDTO> bindings = loadBindings();
        OpenClawAgentBindingDTO current = bindings.getOrDefault(agentId, new OpenClawAgentBindingDTO());
        current.setAgentType(StringUtils.defaultIfBlank(agentType, "native"));
        bindings.put(agentId, normalizeBinding(current));
        persistParam(
                Constant.SERVER_OPENCLAW_AGENT_BINDINGS,
                JsonUtils.toJsonString(bindings),
                "json",
                "OpenClaw agent bindings"
        );
    }

    private List<OpenClawChannelDTO> loadChannels() {
        String raw = sysParamsService.getValue(Constant.SERVER_OPENCLAW_CHANNELS, true);
        List<OpenClawChannelDTO> channels = JsonUtils.parseArray(raw, OpenClawChannelDTO.class);
        List<OpenClawChannelDTO> normalized = new ArrayList<>();
        for (OpenClawChannelDTO channel : channels) {
            if (channel != null) {
                normalized.add(normalizeChannel(channel, ""));
            }
        }
        return normalized;
    }

    private Map<String, OpenClawAgentBindingDTO> loadBindings() {
        String raw = sysParamsService.getValue(Constant.SERVER_OPENCLAW_AGENT_BINDINGS, true);
        if (StringUtils.isBlank(raw)) {
            return new LinkedHashMap<>();
        }
        Map<String, OpenClawAgentBindingDTO> bindings = JsonUtils.parseObject(
                raw,
                new TypeReference<Map<String, OpenClawAgentBindingDTO>>() {
                }
        );
        if (bindings == null) {
            return new LinkedHashMap<>();
        }
        Map<String, OpenClawAgentBindingDTO> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, OpenClawAgentBindingDTO> entry : bindings.entrySet()) {
            normalized.put(entry.getKey(), normalizeBinding(entry.getValue()));
        }
        return normalized;
    }

    private OpenClawChannelDTO normalizeChannel(OpenClawChannelDTO channel, String serverOrigin) {
        String normalizedOrigin = trimTrailingSlash(serverOrigin);
        String serverSecret = StringUtils.trimToEmpty(sysParamsService.getValue(Constant.SERVER_SECRET, true));
        OpenClawChannelDTO normalized = new OpenClawChannelDTO();
        normalized.setId(StringUtils.defaultIfBlank(channel.getId(), UUID.randomUUID().toString()));
        normalized.setName(StringUtils.trimToEmpty(channel.getName()));
        normalized.setBaseUrl(StringUtils.defaultIfBlank(
                trimTrailingSlash(StringUtils.trimToEmpty(channel.getBaseUrl())),
                buildDefaultBaseUrl(normalizedOrigin)
        ));
        normalized.setInventoryPath(normalizeInventoryPath(channel.getInventoryPath()));
        normalized.setAccessToken(StringUtils.defaultIfBlank(StringUtils.trimToEmpty(channel.getAccessToken()), serverSecret));
        normalized.setEnabled(channel.getEnabled() == null ? Boolean.TRUE : channel.getEnabled());
        normalized.setRemark(StringUtils.trimToEmpty(channel.getRemark()));
        return normalized;
    }

    private OpenClawChannelDTO createTransientChannel(String channelId, String channelName) {
        OpenClawChannelDTO channel = new OpenClawChannelDTO();
        channel.setId(StringUtils.defaultIfBlank(StringUtils.trimToEmpty(channelId), UUID.randomUUID().toString()));
        channel.setName(StringUtils.defaultIfBlank(StringUtils.trimToEmpty(channelName), "OpenClaw Runtime"));
        channel.setEnabled(Boolean.TRUE);
        return channel;
    }

    private OpenClawAgentBindingDTO normalizeBinding(OpenClawAgentBindingDTO binding) {
        OpenClawAgentBindingDTO normalized = binding == null ? new OpenClawAgentBindingDTO() : binding;
        normalized.setAgentType(StringUtils.defaultIfBlank(StringUtils.trimToEmpty(normalized.getAgentType()), "native"));
        if (!StringUtils.equals(normalized.getAgentType(), "openclaw")) {
            normalized.setAgentType("native");
            normalized.setChannelId("");
            normalized.setRuntimeAccount("");
            normalized.setRuntimeAccountLabel("");
            normalized.setOpenclawAgentId("");
            normalized.setOpenclawAgentName("");
            normalized.setSyncStatus("native");
            normalized.setErrorMessage("");
            return normalized;
        }
        normalized.setChannelId(StringUtils.trimToEmpty(normalized.getChannelId()));
        normalized.setRuntimeAccount(StringUtils.trimToEmpty(normalized.getRuntimeAccount()));
        normalized.setRuntimeAccountLabel(StringUtils.trimToEmpty(normalized.getRuntimeAccountLabel()));
        normalized.setOpenclawAgentId(StringUtils.trimToEmpty(normalized.getOpenclawAgentId()));
        normalized.setOpenclawAgentName(StringUtils.trimToEmpty(normalized.getOpenclawAgentName()));
        normalized.setSyncStatus(StringUtils.defaultIfBlank(StringUtils.trimToEmpty(normalized.getSyncStatus()), "configured"));
        normalized.setErrorMessage(StringUtils.trimToEmpty(normalized.getErrorMessage()));
        return normalized;
    }

    private void persistParam(String paramCode, String paramValue, String valueType, String remark) {
        SysParamsEntity entity = sysParamsDao.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SysParamsEntity>()
                        .eq("param_code", paramCode)
                        .last("limit 1")
        );
        if (entity == null) {
            entity = new SysParamsEntity();
            entity.setParamCode(paramCode);
            entity.setParamType(1);
        }
        entity.setParamValue(paramValue);
        entity.setValueType(valueType);
        entity.setRemark(remark);
        if (entity.getId() == null) {
            sysParamsService.insert(entity);
        } else {
            sysParamsService.updateById(entity);
        }
    }

    private String buildInventoryUrl(OpenClawChannelDTO channel) {
        return trimTrailingSlash(channel.getBaseUrl()) + normalizeInventoryPath(channel.getInventoryPath());
    }

    private String buildChannelApiUrl(OpenClawChannelDTO channel, String path) {
        return trimTrailingSlash(channel.getBaseUrl()) + normalizeInventoryPath(path);
    }

    private String buildDefaultBaseUrl(String serverOrigin) {
        if (StringUtils.isBlank(serverOrigin)) {
            return "";
        }
        return trimTrailingSlash(serverOrigin) + "/admin/openclaw";
    }

    private String buildInstallCommand(String serverUrl, String adminKey, String accountId, String channelName,
                                       String defaultAgentId) {
        if (StringUtils.isBlank(serverUrl) || StringUtils.isBlank(adminKey) || StringUtils.isBlank(accountId)) {
            return "";
        }
        String encodedName = shellQuote(channelName);
        return "npx -y --registry=https://registry.npmjs.org @galaxyxieyu/openclaw-xiaozhi-cli@latest install"
                + " --server-url " + shellQuote(serverUrl)
                + " --admin-key " + shellQuote(adminKey)
                + " --account " + shellQuote(accountId)
                + " --name " + encodedName
                + " --default-agent-id " + shellQuote(defaultAgentId);
    }

    private String shellQuote(String value) {
        String text = StringUtils.defaultString(value);
        return "'" + text.replace("'", "'\"'\"'") + "'";
    }

    private String normalizeInventoryPath(String path) {
        if (StringUtils.isBlank(path)) {
            return "/inventory";
        }
        String trimmed = StringUtils.trim(path);
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }

    private HttpHeaders buildChannelHeaders(OpenClawChannelDTO channel) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.isNotBlank(channel.getAccessToken())) {
            headers.setBearerAuth(channel.getAccessToken());
            headers.add("X-OpenClaw-Token", channel.getAccessToken());
        }
        return headers;
    }

    private Map<String, Object> requestChannelApi(OpenClawChannelDTO channel, String path, HttpMethod method, Object body) {
        String requestUrl = buildChannelApiUrl(channel, path);
        HttpEntity<?> requestEntity = body == null
                ? new HttpEntity<>(buildChannelHeaders(channel))
                : new HttpEntity<>(body, buildChannelHeaders(channel));
        ResponseEntity<String> response = restTemplate.exchange(requestUrl, method, requestEntity, String.class);
        if (!response.getStatusCode().is2xxSuccessful() || StringUtils.isBlank(response.getBody())) {
            throw new IllegalStateException("OpenClaw 接口未返回有效内容");
        }

        Map<String, Object> payload = JsonUtils.parseObject(response.getBody(), new TypeReference<Map<String, Object>>() {
        });
        if (payload == null) {
            throw new IllegalStateException("OpenClaw 接口返回 JSON 为空");
        }
        Boolean payloadOk = firstBoolean(payload, new String[]{"ok"});
        if (Boolean.FALSE.equals(payloadOk)) {
            throw new IllegalStateException(StringUtils.defaultIfBlank(
                    firstString(payload, new String[]{"message", "errorMessage", "msg"}),
                    "OpenClaw 接口返回失败"
            ));
        }
        return payload;
    }

    private String trimTrailingSlash(String value) {
        if (StringUtils.isBlank(value)) {
            return "";
        }
        String normalized = StringUtils.trim(value);
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private Map<String, Object> unwrapData(Map<String, Object> payload) {
        if (payload == null) {
            return Collections.emptyMap();
        }
        Object data = payload.get("data");
        if (data instanceof Map<?, ?> map) {
            return castMap(map);
        }
        return payload;
    }

    private List<OptionItem> extractOptions(Map<String, Object> root, String[] candidateKeys, String[] valueKeys,
                                            String[] labelKeys) {
        Object raw = findFirst(root, candidateKeys);
        if (raw == null) {
            return new ArrayList<>();
        }
        List<?> list;
        if (raw instanceof List<?> rawList) {
            list = rawList;
        } else if (raw instanceof Map<?, ?> rawMap) {
            Object items = findFirst(castMap(rawMap), new String[]{"items", "list", "data"});
            if (items instanceof List<?> itemsList) {
                list = itemsList;
            } else {
                return new ArrayList<>();
            }
        } else {
            return new ArrayList<>();
        }

        List<OptionItem> options = new ArrayList<>();
        for (Object item : list) {
            OptionItem option = toOption(item, valueKeys, labelKeys);
            if (option != null) {
                options.add(option);
            }
        }
        return options;
    }

    private List<BridgeItem> extractBridges(Map<String, Object> root) {
        Object raw = findFirst(root, new String[]{"bridges", "bridgeList"});
        if (!(raw instanceof List<?> list)) {
            return new ArrayList<>();
        }

        List<BridgeItem> bridges = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> bridge = castMap(map);
            BridgeItem bridgeItem = new BridgeItem();
            bridgeItem.setBridgeId(firstString(bridge, new String[]{"bridgeId", "id"}));
            bridgeItem.setName(firstString(bridge, new String[]{"name", "label"}));
            bridgeItem.setAccount(firstString(bridge, new String[]{"account", "accountId"}));
            bridgeItem.setConnected(Boolean.TRUE.equals(firstBoolean(bridge, new String[]{"connected", "online"})));
            bridgeItem.setIsDefault(Boolean.TRUE.equals(firstBoolean(bridge, new String[]{"isDefault", "default"})));
            bridgeItem.setLastConnectedAt(firstString(bridge, new String[]{"lastConnectedAt"}));
            bridgeItem.setLastDisconnectedAt(firstString(bridge, new String[]{"lastDisconnectedAt"}));
            bridges.add(bridgeItem);
        }
        return bridges;
    }

    private Map<String, List<OptionItem>> extractAccountAgents(Map<String, Object> root) {
        Object raw = findFirst(root, new String[]{"accountAgents", "agentsByAccount"});
        if (!(raw instanceof Map<?, ?> map)) {
            return new LinkedHashMap<>();
        }

        Map<String, List<OptionItem>> accountAgents = new LinkedHashMap<>();
        Map<String, Object> data = castMap(map);
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (StringUtils.isBlank(entry.getKey())) {
                continue;
            }
            Map<String, Object> nested = new LinkedHashMap<>();
            nested.put("items", entry.getValue());
            accountAgents.put(entry.getKey(), extractOptions(
                    nested,
                    new String[]{"items"},
                    new String[]{"id", "agentId", "peerAgentId", "value", "key"},
                    new String[]{"label", "name", "agentName", "displayName", "title", "id"}
            ));
        }
        return accountAgents;
    }

    private Object findFirst(Map<String, Object> root, String[] keys) {
        for (String key : keys) {
            if (root.containsKey(key)) {
                return root.get(key);
            }
        }
        return null;
    }

    private OptionItem toOption(Object item, String[] valueKeys, String[] labelKeys) {
        if (item instanceof String text) {
            if (StringUtils.isBlank(text)) {
                return null;
            }
            OptionItem option = new OptionItem();
            option.setValue(text);
            option.setLabel(text);
            return option;
        }
        if (!(item instanceof Map<?, ?> map)) {
            return null;
        }
        Map<String, Object> raw = castMap(map);
        String value = firstString(raw, valueKeys);
        if (StringUtils.isBlank(value)) {
            return null;
        }
        OptionItem option = new OptionItem();
        option.setValue(value);
        option.setLabel(StringUtils.defaultIfBlank(firstString(raw, labelKeys), value));
        return option;
    }

    private String firstString(Map<String, Object> raw, String[] keys) {
        if (raw == null) {
            return "";
        }
        for (String key : keys) {
            Object value = raw.get(key);
            if (value != null && StringUtils.isNotBlank(String.valueOf(value))) {
                return String.valueOf(value);
            }
        }
        return "";
    }

    private Boolean firstBoolean(Map<String, Object> raw, String[] keys) {
        if (raw == null) {
            return null;
        }
        for (String key : keys) {
            Object value = raw.get(key);
            if (value instanceof Boolean boolValue) {
                return boolValue;
            }
            if (value instanceof String text && StringUtils.isNotBlank(text)) {
                if ("true".equalsIgnoreCase(text) || "1".equals(text)) {
                    return Boolean.TRUE;
                }
                if ("false".equalsIgnoreCase(text) || "0".equals(text)) {
                    return Boolean.FALSE;
                }
            }
        }
        return null;
    }

    private String extractReplyText(Map<String, Object> result, Object rawResult) {
        String replyText = firstString(result, new String[]{"text", "replyText", "reply", "message", "output"});
        if (StringUtils.isNotBlank(replyText)) {
            return replyText;
        }
        if (rawResult == null) {
            return "";
        }
        if (rawResult instanceof Map<?, ?>) {
            return JsonUtils.toJsonString(rawResult);
        }
        return String.valueOf(rawResult);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Map<?, ?> map) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return result;
    }
}

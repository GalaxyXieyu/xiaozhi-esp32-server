package xiaozhi.modules.openclaw.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.core.type.TypeReference;

import lombok.AllArgsConstructor;
import xiaozhi.common.constant.Constant;
import xiaozhi.common.utils.JsonUtils;
import xiaozhi.modules.agent.dto.AgentDTO;
import xiaozhi.modules.agent.service.AgentService;
import xiaozhi.modules.openclaw.dto.OpenClawAgentBindingDTO;
import xiaozhi.modules.openclaw.dto.OpenClawChannelBindingDTO;
import xiaozhi.modules.openclaw.dto.OpenClawChannelDTO;
import xiaozhi.modules.openclaw.dto.OpenClawChannelInventoryDTO;
import xiaozhi.modules.openclaw.dto.OpenClawChannelInventoryDTO.BridgeItem;
import xiaozhi.modules.openclaw.dto.OpenClawChannelInventoryDTO.OptionItem;
import xiaozhi.modules.openclaw.dto.OpenClawChannelSetupGuideDTO;
import xiaozhi.modules.openclaw.dto.OpenClawClearSessionRequestDTO;
import xiaozhi.modules.openclaw.dto.OpenClawClearSessionResponseDTO;
import xiaozhi.modules.openclaw.dto.OpenClawConnectionDTO;
import xiaozhi.modules.openclaw.dto.OpenClawDebugChatRequestDTO;
import xiaozhi.modules.openclaw.dto.OpenClawDebugChatResponseDTO;
import xiaozhi.modules.openclaw.dto.OpenClawDebugSessionTraceResponseDTO;
import xiaozhi.modules.openclaw.dto.OpenClawVoiceInterruptRequestDTO;
import xiaozhi.modules.openclaw.dto.OpenClawVoiceInterruptResponseDTO;
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
    private final AgentService agentService;

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
                if (StringUtils.isBlank(normalizedChannel.getName())) {
                    continue;
                }
                normalized.add(normalizedChannel);
            }
        }
        persistChannels(normalized);
        return normalized;
    }

    @Override
    public OpenClawChannelDTO createChannel(OpenClawChannelDTO channel, String serverOrigin) {
        OpenClawChannelDTO payload = channel == null ? new OpenClawChannelDTO() : channel;
        payload.setId(StringUtils.trimToEmpty(payload.getId()));
        if (StringUtils.isBlank(payload.getId())) {
            payload.setId(generateChannelId(payload.getName(), loadChannels()));
        }
        return upsertChannel(payload.getId(), payload, serverOrigin, true);
    }

    @Override
    public OpenClawChannelDTO updateChannel(String channelId, OpenClawChannelDTO channel, String serverOrigin) {
        if (StringUtils.isBlank(channelId)) {
            throw new IllegalArgumentException("channelId 不能为空");
        }
        return upsertChannel(channelId, channel, serverOrigin, false);
    }

    @Override
    public void deleteChannel(String channelId) {
        if (StringUtils.isBlank(channelId)) {
            throw new IllegalArgumentException("channelId 不能为空");
        }
        List<OpenClawChannelDTO> channels = loadChannels();
        boolean removed = channels.removeIf(item -> StringUtils.equals(item.getId(), channelId));
        if (!removed) {
            throw new IllegalStateException("未找到对应的 OpenClaw channel");
        }
        persistChannels(channels);
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
        guide.setBaseUrl(StringUtils.defaultIfBlank(channel.getBaseUrl(), buildDefaultBaseUrl(normalizedOrigin)));
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
        if (StringUtils.isBlank(channel.getBaseUrl())) {
            inventory.setErrorMessage("当前 OpenClaw channel 尚未配置管理接口地址");
            return inventory;
        }

        String sourceUrl = buildInventoryUrl(channel, channelId);
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
            inventory.setBridgeAgents(extractBridgeAgents(root));
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
        requestBody.put("sessionId", StringUtils.trimToEmpty(request.getSessionId()));
        requestBody.put("deviceId", StringUtils.trimToEmpty(request.getDeviceId()));
        requestBody.put("peerId", StringUtils.trimToEmpty(request.getPeerId()));
        requestBody.put("speaker", StringUtils.trimToEmpty(request.getSpeaker()));
        requestBody.put("text", StringUtils.trimToEmpty(request.getText()));
        requestBody.put("pushToDevice", Boolean.TRUE.equals(request.getPushToDevice()));
        requestBody.put("browserAudio", Boolean.TRUE.equals(request.getBrowserAudio()));

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
        response.setAccepted(firstBoolean(result, new String[]{"accepted"}));
        response.setStatus(StringUtils.defaultIfBlank(
                firstString(result, new String[]{"status"}),
                firstString(root, new String[]{"status"})
        ));
        response.setPushToDevice(Boolean.TRUE.equals(firstBoolean(root, new String[]{"pushToDevice"}))
                || Boolean.TRUE.equals(firstBoolean(result, new String[]{"pushToDevice"})));
        response.setBrowserAudio(Boolean.TRUE.equals(firstBoolean(root, new String[]{"browserAudio"}))
                || Boolean.TRUE.equals(firstBoolean(result, new String[]{"browserAudio"})));
        response.setReplyText(extractReplyText(result, rawResult));
        response.setRawResult(result);
        return response;
    }

    @Override
    public OpenClawDebugSessionTraceResponseDTO getDebugSessionTrace(String channelId, String debugSessionId,
                                                                    String account, String bridgeId, Integer sinceSeq) {
        OpenClawChannelDTO channel = resolveEnabledChannel(channelId);
        Map<String, String> query = buildDebugSessionQuery(debugSessionId, account, bridgeId, sinceSeq);
        String sourceUrl = buildChannelApiUrl(channel, "/debug-session", query);
        Map<String, Object> payload = requestChannelApiByUrl(channel, sourceUrl, HttpMethod.GET, null);
        Map<String, Object> root = unwrapData(payload);
        Object rawResult = root.get("result");
        Map<String, Object> result = rawResult instanceof Map<?, ?> map ? castMap(map) : castMap(root);
        return toDebugSessionTraceResponse(channelId, sourceUrl, result);
    }

    @Override
    public OpenClawClearSessionResponseDTO clearSession(String channelId, OpenClawClearSessionRequestDTO request) {
        OpenClawChannelDTO channel = resolveEnabledChannel(channelId);

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
    public List<OpenClawConnectionDTO> listConnections(String channelId) {
        OpenClawChannelDTO channel = resolveEnabledChannel(channelId);
        Map<String, Object> payload = requestChannelApi(channel, "/connections", HttpMethod.GET, null);
        Map<String, Object> root = unwrapData(payload);
        Object rawConnections = root.get("connections");
        if (!(rawConnections instanceof List<?> list)) {
            return new ArrayList<>();
        }

        List<OpenClawConnectionDTO> connections = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> raw = castMap(map);
            OpenClawConnectionDTO connection = new OpenClawConnectionDTO();
            connection.setSessionId(firstString(raw, new String[]{"sessionId"}));
            connection.setDeviceId(firstString(raw, new String[]{"deviceId"}));
            connection.setClientIp(firstString(raw, new String[]{"clientIp"}));
            connection.setRegisteredAt(firstDouble(raw, new String[]{"registeredAt"}));
            connection.setIsLatest(Boolean.TRUE.equals(firstBoolean(raw, new String[]{"isLatest"})));
            Boolean voiceInterruptEnabled = firstBoolean(raw, new String[]{"voiceInterruptEnabled"});
            connection.setVoiceInterruptEnabled(voiceInterruptEnabled == null ? Boolean.TRUE : voiceInterruptEnabled);
            connections.add(connection);
        }
        return connections;
    }

    @Override
    public List<OpenClawChannelBindingDTO> listChannelBindings(String channelId, Long userId) {
        if (StringUtils.isBlank(channelId)) {
            return new ArrayList<>();
        }
        Map<String, OpenClawAgentBindingDTO> bindings = loadBindings();
        if (bindings.isEmpty() || userId == null) {
            return new ArrayList<>();
        }

        List<AgentDTO> agents = agentService.getUserAgents(userId, null, "name");
        List<OpenClawChannelBindingDTO> results = new ArrayList<>();
        for (AgentDTO agent : agents) {
            if (agent == null || StringUtils.isBlank(agent.getId())) {
                continue;
            }
            OpenClawAgentBindingDTO binding = normalizeBinding(bindings.get(agent.getId()));
            if (!StringUtils.equals(binding.getAgentType(), "openclaw")) {
                continue;
            }
            if (!StringUtils.equals(binding.getChannelId(), channelId)) {
                continue;
            }
            OpenClawChannelBindingDTO item = new OpenClawChannelBindingDTO();
            item.setAgentId(agent.getId());
            item.setAgentName(StringUtils.defaultIfBlank(agent.getAgentName(), agent.getId()));
            item.setAgentType(binding.getAgentType());
            item.setChannelId(binding.getChannelId());
            item.setRuntimeAccount(binding.getRuntimeAccount());
            item.setRuntimeAccountLabel(binding.getRuntimeAccountLabel());
            item.setOpenclawAgentId(binding.getOpenclawAgentId());
            item.setOpenclawAgentName(binding.getOpenclawAgentName());
            item.setSyncStatus(binding.getSyncStatus());
            item.setErrorMessage(binding.getErrorMessage());
            results.add(item);
        }
        return results;
    }

    @Override
    public OpenClawVoiceInterruptResponseDTO getVoiceInterrupt(String channelId, OpenClawVoiceInterruptRequestDTO request) {
        OpenClawChannelDTO channel = resolveEnabledChannel(channelId);
        String sourceUrl = buildChannelApiUrl(channel, "/voice-interrupt", buildVoiceInterruptQuery(request));
        Map<String, Object> payload = requestChannelApiByUrl(channel, sourceUrl, HttpMethod.GET, null);
        return toVoiceInterruptResponse(channelId, sourceUrl, unwrapData(payload));
    }

    @Override
    public OpenClawVoiceInterruptResponseDTO setVoiceInterrupt(String channelId, OpenClawVoiceInterruptRequestDTO request) {
        OpenClawChannelDTO channel = resolveEnabledChannel(channelId);
        String sourceUrl = buildChannelApiUrl(channel, "/voice-interrupt");
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("enabled", request.getEnabled());
        requestBody.put("sessionId", StringUtils.trimToNull(request.getSessionId()));
        requestBody.put("deviceId", StringUtils.trimToNull(request.getDeviceId()));
        requestBody.put("peerId", StringUtils.trimToNull(request.getPeerId()));
        requestBody.put("allowLatest", Boolean.TRUE.equals(request.getAllowLatest()));
        requestBody.put("persist", Boolean.TRUE.equals(request.getPersist()));

        Map<String, Object> payload = requestChannelApi(channel, "/voice-interrupt", HttpMethod.POST, requestBody);
        return toVoiceInterruptResponse(channelId, sourceUrl, unwrapData(payload));
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
        String raw = sysParamsService.getValue(Constant.SERVER_OPENCLAW_CHANNELS, false);
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
        String raw = sysParamsService.getValue(Constant.SERVER_OPENCLAW_AGENT_BINDINGS, false);
        if (StringUtils.isBlank(raw)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> bindings = JsonUtils.parseObject(
                raw,
                new TypeReference<Map<String, Object>>() {
                }
        );
        if (bindings == null) {
            return new LinkedHashMap<>();
        }
        Map<String, OpenClawAgentBindingDTO> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : bindings.entrySet()) {
            OpenClawAgentBindingDTO binding = convertBinding(entry.getValue());
            normalized.put(entry.getKey(), normalizeBinding(binding));
        }
        return normalized;
    }

    private OpenClawAgentBindingDTO convertBinding(Object rawBinding) {
        if (rawBinding == null) {
            return new OpenClawAgentBindingDTO();
        }
        if (rawBinding instanceof OpenClawAgentBindingDTO binding) {
            return binding;
        }
        if (rawBinding instanceof String text && StringUtils.isNotBlank(text)) {
            return JsonUtils.parseObject(text, OpenClawAgentBindingDTO.class);
        }
        return JsonUtils.parseObject(JsonUtils.toJsonString(rawBinding), OpenClawAgentBindingDTO.class);
    }

    private OpenClawChannelDTO normalizeChannel(OpenClawChannelDTO channel, String serverOrigin) {
        OpenClawChannelDTO normalized = new OpenClawChannelDTO();
        String normalizedOrigin = trimTrailingSlash(serverOrigin);
        boolean useLocalDefaultBaseUrl = StringUtils.isBlank(channel.getBaseUrl()) && StringUtils.isNotBlank(normalizedOrigin);
        String accessToken = StringUtils.trimToEmpty(channel.getAccessToken());
        if (useLocalDefaultBaseUrl && StringUtils.isBlank(accessToken)) {
            accessToken = resolveLocalAdminToken();
        }
        normalized.setId(StringUtils.defaultIfBlank(channel.getId(), UUID.randomUUID().toString()));
        normalized.setName(StringUtils.trimToEmpty(channel.getName()));
        normalized.setBaseUrl(useLocalDefaultBaseUrl
                ? buildDefaultBaseUrl(normalizedOrigin)
                : trimTrailingSlash(StringUtils.trimToEmpty(channel.getBaseUrl())));
        normalized.setInventoryPath(normalizeInventoryPath(channel.getInventoryPath()));
        normalized.setAccessToken(accessToken);
        normalized.setEnabled(channel.getEnabled() == null ? Boolean.TRUE : channel.getEnabled());
        normalized.setRemark(StringUtils.trimToEmpty(channel.getRemark()));
        return normalized;
    }

    private OpenClawChannelDTO upsertChannel(String channelId,
                                             OpenClawChannelDTO channel,
                                             String serverOrigin,
                                             boolean prependWhenNew) {
        List<OpenClawChannelDTO> existingChannels = loadChannels();
        OpenClawChannelDTO payload = channel == null ? new OpenClawChannelDTO() : channel;
        OpenClawChannelDTO workingCopy = new OpenClawChannelDTO();
        workingCopy.setId(StringUtils.trimToEmpty(channelId));
        workingCopy.setName(payload.getName());
        workingCopy.setBaseUrl(payload.getBaseUrl());
        workingCopy.setInventoryPath(payload.getInventoryPath());
        workingCopy.setAccessToken(payload.getAccessToken());
        workingCopy.setEnabled(payload.getEnabled());
        workingCopy.setRemark(payload.getRemark());

        OpenClawChannelDTO normalized = normalizeChannel(workingCopy, serverOrigin);
        if (StringUtils.isBlank(normalized.getName())) {
            throw new IllegalArgumentException("channel 名称不能为空");
        }

        boolean matched = false;
        List<OpenClawChannelDTO> nextChannels = new ArrayList<>();
        for (OpenClawChannelDTO existing : existingChannels) {
            if (StringUtils.equals(existing.getId(), normalized.getId())) {
                nextChannels.add(normalized);
                matched = true;
                continue;
            }
            nextChannels.add(existing);
        }
        if (!matched) {
            if (prependWhenNew) {
                nextChannels.add(0, normalized);
            } else {
                throw new IllegalStateException("未找到对应的 OpenClaw channel");
            }
        }
        persistChannels(nextChannels);
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

    private OpenClawChannelDTO resolveEnabledChannel(String channelId) {
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
        return channel;
    }

    private void persistParam(String paramCode, String paramValue, String valueType, String remark) {
        int updated = sysParamsService.updateValueByCode(paramCode, paramValue);
        if (updated > 0) {
            return;
        }

        SysParamsEntity entity = sysParamsDao.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SysParamsEntity>()
                        .eq("param_code", paramCode)
                        .orderByDesc("update_date", "id")
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

    private void persistChannels(List<OpenClawChannelDTO> channels) {
        List<OpenClawChannelDTO> safeChannels = channels == null ? new ArrayList<>() : channels;
        persistParam(
                Constant.SERVER_OPENCLAW_CHANNELS,
                JsonUtils.toJsonString(safeChannels),
                "json",
                "OpenClaw channels"
        );
    }

    private String buildInventoryUrl(OpenClawChannelDTO channel, String channelId) {
        return buildChannelApiUrl(channel, channel.getInventoryPath(), buildInventoryQuery(channelId));
    }

    private String buildChannelApiUrl(OpenClawChannelDTO channel, String path) {
        return trimTrailingSlash(channel.getBaseUrl()) + normalizeInventoryPath(path);
    }

    private String buildChannelApiUrl(OpenClawChannelDTO channel, String path, Map<String, String> queryParams) {
        String baseUrl = buildChannelApiUrl(channel, path);
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl);
        if (queryParams != null) {
            for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                if (StringUtils.isNotBlank(entry.getValue())) {
                    builder.queryParam(entry.getKey(), entry.getValue());
                }
            }
        }
        return builder.build(true).toUriString();
    }

    private String buildDefaultBaseUrl(String serverOrigin) {
        if (StringUtils.isBlank(serverOrigin)) {
            return "";
        }
        return trimTrailingSlash(serverOrigin) + "/admin/openclaw";
    }

    private String resolveLocalAdminToken() {
        return StringUtils.trimToEmpty(sysParamsService.getValue(Constant.SERVER_SECRET, true));
    }

    private String generateChannelId(String channelName, List<OpenClawChannelDTO> existingChannels) {
        String base = StringUtils.defaultIfBlank(buildChannelIdSeed(channelName), "channel");
        String candidate = base;
        int suffix = 2;
        while (containsChannelId(existingChannels, candidate)) {
            candidate = base + "-" + suffix;
            suffix += 1;
        }
        return candidate;
    }

    private String buildChannelIdSeed(String channelName) {
        String normalized = StringUtils.trimToEmpty(channelName).toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("[^a-z0-9]+", "-");
        normalized = normalized.replaceAll("^-+", "");
        normalized = normalized.replaceAll("-+$", "");
        normalized = normalized.replaceAll("-{2,}", "-");
        return normalized;
    }

    private boolean containsChannelId(List<OpenClawChannelDTO> channels, String channelId) {
        if (channels == null || StringUtils.isBlank(channelId)) {
            return false;
        }
        return channels.stream().anyMatch(item -> item != null && StringUtils.equals(item.getId(), channelId));
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
        ensureChannelApiConfigured(channel);
        String requestUrl = buildChannelApiUrl(channel, path);
        return requestChannelApiByUrl(channel, requestUrl, method, body);
    }

    private Map<String, Object> requestChannelApiByUrl(OpenClawChannelDTO channel, String requestUrl, HttpMethod method, Object body) {
        ensureChannelApiConfigured(channel);
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

    private void ensureChannelApiConfigured(OpenClawChannelDTO channel) {
        if (channel == null || StringUtils.isBlank(channel.getBaseUrl())) {
            throw new IllegalStateException("当前 OpenClaw channel 尚未配置管理接口地址");
        }
    }

    private Map<String, String> buildVoiceInterruptQuery(OpenClawVoiceInterruptRequestDTO request) {
        Map<String, String> query = new LinkedHashMap<>();
        if (request == null) {
            return query;
        }
        query.put("sessionId", StringUtils.trimToNull(request.getSessionId()));
        query.put("deviceId", StringUtils.trimToNull(request.getDeviceId()));
        query.put("peerId", StringUtils.trimToNull(request.getPeerId()));
        if (Boolean.TRUE.equals(request.getAllowLatest())) {
            query.put("allowLatest", "true");
        }
        return query;
    }

    private Map<String, String> buildDebugSessionQuery(String debugSessionId, String account, String bridgeId, Integer sinceSeq) {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("debugSessionId", StringUtils.trimToNull(debugSessionId));
        query.put("account", StringUtils.trimToNull(account));
        query.put("bridgeId", StringUtils.trimToNull(bridgeId));
        if (sinceSeq != null && sinceSeq >= 0) {
            query.put("sinceSeq", String.valueOf(sinceSeq));
        }
        return query;
    }

    private Map<String, String> buildInventoryQuery(String channelId) {
        Map<String, String> query = new LinkedHashMap<>();
        if (StringUtils.isNotBlank(channelId)) {
            query.put("account", channelId);
        }
        return query;
    }

    private OpenClawVoiceInterruptResponseDTO toVoiceInterruptResponse(String channelId, String sourceUrl, Map<String, Object> raw) {
        OpenClawVoiceInterruptResponseDTO response = new OpenClawVoiceInterruptResponseDTO();
        response.setChannelId(channelId);
        response.setSourceUrl(sourceUrl);
        response.setEnabled(firstBoolean(raw, new String[]{"enabled"}));
        response.setScope(firstString(raw, new String[]{"scope"}));
        response.setSource(firstString(raw, new String[]{"source"}));
        response.setSessionId(firstString(raw, new String[]{"sessionId"}));
        response.setDeviceId(firstString(raw, new String[]{"deviceId"}));
        Integer updatedConnections = firstInteger(raw, new String[]{"updatedConnections"});
        response.setUpdatedConnections(updatedConnections == null ? 0 : updatedConnections);
        Integer skippedConnections = firstInteger(raw, new String[]{"skippedConnections"});
        response.setSkippedConnections(skippedConnections == null ? 0 : skippedConnections);
        response.setPersisted(Boolean.TRUE.equals(firstBoolean(raw, new String[]{"persisted"})));
        response.setOnline(firstBoolean(raw, new String[]{"online"}));
        response.setRawResult(new LinkedHashMap<>(raw));
        return response;
    }

    private OpenClawDebugSessionTraceResponseDTO toDebugSessionTraceResponse(String channelId, String sourceUrl, Map<String, Object> raw) {
        OpenClawDebugSessionTraceResponseDTO response = new OpenClawDebugSessionTraceResponseDTO();
        response.setChannelId(channelId);
        response.setSourceUrl(sourceUrl);
        response.setDebugSessionId(firstString(raw, new String[]{"debugSessionId"}));
        response.setAccount(firstString(raw, new String[]{"account"}));
        response.setBridgeId(firstString(raw, new String[]{"bridgeId"}));
        response.setPeerId(firstString(raw, new String[]{"peerId"}));
        response.setAgentId(firstString(raw, new String[]{"agentId"}));
        response.setAgentName(firstString(raw, new String[]{"agentName"}));
        response.setStatus(firstString(raw, new String[]{"status"}));
        response.setPending(firstBoolean(raw, new String[]{"pending"}));
        response.setNextSeq(firstInteger(raw, new String[]{"nextSeq"}));
        response.setLatestReplyText(firstString(raw, new String[]{"latestReplyText"}));
        response.setLatestError(firstString(raw, new String[]{"latestError"}));
        response.setUpdatedAt(firstLong(raw, new String[]{"updatedAt"}));
        response.setCreatedAt(firstLong(raw, new String[]{"createdAt"}));

        Object rawBrowserAudio = findFirst(raw, new String[]{"browserAudio"});
        Map<String, Object> browserAudio = rawBrowserAudio instanceof Map<?, ?> browserAudioMap
                ? castMap(browserAudioMap)
                : new LinkedHashMap<>();
        response.getBrowserAudio().setEnabled(firstBoolean(browserAudio, new String[]{"enabled"}));
        response.getBrowserAudio().setReady(firstBoolean(browserAudio, new String[]{"ready"}));
        response.getBrowserAudio().setKind(firstString(browserAudio, new String[]{"kind"}));
        response.getBrowserAudio().setText(firstString(browserAudio, new String[]{"text"}));

        Object rawDeviceDelivery = findFirst(raw, new String[]{"deviceDelivery"});
        Map<String, Object> deviceDelivery = rawDeviceDelivery instanceof Map<?, ?> deviceDeliveryMap
                ? castMap(deviceDeliveryMap)
                : new LinkedHashMap<>();
        response.getDeviceDelivery().setEnabled(firstBoolean(deviceDelivery, new String[]{"enabled"}));
        response.getDeviceDelivery().setStatus(firstString(deviceDelivery, new String[]{"status"}));
        response.getDeviceDelivery().setMessage(firstString(deviceDelivery, new String[]{"message"}));

        Object rawEvents = findFirst(raw, new String[]{"events"});
        if (rawEvents instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> map)) {
                    continue;
                }
                Map<String, Object> eventMap = castMap(map);
                OpenClawDebugSessionTraceResponseDTO.TraceEvent event = new OpenClawDebugSessionTraceResponseDTO.TraceEvent();
                event.setSeq(firstInteger(eventMap, new String[]{"seq"}));
                event.setType(firstString(eventMap, new String[]{"type"}));
                event.setTimestamp(firstLong(eventMap, new String[]{"timestamp"}));
                event.setTitle(firstString(eventMap, new String[]{"title"}));
                event.setMessage(firstString(eventMap, new String[]{"message"}));
                event.setStatus(firstString(eventMap, new String[]{"status"}));
                event.setAgentId(firstString(eventMap, new String[]{"agentId"}));
                event.setAgentName(firstString(eventMap, new String[]{"agentName"}));
                event.setSessionKey(firstString(eventMap, new String[]{"sessionKey"}));
                Object rawPayload = findFirst(eventMap, new String[]{"payload"});
                event.setPayload(rawPayload instanceof Map<?, ?> payloadMap ? castMap(payloadMap) : new LinkedHashMap<>());
                response.getEvents().add(event);
            }
        }

        response.setRawResult(new LinkedHashMap<>(raw));
        return response;
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

    private Long firstLong(Map<String, Object> source, String[] candidateKeys) {
        Object raw = findFirst(source, candidateKeys);
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw instanceof String text && StringUtils.isNotBlank(text)) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
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

    private Map<String, List<OptionItem>> extractBridgeAgents(Map<String, Object> root) {
        Object raw = findFirst(root, new String[]{"bridgeAgents", "agentsByBridge"});
        if (!(raw instanceof Map<?, ?> map)) {
            return new LinkedHashMap<>();
        }

        Map<String, List<OptionItem>> bridgeAgents = new LinkedHashMap<>();
        Map<String, Object> data = castMap(map);
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (StringUtils.isBlank(entry.getKey())) {
                continue;
            }
            Map<String, Object> nested = new LinkedHashMap<>();
            nested.put("items", entry.getValue());
            bridgeAgents.put(entry.getKey(), extractOptions(
                    nested,
                    new String[]{"items"},
                    new String[]{"id", "agentId", "peerAgentId", "value", "key"},
                    new String[]{"label", "name", "agentName", "displayName", "title", "id"}
            ));
        }
        return bridgeAgents;
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

    private Integer firstInteger(Map<String, Object> raw, String[] keys) {
        if (raw == null) {
            return null;
        }
        for (String key : keys) {
            Object value = raw.get(key);
            if (value instanceof Number number) {
                return number.intValue();
            }
            if (value instanceof String text && StringUtils.isNotBlank(text)) {
                try {
                    return Integer.parseInt(text);
                } catch (NumberFormatException ignore) {
                    // ignore invalid integer
                }
            }
        }
        return null;
    }

    private Double firstDouble(Map<String, Object> raw, String[] keys) {
        if (raw == null) {
            return null;
        }
        for (String key : keys) {
            Object value = raw.get(key);
            if (value instanceof Number number) {
                return number.doubleValue();
            }
            if (value instanceof String text && StringUtils.isNotBlank(text)) {
                try {
                    return Double.parseDouble(text);
                } catch (NumberFormatException ignore) {
                    // ignore invalid number
                }
            }
        }
        return null;
    }

    private String extractReplyText(Map<String, Object> result, Object rawResult) {
        String replyText = extractReplyTextValue(result);
        if (StringUtils.isNotBlank(replyText)) {
            return replyText;
        }
        return extractReplyTextValue(rawResult);
    }

    private String extractReplyTextValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String text) {
            return StringUtils.trimToEmpty(text);
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof List<?> list) {
            StringBuilder builder = new StringBuilder();
            for (Object item : list) {
                String nested = extractReplyTextValue(item);
                if (StringUtils.isBlank(nested)) {
                    continue;
                }
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append(nested.trim());
            }
            return builder.toString().trim();
        }
        if (!(value instanceof Map<?, ?> map)) {
            return StringUtils.trimToEmpty(String.valueOf(value));
        }

        Map<String, Object> normalized = castMap(map);
        String direct = firstString(normalized, new String[]{"text", "replyText", "reply", "response", "message", "output", "finalText", "outputText"});
        if (StringUtils.isNotBlank(direct)) {
            return direct;
        }

        String nestedPayload = extractReplyTextValue(normalized.get("payload"));
        if (StringUtils.isNotBlank(nestedPayload)) {
            return nestedPayload;
        }

        String nestedPayloads = extractReplyTextValue(normalized.get("payloads"));
        if (StringUtils.isNotBlank(nestedPayloads)) {
            return nestedPayloads;
        }

        String nestedResult = extractReplyTextValue(normalized.get("result"));
        if (StringUtils.isNotBlank(nestedResult)) {
            return nestedResult;
        }

        String nestedMessages = extractReplyTextValue(normalized.get("messages"));
        if (StringUtils.isNotBlank(nestedMessages)) {
            return nestedMessages;
        }

        return "";
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

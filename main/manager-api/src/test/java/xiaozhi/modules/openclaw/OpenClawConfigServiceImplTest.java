package xiaozhi.modules.openclaw;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import xiaozhi.common.constant.Constant;
import xiaozhi.common.utils.JsonUtils;
import xiaozhi.modules.agent.service.AgentService;
import xiaozhi.modules.openclaw.dto.OpenClawChannelDTO;
import xiaozhi.modules.openclaw.dto.OpenClawChannelInventoryDTO;
import xiaozhi.modules.openclaw.service.impl.OpenClawConfigServiceImpl;
import xiaozhi.modules.sys.dao.SysParamsDao;
import xiaozhi.modules.sys.service.SysParamsService;

public class OpenClawConfigServiceImplTest {

    @Test
    @DisplayName("创建 channel 时应自动补齐本地接入默认值")
    void createChannelShouldUseLocalDefaults() {
        SysParamsService sysParamsService = mock(SysParamsService.class);
        SysParamsDao sysParamsDao = mock(SysParamsDao.class);
        RestTemplate restTemplate = mock(RestTemplate.class);
        AgentService agentService = mock(AgentService.class);
        OpenClawConfigServiceImpl service = new OpenClawConfigServiceImpl(
                sysParamsService,
                sysParamsDao,
                restTemplate,
                agentService
        );

        when(sysParamsService.getValue(Constant.SERVER_OPENCLAW_CHANNELS, false)).thenReturn("[]");
        when(sysParamsService.getValue(Constant.SERVER_SECRET, true)).thenReturn("local-admin-secret");
        when(sysParamsService.updateValueByCode(eq(Constant.SERVER_OPENCLAW_CHANNELS), anyString())).thenReturn(1);

        OpenClawChannelDTO request = new OpenClawChannelDTO();
        request.setName("本地接入");

        OpenClawChannelDTO created = service.createChannel(request, "https://robot.local");

        assertEquals("https://robot.local/admin/openclaw", created.getBaseUrl());
        assertEquals("/inventory", created.getInventoryPath());
        assertEquals("local-admin-secret", created.getAccessToken());
    }

    @Test
    @DisplayName("拉取 inventory 时应按 channelId 限定 account")
    void getChannelInventoryShouldScopeAccountByChannelId() {
        SysParamsService sysParamsService = mock(SysParamsService.class);
        SysParamsDao sysParamsDao = mock(SysParamsDao.class);
        RestTemplate restTemplate = mock(RestTemplate.class);
        AgentService agentService = mock(AgentService.class);
        OpenClawConfigServiceImpl service = new OpenClawConfigServiceImpl(
                sysParamsService,
                sysParamsDao,
                restTemplate,
                agentService
        );

        OpenClawChannelDTO channel = new OpenClawChannelDTO();
        channel.setId("local-channel");
        channel.setName("本地 Channel");
        channel.setBaseUrl("https://runtime.example/admin/openclaw");
        channel.setInventoryPath("/inventory");
        channel.setAccessToken("runtime-token");
        channel.setEnabled(true);

        when(sysParamsService.getValue(Constant.SERVER_OPENCLAW_CHANNELS, false))
                .thenReturn(JsonUtils.toJsonString(java.util.List.of(channel)));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(
                        "{\"ok\":true,\"healthy\":true,"
                                + "\"runtimeAccounts\":[{\"value\":\"local-channel\",\"label\":\"本地 Channel\"}],"
                                + "\"agents\":[{\"value\":\"agent-a\",\"label\":\"Agent A\"}],"
                                + "\"bridges\":[{\"bridgeId\":\"bridge-a\",\"account\":\"local-channel\",\"connected\":true}]}"
                ));

        OpenClawChannelInventoryDTO inventory = service.getChannelInventory("local-channel");

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(urlCaptor.capture(), eq(HttpMethod.GET), entityCaptor.capture(), eq(String.class));

        assertEquals("https://runtime.example/admin/openclaw/inventory?account=local-channel", urlCaptor.getValue());
        HttpHeaders headers = entityCaptor.getValue().getHeaders();
        assertEquals("Bearer runtime-token", headers.getFirst(HttpHeaders.AUTHORIZATION));
        assertTrue(headers.getOrEmpty("X-OpenClaw-Token").contains("runtime-token"));
        assertTrue(Boolean.TRUE.equals(inventory.getHealthy()));
        assertEquals("agent-a", inventory.getAgents().get(0).getValue());
        assertEquals("local-channel", inventory.getRuntimeAccounts().get(0).getValue());
    }
}

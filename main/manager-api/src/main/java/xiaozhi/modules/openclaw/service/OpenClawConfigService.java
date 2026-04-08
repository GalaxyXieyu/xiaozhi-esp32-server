package xiaozhi.modules.openclaw.service;

import java.util.List;

import xiaozhi.modules.openclaw.dto.OpenClawAgentBindingDTO;
import xiaozhi.modules.openclaw.dto.OpenClawChannelDTO;
import xiaozhi.modules.openclaw.dto.OpenClawChannelInventoryDTO;
import xiaozhi.modules.openclaw.dto.OpenClawChannelSetupGuideDTO;
import xiaozhi.modules.openclaw.dto.OpenClawClearSessionRequestDTO;
import xiaozhi.modules.openclaw.dto.OpenClawClearSessionResponseDTO;
import xiaozhi.modules.openclaw.dto.OpenClawConnectionDTO;
import xiaozhi.modules.openclaw.dto.OpenClawDebugChatRequestDTO;
import xiaozhi.modules.openclaw.dto.OpenClawDebugChatResponseDTO;
import xiaozhi.modules.openclaw.dto.OpenClawVoiceInterruptRequestDTO;
import xiaozhi.modules.openclaw.dto.OpenClawVoiceInterruptResponseDTO;

public interface OpenClawConfigService {
    List<OpenClawChannelDTO> getChannels();

    List<OpenClawChannelDTO> saveChannels(List<OpenClawChannelDTO> channels, String serverOrigin);

    OpenClawChannelDTO createChannel(OpenClawChannelDTO channel, String serverOrigin);

    OpenClawChannelDTO updateChannel(String channelId, OpenClawChannelDTO channel, String serverOrigin);

    void deleteChannel(String channelId);

    OpenClawChannelInventoryDTO getChannelInventory(String channelId);

    OpenClawChannelSetupGuideDTO getChannelSetupGuide(String channelId, String channelName, String serverOrigin);

    OpenClawDebugChatResponseDTO directChat(String channelId, OpenClawDebugChatRequestDTO request);

    OpenClawClearSessionResponseDTO clearSession(String channelId, OpenClawClearSessionRequestDTO request);

    List<OpenClawConnectionDTO> listConnections(String channelId);

    OpenClawVoiceInterruptResponseDTO getVoiceInterrupt(String channelId, OpenClawVoiceInterruptRequestDTO request);

    OpenClawVoiceInterruptResponseDTO setVoiceInterrupt(String channelId, OpenClawVoiceInterruptRequestDTO request);

    OpenClawAgentBindingDTO getAgentBinding(String agentId);

    OpenClawAgentBindingDTO saveAgentBinding(String agentId, OpenClawAgentBindingDTO binding);

    void ensureAgentType(String agentId, String agentType);
}

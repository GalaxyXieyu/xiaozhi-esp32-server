package xiaozhi.modules.openclaw.service;

import java.util.List;

import xiaozhi.modules.openclaw.dto.OpenClawAgentBindingDTO;
import xiaozhi.modules.openclaw.dto.OpenClawChannelDTO;
import xiaozhi.modules.openclaw.dto.OpenClawChannelInventoryDTO;
import xiaozhi.modules.openclaw.dto.OpenClawChannelSetupGuideDTO;

public interface OpenClawConfigService {
    List<OpenClawChannelDTO> getChannels();

    List<OpenClawChannelDTO> saveChannels(List<OpenClawChannelDTO> channels, String serverOrigin);

    OpenClawChannelInventoryDTO getChannelInventory(String channelId);

    OpenClawChannelSetupGuideDTO getChannelSetupGuide(String channelId, String channelName, String serverOrigin);

    OpenClawAgentBindingDTO getAgentBinding(String agentId);

    OpenClawAgentBindingDTO saveAgentBinding(String agentId, OpenClawAgentBindingDTO binding);

    void ensureAgentType(String agentId, String agentType);
}

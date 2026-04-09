import { getServiceUrl } from '../api';
import RequestService from '../httpRequest';

export default {
    getChannels(callback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/openclaw-config/channels`)
            .method('GET')
            .success((res) => {
                RequestService.clearRequestTime();
                callback(res);
            })
            .networkFail(() => {
                RequestService.reAjaxFun(() => {
                    this.getChannels(callback);
                });
            }).send();
    },
    saveChannels(channels, callback, failCallback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/openclaw-config/channels`)
            .method('PUT')
            .data(channels)
            .success((res) => {
                RequestService.clearRequestTime();
                callback(res);
            })
            .fail((err) => {
                RequestService.clearRequestTime();
                if (failCallback) {
                    failCallback(err);
                }
            })
            .networkFail(() => {
                RequestService.reAjaxFun(() => {
                    this.saveChannels(channels, callback, failCallback);
                });
            }).send();
    },
    createChannel(channel, callback, failCallback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/openclaw-config/channels`)
            .method('POST')
            .data(channel)
            .success((res) => {
                RequestService.clearRequestTime();
                callback(res);
            })
            .fail((err) => {
                RequestService.clearRequestTime();
                if (failCallback) {
                    failCallback(err);
                }
            })
            .networkFail(() => {
                RequestService.reAjaxFun(() => {
                    this.createChannel(channel, callback, failCallback);
                });
            }).send();
    },
    updateChannel(channelId, channel, callback, failCallback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/openclaw-config/channels/${channelId}`)
            .method('PUT')
            .data(channel)
            .success((res) => {
                RequestService.clearRequestTime();
                callback(res);
            })
            .fail((err) => {
                RequestService.clearRequestTime();
                if (failCallback) {
                    failCallback(err);
                }
            })
            .networkFail(() => {
                RequestService.reAjaxFun(() => {
                    this.updateChannel(channelId, channel, callback, failCallback);
                });
            }).send();
    },
    deleteChannel(channelId, callback, failCallback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/openclaw-config/channels/${channelId}`)
            .method('DELETE')
            .success((res) => {
                RequestService.clearRequestTime();
                callback(res);
            })
            .fail((err) => {
                RequestService.clearRequestTime();
                if (failCallback) {
                    failCallback(err);
                }
            })
            .networkFail(() => {
                RequestService.reAjaxFun(() => {
                    this.deleteChannel(channelId, callback, failCallback);
                });
            }).send();
    },
    getChannelInventory(channelId, callback, failCallback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/openclaw-config/channels/${channelId}/inventory`)
            .method('GET')
            .success((res) => {
                RequestService.clearRequestTime();
                callback(res);
            })
            .fail((err) => {
                RequestService.clearRequestTime();
                if (failCallback) {
                    failCallback(err);
                }
            })
            .networkFail(() => {
                RequestService.reAjaxFun(() => {
                    this.getChannelInventory(channelId, callback, failCallback);
                });
            }).send();
    },
    getChannelSetupGuide(channelId, callback, failCallback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/openclaw-config/channels/${channelId}/setup-guide`)
            .method('GET')
            .success((res) => {
                RequestService.clearRequestTime();
                callback(res);
            })
            .fail((err) => {
                RequestService.clearRequestTime();
                if (failCallback) {
                    failCallback(err);
                }
            })
            .networkFail(() => {
                RequestService.reAjaxFun(() => {
                    this.getChannelSetupGuide(channelId, callback, failCallback);
                });
            }).send();
    },
    directChat(channelId, data, callback, failCallback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/openclaw-config/channels/${channelId}/direct-chat`)
            .method('POST')
            .data(data)
            .success((res) => {
                RequestService.clearRequestTime();
                callback(res);
            })
            .fail((err) => {
                RequestService.clearRequestTime();
                if (failCallback) {
                    failCallback(err);
                }
            })
            .networkFail(() => {
                RequestService.reAjaxFun(() => {
                    this.directChat(channelId, data, callback, failCallback);
                });
            }).send();
    },
    getDebugSession(channelId, debugSessionId, params, callback, failCallback) {
        const query = new URLSearchParams();
        if (params && params.account) {
            query.append('account', params.account);
        }
        if (params && params.bridgeId) {
            query.append('bridgeId', params.bridgeId);
        }
        if (params && Number.isInteger(params.sinceSeq)) {
            query.append('sinceSeq', `${params.sinceSeq}`);
        }
        const suffix = query.toString() ? `?${query.toString()}` : '';
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/openclaw-config/channels/${channelId}/debug-sessions/${encodeURIComponent(debugSessionId)}${suffix}`)
            .method('GET')
            .success((res) => {
                RequestService.clearRequestTime();
                callback(res);
            })
            .fail((err) => {
                RequestService.clearRequestTime();
                if (failCallback) {
                    failCallback(err);
                }
            })
            .networkFail(() => {
                RequestService.reAjaxFun(() => {
                    this.getDebugSession(channelId, debugSessionId, params, callback, failCallback);
                });
            }).send();
    },
    clearSession(channelId, data, callback, failCallback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/openclaw-config/channels/${channelId}/clear-session`)
            .method('POST')
            .data(data)
            .success((res) => {
                RequestService.clearRequestTime();
                callback(res);
            })
            .fail((err) => {
                RequestService.clearRequestTime();
                if (failCallback) {
                    failCallback(err);
                }
            })
            .networkFail(() => {
                RequestService.reAjaxFun(() => {
                    this.clearSession(channelId, data, callback, failCallback);
                });
            }).send();
    },
    getConnections(channelId, callback, failCallback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/openclaw-config/channels/${channelId}/connections`)
            .method('GET')
            .success((res) => {
                RequestService.clearRequestTime();
                callback(res);
            })
            .fail((err) => {
                RequestService.clearRequestTime();
                if (failCallback) {
                    failCallback(err);
                }
            })
            .networkFail(() => {
                RequestService.reAjaxFun(() => {
                    this.getConnections(channelId, callback, failCallback);
                });
            }).send();
    },
    getChannelBindings(channelId, callback, failCallback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/openclaw-config/channels/${channelId}/bindings`)
            .method('GET')
            .success((res) => {
                RequestService.clearRequestTime();
                callback(res);
            })
            .fail((err) => {
                RequestService.clearRequestTime();
                if (failCallback) {
                    failCallback(err);
                }
            })
            .networkFail(() => {
                RequestService.reAjaxFun(() => {
                    this.getChannelBindings(channelId, callback, failCallback);
                });
            }).send();
    },
    getVoiceInterrupt(channelId, params, callback, failCallback) {
        const search = new URLSearchParams();
        Object.entries(params || {}).forEach(([key, value]) => {
            if (value !== undefined && value !== null && value !== "") {
                search.append(key, value);
            }
        });
        const query = search.toString();
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/openclaw-config/channels/${channelId}/voice-interrupt${query ? `?${query}` : ''}`)
            .method('GET')
            .success((res) => {
                RequestService.clearRequestTime();
                callback(res);
            })
            .fail((err) => {
                RequestService.clearRequestTime();
                if (failCallback) {
                    failCallback(err);
                }
            })
            .networkFail(() => {
                RequestService.reAjaxFun(() => {
                    this.getVoiceInterrupt(channelId, params, callback, failCallback);
                });
            }).send();
    },
    setVoiceInterrupt(channelId, data, callback, failCallback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/openclaw-config/channels/${channelId}/voice-interrupt`)
            .method('POST')
            .data(data)
            .success((res) => {
                RequestService.clearRequestTime();
                callback(res);
            })
            .fail((err) => {
                RequestService.clearRequestTime();
                if (failCallback) {
                    failCallback(err);
                }
            })
            .networkFail(() => {
                RequestService.reAjaxFun(() => {
                    this.setVoiceInterrupt(channelId, data, callback, failCallback);
                });
            }).send();
    },
    getAgentBinding(agentId, callback, failCallback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/openclaw-config/agents/${agentId}`)
            .method('GET')
            .success((res) => {
                RequestService.clearRequestTime();
                callback(res);
            })
            .fail((err) => {
                RequestService.clearRequestTime();
                if (failCallback) {
                    failCallback(err);
                }
            })
            .networkFail(() => {
                RequestService.reAjaxFun(() => {
                    this.getAgentBinding(agentId, callback, failCallback);
                });
            }).send();
    },
    updateAgentBinding(agentId, data, callback, failCallback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/openclaw-config/agents/${agentId}`)
            .method('PUT')
            .data(data)
            .success((res) => {
                RequestService.clearRequestTime();
                callback(res);
            })
            .fail((err) => {
                RequestService.clearRequestTime();
                if (failCallback) {
                    failCallback(err);
                }
            })
            .networkFail(() => {
                RequestService.reAjaxFun(() => {
                    this.updateAgentBinding(agentId, data, callback, failCallback);
                });
            }).send();
    },
};

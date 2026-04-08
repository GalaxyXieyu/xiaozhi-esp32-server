<template>
  <div class="welcome">
    <HeaderBar />

    <div class="operation-bar">
      <div class="title-block">
        <h2 class="page-title">设备运行时控制台</h2>
        <p class="page-subtitle">统一管理设备播报打断，并保留 OpenClaw Channel 与在线调试能力。后台入口固定在顶部导航的“设备运行时控制”。</p>
      </div>
      <div class="page-actions">
        <el-button class="ghost-btn" @click="goToRoleConfig">返回智能体配置</el-button>
        <el-button class="ghost-btn" @click="resetDraft">新建 Channel</el-button>
        <el-button type="primary" class="refresh-btn" :loading="loading" @click="loadChannels">刷新</el-button>
      </div>
    </div>

    <div class="main-wrapper">
      <div class="content-panel">
        <div
          class="content-area"
          v-loading="loading"
          element-loading-text="正在加载 OpenClaw channel 配置"
          element-loading-spinner="el-icon-loading"
          element-loading-background="rgba(255, 255, 255, 0.72)"
        >
          <div class="overview-strip">
            <div class="overview-intro">
              <div class="section-eyebrow">Runtime Workbench</div>
              <h3 class="overview-title">先控设备运行时，再做 Channel 接入和在线调试</h3>
              <p class="overview-description">
                运行时语音打断是当前页的主任务。Channel、Inventory 和在线调试保留为同一工作台里的辅助分区，避免继续堆成长页。
              </p>
            </div>
            <div class="overview-stats">
              <div class="overview-stat">
                <span class="overview-stat-label">当前 Channel</span>
                <strong class="overview-stat-value">{{ draft.name || "未选择" }}</strong>
                <span class="overview-stat-note">{{ draft.id || "保存后生成 account id" }}</span>
              </div>
              <div class="overview-stat">
                <span class="overview-stat-label">安装命令</span>
                <strong class="overview-stat-value">{{ commandStatusText }}</strong>
                <span class="overview-stat-note">默认 Agent：{{ setupGuide.defaultAgentId || "main" }}</span>
              </div>
              <div class="overview-stat">
                <span class="overview-stat-label">Inventory</span>
                <strong class="overview-stat-value">{{ inventoryStatusText }}</strong>
                <span class="overview-stat-note">{{ bridgeStatusText }}</span>
              </div>
              <div class="overview-stat">
                <span class="overview-stat-label">在线设备</span>
                <strong class="overview-stat-value">{{ connections.length }}</strong>
                <span class="overview-stat-note">{{ voiceInterruptScopeText }}</span>
              </div>
            </div>
          </div>

          <div class="workspace-shell">
            <aside class="workspace-sidebar">
              <el-card class="surface-card sidebar-card registry-card" shadow="never">
                <div class="section-header">
                  <div>
                    <div class="section-eyebrow">Channel Registry</div>
                    <h3>已绑定 Channel</h3>
                  </div>
                  <span class="tool-count">{{ channels.length }} 个</span>
                </div>
                <p class="section-description sidebar-description">
                  智能体绑定页只消费这里的 channel。左侧只负责选上下文，右侧负责编辑与控制。
                </p>
                <div class="sidebar-toolbar">
                  <el-button size="small" type="primary" plain @click="resetDraft">新建</el-button>
                  <el-button size="small" @click="loadChannels" :loading="loading">刷新列表</el-button>
                </div>
                <div v-if="channels.length" class="channel-list">
                  <button
                    v-for="item in channels"
                    :key="item.id"
                    class="channel-item"
                    :class="{ active: item.id === draft.id }"
                    @click="selectChannel(item)"
                  >
                    <div class="channel-item-main">
                      <span class="channel-name">{{ item.name || "未命名 channel" }}</span>
                      <el-tag size="mini" :type="item.enabled ? 'success' : 'info'">{{ item.enabled ? "启用" : "停用" }}</el-tag>
                    </div>
                    <span class="channel-url">Account: {{ item.id }}</span>
                    <span class="channel-hint">{{ item.remark || "保存后可生成安装命令并同步 inventory" }}</span>
                  </button>
                </div>
                <el-empty v-else description="尚未绑定 OpenClaw channel" :image-size="88" />
              </el-card>

              <el-card class="surface-card sidebar-card context-card" shadow="never">
                <div class="section-eyebrow">Selected Context</div>
                <div class="context-title">{{ draft.name || "尚未选择 Channel" }}</div>
                <div class="context-id">{{ draft.id || "点击左侧 Channel 或直接新建" }}</div>
                <div class="context-grid">
                  <div class="context-metric">
                    <span>命令</span>
                    <strong>{{ commandStatusText }}</strong>
                  </div>
                  <div class="context-metric">
                    <span>Inventory</span>
                    <strong>{{ inventoryStatusText }}</strong>
                  </div>
                  <div class="context-metric">
                    <span>Bridge</span>
                    <strong>{{ inventory.bridges.length || 0 }}</strong>
                  </div>
                  <div class="context-metric">
                    <span>在线设备</span>
                    <strong>{{ connections.length || 0 }}</strong>
                  </div>
                </div>
                <div class="step-list compact-step-list">
                  <div class="step-item">
                    <span class="step-index">1</span>
                    <span>在左侧选一个 channel，或创建新 channel</span>
                  </div>
                  <div class="step-item">
                    <span class="step-index">2</span>
                    <span>在右侧 tab 内完成接入、运行时控制与观测</span>
                  </div>
                  <div class="step-item">
                    <span class="step-index">3</span>
                    <span>在线调试统一走弹窗工作台，不再塞回长页面</span>
                  </div>
                </div>
              </el-card>
            </aside>

            <section class="workspace-main">
              <el-card class="surface-card workspace-card" shadow="never">
                <el-tabs v-model="activeWorkspaceTab" class="workspace-tabs">
                  <el-tab-pane label="运行时控制" name="runtime">
                    <div class="tab-panel">
                      <div class="section-header">
                        <div>
                          <div class="section-eyebrow">Runtime Voice Interrupt</div>
                          <h3>运行时语音打断</h3>
                        </div>
                        <div class="inventory-tags">
                          <el-tag :type="voiceInterruptState.enabled ? 'success' : 'info'">
                            {{ voiceInterruptState.enabled ? "当前开启" : "当前关闭" }}
                          </el-tag>
                          <el-tag size="mini" type="info">{{ voiceInterruptScopeText }}</el-tag>
                          <el-button size="small" :loading="voiceInterruptLoading || connectionsLoading" @click="refreshVoiceInterruptPanel">
                            刷新
                          </el-button>
                        </div>
                      </div>
                      <p class="section-description">
                        这里只控制设备播报时是否允许被人声打断。它作用于当前 runtime 下的所有 ESP32 在线连接，不区分原生 Agent 还是 OpenClaw Agent；但它不是整机唤醒词，也不是常开收音总开关。
                      </p>
                      <el-alert
                        v-if="draft.id"
                        title="这块控制的是 runtime 连接级别的播报打断开关，同一台服务器上的原生模式和 OpenClaw 模式设备都会生效。"
                        type="success"
                        :closable="false"
                        show-icon
                        class="top-alert"
                      />
                      <el-alert
                        v-if="!draft.id"
                        title="请先保存并选择一个 Channel，再管理运行时语音打断。"
                        type="info"
                        :closable="false"
                        show-icon
                        class="top-alert"
                      />
                      <div v-else class="interrupt-shell">
                        <div class="interrupt-summary-grid">
                          <div class="interrupt-summary-card">
                            <div class="interrupt-summary-label">状态来源</div>
                            <div class="interrupt-summary-value">{{ voiceInterruptSourceText }}</div>
                          </div>
                          <div class="interrupt-summary-card">
                            <div class="interrupt-summary-label">作用范围</div>
                            <div class="interrupt-summary-value">{{ voiceInterruptScopeText }}</div>
                          </div>
                          <div class="interrupt-summary-card">
                            <div class="interrupt-summary-label">当前定位</div>
                            <div class="interrupt-summary-value">{{ voiceInterruptTargetText }}</div>
                          </div>
                          <div class="interrupt-summary-card">
                            <div class="interrupt-summary-label">最近变更</div>
                            <div class="interrupt-summary-value">{{ voiceInterruptUpdateText }}</div>
                          </div>
                        </div>

                        <div class="interrupt-control-block">
                          <div>
                            <div class="inventory-title">全局默认值</div>
                            <div class="command-hint">会同步更新当前在线连接，但已持久化到设备维度的机器会被跳过。</div>
                          </div>
                          <div class="inline-actions">
                            <el-button
                              size="small"
                              type="primary"
                              :loading="voiceInterruptActionKey === 'global-on'"
                              @click="setGlobalVoiceInterrupt(true)"
                            >
                              全局开启
                            </el-button>
                            <el-button
                              size="small"
                              type="warning"
                              plain
                              :loading="voiceInterruptActionKey === 'global-off'"
                              @click="setGlobalVoiceInterrupt(false)"
                            >
                              全局关闭
                            </el-button>
                          </div>
                        </div>

                        <div class="interrupt-control-block">
                          <div>
                            <div class="inventory-title">按设备 ID 控制</div>
                            <div class="command-hint">适合后台直接指定某台 ESP32。勾选“持久化”后，新连接建立时也会沿用该设备配置，不区分它后面跑的是原生还是 OpenClaw。</div>
                          </div>
                          <div class="interrupt-manual-row">
                            <el-input
                              v-model.trim="manualVoiceInterruptDeviceId"
                              class="interrupt-device-input"
                              placeholder="输入 deviceId，例如 MAC 或业务设备号"
                            />
                            <el-checkbox v-model="manualVoiceInterruptPersist">持久化到设备</el-checkbox>
                            <el-button
                              size="small"
                              :loading="voiceInterruptActionKey === 'inspect-device'"
                              @click="inspectManualVoiceInterrupt"
                            >
                              查询
                            </el-button>
                            <el-button
                              size="small"
                              type="primary"
                              :loading="voiceInterruptActionKey === 'device-on'"
                              @click="applyManualVoiceInterrupt(true)"
                            >
                              开启
                            </el-button>
                            <el-button
                              size="small"
                              type="warning"
                              plain
                              :loading="voiceInterruptActionKey === 'device-off'"
                              @click="applyManualVoiceInterrupt(false)"
                            >
                              关闭
                            </el-button>
                          </div>
                        </div>

                        <div class="bridge-strip">
                          <div class="section-header compact-header">
                            <div>
                              <div class="inventory-title">当前在线设备</div>
                              <div class="command-hint">“当前连接”只影响这次在线会话；“按设备”会写成设备维度策略。</div>
                            </div>
                            <el-tag size="mini" :type="connections.length ? 'success' : 'info'">{{ connections.length }} 台在线</el-tag>
                          </div>
                          <div v-if="connections.length" class="connection-grid">
                            <div v-for="item in connections" :key="item.sessionId" class="connection-card">
                              <div class="connection-card-head">
                                <div>
                                  <div class="bridge-name">{{ item.deviceId || item.sessionId }}</div>
                                  <div class="bridge-meta-line">Session: {{ item.sessionId || "-" }}</div>
                                </div>
                                <div class="inventory-tags">
                                  <el-tag size="mini" :type="item.voiceInterruptEnabled ? 'success' : 'info'">
                                    {{ item.voiceInterruptEnabled ? "可打断" : "不可打断" }}
                                  </el-tag>
                                  <el-tag v-if="item.isLatest" size="mini" type="warning">Latest</el-tag>
                                </div>
                              </div>
                              <div class="bridge-meta-line">Device ID: {{ item.deviceId || "-" }}</div>
                              <div class="bridge-meta-line">Client IP: {{ item.clientIp || "-" }}</div>
                              <div class="bridge-meta-line">连接时间: {{ formatConnectionTime(item.registeredAt) }}</div>
                              <div class="connection-actions">
                                <el-button
                                  size="mini"
                                  type="primary"
                                  :loading="voiceInterruptActionKey === `session-on:${item.sessionId}`"
                                  @click="setConnectionVoiceInterrupt(item, true, false)"
                                >
                                  当前连接开启
                                </el-button>
                                <el-button
                                  size="mini"
                                  type="warning"
                                  plain
                                  :loading="voiceInterruptActionKey === `session-off:${item.sessionId}`"
                                  @click="setConnectionVoiceInterrupt(item, false, false)"
                                >
                                  当前连接关闭
                                </el-button>
                                <el-button
                                  size="mini"
                                  plain
                                  :disabled="!item.deviceId"
                                  :loading="voiceInterruptActionKey === `persist-on:${item.deviceId}`"
                                  @click="setConnectionVoiceInterrupt(item, true, true)"
                                >
                                  按设备开启
                                </el-button>
                                <el-button
                                  size="mini"
                                  plain
                                  :disabled="!item.deviceId"
                                  :loading="voiceInterruptActionKey === `persist-off:${item.deviceId}`"
                                  @click="setConnectionVoiceInterrupt(item, false, true)"
                                >
                                  按设备关闭
                                </el-button>
                              </div>
                            </div>
                          </div>
                          <el-empty v-else description="当前没有在线 ESP32 连接" :image-size="72" />
                        </div>
                      </div>
                    </div>
                  </el-tab-pane>

                  <el-tab-pane :label="draft.id ? 'Channel 接入' : '新建 Channel'" name="channel">
                    <div class="tab-panel">
                      <div class="section-header">
                        <div>
                          <div class="section-eyebrow">Channel Editor</div>
                          <h3>{{ draft.id ? "编辑 Channel" : "新增 Channel" }}</h3>
                        </div>
                        <div class="inline-actions">
                          <el-button size="small" @click="resetDraft">清空</el-button>
                          <el-button size="small" type="danger" plain :disabled="!draft.id" @click="removeDraftChannel">删除</el-button>
                        </div>
                      </div>
                      <p class="section-description">
                        主流程只需要填写名称。保存后系统会生成 account id、安装命令和后台接入参数。
                      </p>

                      <div class="channel-editor-grid">
                        <div class="editor-pane">
                          <el-form label-position="top" class="channel-form">
                            <el-form-item label="Channel 名称">
                              <el-input v-model="draft.name" maxlength="64" placeholder="例如：生产 Runtime" />
                            </el-form-item>
                            <el-form-item label="备注">
                              <el-input v-model="draft.remark" type="textarea" :rows="3" resize="none" maxlength="200" placeholder="可选，用于记录用途或环境说明" />
                            </el-form-item>
                            <div class="channel-toggle">
                              <span>启用该 channel</span>
                              <el-switch v-model="draft.enabled" />
                            </div>
                            <div class="channel-actions">
                              <el-button type="primary" @click="saveChannels" :loading="saving">保存 Channel 配置</el-button>
                              <el-button @click="copyInstallCommand" :disabled="!setupGuide.installCommand">复制安装命令</el-button>
                              <el-button @click="syncDraftInventory" :loading="inventoryLoading" :disabled="!draft.id">测试并拉取 Inventory</el-button>
                            </div>
                          </el-form>

                          <el-collapse v-model="advancedPanels" class="advanced-panel">
                            <el-collapse-item name="advanced" title="高级配置（通常不用改）">
                              <el-form label-position="top" class="channel-form advanced-form">
                                <el-form-item label="管理接口基础地址">
                                  <el-input v-model="draft.baseUrl" placeholder="默认自动生成，例如：https://example.com/admin/openclaw" />
                                </el-form-item>
                                <el-form-item label="Inventory 路径">
                                  <el-input v-model="draft.inventoryPath" placeholder="/inventory" />
                                </el-form-item>
                                <el-form-item label="Access Token">
                                  <el-input v-model="draft.accessToken" show-password placeholder="默认自动注入 server secret" />
                                </el-form-item>
                              </el-form>
                            </el-collapse-item>
                          </el-collapse>
                        </div>

                        <div class="editor-pane guide-pane">
                          <div class="section-eyebrow">Setup Flow</div>
                          <div class="step-list compact-step-list">
                            <div class="step-item">
                              <span class="step-index">1</span>
                              <span>填写 Channel 名称并保存</span>
                            </div>
                            <div class="step-item">
                              <span class="step-index">2</span>
                              <span>复制命令到 OpenClaw 项目目录执行</span>
                            </div>
                            <div class="step-item">
                              <span class="step-index">3</span>
                              <span>执行完成后回来点击“测试并拉取 Inventory”</span>
                            </div>
                          </div>

                          <div class="command-panel" v-loading="guideLoading">
                            <div class="command-header">
                              <div>
                                <div class="section-eyebrow">Setup Command</div>
                                <h4 class="command-title">可复制的 OpenClaw 安装命令</h4>
                              </div>
                              <el-tag size="mini" :type="setupGuide.installCommand ? 'success' : 'info'">{{ commandStatusText }}</el-tag>
                            </div>
                            <div class="command-meta">
                              <div class="meta-line">
                                <span class="meta-line-key">Account ID</span>
                                <span class="meta-line-value">{{ setupGuide.channelId || "保存后自动生成" }}</span>
                              </div>
                              <div class="meta-line">
                                <span class="meta-line-key">默认 Agent</span>
                                <span class="meta-line-value">{{ setupGuide.defaultAgentId || "main" }}</span>
                              </div>
                            </div>
                            <el-alert
                              v-if="!setupGuide.accessTokenConfigured && draft.id"
                              title="系统未检测到 server secret，当前无法生成可执行命令，请先补齐后台 server.secret。"
                              type="warning"
                              :closable="false"
                              show-icon
                              class="top-alert"
                            />
                            <el-input
                              type="textarea"
                              :rows="6"
                              resize="none"
                              readonly
                              class="command-textarea"
                              :value="setupGuide.installCommand || '先保存 Channel，系统会在这里生成可直接复制的 npx 命令。'"
                            />
                            <div class="command-hint">
                              命令示例用法：进入 OpenClaw 项目目录后执行即可，例如 `cd ~/openclaw && ...`。命令里已经带好 server、admin key、account 和 channel 名称。
                            </div>
                          </div>
                        </div>
                      </div>
                    </div>
                  </el-tab-pane>

                  <el-tab-pane label="观测与调试" name="observe">
                    <div class="tab-panel observe-grid">
                      <div class="observe-main">
                        <div class="section-header">
                          <div>
                            <div class="section-eyebrow">Inventory</div>
                            <h3>Channel 可选项</h3>
                          </div>
                          <div class="inventory-tags">
                            <el-tag :type="inventory.healthy ? 'success' : 'warning'">{{ inventoryStatusText }}</el-tag>
                            <el-tag size="mini" :type="(inventory.connectedBridgeCount || 0) > 0 ? 'success' : 'info'">{{ bridgeStatusText }}</el-tag>
                          </div>
                        </div>
                        <p class="section-description">
                          这里展示 channel 实际回传的 runtime/account 与 OpenClaw agent 列表。智能体绑定页会直接消费这些下拉项。
                        </p>
                        <el-alert
                          v-if="inventory.errorMessage"
                          :title="inventory.errorMessage"
                          type="warning"
                          :closable="false"
                          show-icon
                          class="top-alert"
                        />
                        <div class="bridge-strip">
                          <div class="inventory-title">Bridge 状态</div>
                          <div v-if="inventory.bridges.length" class="bridge-grid">
                            <div v-for="item in inventory.bridges" :key="item.bridgeId" class="bridge-card">
                              <div class="bridge-card-head">
                                <span class="bridge-name">{{ item.name || item.bridgeId }}</span>
                                <el-tag size="mini" :type="item.connected ? 'success' : 'info'">{{ item.connected ? "在线" : "离线" }}</el-tag>
                              </div>
                              <div class="bridge-meta-line">Account: {{ item.account || "-" }}</div>
                              <div class="bridge-meta-line">Bridge ID: {{ item.bridgeId }}</div>
                            </div>
                          </div>
                          <el-empty v-else description="当前未发现 OpenClaw bridge" :image-size="72" />
                        </div>
                        <div class="inventory-grid">
                          <div class="inventory-block">
                            <div class="inventory-title">Runtime / Account</div>
                            <div v-if="inventory.runtimeAccounts.length" class="inventory-list">
                              <div v-for="item in inventory.runtimeAccounts" :key="item.value" class="inventory-chip">
                                {{ item.label }}
                              </div>
                            </div>
                            <el-empty v-else description="当前未返回 runtime/account 选项" :image-size="80" />
                          </div>
                          <div class="inventory-block">
                            <div class="inventory-title">OpenClaw Agents</div>
                            <div v-if="inventory.agents.length" class="inventory-list">
                              <div v-for="item in inventory.agents" :key="item.value" class="inventory-chip">
                                {{ item.label }}
                              </div>
                            </div>
                            <el-empty v-else description="当前未返回 OpenClaw agent 选项" :image-size="80" />
                          </div>
                        </div>
                        <div class="runtime-list">
                          <div class="runtime-item">
                            <span class="runtime-path">Source URL</span>
                            <span class="runtime-note">{{ inventory.sourceUrl || "尚未测试 inventory 接口" }}</span>
                          </div>
                          <div class="runtime-item">
                            <span class="runtime-path">绑定策略</span>
                            <span class="runtime-note">先绑定 Channel，再让智能体表单按 channel 下拉选择 runtime/account 和 OpenClaw agent。</span>
                          </div>
                        </div>
                      </div>

                      <div class="observe-side">
                        <div class="debug-entry-card">
                          <div class="section-header">
                            <div>
                              <div class="section-eyebrow">Online Console</div>
                              <h3>OpenClaw 在线调试台</h3>
                            </div>
                            <el-button
                              type="primary"
                              class="debug-open-btn"
                              :disabled="!draft.id || !inventory.runtimeAccounts.length"
                              @click="showDebugDialog = true"
                            >
                              打开调试台
                            </el-button>
                          </div>
                          <p class="section-description">
                            在线调试已经改成独立弹窗工作台，避免在长页里挤压阅读空间。适合集中验证 channel 路由、agent 选择和回复文本。
                          </p>
                          <div class="debug-entry-grid">
                            <div class="debug-entry-stat">
                              <span class="debug-entry-label">当前 Channel</span>
                              <strong class="debug-entry-value">{{ draft.name || "未选择" }}</strong>
                            </div>
                            <div class="debug-entry-stat">
                              <span class="debug-entry-label">Runtime / Account</span>
                              <strong class="debug-entry-value">{{ inventory.runtimeAccounts.length || 0 }}</strong>
                            </div>
                            <div class="debug-entry-stat">
                              <span class="debug-entry-label">Bridge</span>
                              <strong class="debug-entry-value">{{ inventory.bridges.length || 0 }}</strong>
                            </div>
                            <div class="debug-entry-stat">
                              <span class="debug-entry-label">OpenClaw Agent</span>
                              <strong class="debug-entry-value">{{ inventory.agents.length || 0 }}</strong>
                            </div>
                          </div>
                          <el-alert
                            v-if="!draft.id"
                            title="请先保存并选择一个 Channel，再开始在线调试。"
                            type="info"
                            :closable="false"
                            show-icon
                            class="top-alert"
                          />
                          <el-alert
                            v-else-if="!inventory.runtimeAccounts.length"
                            title="当前还没有可用的 runtime/account，请先执行安装命令并同步 inventory。"
                            type="warning"
                            :closable="false"
                            show-icon
                            class="top-alert"
                          />
                        </div>
                      </div>
                    </div>
                  </el-tab-pane>
                </el-tabs>
              </el-card>
            </section>
          </div>
        </div>
      </div>
    </div>

    <OpenClawDebugDialog
      :visible.sync="showDebugDialog"
      :channel="draft"
      :inventory="inventory"
      :route-prefill="routePrefill"
    />

    <el-footer>
      <VersionFooter />
    </el-footer>
  </div>
</template>

<script>
import Api from "@/apis/api";
import HeaderBar from "@/components/HeaderBar.vue";
import OpenClawDebugDialog from "@/components/OpenClawDebugDialog.vue";
import VersionFooter from "@/components/VersionFooter.vue";

const createEmptyChannel = () => ({
  id: "",
  name: "",
  baseUrl: "",
  inventoryPath: "/inventory",
  accessToken: "",
  enabled: true,
  remark: "",
});

const createEmptyInventory = () => ({
  channelId: "",
  sourceUrl: "",
  healthy: false,
  errorMessage: "",
  runtimeAccounts: [],
  agents: [],
  bridges: [],
  accountAgents: {},
  bridgeAgents: {},
  connectedBridgeCount: 0,
});

const createEmptySetupGuide = () => ({
  channelId: "",
  channelName: "",
  serverUrl: "",
  baseUrl: "",
  inventoryPath: "/inventory",
  defaultAgentId: "main",
  accessTokenConfigured: true,
  installCommand: "",
});

const createEmptyVoiceInterruptState = () => ({
  channelId: "",
  sourceUrl: "",
  enabled: true,
  scope: "global",
  source: "runtime-default",
  sessionId: "",
  deviceId: "",
  updatedConnections: 0,
  skippedConnections: 0,
  persisted: false,
  online: null,
  rawResult: {},
});

const createEmptyRoutePrefill = () => ({
  channelId: "",
  runtimeAccount: "",
  openclawAgentId: "",
  openclawAgentName: "",
  entry: "",
});

export default {
  name: "OpenClawManagement",
  components: {
    HeaderBar,
    OpenClawDebugDialog,
    VersionFooter,
  },
  data() {
    return {
      loading: false,
      saving: false,
      guideLoading: false,
      inventoryLoading: false,
      agentId: "",
      channels: [],
      draft: createEmptyChannel(),
      inventory: createEmptyInventory(),
      setupGuide: createEmptySetupGuide(),
      voiceInterruptState: createEmptyVoiceInterruptState(),
      connections: [],
      voiceInterruptLoading: false,
      connectionsLoading: false,
      voiceInterruptActionKey: "",
      manualVoiceInterruptDeviceId: "",
      manualVoiceInterruptPersist: true,
      routePrefill: createEmptyRoutePrefill(),
      routePrefillApplied: false,
      activeWorkspaceTab: "runtime",
      advancedPanels: [],
      showDebugDialog: false,
    };
  },
  computed: {
    inventoryStatusText() {
      if (this.inventoryLoading) {
        return "同步中";
      }
      if (this.inventory.healthy) {
        return "已就绪";
      }
      if (this.inventory.errorMessage) {
        return "需检查";
      }
      return "未同步";
    },
    commandStatusText() {
      if (this.guideLoading) {
        return "生成中";
      }
      if (this.setupGuide.installCommand) {
        return "可复制";
      }
      if (this.draft.id) {
        return "待生成";
      }
      return "待保存";
    },
    bridgeStatusText() {
      const connected = this.inventory.connectedBridgeCount || 0;
      if (!this.draft.id) {
        return "未选择";
      }
      if (!this.inventory.bridges.length) {
        return "未接入";
      }
      return connected > 0 ? `${connected} 在线` : "全部离线";
    },
    voiceInterruptScopeText() {
      const scopeMap = {
        global: "全局默认",
        device: "设备维度",
        session: "当前会话",
      };
      return scopeMap[this.voiceInterruptState.scope] || "未查询";
    },
    voiceInterruptSourceText() {
      const sourceMap = {
        "runtime-default": "运行时默认值",
        connection: "在线连接",
        persisted: "设备持久化",
      };
      return sourceMap[this.voiceInterruptState.source] || "未查询";
    },
    voiceInterruptTargetText() {
      if (this.voiceInterruptState.deviceId) {
        return this.voiceInterruptState.deviceId;
      }
      if (this.voiceInterruptState.sessionId) {
        return this.voiceInterruptState.sessionId;
      }
      return "全局";
    },
    voiceInterruptUpdateText() {
      if (this.voiceInterruptState.scope === "global") {
        return `更新 ${this.voiceInterruptState.updatedConnections || 0}，跳过 ${this.voiceInterruptState.skippedConnections || 0}`;
      }
      if (this.voiceInterruptState.persisted) {
        return "已写入设备持久化策略";
      }
      if (this.voiceInterruptState.sessionId || this.voiceInterruptState.deviceId) {
        return "已定位到目标连接";
      }
      return "暂无变更";
    },
  },
  watch: {
    "$route.query": {
      immediate: true,
      handler(query) {
        const routeQuery = query || {};
        const entryTabMap = {
          channel: "channel",
          inventory: "observe",
          debug: "observe",
          runtime: "runtime",
        };
        this.agentId = routeQuery.agentId || "";
        this.routePrefill = {
          channelId: routeQuery.channelId || "",
          runtimeAccount: routeQuery.runtimeAccount || "",
          openclawAgentId: routeQuery.openclawAgentId || "",
          openclawAgentName: routeQuery.openclawAgentName || "",
          entry: routeQuery.entry || "",
        };
        if (entryTabMap[routeQuery.entry]) {
          this.activeWorkspaceTab = entryTabMap[routeQuery.entry];
        }
        this.routePrefillApplied = false;
      },
    },
  },
  created() {
    this.loadChannels();
  },
  methods: {
    loadChannels(preferredChannelId = "") {
      this.loading = true;
      Api.openclaw.getChannels(({ data }) => {
        this.loading = false;
        if (data.code === 0) {
          this.channels = Array.isArray(data.data) ? data.data : [];
          if (preferredChannelId) {
            const preferredChannel = this.channels.find((item) => item.id === preferredChannelId);
            if (preferredChannel) {
              this.selectChannel(preferredChannel);
              return;
            }
          }
          if (!this.routePrefillApplied && this.routePrefill.channelId) {
            const routedChannel = this.channels.find((item) => item.id === this.routePrefill.channelId);
            this.routePrefillApplied = true;
            if (routedChannel) {
              this.selectChannel(routedChannel);
              return;
            }
          }
          if (this.draft.id) {
            const matched = this.channels.find((item) => item.id === this.draft.id);
            if (matched) {
              this.selectChannel(matched);
              return;
            }
          }
          if (!this.draft.id && !this.draft.name && this.channels.length) {
            this.selectChannel(this.channels[0]);
          }
        } else {
          this.$message.error(data.msg || "获取 OpenClaw channel 列表失败");
        }
      });
    },
    selectChannel(channel) {
      this.draft = {
        id: channel.id || "",
        name: channel.name || "",
        baseUrl: channel.baseUrl || "",
        inventoryPath: channel.inventoryPath || "/inventory",
        accessToken: channel.accessToken || "",
        enabled: channel.enabled !== false,
        remark: channel.remark || "",
      };
      this.refreshSetupGuide();
      this.syncDraftInventory();
      this.refreshVoiceInterruptPanel();
    },
    resetDraft() {
      this.draft = createEmptyChannel();
      this.inventory = createEmptyInventory();
      this.setupGuide = createEmptySetupGuide();
      this.voiceInterruptState = createEmptyVoiceInterruptState();
      this.connections = [];
      this.voiceInterruptActionKey = "";
      this.manualVoiceInterruptDeviceId = "";
      this.manualVoiceInterruptPersist = true;
      this.activeWorkspaceTab = "channel";
      this.advancedPanels = [];
    },
    refreshSetupGuide() {
      if (!this.draft.id) {
        this.setupGuide = createEmptySetupGuide();
        return;
      }
      this.guideLoading = true;
      Api.openclaw.getChannelSetupGuide(this.draft.id, ({ data }) => {
        this.guideLoading = false;
        if (data.code === 0) {
          this.setupGuide = data.data || createEmptySetupGuide();
        } else {
          this.setupGuide = createEmptySetupGuide();
          this.$message.error(data.msg || "生成安装命令失败");
        }
      }, ({ data }) => {
        this.guideLoading = false;
        this.setupGuide = createEmptySetupGuide();
        this.$message.error((data && data.msg) || "生成安装命令失败");
      });
    },
    saveChannels() {
      if (!this.draft.name) {
        this.$message.warning("请先填写 Channel 名称");
        return;
      }
      this.saving = true;
      const payload = { ...this.draft };
      const onSuccess = ({ data }) => {
        this.saving = false;
        if (data.code === 0) {
          const savedChannel = data.data || {};
          this.loadChannels(savedChannel.id || this.draft.id || "");
          this.$message.success("OpenClaw channel 配置已保存");
        } else {
          this.$message.error(data.msg || "保存 OpenClaw channel 失败");
        }
      };
      const onFail = ({ data }) => {
        this.saving = false;
        this.$message.error((data && data.msg) || "保存 OpenClaw channel 失败");
      };
      if (this.draft.id) {
        Api.openclaw.updateChannel(this.draft.id, payload, onSuccess, onFail);
        return;
      }
      Api.openclaw.createChannel(payload, onSuccess, onFail);
    },
    removeDraftChannel() {
      if (!this.draft.id) {
        return;
      }
      const deletingId = this.draft.id;
      const fallbackChannel = this.channels.find((item) => item.id !== deletingId);
      this.saving = true;
      Api.openclaw.deleteChannel(deletingId, ({ data }) => {
        this.saving = false;
        if (data.code === 0) {
          this.resetDraft();
          this.loadChannels(fallbackChannel ? fallbackChannel.id : "");
          this.$message.success("OpenClaw channel 已删除");
        } else {
          this.$message.error(data.msg || "删除 OpenClaw channel 失败");
        }
      }, ({ data }) => {
        this.saving = false;
        this.$message.error((data && data.msg) || "删除 OpenClaw channel 失败");
      });
    },
    normalizeChannelPath(path, fallback = "/") {
      const text = (path || "").trim();
      if (!text) {
        return fallback;
      }
      return text.startsWith("/") ? text : `/${text}`;
    },
    buildChannelApiUrl(path, query = null) {
      const baseUrl = (this.draft.baseUrl || "").trim().replace(/\/+$/, "");
      if (!baseUrl) {
        throw new Error("当前 Channel 缺少 baseUrl");
      }
      const url = new URL(`${baseUrl}${this.normalizeChannelPath(path)}`);
      Object.entries(query || {}).forEach(([key, value]) => {
        if (value === undefined || value === null || value === "") {
          return;
        }
        url.searchParams.set(key, value);
      });
      return url.toString();
    },
    buildChannelApiHeaders(includeJson = false) {
      const headers = {};
      const accessToken = (this.draft.accessToken || "").trim();
      if (includeJson) {
        headers["Content-Type"] = "application/json";
      }
      if (accessToken) {
        headers.Authorization = `Bearer ${accessToken}`;
        headers["X-OpenClaw-Token"] = accessToken;
      }
      return headers;
    },
    async requestChannelEndpoint(path, { method = "GET", body = null, query = null } = {}) {
      const response = await fetch(this.buildChannelApiUrl(path, query), {
        method,
        headers: this.buildChannelApiHeaders(Boolean(body)),
        body: body ? JSON.stringify(body) : undefined,
      });
      const rawText = await response.text();
      let payload = {};
      if (rawText) {
        try {
          payload = JSON.parse(rawText);
        } catch (error) {
          throw new Error(`OpenClaw 接口返回了非 JSON 内容（HTTP ${response.status}）`);
        }
      }
      if (!response.ok || payload.ok === false) {
        throw new Error(
          payload.message || payload.errorMessage || payload.msg || `OpenClaw 接口请求失败（HTTP ${response.status}）`
        );
      }
      return payload && payload.data ? payload.data : payload;
    },
    pickChannelReplyText(result) {
      if (!result) {
        return "";
      }
      const tryKeys = (source, keys) => {
        for (const key of keys) {
          const value = source ? source[key] : "";
          if (typeof value === "string" && value.trim()) {
            return value.trim();
          }
        }
        return "";
      };
      if (typeof result === "string") {
        return result.trim();
      }
      const directText = tryKeys(result, ["text", "replyText", "reply", "message", "output"]);
      if (directText) {
        return directText;
      }
      const nestedPayloads = [
        result.data,
        result.payload,
        result.response,
        result.result,
      ];
      for (const item of nestedPayloads) {
        if (item && typeof item === "object") {
          const nestedText = tryKeys(item, ["text", "replyText", "reply", "message", "output"]);
          if (nestedText) {
            return nestedText;
          }
        }
      }
      return "";
    },
    async syncDraftInventory() {
      if (!this.draft.id) {
        this.inventory = {
          ...createEmptyInventory(),
          errorMessage: "请先保存 Channel，再同步 inventory。",
        };
        return;
      }
      this.inventoryLoading = true;
      try {
        const payload = await this.requestChannelEndpoint(
          this.draft.inventoryPath || "/inventory"
        );
        this.inventoryLoading = false;
        this.inventory = {
          ...createEmptyInventory(),
          ...(payload || {}),
          channelId: this.draft.id,
          sourceUrl: this.buildChannelApiUrl(this.draft.inventoryPath || "/inventory"),
        };
      } catch (error) {
        this.inventoryLoading = false;
        this.inventory = {
          ...createEmptyInventory(),
          channelId: this.draft.id,
          sourceUrl: (this.draft.baseUrl || "").trim(),
          errorMessage: error.message || "同步 OpenClaw inventory 失败",
        };
      }
    },
    refreshVoiceInterruptPanel() {
      if (!this.draft.id) {
        this.voiceInterruptState = createEmptyVoiceInterruptState();
        this.connections = [];
        return;
      }
      this.loadVoiceInterruptState();
      this.loadConnections();
    },
    loadConnections() {
      if (!this.draft.id) {
        this.connections = [];
        return;
      }
      this.connectionsLoading = true;
      Api.openclaw.getConnections(this.draft.id, ({ data }) => {
        this.connectionsLoading = false;
        if (data.code === 0) {
          this.connections = Array.isArray(data.data) ? data.data : [];
          return;
        }
        this.connections = [];
        this.$message.error(data.msg || "获取在线设备失败");
      }, ({ data }) => {
        this.connectionsLoading = false;
        this.connections = [];
        this.$message.error((data && data.msg) || "获取在线设备失败");
      });
    },
    loadVoiceInterruptState(params = {}, actionKey = "") {
      if (!this.draft.id) {
        this.voiceInterruptState = createEmptyVoiceInterruptState();
        return;
      }
      if (actionKey) {
        this.voiceInterruptActionKey = actionKey;
      } else {
        this.voiceInterruptLoading = true;
      }
      Api.openclaw.getVoiceInterrupt(this.draft.id, params, ({ data }) => {
        this.voiceInterruptLoading = false;
        if (actionKey) {
          this.voiceInterruptActionKey = "";
        }
        if (data.code === 0) {
          this.voiceInterruptState = data.data || createEmptyVoiceInterruptState();
          return;
        }
        this.$message.error(data.msg || "获取语音打断状态失败");
      }, ({ data }) => {
        this.voiceInterruptLoading = false;
        if (actionKey) {
          this.voiceInterruptActionKey = "";
        }
        this.$message.error((data && data.msg) || "获取语音打断状态失败");
      });
    },
    submitVoiceInterrupt(payload, actionKey, successMessage) {
      if (!this.draft.id) {
        this.$message.warning("请先保存并选择一个 Channel");
        return;
      }
      this.voiceInterruptActionKey = actionKey;
      Api.openclaw.setVoiceInterrupt(this.draft.id, payload, ({ data }) => {
        this.voiceInterruptActionKey = "";
        if (data.code === 0) {
          this.voiceInterruptState = data.data || createEmptyVoiceInterruptState();
          this.loadConnections();
          this.$message.success(successMessage);
          return;
        }
        this.$message.error(data.msg || "设置语音打断失败");
      }, ({ data }) => {
        this.voiceInterruptActionKey = "";
        this.$message.error((data && data.msg) || "设置语音打断失败");
      });
    },
    setGlobalVoiceInterrupt(enabled) {
      this.submitVoiceInterrupt(
        { enabled },
        enabled ? "global-on" : "global-off",
        enabled ? "已开启全局语音打断" : "已关闭全局语音打断"
      );
    },
    setConnectionVoiceInterrupt(connection, enabled, persist) {
      if (!connection || !connection.sessionId) {
        this.$message.warning("当前连接信息不完整");
        return;
      }
      if (persist && !connection.deviceId) {
        this.$message.warning("当前连接缺少 deviceId，无法写入设备维度策略");
        return;
      }
      const actionKey = persist
        ? `${enabled ? "persist-on" : "persist-off"}:${connection.deviceId}`
        : `${enabled ? "session-on" : "session-off"}:${connection.sessionId}`;
      const payload = persist ? {
        enabled,
        deviceId: connection.deviceId,
        persist: true,
      } : {
        enabled,
        sessionId: connection.sessionId,
      };
      this.submitVoiceInterrupt(
        payload,
        actionKey,
        persist
          ? `已${enabled ? "开启" : "关闭"}设备维度语音打断`
          : `已${enabled ? "开启" : "关闭"}当前连接语音打断`
      );
    },
    inspectManualVoiceInterrupt() {
      if (!this.manualVoiceInterruptDeviceId) {
        this.$message.warning("请先输入 deviceId");
        return;
      }
      this.loadVoiceInterruptState({ deviceId: this.manualVoiceInterruptDeviceId }, "inspect-device");
    },
    applyManualVoiceInterrupt(enabled) {
      if (!this.manualVoiceInterruptDeviceId) {
        this.$message.warning("请先输入 deviceId");
        return;
      }
      this.submitVoiceInterrupt(
        {
          enabled,
          deviceId: this.manualVoiceInterruptDeviceId,
          persist: this.manualVoiceInterruptPersist,
        },
        enabled ? "device-on" : "device-off",
        this.manualVoiceInterruptPersist
          ? `已${enabled ? "开启" : "关闭"}设备维度语音打断`
          : `已${enabled ? "开启" : "关闭"}当前设备在线连接语音打断`
      );
    },
    formatConnectionTime(timestamp) {
      if (timestamp === undefined || timestamp === null || timestamp === "") {
        return "-";
      }
      const numeric = Number(timestamp);
      if (Number.isNaN(numeric)) {
        return String(timestamp);
      }
      return new Date(numeric * 1000).toLocaleString();
    },
    copyInstallCommand() {
      if (!this.setupGuide.installCommand) {
        this.$message.warning("请先保存 Channel 并生成安装命令");
        return;
      }
      this.copyText(this.setupGuide.installCommand);
    },
    copyText(text) {
      if (navigator.clipboard && window.isSecureContext) {
        navigator.clipboard.writeText(text).then(() => {
          this.$message.success("安装命令已复制");
        }).catch(() => {
          this.copyTextFallback(text);
        });
        return;
      }
      this.copyTextFallback(text);
    },
    copyTextFallback(text) {
      const textarea = document.createElement("textarea");
      textarea.value = text;
      textarea.style.position = "fixed";
      textarea.style.opacity = "0";
      document.body.appendChild(textarea);
      textarea.select();

      try {
        const copied = document.execCommand("copy");
        if (copied) {
          this.$message.success("安装命令已复制");
        } else {
          this.$message.error("复制失败，请手动复制");
        }
      } catch (error) {
        this.$message.error("复制失败，请手动复制");
        console.error("复制安装命令失败:", error);
      } finally {
        document.body.removeChild(textarea);
      }
    },
    goToRoleConfig() {
      if (this.agentId) {
        this.$router.push({
          path: "/role-config",
          query: { agentId: this.agentId },
        });
        return;
      }
      this.$router.push("/role-config");
    },
  },
};
</script>

<style scoped>
.welcome {
  min-width: 0;
  min-height: 506px;
  height: 100vh;
  display: flex;
  flex-direction: column;
  background:
    radial-gradient(circle at top left, rgba(112, 146, 255, 0.18), transparent 24%),
    linear-gradient(145deg, #eef2ff, #f8fbff);
}

.operation-bar {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  padding: 24px 28px 14px;
}

.title-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-width: 760px;
}

.page-title {
  margin: 0;
  font-size: 30px;
  line-height: 1.15;
  color: #24304a;
}

.page-subtitle {
  margin: 0;
  font-size: 14px;
  line-height: 1.7;
  color: #6e7a92;
}

.page-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.ghost-btn,
.refresh-btn {
  border-radius: 999px;
}

.main-wrapper {
  flex: 1;
  min-height: 0;
  padding: 0 28px 28px;
}

.content-panel,
.content-area {
  height: 100%;
  min-height: 0;
}

.content-area {
  overflow: auto;
  padding-right: 4px;
}

.surface-card {
  border-radius: 24px;
  border: none;
  box-shadow: 0 14px 42px rgba(36, 48, 74, 0.08);
}

.overview-strip {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  padding: 22px 24px;
  border-radius: 26px;
  background:
    radial-gradient(circle at top right, rgba(87, 120, 255, 0.24), transparent 22%),
    linear-gradient(135deg, #ffffff, #eef4ff 58%, #f6f9ff);
  border: 1px solid rgba(182, 197, 235, 0.72);
}

.overview-intro {
  flex: 1;
  min-width: 0;
}

.overview-title {
  margin: 8px 0 0;
  font-size: 28px;
  line-height: 1.18;
  color: #22304f;
}

.overview-description {
  max-width: 760px;
  margin: 12px 0 0;
  color: #60708d;
  line-height: 1.7;
}

.overview-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  min-width: min(520px, 48%);
}

.overview-stat {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(248, 250, 255, 0.92);
  border: 1px solid rgba(214, 223, 243, 0.86);
}

.overview-stat-label {
  font-size: 12px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: #7a86a0;
}

.overview-stat-value {
  font-size: 18px;
  line-height: 1.2;
  color: #24304a;
  word-break: break-word;
}

.overview-stat-note {
  font-size: 12px;
  line-height: 1.5;
  color: #68758e;
  word-break: break-word;
}

.workspace-shell {
  display: grid;
  grid-template-columns: minmax(280px, 320px) minmax(0, 1fr);
  gap: 18px;
  margin-top: 20px;
  align-items: start;
}

.workspace-sidebar {
  position: sticky;
  top: 0;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.sidebar-card,
.workspace-card {
  padding: 10px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}

.section-eyebrow {
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.14em;
  color: #8090ae;
}

.section-header h3 {
  margin: 8px 0 0;
  font-size: 22px;
  color: #24304a;
}

.section-description {
  margin: 16px 0 0;
  color: #69758d;
  line-height: 1.7;
}

.sidebar-description {
  margin-bottom: 0;
}

.tool-count {
  color: #5d6882;
  font-weight: 600;
}

.sidebar-toolbar {
  display: flex;
  gap: 10px;
  margin-top: 18px;
}

.channel-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 16px;
  max-height: 480px;
  overflow: auto;
  padding-right: 4px;
}

.channel-item {
  width: 100%;
  padding: 14px 16px;
  border: 1px solid #e4eaf8;
  border-radius: 16px;
  background: linear-gradient(180deg, #fbfcff, #f5f8ff);
  text-align: left;
  cursor: pointer;
  transition: border-color 0.2s ease, transform 0.2s ease, box-shadow 0.2s ease;
}

.channel-item:hover {
  transform: translateY(-1px);
  border-color: #c7d5fa;
  box-shadow: 0 10px 24px rgba(87, 120, 255, 0.08);
}

.channel-item.active {
  border-color: #5778ff;
  background: linear-gradient(180deg, #f2f5ff, #e9efff);
  box-shadow: inset 0 0 0 1px rgba(87, 120, 255, 0.16);
}

.channel-item-main {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.channel-name {
  font-weight: 600;
  color: #24304a;
}

.channel-url {
  display: block;
  margin-top: 8px;
  color: #7a86a0;
  word-break: break-all;
}

.channel-hint {
  display: block;
  margin-top: 6px;
  color: #8a94ab;
  font-size: 12px;
  line-height: 1.5;
}

.context-card {
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.95), rgba(244, 247, 255, 0.98)),
    #fff;
}

.context-title {
  margin-top: 10px;
  font-size: 22px;
  line-height: 1.2;
  font-weight: 700;
  color: #24304a;
  word-break: break-word;
}

.context-id {
  margin-top: 8px;
  color: #6f7b95;
  word-break: break-word;
}

.context-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-top: 18px;
}

.context-metric {
  padding: 14px 16px;
  border-radius: 16px;
  background: #f7f9ff;
  border: 1px solid #e3e9f6;
}

.context-metric span {
  display: block;
  font-size: 12px;
  color: #7a86a0;
}

.context-metric strong {
  display: block;
  margin-top: 6px;
  font-size: 16px;
  color: #24304a;
  word-break: break-word;
}

.step-list {
  display: grid;
  gap: 10px;
  margin-top: 18px;
}

.compact-step-list {
  margin-top: 16px;
}

.step-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 16px;
  background: #f8faff;
  color: #44506a;
  line-height: 1.6;
}

.step-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  flex-shrink: 0;
  border-radius: 999px;
  background: #223d7a;
  color: #fff;
  font-size: 12px;
  font-weight: 700;
}

.workspace-main {
  min-width: 0;
}

.workspace-card {
  min-height: calc(100vh - 240px);
}

::v-deep .workspace-tabs .el-tabs__header {
  margin-bottom: 8px;
}

::v-deep .workspace-tabs .el-tabs__nav-wrap::after {
  background-color: #e2e8f6;
}

::v-deep .workspace-tabs .el-tabs__item {
  height: 44px;
  line-height: 44px;
  font-size: 15px;
  color: #5d6b88;
}

::v-deep .workspace-tabs .el-tabs__item.is-active {
  color: #3153a6;
  font-weight: 600;
}

::v-deep .workspace-tabs .el-tabs__active-bar {
  height: 3px;
  border-radius: 999px;
  background: linear-gradient(90deg, #2f58b3, #6284ff);
}

.tab-panel {
  padding: 10px 2px 6px;
}

.channel-editor-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(320px, 0.95fr);
  gap: 18px;
  margin-top: 18px;
}

.editor-pane {
  min-width: 0;
  padding: 18px;
  border-radius: 22px;
  background: #f9fbff;
  border: 1px solid #e6ecfa;
}

.guide-pane {
  background:
    linear-gradient(180deg, rgba(244, 247, 255, 0.96), rgba(238, 243, 255, 0.98)),
    #eef3ff;
}

.channel-form {
  margin-top: 0;
}

.channel-toggle {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 6px;
  color: #44506a;
}

.channel-actions,
.inline-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.channel-actions {
  margin-top: 18px;
}

.command-panel {
  margin-top: 18px;
  padding: 18px;
  border-radius: 20px;
  background: linear-gradient(180deg, #f7f9ff, #edf2ff);
}

.command-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.command-title {
  margin: 8px 0 0;
  font-size: 18px;
  color: #24304a;
}

.command-meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 16px;
}

.meta-line {
  padding: 12px 14px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.72);
}

.meta-line-key {
  display: block;
  font-size: 12px;
  color: #6f7b95;
}

.meta-line-value {
  display: block;
  margin-top: 6px;
  color: #23304d;
  font-weight: 600;
  word-break: break-all;
}

.command-textarea {
  margin-top: 16px;
}

.command-hint {
  margin-top: 12px;
  color: #68758e;
  line-height: 1.7;
}

.advanced-panel {
  margin-top: 18px;
}

.advanced-form {
  margin-top: 0;
}

.observe-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(320px, 0.85fr);
  gap: 18px;
  align-items: start;
}

.observe-main,
.observe-side {
  min-width: 0;
}

.inventory-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
  margin-top: 16px;
}

.inventory-block {
  padding: 18px;
  border-radius: 18px;
  background: #f8faff;
}

.inventory-title {
  margin-bottom: 12px;
  font-size: 15px;
  font-weight: 600;
  color: #24304a;
}

.inventory-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.inventory-chip {
  padding: 8px 12px;
  border-radius: 999px;
  background: #e9efff;
  color: #3656a3;
}

.inventory-tags {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}

.bridge-strip {
  margin-top: 18px;
}

.bridge-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 14px;
}

.bridge-card {
  padding: 16px;
  border-radius: 18px;
  background: #f6f9ff;
  border: 1px solid #dde6fb;
}

.bridge-card-head {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: center;
  margin-bottom: 10px;
}

.bridge-name {
  font-weight: 600;
  color: #24304a;
}

.bridge-meta-line {
  font-size: 13px;
  color: #6e7891;
  line-height: 1.6;
  word-break: break-word;
}

.interrupt-shell {
  margin-top: 18px;
}

.interrupt-summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.interrupt-summary-card {
  padding: 16px;
  border-radius: 18px;
  background: #f6f9ff;
  border: 1px solid #dde6fb;
}

.interrupt-summary-label {
  font-size: 12px;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: #7a86a0;
}

.interrupt-summary-value {
  margin-top: 8px;
  font-size: 16px;
  font-weight: 600;
  color: #24304a;
  word-break: break-word;
}

.interrupt-control-block {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
  margin-top: 18px;
  padding: 16px 18px;
  border-radius: 18px;
  background: #f8faff;
}

.interrupt-manual-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.interrupt-device-input {
  width: 320px;
}

.compact-header {
  margin-bottom: 12px;
}

.connection-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 14px;
}

.connection-card {
  padding: 16px;
  border-radius: 18px;
  background: #f6f9ff;
  border: 1px solid #dde6fb;
}

.connection-card-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 12px;
}

.connection-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 14px;
}

.debug-entry-card {
  padding: 20px;
  border-radius: 22px;
  border: 1px solid #e4eaf8;
  background:
    radial-gradient(circle at top right, rgba(87, 120, 255, 0.1), transparent 30%),
    linear-gradient(180deg, #ffffff, #f8fbff);
}

.debug-open-btn {
  border-radius: 999px;
}

.debug-entry-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  margin-top: 18px;
}

.debug-entry-stat {
  padding: 16px 18px;
  border-radius: 18px;
  background: #f7f9fd;
  border: 1px solid #e3e9f6;
}

.debug-entry-label {
  display: block;
  font-size: 12px;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: #7a86a0;
}

.debug-entry-value {
  display: block;
  margin-top: 8px;
  font-size: 18px;
  font-weight: 700;
  color: #24304a;
  word-break: break-word;
}

.runtime-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 18px;
}

.runtime-item {
  display: flex;
  gap: 14px;
  padding: 12px 14px;
  border-radius: 16px;
  background: #f8faff;
}

.runtime-path {
  min-width: 120px;
  font-weight: 600;
  color: #24304a;
}

.runtime-note {
  color: #68758e;
  word-break: break-word;
}

.top-alert {
  margin-top: 16px;
}

@media (max-width: 1360px) {
  .overview-strip {
    flex-direction: column;
  }

  .overview-stats {
    min-width: 0;
  }

  .workspace-shell {
    grid-template-columns: 1fr;
  }

  .workspace-sidebar {
    position: static;
  }
}

@media (max-width: 1200px) {
  .operation-bar {
    flex-direction: column;
  }

  .page-actions {
    justify-content: flex-start;
  }

  .overview-stats,
  .channel-editor-grid,
  .observe-grid,
  .inventory-grid,
  .interrupt-summary-grid,
  .command-meta,
  .debug-entry-grid {
    grid-template-columns: 1fr;
  }

  .context-grid {
    grid-template-columns: 1fr 1fr;
  }

  .workspace-card {
    min-height: auto;
  }

  .interrupt-control-block {
    flex-direction: column;
    align-items: stretch;
  }
}

@media (max-width: 768px) {
  .main-wrapper {
    padding: 0 16px 20px;
  }

  .operation-bar {
    padding: 18px 16px 12px;
  }

  .page-title {
    font-size: 24px;
  }

  .overview-strip,
  .editor-pane,
  .debug-entry-card {
    padding: 16px;
  }

  .context-grid,
  .inventory-grid,
  .debug-entry-grid {
    grid-template-columns: 1fr;
  }

  .interrupt-device-input {
    width: 100%;
  }

  .runtime-item {
    flex-direction: column;
  }
}
</style>

<template>
  <el-dialog
    :visible.sync="dialogVisible"
    :title="isEditMode ? '编辑 Channel' : '新建 Channel'"
    width="620px"
    top="6vh"
    append-to-body
    :custom-class="`openclaw-channel-editor ${showAdvanced ? 'is-advanced-open' : 'is-advanced-closed'}`"
    @close="handleClose"
  >
    <div class="editor-shell">
      <el-form label-position="top" class="editor-form">
        <el-form-item label="Channel 名称">
          <el-input
            v-model.trim="localForm.name"
            maxlength="64"
            placeholder="例如：福丰播报渠道"
            @keyup.enter.native="submit"
          />
        </el-form-item>

        <el-form-item label="备注">
          <el-input
            v-model.trim="localForm.remark"
            type="textarea"
            :rows="2"
            resize="none"
            maxlength="200"
            placeholder="可选，用于说明用途或环境"
          />
        </el-form-item>

        <div class="editor-switch-row">
          <div>
            <div class="switch-title">启用该 Channel</div>
            <div class="switch-hint">关闭后仅保留配置。</div>
          </div>
          <el-switch v-model="localForm.enabled" />
        </div>

        <div v-if="!isEditMode" class="create-hint">
          创建后会自动生成当前服务的本地接入命令。先完成接入，再检测连接并加载这个 Channel 的 Agent。
        </div>

        <template v-else>
          <button type="button" class="editor-toggle" @click="showAdvanced = !showAdvanced">
            <span>高级配置</span>
            <i :class="showAdvanced ? 'el-icon-arrow-up' : 'el-icon-arrow-down'" />
          </button>

          <el-collapse-transition>
            <div v-if="showAdvanced" class="advanced-grid">
              <el-form-item label="管理接口基础地址">
                <el-input v-model.trim="localForm.baseUrl" placeholder="留空时恢复为当前服务的本地 OpenClaw 接入地址" />
              </el-form-item>
              <el-form-item label="Inventory 路径">
                <el-input v-model.trim="localForm.inventoryPath" placeholder="/inventory" />
              </el-form-item>
              <el-form-item label="Access Token">
                <el-input v-model.trim="localForm.accessToken" show-password placeholder="留空时使用本地默认鉴权；自定义远端地址时请按需填写" />
              </el-form-item>
            </div>
          </el-collapse-transition>
        </template>
      </el-form>
    </div>

    <span slot="footer" class="dialog-footer">
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">
        {{ isEditMode ? "保存修改" : "创建并进入接入" }}
      </el-button>
    </span>
  </el-dialog>
</template>

<script>
const createEmptyChannel = () => ({
  id: "",
  name: "",
  baseUrl: "",
  inventoryPath: "/inventory",
  accessToken: "",
  enabled: true,
  remark: "",
});

export default {
  name: "OpenClawChannelEditorDialog",
  props: {
    visible: {
      type: Boolean,
      default: false,
    },
    channel: {
      type: Object,
      default: () => createEmptyChannel(),
    },
    saving: {
      type: Boolean,
      default: false,
    },
    isEditMode: {
      type: Boolean,
      default: false,
    },
  },
  data() {
    return {
      dialogVisible: false,
      localForm: createEmptyChannel(),
      showAdvanced: false,
    };
  },
  watch: {
    visible: {
      immediate: true,
      handler(value) {
        this.dialogVisible = value;
        if (value) {
          this.syncLocalForm();
        }
      },
    },
    channel: {
      deep: true,
      handler() {
        if (this.dialogVisible) {
          this.syncLocalForm();
        }
      },
    },
    dialogVisible(value) {
      if (!value) {
        this.$emit("update:visible", false);
      }
    },
  },
  methods: {
    syncLocalForm() {
      const source = this.channel || {};
      this.localForm = {
        id: source.id || "",
        name: source.name || "",
        baseUrl: source.baseUrl || "",
        inventoryPath: source.inventoryPath || "/inventory",
        accessToken: source.accessToken || "",
        enabled: source.enabled !== false,
        remark: source.remark || "",
      };
      this.showAdvanced = this.isEditMode && Boolean(
        this.localForm.baseUrl ||
        this.localForm.accessToken ||
        (this.localForm.inventoryPath && this.localForm.inventoryPath !== "/inventory")
      );
    },
    handleClose() {
      this.dialogVisible = false;
    },
    submit() {
      if (!this.localForm.name) {
        this.$message.warning("请先填写 Channel 名称");
        return;
      }
      this.$emit("save", { ...this.localForm });
    },
  },
};
</script>

<style scoped>
.editor-shell {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.editor-form {
  padding: 0 2px 4px;
}

.editor-switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  border-radius: 20px;
  background: linear-gradient(180deg, #f7f9ff, #f2f5fc);
  border: 1px solid #e5ebf7;
  margin-bottom: 10px;
}

.switch-title {
  color: #1f2940;
  font-weight: 700;
}

.switch-hint {
  margin-top: 4px;
  color: #70809b;
  font-size: 12px;
  line-height: 1.5;
}

.editor-toggle {
  width: 100%;
  border: none;
  background: linear-gradient(180deg, #eef3ff, #e8eefc);
  border-radius: 18px;
  padding: 12px 16px;
  color: #294177;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
}

.create-hint {
  margin-top: 4px;
  padding: 14px 16px;
  border-radius: 18px;
  background: linear-gradient(180deg, #eef4ff, #f7faff);
  border: 1px solid #dbe6fb;
  color: #55657f;
  line-height: 1.7;
}

.advanced-grid {
  margin-top: 10px;
  padding: 14px;
  border-radius: 20px;
  background: linear-gradient(180deg, #fcfdff, #f7f9fe);
  border: 1px solid #e6ebf4;
}

::v-deep .openclaw-channel-editor {
  margin: 0 auto !important;
  border-radius: 32px;
  overflow: hidden;
  box-shadow: 0 28px 60px rgba(32, 44, 70, 0.24);
}

::v-deep .openclaw-channel-editor .el-dialog__header {
  padding: 24px 28px 0;
  text-align: center;
}

::v-deep .openclaw-channel-editor .el-dialog__title {
  color: #22304f;
  font-size: 16px;
  font-weight: 700;
}

::v-deep .openclaw-channel-editor .el-dialog__body {
  padding: 14px 24px 18px;
}

::v-deep .openclaw-channel-editor .el-dialog__footer {
  padding: 0 24px 20px;
}

::v-deep .openclaw-channel-editor.is-advanced-closed .el-dialog__body {
  padding-bottom: 10px;
}

::v-deep .openclaw-channel-editor.is-advanced-closed .el-dialog__footer {
  padding-top: 0;
  padding-bottom: 16px;
}

::v-deep .openclaw-channel-editor .el-form-item__label {
  padding-bottom: 6px;
  color: #2b3957;
  font-weight: 600;
}

::v-deep .openclaw-channel-editor .el-form-item {
  margin-bottom: 14px;
}

::v-deep .openclaw-channel-editor.is-advanced-closed .el-form-item:last-of-type {
  margin-bottom: 10px;
}

::v-deep .openclaw-channel-editor .el-input__inner,
::v-deep .openclaw-channel-editor .el-textarea__inner {
  border-radius: 16px;
  border-color: #dde5f2;
  background: #fbfcff;
}

::v-deep .openclaw-channel-editor .el-input__inner {
  height: 38px;
}

::v-deep .openclaw-channel-editor .el-textarea__inner {
  min-height: 68px !important;
}

::v-deep .openclaw-channel-editor .el-button {
  border-radius: 14px;
  min-width: 96px;
}

@media (max-width: 640px) {
  ::v-deep .openclaw-channel-editor {
    width: calc(100vw - 24px) !important;
  }

  ::v-deep .openclaw-channel-editor .el-dialog__body,
  ::v-deep .openclaw-channel-editor .el-dialog__footer,
  ::v-deep .openclaw-channel-editor .el-dialog__header {
    padding-left: 18px;
    padding-right: 18px;
  }

  .editor-switch-row {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>

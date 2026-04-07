<template>
  <el-dialog :visible="visible" @close="handleClose"  width="25%" center @open="handleOpen">
    <div
      style="margin: 0 10px 10px;display: flex;align-items: center;gap: 10px;font-weight: 700;font-size: 20px;text-align: left;color: #3d4566;">
      <div
        style="width: 40px;height: 40px;border-radius: 50%;background: #5778ff;display: flex;align-items: center;justify-content: center;">
        <img loading="lazy" src="@/assets/home/equipment.png" alt="" style="width: 18px;height: 15px;" />
      </div>
      {{ $t('addAgentDialog.title') }}
    </div>
    <div style="height: 1px;background: #e8f0ff;" />
    <div style="margin: 22px 15px;">
      <div style="font-weight: 400;text-align: left;color: #3d4566;">
        <div style="color: red;display: inline-block;">*</div> {{ $t('addAgentDialog.agentName') }}：
      </div>
      <div class="input-46" style="margin-top: 12px;">
        <el-input maxLength="64" ref="inputRef" :placeholder="$t('addAgentDialog.placeholder')" v-model="wisdomBodyName" @keyup.enter.native="confirm" />
      </div>
      <div style="font-weight: 400;text-align: left;color: #3d4566;margin-top: 18px;">
        <div style="color: red;display: inline-block;">*</div> 智能体类型：
      </div>
      <el-radio-group v-model="agentType" style="margin-top: 12px;display:flex;gap:12px;">
        <el-radio-button label="native">原生</el-radio-button>
        <el-radio-button label="openclaw">OpenClaw</el-radio-button>
      </el-radio-group>
      <div style="margin-top: 10px;font-size: 12px;color: #818cae;text-align: left;">
        {{ agentType === 'openclaw' ? '创建后将使用 OpenClaw 类型配置流，提示词改为由 OpenClaw 侧管理。' : '原生类型继续使用当前本地提示词与函数配置流。' }}
      </div>
    </div>
    <div style="display: flex;margin: 15px 15px;gap: 7px;">
      <div class="dialog-btn" @click="confirm">
        {{ $t('addAgentDialog.confirm') }}
      </div>
      <div class="dialog-btn" style="background: #e6ebff;border: 1px solid #adbdff;color: #5778ff;" @click="cancel">
        {{ $t('addAgentDialog.cancel') }}
      </div>
    </div>
  </el-dialog>
</template>

<script>
import Api from '@/apis/api';

export default {
  name: 'AddWisdomBodyDialog',
  props: {
    visible: { type: Boolean, required: true }
  },
  data() {
    return {
      wisdomBodyName: "",
      inputRef: null,
      agentType: "native"
    }
  },
  methods: {
    handleOpen() {
      this.$nextTick(() => {
        this.$refs.inputRef.focus();
      });
    },
    confirm() {
      if (!this.wisdomBodyName.trim()) {
        this.$message.error(this.$t('addAgentDialog.nameRequired'));
        return;
      }
      Api.agent.addAgent(this.wisdomBodyName, this.agentType, (res) => {
        this.$message.success({
          message: this.$t('addAgentDialog.addSuccess'),
          showClose: true
        });
        this.$emit('confirm', res);
        this.$emit('update:visible', false);
        this.wisdomBodyName = "";
        this.agentType = "native";
      });
    },
    cancel() {
      this.$emit('update:visible', false)
      this.wisdomBodyName = ""
      this.agentType = "native";
    },
    handleClose() {
      this.$emit('update:visible', false);
      this.agentType = "native";
    },
  }
}
</script>

<style scoped>
.input-46 {
  border: 1px solid #e4e6ef;
  background: #f6f8fb;
  border-radius: 15px;
}

.dialog-btn {
  cursor: pointer;
  flex: 1;
  border-radius: 23px;
  background: #5778ff;
  height: 40px;
  font-weight: 500;
  font-size: 12px;
  color: #fff;
  line-height: 40px;
  text-align: center;
}

::v-deep .el-dialog {
  border-radius: 15px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

::v-deep .el-dialog__headerbtn {
  display: none;
}

::v-deep .el-dialog__body {
  padding: 4px 6px;
}

::v-deep .el-dialog__header {
  padding: 10px;
}
</style>

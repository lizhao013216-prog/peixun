<script setup lang="ts">
import { ref, computed } from "vue";
import { store, command, newWorkspace, login, notify } from "../store";
import Icon from "../components/Icon.vue";
import Modal from "../components/Modal.vue";
const s = computed(() => store.data),
  name = ref("独立业务演示"),
  clone = ref(false),
  reset = ref(false),
  confirmText = ref(""),
  profile = ref(s.value.settings.platformProfile);
const capabilities = [
  ["模型与资产", "FBX / OBJ / STEP / 三级LOD", "转换与几何处理模拟"],
  ["编辑与行为", "关键帧 / 脚本 / 模板 / UI", "配置、版本与业务规则真实"],
  ["物理与交互", "刚体 / 六自由度 / 碰撞", "物理反馈与XR输入模拟"],
  ["环境与光照", "天气 / 昼夜 / 环境参数", "示意状态反馈"],
  ["台位与协同", "TCP / VRPN / 双身份会话", "外设模拟，业务成员与权限真实"],
  ["发布与报告", "浏览器运行 / 目标包清单", "原生包模拟，MD报告真实导出"],
];
async function administrator() {
  await login("ADMIN");
  notify("已切换演示管理员");
}
async function doReset() {
  const r = await command(
    "workspace.reset",
    {},
    "当前工作区已恢复种子，新运行轮次已生效",
  );
  if (r) {
    reset.value = false;
    confirmText.value = "";
  }
}
</script>
<template>
  <div class="toolbar">
    <span class="pill">{{ s.seedVersion }}</span
    ><span class="pill">第 {{ s.epoch }} 轮演示</span
    ><span class="spacer"></span
    ><button class="btn secondary" @click="administrator">
      切换演示管理员
    </button>
  </div>
  <div class="grid-2">
    <section class="panel">
      <div class="panel-header"><h3>独立工作区</h3></div>
      <div class="panel-body form-stack">
        <label class="field"
          ><span>新工作区名称</span><input v-model="name" /></label
        ><label class="checkbox-row"
          ><input
            v-model="clone"
            type="checkbox"
          />克隆当前工作区的业务对象与状态</label
        ><button
          class="btn primary"
          :disabled="!name.trim()"
          @click="newWorkspace(name, clone)"
        >
          <Icon name="FolderPlus" :size="15" />创建工作区
        </button>
        <p class="note-caption">
          不同工作区分别持久保存。新建使用初始样例；克隆用于独立对比方案。已确认的业务成绩不会混入其他工作区。
        </p>
        <hr class="line-divider" />
        <div class="kv-list">
          <div>
            <span>当前工作区</span><b>{{ s.name }}</b>
          </div>
          <div>
            <span>标识</span><b class="mono">{{ s.id }}</b>
          </div>
          <div>
            <span>数据修订</span><b>{{ s.revision }}</b>
          </div>
          <div><span>保存方式</span><b>服务端数据库持久保存</b></div>
        </div>
      </div>
    </section>
    <section class="panel">
      <div class="panel-header"><h3>平台模拟配置</h3></div>
      <div class="panel-body form-stack">
        <label class="field"
          ><span>虚拟平台响应</span
          ><select v-model="profile">
            <option value="SUCCESS">正常响应</option>
            <option value="FAIL_ONCE">下一次失败，后续恢复</option>
            <option value="ALWAYS_FAIL">持续失败</option>
            <option value="TIMEOUT">模拟超时</option>
            <option value="DUPLICATE_CALLBACK">重复观察事件</option>
            <option value="OUT_OF_ORDER">乱序观察事件</option>
          </select></label
        ><button
          class="btn primary"
          @click="
            command(
              'settings.save',
              { platformProfile: profile },
              '平台模拟配置已保存',
            )
          "
        >
          应用模拟配置
        </button>
        <div class="info-note">
          <Icon
            name="Info"
            :size="16"
          />平台技术故障不扣学员分。发布失败保留日志，恢复后可重新构建；重复事件不得重复授分。
        </div>
        <p class="note-caption">
          模拟参数仅在此管理入口配置，普通业务操作不直接指定平台成功结果。
        </p>
      </div>
    </section>
  </div>
  <section class="panel">
    <div class="panel-header">
      <div>
        <h3>假设虚拟平台能力</h3>
        <p>仅说明本Demo的能力边界，未声明真实硬件或图形性能已通过验收</p>
      </div>
    </div>
    <div class="table-scroll">
      <table class="data-table">
        <thead>
          <tr>
            <th>能力域</th>
            <th>接口与功能范围</th>
            <th>本次实现</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="c in capabilities" :key="c[0]">
            <td>
              <b>{{ c[0] }}</b>
            </td>
            <td>{{ c[1] }}</td>
            <td>{{ c[2] }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
  <section class="panel danger-zone">
    <div class="panel-header">
      <div>
        <h3>重新演示当前工作区</h3>
        <p>清空当前轮次的课程、预案、成绩与运行记录，恢复初始样例</p>
      </div>
      <button class="btn danger" @click="reset = true">
        <Icon name="RotateCcw" :size="15" />重置工作区
      </button>
    </div>
    <div class="panel-body">
      <p class="note-caption">
        重置会递增运行轮次，旧请求将被拒绝。其他工作区以及已经下载的报告保持原样。需要保留当前成果时，请先克隆工作区。
      </p>
    </div>
  </section>
  <Modal v-if="reset" title="确认重置当前工作区" @close="reset = false"
    ><div class="info-note warning">
      <Icon name="AlertTriangle" :size="18" />将重置“{{
        s.name
      }}”中的本轮业务数据。此操作不会影响其他工作区。
    </div>
    <label class="field"
      ><span>输入工作区名称以确认</span
      ><input v-model="confirmText" :placeholder="s.name" /></label
    ><template #footer
      ><button class="btn secondary" @click="reset = false">取消</button
      ><button
        class="btn danger"
        :disabled="confirmText !== s.name"
        @click="doReset"
      >
        确认重置
      </button></template
    ></Modal
  >
</template>

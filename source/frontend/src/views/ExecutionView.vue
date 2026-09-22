<script setup lang="ts">
import { computed, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  store,
  command,
  selected,
  download,
  login,
  notify,
  fmt,
} from "../store";
import { pathFor } from "../catalog";
import Icon from "../components/Icon.vue";
import Badge from "../components/Badge.vue";
import Modal from "../components/Modal.vue";
import Empty from "../components/Empty.vue";
import Gantt from "../components/Gantt.vue";
const route = useRoute(),
  router = useRouter(),
  s = computed(() => store.data);
const execution = computed(() =>
  selected(
    s.value.executions,
    store.selectedExecution || String(route.params.id),
  ),
);
const showCreate = ref(false),
  planId = ref(""),
  fix = ref(false),
  comment = ref("已调整示例装配状态，申请复查。");
const plans = computed(() =>
  s.value.plans.filter((p: any) =>
    ["APPROVED", "PUBLISHED"].includes(p.status),
  ),
);
const phases = ["下发签收", "开工准备", "施工报工", "检查整改", "验收交接"];
const phase = computed(() =>
  !execution.value
    ? 0
    : {
        ISSUED: 0,
        ACKNOWLEDGED: 1,
        READY: 2,
        PENDING_INSPECTION: 3,
        RECTIFICATION_REQUIRED: 3,
        PENDING_RECHECK: 3,
        READY_HANDOVER: 4,
        ACCEPTED: 5,
      }[execution.value.status] || 0,
);
const latestCheck = (item: number) =>
  execution.value?.inspections.filter((i: any) => i.item === item).at(-1);
async function create() {
  const r = await command(
    "execution.create",
    { planId: planId.value },
    "模拟执行单已下发",
  );
  if (r) {
    store.selectedExecution = r.id;
    showCreate.value = false;
  }
}
async function asRole(role: string) {
  await login(role);
  notify("已切换演示身份");
}
async function submitFix() {
  const r = await command(
    "rectification.submit",
    { id: execution.value.id, comment: comment.value },
    "整改工时及说明已保存",
  );
  if (r) fix.value = false;
}
</script>
<template>
  <div class="toolbar">
    <select
      v-if="s.executions.length"
      v-model="store.selectedExecution"
      aria-label="选择执行单"
    >
      <option value="">最近执行单</option>
      <option v-for="e in s.executions" :key="e.id" :value="e.id">
        {{ e.name }} · {{ e.id }}
      </option></select
    ><Badge v-if="execution" :status="execution.status" /><span
      class="spacer"
    ></span
    ><button class="btn secondary" @click="asRole('WORKER')">施工员</button
    ><button class="btn secondary" @click="asRole('INSPECTOR')">验收员</button
    ><button
      class="btn primary"
      @click="
        planId =
          plans.find((p: any) => p.id === store.selectedPlan)?.id ||
          plans.at(-1)?.id ||
          '';
        showCreate = true;
      "
    >
      <Icon name="Send" :size="15" />下发模拟施工
    </button>
  </div>
  <section v-if="!execution" class="panel">
    <Empty
      title="从批准的预案进入模拟施工"
      description="签收、准备、报工、检查与整改分别记录。模拟实绩始终与预案预计值独立保存。"
      icon="HardHat"
      ><button
        class="btn primary"
        @click="
          planId = plans.at(-1)?.id || '';
          showCreate = true;
        "
      >
        创建执行单
      </button></Empty
    >
  </section>
  <template v-else
    ><section class="panel workflow-panel">
      <div class="panel-header">
        <div>
          <h3>{{ execution.name }}</h3>
          <p>
            {{ execution.id }} · 来源 {{ execution.planVersion }} · 模拟施工数据
          </p>
        </div>
        <span class="clock-display"
          ><small>T+</small><b>{{ execution.clock }}</b
          ><small>min</small></span
        >
      </div>
      <div class="workflow-track">
        <button
          v-for="(name, i) in phases"
          :key="name"
          :class="{ completed: i < phase }"
          style="cursor: default"
        >
          <div class="workflow-icon">
            <Icon
              :name="
                i < phase
                  ? 'Check'
                  : [
                      'Send',
                      'ListChecks',
                      'Hammer',
                      'ClipboardCheck',
                      'FolderCheck',
                    ][i]
              "
              :size="21"
            />
          </div>
          <div>
            <b>{{ name }}</b
            ><span>{{
              i < phase ? "已完成" : i === phase ? "当前阶段" : "等待前置"
            }}</span>
          </div>
        </button>
      </div>
    </section>
    <div class="info-note warning">
      <Icon name="Info" :size="17" /><span v-if="execution.status === 'ISSUED'"
        >执行单已下发，等待施工员签收。</span
      ><span v-else-if="execution.status === 'ACKNOWLEDGED'"
        >签收完成。请确认本次培训证据和人员可用性，再进入开工准备。</span
      ><span v-else-if="execution.status === 'READY'"
        >开工条件已具备。可提交第一批模拟实绩，到首次检查结束；后续仍需检查与验收。</span
      ><span v-else-if="execution.status === 'PENDING_INSPECTION'"
        >首次报工已保存，请验收员逐项提交四项检查结论。</span
      ><span v-else-if="execution.status === 'RECTIFICATION_REQUIRED'"
        >有检查项未通过，请施工员提交整改说明和工时。</span
      ><span v-else-if="execution.status === 'PENDING_RECHECK'"
        >整改已提交，请验收员复查；原失败记录保留。</span
      ><span v-else-if="execution.status === 'READY_HANDOVER'"
        >检查及整改已闭环，可提交交接工序。</span
      ><span v-else>模拟施工已验收完成，可以对比实绩并提出改进。</span>
    </div>
    <div class="toolbar">
      <button
        v-if="execution.status === 'ISSUED'"
        class="btn primary"
        @click="command('execution.ack', { id: execution.id }, '执行单已签收')"
      >
        签收执行单</button
      ><button
        v-if="execution.status === 'ACKNOWLEDGED'"
        class="btn primary"
        @click="
          command('execution.prepare', { id: execution.id }, '开工准备已确认')
        "
      >
        确认开工准备</button
      ><button
        v-if="execution.status === 'ACKNOWLEDGED'"
        class="btn secondary"
        @click="router.push(pathFor('S204'))"
      >
        检查培训证据</button
      ><button
        v-if="execution.status === 'READY'"
        class="btn primary"
        @click="
          command(
            'execution.report',
            { id: execution.id },
            '第一批模拟实绩已保存，等待检查',
          )
        "
      >
        <Icon name="Upload" :size="15" />提交首批模拟实绩</button
      ><button
        v-if="execution.status === 'RECTIFICATION_REQUIRED'"
        class="btn primary"
        @click="fix = true"
      >
        提交整改与工时</button
      ><button
        v-if="execution.status === 'READY_HANDOVER'"
        class="btn primary"
        @click="
          command(
            'execution.finish',
            { id: execution.id },
            '交接完成，实绩已验收',
          )
        "
      >
        完成验收交接</button
      ><template v-if="execution.status === 'ACCEPTED'"
        ><button
          class="btn primary"
          @click="router.push(pathFor('S315', execution.id))"
        >
          对比预案与实绩<Icon name="ArrowRight" :size="15" /></button
        ><button
          class="btn secondary"
          @click="download('executions', execution.id)"
        >
          导出MD报告
        </button></template
      >
    </div>
    <div v-if="execution.summary" class="metric-strip">
      <div class="metric">
        <span>当前模拟实绩</span><b>{{ execution.clock }}<small>min</small></b>
        <p>
          {{
            execution.status === "ACCEPTED" ? "已完成交接" : "当前已记录时刻"
          }}
        </p>
      </div>
      <div class="metric">
        <span>累计费用</span
        ><b>{{ fmt(execution.summary.cost) }}<small>示例单位</small></b>
        <p>人工 {{ execution.summary.labor }} + 物料与运输</p>
      </div>
      <div class="metric">
        <span>首次合格率</span
        ><b>{{ execution.summary.firstPassRate }}<small>%</small></b>
        <p>分母固定为4个检查项</p>
      </div>
      <div class="metric">
        <span>最终合格率</span
        ><b>{{ execution.summary.finalPassRate }}<small>%</small></b>
        <p>{{ execution.summary.inspectionRecords }} 条检查 / 复查记录</p>
      </div>
    </div>
    <div v-if="phase >= 3" class="grid-2">
      <section class="panel">
        <div class="panel-header">
          <h3>质量检查</h3>
          <span class="subtle-label">不同检查项分别判定</span>
        </div>
        <div
          v-for="(name, index) in [
            '结构完整性',
            '部件对应性',
            '装配状态',
            '检测记录',
          ]"
          :key="name"
          class="checklist-item"
        >
          <div
            class="check-icon"
            :class="{ done: latestCheck(index + 1)?.passed }"
          >
            <Icon
              :name="
                latestCheck(index + 1)
                  ? latestCheck(index + 1).passed
                    ? 'Check'
                    : 'X'
                  : 'ClipboardCheck'
              "
              :size="17"
            />
          </div>
          <div>
            <b>{{ name }}</b>
            <p>
              {{
                latestCheck(index + 1)
                  ? `第${latestCheck(index + 1).round}轮 · ${latestCheck(index + 1).passed ? "通过" : "未通过"}`
                  : "等待首次检查"
              }}
            </p>
          </div>
          <template
            v-if="
              !latestCheck(index + 1) &&
              execution.status === 'PENDING_INSPECTION'
            "
            ><button
              class="btn secondary small"
              @click="
                command(
                  'inspection.submit',
                  { id: execution.id, item: index + 1, passed: true },
                  '检查通过记录已保存',
                )
              "
            >
              通过</button
            ><button
              class="btn danger small"
              @click="
                command(
                  'inspection.submit',
                  { id: execution.id, item: index + 1, passed: false },
                  '不合格记录已保存并生成整改单',
                )
              "
            >
              不通过
            </button></template
          ><template
            v-if="
              execution.status === 'PENDING_RECHECK' &&
              execution.rectifications.some(
                (r: any) =>
                  r.item === index + 1 && r.status === 'FIX_SUBMITTED',
              )
            "
            ><button
              class="btn teal small"
              @click="
                command(
                  'inspection.recheck',
                  { id: execution.id, item: index + 1, passed: true },
                  '复查通过，失败历史保留',
                )
              "
            >
              复查通过</button
            ><button
              class="text-btn danger"
              @click="
                command(
                  'inspection.recheck',
                  { id: execution.id, item: index + 1, passed: false },
                  '复查未通过，需继续整改',
                )
              "
            >
              仍不通过
            </button></template
          >
        </div>
      </section>
      <section class="panel">
        <div class="panel-header"><h3>整改跟踪</h3></div>
        <div class="panel-body">
          <div
            v-for="r in execution.rectifications"
            :key="r.id"
            class="list-card"
          >
            <div class="resource-icon"><Icon name="Wrench" /></div>
            <div>
              <b>{{ r.description }}</b>
              <p>{{ r.comment || "待施工员填写整改说明" }}</p>
            </div>
            <Badge :status="r.status" />
          </div>
          <Empty
            v-if="!execution.rectifications.length"
            title="当前没有整改单"
            description="检查不合格时生成独立整改单，完成后仍需验收员复查。"
            icon="ClipboardCheck"
          />
        </div>
      </section>
    </div>
    <section v-if="execution.logs.length" class="panel gantt-panel">
      <div class="panel-header">
        <h3>模拟实绩工序记录</h3>
        <span class="subtle-label">返工与复查单独计费，父工序不重复累加</span>
      </div>
      <Gantt :spans="execution.logs" :clock="execution.clock" />
    </section>
    <section v-if="execution.ledger.length" class="panel">
      <div class="panel-header">
        <h3>施工物料账本</h3>
        <span class="subtle-label">独立于演练账本</span>
      </div>
      <div class="inventory-boxes">
        <div
          v-for="(label, key) in {
            remote: '远端现货',
            inTransit: '在途',
            local: '本地现货',
            consumed: '累计领用',
          }"
          :key="key"
        >
          <span>{{ label }}</span
          ><b>{{ execution.inventory[key] }}</b
          ><small>件</small>
        </div>
      </div>
    </section></template
  ><Modal
    v-if="showCreate"
    title="下发模拟施工执行单"
    @close="showCreate = false"
    ><label class="field"
      ><span>批准的预案版本</span
      ><select v-model="planId">
        <option value="" disabled>请选择预案</option>
        <option v-for="p in plans" :key="p.id" :value="p.id">
          {{ p.name }} · {{ p.version }} · 到货T+{{ p.eta }}
        </option>
      </select></label
    >
    <div class="info-note" style="margin-top: 18px">
      <Icon
        name="Info"
        :size="16"
      />本版固定实绩样例用于到货T+135的P1方案，包含一次不合格、整改与复查。开工前检查本次个人培训证据。
    </div>
    <template #footer
      ><button class="btn primary" :disabled="!planId" @click="create">
        下发执行单
      </button></template
    ></Modal
  ><Modal v-if="fix" title="提交整改记录" @close="fix = false"
    ><div class="form-stack">
      <label class="field"
        ><span>整改说明 *</span><textarea v-model="comment" />
      </label>
      <div class="kv-list">
        <div><span>维修人员</span><b>M1 / M2</b></div>
        <div><span>本次整改工时</span><b>10 分钟 / 人</b></div>
        <div><span>下一步</span><b>验收员复查</b></div>
      </div>
    </div>
    <template #footer
      ><button class="btn primary" @click="submitFix">
        提交整改并申请复查
      </button></template
    ></Modal
  >
</template>

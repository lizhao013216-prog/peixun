<script setup lang="ts">
import { computed, ref, onUnmounted, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  store,
  command,
  selected,
  download,
  fmt,
  login,
  notify,
} from "../store";
import { pathFor } from "../catalog";
import Icon from "../components/Icon.vue";
import Badge from "../components/Badge.vue";
import Modal from "../components/Modal.vue";
import Empty from "../components/Empty.vue";
import SceneView from "../components/SceneView.vue";
import Gantt from "../components/Gantt.vue";
defineProps<{ page: any }>();
const route = useRoute(),
  router = useRouter(),
  s = computed(() => store.data),
  run = computed(() =>
    selected(s.value.runs, store.selectedRun || String(route.params.id)),
  );
const plans = computed(() =>
  s.value.plans.filter((p: any) =>
    ["APPROVED", "PUBLISHED"].includes(p.status),
  ),
);
const showCreate = ref(false),
  showInject = ref(false),
  playing = ref(false),
  replaying = ref(false),
  replayTime = ref(0),
  form = ref({ name: "基线保障演练", planId: "", scenario: "BASE" }),
  injection = ref({ eta: 180, transportCost: 60 });
let timer: any;
const clock = computed(() =>
  replaying.value ? replayTime.value : run.value?.clock || 0,
);
const spans = computed(() => run.value?.schedule.spans || []);
const finished = computed(
  () => spans.value.filter((o: any) => o.end <= clock.value).length,
);
const current = computed(() =>
  spans.value.filter((o: any) => o.start <= clock.value && o.end > clock.value),
);
const events = computed(() =>
  [...(run.value?.events || [])]
    .filter((e: any) => e.minute <= clock.value)
    .reverse(),
);
const inventory = computed(() => {
  const r = run.value;
  if (!r) return { remote: 4, local: 0, inTransit: 0, consumed: 0 };
  const stock = (id: string) =>
    r.resourceSnapshot.find((x: any) => x.id === id)?.capacity || 0;
  const sum = (action: string) =>
    r.ledger
      .filter((x: any) => x.action === action && x.minute <= clock.value)
      .reduce((n: number, x: any) => n + x.quantity, 0);
  return {
    remote: stock("WH-REMOTE") - sum("DISPATCH"),
    local: stock("WH-LOCAL") + sum("RECEIVE") - sum("ISSUE"),
    inTransit: sum("DISPATCH") - sum("RECEIVE"),
    consumed: sum("ISSUE"),
  };
});
function openCreate(scenario = "BASE") {
  form.value = {
    name: scenario === "DELAY" ? "到货延迟情景演练" : "基线保障演练",
    planId:
      plans.value.find((p: any) => p.id === store.selectedPlan)?.id ||
      plans.value.at(-1)?.id ||
      "",
    scenario,
  };
  showCreate.value = true;
}
async function create() {
  const r = await command("run.create", form.value, "演练已初始化");
  if (r) {
    store.selectedRun = r.id;
    showCreate.value = false;
    replaying.value = false;
    router.push(pathFor("S311", r.id));
  }
}
function stop() {
  playing.value = false;
  clearInterval(timer);
}
async function play() {
  if (playing.value) {
    stop();
    await command("run.pause", { id: run.value.id }, "演练已暂停");
    return;
  }
  if (run.value.status === "COMPLETED") return;
  await command("run.start", { id: run.value.id }, "");
  playing.value = true;
  timer = setInterval(async () => {
    if (store.busy || !playing.value) return;
    if (run.value.status === "COMPLETED") {
      stop();
      return;
    }
    const r = await command("run.step", { id: run.value.id }, "");
    if (!r || r.status === "COMPLETED") stop();
  }, 950);
}
async function finish() {
  stop();
  await command(
    "run.finish",
    { id: run.value.id },
    "演练已完成，结果和过程记录已保存",
  );
}
async function branch() {
  stop();
  const r = await command(
    "run.branch",
    {
      parentId: run.value.id,
      planId: run.value.planId,
      name: "加急优化方案演练",
    },
    "已从T+60建立加急分支",
  );
  if (r) {
    store.selectedRun = r.id;
    replaying.value = false;
  }
}
async function inject() {
  const r = await command(
    "run.inject",
    { id: run.value.id, ...injection.value },
    "导调事件已采纳",
  );
  if (r) showInject.value = false;
}
async function director() {
  await login("DIRECTOR");
  notify("已切换为导调员");
}
watch(
  () => run.value?.id,
  () => {
    stop();
    replaying.value = false;
  },
);
onUnmounted(stop);
</script>
<template>
  <div class="toolbar">
    <select
      v-if="s.runs.length"
      v-model="store.selectedRun"
      aria-label="选择演练"
    >
      <option value="">最近一次演练</option>
      <option v-for="r in s.runs" :key="r.id" :value="r.id">
        {{ r.name }} · {{ r.id }}
      </option></select
    ><Badge v-if="run" :status="run.status" /><span class="spacer"></span
    ><button class="btn secondary" @click="director">切换导调员</button
    ><button class="btn secondary" @click="openCreate('DELAY')">
      创建延迟情景</button
    ><button class="btn primary" @click="openCreate()">
      <Icon name="Plus" :size="15" />新建演练
    </button>
  </div>
  <section v-if="!run" class="panel">
    <Empty
      title="让预案进入可验证的演练"
      description="选择已批准预案，初始化资源与事件；对比正常、延迟和加急条件下的方案效果。"
      icon="Radar"
      ><button v-if="plans.length" class="btn primary" @click="openCreate()">
        初始化演练</button
      ><button v-else class="btn primary" @click="router.push(pathFor('S305'))">
        先编制并批准预案
      </button></Empty
    >
  </section>
  <template v-else
    ><div class="metric-strip">
      <div class="metric">
        <span>{{
          run.status === "COMPLETED" ? "最终工期" : "预计完成时间"
        }}</span
        ><b>{{ run.schedule.finish }}<small>min</small></b>
        <p>
          {{
            run.schedule.onTime
              ? "满足截止时间"
              : "超过期限 " + run.schedule.lateMinutes + " min"
          }}
        </p>
      </div>
      <div class="metric">
        <span>预计总费用</span
        ><b>{{ fmt(run.schedule.cost) }}<small>示例单位</small></b>
        <p>运输费用 {{ run.transportCost }}</p>
      </div>
      <div class="metric">
        <span>关键工序等待</span
        ><b>{{ run.schedule.waiting }}<small>min</small></b>
        <p>备件到货 T+{{ run.eta }} min</p>
      </div>
      <div class="metric">
        <span>已完成工序</span
        ><b
          >{{ finished }}<small>/ {{ spans.length }}</small></b
        >
        <p>{{ run.planVersion }} · 独立资源与账本</p>
      </div>
    </div>
    <section class="panel section-space">
      <div class="panel-header">
        <div>
          <h3>{{ run.name }}</h3>
          <p>
            {{ run.id }} · 预案 {{ run.planVersion
            }}{{ run.parentId ? " · 来自T+60优化分支" : "" }}
          </p>
        </div>
        <div class="row-flex">
          <div class="clock-display">
            <small>T+</small><b>{{ fmt(clock) }}</b
            ><small>min</small>
          </div>
          <span class="pill" v-if="replaying">只读复盘</span>
        </div>
      </div>
      <div class="panel-body">
        <div class="toolbar" style="margin-bottom: 0">
          <template v-if="run.status !== 'COMPLETED' && !replaying"
            ><button class="btn primary" @click="play">
              <Icon :name="playing ? 'Pause' : 'Play'" :size="15" />{{
                playing ? "暂停推演" : "自动推演"
              }}</button
            ><button
              class="btn secondary"
              :disabled="playing || store.busy > 0"
              @click="command('run.step', { id: run.id }, '')"
            >
              <Icon name="StepForward" :size="15" />下一事件</button
            ><button
              class="btn secondary"
              :disabled="store.busy > 0"
              @click="finish"
            >
              运行至结束</button
            ><button
              class="btn secondary"
              :disabled="run.clock >= run.eta"
              @click="
                stop();
                showInject = true;
              "
            >
              <Icon name="Zap" :size="15" />导调注入
            </button></template
          ><button v-if="run.clock >= 60" class="btn teal" @click="branch">
            <Icon name="GitBranch" :size="15" />从T+60采纳加急</button
          ><span class="spacer"></span
          ><button
            v-if="run.status === 'COMPLETED'"
            class="btn secondary"
            @click="
              replaying = !replaying;
              replayTime = 60;
            "
          >
            <Icon name="History" :size="15" />{{
              replaying ? "退出复盘" : "过程复盘"
            }}</button
          ><button
            v-if="run.status === 'COMPLETED'"
            class="btn secondary"
            @click="download('runs', run.id)"
          >
            <Icon name="Download" :size="15" />报告
          </button>
        </div>
        <div v-if="replaying" class="range-row" style="margin-top: 18px">
          <span class="subtle-label">T+0</span
          ><input
            v-model.number="replayTime"
            type="range"
            min="0"
            :max="run.schedule.finish"
            step="1"
            aria-label="复盘时间"
          /><span class="subtle-label">T+{{ run.schedule.finish }}</span>
        </div>
      </div>
    </section>
    <div class="detail-layout">
      <div>
        <SceneView
          :state="
            finished === spans.length
              ? 'COMPLETED'
              : clock > 0
                ? 'RUNNING'
                : 'READY'
          "
        />
        <section class="panel gantt-panel">
          <div class="panel-header">
            <h3>工序计划与执行进度</h3>
            <span class="subtle-label">{{
              current.length
                ? current.map((o: any) => o.name).join(" / ")
                : clock < run.schedule.finish
                  ? "等待资源或下一事件"
                  : "全部完成"
            }}</span>
          </div>
          <Gantt :spans="spans" :clock="clock" />
        </section>
      </div>
      <div class="detail-sidebar">
        <section class="panel">
          <div class="panel-header">
            <h3>事件主线</h3>
            <span class="subtle-label">{{ events.length }} 条</span>
          </div>
          <div class="event-feed">
            <div v-for="e in events" :key="e.id">
              <time>{{ e.minute }}</time
              ><b>{{ e.name }}</b
              ><span>{{ e.type }} · {{ e.id }}</span>
            </div>
            <p v-if="!events.length" class="empty-small">
              开始演练后显示事件。
            </p>
          </div>
        </section>
        <section class="panel">
          <div class="panel-header"><h3>调拨状态</h3></div>
          <div class="panel-body kv-list">
            <div><span>发运数量</span><b>2 件</b></div>
            <div>
              <span>到货时间</span><b>T+{{ run.eta }} min</b>
            </div>
            <div><span>单据分区</span><b>当前演练</b></div>
            <div>
              <span>人工费用</span><b>{{ run.schedule.labor }}</b>
            </div>
            <div>
              <span>物料费用</span><b>{{ run.schedule.materialCost }}</b>
            </div>
          </div>
        </section>
      </div>
    </div>
    <section class="panel">
      <div class="panel-header">
        <h3>物料账本</h3>
        <span class="subtle-label">只影响当前演练 · 随复盘时间同步</span>
      </div>
      <div class="inventory-boxes">
        <div
          v-for="(label, key) in {
            remote: '远端现货',
            inTransit: '运输在途',
            local: '本地现货',
            consumed: '累计领用',
          }"
          :key="key"
        >
          <span>{{ label }}</span
          ><b>{{ inventory[key] }}</b
          ><small>件</small>
        </div>
      </div>
      <div v-if="run.status === 'COMPLETED'" class="panel-body">
        <div class="button-row">
          <button
            class="btn secondary"
            @click="
              store.selectedPlan = run.planId;
              router.push(pathFor('S312', run.id));
            "
          >
            进入指标评估</button
          ><button
            class="btn primary"
            @click="
              store.selectedPlan = run.planId;
              router.push(pathFor('S306', run.planId));
            "
          >
            将优化形成预案版本<Icon name="ArrowRight" :size="15" />
          </button>
        </div>
      </div></section></template
  ><Modal
    v-if="showCreate"
    title="初始化保障行动演练"
    @close="showCreate = false"
    ><div class="form-stack">
      <label class="field"
        ><span>演练名称</span><input v-model="form.name" /></label
      ><label class="field"
        ><span>已批准预案</span
        ><select v-model="form.planId">
          <option value="" disabled>请选择已批准预案</option>
          <option v-for="p in plans" :key="p.id" :value="p.id">
            {{ p.name }} · {{ p.version }} · {{ p.schedule.finish }} min
          </option>
        </select></label
      ><label class="field"
        ><span>导调情景</span
        ><select v-model="form.scenario">
          <option value="BASE">按当前预案的到货时间执行</option>
          <option value="DELAY">T+60注入延迟，到货改为T+180</option>
        </select></label
      >
      <div class="info-note">
        <Icon
          name="Layers3"
          :size="17"
        />每次演练复制独立的预案输入、资源和账本，不修改其他演练或施工实绩。
      </div>
    </div>
    <template #footer
      ><button class="btn primary" :disabled="!form.planId" @click="create">
        创建演练
      </button></template
    ></Modal
  ><Modal
    v-if="showInject"
    title="导调事件：调整到货"
    @close="showInject = false"
    ><div class="form-stack">
      <div class="info-note">
        <Icon name="Clock3" :size="16" />当前仿真时间 T+{{ run.clock }}，原到货
        T+{{ run.eta }}。调整只作用于尚未到货的物料。
      </div>
      <label class="field"
        ><span>新的到货时间（分钟）</span
        ><input
          v-model.number="injection.eta"
          type="number"
          :min="run.clock + 1" /></label
      ><label class="field"
        ><span>调整后的运输费用</span
        ><input v-model.number="injection.transportCost" type="number" min="0"
      /></label>
    </div>
    <template #footer
      ><button class="btn primary" @click="inject">
        采纳导调事件
      </button></template
    ></Modal
  >
</template>

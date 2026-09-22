<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { store, command, selected, login, notify, fmt } from "../store";
import { pathFor } from "../catalog";
import Icon from "../components/Icon.vue";
import Badge from "../components/Badge.vue";
import Modal from "../components/Modal.vue";
import Empty from "../components/Empty.vue";
import Gantt from "../components/Gantt.vue";
const props = defineProps<{ page: any }>(),
  route = useRoute(),
  router = useRouter(),
  s = computed(() => store.data);
const query = ref(""),
  taskId = ref(String(route.params.id || "")),
  task = computed(() => selected(s.value.tasks, taskId.value));
const plan = computed(() =>
  selected(s.value.plans, store.selectedPlan || String(route.params.id)),
);
const showTask = ref(false),
  showPlan = ref(false),
  showResource = ref(false),
  showSub = ref(false),
  showAnalysis = ref(false),
  showDocument = ref(false),
  detail = ref<any>(null);
const taskForm = ref({
    name: "泵组维修保障任务",
    taskType: "维修",
    deadline: 270,
    importance: 80,
    description: "通用泵组状态检查与密封件更换",
  }),
  planForm = ref({
    name: "通用泵组维修保障预案",
    taskId: "",
    templateId: "TPL-PLAN-01",
  }),
  editPlan = ref<any>({
    eta: 90,
    transportCost: 60,
    operations: [],
    notes: "",
  }),
  resourceForm = ref<any>({}),
  resourceCategory = ref("全部资源"),
  cycle = ref(6),
  extraMonth = ref(0),
  subType = ref("施工总计划"),
  subForm = ref<any>({
    type: "施工总计划",
    content: "",
    owner: "本地维修单位",
    quantity: 2,
    supplier: "演示供应单位",
    amount: 100,
    criteria: "按配置的四项检查逐项验证",
  }),
  analysisForm = ref({
    type: "FMECA",
    tag: "密封件状态异常",
    conclusion: "对泵组密封组件进行检查，更换2件示例备件并进行检测确认。",
  }),
  docForm = ref({ name: "", type: "技术资料", content: "" }),
  repairCost = ref(800),
  replaceCost = ref(950),
  compatible = ref(true);
watch(
  () => plan.value?.id,
  () => {
    if (plan.value) editPlan.value = JSON.parse(JSON.stringify(plan.value));
  },
  { immediate: true },
);
const resources = computed(() =>
  s.value.resources.filter(
    (r: any) =>
      (resourceCategory.value === "全部资源" ||
        r.category === resourceCategory.value) &&
      `${r.name}${r.id}`.includes(query.value),
  ),
);
const matchingTemplates = computed(() =>
  s.value.templates.filter(
    (t: any) =>
      t.type === "PLAN" &&
      t.equipmentType === (task.value?.equipmentType || "PUMP") &&
      t.taskType === (task.value?.taskType || "维修"),
  ),
);
async function createTask() {
  const r = await command("task.create", taskForm.value, "保障任务已创建");
  if (r) {
    taskId.value = r.id;
    showTask.value = false;
  }
}
function openPlan(tpl = "TPL-PLAN-01") {
  planForm.value = {
    taskId: task.value?.id || "",
    name: "通用泵组维修保障预案",
    templateId: tpl,
  };
  showPlan.value = true;
}
async function createPlan() {
  const r = await command(
    "plan.create",
    planForm.value,
    "预案草稿已创建并完成初始排程",
  );
  if (r) {
    store.selectedPlan = r.id;
    showPlan.value = false;
    router.push(pathFor("S306", r.id));
  }
}
async function savePlan() {
  await command(
    "plan.save",
    {
      id: plan.value.id,
      eta: editPlan.value.eta,
      transportCost: editPlan.value.transportCost,
      operations: editPlan.value.operations,
      notes: editPlan.value.notes,
    },
    "预案已保存并重新排程",
  );
}
async function revise(version: string) {
  const r = await command(
    "plan.revise",
    { id: plan.value.id, version },
    `已创建${version}草稿，原版本保持不变`,
  );
  if (r) {
    store.selectedPlan = r.id;
    router.push(pathFor("S306", r.id));
  }
}
async function switchReviewer() {
  await login("REVIEWER_L1");
  notify("已切换项目审核员");
}
async function saveResource() {
  const r = await command(
    "resource.save",
    resourceForm.value,
    "资源配置已保存，已冻结预案保持原快照",
  );
  if (r) showResource.value = false;
}
function newSub() {
  subForm.value = {
    type: subType.value,
    content:
      subType.value === "施工总计划"
        ? "依据预案八工序组织施工，按前置依赖执行并记录资源占用。"
        : subType.value === "单装试验大纲"
          ? "逐项验证结构完整性、部件对应性、装配状态和检测记录；失败项必须整改复查。"
          : subType.value === "修理调试计划"
            ? "由M1/M2执行拆解、更换和装配，E1/Q1执行检测并保存记录。"
            : subType.value === "采购计划"
              ? "按需求与库存差额采购，审批后形成模拟采购单与到货待办。"
              : "按来源单据验收入库、预留、出库和领用，台账保留每次动作。",
    owner: "本地维修单位",
    quantity: 2,
    supplier: "演示供应单位",
    amount: 100,
    criteria: "四项检查均通过",
  };
  showSub.value = true;
}
async function createSub() {
  const f = subForm.value;
  const r = await command(
    "subplan.create",
    {
      planId: plan.value.id,
      ...f,
      content: `${f.content}\n责任单位：${f.owner}\n数量：${f.quantity}；单价：${f.amount}；合计：${f.quantity * f.amount}\n供应方：${f.supplier}\n检查要求：${f.criteria}`,
    },
    "专项计划草稿已创建",
  );
  if (r) showSub.value = false;
}
const prep = computed(() =>
  plan.value
    ? [
        {
          name: "人员准备",
          icon: "Users",
          note: plan.value.trainingConfirmed
            ? "M1培训证据已确认；技能记录独立保留"
            : "M1需要本次维修课程培训证据",
          ok: plan.value.trainingConfirmed,
          action: "training",
        },
        {
          name: "物料准备",
          icon: "Package",
          note: "本地0件 / 远端4件 / 需求2件；预计T+" + plan.value.eta + "到货",
          ok: s.value.orders.some(
            (o: any) =>
              o.planId === plan.value.id &&
              ["RECEIVED", "CLOSED"].includes(o.status),
          ),
          action: "material",
        },
        {
          name: "器材准备",
          icon: "Wrench",
          note: "TOOL-01 / TEST-01，按工序独占分配",
          ok: plan.value.resourceSnapshot
            .filter((r: any) => r.category === "器材")
            .every((r: any) => r.available),
        },
        {
          name: "设施准备",
          icon: "Warehouse",
          note: "维修场地、仓储、泊位及能力窗口已配置",
          ok: plan.value.resourceSnapshot
            .filter((r: any) => r.category === "设施")
            .every((r: any) => r.available),
        },
        {
          name: "信息准备",
          icon: "Files",
          note: `已关联 ${s.value.documents.length} 份构型与技术资料`,
          ok: s.value.documents.length > 0,
        },
        {
          name: "计划准备",
          icon: "ListChecks",
          note:
            plan.value.status === "DRAFT"
              ? "预案编制中，请完成排程校验和审批"
              : "预案已有版本记录，专项计划独立审批",
          ok: ["APPROVED", "PUBLISHED"].includes(plan.value.status),
        },
      ]
    : [],
);
</script>
<template>
  <div
    v-if="!['S301', 'S302', 'S303', 'S304', 'S305'].includes(page.id)"
    class="toolbar"
  >
    <select
      v-if="s.plans.length"
      v-model="store.selectedPlan"
      aria-label="当前预案"
    >
      <option value="">最近的预案</option>
      <option v-for="p in s.plans" :key="p.id" :value="p.id">
        {{ p.name }} · {{ p.version }} · {{ p.id }}
      </option></select
    ><Badge v-if="plan" :status="plan.status" /><span class="spacer"></span
    ><button class="btn secondary" @click="router.push(pathFor('S305'))">
      预案库
    </button>
  </div>
  <template v-if="page.id === 'S301'"
    ><div class="toolbar">
      <div class="filter-search">
        <Icon name="Search" :size="15" /><input
          v-model="query"
          placeholder="搜索任务…"
        />
      </div>
      <span class="spacer"></span
      ><button class="btn primary" @click="showTask = true">
        <Icon name="Plus" :size="16" />新建保障任务
      </button>
    </div>
    <section class="panel">
      <div class="table-scroll" v-if="s.tasks.length">
        <table class="data-table">
          <thead>
            <tr>
              <th>保障任务</th>
              <th>任务类型</th>
              <th>优先级</th>
              <th>截止时间</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="t in s.tasks.filter((t: any) => t.name.includes(query))"
              :key="t.id"
            >
              <td>
                <b>{{ t.name }}</b
                ><small>{{ t.id }}</small>
              </td>
              <td>{{ t.taskType }}</td>
              <td>
                <span class="pill">{{ t.importance }}</span>
              </td>
              <td>T+{{ t.deadline }} min</td>
              <td><Badge :status="t.status" /></td>
              <td>
                <div class="actions">
                  <button class="text-btn" @click="taskId = t.id">
                    查看剖面</button
                  ><button
                    class="text-btn"
                    @click="
                      taskId = t.id;
                      openPlan();
                    "
                  >
                    编制预案
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <Empty
        v-else
        title="从保障任务开始"
        description="明确对象、维修范围和时间约束，再建立工序与保障方案。"
        icon="ClipboardList"
        ><button class="btn primary" @click="showTask = true">
          创建首个保障任务
        </button></Empty
      >
    </section>
    <div v-if="task" class="grid-2" style="margin-top: 20px">
      <section class="panel">
        <div class="panel-header">
          <div>
            <h3>任务剖面 · {{ task.name }}</h3>
            <p>项目 → 任务包 → 叶子工序</p>
          </div>
        </div>
        <div class="panel-body">
          <div class="list-card">
            <div class="resource-icon"><Icon name="Ship" /></div>
            <div>
              <b>演示船A · 通用泵组维修保障</b>
              <p>{{ task.description }}</p>
            </div>
          </div>
          <div
            style="
              margin-left: 24px;
              border-left: 1px solid #e2ebf4;
              padding-left: 17px;
            "
          >
            <div class="section-label">泵组维修任务包</div>
            <div
              v-for="op in task.wbs[0].children"
              :key="op.id"
              class="row-flex"
              style="padding: 9px 0; font-size: 11px"
            >
              <Icon name="GitCommitHorizontal" :size="14" /><span
                class="mono muted"
                >{{ op.id }}</span
              ><span>{{ op.name }}</span
              ><span class="spacer"></span
              ><span class="muted">{{ op.duration }} min</span>
            </div>
          </div>
        </div>
      </section>
      <section class="panel">
        <div class="panel-header"><h3>任务资料与下一步</h3></div>
        <div class="panel-body">
          <div class="kv-list">
            <div>
              <span>装备对象</span><b>{{ task.equipmentId }}</b>
            </div>
            <div>
              <span>任务类型</span><b>{{ task.taskType }}</b>
            </div>
            <div>
              <span>完成期限</span><b>T+{{ task.deadline }} min</b>
            </div>
            <div>
              <span>重要度</span><b>{{ task.importance }}</b>
            </div>
            <div>
              <span>来源问题</span><b>{{ task.sourceIssueId || "人工创建" }}</b>
            </div>
          </div>
          <hr class="line-divider" />
          <div class="form-stack">
            <button class="btn secondary" @click="router.push(pathFor('S302'))">
              查看履历与技术资料</button
            ><button class="btn primary" @click="openPlan()">
              匹配模板并编制预案<Icon name="ArrowRight" :size="15" />
            </button>
          </div>
        </div>
      </section></div
  ></template>
  <template v-else-if="page.id === 'S302'"
    ><div class="toolbar">
      <span class="pill">EQ-DEMO-01 · 构型 V1</span><span class="spacer"></span
      ><button class="btn secondary" @click="showAnalysis = true">
        结构化故障分析</button
      ><button
        class="btn primary"
        @click="command('history.import', {}, '导入校验已完成')"
      >
        <Icon name="RefreshCw" :size="15" />模拟PLSE同步
      </button>
    </div>
    <div
      v-if="s.lastImport"
      class="info-note"
      :class="{ warning: s.lastImport.rejected > 0 }"
    >
      <Icon name="FileCheck2" :size="17" />新增 {{ s.lastImport.added }} 条 /
      重复跳过 {{ s.lastImport.duplicate }} 条 / 拒绝
      {{ s.lastImport.rejected }} 条。<button
        v-if="s.lastImport.rejected"
        class="text-btn"
        @click="command('history.import', { repair: true }, '已修复主键并补导')"
      >
        修复缺失主键后重传
      </button>
    </div>
    <div class="grid-2">
      <section class="panel">
        <div class="panel-header">
          <h3>履历记录</h3>
          <span class="subtle-label">使用 / 维修 / 改装</span>
        </div>
        <div class="table-scroll">
          <table v-if="s.histories.length" class="data-table">
            <thead>
              <tr>
                <th>记录</th>
                <th>类型</th>
                <th>内容</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="h in s.histories" :key="h.id">
                <td>{{ h.recordId }}</td>
                <td>{{ h.type }}</td>
                <td>{{ h.description || "修复后的合成履历" }}</td>
              </tr>
            </tbody>
          </table>
          <Empty
            v-else
            title="导入装备履历"
            description="10条样例包含8条有效、1条重复与1条缺失主键，用于演示校验和修复。"
            icon="History"
          />
        </div>
      </section>
      <section class="panel">
        <div class="panel-header">
          <h3>构型与技术资料</h3>
          <button class="text-btn" @click="showDocument = true">
            新增资料
          </button>
        </div>
        <div class="panel-body">
          <div v-for="d in s.documents" :key="d.id" class="list-card">
            <div class="resource-icon"><Icon name="FileText" /></div>
            <div>
              <b>{{ d.name }}</b>
              <p>{{ d.type }} · V{{ d.version }}</p>
            </div>
            <button class="text-btn" @click="detail = d">查看</button>
          </div>
        </div>
      </section>
    </div>
    <section class="panel">
      <div class="panel-header">
        <h3>分析结果与维修需求</h3>
        <span class="subtle-label">专家输入与规则示例</span>
      </div>
      <div class="panel-body">
        <div v-for="a in s.analyses" :key="a.id" class="list-card">
          <div class="resource-icon"><Icon name="GitBranch" /></div>
          <div>
            <b>{{ a.type }} · {{ a.tag }}</b>
            <p>{{ a.conclusion }}</p>
            <p>派生需求：{{ a.requirement }}</p>
          </div>
          <Badge :status="a.status" />
        </div>
        <p v-if="!s.analyses.length" class="empty-small">
          选择FTA、FMECA等分析类型，录入依据与专家结论，形成维修需求。
        </p>
      </div>
    </section></template
  >
  <template v-else-if="page.id === 'S303'"
    ><div class="toolbar">
      <div class="filter-search">
        <Icon name="Search" :size="15" /><input
          v-model="query"
          placeholder="搜索资源名称或编号…"
        />
      </div>
      <span class="spacer"></span
      ><button
        class="btn primary"
        @click="
          resourceForm = {
            name: '',
            category: '人员',
            skill: '维修',
            capacity: 1,
            rate: 1.5,
            available: true,
            organization: '本地维修单位',
            start: 0,
            end: 360,
          };
          showResource = true;
        "
      >
        <Icon name="Plus" :size="15" />新增资源
      </button>
    </div>
    <div class="tabs">
      <button
        v-for="c in ['全部资源', '人员', '物料', '器材', '设施']"
        :key="c"
        :class="{ active: resourceCategory === c }"
        @click="resourceCategory = c"
      >
        {{ c }}
      </button>
    </div>
    <div class="info-note">
      <Icon name="Network" :size="16" />远端供应单位 → 供应 / 运输 →
      本地维修单位。人员与工具记录可用日历，物料数量作为独立运行账本的初始来源。
    </div>
    <section class="panel">
      <div class="table-scroll">
        <table class="data-table">
          <thead>
            <tr>
              <th>资源</th>
              <th>类别 / 技能</th>
              <th>归属单位</th>
              <th>数量 / 容量</th>
              <th>可用窗口</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in resources" :key="r.id">
              <td>
                <b>{{ r.name }}</b
                ><small>{{ r.id }}</small>
              </td>
              <td>
                {{ r.category
                }}<small>{{ r.skill || r.capability || "—" }}</small>
              </td>
              <td>{{ r.organization }}</td>
              <td>{{ r.capacity }}</td>
              <td>0～{{ r.end }} min</td>
              <td><Badge :status="r.available ? 'READY' : 'DISABLED'" /></td>
              <td>
                <button
                  class="text-btn"
                  @click="
                    resourceForm = JSON.parse(JSON.stringify(r));
                    showResource = true;
                  "
                >
                  编辑
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section></template
  >
  <template v-else-if="page.id === 'S304'"
    ><div class="grid-2">
      <section class="panel">
        <div class="panel-header">
          <h3>单装备全寿期维修窗口</h3>
          <span class="pill">24 个月</span>
        </div>
        <div class="panel-body">
          <div class="form-grid">
            <label class="field"
              ><span>维修周期（月）</span
              ><input
                v-model.number="cycle"
                type="number"
                min="1"
                max="24" /></label
            ><label class="field"
              ><span>高强度附加窗口</span
              ><select v-model.number="extraMonth">
                <option :value="0">无附加窗口</option>
                <option v-for="m in 24" :key="m" :value="m">
                  第{{ m }}月（强度0.9）
                </option>
              </select></label
            >
          </div>
          <div class="form-footer">
            <button
              class="btn primary"
              @click="
                command(
                  'lifecycle.calculate',
                  { cycle, highIntensityMonth: extraMonth },
                  '寿期窗口已重算',
                )
              "
            >
              计算方案
            </button>
          </div>
        </div>
        <div class="day-windows">
          <div
            v-for="m in 24"
            :key="m"
            :class="{
              active: s.lifecycle?.windows.some((w: any) => w.month === m),
            }"
          >
            <span>第</span><b>{{ m }}</b
            ><span>{{
              s.lifecycle?.windows.some((w: any) => w.month === m)
                ? "维修窗口"
                : "月"
            }}</span>
          </div>
        </div>
      </section>
      <section class="panel">
        <div class="panel-header"><h3>寿期计算结果</h3></div>
        <div v-if="s.lifecycle" class="panel-body">
          <div class="metric-strip" style="grid-template-columns: 1fr 1fr">
            <div class="metric">
              <span>维修窗口</span
              ><b>{{ s.lifecycle.windows.length }}<small>次</small></b>
            </div>
            <div class="metric">
              <span>累计停用</span
              ><b>{{ s.lifecycle.downtime }}<small>天</small></b>
            </div>
            <div class="metric">
              <span>可用天数</span
              ><b>{{ s.lifecycle.availableDays }}<small>天</small></b>
            </div>
            <div class="metric">
              <span>可用占比</span
              ><b>{{ s.lifecycle.availability }}<small>%</small></b>
            </div>
          </div>
          <p class="note-caption">
            使用每月30天的示例历法，每窗口2天，含第24月终点；只表示给定模型的计算结果。
          </p>
        </div>
        <Empty
          v-else
          title="配置周期后计算"
          description="周期6个月生成4个窗口；改为8个月生成3个窗口。"
        />
      </section>
    </div>
    <section class="panel">
      <div class="panel-header">
        <h3>多任务优先级</h3>
        <button class="text-btn" @click="showTask = true">新增任务</button>
      </div>
      <div class="table-scroll">
        <table class="data-table">
          <thead>
            <tr>
              <th>顺序</th>
              <th>任务</th>
              <th>重要度</th>
              <th>截止时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="(t, i) in [...s.tasks].sort(
                (a: any, b: any) =>
                  b.importance - a.importance || a.deadline - b.deadline,
              )"
              :key="t.id"
            >
              <td>{{ i + 1 }}</td>
              <td>{{ t.name }}</td>
              <td>{{ t.importance }}</td>
              <td>{{ t.deadline }} min</td>
              <td>
                <button
                  class="text-btn"
                  @click="
                    command(
                      'task.save',
                      { id: t.id, importance: t.importance === 95 ? 50 : 95 },
                      '重要度已更新，排序已重算',
                    )
                  "
                >
                  调整重要度
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="panel-body">
        <p class="note-caption">
          排序按重要度降序、截止时间升序。资源容量与日历仍是排程的硬约束；优先级不会取消资源占用约束。
        </p>
      </div>
    </section></template
  >
  <template v-else-if="page.id === 'S305'"
    ><div class="toolbar">
      <select v-if="s.tasks.length" v-model="taskId">
        <option value="">最近的保障任务</option>
        <option v-for="t in s.tasks" :key="t.id" :value="t.id">
          {{ t.name }}
        </option></select
      ><span class="spacer"></span
      ><button class="btn secondary" @click="router.push(pathFor('S301'))">
        保障任务</button
      ><button class="btn primary" :disabled="!task" @click="openPlan('')">
        <Icon name="Plus" :size="15" />新建空白预案
      </button>
    </div>
    <section class="panel">
      <div class="panel-header">
        <div>
          <h3>模板匹配</h3>
          <p>先校验装备类型与任务类型，再选择可复用模板</p>
        </div>
        <span class="subtle-label"
          >{{ matchingTemplates.length }} 个适配模板</span
        >
      </div>
      <div class="panel-body">
        <div v-for="t in matchingTemplates" :key="t.id" class="list-card">
          <div class="resource-icon"><Icon name="Library" /></div>
          <div>
            <b>{{ t.name }}</b>
            <p>{{ t.description }} · 装备类型一致 / 任务类型一致</p>
          </div>
          <Badge :status="t.status" /><button
            class="btn secondary small"
            :disabled="!task"
            @click="openPlan(t.id)"
          >
            复用模板
          </button>
        </div>
        <Empty
          v-if="!matchingTemplates.length"
          title="没有满足条件的模板"
          description="可以新建预案，编制完成并审核后再作为模板复用。"
        />
      </div>
    </section>
    <section class="panel">
      <div class="panel-header"><h3>项目预案版本</h3></div>
      <div v-if="s.plans.length" class="table-scroll">
        <table class="data-table">
          <thead>
            <tr>
              <th>预案名称</th>
              <th>版本</th>
              <th>预计工期</th>
              <th>预计费用</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="p in [...s.plans].reverse()" :key="p.id">
              <td>
                <b>{{ p.name }}</b
                ><small>{{ p.id }}</small>
              </td>
              <td>
                <span class="pill">{{ p.version }}</span>
              </td>
              <td>{{ p.schedule.finish }} min</td>
              <td>{{ fmt(p.schedule.cost) }}</td>
              <td><Badge :status="p.status" /></td>
              <td>
                <button
                  class="text-btn"
                  @click="
                    store.selectedPlan = p.id;
                    router.push(pathFor('S306', p.id));
                  "
                >
                  打开预案
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <Empty
        v-else
        title="还没有项目预案"
        description="为已创建任务匹配模板，或从空白预案开始。"
      /></section
  ></template>
  <section v-else-if="!plan" class="panel">
    <Empty
      title="先建立保障预案"
      description="当前环节需要关联一个预案版本。请先创建任务并编制预案。"
      icon="Library"
      ><button class="btn primary" @click="router.push(pathFor('S305'))">
        进入预案库
      </button></Empty
    >
  </section>
  <template v-else-if="page.id === 'S306'"
    ><div class="metric-strip">
      <div class="metric">
        <span>预计完成时间</span
        ><b>{{ plan.schedule.finish }}<small>min</small></b>
        <p>截止 T+{{ plan.deadline }} min</p>
      </div>
      <div class="metric">
        <span>预计总费用</span
        ><b>{{ fmt(plan.schedule.cost) }}<small>示例单位</small></b>
        <p>人工 + 备件 + 运输</p>
      </div>
      <div class="metric">
        <span>资源等待</span
        ><b>{{ plan.schedule.waiting }}<small>min</small></b>
        <p>按约束等待的工序时间</p>
      </div>
      <div class="metric">
        <span>工序数量</span
        ><b>{{ plan.operations.length }}<small>项</small></b>
        <p>
          {{ plan.version }} ·
          {{ plan.schedule.onTime ? "满足截止时间" : "存在超期风险" }}
        </p>
      </div>
    </div>
    <div class="toolbar">
      <h3>
        {{ plan.name }} <span class="muted">· {{ plan.version }}</span>
      </h3>
      <span class="spacer"></span
      ><button
        v-if="plan.status === 'DRAFT'"
        class="btn secondary"
        @click="savePlan"
      >
        保存并重算</button
      ><button
        v-if="plan.status === 'DRAFT'"
        class="btn primary"
        @click="command('plan.submit', { id: plan.id }, '预案已提交审核')"
      >
        提交审核</button
      ><template v-if="plan.status === 'PENDING_REVIEW'"
        ><button class="btn secondary" @click="switchReviewer">
          切换项目审核员</button
        ><button
          class="btn primary"
          @click="command('plan.approve', { id: plan.id }, '预案已批准')"
        >
          审核通过
        </button></template
      ><template v-if="['APPROVED', 'PUBLISHED'].includes(plan.status)"
        ><button
          class="btn secondary"
          @click="revise(plan.version === 'P0' ? 'P1' : 'P2')"
        >
          创建{{ plan.version === "P0" ? "P1" : "P2" }}版本</button
        ><button
          class="btn primary"
          @click="router.push(pathFor('S310', plan.id))"
        >
          进入导调演练<Icon name="ArrowRight" :size="15" /></button
      ></template>
    </div>
    <section class="panel gantt-panel">
      <div class="panel-header">
        <h3>资源受限排程</h3>
        <span class="subtle-label">依赖顺序 + 日历 + 到货条件</span>
      </div>
      <Gantt :spans="plan.schedule.spans" />
    </section>
    <div class="grid-2" style="margin-top: 20px">
      <section class="panel">
        <div class="panel-header"><h3>工序配置</h3></div>
        <div class="table-scroll">
          <table class="data-table">
            <thead>
              <tr>
                <th>工序</th>
                <th>前置</th>
                <th>时长 min</th>
                <th>资源</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="op in editPlan.operations" :key="op.id">
                <td>
                  <b>{{ op.id }} {{ op.name }}</b>
                </td>
                <td>{{ op.predecessors.join(", ") || "无" }}</td>
                <td>
                  <input
                    v-if="plan.status === 'DRAFT'"
                    v-model.number="op.duration"
                    class="table-input"
                    type="number"
                    min="1"
                    :aria-label="op.id + '工序时长'"
                  /><template v-else>{{ op.duration }}</template>
                </td>
                <td>{{ op.resources.join(" / ") }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
      <section class="panel">
        <div class="panel-header"><h3>到货与准备参数</h3></div>
        <div class="panel-body form-stack">
          <label class="field"
            ><span>备件预计到货（T+分钟）</span
            ><input
              v-model.number="editPlan.eta"
              type="number"
              min="0"
              :disabled="plan.status !== 'DRAFT'" /></label
          ><label class="field"
            ><span>运输费用（示例单位）</span
            ><input
              v-model.number="editPlan.transportCost"
              type="number"
              min="0"
              :disabled="plan.status !== 'DRAFT'" /></label
          ><label class="field"
            ><span>预案说明</span
            ><textarea
              v-model="editPlan.notes"
              :disabled="plan.status !== 'DRAFT'"
              placeholder="说明工序、资源或本次版本调整的依据"
            /></label
          ><button
            class="btn secondary"
            @click="router.push(pathFor('S307', plan.id))"
          >
            检查六类生产准备
          </button>
          <p class="note-caption">
            批准的预案冻结输入和资源快照。到货延迟可以进入演练；实际领用必须等待到库。
          </p>
        </div>
      </section>
    </div></template
  >
  <template v-else-if="page.id === 'S307'"
    ><div class="detail-layout">
      <section class="panel">
        <div class="panel-header">
          <div>
            <h3>六类准备清单</h3>
            <p>预案 {{ plan.version }} · {{ plan.name }}</p>
          </div>
          <span class="completion-caption"
            ><b>{{ prep.filter((x) => x.ok).length }}</b> / 6 已具备</span
          >
        </div>
        <div v-for="item in prep" :key="item.name" class="checklist-item">
          <div class="check-icon" :class="{ done: item.ok }">
            <Icon :name="item.ok ? 'Check' : item.icon" :size="18" />
          </div>
          <div>
            <b>{{ item.name }}</b>
            <p>{{ item.note }}</p>
          </div>
          <button
            v-if="item.action === 'training' && !item.ok"
            class="btn secondary small"
            @click="
              command(
                'preparation.request',
                { id: plan.id },
                '培训需求已发送至维修培训系统',
              )
            "
          >
            生成培训需求</button
          ><button
            v-if="item.action === 'material'"
            class="btn secondary small"
            @click="
              command(
                'order.create',
                { planId: plan.id, type: 'TRANSFER', quantity: 2 },
                '调拨草稿已生成',
              )
            "
          >
            生成调拨单</button
          ><Badge v-if="!item.action" :status="item.ok ? 'READY' : 'PENDING'" />
        </div>
      </section>
      <div class="detail-sidebar">
        <section class="panel">
          <div class="panel-header"><h3>库存与需求</h3></div>
          <div class="panel-body kv-list">
            <div><span>物料</span><b>示例密封件</b></div>
            <div><span>本地现货</span><b>0 件</b></div>
            <div><span>远端现货</span><b>4 件</b></div>
            <div><span>本次需求</span><b>2 件</b></div>
            <div>
              <span>计划到货</span><b>T+{{ plan.eta }} min</b>
            </div>
            <button
              class="btn secondary full"
              @click="
                command(
                  'order.create',
                  { planId: plan.id, type: 'PURCHASE', quantity: 2 },
                  '采购草稿已生成',
                )
              "
            >
              采购支线：生成采购单
            </button>
          </div>
        </section>
        <section class="panel">
          <div class="panel-header"><h3>跨系统衔接</h3></div>
          <div class="panel-body form-stack">
            <button class="btn secondary" @click="router.push(pathFor('S202'))">
              制作维修课件</button
            ><button
              class="btn secondary"
              @click="router.push(pathFor('S308', plan.id))"
            >
              编制五类专项计划
            </button>
          </div>
        </section>
      </div>
    </div>
    <section class="panel">
      <div class="panel-header">
        <h3>调拨 / 采购单据</h3>
        <span class="subtle-label">独立模拟单据账本</span>
      </div>
      <div class="table-scroll">
        <table
          v-if="s.orders.some((o: any) => o.planId === plan.id)"
          class="data-table"
        >
          <thead>
            <tr>
              <th>单据</th>
              <th>类型</th>
              <th>数量</th>
              <th>状态</th>
              <th>账本动作</th>
              <th>下一步</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="o in s.orders.filter((o: any) => o.planId === plan.id)"
              :key="o.id"
            >
              <td>
                <b>{{ o.id }}</b
                ><small>{{ o.source }} → {{ o.target }}</small>
              </td>
              <td>{{ o.type === "TRANSFER" ? "调拨" : "采购" }}</td>
              <td>{{ o.quantity }} 件</td>
              <td><Badge :status="o.status" /></td>
              <td>{{ o.ledger.length }} 条</td>
              <td>
                <button
                  v-if="o.status !== 'CLOSED'"
                  class="text-btn"
                  @click="command('order.advance', { id: o.id })"
                >
                  {{
                    {
                      DRAFT: "审批单据",
                      APPROVED: "模拟发运",
                      DISPATCHED: "验收入库",
                      RECEIVED: "确认关闭",
                    }[o.status]
                  }}</button
                ><button v-else class="text-btn" @click="detail = o">
                  查看台账
                </button>
              </td>
            </tr>
          </tbody>
        </table>
        <p v-else class="empty-small">
          从准备项生成单据后，继续审批、发运、收货和关闭。
        </p>
      </div>
    </section></template
  >
  <template v-else-if="page.id === 'S308'"
    ><div class="tabs">
      <button
        v-for="t in [
          '施工总计划',
          '采购计划',
          '进出库计划',
          '修理调试计划',
          '单装试验大纲',
        ]"
        :key="t"
        :class="{ active: subType === t }"
        @click="subType = t"
      >
        {{ t }}
      </button>
    </div>
    <div class="toolbar">
      <p>每类计划分别编制、审核，关联预案 {{ plan.version }}。</p>
      <span class="spacer"></span
      ><button class="btn secondary" @click="switchReviewer">
        切换项目审核员</button
      ><button class="btn primary" @click="newSub">
        <Icon name="Plus" :size="15" />编制{{ subType }}
      </button>
    </div>
    <section class="panel">
      <div class="panel-body" style="padding-top: 20px">
        <div
          v-for="sub in s.subplans.filter(
            (sub: any) => sub.planId === plan.id && sub.type === subType,
          )"
          :key="sub.id"
          class="list-card"
        >
          <div class="resource-icon"><Icon name="NotebookPen" /></div>
          <div>
            <b>{{ sub.name }}</b>
            <p>{{ sub.id }} · {{ sub.owner }}</p>
          </div>
          <Badge :status="sub.status" /><button
            class="text-btn"
            @click="detail = sub"
          >
            查看内容</button
          ><button
            v-if="sub.status === 'DRAFT'"
            class="text-btn"
            @click="
              command('subplan.submit', { id: sub.id }, '专项计划已提交审核')
            "
          >
            提交审核</button
          ><button
            v-if="sub.status === 'PENDING_REVIEW'"
            class="text-btn"
            @click="
              command('subplan.approve', { id: sub.id }, '专项计划已批准')
            "
          >
            审核通过
          </button>
        </div>
        <Empty
          v-if="
            !s.subplans.some(
              (sub: any) => sub.planId === plan.id && sub.type === subType,
            )
          "
          :title="'编制' + subType"
          description="补齐责任、数量、作业说明和检查要求，形成可追溯的专项计划。"
        />
      </div></section
  ></template>
  <template v-else-if="page.id === 'S309'"
    ><div class="grid-2">
      <section class="panel">
        <div class="panel-header"><h3>以换代修方案比较</h3></div>
        <div class="panel-body form-stack">
          <div class="form-grid">
            <label class="field"
              ><span>修复费用</span
              ><input
                v-model.number="repairCost"
                type="number"
                min="0" /></label
            ><label class="field"
              ><span>更换费用</span
              ><input v-model.number="replaceCost" type="number" min="0"
            /></label>
          </div>
          <label class="checkbox-row"
            ><input
              v-model="compatible"
              type="checkbox"
            />替换件满足兼容性条件</label
          ><button
            class="btn primary"
            @click="
              command(
                'special.evaluate',
                {
                  type: 'REPLACE',
                  planId: plan.id,
                  repairCost,
                  replaceCost,
                  compatible,
                },
                '候选方案已计算',
              )
            "
          >
            比较候选
          </button>
        </div>
      </section>
      <section class="panel">
        <div class="panel-header"><h3>设备出舱路径</h3></div>
        <div class="panel-body">
          <div class="info-note">
            <Icon
              name="Route"
              :size="17"
            />三条预设路径分别检查尺寸、承载与通行条件。几何校验由假设平台提供模拟回执。
          </div>
          <button
            class="btn secondary full"
            @click="
              command(
                'special.evaluate',
                { type: 'PATH', planId: plan.id },
                '三条路径已完成模拟校验',
              )
            "
          >
            校验三条候选路径
          </button>
        </div>
      </section>
    </div>
    <section v-if="s.special" class="panel">
      <div class="panel-header">
        <h3>候选结果</h3>
        <Badge :status="s.special.status" />
      </div>
      <div class="panel-body">
        <div
          v-for="(c, i) in s.special.candidates"
          :key="c.name"
          class="list-card"
        >
          <div class="resource-icon">
            <Icon :name="c.feasible ? 'Route' : 'Ban'" />
          </div>
          <div>
            <b>{{ c.name }}</b>
            <p>
              {{
                c.reason ||
                (c.feasible
                  ? `预计费用 ${c.cost} / 时长 ${c.duration} min`
                  : "不满足部件兼容性条件")
              }}
            </p>
          </div>
          <Badge :status="c.feasible ? 'READY' : 'FAILED'" /><button
            class="btn secondary small"
            :disabled="!c.feasible"
            @click="
              command('special.adopt', { index: i }, '候选已采纳并关联当前预案')
            "
          >
            {{ s.special.selectedIndex === i ? "已采纳" : "采纳方案" }}
          </button>
        </div>
      </div>
    </section></template
  >
  <Modal v-if="showTask" title="新建保障任务" @close="showTask = false"
    ><div class="form-grid">
      <label class="field full"
        ><span>任务名称 *</span><input v-model="taskForm.name" /></label
      ><label class="field"
        ><span>任务类型</span
        ><select v-model="taskForm.taskType">
          <option>维修</option>
          <option>巡检</option>
          <option>其他</option>
        </select></label
      ><label class="field"
        ><span>重要度（0～100）</span
        ><input
          v-model.number="taskForm.importance"
          type="number"
          min="0"
          max="100" /></label
      ><label class="field full"
        ><span>完成期限（T+分钟）</span
        ><input
          v-model.number="taskForm.deadline"
          type="number"
          min="1" /></label
      ><label class="field full"
        ><span>任务范围</span><textarea v-model="taskForm.description" />
      </label>
    </div>
    <template #footer
      ><button class="btn secondary" @click="showTask = false">取消</button
      ><button
        class="btn primary"
        :disabled="!taskForm.name.trim()"
        @click="createTask"
      >
        创建任务
      </button></template
    ></Modal
  >
  <Modal v-if="showPlan" title="创建保障预案" @close="showPlan = false"
    ><div class="form-stack">
      <label class="field"
        ><span>预案名称</span><input v-model="planForm.name" /></label
      ><label class="field"
        ><span>关联任务</span
        ><select v-model="planForm.taskId">
          <option value="" disabled>请选择任务</option>
          <option v-for="t in s.tasks" :key="t.id" :value="t.id">
            {{ t.name }}
          </option>
        </select></label
      ><label class="field"
        ><span>来源模板</span
        ><select v-model="planForm.templateId">
          <option value="">新建空白预案（载入可编辑八工序）</option>
          <option
            v-for="t in s.templates.filter((t: any) => t.type === 'PLAN')"
            :key="t.id"
            :value="t.id"
          >
            {{ t.name }}
          </option>
        </select></label
      >
      <p class="note-caption">
        创建时校验任务类型与装备类型，复制当前资源快照并计算初始排程。
      </p>
    </div>
    <template #footer
      ><button
        class="btn primary"
        :disabled="!planForm.taskId"
        @click="createPlan"
      >
        创建并计算
      </button></template
    ></Modal
  >
  <Modal v-if="showResource" title="编辑保障资源" @close="showResource = false"
    ><div class="form-grid">
      <label class="field full"
        ><span>资源名称</span><input v-model="resourceForm.name" /></label
      ><label class="field"
        ><span>类别</span
        ><select v-model="resourceForm.category">
          <option>人员</option>
          <option>物料</option>
          <option>器材</option>
          <option>设施</option>
        </select></label
      ><label class="field"
        ><span>技能 / 能力标签</span
        ><input v-model="resourceForm.skill" /></label
      ><label class="field"
        ><span>数量 / 容量</span
        ><input
          v-model.number="resourceForm.capacity"
          type="number"
          min="0" /></label
      ><label class="field"
        ><span>人工费率 / 分钟</span
        ><input
          v-model.number="resourceForm.rate"
          type="number"
          min="0"
          step=".5" /></label
      ><label class="field"
        ><span>可用开始（分钟）</span
        ><input
          v-model.number="resourceForm.start"
          type="number"
          min="0" /></label
      ><label class="field"
        ><span>可用结束（分钟）</span
        ><input
          v-model.number="resourceForm.end"
          type="number"
          min="1" /></label
      ><label class="field full"
        ><span>所属单位</span
        ><input v-model="resourceForm.organization" /></label
      ><label class="checkbox-row"
        ><input
          v-model="resourceForm.available"
          type="checkbox"
        />当前可用</label
      >
    </div>
    <template #footer
      ><button class="btn primary" @click="saveResource">
        保存资源
      </button></template
    ></Modal
  >
  <Modal v-if="showSub" :title="'编制' + subType" @close="showSub = false"
    ><div class="form-grid">
      <label class="field full"
        ><span>责任单位</span><input v-model="subForm.owner" /></label
      ><label class="field"
        ><span>物料 / 项目数量</span
        ><input
          v-model.number="subForm.quantity"
          type="number"
          min="1" /></label
      ><label class="field"
        ><span>示例单价</span
        ><input v-model.number="subForm.amount" type="number" min="0" /></label
      ><label class="field full"
        ><span>供应方 / 配合单位</span
        ><input v-model="subForm.supplier" /></label
      ><label class="field full"
        ><span>编制说明与执行步骤</span
        ><textarea v-model="subForm.content" /></label
      ><label class="field full"
        ><span>检查与验收要求</span><input v-model="subForm.criteria"
      /></label>
    </div>
    <template #footer
      ><span class="muted" style="margin-right: auto; font-size: 11px"
        >示例合计：{{ fmt(subForm.quantity * subForm.amount) }}</span
      ><button class="btn primary" @click="createSub">
        保存计划草稿
      </button></template
    ></Modal
  >
  <Modal
    v-if="showAnalysis"
    title="结构化分析到维修需求"
    @close="showAnalysis = false"
    ><div class="form-stack">
      <label class="field"
        ><span>分析类型</span
        ><select v-model="analysisForm.type">
          <option
            v-for="t in ['FTA', 'OTA', 'FMECA', 'RCMA', 'MTA', 'LORA', 'LCC']"
            :key="t"
          >
            {{ t }}
          </option>
        </select></label
      ><label class="field"
        ><span>故障标签</span><input v-model="analysisForm.tag" /></label
      ><label class="field"
        ><span>依据与专家结论 *</span
        ><textarea v-model="analysisForm.conclusion" />
      </label>
      <div class="info-note">
        <Icon
          name="Info"
          :size="16"
        />本环节保存结构化专家输入与维修需求，不代替专业可靠性分析软件。
      </div>
    </div>
    <template #footer
      ><button
        class="btn primary"
        @click="
          command('analysis.create', analysisForm, '分析与维修需求已保存');
          showAnalysis = false;
        "
      >
        确认并形成需求
      </button></template
    ></Modal
  >
  <Modal v-if="showDocument" title="新增技术资料" @close="showDocument = false"
    ><div class="form-stack">
      <label class="field"
        ><span>资料名称</span><input v-model="docForm.name" /></label
      ><label class="field"
        ><span>类别</span
        ><select v-model="docForm.type">
          <option>技术资料</option>
          <option>构型/BOM</option>
          <option>手册</option>
          <option>标准</option>
          <option>图纸说明</option>
        </select></label
      ><label class="field"
        ><span>资料内容</span><textarea v-model="docForm.content" />
      </label>
    </div>
    <template #footer
      ><button
        class="btn primary"
        @click="
          command('document.save', docForm, '资料已保存');
          showDocument = false;
        "
      >
        保存资料
      </button></template
    ></Modal
  >
  <Modal
    v-if="detail"
    :title="detail.name || detail.id"
    wide
    @close="detail = null"
    ><p
      v-if="detail.content"
      class="prose"
      style="white-space: pre-wrap; line-height: 1.9; color: #7890a5"
    >
      {{ detail.content }}
    </p>
    <pre v-else class="code-area">{{ JSON.stringify(detail, null, 2) }}</pre>
  </Modal>
</template>

<script setup lang="ts">
import { ref, computed } from "vue";
import { useRouter } from "vue-router";
import { tours, guide, startGuide, currentTour, guidePath } from "../guide";
import {
  store,
  refreshWorkspaceList,
  newWorkspace,
  login,
  switchWorkspace,
  notify,
} from "../store";
import { seedDemo } from "../lib/seed-demo.mjs";
import Icon from "../components/Icon.vue";
const router = useRouter();
const building = ref(false),
  progress = ref(""),
  failed = ref(""),
  partialWorkspace = ref("");
const selectedTour = ref(guide.tour),
  mode = ref(guide.mode);
const tour = computed(
  () => tours.find((t) => t.id === selectedTour.value) || tours[0],
);
const ready = computed(() =>
  store.data?.archives.some((a: any) => a.status === "PUBLISHED"),
);
const stages: Record<string, string> = {
  workspace: "创建独立演示空间",
  task: "建立保障任务",
  plan: "编制并审批保障预案",
  preparation: "关联培训需求",
  course: "制作、审核并发布课程",
  training: "完成操作与维修训练",
  history: "准备履历资料",
  lifecycle: "计算维修周期",
  run: "生成正常、延迟和加急演练",
  sensitivity: "计算敏感性",
  evaluation: "计算评价指标",
  execution: "建立模拟施工记录",
  inspection: "记录检查与复查",
  rectification: "记录整改",
  comparison: "对比实绩",
  archive: "完成归档审核与发布",
};
async function prepareCase() {
  building.value = true;
  failed.value = "";
  partialWorkspace.value = "";
  try {
    const result = await seedDemo(
      window.location.origin,
      `汇报演示 · ${new Date().toLocaleString("zh-CN")}`,
      (event: any) => {
        partialWorkspace.value = event.workspace;
        progress.value = stages[event.action.split(".")[0]] || "保存业务记录";
      },
    );
    await refreshWorkspaceList();
    guide.active = false;
    guide.mode = "present";
    mode.value = "present";
    await switchWorkspace(result.workspace);
    notify("完整案例已就绪，请选择一个系统开始讲解");
  } catch (e: any) {
    failed.value = `案例准备未完成：${e.message}。已产生的数据保留在独立工作区，可检查后重新创建。`;
    await refreshWorkspaceList().catch(() => {});
  } finally {
    building.value = false;
  }
}
function begin(index = 0) {
  startGuide(selectedTour.value, mode.value);
  guide.index = index;
  router.push(guidePath());
  window.scrollTo(0, 0);
}
async function practice() {
  building.value = true;
  progress.value = "创建练习工作区";
  try {
    await login("ADMIN");
    const workspace = await newWorkspace(
      `手动练习 · ${new Date().toLocaleString("zh-CN")}`,
    );
    if (workspace) {
      mode.value = "practice";
      begin();
    }
  } catch (e: any) {
    notify(e.message, "error");
  } finally {
    building.value = false;
  }
}
</script>
<template>
  <section class="demo-start-panel">
    <div class="demo-start-copy">
      <span class="section-kicker">第一次演示，从这里开始</span>
      <h2>先准备案例，再选择一条讲解路线。</h2>
      <p>
        每一步都会告诉你：进入哪个页面、使用什么身份、点击哪个按钮，以及应该看到什么结果。
      </p>
      <div class="button-row">
        <button class="btn primary" :disabled="building" @click="prepareCase">
          <Icon name="Play" :size="18" />{{
            building ? "正在准备…" : "准备一套完整演示案例"
          }}
        </button>
        <button
          v-if="guide.active"
          class="btn secondary"
          :disabled="building"
          @click="router.push(guidePath())"
        >
          继续上次指引 · {{ currentTour().title }}
        </button>
      </div>
      <p class="guide-note">
        自动在新工作区生成真实业务记录，保留现有数据。完成后按下方路线查看和讲解；需要练习新建与审批时，选择“跟随操作”。
      </p>
    </div>
    <div class="demo-ready-card">
      <Icon :name="ready ? 'CircleCheck' : 'ClipboardList'" :size="28" />
      <b>{{ ready ? "当前工作区已有归档成果" : "当前工作区可继续操作" }}</b>
      <span>{{ store.data.name }}</span>
      <p>
        {{ store.data.courses.length }} 门课程 ·
        {{ store.data.runs.length }} 次演练 ·
        {{ store.data.archives.length }} 项成果
      </p>
    </div>
  </section>
  <div v-if="building" class="demo-progress" role="status">
    <span class="spinner"></span>
    <div>
      <b>{{ progress }}</b>
      <p>请留在当前页面，完成后会自动切换到新工作区。</p>
    </div>
  </div>
  <div v-if="failed" class="info-note warning" role="alert">
    {{ failed }}
    <span v-if="partialWorkspace">工作区：{{ partialWorkspace }}</span>
  </div>
  <div class="guide-section-title">
    <div>
      <h2>选择你要展示的系统</h2>
      <p>推荐顺序：操作训练 → 维修培训 → 保障筹划与演练</p>
    </div>
    <span>三条路线 · 一套关联案例</span>
  </div>
  <div class="tour-grid">
    <button
      v-for="t in tours"
      :key="t.id"
      class="tour-card"
      :class="[t.color, { selected: selectedTour === t.id }]"
      :aria-pressed="selectedTour === t.id"
      :disabled="building"
      @click="selectedTour = t.id"
    >
      <div class="tour-card-top">
        <span class="tour-number">{{ t.number }}</span
        ><Icon :name="t.icon" :size="28" />
      </div>
      <h3>{{ t.title }}</h3>
      <p>{{ t.summary }}</p>
      <div class="tour-card-footer">
        <span>{{ t.steps.length }} 个步骤</span><span>{{ t.duration }}</span>
      </div>
    </button>
  </div>
  <section class="panel guide-outline">
    <div class="panel-header">
      <div>
        <h3>{{ tour.title }} · 演示路线</h3>
        <p>{{ tour.takeaway }}</p>
      </div>
      <div class="guide-mode" aria-label="演示方式">
        <button
          :class="{ active: mode === 'present' }"
          @click="mode = 'present'"
        >
          讲解已有案例</button
        ><button
          :class="{ active: mode === 'practice' }"
          @click="mode = 'practice'"
        >
          跟随操作
        </button>
      </div>
    </div>
    <div class="guide-launch">
      <p v-if="mode === 'present'">
        适合客户汇报。先准备完整案例，再沿着页面查看结果、复盘过程和解释业务逻辑。
      </p>
      <p v-else>
        适合亲自操作。按步骤创建、提交和审核；需要时点击指引中的身份切换按钮。
      </p>
      <div class="button-row">
        <button class="btn primary" :disabled="building" @click="begin()">
          <Icon name="Play" :size="16" />{{
            mode === "present" ? "开始讲解" : "在当前工作区开始"
          }}</button
        ><button
          v-if="mode === 'practice'"
          class="btn secondary"
          :disabled="building"
          @click="practice"
        >
          新建练习空间并开始
        </button>
      </div>
    </div>
    <div v-if="mode === 'present' && !ready" class="guide-empty-note">
      当前工作区尚无已发布归档成果。首次汇报建议先点击上方“准备一套完整演示案例”。
    </div>
    <div class="guide-step-list">
      <button
        v-for="(step, i) in tour.steps"
        :key="i"
        :disabled="building"
        @click="begin(i)"
      >
        <span class="outline-number">{{ String(i + 1).padStart(2, "0") }}</span>
        <div>
          <b>{{ step.title }}</b>
          <p>{{ mode === "present" ? step.inspect : step.actions[0] }}</p>
        </div>
        <span class="outline-role">{{
          store.accounts.find((a) => a.id === step.role)?.name || step.role
        }}</span>
        <Icon name="ArrowRight" :size="18" />
      </button>
    </div>
  </section>
</template>

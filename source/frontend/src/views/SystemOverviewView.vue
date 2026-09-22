<script setup lang="ts">
import { computed } from "vue";
import { useRouter } from "vue-router";
import type { PageMeta } from "../catalog";
import { pathForFeature } from "../catalog";
import { store, fmt } from "../store";
import { systemFor } from "../domain";
import Icon from "../components/Icon.vue";

const props = defineProps<{ page: PageMeta }>();
const router = useRouter(),
  s = computed(() => store.data),
  system = computed(() => systemFor(props.page.system)!);
const courses = computed(() =>
  s.value.courses.filter((item: any) => item.domain === props.page.system),
);
const assignments = computed(() =>
  s.value.assignments.filter((item: any) => item.domain === props.page.system),
);
const attempts = computed(() =>
  s.value.attempts.filter((item: any) => item.domain === props.page.system),
);
const completed = computed(() =>
  attempts.value.filter((item: any) => item.status === "COMPLETED"),
);
const resourceCards = computed(() => [
  {
    label: "可用素材",
    value: s.value.assets.length,
    suffix: "项",
    hint: "本系统自有与已授权素材",
    icon: "Boxes",
    color: "blue",
    feature: "assets",
  },
  {
    label: "仿真工程",
    value: s.value.simulationProjects.length,
    suffix: "个",
    hint: "独立场景与对象实例",
    icon: "Network",
    color: "teal",
    feature: "simulation-projects",
  },
  {
    label: "系统模板",
    value: s.value.systemTemplates.length,
    suffix: "个",
    hint: "不可变版本与修订复用",
    icon: "Copy",
    color: "purple",
    feature: "simulation-templates",
  },
]);
const trainingCards = computed(() => [
  {
    label: "课件版本",
    value: courses.value.length,
    suffix: "个",
    hint: `${courses.value.filter((item: any) => item.status === "PUBLISHED").length} 个已发布`,
    icon: "BookOpenCheck",
    color: "blue",
    feature: "coursewares",
  },
  {
    label: "培训任务",
    value: assignments.value.length,
    suffix: "项",
    hint: `${assignments.value.filter((item: any) => item.status === "RUNNING").length} 项进行中`,
    icon: "Users",
    color: "teal",
    feature: "assignments",
  },
  {
    label: "完成训练",
    value: completed.value.length,
    suffix: "次",
    hint: completed.value.length
      ? `平均得分 ${fmt(completed.value.reduce((sum: number, item: any) => sum + (item.score || 0), 0) / completed.value.length, 1)}`
      : "等待本系统首个结果",
    icon: "GraduationCap",
    color: "purple",
    feature: "training-evaluations",
  },
]);
const supportCards = computed(() => [
  {
    label: "保障任务",
    value: s.value.tasks.length,
    suffix: "项",
    hint: "任务范围与目标",
    icon: "ClipboardList",
    color: "blue",
    feature: "tasks",
  },
  {
    label: "预案版本",
    value: s.value.plans.length,
    suffix: "个",
    hint: "工序、资源与排程",
    icon: "GanttChart",
    color: "teal",
    feature: "plan-library",
  },
  {
    label: "完成演练",
    value: s.value.runs.filter((item: any) => item.status === "COMPLETED")
      .length,
    suffix: "次",
    hint: "方案指标与事件记录",
    icon: "Radar",
    color: "purple",
    feature: "exercises",
  },
  {
    label: "成果归档",
    value: s.value.archives.length,
    suffix: "份",
    hint: "三级审核与发布",
    icon: "FolderCheck",
    color: "amber",
    feature: "archives",
  },
]);
const cards = computed(() =>
  props.page.system === "SUPPORT" ? supportCards.value : trainingCards.value,
);
function go(feature: string) {
  router.push(pathForFeature(props.page.system!, feature));
}
</script>

<template>
  <section class="business-intro">
    <div>
      <span class="task-eyebrow">{{ system.name }} · 本系统工作台</span>
      <h2>从本系统上下文进入任务，不带入其他系统的选择。</h2>
      <p v-if="page.system === 'SUPPORT'">
        正式保障任务、预案、演练、模拟施工和成果归档保持原有业务链；教学训练使用独立案例副本和学习成绩。
      </p>
      <p v-else>
        课件制作、任务分配、学员训练、培训评价和训练归档均按当前系统领域筛选。
      </p>
    </div>
  </section>
  <div class="section-label"><span>资源准备</span><span>素材、独立仿真工程与系统模板</span></div>
  <section class="stat-grid">
    <button v-for="item in resourceCards" :key="item.label" class="stat-card" @click="go(item.feature)">
      <div class="stat-top"><span>{{ item.label }}</span><span class="stat-icon" :class="item.color"><Icon :name="item.icon" :size="20" /></span></div>
      <div class="stat-number">{{ item.value }}<small>{{ item.suffix }}</small></div>
      <div class="stat-foot">{{ item.hint }}<Icon name="ArrowUpRight" :size="15" /></div>
    </button>
  </section>
  <template v-if="page.system === 'SUPPORT'">
    <div class="section-label"><span>教学训练</span><span>课件、学员任务与学习成绩，不计入正式演练次数</span></div>
    <section class="stat-grid">
      <button v-for="item in trainingCards" :key="item.label" class="stat-card" @click="go(item.feature)">
        <div class="stat-top"><span>{{ item.label }}</span><span class="stat-icon" :class="item.color"><Icon :name="item.icon" :size="20" /></span></div>
        <div class="stat-number">{{ item.value }}<small>{{ item.suffix }}</small></div>
        <div class="stat-foot">{{ item.hint }}<Icon name="ArrowUpRight" :size="15" /></div>
      </button>
      <button class="stat-card" @click="go('training-issues')">
        <div class="stat-top"><span>训练问题</span><span class="stat-icon amber"><Icon name="MessageSquareWarning" :size="20" /></span></div>
        <div class="stat-number">{{ s.issues.filter((item: any) => item.domain === 'SUPPORT').length }}<small>项</small></div>
        <div class="stat-foot">可选分流，不影响正常归档<Icon name="ArrowUpRight" :size="15" /></div>
      </button>
    </section>
  </template>
  <div class="section-label"><span>{{ page.system === 'SUPPORT' ? '保障业务' : '教学训练' }}</span><span>当前系统业务数据</span></div>
  <section class="stat-grid">
    <button
      v-for="item in cards"
      :key="item.label"
      class="stat-card"
      @click="go(item.feature)"
    >
      <div class="stat-top">
        <span>{{ item.label }}</span>
        <span class="stat-icon" :class="item.color"
          ><Icon :name="item.icon" :size="20"
        /></span>
      </div>
      <div class="stat-number">
        {{ item.value }}<small>{{ item.suffix }}</small>
      </div>
      <div class="stat-foot">
        {{ item.hint }}<Icon name="ArrowUpRight" :size="15" />
      </div>
    </button>
  </section>
  <section v-if="page.system === 'SUPPORT'" class="panel panel-body">
    <div class="info-note">
      <Icon name="BookOpenCheck" :size="20" />
      <div>
        <b>保障业务与教学训练双主线独立运行</b>
        <p>学员只操作本人训练副本；方案工期/费用与学习成绩分别计算。正式保障任务和预案不会被训练修改。</p>
      </div>
    </div>
    <div class="button-row">
      <button class="btn primary" @click="go('tasks')">进入保障任务</button>
      <button class="btn secondary" @click="go('coursewares')">
        制作保障教学课件
      </button>
    </div>
  </section>
</template>

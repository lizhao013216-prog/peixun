<script setup lang="ts">
import { computed } from "vue";
import { useRouter } from "vue-router";
import { store, fmt, time, login, notify } from "../store";
import { pathFor, actionText } from "../catalog";
import Icon from "../components/Icon.vue";
import Badge from "../components/Badge.vue";
import SceneView from "../components/SceneView.vue";
import { guide } from "../guide";
const router = useRouter(),
  s = computed(() => store.data);
const go = (id: string, target?: string) => router.push(pathFor(id, target));
async function enterRole(actor: string, page: string) {
  try {
    await login(actor);
    guide.active = false;
    go(page);
  } catch (e: any) {
    notify(e.message, "error");
  }
}
const completed = computed(() =>
  s.value.runs.filter((r: any) => r.status === "COMPLETED"),
);
const attempts = computed(() =>
  s.value.attempts.filter(
    (a: any) => a.status === "COMPLETED" && a.scope === "INDIVIDUAL",
  ),
);
const events = computed(() => [...s.value.events].reverse().slice(0, 5));
const steps = computed(() => [
  {
    name: "任务建模",
    sub: "对象与维修范围",
    done: s.value.tasks.length > 0,
    route: "S301",
    icon: "ClipboardList",
  },
  {
    name: "预案筹划",
    sub: "工序与资源准备",
    done: s.value.plans.some((p: any) =>
      ["APPROVED", "PUBLISHED"].includes(p.status),
    ),
    route: "S306",
    icon: "GitBranch",
  },
  {
    name: "技能培训",
    sub: "课件与训练证据",
    done: s.value.assignments.some((a: any) => a.confirmed),
    route: "S203",
    icon: "GraduationCap",
  },
  {
    name: "导调演练",
    sub: "监控与优化方案",
    done: completed.value.length > 0,
    route: "S311",
    icon: "Radar",
  },
  {
    name: "模拟施工",
    sub: "报工与验收闭环",
    done: s.value.executions.some((e: any) => e.status === "ACCEPTED"),
    route: "S314",
    icon: "HardHat",
  },
  {
    name: "成果归档",
    sub: "审核发布与复用",
    done: s.value.archives.some((a: any) => a.status === "PUBLISHED"),
    route: "S316",
    icon: "FolderCheck",
  },
]);
const done = computed(() => steps.value.filter((x) => x.done).length);
const statCards = computed(() => [
  {
    label: "保障任务",
    value: s.value.tasks.length,
    suffix: "项",
    hint: `${s.value.plans.length} 个关联预案`,
    icon: "ClipboardList",
    color: "blue",
    route: "S301",
  },
  {
    label: "已发布课件",
    value: s.value.courses.filter((c: any) => c.status === "PUBLISHED").length,
    suffix: "门",
    hint: `${s.value.assets.length} 项可复用资源`,
    icon: "BookOpenCheck",
    color: "teal",
    route: "S202",
  },
  {
    label: "完成训练",
    value: attempts.value.length,
    suffix: "次",
    hint: attempts.value.length
      ? `平均得分 ${fmt(attempts.value.reduce((n: number, a: any) => n + a.score, 0) / attempts.value.length, 1)}`
      : "等待首次个人训练",
    icon: "Users",
    color: "purple",
    route: "S204",
  },
  {
    label: "完成演练",
    value: completed.value.length,
    suffix: "次",
    hint: completed.value.length
      ? `最近工期 ${completed.value.at(-1).schedule.finish} min`
      : "准备一个可验证的方案",
    icon: "Radar",
    color: "amber",
    route: "S311",
  },
]);
</script>
<template>
  <section class="role-entry-grid" aria-label="按角色开始业务">
    <button @click="enterRole('AUTHOR', 'S202')">
      <Icon name="BookOpenCheck" :size="26" />
      <div>
        <b>教员：制作与发布课件</b
        ><span>编辑步骤 → 学员预览 → 审核发布 → 分配任务</span>
      </div>
      <Icon name="ArrowRight" />
    </button>
    <button @click="enterRole('LEARNER_A', 'S203')">
      <Icon name="GraduationCap" :size="26" />
      <div>
        <b>学员：开始我的训练</b
        ><span>读任务 → 选对象 → 执行动作 → 看结果</span>
      </div>
      <Icon name="ArrowRight" />
    </button>
    <button @click="enterRole('INSTRUCTOR', 'S204')">
      <Icon name="ClipboardCheck" :size="26" />
      <div>
        <b>教员：检查培训结果</b
        ><span>查看成绩与记录 → 确认证据 → 回流准备</span>
      </div>
      <Icon name="ArrowRight" />
    </button>
  </section>
  <section class="welcome-panel">
    <div class="welcome-copy">
      <div class="section-kicker">
        <span class="tiny-line"></span>三系统业务演示 · 从这里开始
      </div>
      <h2>看懂业务全流程，<br />一步一步完成演示。</h2>
      <p>
        先准备完整案例，再选择演示路线。<br
          class="desktop-only"
        />每一步都有操作说明、角色提示和预期结果。
      </p>
      <div class="button-row">
        <button class="btn primary" @click="go('C08')">
          <Icon name="Play" :size="18" />开始演示</button
        ><button class="text-btn" @click="go('S301')">
          创建保障任务<Icon name="ArrowRight" :size="16" />
        </button>
      </div>
      <div class="welcome-meta">
        <span><i class="live-dot"></i>示例装备 EQ-DEMO-01</span
        ><span>通用泵组 · 四设备联动</span>
      </div>
    </div>
    <div class="welcome-visual">
      <SceneView compact :state="s.topology.pumpState" />
      <div class="visual-stamp">
        <Icon name="Layers3" :size="18" />
        <div>
          <b>业务共用 · 能力复用</b><span>统一资源 / 统一版本 / 全程留痕</span>
        </div>
      </div>
    </div>
  </section>
  <section class="stat-grid">
    <button
      v-for="st in statCards"
      :key="st.label"
      class="stat-card"
      @click="go(st.route)"
    >
      <div class="stat-top">
        <span>{{ st.label }}</span
        ><span class="stat-icon" :class="st.color"
          ><Icon :name="st.icon" :size="20"
        /></span>
      </div>
      <div class="stat-number">
        {{ st.value }}<small>{{ st.suffix }}</small>
      </div>
      <div class="stat-foot">
        {{ st.hint }}<Icon name="ArrowUpRight" :size="15" />
      </div>
    </button>
  </section>
  <section class="panel workflow-panel">
    <div class="panel-header">
      <div>
        <h3>全业务流程</h3>
        <p>六个环节，共享同一项目与业务证据</p>
      </div>
      <span class="completion-caption"
        ><b>{{ done }}</b> / 6 环节已完成</span
      >
    </div>
    <div class="workflow-track">
      <button
        v-for="(step, i) in steps"
        :key="step.name"
        :class="{ completed: step.done }"
        @click="go(step.route)"
      >
        <div class="workflow-icon">
          <Icon :name="step.done ? 'Check' : step.icon" :size="22" />
        </div>
        <div>
          <b>{{ step.name }}</b
          ><span>{{ step.sub }}</span>
        </div>
        <i>{{ String(i + 1).padStart(2, "0") }}</i>
      </button>
    </div>
  </section>
  <section class="system-grid">
    <article
      class="system-card"
      v-for="(c, i) in [
        {
          title: '装备操作训练',
          en: 'OPERATION TRAINING',
          icon: 'Orbit',
          text: '素材制作、设备组网、课程发布与协同操作，建立可复用的训练内容。',
          route: 'S101',
          tag: '系统一',
          count: s.courses.filter((c: any) => c.domain === 'OPERATION').length,
          unit: '门操作课程',
        },
        {
          title: '维修技能培训',
          en: 'MAINTENANCE TRAINING',
          icon: 'GraduationCap',
          text: '将维修需求编排为交互课件，完成技能训练、过程评价与培训证据确认。',
          route: 'S202',
          tag: '系统二',
          count: s.courses.filter((c: any) => c.domain === 'MAINTENANCE')
            .length,
          unit: '门维修课件',
        },
        {
          title: '保障筹划与演练',
          en: 'SUPPORT OPERATIONS',
          icon: 'Network',
          text: '从任务资源建模到预案演练、模拟施工和成果归档，验证保障方案。',
          route: 'S301',
          tag: '系统三',
          count: s.plans.length,
          unit: '个保障预案',
        },
      ]"
      :key="c.title"
    >
      <div class="system-card-top">
        <div class="system-symbol" :class="['blue', 'teal', 'amber'][i]">
          <Icon :name="c.icon" :size="24" />
        </div>
        <span>{{ c.tag }}</span>
      </div>
      <small>{{ c.en }}</small>
      <h3>{{ c.title }}</h3>
      <p>{{ c.text }}</p>
      <div class="system-card-footer">
        <span
          ><b>{{ c.count }}</b> {{ c.unit }}</span
        ><button class="text-btn" @click="go(c.route)">
          进入系统<Icon name="ArrowRight" :size="16" />
        </button>
      </div>
    </article>
  </section>
  <div class="grid-2 dashboard-bottom">
    <section class="panel">
      <div class="panel-header">
        <div>
          <h3>保障任务</h3>
          <p>最近创建的业务任务及预案入口</p>
        </div>
        <button class="text-btn" @click="go('S301')">
          全部任务<Icon name="ChevronRight" :size="15" />
        </button>
      </div>
      <div v-if="!s.tasks.length" class="inline-empty">
        <Icon name="ClipboardList" :size="28" />
        <div>
          <b>工作空间已就绪</b>
          <p>创建首个任务，开始筹划与演练。</p>
        </div>
        <button class="btn secondary small" @click="go('S301')">
          新建任务
        </button>
      </div>
      <div v-else class="task-list">
        <button
          v-for="t in [...s.tasks].reverse().slice(0, 4)"
          :key="t.id"
          @click="go('S301', t.id)"
        >
          <div class="task-file"><Icon name="ClipboardList" /></div>
          <div>
            <b>{{ t.name }}</b
            ><span>{{ t.id }} · 截止 T+{{ t.deadline }} min</span>
          </div>
          <Badge :status="t.status" /><Icon name="ChevronRight" :size="16" />
        </button>
      </div>
    </section>
    <section class="panel">
      <div class="panel-header">
        <div>
          <h3>最近动态</h3>
          <p>业务变更与操作记录</p>
        </div>
        <span class="subtle-label">实时同步</span>
      </div>
      <div v-if="!events.length" class="inline-empty">
        <Icon name="History" :size="27" />
        <div>
          <b>等待首个业务操作</b>
          <p>这里将记录创建、训练、审批与运行过程。</p>
        </div>
      </div>
      <div v-else class="activity-list">
        <div v-for="e in events" :key="e.id">
          <span class="activity-dot"></span>
          <div>
            <b>{{ actionText[e.action] || e.action }}</b
            ><span>{{
              store.accounts.find((a) => a.id === e.actor)?.name || e.actor
            }}</span>
          </div>
          <time>{{ time(e.at) }}</time>
        </div>
      </div>
    </section>
  </div>
</template>

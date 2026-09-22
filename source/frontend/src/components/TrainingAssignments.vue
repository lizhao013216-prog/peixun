<script setup lang="ts">
import { computed, ref } from "vue";
import { useRouter } from "vue-router";
import { store, command, login, notify } from "../store";
import { pathFor, pathForFeature } from "../catalog";
import { systemName, type SystemId } from "../domain";
import Icon from "./Icon.vue";
import Badge from "./Badge.vue";
import Modal from "./Modal.vue";
const router = useRouter(),
  s = computed(() => store.data);
const props = defineProps<{ domain: SystemId }>();
const isLearner = computed(
  () => store.accounts.find((a) => a.id === store.actor)?.role === "LEARNER",
);
const tasks = computed(() =>
  [...s.value.assignments]
    .reverse()
    .filter(
      (a: any) =>
        (a.domain || courseFor(a)?.domain) === props.domain &&
        (!isLearner.value || a.learnerId === store.actor),
    ),
);
const starting = ref<any>(null),
  mode = ref("GUIDED"),
  scope = ref("INDIVIDUAL");
const selectedCourse = computed(() =>
  s.value.courses.find((c: any) => c.id === starting.value?.courseId),
);
const courseFor = (a: any) =>
  s.value.courses.find((c: any) => c.id === a.courseId);
const ongoing = (a: any) =>
  s.value.attempts.find(
    (t: any) =>
      t.assignmentId === a.id && ["RUNNING", "PAUSED"].includes(t.status),
  );
const last = (a: any) =>
  s.value.attempts.filter((t: any) => t.assignmentId === a.id).at(-1);
function open(a: any) {
  starting.value = a;
  mode.value = "GUIDED";
  scope.value = "INDIVIDUAL";
}
function enter(t: any) {
  store.selectedAttemptBySystem[props.domain] = t.id;
  router.push(pathFor("C05", t.id));
}
async function useLearner(id: string) {
  try {
    await login(id);
  } catch (e: any) {
    notify(e.message, "error");
  }
}
async function start() {
  const r = await command(
    "training.start",
    { assignmentId: starting.value.id, mode: mode.value, scope: scope.value },
    "训练已开始，请先选择本步操作对象",
  );
  if (r) {
    starting.value = null;
    enter(r);
  }
}
</script>
<template>
  <section class="business-intro">
    <div>
      <span class="task-eyebrow">学员学习入口</span>
      <h2>{{ isLearner ? "我的培训任务" : "培训任务与学员进度" }}</h2>
      <p>
        {{
          isLearner
            ? "选择分配给你的课程。开始前了解学习目标，进入后每一步都有操作说明和结果反馈。"
            : "教员分配课程后，学员从这里开始训练。演示时可先切换为对应学员，体验真实操作流程。"
        }}
      </p>
    </div>
    <div class="button-row">
      <button
        v-if="!isLearner"
        class="btn primary"
        @click="useLearner('LEARNER_A')"
      >
        以学员A进入</button
      ><button
        v-if="!isLearner"
        class="btn secondary"
        @click="router.push(pathForFeature(domain, 'coursewares'))"
      >
        制作 / 分配课件
      </button>
    </div>
  </section>
  <div class="business-stages">
    <span>1 选择任务</span><span>2 阅读任务说明</span
    ><span>3 逐步操作与反馈</span><span>4 查看成绩</span
    ><span>5 教员确认证据</span>
  </div>
  <div v-if="!tasks.length" class="panel empty-state">
    <Icon name="BookOpen" :size="32" />
    <h3>{{ isLearner ? "暂时没有分配给你的任务" : "先制作并发布一门课程" }}</h3>
    <p>
      {{
        isLearner
          ? "请教员在已发布课件中选择你的身份并分配任务。任务分配后会出现在这里。"
          : "课件发布后点击“分配培训任务”，选择学员，生成真实培训任务。"
      }}
    </p>
    <button
      v-if="!isLearner"
      class="btn primary"
      @click="router.push(pathForFeature(domain, 'coursewares'))"
    >
      前往制作课件
    </button>
  </div>
  <div class="training-task-grid">
    <article v-for="a in tasks" :key="a.id" class="panel training-task-card">
      <div class="panel-header">
        <span class="pill">{{ systemName(domain) }}</span
        ><Badge :status="ongoing(a)?.status || a.status" />
      </div>
      <div class="panel-body">
        <h3>{{ a.name }}</h3>
        <p>
          {{
            courseFor(a)?.description ||
            "按课程步骤完成模拟操作，提交成绩和过程记录。"
          }}
        </p>
        <div class="task-facts">
          <span
            >学员：{{
              store.accounts.find((u) => u.id === a.learnerId)?.name
            }}</span
          ><span
            >{{ courseFor(a)?.steps.length }} 个步骤 · V{{
              courseFor(a)?.version
            }}</span
          ><span v-if="ongoing(a)"
            >进度：已完成 {{ ongoing(a).currentStep }} /
            {{ courseFor(a)?.steps.length }}</span
          >
        </div>
        <div class="task-next">
          <b>现在该做什么</b>
          <p>
            {{
              ongoing(a)
                ? "点击继续训练，恢复上次步骤。已经通过的步骤和成绩会保留。"
                : last(a)
                  ? "查看最近一次训练结果；需要重新练习时可新建补训。"
                  : "先阅读课程目标、训练方式和评分规则，再开始第一个步骤。"
            }}
          </p>
        </div>
        <div class="button-row">
          <button
            v-if="ongoing(a)"
            class="btn primary"
            @click="enter(ongoing(a))"
          >
            继续训练</button
          ><button v-else class="btn primary" @click="open(a)">
            {{ last(a) ? "新建补训" : "查看任务并开始" }}</button
          ><button v-if="last(a)" class="btn secondary" @click="enter(last(a))">
            查看训练结果
          </button>
        </div>
      </div>
    </article>
  </div>
  <Modal v-if="starting" title="开始训练前，请确认" @close="starting = null"
    ><div class="form-stack">
      <div class="task-brief">
        <h3>{{ starting.name }}</h3>
        <p>
          {{
            selectedCourse?.description || "按步骤完成模拟操作，形成训练记录。"
          }}
        </p>
        <b
          >共 {{ selectedCourse?.steps.length }} 步 · 第一项：{{
            selectedCourse?.steps[0]?.name
          }}</b
        >
      </div>
      <div class="info-note">
        操作顺序：选择对象 → 点击具体动作按钮 → 阅读结果 →
        继续下一步。选错对象不通过本步，可按提示重试。
      </div>
      <label class="field"
        ><span>训练方式</span
        ><select v-model="mode">
          <option value="GUIDED">
            提示操作 · 显示对象、操作说明和预期结果（推荐）
          </option>
          <option value="FREE">自由操作 · 隐藏目标提示，自主选择对象</option>
          <option value="DEMONSTRATION">演示讲解 · 不计个人成绩</option>
        </select></label
      >
      <label v-if="mode !== 'DEMONSTRATION'" class="field"
        ><span>成绩归属</span
        ><select v-model="scope">
          <option value="INDIVIDUAL">个人训练 · 完成后可由教员确认证据</option>
          <option value="TEAM">团队训练 · 操作前需申请控制权</option>
        </select></label
      >
      <p>
        {{
          mode === "DEMONSTRATION"
            ? "本次演示不计个人成绩。"
            : "满分100分；每次错误扣5分，主动查看提示扣2分；平台故障不扣分。"
        }}
      </p>
      <div v-if="store.actor !== starting.learnerId" class="info-note warning">
        当前不是分配学员。建议切换身份后开始，以便展示学员视角。
      </div>
    </div>
    <template #footer
      ><button
        v-if="store.actor !== starting.learnerId"
        class="btn secondary"
        @click="useLearner(starting.learnerId)"
      >
        切换为分配学员</button
      ><button class="btn primary" :disabled="store.busy > 0" @click="start">
        开始训练
      </button></template
    ></Modal
  >
</template>

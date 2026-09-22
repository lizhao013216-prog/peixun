<script setup lang="ts">
import { computed, ref, watch, nextTick } from "vue";
import { useRoute, useRouter } from "vue-router";
import { store, command, download, login, notify } from "../store";
import { pathFor } from "../catalog";
import { lessonStep, objectName } from "../lesson";
import LessonStep from "./LessonStep.vue";
import Icon from "./Icon.vue";
import Badge from "./Badge.vue";
import Modal from "./Modal.vue";
const route = useRoute(),
  router = useRouter(),
  s = computed(() => store.data);
const available = computed(() =>
  s.value.attempts.filter(
    (a: any) =>
      !store.actor.startsWith("LEARNER") ||
      a.learnerId === store.actor ||
      a.scope === "TEAM",
  ),
);
const attempt = computed(() => {
  const id = String(route.params.id || "current");
  return id !== "current"
    ? s.value.attempts.find((a: any) => a.id === id)
    : available.value.find((a: any) => a.id === store.selectedAttempt) ||
        available.value.at(-1);
});
const course = computed(() =>
  s.value.courses.find((c: any) => c.id === attempt.value?.courseId),
);
const assignment = computed(() =>
  s.value.assignments.find((a: any) => a.id === attempt.value?.assignmentId),
);
const lastEvent = computed(() => attempt.value?.events.at(-1));
const acknowledged = ref("");
const needsNext = computed(
  () =>
    attempt.value?.status !== "COMPLETED" &&
    lastEvent.value?.kind === "PASS" &&
    acknowledged.value !== lastEvent.value.id,
);
const index = computed(() =>
  Math.max(0, attempt.value?.currentStep - (needsNext.value ? 1 : 0)),
);
const current = computed(() => lessonStep(course.value?.steps[index.value]));
const confirmed = computed(
  () =>
    assignment.value?.evidenceAttemptId === attempt.value?.id &&
    assignment.value?.confirmed,
);
const teacher = computed(
  () => store.accounts.find((a) => a.id === store.actor)?.role === "INSTRUCTOR",
);
const feedbackOpen = ref(false),
  rating = ref(4),
  comment = ref(""),
  recordsOpen = ref(false);
const feedbackDone = computed(() =>
  s.value.feedback.some((f: any) => f.attemptId === attempt.value?.id),
);
const resultTitles: Record<string, string> = {
  PASS: "操作成功 · 本步已通过",
  ERROR: "本步未通过 · 请修正后重试",
  HELP: "操作提示已打开",
  TECHNICAL_FAILURE: "平台响应失败 · 可重试，不扣分",
};
const canOperate = computed(
  () =>
    !store.actor.startsWith("LEARNER") ||
    store.actor === attempt.value?.learnerId ||
    attempt.value?.scope === "TEAM",
);
watch(
  () => attempt.value?.id,
  () => (acknowledged.value = ""),
);
async function submit(payload: any) {
  const a = attempt.value;
  const r = await command(
    "training.action",
    { id: a.id, kind: "PASS", stepId: current.value.id, ...payload },
    "",
  );
  if (r) {
    await nextTick();
    document
      .querySelector(".training-feedback")
      ?.scrollIntoView({ block: "nearest", behavior: "smooth" });
  }
}
async function help() {
  await command("training.action", { id: attempt.value.id, kind: "HELP" }, "");
}
function next() {
  acknowledged.value = lastEvent.value.id;
}
async function useTeacher() {
  try {
    await login("INSTRUCTOR");
    notify("已切换教员，可检查记录并确认证据");
  } catch (e: any) {
    notify(e.message, "error");
  }
}
async function feedback() {
  const r = await command(
    "training.feedback",
    { id: attempt.value.id, rating: rating.value, comment: comment.value },
    "课程反馈已保存",
  );
  if (r) feedbackOpen.value = false;
}
</script>
<template>
  <section v-if="!attempt || !course" class="panel empty-state">
    <Icon name="BookOpen" :size="32" />
    <h1>先选择一个培训任务</h1>
    <p>从分配给你的任务开始，系统会显示课程目标和第一个操作步骤。</p>
    <button class="btn primary" @click="router.push(pathFor('S203'))">
      查看我的培训任务
    </button>
  </section>
  <template v-else>
    <div class="toolbar">
      <button class="text-btn" @click="router.push(pathFor('S203'))">
        ← 返回培训任务</button
      ><span class="spacer"></span><Badge :status="attempt.status" /><button
        class="btn secondary small"
        @click="recordsOpen = !recordsOpen"
      >
        {{ recordsOpen ? "收起操作记录" : "查看操作记录" }}
      </button>
    </div>
    <section class="student-heading">
      <div>
        <span class="task-eyebrow"
          >{{ attempt.domain === "OPERATION" ? "操作训练" : "维修培训" }} · V{{
            attempt.courseVersion
          }}</span
        >
        <h1>{{ course.name }}</h1>
        <p>
          {{
            course.description || "按课程步骤完成模拟操作，形成训练过程和成绩。"
          }}
        </p>
      </div>
      <div class="training-counter">
        <b
          >{{ attempt.currentStep
          }}<small> / {{ course.steps.length }}</small></b
        ><span>步骤已完成</span>
      </div>
    </section>
    <div class="training-progress">
      <div
        :style="{
          width: `${(attempt.currentStep / course.steps.length) * 100}%`,
        }"
      ></div>
    </div>
    <section
      v-if="lastEvent"
      class="training-feedback"
      :class="lastEvent.kind"
      role="status"
      aria-live="polite"
    >
      <Icon
        :name="
          lastEvent.kind === 'ERROR' || lastEvent.kind === 'TECHNICAL_FAILURE'
            ? 'CircleAlert'
            : 'CircleCheck'
        "
        :size="26"
      />
      <div>
        <b>{{ resultTitles[lastEvent.kind] || "操作已记录" }}</b>
        <p>
          {{ lastEvent.name || lastEvent.step
          }}<template v-if="lastEvent.target">
            · 选择了{{ objectName(lastEvent.target) }}</template
          ><template v-if="lastEvent.actionLabel && lastEvent.kind === 'PASS'">
            · {{ lastEvent.actionLabel }}</template
          >
        </p>
        <p class="feedback-main">
          {{
            lastEvent.message ||
            (lastEvent.kind === "PASS"
              ? "本步骤操作已完成，记录已保存。"
              : "请检查当前步骤要求后重试。")
          }}
        </p>
        <small v-if="lastEvent.kind === 'ERROR'"
          >本步进度未前进。{{
            attempt.scope === "NONE"
              ? "本次为不计分演示。"
              : "本次错误扣5分，可修正后重试。"
          }}</small
        ><small v-else-if="lastEvent.kind === 'HELP'">{{
          attempt.scope === "NONE"
            ? "本次为不计分演示。"
            : "本次主动帮助扣2分。"
        }}</small>
      </div>
      <button v-if="needsNext" class="btn primary" @click="next">
        继续下一步：{{ course.steps[attempt.currentStep]?.name
        }}<Icon name="ArrowRight" :size="16" />
      </button>
    </section>
    <div v-if="attempt.status === 'COMPLETED'" class="panel training-complete">
      <span class="task-eyebrow">训练完成</span>
      <h2>全部步骤已完成，接下来查看成绩与培训证据。</h2>
      <div class="completion-metrics">
        <div>
          <b>{{ attempt.score ?? "不计分" }}</b
          ><span>{{ attempt.scope === "TEAM" ? "团队成绩" : "本次成绩" }}</span>
        </div>
        <div>
          <b>{{ attempt.errors }}</b
          ><span>错误次数</span>
        </div>
        <div>
          <b>{{ attempt.helps }}</b
          ><span>帮助次数</span>
        </div>
        <div>
          <b>{{ attempt.correctRate }}%</b><span>正确率（不含帮助）</span>
        </div>
      </div>
      <p v-if="attempt.scope !== 'NONE'">
        成绩 = 已通过步骤占比 × 100 − 错误次数 × 5 − 帮助次数 × 2，最低0分。
      </p>
      <div class="button-row">
        <button class="btn primary" @click="feedbackOpen = true">
          {{ feedbackDone ? "补充课程反馈" : "填写课程反馈" }}</button
        ><button
          class="btn secondary"
          @click="download('attempts', attempt.id)"
        >
          导出训练报告</button
        ><button class="btn secondary" @click="router.push(pathFor('S203'))">
          返回我的任务
        </button>
      </div>
      <div class="evidence-next">
        <Icon name="GraduationCap" :size="26" />
        <div>
          <b>{{
            confirmed
              ? "教员已确认本次培训证据"
              : attempt.scope !== "INDIVIDUAL"
                ? "本次不生成个人培训证据"
                : "下一环节：等待教员检查并确认证据"
          }}</b>
          <p>
            {{
              confirmed
                ? "培训证据已保存；关联保障预案的课程会回填准备状态。"
                : attempt.scope !== "INDIVIDUAL"
                  ? "团队训练和演示讲解不能代替个人培训记录。"
                  : "教员查看你的成绩和操作记录后确认。学员完成训练与教员确认是两个独立步骤。"
            }}
          </p>
        </div>
        <button
          v-if="!confirmed && attempt.scope === 'INDIVIDUAL' && teacher"
          class="btn primary"
          :disabled="store.busy > 0"
          @click="
            command(
              'training.confirm',
              { id: attempt.id },
              '已确认本次培训证据',
            )
          "
        >
          确认证据</button
        ><button
          v-else-if="!confirmed && attempt.scope === 'INDIVIDUAL'"
          class="btn secondary"
          @click="useTeacher"
        >
          演示：切换教员验收
        </button>
      </div>
    </div>
    <template v-else>
      <div v-if="attempt.status === 'PAUSED'" class="info-note warning">
        <b>训练已暂停，当前进度已保存。</b
        ><button
          class="btn primary"
          @click="command('training.pause', { id: attempt.id }, '训练已继续')"
        >
          继续训练
        </button>
      </div>
      <div v-if="!canOperate" class="info-note warning">
        该任务分配给其他学员，当前身份只能查看。请在右上角选择对应身份。
      </div>
      <div class="student-body">
        <aside class="panel student-outline">
          <div class="panel-header"><h3>本课步骤</h3></div>
          <ol>
            <li
              v-for="(st, i) in course.steps"
              :key="st.id"
              :class="{ passed: i < attempt.currentStep, current: i === index }"
            >
              <span>{{ Number(i) + 1 }}</span>
              <div>
                <b>{{ st.name }}</b
                ><small>{{
                  i < attempt.currentStep
                    ? "已通过"
                    : i === attempt.currentStep
                      ? "待完成"
                      : "尚未开始"
                }}</small>
              </div>
              <Icon v-if="i < attempt.currentStep" name="Check" :size="16" />
            </li>
          </ol>
        </aside>
        <section class="panel student-current">
          <div class="panel-header">
            <div>
              <span class="task-eyebrow"
                >第 {{ index + 1 }} / {{ course.steps.length }} 步</span
              >
              <h3>
                {{
                  needsNext
                    ? "本步已完成，请阅读结果后继续"
                    : "现在请完成下面这项操作"
                }}
              </h3>
            </div>
            <span class="pill">{{
              attempt.mode === "FREE"
                ? "自由操作"
                : attempt.mode === "DEMONSTRATION"
                  ? "演示讲解 · 不计分"
                  : "提示操作"
            }}</span>
          </div>
          <LessonStep
            :key="`${attempt.id}:${index}`"
            :step="current"
            :state="attempt.entityState"
            :free="attempt.mode === 'FREE'"
            :disabled="
              store.busy > 0 ||
              needsNext ||
              attempt.status !== 'RUNNING' ||
              !canOperate
            "
            @submit="submit"
          />
          <div class="student-controls">
            <span
              >已通过 {{ attempt.currentStep }} 步 · 错误
              {{ attempt.errors }} 次 · 帮助 {{ attempt.helps }} 次</span
            >
            <div class="button-row">
              <button
                class="btn secondary small"
                :disabled="store.busy > 0 || !canOperate"
                @click="command('training.pause', { id: attempt.id })"
              >
                {{
                  attempt.status === "PAUSED" ? "继续训练" : "暂停并保存进度"
                }}</button
              ><button
                class="btn secondary small"
                :disabled="
                  store.busy > 0 ||
                  needsNext ||
                  attempt.status !== 'RUNNING' ||
                  !canOperate
                "
                @click="help"
              >
                {{
                  attempt.scope === "NONE"
                    ? "查看操作提示"
                    : "查看操作提示（扣2分）"
                }}
              </button>
            </div>
          </div>
        </section>
      </div>
      <section
        v-if="attempt.scope === 'TEAM'"
        class="panel panel-body team-controls"
      >
        <h3>团队协作</h3>
        <p>
          团队操作前需申请对象控制权，有效30秒。当前模式：{{
            attempt.interactionMode === "REVIEW" ? "仅主持人操作" : "成员交互"
          }}。
        </p>
        <div class="button-row">
          <button
            class="btn secondary"
            @click="command('training.join', { id: attempt.id }, '已加入会话')"
          >
            加入会话</button
          ><button
            class="btn primary"
            @click="
              command(
                'training.control',
                { id: attempt.id },
                '已取得30秒控制权',
              )
            "
          >
            申请控制权</button
          ><button
            class="btn secondary"
            @click="
              command('training.collaboration', {
                id: attempt.id,
                mode:
                  attempt.interactionMode === 'REVIEW'
                    ? 'INTERACTIVE'
                    : 'REVIEW',
              })
            "
          >
            切换协同模式
          </button>
        </div>
      </section>
    </template>
    <section v-if="recordsOpen" class="panel">
      <div class="panel-header"><h3>操作记录 · 每次提交均保留</h3></div>
      <div class="table-scroll">
        <table class="data-table">
          <thead>
            <tr>
              <th>步骤</th>
              <th>操作</th>
              <th>结果说明</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="ev in attempt.events" :key="ev.id">
              <td>{{ ev.step }} · {{ ev.name }}</td>
              <td>
                {{ objectName(ev.target) }}<br />{{ ev.actionLabel || ev.kind }}
              </td>
              <td>{{ ev.message || resultTitles[ev.kind] }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </template>
  <Modal
    v-if="feedbackOpen"
    title="课程体验与反馈"
    @close="feedbackOpen = false"
    ><div class="form-stack">
      <label class="field"
        ><span>课程评分</span
        ><select v-model="rating">
          <option v-for="n in 5" :value="n" :key="n">{{ n }} 分</option>
        </select></label
      ><label class="field"
        ><span>哪里还不够清楚？</span
        ><textarea
          v-model="comment"
          placeholder="例如：某一步的对象、操作说明或结果不够清晰"
        />
      </label>
    </div>
    <template #footer
      ><button class="btn primary" :disabled="store.busy > 0" @click="feedback">
        提交反馈
      </button></template
    ></Modal
  >
</template>

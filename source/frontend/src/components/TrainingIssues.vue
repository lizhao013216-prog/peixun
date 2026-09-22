<script setup lang="ts">
import { computed, reactive, watch } from "vue";
import { command, store } from "../store";
import type { SystemId } from "../domain";
import Badge from "./Badge.vue";
import Empty from "./Empty.vue";
import Icon from "./Icon.vue";

const props = defineProps<{ domain: SystemId }>();
const role = computed(
  () => store.accounts.find((item) => item.id === store.actor)?.role || "",
);
const issues = computed(() =>
  [...(store.data.issues || [])]
    .filter(
      (item: any) =>
        item.domain === props.domain &&
        (role.value !== "LEARNER" || item.learnerId === store.actor),
    )
    .reverse(),
);
const taskDrafts = reactive<Record<string, any>>({});
const opinions = reactive<Record<string, string>>({});

watch(
  issues,
  (items) => {
    for (const item of items) {
      taskDrafts[item.id] ||= {
        name: `${item.name}整改保障任务`,
        object: "泵组设备",
        scope: item.description || "核实并处理训练中发现的装备保障问题",
        taskType: "故障处置",
        dueMinutes: 240,
        constraints: "需保留问题来源和核实结论",
      };
      opinions[item.id] ||= "已核实问题现象与训练记录，进入后续处理。";
    }
  },
  { immediate: true },
);

const typeNames: Record<string, string> = {
  CONTENT: "课程内容",
  LEARNING: "学习掌握",
  PLATFORM: "平台技术",
  EQUIPMENT_SUPPORT: "疑似装备保障",
};
const statusNames: Record<string, string> = {
  TRIAGE: "待分流",
  IN_PROGRESS: "处理中",
  RESOLVED: "已解决",
  CLOSED: "已关闭",
};
const canTeach = computed(() => ["INSTRUCTOR", "ADMIN"].includes(role.value));
const canPlan = computed(() => ["PLANNER", "ADMIN"].includes(role.value));

async function routeIssue(issue: any) {
  await command(
    "issue.route",
    {
      id: issue.id,
      opinion: opinions[issue.id],
    },
    issue.type === "CONTENT"
      ? "已建立课件修订草稿"
      : issue.type === "LEARNING"
        ? "已建立补训任务，原成绩保持不变"
        : "已登记技术处理单，不扣减学习成绩",
  );
}
async function verify(issue: any) {
  await command(
    "issue.verify",
    { id: issue.id, opinion: opinions[issue.id] },
    "疑似保障问题已核实",
  );
}
async function createTask(issue: any) {
  await command(
    "issue.route",
    { id: issue.id, ...taskDrafts[issue.id] },
    "已创建正式保障任务并保留来源链",
  );
}
async function resolve(issue: any, close = false) {
  await command(
    close ? "issue.close" : "issue.resolve",
    { id: issue.id, opinion: opinions[issue.id] },
    close ? "问题已关闭" : "问题已解决",
  );
}
</script>

<template>
  <section class="business-intro">
    <div>
      <span class="task-eyebrow">训练问题闭环 · 可选流程</span>
      <h2>训练可以正常完成和归档，发现的问题再按类型分流。</h2>
      <p>
        内容问题进入课件修订，学习问题进入补训，平台问题进入技术处理；疑似装备问题须经保障人员核实后才能生成正式任务。
      </p>
    </div>
  </section>

  <section v-if="issues.length" class="issue-grid">
    <article v-for="issue in issues" :key="issue.id" class="panel">
      <div class="panel-header">
        <div>
          <span class="task-eyebrow">{{ typeNames[issue.type] }} · {{ issue.id }}</span>
          <h3>{{ issue.name }}</h3>
        </div>
        <Badge :status="issue.status" />
      </div>
      <div class="panel-body form-stack">
        <p>{{ issue.description || "未填写补充说明" }}</p>
        <div class="task-facts">
          <span>状态：{{ statusNames[issue.status] || issue.status }}</span>
          <span>训练：{{ issue.sourceAttemptId }}</span>
          <span>课件：{{ issue.sourceCourseRef?.id }} V{{ issue.sourceCourseRef?.version }}</span>
          <span v-if="issue.resolutionId">处理结果：{{ issue.resolutionId }}</span>
          <span v-if="issue.linkedTaskId">正式任务：{{ issue.linkedTaskId }}</span>
        </div>

        <label
          v-if="role !== 'LEARNER' && issue.status !== 'CLOSED'"
          class="field"
        >
          <span>处理意见</span>
          <textarea v-model="opinions[issue.id]" />
        </label>

        <div
          v-if="
            issue.type === 'EQUIPMENT_SUPPORT' &&
            issue.verified &&
            !issue.linkedTaskId &&
            canPlan
          "
          class="form-grid"
        >
          <label class="field"><span>任务名称</span><input v-model="taskDrafts[issue.id].name" /></label>
          <label class="field"><span>保障对象</span><input v-model="taskDrafts[issue.id].object" /></label>
          <label class="field"><span>任务类型</span><input v-model="taskDrafts[issue.id].taskType" /></label>
          <label class="field"><span>完成时限（分钟）</span><input v-model.number="taskDrafts[issue.id].dueMinutes" type="number" min="1" max="10080" /></label>
          <label class="field wide"><span>任务范围</span><textarea v-model="taskDrafts[issue.id].scope" /></label>
          <label class="field wide"><span>约束条件</span><textarea v-model="taskDrafts[issue.id].constraints" /></label>
        </div>

        <div class="button-row">
          <button
            v-if="
              issue.status === 'TRIAGE' &&
              issue.type !== 'EQUIPMENT_SUPPORT' &&
              canTeach
            "
            class="btn primary"
            :disabled="store.busy > 0"
            @click="routeIssue(issue)"
          >
            {{
              issue.type === "CONTENT"
                ? "创建课件修订"
                : issue.type === "LEARNING"
                  ? "分配补训"
                  : "登记技术处理"
            }}
          </button>
          <button
            v-if="
              issue.type === 'EQUIPMENT_SUPPORT' &&
              !issue.verified &&
              canPlan
            "
            class="btn primary"
            :disabled="store.busy > 0 || !opinions[issue.id]?.trim()"
            @click="verify(issue)"
          >
            核实疑似保障问题
          </button>
          <button
            v-if="
              issue.type === 'EQUIPMENT_SUPPORT' &&
              issue.verified &&
              !issue.linkedTaskId &&
              canPlan
            "
            class="btn primary"
            :disabled="
              store.busy > 0 ||
              !taskDrafts[issue.id].object?.trim() ||
              !taskDrafts[issue.id].scope?.trim() ||
              !taskDrafts[issue.id].taskType?.trim()
            "
            @click="createTask(issue)"
          >
            确认并创建正式保障任务
          </button>
          <button
            v-if="
              issue.status === 'IN_PROGRESS' &&
              (canTeach || canPlan)
            "
            class="btn secondary"
            :disabled="store.busy > 0 || !opinions[issue.id]?.trim()"
            @click="resolve(issue)"
          >
            标记已解决
          </button>
          <button
            v-if="issue.status === 'RESOLVED' && (canTeach || canPlan)"
            class="btn secondary"
            :disabled="store.busy > 0 || !opinions[issue.id]?.trim()"
            @click="resolve(issue, true)"
          >
            关闭问题
          </button>
        </div>

        <details v-if="issue.history?.length">
          <summary>查看状态历史（{{ issue.history.length }}）</summary>
          <div v-for="item in issue.history" :key="`${item.at}:${item.status}`" class="list-card compact">
            <Icon name="GitBranch" :size="18" />
            <div><b>{{ statusNames[item.status] || item.status }}</b><p>{{ item.reason }} · {{ item.actor }}</p></div>
          </div>
        </details>
      </div>
    </article>
  </section>
  <Empty
    v-else
    title="当前没有训练问题"
    description="训练完成与归档不依赖问题单；确有问题时，学员可从训练结果页自愿登记。"
    icon="CircleCheck"
  />
</template>

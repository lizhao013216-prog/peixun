<script setup lang="ts">
import { computed } from "vue";
import { useRouter } from "vue-router";
import { store, command, download, fmt } from "../store";
import { pathFor, pathForFeature, type PageMeta } from "../catalog";
import { systemName, type SystemId } from "../domain";
import Icon from "../components/Icon.vue";
import Empty from "../components/Empty.vue";
import TrainingAssignments from "../components/TrainingAssignments.vue";
import TrainingPlayer from "../components/TrainingPlayer.vue";

const props = defineProps<{ page: PageMeta }>();
const router = useRouter();
const s = computed(() => store.data);
const domain = computed<SystemId>(() => props.page.system || "MAINTENANCE");
const results = computed(() =>
  s.value.attempts.filter(
    (a: any) => a.status === "COMPLETED" && a.domain === domain.value,
  ),
);
const personalResults = computed(() =>
  results.value.filter((a: any) => a.scope === "INDIVIDUAL"),
);
const assignments = computed(() =>
  s.value.assignments.filter((a: any) => a.domain === domain.value),
);
const feedback = computed(() =>
  s.value.feedback.filter((f: any) => f.domain === domain.value),
);

async function revise(id: string) {
  const r = await command("course.revise", { id }, "已建立课程改进草稿");
  if (r) {
    const revisedDomain = r.domain as SystemId;
    store.selectedCourseBySystem[revisedDomain] = r.id;
    router.push(pathForFeature(revisedDomain, "coursewares", r.id));
  }
}

function openAttempt(a: any) {
  store.selectedAttemptBySystem[domain.value] = a.id;
  router.push(pathFor("C05", a.id));
}
</script>

<template>
  <TrainingAssignments v-if="page.feature === 'assignments'" :domain="domain" />
  <section
    v-else-if="page.feature === 'training-scope'"
    class="panel empty-state"
  >
    <Icon name="BookOpenCheck" :size="32" />
    <span class="task-eyebrow">{{ systemName(domain) }} · P1 范围说明</span>
    <h1>教学训练入口已纳入本系统，但完整业务尚未开放。</h1>
    <p>
      当前阶段只建立独立入口和业务边界，不复用装备维修保障的课程、任务、评价与记录。
      按执行指南，保障教学训练完整闭环将在 P5 阶段实施。
    </p>
  </section>
  <template v-else-if="page.feature === 'training-evaluations'">
    <section class="business-intro">
      <div>
        <span class="task-eyebrow">{{ systemName(domain) }} · 教员验收</span>
        <h2>检查本系统训练结果，再确认本次培训证据。</h2>
        <p>
          先查看成绩和过程详情，再确认个人训练证据；各子系统的课程、任务与评价互不混用。
        </p>
      </div>
    </section>
    <div class="metric-strip">
      <div class="metric">
        <span>完成的个人训练</span
        ><b>{{ personalResults.length }}<small>次</small></b>
        <p>不含演示与团队成绩</p>
      </div>
      <div class="metric">
        <span>平均得分</span
        ><b
          >{{
            personalResults.length
              ? fmt(
                  personalResults.reduce(
                    (n: number, a: any) => n + a.score,
                    0,
                  ) / personalResults.length,
                  1,
                )
              : "—"
          }}<small>分</small></b
        >
        <p>来源于本系统有效步骤事件</p>
      </div>
      <div class="metric">
        <span>已确认培训证据</span
        ><b
          >{{ assignments.filter((a: any) => a.confirmed).length
          }}<small>份</small></b
        >
        <p>仅确认本系统本次培训完成</p>
      </div>
      <div class="metric">
        <span>课程反馈</span><b>{{ feedback.length }}<small>条</small></b>
        <p>问卷与操作成绩分列</p>
      </div>
    </div>
    <section class="panel">
      <div class="panel-header">
        <h3>训练结果</h3>
        <span class="subtle-label">每次尝试保留独立课程版本</span>
      </div>
      <div v-if="results.length" class="table-scroll">
        <table class="data-table">
          <thead>
            <tr>
              <th>课程</th>
              <th>评价范围</th>
              <th>得分</th>
              <th>正确率</th>
              <th>错误 / 帮助</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="a in [...results].reverse()" :key="a.id">
              <td>
                <b>{{ a.courseName }}</b
                ><small>V{{ a.courseVersion }} · {{ a.id }}</small>
              </td>
              <td>
                {{
                  a.scope === "INDIVIDUAL"
                    ? "个人"
                    : a.scope === "TEAM"
                      ? "团队"
                      : "演示"
                }}
              </td>
              <td>
                <b>{{ a.score ?? "不适用" }}</b>
              </td>
              <td>{{ a.scope === "NONE" ? "—" : `${a.correctRate}%` }}</td>
              <td>{{ a.errors }} / {{ a.helps }}</td>
              <td>
                <div class="actions">
                  <button class="text-btn" @click="openAttempt(a)">
                    过程详情
                  </button>
                  <button
                    v-if="
                      a.scope === 'INDIVIDUAL' &&
                      !assignments.some(
                        (task: any) =>
                          task.evidenceAttemptId === a.id && task.confirmed,
                      ) &&
                      store.accounts.find((u) => u.id === store.actor)?.role ===
                        'INSTRUCTOR'
                    "
                    class="text-btn"
                    @click="
                      command(
                        'training.confirm',
                        { id: a.id },
                        '教员已确认本次培训证据',
                      )
                    "
                  >
                    确认证据
                  </button>
                  <button class="text-btn" @click="download('attempts', a.id)">
                    导出MD
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <Empty
        v-else
        title="等待首个训练结果"
        description="完成一门已分配的课程，系统将按操作事件计算成绩。"
        icon="GraduationCap"
      >
        <button
          class="btn primary"
          @click="router.push(pathForFeature(domain, 'assignments'))"
        >
          查看培训任务
        </button>
      </Empty>
    </section>
    <section class="panel">
      <div class="panel-header"><h3>问卷与改进反馈</h3></div>
      <div class="panel-body">
        <div v-for="f in feedback" :key="f.id" class="list-card">
          <div class="resource-icon"><Icon name="MessageSquareText" /></div>
          <div>
            <b>课程体验 {{ f.rating }} / 5</b>
            <p>{{ f.comment || "未填写文字意见" }} · {{ f.attemptId }}</p>
          </div>
          <button class="text-btn" @click="revise(f.courseId)">
            创建课程修订<Icon name="ArrowRight" :size="14" />
          </button>
        </div>
        <p v-if="!feedback.length" class="empty-small">
          学员完成后可提交问卷，反馈可以关联新的课程版本。
        </p>
      </div>
    </section>
  </template>
  <TrainingPlayer v-else />
</template>

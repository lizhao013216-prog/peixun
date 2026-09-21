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
import SceneView from "../components/SceneView.vue";
const props = defineProps<{ page: any }>(),
  route = useRoute(),
  router = useRouter(),
  s = computed(() => store.data);
const startAssignment = ref<any>(null),
  mode = ref("GUIDED"),
  scope = ref("INDIVIDUAL"),
  questionnaire = ref(false),
  rating = ref(4),
  comment = ref(""),
  target = ref("PUMP-01"),
  showEvents = ref(false);
const attempt = computed(() =>
  selected(s.value.attempts, store.selectedAttempt || String(route.params.id)),
);
const course = computed(() =>
  attempt.value
    ? s.value.courses.find((c: any) => c.id === attempt.value.courseId)
    : null,
);
const step = computed(() => course.value?.steps[attempt.value?.currentStep]);
const results = computed(() =>
  s.value.attempts.filter((a: any) => a.status === "COMPLETED"),
);
const personalResults = computed(() =>
  results.value.filter((a: any) => a.scope === "INDIVIDUAL"),
);
async function start() {
  const r = await command(
    "training.start",
    {
      assignmentId: startAssignment.value.id,
      mode: mode.value,
      scope: scope.value,
    },
    "训练会话已开始",
  );
  if (r) {
    store.selectedAttempt = r.id;
    startAssignment.value = null;
    router.push(pathFor("C05", r.id));
  }
}
async function act(kind: string) {
  await command(
    "training.action",
    {
      id: attempt.value.id,
      kind: kind === "PASS" && target.value !== "PUMP-01" ? "ERROR" : kind,
      stepId: step.value?.id,
      target: target.value,
    },
    kind === "HELP" ? "帮助已记录；请按当前步骤说明继续" : "",
  );
}
async function evaluate() {
  const r = await command(
    "training.feedback",
    { id: attempt.value.id, rating: rating.value, comment: comment.value },
    "问卷反馈已保存",
  );
  if (r) questionnaire.value = false;
}
async function revise(cId: string) {
  const r = await command("course.revise", { id: cId }, "已建立课程改进草稿");
  if (r) {
    store.selectedCourse = r.id;
    router.push(pathFor(r.domain === "OPERATION" ? "S104" : "S202", r.id));
  }
}
async function switchActor(id: string) {
  await login(id);
  notify("已切换演示身份");
}
</script>
<template>
  <template v-if="page.id === 'S203'"
    ><div class="toolbar">
      <p>已发布课件分配给学员，完成个人训练后由教员确认证据。</p>
      <span class="spacer"></span
      ><button class="btn secondary" @click="switchActor('INSTRUCTOR')">
        切换教员</button
      ><button class="btn primary" @click="router.push(pathFor('S202'))">
        <Icon name="Plus" :size="15" />选择课件分配
      </button>
    </div>
    <section class="panel">
      <div class="panel-header">
        <h3>培训任务清单</h3>
        <span class="subtle-label">{{ s.assignments.length }} 项任务</span>
      </div>
      <div v-if="s.assignments.length" class="table-scroll">
        <table class="data-table">
          <thead>
            <tr>
              <th>课程与版本</th>
              <th>分配学员</th>
              <th>训练状态</th>
              <th>培训证据</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="a in [...s.assignments].reverse()" :key="a.id">
              <td>
                <b>{{ a.name }}</b
                ><small>{{ a.id }}</small>
              </td>
              <td>
                {{
                  a.learnerId === "LEARNER_A" ? "李工 · 学员A" : "王工 · 学员B"
                }}
              </td>
              <td><Badge :status="a.status" /></td>
              <td><Badge :status="a.confirmed ? 'CONFIRMED' : 'PENDING'" /></td>
              <td>
                <div class="actions">
                  <button class="text-btn" @click="startAssignment = a">
                    {{
                      a.status === "COMPLETED" ? "新建补训" : "开始训练"
                    }}</button
                  ><button
                    v-if="s.attempts.some((t: any) => t.assignmentId === a.id)"
                    class="text-btn"
                    @click="
                      store.selectedAttempt = s.attempts
                        .filter((t: any) => t.assignmentId === a.id)
                        .at(-1).id;
                      router.push(pathFor('C05', store.selectedAttempt));
                    "
                  >
                    查看尝试
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <Empty
        v-else
        title="还没有培训任务"
        description="先完成课件制作与发布，然后分配给学员。"
        icon="Users"
        ><button class="btn primary" @click="router.push(pathFor('S202'))">
          前往维修课件
        </button></Empty
      >
    </section>
    <section class="panel">
      <div class="panel-header"><h3>已发布课程</h3></div>
      <div class="panel-body">
        <div
          v-for="c in s.courses.filter((c: any) => c.status === 'PUBLISHED')"
          :key="c.id"
          class="list-card"
        >
          <div class="resource-icon"><Icon name="BookOpenCheck" /></div>
          <div>
            <b>{{ c.name }}</b>
            <p>
              {{ c.domain === "OPERATION" ? "操作训练" : "维修培训" }} · V{{
                c.version
              }}
              · {{ c.steps.length }} 个步骤
            </p>
          </div>
          <button
            class="btn secondary small"
            @click="
              command(
                'training.assign',
                { courseId: c.id, learnerId: 'LEARNER_A' },
                '已分配给学员A',
              )
            "
          >
            分配学员A
          </button>
        </div>
        <p
          v-if="!s.courses.some((c: any) => c.status === 'PUBLISHED')"
          class="empty-small"
        >
          发布成功的课件将在此显示。
        </p>
      </div>
    </section></template
  >
  <template v-else-if="page.id === 'S204'"
    ><div class="metric-strip">
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
        <p>来源于有效步骤事件</p>
      </div>
      <div class="metric">
        <span>已确认培训证据</span
        ><b
          >{{ s.assignments.filter((a: any) => a.confirmed).length
          }}<small>份</small></b
        >
        <p>仅确认本次培训完成</p>
      </div>
      <div class="metric">
        <span>课程反馈</span><b>{{ s.feedback.length }}<small>条</small></b>
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
                  <button
                    class="text-btn"
                    @click="
                      store.selectedAttempt = a.id;
                      router.push(pathFor('C05', a.id));
                    "
                  >
                    过程详情</button
                  ><button
                    v-if="a.scope === 'INDIVIDUAL'"
                    class="text-btn"
                    @click="
                      command(
                        'training.confirm',
                        { id: a.id },
                        '教员已确认本次培训证据',
                      )
                    "
                  >
                    确认证据</button
                  ><button class="text-btn" @click="download('attempts', a.id)">
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
        ><button class="btn primary" @click="router.push(pathFor('S203'))">
          查看培训任务
        </button></Empty
      >
    </section>
    <section class="panel">
      <div class="panel-header"><h3>问卷与改进反馈</h3></div>
      <div class="panel-body">
        <div v-for="f in s.feedback" :key="f.id" class="list-card">
          <div class="resource-icon"><Icon name="MessageSquareText" /></div>
          <div>
            <b>课程体验 {{ f.rating }} / 5</b>
            <p>{{ f.comment || "未填写文字意见" }} · {{ f.attemptId }}</p>
          </div>
          <button class="text-btn" @click="revise(f.courseId)">
            创建课程修订<Icon name="ArrowRight" :size="14" />
          </button>
        </div>
        <p v-if="!s.feedback.length" class="empty-small">
          学员完成后可提交问卷，反馈可以关联新的课程版本。
        </p>
      </div>
    </section></template
  >
  <template v-else
    ><div class="toolbar">
      <select
        v-if="s.attempts.length"
        v-model="store.selectedAttempt"
        aria-label="训练尝试"
      >
        <option value="">最近一次训练</option>
        <option v-for="a in s.attempts" :key="a.id" :value="a.id">
          {{ a.courseName }} · {{ a.id }}
        </option></select
      ><Badge v-if="attempt" :status="attempt.status" /><span
        class="spacer"
      ></span
      ><button class="btn secondary" @click="router.push(pathFor('S203'))">
        培训任务</button
      ><button v-if="attempt" class="btn secondary" @click="showEvents = true">
        <Icon name="History" :size="15" />操作记录
      </button>
    </div>
    <section v-if="!attempt" class="panel">
      <Empty
        title="开始一次完整的训练"
        description="从培训任务选择已发布课程，并创建新的训练尝试。"
        icon="Play"
        ><button class="btn primary" @click="router.push(pathFor('S203'))">
          选择培训任务
        </button></Empty
      >
    </section>
    <template v-else
      ><div v-if="attempt.status === 'COMPLETED'" class="result-banner">
        <div class="score-circle">
          <b>{{ attempt.score ?? "—" }}</b
          ><small>{{
            attempt.scope === "TEAM"
              ? "团队成绩"
              : attempt.scope === "NONE"
                ? "不计成绩"
                : "个人成绩"
          }}</small>
        </div>
        <div>
          <h3>{{ attempt.courseName }} · 训练完成</h3>
          <p>
            {{ attempt.currentStep }} 步通过 · {{ attempt.errors }} 次错误 ·
            {{ attempt.helps }} 次帮助<br />正确率 {{ attempt.correctRate }}% ·
            课程 V{{ attempt.courseVersion }}
          </p>
        </div>
        <div class="button-row">
          <button class="btn secondary" @click="questionnaire = true">
            填写反馈</button
          ><button
            class="btn primary"
            @click="download('attempts', attempt.id)"
          >
            <Icon name="Download" :size="15" />导出训练报告
          </button>
        </div>
      </div>
      <div class="training-layout">
        <section class="panel">
          <div class="panel-header">
            <h3>训练步骤</h3>
            <span class="subtle-label"
              >{{ attempt.currentStep }} / {{ course.steps.length }}</span
            >
          </div>
          <div class="panel-body" style="padding-bottom: 15px">
            <div class="progress-bar">
              <i
                :style="{
                  width: `${(attempt.currentStep / course.steps.length) * 100}%`,
                }"
              ></i>
            </div>
          </div>
          <div class="step-list">
            <div
              v-for="(st, i) in course.steps"
              :key="st.id"
              class="step-item"
              :style="{
                background: i === attempt.currentStep ? '#f1f6fc' : '',
              }"
            >
              <span
                class="step-num"
                :class="{
                  done: i < attempt.currentStep,
                  active: i === attempt.currentStep,
                }"
                ><Icon
                  v-if="i < attempt.currentStep"
                  name="Check"
                  :size="13"
                /><template v-else>{{ Number(i) + 1 }}</template></span
              >
              <div>
                <b>{{
                  attempt.mode === "FREE" && i >= attempt.currentStep
                    ? "待执行步骤"
                    : st.name
                }}</b>
                <p>
                  {{ st.id }} ·
                  {{
                    i < attempt.currentStep
                      ? "已通过"
                      : i === attempt.currentStep
                        ? "当前步骤"
                        : "未开始"
                  }}
                </p>
              </div>
            </div>
          </div>
        </section>
        <div>
          <div class="toolbar">
            <span class="pill">{{
              {
                GUIDED: "提示操作",
                FREE: "自由操作",
                DEMONSTRATION: "演示模式",
              }[attempt.mode]
            }}</span
            ><span class="pill">{{
              attempt.scope === "TEAM"
                ? "团队训练"
                : attempt.scope === "NONE"
                  ? "演示讲解"
                  : "个人训练"
            }}</span
            ><span class="spacer"></span
            ><span class="subtle-label">{{ attempt.courseName }}</span>
          </div>
          <section class="panel">
            <SceneView :state="attempt.entityState" @select="target = $event" />
            <div v-if="attempt.status !== 'COMPLETED'" class="training-control">
              <div>
                <h3>
                  {{
                    attempt.mode === "FREE" ? "请自主完成当前操作" : step?.name
                  }}
                </h3>
                <p>
                  {{
                    attempt.mode === "FREE"
                      ? "选择对象并提交操作；评价时可查看完整记录。"
                      : step?.description
                  }}
                </p>
              </div>
              <div class="button-row">
                <button
                  class="btn secondary small"
                  @click="command('training.pause', { id: attempt.id })"
                >
                  <Icon
                    :name="attempt.status === 'PAUSED' ? 'Play' : 'Pause'"
                    :size="14"
                  />{{ attempt.status === "PAUSED" ? "继续" : "暂停" }}</button
                ><button
                  class="btn secondary small"
                  :disabled="attempt.status !== 'RUNNING'"
                  @click="act('HELP')"
                >
                  请求帮助</button
                ><button
                  class="btn primary"
                  :disabled="store.busy > 0 || attempt.status !== 'RUNNING'"
                  @click="act('PASS')"
                >
                  <Icon name="MousePointer2" :size="15" />执行当前步骤
                </button>
              </div>
            </div>
          </section>
          <div class="grid-2" style="margin-top: 18px">
            <section class="panel">
              <div class="panel-header"><h3>操作对象与反馈</h3></div>
              <div class="panel-body">
                <label class="field"
                  ><span>当前选择的对象</span
                  ><select v-model="target">
                    <option value="PUMP-01">PUMP-01 · 通用泵组</option>
                    <option value="VALVE-01">
                      VALVE-01 · 阀门（非当前目标）
                    </option>
                    <option value="CTRL-01">
                      CTRL-01 · 控制单元（非当前目标）
                    </option>
                  </select></label
                >
                <div class="pill-list" style="margin-top: 14px">
                  <span class="pill">通过 {{ attempt.currentStep }}</span
                  ><span class="pill">错误 {{ attempt.errors }}</span
                  ><span class="pill">帮助 {{ attempt.helps }}</span>
                </div>
                <p class="note-caption">
                  {{
                    attempt.events.at(-1)?.kind === "ERROR"
                      ? "对象或动作不符合当前步骤，请检查后重试。"
                      : attempt.events.at(-1)?.kind === "TECHNICAL_FAILURE"
                        ? "平台模拟失败，技术故障不扣分，可重试。"
                        : attempt.events.at(-1)?.kind === "HELP"
                          ? "帮助：当前步骤应操作 PUMP-01，请确认前置步骤已完成。"
                          : "每个有效操作均保存到当前尝试记录。"
                  }}
                </p>
              </div>
            </section>
            <section class="panel">
              <div class="panel-header">
                <h3>协同会话</h3>
                <span class="subtle-label"
                  >{{ attempt.members.length }} 位成员</span
                >
              </div>
              <div class="panel-body">
                <div class="pill-list">
                  <span v-for="m in attempt.members" :key="m" class="pill">{{
                    store.accounts.find((a) => a.id === m)?.name || m
                  }}</span>
                </div>
                <div
                  class="button-row"
                  style="margin-top: 15px; flex-wrap: wrap"
                >
                  <button
                    class="btn secondary small"
                    @click="
                      command('training.join', { id: attempt.id }, '已加入会话')
                    "
                  >
                    加入会话</button
                  ><button
                    v-if="attempt.scope === 'TEAM'"
                    class="btn secondary small"
                    @click="
                      command(
                        'training.control',
                        { id: attempt.id },
                        '已取得30秒对象控制权',
                      )
                    "
                  >
                    申请控制权</button
                  ><button
                    class="btn secondary small"
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
                    {{
                      attempt.interactionMode === "REVIEW"
                        ? "切换自由交互"
                        : "切换评审模式"
                    }}
                  </button>
                </div>
                <p class="note-caption">
                  {{
                    attempt.scope === "TEAM"
                      ? "团队成绩不作为个人培训通过证据。"
                      : "个人成绩只归属于当前分配学员。"
                  }}
                  当前权限：{{
                    attempt.interactionMode === "REVIEW"
                      ? "主持人可操作"
                      : "自由交互"
                  }}。
                </p>
              </div>
            </section>
          </div>
        </div>
      </div></template
    ></template
  >
  <Modal
    v-if="startAssignment"
    title="创建训练尝试"
    @close="startAssignment = null"
    ><div class="form-stack">
      <div class="info-note">
        <Icon name="BookOpen" :size="16" />{{ startAssignment.name }} ·
        每次创建独立的尝试记录。
      </div>
      <label class="field"
        ><span>训练模式</span
        ><select v-model="mode">
          <option value="GUIDED">提示操作</option>
          <option value="FREE">自由操作</option>
          <option value="DEMONSTRATION">演示讲解（不计个人成绩）</option>
        </select></label
      ><label v-if="mode !== 'DEMONSTRATION'" class="field"
        ><span>评价范围</span
        ><select v-model="scope">
          <option value="INDIVIDUAL">个人训练</option>
          <option value="TEAM">团队训练</option>
        </select></label
      >
      <p class="note-caption">
        当前身份：{{
          store.accounts.find((a) => a.id === store.actor)?.name
        }}。个人训练需由分配学员、教员或演示管理员启动。
      </p>
    </div>
    <template #footer
      ><button
        class="btn secondary"
        @click="switchActor(startAssignment.learnerId)"
      >
        切换为分配学员</button
      ><button class="btn primary" @click="start">开始训练</button></template
    ></Modal
  >
  <Modal
    v-if="questionnaire"
    title="课程体验与反馈"
    @close="questionnaire = false"
    ><div class="form-stack">
      <label class="field"
        ><span>课程体验评分</span
        ><select v-model.number="rating">
          <option v-for="i in 5" :key="i" :value="i">{{ i }} 分</option>
        </select></label
      ><label class="field"
        ><span>改进建议</span
        ><textarea
          v-model="comment"
          placeholder="说明操作提示、步骤或内容可以如何改进"
        />
      </label>
    </div>
    <template #footer
      ><button class="btn primary" @click="evaluate">提交反馈</button></template
    ></Modal
  >
  <Modal
    v-if="showEvents && attempt"
    title="训练操作记录"
    wide
    @close="showEvents = false"
    ><div class="info-note">
      <Icon name="History" :size="16" />记录固定引用课程 V{{
        attempt.courseVersion
      }}。此视图只读，不触发计分或对象操作。
    </div>
    <div class="table-scroll">
      <table class="data-table">
        <thead>
          <tr>
            <th>序号</th>
            <th>步骤</th>
            <th>结果</th>
            <th>身份</th>
            <th>时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(e, i) in attempt.events" :key="e.id">
            <td>{{ Number(i) + 1 }}</td>
            <td>{{ e.step }} · {{ e.name || "" }}</td>
            <td :class="e.kind === 'ERROR' ? 'text-red' : 'text-teal'">
              {{
                {
                  PASS: "通过",
                  ERROR: "错误操作",
                  HELP: "请求帮助",
                  TECHNICAL_FAILURE: "技术故障（不扣分）",
                }[e.kind]
              }}
            </td>
            <td>{{ e.actor }}</td>
            <td>{{ new Date(e.at).toLocaleTimeString("zh-CN") }}</td>
          </tr>
        </tbody>
      </table>
    </div>
    <p v-if="!attempt.events.length" class="empty-small">
      还没有操作记录。
    </p></Modal
  >
</template>

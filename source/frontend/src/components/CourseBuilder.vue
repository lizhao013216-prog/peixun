<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { store, command, login, notify } from "../store";
import { pathForFeature } from "../catalog";
import { systemName, type SystemId } from "../domain";
import { lessonStep, trainingObjects, objectName } from "../lesson";
import Badge from "./Badge.vue";
import Icon from "./Icon.vue";
import Modal from "./Modal.vue";
import LessonStep from "./LessonStep.vue";
const props = defineProps<{ domain: SystemId }>();
const route = useRoute(),
  router = useRouter(),
  s = computed(() => store.data);
const courses = computed(() =>
  s.value.courses.filter((c: any) => c.domain === props.domain),
);
const routeCourseId = computed(() => String(route.params.id || "current"));
const course = computed(() => {
  if (routeCourseId.value !== "current")
    return courses.value.find((c: any) => c.id === routeCourseId.value);
  const selectedId = store.selectedCourseBySystem[props.domain];
  return (
    courses.value.find((c: any) => c.id === selectedId) || courses.value.at(-1)
  );
});
const courseNotFound = computed(
  () => routeCourseId.value !== "current" && !course.value,
);
const role = computed(
  () => store.accounts.find((a) => a.id === store.actor)?.role,
);
const canEdit = computed(() =>
  ["AUTHOR", "INSTRUCTOR", "ADMIN"].includes(role.value),
);
const canReview = computed(
  () =>
    ["INSTRUCTOR", "ADMIN"].includes(role.value) &&
    course.value?.createdBy !== store.actor,
);
const canAssign = computed(() => ["INSTRUCTOR", "ADMIN"].includes(role.value));
const creating = ref(false),
  preview = ref(false),
  assigning = ref(false),
  previewResult = ref<any>(null),
  stepIndex = ref(0);
const draft = ref<any>({ name: "", description: "", steps: [] }),
  newName = ref(""),
  newDescription = ref(""),
  requestId = ref(""),
  learner = ref("LEARNER_A"),
  reviewComment = ref("");
const assigned = computed(() =>
  s.value.assignments.filter((a: any) => a.courseId === course.value?.id),
);
const job = computed(() =>
  s.value.jobs.filter((j: any) => j.targetId === course.value?.id).at(-1),
);
const editable = computed(
  () => course.value?.status === "DRAFT" && canEdit.value,
);
const current = computed(() => draft.value.steps[stepIndex.value]);
const issues = computed(() =>
  draft.value.steps.flatMap((st: any, i: number) =>
    ["name", "description", "target", "actionLabel", "expectedResult"]
      .filter((k) => !String(st[k] || "").trim())
      .map(
        (k) =>
          `第${i + 1}步缺少${({ name: "步骤名称", description: "操作说明", target: "操作对象", actionLabel: "动作按钮文字", expectedResult: "预期结果" } as any)[k]}`,
      ),
  ),
);
const dirty = computed(
  () =>
    course.value &&
    JSON.stringify({
      name: course.value.name,
      description: course.value.description,
      steps: course.value.steps.map(lessonStep),
    }) !== JSON.stringify(draft.value),
);
const phase = computed(() =>
  !course.value
    ? 0
    : course.value.status === "DRAFT"
      ? 1
      : course.value.status === "PENDING_REVIEW"
        ? 2
        : course.value.status === "PUBLISHED"
          ? 4
          : 3,
);
const stages = ["建立课程", "编辑与预览", "独立审核", "发布课件", "分配并训练"];
const stageCopy = computed(() => {
  const status = course.value?.status;
  if (status === "DRAFT")
    return [
      "现在：编辑教学步骤",
      "逐步说明学员要选哪个对象、做什么动作、看到什么结果。保存后可预览学员视角，再提交审核。",
    ];
  if (status === "PENDING_REVIEW")
    return [
      "现在：等待另一位教员审核",
      "审核人检查教学步骤和学员预览，确认后“审核通过”；不符合要求可填写意见退回。",
    ];
  if (status === "APPROVED")
    return [
      "审核已通过，下一步发布课件",
      "点击“发布课程”，完成后即可选择学员并分配培训任务。",
    ];
  if (status === "BUILDING")
    return [
      "正在发布，请稍候",
      "课程正在构建，页面会自动更新结果。此时不能重复发布或分配任务。",
    ];
  if (status === "BUILD_FAILED")
    return [
      "发布未成功，请处理后重试",
      job.value?.message ||
        "查看发布记录；如果启用了模拟故障，请先在演示管理恢复正常响应。",
    ];
  if (status === "PUBLISHED")
    return [
      "课件已发布，下一步分配培训任务",
      "选择学员并分配任务。学员将在“培训任务”看到课程，完成后由教员确认证据。",
    ];
  return [
    "先建立一门课程",
    "填写课程目标并载入步骤模板，再逐项完善具体操作和预期结果。",
  ];
});
function sync() {
  if (course.value)
    draft.value = {
      name: course.value.name,
      description: course.value.description,
      steps: course.value.steps.map(lessonStep),
    };
  stepIndex.value = 0;
}
watch(() => course.value?.id, sync, { immediate: true });
function selectCourse(e: Event) {
  if (dirty.value && course.value?.status === "DRAFT") {
    notify("请先保存当前课件修改，再切换课程", "error");
    (e.target as HTMLSelectElement).value = course.value.id;
    return;
  }
  const id = (e.target as HTMLSelectElement).value;
  store.selectedCourseBySystem[props.domain] = id;
  router.replace(pathForFeature(props.domain, "coursewares", id));
}
async function useRole(id: string) {
  try {
    await login(id);
  } catch (e: any) {
    notify(e.message, "error");
  }
}
function reviewer() {
  const candidate = store.accounts.find(
    (a) => a.role === "INSTRUCTOR" && a.id !== course.value.createdBy,
  );
  if (candidate) useRole(candidate.id);
}
function newCourse() {
  if (dirty.value && course.value?.status === "DRAFT") {
    notify("请先保存当前课件修改", "error");
    return;
  }
  newName.value =
    props.domain === "OPERATION" ? "泵组操作与异常识别" : "泵组维修技能培训";
  newDescription.value =
    props.domain === "OPERATION"
      ? "掌握泵组场景确认、设备联动、异常识别与记录提交流程。"
      : "掌握维修准备、部件识别、模拟操作、检测与训练记录提交流程。";
  requestId.value = "";
  creating.value = true;
}
async function create() {
  const r = await command(
    "course.create",
    {
      domain: props.domain,
      name: newName.value,
      description: newDescription.value,
      requestId: requestId.value,
    },
    "课程草稿已创建",
  );
  if (r) {
    store.selectedCourseBySystem[props.domain] = r.id;
    router.replace(pathForFeature(props.domain, "coursewares", r.id));
    creating.value = false;
  }
}
async function save() {
  if (!draft.value.name.trim() || issues.value.length) {
    notify(issues.value[0] || "请填写课程名称", "error");
    return false;
  }
  const r = await command(
    "course.save",
    { id: course.value.id, ...draft.value },
    "课程内容已保存",
  );
  return !!r;
}
async function submit() {
  if (await save())
    await command(
      "course.submit",
      { id: course.value.id },
      "课程已提交，等待独立审核",
    );
}
async function openPreview() {
  if (course.value.status === "DRAFT" && dirty.value && !(await save())) return;
  previewResult.value = null;
  preview.value = true;
}
function tryPreview(p: any) {
  const st = current.value;
  previewResult.value = {
    ok: p.target === st.target && p.actionId === st.actionId,
    message:
      p.target === st.target && p.actionId === st.actionId
        ? st.expectedResult
        : `对象不正确：请选择${objectName(st.target)}，当前选择${objectName(p.target)}。`,
  };
}
async function assign() {
  const r = await command(
    "training.assign",
    { courseId: course.value.id, learnerId: learner.value },
    "培训任务已分配",
  );
  if (r) assigning.value = false;
}
async function revise() {
  const r = await command(
    "course.revise",
    { id: course.value.id },
    "已创建独立修订草稿",
  );
  if (r) {
    store.selectedCourseBySystem[props.domain] = r.id;
    router.replace(pathForFeature(props.domain, "coursewares", r.id));
  }
}
</script>
<template>
  <section class="business-intro">
    <div>
      <span class="task-eyebrow">教员工作区 · {{ systemName(domain) }}</span>
      <h2>把教学目标变成学员能照着完成的步骤。</h2>
      <p>
        一门课依次经过编辑、预览、审核、发布和任务分配。当前状态与下一项操作始终显示在这里。
      </p>
    </div>
    <button
      v-if="canEdit"
      class="btn primary"
      :disabled="store.busy > 0"
      @click="newCourse"
    >
      <Icon name="Plus" :size="17" />{{
        domain === "OPERATION" ? "新建操作课程" : "新建维修课件"
      }}</button
    ><button v-else class="btn primary" @click="useRole('AUTHOR')">
      以制作教员进入
    </button>
  </section>
  <div class="business-stages">
    <span
      v-for="(name, i) in stages"
      :key="name"
      :class="{ active: phase === i, passed: phase > i }"
      ><Icon v-if="phase > i" name="Check" :size="16" />{{ i + 1 }}
      {{ name }}</span
    >
  </div>
  <div v-if="courses.length" class="toolbar course-selector">
    <label
      >当前课件<select
        aria-label="当前课件"
        :value="course?.id"
        @change="selectCourse"
      >
        <option v-for="c in courses" :value="c.id" :key="c.id">
          {{ c.name }} · V{{ c.version }}
        </option>
      </select></label
    ><Badge :status="course?.status" /><span class="spacer"></span
    ><span>共 {{ courses.length }} 个课程版本</span>
  </div>
  <section class="course-next-action">
    <div>
      <h3>{{ stageCopy[0] }}</h3>
      <p>{{ stageCopy[1] }}</p>
      <p v-if="course?.reviewComment" class="text-red">
        退回意见：{{ course.reviewComment }}
      </p>
    </div>
    <div v-if="course" class="button-row">
      <template v-if="editable"
        ><button class="btn secondary" :disabled="store.busy > 0" @click="save">
          保存课件</button
        ><button
          class="btn primary"
          :disabled="store.busy > 0 || issues.length > 0"
          @click="submit"
        >
          提交审核
        </button></template
      >
      <template v-else-if="course.status === 'PENDING_REVIEW'"
        ><button v-if="!canReview" class="btn primary" @click="reviewer">
          切换独立审核教员</button
        ><button
          v-else
          class="btn primary"
          :disabled="store.busy > 0"
          @click="
            command(
              'course.approve',
              { id: course.id },
              '审核已通过，下一步发布课件',
            )
          "
        >
          审核通过
        </button></template
      >
      <template v-else-if="['APPROVED', 'BUILD_FAILED'].includes(course.status)"
        ><button
          v-if="canEdit"
          class="btn primary"
          :disabled="store.busy > 0"
          @click="
            command(
              'course.publish',
              { id: course.id },
              '已开始发布，请等待构建结果',
            )
          "
        >
          {{
            course.status === "BUILD_FAILED" ? "重试发布" : "发布课程"
          }}</button
        ><button v-else class="btn secondary" @click="useRole('AUTHOR')">
          切换制作教员发布
        </button></template
      >
      <template v-else-if="course.status === 'PUBLISHED'"
        ><button v-if="canAssign" class="btn primary" @click="assigning = true">
          分配培训任务</button
        ><button v-else class="btn primary" @click="useRole('INSTRUCTOR')">
          切换教员分配</button
        ><button v-if="canEdit" class="btn secondary" @click="revise">
          创建修订版
        </button></template
      >
    </div>
  </section>
  <section v-if="courseNotFound" class="panel empty-state">
    <Icon name="CircleAlert" :size="36" />
    <h3>当前系统中没有这个课件</h3>
    <p>
      请求的课件编号为
      {{ routeCourseId }}。系统没有自动改为其他领域或列表最后一项。
    </p>
    <button
      class="btn primary"
      @click="router.replace(pathForFeature(domain, 'coursewares'))"
    >
      返回本系统课件列表
    </button>
  </section>
  <section v-else-if="!course" class="panel empty-state">
    <Icon name="BookOpen" :size="36" />
    <h3>先填写课程名称和教学目标</h3>
    <p>
      系统提供可编辑的{{
        domain === "OPERATION" ? 8 : 10
      }}步模板，你可以修改操作说明、目标对象、按钮文字和预期结果。
    </p>
    <button v-if="canEdit" class="btn primary" @click="newCourse">
      新建课程
    </button>
  </section>
  <template v-else>
    <section
      v-if="course.status === 'PENDING_REVIEW' && canReview"
      class="panel panel-body review-input"
    >
      <label class="field"
        ><span>审核意见（退回时必填）</span
        ><input
          v-model="reviewComment"
          placeholder="指出需要修改的步骤或说明" /></label
      ><button
        class="btn secondary"
        :disabled="!reviewComment.trim() || store.busy > 0"
        @click="
          command(
            'course.return',
            { id: course.id, comment: reviewComment },
            '已退回制作教员修改',
          )
        "
      >
        退回修改
      </button>
    </section>
    <section v-if="assigned.length" class="info-note course-handoff">
      <Icon name="Users" :size="24" />
      <div>
        <b>已分配 {{ assigned.length }} 项培训任务</b>
        <p>接下来让学员进入培训任务；完成训练后回到培训评价确认证据。</p>
      </div>
      <button
        class="btn primary"
        @click="router.push(pathForFeature(domain, 'assignments'))"
      >
        查看学员任务</button
      ><button
        class="btn secondary"
        @click="router.push(pathForFeature(domain, 'training-evaluations'))"
      >
        查看培训结果
      </button>
    </section>
    <section class="panel">
      <div class="panel-header">
        <div>
          <h3>课程信息与教学目标</h3>
          <p>学员开始训练前会看到这些内容。</p>
        </div>
        <span class="pill">V{{ course.version }}</span>
      </div>
      <div class="panel-body form-grid">
        <label class="field"
          ><span>课程名称</span
          ><input v-model="draft.name" :disabled="!editable" /></label
        ><label class="field"
          ><span>学完这门课要掌握什么</span
          ><textarea v-model="draft.description" :disabled="!editable" />
        </label>
      </div>
    </section>
    <section class="panel course-step-editor">
      <div class="panel-header">
        <div>
          <h3>教学步骤 · 选择一步进行{{ editable ? "编辑" : "查看" }}</h3>
          <p>每一步都要说明“做什么、怎么做、做完看到什么”。</p>
        </div>
        <button
          class="btn secondary"
          :disabled="issues.length > 0 || store.busy > 0"
          @click="openPreview"
        >
          <Icon name="Play" :size="16" />预览学员视角
        </button>
      </div>
      <div v-if="issues.length" class="info-note warning">
        {{ issues[0] }}。完善后才能提交审核和预览。
      </div>
      <div class="course-editor-body">
        <nav aria-label="教学步骤">
          <button
            v-for="(st, i) in draft.steps"
            :key="st.id"
            :class="{ active: stepIndex === i }"
            @click="stepIndex = Number(i)"
          >
            <span>{{ Number(i) + 1 }}</span
            ><b>{{ st.name }}</b
            ><Icon name="ChevronRight" :size="16" />
          </button>
        </nav>
        <div v-if="current" class="step-fields">
          <label class="field"
            ><span>步骤名称</span
            ><input v-model="current.name" :disabled="!editable"
          /></label>
          <label class="field"
            ><span>学员应该怎么操作</span
            ><textarea
              v-model="current.description"
              :disabled="!editable"
              placeholder="说明学员应选择哪个对象、执行什么操作"
            />
          </label>
          <div class="form-grid">
            <label class="field"
              ><span>正确的操作对象</span
              ><select v-model="current.target" :disabled="!editable">
                <option v-for="o in trainingObjects" :key="o.id" :value="o.id">
                  {{ o.name }} · {{ o.id }}
                </option>
              </select></label
            ><label class="field"
              ><span>学员看到的动作按钮文字</span
              ><input
                v-model="current.actionLabel"
                :disabled="!editable"
                placeholder="例如：确认维修训练任务"
            /></label>
          </div>
          <label class="field"
            ><span>操作成功后显示什么结果</span
            ><textarea v-model="current.expectedResult" :disabled="!editable" />
          </label>
          <div class="step-rule-summary">
            <Icon name="CircleCheck" :size="20" /><span
              >通过条件：按顺序选择<strong>{{
                objectName(current.target)
              }}</strong
              >，执行<strong>{{ current.actionLabel }}</strong
              >。选择错误对象会保留本步并反馈原因。</span
            >
          </div>
          <div class="button-row">
            <button
              class="btn secondary"
              :disabled="stepIndex === 0"
              @click="stepIndex--"
            >
              上一个步骤</button
            ><button
              class="btn secondary"
              :disabled="stepIndex === draft.steps.length - 1"
              @click="stepIndex++"
            >
              下一个步骤</button
            ><button
              v-if="editable"
              class="btn primary"
              :disabled="store.busy > 0 || issues.length > 0"
              @click="save"
            >
              保存步骤
            </button>
          </div>
        </div>
      </div>
    </section>
    <p class="note-caption">
      {{
        editable
          ? "保存后预览和学员训练使用同一份步骤内容；审核发布后的版本保留历史，修改时创建新版本。"
          : "当前版本不可直接编辑；已发布课件如需修改，请创建修订版。"
      }}
    </p>
  </template>
  <Modal
    v-if="creating"
    :title="domain === 'OPERATION' ? '新建操作课程' : '新建维修课件'"
    @close="creating = false"
    ><div class="form-stack">
      <label class="field"
        ><span>课程名称 *</span><input v-model="newName" /></label
      ><label class="field"
        ><span>教学目标 *</span><textarea v-model="newDescription" /></label
      ><label v-if="domain === 'MAINTENANCE'" class="field"
        ><span>关联保障培训需求（可选）</span
        ><select v-model="requestId">
          <option value="">独立培训课程</option>
          <option
            v-for="r in s.requests.filter((r: any) => r.status !== 'COMPLETED')"
            :key="r.id"
            :value="r.id"
          >
            {{ r.name }}
          </option>
        </select></label
      >
      <div class="info-note">
        创建后载入{{
          domain === "OPERATION" ? 8 : 10
        }}步模板。下一步逐项完善操作内容并预览学员视角。
      </div>
    </div>
    <template #footer
      ><button class="btn secondary" @click="creating = false">取消</button
      ><button
        class="btn primary"
        :disabled="!newName.trim() || !newDescription.trim() || store.busy > 0"
        @click="create"
      >
        创建草稿
      </button></template
    ></Modal
  >
  <Modal
    v-if="preview"
    title="学员视角预览 · 不计成绩"
    wide
    @close="preview = false"
    ><div class="preview-notice">
      当前预览第
      {{ stepIndex + 1 }}
      步。对象选择、操作文字和结果说明与学员训练一致；预览不会产生培训成绩或证据。
    </div>
    <div
      v-if="previewResult"
      class="training-feedback"
      :class="previewResult.ok ? 'PASS' : 'ERROR'"
    >
      <b>{{ previewResult.ok ? "预览操作通过" : "预览操作未通过" }}</b>
      <p>{{ previewResult.message }}</p>
    </div>
    <LessonStep
      :key="stepIndex"
      :step="current"
      @submit="tryPreview"
    /><template #footer
      ><button class="btn secondary" @click="preview = false">
        返回课件制作</button
      ><button
        class="btn primary"
        :disabled="stepIndex >= draft.steps.length - 1"
        @click="
          stepIndex++;
          previewResult = null;
        "
      >
        预览下一步
      </button></template
    ></Modal
  >
  <Modal v-if="assigning" title="分配培训任务" @close="assigning = false"
    ><div class="form-stack">
      <div class="info-note">
        {{ course.name }} · V{{ course.version }} · {{ course.steps.length }}步
      </div>
      <label class="field"
        ><span>选择学员</span
        ><select v-model="learner">
          <option
            v-for="u in store.accounts.filter((a) => a.role === 'LEARNER')"
            :key="u.id"
            :value="u.id"
          >
            {{ u.name }}
          </option>
        </select></label
      >
      <p>
        分配后学员将在“培训任务”中看到课程；任务会记录课程版本，成绩由学员实际操作产生。
      </p>
    </div>
    <template #footer
      ><button class="btn secondary" @click="assigning = false">取消</button
      ><button class="btn primary" :disabled="store.busy > 0" @click="assign">
        确认分配任务
      </button></template
    ></Modal
  >
</template>

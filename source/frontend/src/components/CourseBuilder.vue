<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { onBeforeRouteLeave, useRoute, useRouter } from "vue-router";
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
const modern = computed(() => course.value?.interactionSchemaVersion === 3);
const templates = computed(() =>
  (s.value.systemTemplates || []).filter(
    (item: any) => item.status === "PUBLISHED" && item.sceneSnapshot,
  ),
);
const stations = computed(() =>
  (s.value.stations || []).filter((item: any) => item.domain === props.domain),
);
const boundTemplate = computed(() => {
  const ref = course.value?.environment?.templateRef;
  return templates.value.find(
    (item: any) => item.id === ref?.id && item.version === ref?.version,
  );
});
const curriculum = computed(() =>
  (s.value.curricula || []).find((item: any) => item.id === course.value?.curriculumId),
);
const unit = computed(() =>
  curriculum.value?.units?.find((item: any) => item.id === course.value?.unitId),
);
const availableObjects = computed(() =>
  modern.value ? boundTemplate.value?.sceneSnapshot?.objects || [] : trainingObjects,
);
const objectLabel = (id: string) =>
  availableObjects.value.find((item: any) => item.id === id)?.name || objectName(id);
const creating = ref(false),
  preview = ref(false),
  assigning = ref(false),
  previewResult = ref<any>(null),
  previewSession = ref<any>(null),
  previewStates = ref<Record<string, any>>({}),
  stepIndex = ref(0),
  draggedStep = ref(-1),
  validationReport = ref<any>(null);
const draft = ref<any>({ name: "", description: "", mode: "GUIDED", scoreRule: {}, steps: [] }),
  supportPreset = ref("PUMP_DELAY"),
  newName = ref(""),
  newDescription = ref(""),
  curriculumName = ref(""),
  curriculumObjective = ref(""),
  unitName = ref("第一课时"),
  existingCurriculumId = ref(""),
  existingUnitId = ref(""),
  newUnitName = ref(""),
  templateId = ref(""),
  rebindTemplateId = ref(""),
  contractVersion = ref("V3"),
  requestId = ref(""),
  learner = ref("LEARNER_A"),
  assignmentMode = ref("GUIDED"),
  assignmentDueAt = ref(""),
  assignmentStationId = ref(""),
  baseEditRevision = ref(0),
  reviewComment = ref("");
const assigned = computed(() =>
  s.value.assignments.filter((a: any) => a.courseId === course.value?.id),
);
const curricula = computed(() => (s.value.curricula || []).filter((item: any) => item.domain === props.domain));
const selectedCurriculum = computed(() => curricula.value.find((item: any) => item.id === existingCurriculumId.value));
const job = computed(() =>
  s.value.jobs.filter((j: any) => j.targetId === course.value?.id).at(-1),
);
const editable = computed(
  () => course.value?.status === "DRAFT" && canEdit.value,
);
const current = computed(() => draft.value.steps[stepIndex.value]);
const currentObject = computed(() =>
  availableObjects.value.find((item: any) => item.id === current.value?.target),
);
const currentActions = computed(() => currentObject.value?.actions || []);
const currentAction = computed(() =>
  currentActions.value.find((item: any) => item.id === current.value?.actionId),
);
const conditionObject = computed(() =>
  availableObjects.value.find((item: any) => item.id === current.value?.precondition?.objectId),
);
const conditionFields = computed(() => conditionObject.value?.stateFields || []);
const conditionField = computed(() =>
  conditionFields.value.find((item: any) => item.id === current.value?.precondition?.field),
);
const issues = computed(() =>
  draft.value.steps.flatMap((st: any, i: number) => {
    const missing = ["name", "description", "target", "actionLabel", "expectedResult"]
      .filter((k) => !String(st[k] || "").trim())
      .map(
        (k) =>
          `第${i + 1}步缺少${({ name: "步骤名称", description: "操作说明", target: "操作对象", actionLabel: "动作按钮文字", expectedResult: "预期结果" } as any)[k]}`,
      );
    if (modern.value) {
      if (!availableObjects.value.some((item: any) => item.id === st.target))
        missing.push(`第${i + 1}步引用的对象不在绑定模板中`);
      const object = availableObjects.value.find((item: any) => item.id === st.target);
      if (!object?.actions?.some((item: any) => item.id === st.actionId))
        missing.push(`第${i + 1}步尚未选择有效设备动作`);
      if (!(Number(st.points) > 0)) missing.push(`第${i + 1}步分值必须大于0`);
    }
    return missing;
  }).concat(
    modern.value && draft.value.steps.reduce((sum: number, item: any) => sum + Number(item.points || 0), 0) !== Number(draft.value.scoreRule?.total || 0)
      ? ["步骤分值合计必须等于课件总分"]
      : [],
    modern.value ? course.value?.environmentIssues || [] : [],
  ),
);
const dirty = computed(
  () =>
    course.value &&
    JSON.stringify({
      name: course.value.name,
      description: course.value.description,
      mode: course.value.mode || "GUIDED",
      scoreRule: course.value.scoreRule || {},
      supportCaseSnapshot: course.value.supportCaseSnapshot || null,
      steps: modern.value ? course.value.steps : course.value.steps.map(lessonStep),
    }) !== JSON.stringify(draft.value),
);
watch(dirty, (value) => { store.unsavedContext = value && course.value?.status === "DRAFT" ? `课件“${draft.value.name || course.value?.name}”` : ""; });
onBeforeRouteLeave(() => !dirty.value || course.value?.status !== "DRAFT" || confirm("课件存在未保存修改，离开后将丢失。是否继续？"));
onBeforeUnmount(() => { if (store.unsavedContext.startsWith("课件")) store.unsavedContext = ""; });
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
const stages = computed(() =>
  modern.value
    ? ["建立课程", "编辑与预览", "独立审核", "发布课件", "分配并训练"]
    : ["建立课程", "编辑与预览", "独立审核", "发布课件", "分配并训练"],
);
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
      modern.value ? "本版本的环境、步骤、评分和设备能力已冻结；当前不会落入旧训练器。" : "选择学员并分配任务。学员将在“培训任务”看到课程，完成后由教员确认证据。",
    ];
  return [
    "先建立一门课程",
    "填写课程目标并载入步骤模板，再逐项完善具体操作和预期结果。",
  ];
});
function sync() {
  if (course.value) {
    baseEditRevision.value = course.value.editRevision || 0;
    draft.value = {
      name: course.value.name,
      description: course.value.description,
      mode: course.value.mode || "GUIDED",
      scoreRule: JSON.parse(JSON.stringify(course.value.scoreRule || { total: 100, errorPenalty: 5, helpPenalty: 2 })),
      supportCaseSnapshot: course.value.supportCaseSnapshot ? JSON.parse(JSON.stringify(course.value.supportCaseSnapshot)) : null,
      steps: modern.value
        ? JSON.parse(JSON.stringify(course.value.steps))
        : course.value.steps.map(lessonStep),
    };
  }
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
    props.domain === "SUPPORT"
      ? "泵组保障方案编制与延迟事件处置"
      : props.domain === "OPERATION"
        ? "泵组操作与异常识别"
        : "泵组维修技能培训";
  newDescription.value =
    props.domain === "SUPPORT"
      ? "掌握任务理解、资源配置、方案编制、计算验证、事件处置和复盘提交。"
      : props.domain === "OPERATION"
      ? "掌握泵组场景确认、设备联动、异常识别与记录提交流程。"
      : "掌握维修准备、部件识别、模拟操作、检测与训练记录提交流程。";
  curriculumName.value = newName.value;
  curriculumObjective.value = newDescription.value;
  unitName.value = "第一课时";
  existingCurriculumId.value = "";
  existingUnitId.value = "";
  templateId.value = templates.value[0]?.id || "";
  contractVersion.value = "V3";
  requestId.value = "";
  creating.value = true;
}
async function create() {
  const isModern = contractVersion.value === "V3";
  if (isModern && !templateId.value) {
    notify("请先在仿真系统模板库发布一个可用模板", "error");
    return;
  }
  const r = await command(
    isModern ? "courseware.create" : "course.create",
    isModern
      ? {
          domain: props.domain,
          curriculumId: existingCurriculumId.value,
          unitId: existingUnitId.value,
          curriculumName: existingCurriculumId.value ? "" : curriculumName.value,
          curriculumObjective: existingCurriculumId.value ? "" : curriculumObjective.value,
          unitName: existingCurriculumId.value ? "" : unitName.value,
          name: newName.value,
          description: newDescription.value,
          templateId: templateId.value,
          stepCount: props.domain === "SUPPORT" ? 6 : 5,
        }
      : {
          domain: props.domain,
          name: newName.value,
          description: newDescription.value,
          requestId: requestId.value,
        },
    isModern ? "V3课件草稿已创建" : "兼容课件草稿已创建",
  );
  if (r) {
    store.selectedCourseBySystem[props.domain] = r.id;
    router.replace(pathForFeature(props.domain, "coursewares", r.id));
    creating.value = false;
  }
}
async function save() {
  if (!draft.value.name.trim() || (!modern.value && issues.value.length)) {
    notify(issues.value[0] || "请填写课程名称", "error");
    return false;
  }
  const r = await command(
    modern.value ? "courseware.save" : "course.save",
    modern.value
      ? { id: course.value.id, expectedEditRevision: baseEditRevision.value, ...draft.value }
      : { id: course.value.id, ...draft.value },
    "课程内容已保存",
  );
  if (r) baseEditRevision.value = r.editRevision || baseEditRevision.value;
  return !!r;
}
async function submit() {
  if (await save())
    await command(
      modern.value ? "courseware.submit" : "course.submit",
      { id: course.value.id },
      "课程已提交，等待独立审核",
    );
}
async function openPreview() {
  if (course.value.status === "DRAFT" && dirty.value && !(await save())) return;
  if (modern.value) {
    validationReport.value = await command("courseware.validate", { id: course.value.id }, "");
    if (!validationReport.value?.valid) {
      notify(validationReport.value?.issues?.[0] || "课件还不能预览", "error");
      return;
    }
  }
  previewResult.value = null;
  if (modern.value) {
    previewSession.value = await command("coursewarePreview.start", { id: course.value.id }, "");
    if (!previewSession.value) return;
    previewStates.value = previewSession.value.states || {};
    stepIndex.value = previewSession.value.currentStep || 0;
  }
  preview.value = true;
}
async function tryPreview(p: any) {
  const st = current.value;
  if (modern.value) {
    const result = await command("coursewarePreview.action", { previewId: previewSession.value?.id, stepId: st.id, ...p }, "");
    if (result)
      previewResult.value = {
        ok: result.passed,
        message: result.reason || result.event?.reason || st.expectedResult,
      };
    if (result) previewStates.value = result.states || previewStates.value;
    return;
  }
  previewResult.value = {
    ok: p.target === st.target && p.actionId === st.actionId,
    message:
      p.target === st.target && p.actionId === st.actionId
        ? st.expectedResult
        : `对象不正确：请选择${objectLabel(st.target)}，当前选择${objectLabel(p.target)}。`,
  };
}
async function assign() {
  const r = await command(
    "training.assign",
    { courseId: course.value.id, learnerId: learner.value, mode: assignmentMode.value, dueAt: assignmentDueAt.value, stationId: assignmentStationId.value },
    "培训任务已分配",
  );
  if (r) assigning.value = false;
}
async function addCurriculumUnit() {
  if (!curriculum.value || !newUnitName.value.trim()) return;
  const result = await command("curriculum.unit.add", { id: curriculum.value.id, name: newUnitName.value, objective: curriculum.value.objective }, "课时已加入当前课程");
  if (result) newUnitName.value = "";
}
async function continuePreview() {
  if (!previewResult.value?.ok || !previewSession.value) return;
  const result = await command("coursewarePreview.continue", { previewId: previewSession.value.id }, "");
  if (!result) return;
  previewSession.value = result;
  previewStates.value = result.states || previewStates.value;
  stepIndex.value = Math.min(result.currentStep || 0, draft.value.steps.length - 1);
  previewResult.value = null;
}
async function closePreview() {
  if (modern.value && previewSession.value?.id)
    await command("coursewarePreview.close", { previewId: previewSession.value.id }, "");
  preview.value = false;
  previewSession.value = null;
}
async function revise() {
  const r = await command(
    modern.value ? "courseware.revise" : "course.revise",
    { id: course.value.id },
    "已创建独立修订草稿",
  );
  if (r) {
    store.selectedCourseBySystem[props.domain] = r.id;
    router.replace(pathForFeature(props.domain, "coursewares", r.id));
  }
}
function addStep() {
  draft.value.steps.push({ id: `STEP-${crypto.randomUUID()}`, name: `步骤${draft.value.steps.length + 1}`, description: "", target: "", actionId: "", actionLabel: "", parameters: {}, precondition: null, completion: { kind: "ACTION_SUCCEEDED" }, expectedResult: "", points: 0, mode: draft.value.mode || "GUIDED", ...(props.domain === 'SUPPORT' ? { supportKind: 'TASK_CONFIRM' } : {}) });
  stepIndex.value = draft.value.steps.length - 1;
}
function applySupportPreset() {
  if (!draft.value.supportCaseSnapshot) return;
  if (supportPreset.value === "REMOTE_REPAIR") {
    Object.assign(draft.value.supportCaseSnapshot, { name: "远端控制柜抢修与人员调度", taskObject: "演示站B · 远端控制柜", scope: "控制柜故障检查、工具与人员资源调配", deadline: 333, arrivalTime: 120, transportCost: 80 });
  } else {
    Object.assign(draft.value.supportCaseSnapshot, { name: "泵组保障方案编制与延迟事件处置", taskObject: "演示船A · 通用泵组", scope: "泵组保障资源、工序与延迟到货处置", deadline: 270, arrivalTime: 90, transportCost: 100 });
  }
}
function copyStep() {
  if (!current.value) return;
  const copy = JSON.parse(JSON.stringify(current.value));
  copy.id = `STEP-${crypto.randomUUID()}`;
  copy.name = `${copy.name} · 副本`;
  draft.value.steps.splice(stepIndex.value + 1, 0, copy);
  stepIndex.value++;
}
function deleteStep() {
  if (!current.value || !confirm(`删除“${current.value.name}”会同时移除该步骤的分值和条件，是否继续？`)) return;
  draft.value.steps.splice(stepIndex.value, 1);
  stepIndex.value = Math.max(0, Math.min(stepIndex.value, draft.value.steps.length - 1));
}
function moveStep(offset: number) {
  const target = stepIndex.value + offset;
  if (target < 0 || target >= draft.value.steps.length) return;
  const [item] = draft.value.steps.splice(stepIndex.value, 1);
  draft.value.steps.splice(target, 0, item);
  stepIndex.value = target;
}
function dropStep(target: number) {
  if (draggedStep.value < 0 || draggedStep.value === target) return;
  const [item] = draft.value.steps.splice(draggedStep.value, 1);
  draft.value.steps.splice(target, 0, item);
  stepIndex.value = target;
  draggedStep.value = -1;
}
function selectTarget() {
  if (!modern.value || !current.value) return;
  current.value.actionId = "";
  current.value.actionLabel = "";
  current.value.parameters = {};
}
function selectAction() {
  if (!modern.value || !current.value) return;
  current.value.actionLabel = currentAction.value?.label || "";
  current.value.parameters = {};
  for (const parameter of currentAction.value?.parameters || [])
    current.value.parameters[parameter.id] = parameter.valueType === "BOOLEAN" ? false : parameter.valueType === "NUMBER" ? 0 : "";
}
function addCondition() {
  const object = currentObject.value || availableObjects.value[0];
  const field = object?.stateFields?.[0];
  if (!object || !field) {
    notify("当前模板对象没有可用于前置条件的状态字段", "error");
    return;
  }
  current.value.precondition = {
    objectId: object.id,
    field: field.id,
    operator: "EQ",
    value: field.valueType === "BOOLEAN" ? false : field.valueType === "NUMBER" ? 0 : "READY",
  };
}
function selectConditionObject() {
  const field = conditionFields.value[0];
  current.value.precondition.field = field?.id || "";
  current.value.precondition.value = field?.valueType === "BOOLEAN" ? false : field?.valueType === "NUMBER" ? 0 : "";
}
function selectConditionField() {
  current.value.precondition.value = conditionField.value?.valueType === "BOOLEAN" ? false : conditionField.value?.valueType === "NUMBER" ? 0 : "";
}
async function rebind() {
  if (!rebindTemplateId.value || rebindTemplateId.value === boundTemplate.value?.id) return;
  const result = await command("courseware.rebind", { id: course.value.id, expectedEditRevision: baseEditRevision.value, templateId: rebindTemplateId.value }, "课件环境已更换，请处理受影响步骤");
  if (result) baseEditRevision.value = result.editRevision || baseEditRevision.value;
  if (result?.environmentIssues?.length) notify(result.environmentIssues[0], "error");
}
</script>
<template>
  <section class="business-intro">
    <div>
      <span class="task-eyebrow">教员工作区 · {{ systemName(domain) }}</span>
      <h2>把教学目标变成学员能照着完成的步骤。</h2>
      <p>
        V3课件显式绑定已发布仿真模板，可自由编排步骤并冻结发布版本；旧课件继续走兼容流程。
      </p>
    </div>
    <button
      v-if="canEdit"
      class="btn primary"
      :disabled="store.busy > 0"
      @click="newCourse"
    >
      <Icon name="Plus" :size="17" />{{
        domain === "OPERATION" ? "新建操作课件" : "新建维修课件"
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
  <section v-if="modern && curriculum" class="panel panel-body"><div class="panel-header"><div><h3>{{ curriculum.name }}</h3><small>{{ curriculum.objective }} · 面向 {{ curriculum.audience }}</small></div><span class="pill">{{ curriculum.units?.length || 0 }} 个课时</span></div><div class="relation-list"><div v-for="item in curriculum.units || []" :key="item.id" class="relation-row"><b>第{{ item.order }}课时 · {{ item.name }}</b><span>{{ item.id }}</span><small>{{ item.id === unit?.id ? `当前课件位于本课时` : '可在新建课件时选择' }}</small></div></div><div v-if="canEdit" class="button-row"><input v-model="newUnitName" placeholder="新增课时名称" /><button class="btn secondary" :disabled="!newUnitName.trim()" @click="addCurriculumUnit"><Icon name="Plus" :size="15" />新增课时</button></div></section>
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
              modern ? 'courseware.approve' : 'course.approve',
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
              modern ? 'courseware.publish' : 'course.publish',
              { id: course.id },
              modern ? 'V3课件已发布并冻结快照' : '已开始发布，请等待构建结果',
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
      先在仿真系统模板库发布环境，再建立课程、课时和可自由增删排序的V3课件步骤。
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
            modern ? 'courseware.return' : 'course.return',
            { id: course.id, comment: reviewComment },
            '已退回制作教员修改',
          )
        "
      >
        退回修改
      </button>
    </section>
    <section v-if="modern && course.status === 'PUBLISHED'" class="info-note course-handoff">
      <Icon name="ShieldCheck" :size="24" />
      <div>
        <b>V3发布快照已冻结</b>
        <p>环境、步骤、评分和设备能力已冻结为发布快照；新任务会按此版本独立运行，后续修订不会改写已分配任务。</p>
      </div>
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
    <section v-if="modern" class="panel courseware-environment">
      <div class="panel-header">
        <div><h3>课程、课时与仿真环境</h3><p>环境只能通过明确改绑操作更换，普通保存不会读取全局场景。</p></div>
        <span class="pill">交互契约 V3</span>
      </div>
      <div class="panel-body template-facts">
        <article><span>课程</span><b>{{ curriculum?.name }}</b><small>{{ curriculum?.objective }}</small></article>
        <article><span>课时</span><b>{{ unit?.name }}</b><small>{{ unit?.objective }}</small></article>
        <article><span>绑定模板</span><b>{{ boundTemplate?.name }}</b><small>{{ boundTemplate?.id }} · V{{ boundTemplate?.version }}</small></article>
        <article><span>环境摘要</span><b>{{ availableObjects.length }} 个对象</b><small>{{ boundTemplate?.linkageMode === 'NONE' ? '不使用设备联动' : `${boundTemplate?.connectionCount || 0}连接 / ${boundTemplate?.ruleCount || 0}规则` }}</small></article>
      </div>
      <div class="panel-body template-columns">
        <div><h4>冻结引用</h4><div class="relation-list"><div class="relation-row"><b>场景</b><span>{{ course.environment.sceneRef.id }} · V{{ course.environment.sceneRef.version }}</span></div><div class="relation-row"><b>关系</b><span>{{ course.environment.topologyRef ? `${course.environment.topologyRef.id} · V${course.environment.topologyRef.version}` : '本模板不使用设备联动' }}</span></div></div></div>
        <div><h4>素材依赖版本</h4><div class="relation-list"><div v-for="dependency in boundTemplate?.dependencies || []" :key="`${dependency.assetRef.id}-${dependency.assetRef.version}`" class="relation-row"><b>{{ dependency.name }}</b><span>{{ dependency.assetRef.id }} · V{{ dependency.assetRef.version }}</span></div></div></div>
      </div>
      <div v-if="editable" class="panel-body environment-rebind">
        <label class="field"><span>显式更换模板版本</span><select v-model="rebindTemplateId"><option value="">选择其他已发布模板</option><option v-for="item in templates.filter((item: any) => item.id !== boundTemplate?.id)" :key="item.id" :value="item.id">{{ item.name }} · V{{ item.version }}</option></select></label>
        <button class="btn secondary" :disabled="!rebindTemplateId || store.busy > 0" @click="rebind">检查影响并改绑</button>
      </div>
      <div v-if="course.environmentIssues?.length" class="info-note warning">{{ course.environmentIssues[0] }}。系统不会自动替换为其他对象或动作。</div>
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
        <div v-if="modern && editable" class="button-row">
          <button class="btn secondary" @click="addStep"><Icon name="Plus" :size="15" />新增步骤</button>
          <button class="btn secondary" :disabled="!current" @click="copyStep">复制步骤</button>
          <button class="btn secondary danger" :disabled="!current" @click="deleteStep">删除步骤</button>
        </div>
      </div>
      <div v-if="issues.length" class="info-note warning">
        {{ issues[0] }}。完善后才能提交审核和预览。
      </div>
      <div v-if="modern && domain === 'SUPPORT' && draft.supportCaseSnapshot" class="panel-body form-stack">
        <div class="form-grid"><label class="field"><span>案例模板</span><select v-model="supportPreset" :disabled="!editable" @change="applySupportPreset"><option value="PUMP_DELAY">泵组延迟到货案例</option><option value="REMOTE_REPAIR">远端控制柜抢修案例</option></select></label><label class="field"><span>教学案例名称</span><input v-model="draft.supportCaseSnapshot.name" :disabled="!editable" /></label><label class="field"><span>任务对象</span><input v-model="draft.supportCaseSnapshot.taskObject" :disabled="!editable" /></label><label class="field"><span>完成时限（分钟）</span><input v-model.number="draft.supportCaseSnapshot.deadline" type="number" min="1" :disabled="!editable" /></label><label class="field full"><span>任务范围</span><textarea v-model="draft.supportCaseSnapshot.scope" :disabled="!editable" /></label><label class="field"><span>预计到货（分钟）</span><input v-model.number="draft.supportCaseSnapshot.arrivalTime" type="number" min="0" :disabled="!editable" /></label><label class="field"><span>运输费用</span><input v-model.number="draft.supportCaseSnapshot.transportCost" type="number" min="0" :disabled="!editable" /></label></div>
      </div>
      <div v-if="modern" class="panel-body form-grid score-rule-fields">
        <label class="field"><span>课件总分</span><input v-model.number="draft.scoreRule.total" type="number" min="1" :disabled="!editable" /></label>
        <label class="field"><span>每次错误扣分</span><input v-model.number="draft.scoreRule.errorPenalty" type="number" min="0" :disabled="!editable" /></label>
        <label class="field"><span>每次帮助扣分</span><input v-model.number="draft.scoreRule.helpPenalty" type="number" min="0" :disabled="!editable" /></label>
      </div>
      <div class="course-editor-body">
        <nav aria-label="教学步骤">
          <button
            v-for="(st, i) in draft.steps"
            :key="st.id"
            :class="{ active: stepIndex === i }"
            :draggable="modern && editable"
            @dragstart="draggedStep = Number(i)"
            @dragover.prevent
            @drop="dropStep(Number(i))"
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
              ><select v-model="current.target" :disabled="!editable" @change="selectTarget">
                <option value="">请选择模板中的对象</option>
                <option v-for="o in availableObjects" :key="o.id" :value="o.id">
                  {{ o.name }} · {{ o.id }}
                </option>
              </select></label
            ><label v-if="modern" class="field"><span>实际设备动作</span><select v-model="current.actionId" :disabled="!editable || !current.target" @change="selectAction"><option value="">请选择有限动作</option><option v-for="action in currentActions" :key="action.id" :value="action.id">{{ action.label }} · {{ action.id }}</option></select></label>
            <label class="field"
              ><span>学员看到的动作按钮文字</span
              ><input
                v-model="current.actionLabel"
                :disabled="!editable"
                placeholder="例如：确认维修训练任务"
            /></label>
          </div>
          <div v-if="modern && currentAction?.parameters?.length" class="form-grid parameter-fields">
            <label v-for="parameter in currentAction.parameters" :key="parameter.id" class="field"><span>动作参数 · {{ parameter.id }}</span>
              <select v-if="parameter.valueType === 'BOOLEAN'" v-model="current.parameters[parameter.id]" :disabled="!editable"><option :value="true">是</option><option :value="false">否</option></select>
              <input v-else-if="parameter.valueType === 'NUMBER'" v-model.number="current.parameters[parameter.id]" type="number" :disabled="!editable" />
              <input v-else v-model="current.parameters[parameter.id]" :disabled="!editable" />
            </label>
          </div>
          <div v-if="modern" class="form-grid">
            <label class="field"><span>本步分值</span><input v-model.number="current.points" type="number" min="1" :disabled="!editable" /></label>
            <label class="field"><span>教学模式</span><select v-model="current.mode" :disabled="!editable"><option value="GUIDED">引导</option><option value="FREE">自由</option><option value="DEMONSTRATION">演示</option></select></label>
            <label v-if="domain === 'SUPPORT'" class="field"><span>保障教学动作</span><select v-model="current.supportKind" :disabled="!editable"><option value="TASK_CONFIRM">确认任务</option><option value="RESOURCE_CONFIGURE">配置资源</option><option value="PLAN_EDIT">编制工序</option><option value="PLAN_CALCULATE">计算验证</option><option value="EVENT_HANDLE">事件处置</option><option value="REVIEW_SUBMIT">提交复盘</option></select></label>
          </div>
          <div v-if="modern" class="step-condition-editor">
            <div class="rule-card-head"><div><Icon name="GitBranch" :size="18" /><b>前置状态条件（可选）</b></div><button v-if="editable && !current.precondition" class="text-btn" @click="addCondition">添加条件</button><button v-else-if="editable" class="text-btn danger" @click="current.precondition = null">移除条件</button></div>
            <div v-if="current.precondition" class="rule-sentence">
              <select v-model="current.precondition.objectId" :disabled="!editable" @change="selectConditionObject"><option v-for="item in availableObjects" :key="item.id" :value="item.id">{{ item.name }}</option></select>
              <select v-model="current.precondition.field" :disabled="!editable" @change="selectConditionField"><option v-for="field in conditionFields" :key="field.id" :value="field.id">{{ field.id }}</option></select>
              <select v-model="current.precondition.operator" :disabled="!editable"><option value="EQ">等于</option><option value="NE">不等于</option><option v-if="conditionField?.valueType === 'NUMBER'" value="LT">小于</option><option v-if="conditionField?.valueType === 'NUMBER'" value="GTE">大于等于</option></select>
              <select v-if="conditionField?.valueType === 'BOOLEAN'" v-model="current.precondition.value" :disabled="!editable"><option :value="true">是</option><option :value="false">否</option></select>
              <input v-else-if="conditionField?.valueType === 'NUMBER'" v-model.number="current.precondition.value" type="number" :disabled="!editable" />
              <input v-else v-model="current.precondition.value" :disabled="!editable" />
            </div>
            <p v-else class="note-caption">无前置状态条件；完成判断固定为“所选有限动作由P2规则服务执行成功”。</p>
          </div>
          <label class="field"
            ><span>操作成功后显示什么结果</span
            ><textarea v-model="current.expectedResult" :disabled="!editable" />
          </label>
          <div class="step-rule-summary">
            <Icon name="CircleCheck" :size="20" /><span
              >通过条件：按顺序选择<strong>{{
                objectLabel(current.target)
              }}</strong
              >，执行<strong>{{ current.actionLabel }}</strong
              >。选择错误对象会保留本步并反馈原因。</span
            >
          </div>
          <div class="button-row">
            <button
              class="btn secondary"
              :disabled="stepIndex === 0"
              @click="modern && editable ? moveStep(-1) : stepIndex--"
            >
              {{ modern && editable ? '上移步骤' : '上一个步骤' }}</button
            ><button
              class="btn secondary"
              :disabled="stepIndex === draft.steps.length - 1"
              @click="modern && editable ? moveStep(1) : stepIndex++"
            >
              {{ modern && editable ? '下移步骤' : '下一个步骤' }}</button
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
    :title="`新建${systemName(domain)}课件`"
    @close="creating = false"
    ><div class="form-stack">
      <label class="field"
        ><span>课程名称 *</span><input v-model="newName" /></label
      ><label class="field"
        ><span>教学目标 *</span><textarea v-model="newDescription" /></label
      ><label class="field"><span>课件契约</span><select v-model="contractVersion"><option value="V3">V3 · 绑定模板与自由步骤</option><option value="V2">V2 · 原训练兼容课件</option></select></label>
      <template v-if="contractVersion === 'V3'">
        <label class="field"><span>加入已有课程（可选）</span><select v-model="existingCurriculumId" @change="existingUnitId = selectedCurriculum?.units?.[0]?.id || ''"><option value="">新建课程组织</option><option v-for="item in curricula" :key="item.id" :value="item.id">{{ item.name }} · {{ item.units?.length || 0 }}课时</option></select></label>
        <label v-if="existingCurriculumId" class="field"><span>选择课时 *</span><select v-model="existingUnitId"><option v-for="item in selectedCurriculum?.units || []" :key="item.id" :value="item.id">第{{ item.order }}课时 · {{ item.name }}</option></select></label>
        <label v-if="!existingCurriculumId" class="field"><span>课程组织名称 *</span><input v-model="curriculumName" /></label>
        <label v-if="!existingCurriculumId" class="field"><span>应掌握的能力 *</span><textarea v-model="curriculumObjective" /></label>
        <label v-if="!existingCurriculumId" class="field"><span>课时名称 *</span><input v-model="unitName" /></label>
        <label class="field"><span>仿真环境模板 *</span><select v-model="templateId"><option value="">请选择已发布模板</option><option v-for="item in templates" :key="item.id" :value="item.id">{{ item.name }} · V{{ item.version }} · {{ item.sceneSnapshot.objects.length }}对象</option></select></label>
      </template>
      <label v-if="contractVersion === 'V2' && domain === 'MAINTENANCE'" class="field"
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
        {{ contractVersion === 'V3' ? '创建后生成5个稳定编号的空白步骤，可增删、复制、排序，并从绑定场景动态选择对象和动作。' : `兼容课件继续载入${domain === 'OPERATION' ? 8 : 10}步旧模板，可立即用于原训练金标。` }}
      </div>
    </div>
    <template #footer
      ><button class="btn secondary" @click="creating = false">取消</button
      ><button
        class="btn primary"
        :disabled="!newName.trim() || !newDescription.trim() || (contractVersion === 'V3' && ((!existingCurriculumId && (!curriculumName.trim() || !curriculumObjective.trim() || !unitName.trim())) || (existingCurriculumId && !existingUnitId) || !templateId)) || store.busy > 0"
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
    @close="closePreview"
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
      :objects="modern ? availableObjects : undefined"
      :states="modern ? previewStates : undefined"
      :topology="modern ? boundTemplate?.topologySnapshot : undefined"
      :domain="domain"
      :scene-title="`${systemName(domain)} · ${course.name}`"
      @submit="tryPreview"
    /><template #footer
      ><button class="btn secondary" @click="closePreview">
        返回课件制作</button
      ><button
        class="btn primary"
        :disabled="!previewResult?.ok || stepIndex >= draft.steps.length - 1"
        @click="continuePreview"
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
      <label class="field"><span>训练模式</span><select v-model="assignmentMode"><option value="GUIDED">引导训练</option><option value="FREE">自由训练</option><option value="DEMONSTRATION">演示讲解（不计分）</option></select></label>
      <label class="field"><span>完成期限</span><input v-model="assignmentDueAt" type="datetime-local" /></label>
      <label class="field"><span>指定台位（可选）</span><select v-model="assignmentStationId"><option value="">无需指定台位</option><option v-for="station in stations" :key="station.id" :value="station.id">{{ station.name }} · {{ station.status === 'CONNECTED' ? '已连接' : '未连接' }}</option></select></label>
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

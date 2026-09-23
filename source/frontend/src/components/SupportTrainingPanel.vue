<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { command, download, login, notify, store } from "../store";
import Icon from "./Icon.vue";
import EquipmentSceneView from "./EquipmentSceneView.vue";

const props = defineProps<{ attempt: any; assignment: any }>();
const emit = defineEmits<{ issue: [] }>();
const practice = computed(() => props.attempt.supportTraining);
const checkpoints = computed(() => practice.value?.checkpoints || {});
const operation = computed(() => practice.value?.operations?.[0]);
const resource = computed(() => practice.value?.resources?.[0]);
const teachingSteps = computed(() => practice.value?.teachingSteps || []);
const teachingStep = (kind: string) => teachingSteps.value.find((item: any) => item.supportKind === kind);
const stageReady = (kind: string, checkpoint: string) => !teachingStep(kind) || !!checkpoints.value[checkpoint];
const duration = ref(30), capacity = ref(1), strategy = ref("EXPEDITE"), reflection = ref("根据延迟前后结果选择加急方案，并保留计算依据。对费用增加和工期改善分别说明。");
watch(() => props.attempt.id, () => {
  duration.value = Number(operation.value?.duration || 30);
  capacity.value = Number(resource.value?.capacity || 1);
}, { immediate: true });
const completed = computed(() => props.attempt.status === "COMPLETED");
const confirmed = computed(() => props.assignment?.confirmedAttemptIds?.includes(props.attempt.id) || store.data?.trainingArchives?.some((item: any) => item.attemptId === props.attempt.id));
const teacher = computed(() => store.accounts.find((item) => item.id === store.actor)?.role === "INSTRUCTOR");
async function run(action: string, payload: any, message: string) {
  return command(action, { id: props.attempt.id, ...payload }, message);
}
async function useTeacher() {
  try { await login("INSTRUCTOR"); notify("已切换教员，可确认证据"); }
  catch (error: any) { notify(error.message, "error"); }
}
</script>

<template>
  <section class="panel support-training-panel">
    <div class="panel-header">
      <div><span class="task-eyebrow">保障教学案例副本 · {{ practice.id }}</span><h3>{{ practice.name }}</h3></div>
      <span class="pill">TRAINING · 不修改正式预案</span>
    </div>
    <div class="panel-body">
      <div class="info-note">
        <Icon name="ShieldCheck" :size="20" />
        <div><b>{{ practice.taskObject }}</b><p>{{ practice.scope }}；时限 T+{{ practice.deadline }} 分钟。工期/费用是方案指标，学习分数按教学阶段单独计算。</p></div>
      </div>
      <EquipmentSceneView :context-id="attempt.id" domain="SUPPORT" :title="`保障任务筹划及行为演练 · ${practice.name}`" :scene="attempt.courseSnapshot?.sceneSnapshot" :topology="attempt.courseSnapshot?.topologySnapshot" :states="attempt.runtime?.states" :mode="completed ? 'result' : 'training'" compact show-relations />
      <div v-if="practice.status === 'PLAN_EDITING'" class="info-note warning"><Icon name="CircleAlert" :size="18" /><div><b>方案已修改，旧计算和处置结果已失效</b><p>当前方案版本 R{{ practice.planRevision }}。请重新运行计算、注入事件并完成处置后再提交复盘。</p></div></div>

      <div v-if="!completed" class="support-stage-list">
        <article v-if="teachingStep('TASK_CONFIRM')" :class="{ done: checkpoints.task }">
          <span>1</span><div><b>{{ teachingStep('TASK_CONFIRM').name }} · {{ teachingStep('TASK_CONFIRM').points }}分</b><p>{{ teachingStep('TASK_CONFIRM').description }}</p></div>
          <button class="btn primary small" :disabled="checkpoints.task || store.busy > 0" @click="run('training.support.confirm', {}, '任务已确认')">确认任务</button>
        </article>
        <article v-if="teachingStep('RESOURCE_CONFIGURE')" :class="{ done: checkpoints.resource }">
          <span>2</span><div><b>{{ teachingStep('RESOURCE_CONFIGURE').name }} · {{ teachingStep('RESOURCE_CONFIGURE').points }}分</b><p>{{ teachingStep('RESOURCE_CONFIGURE').description }}；当前资源 {{ resource?.name }}（{{ resource?.id }}）。</p><label class="field"><span>可用数量</span><input v-model.number="capacity" type="number" min="0" max="20" /></label></div>
          <button class="btn primary small" :disabled="!stageReady('TASK_CONFIRM', 'task') || store.busy > 0" @click="run('training.support.resource', { resourceId: resource.id, capacity, available: capacity > 0 }, '训练资源已保存')">保存资源</button>
        </article>
        <article v-if="teachingStep('PLAN_EDIT')" :class="{ done: checkpoints.plan }">
          <span>3</span><div><b>{{ teachingStep('PLAN_EDIT').name }} · {{ teachingStep('PLAN_EDIT').points }}分</b><p>{{ teachingStep('PLAN_EDIT').description }}；当前工序 {{ operation?.name }}（{{ operation?.id }}）。</p><label class="field"><span>工序时长（分钟）</span><input v-model.number="duration" type="number" min="1" max="1440" /></label></div>
          <button class="btn primary small" :disabled="!stageReady('RESOURCE_CONFIGURE', 'resource') || store.busy > 0" @click="run('training.support.plan', { operationId: operation.id, duration }, '训练方案已保存')">保存工序</button>
        </article>
        <article v-if="teachingStep('PLAN_CALCULATE')" :class="{ done: checkpoints.calculate }">
          <span>4</span><div><b>{{ teachingStep('PLAN_CALCULATE').name }} · {{ teachingStep('PLAN_CALCULATE').points }}分</b><p>{{ teachingStep('PLAN_CALCULATE').description }}；计算结果仅写入本次训练副本。</p><p v-if="practice.result"><strong>工期 {{ practice.result.finish }} 分钟 · 费用 {{ practice.result.cost }} · 输入版本 R{{ practice.result.inputRevision }}</strong></p></div>
          <button class="btn primary small" :disabled="!stageReady('PLAN_EDIT', 'plan') || store.busy > 0" @click="run('training.support.calculate', {}, '排程和费用已重新计算')">运行后台计算</button>
        </article>
        <article v-if="teachingStep('EVENT_HANDLE')" :class="{ done: checkpoints.resolve }">
          <span>5</span><div><b>{{ teachingStep('EVENT_HANDLE').name }} · {{ teachingStep('EVENT_HANDLE').points }}分</b><p>{{ teachingStep('EVENT_HANDLE').description }}；处置前后结果均保留。</p><div v-if="practice.delayedResult" class="task-facts"><span>延迟后工期 {{ practice.delayedResult.finish }}</span><span>费用 {{ practice.delayedResult.cost }}</span></div><label class="field"><span>处置策略</span><select v-model="strategy"><option value="EXPEDITE">加急到货 T+135</option><option value="WAIT">维持等待</option></select></label></div>
          <div class="button-row"><button class="btn secondary small" :disabled="!checkpoints.calculate || practice.status === 'DELAY_INJECTED' || practice.status === 'RESOLVED' || store.busy > 0" @click="run('training.support.delay', { arrivalTime: 180 }, '延迟事件已注入')">注入延迟</button><button class="btn primary small" :disabled="practice.status !== 'DELAY_INJECTED' || store.busy > 0" @click="run('training.support.resolve', { strategy, arrivalTime: 135, transportCost: 140 }, '事件处置完成')">执行处置</button></div>
        </article>
        <article v-if="teachingStep('REVIEW_SUBMIT')" :class="{ done: checkpoints.submit }">
          <span>6</span><div><b>{{ teachingStep('REVIEW_SUBMIT').name }} · {{ teachingStep('REVIEW_SUBMIT').points }}分</b><p>{{ teachingStep('REVIEW_SUBMIT').description }}</p><p v-if="practice.resolvedResult"><strong>处置后工期 {{ practice.resolvedResult.finish }} 分钟 · 费用 {{ practice.resolvedResult.cost }} · 输入版本 R{{ practice.resolvedResult.inputRevision }}</strong></p><label class="field"><span>复盘结论</span><textarea v-model="reflection" /></label></div>
          <button class="btn primary small" :disabled="!(teachingStep('EVENT_HANDLE') ? checkpoints.resolve : checkpoints.calculate) || reflection.trim().length < 5 || store.busy > 0" @click="run('training.support.submit', { reflection }, '保障教学训练已完成')">提交复盘</button>
        </article>
      </div>

      <div v-else class="training-complete support-complete">
        <span class="task-eyebrow">保障教学训练完成</span><h2>方案结果与学习成绩分别记录</h2>
        <div class="completion-metrics"><div><b>{{ attempt.evaluation?.planMetrics?.finish }}</b><span>方案工期（分钟）</span></div><div><b>{{ attempt.evaluation?.planMetrics?.cost }}</b><span>方案费用</span></div><div><b>{{ attempt.evaluation?.learningScore }}</b><span>学习成绩</span></div><div><b>{{ attempt.events.length }}</b><span>教学事件</span></div></div>
        <div class="info-note"><b>指标含义：</b>工期/费用评价本次训练方案；学习成绩按发布课件的 {{ teachingSteps.length }} 个配置步骤与冻结分值计算，两者不互相替代。</div>
        <div class="button-row"><button class="btn secondary" @click="download('attempts', attempt.id)">导出训练报告</button><button class="btn secondary" @click="emit('issue')">记录可选问题</button><button v-if="!confirmed && teacher" class="btn primary" @click="command('training.confirm', { id: attempt.id }, '已确认本次培训证据')">确认证据并归档</button><button v-else-if="!confirmed" class="btn secondary" @click="useTeacher">演示：切换教员验收</button><span v-else class="pill">证据已确认归档</span></div>
      </div>
    </div>
  </section>
</template>

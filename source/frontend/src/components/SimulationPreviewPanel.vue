<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { store, command } from "../store";
import Icon from "./Icon.vue";
import Badge from "./Badge.vue";
import EquipmentSceneView from "./EquipmentSceneView.vue";

const props = defineProps<{ project?: any; scene?: any; template?: any }>();
const selectedPreviewId = ref("");
const selectedObjectId = ref("");
const selectedActionId = ref("");
const numberValue = ref(0.5);
const booleanValue = ref(false);
const previews = computed(() =>
  (store.data?.simulationPreviews || []).filter((item: any) =>
    props.template ? item.templateRef?.id === props.template.id : item.projectRef?.id === props.project?.id,
  ),
);
const preview = computed(() => previews.value.find((item: any) => item.id === selectedPreviewId.value) || previews.value.at(-1));
const objects = computed(() => preview.value?.objectsSnapshot || props.scene?.objects || props.template?.sceneSnapshot?.objects || []);
const selectedObject = computed(() => objects.value.find((item: any) => item.id === selectedObjectId.value));
const actions = computed(() => selectedObject.value?.actions || []);
const selectedAction = computed(() => actions.value.find((item: any) => item.id === selectedActionId.value));
const requiresNumber = computed(() => selectedAction.value?.parameters?.some((item: any) => item.valueType === "NUMBER"));
const requiresBoolean = computed(() => selectedAction.value?.parameters?.some((item: any) => item.valueType === "BOOLEAN"));

watch(previews, (items) => { if (!selectedPreviewId.value && items.length) selectedPreviewId.value = items.at(-1).id; }, { immediate: true });
watch(objects, (items) => { if (!items.some((item: any) => item.id === selectedObjectId.value)) selectedObjectId.value = items[0]?.id || ""; }, { immediate: true });
watch(actions, (items) => { if (!items.some((item: any) => item.id === selectedActionId.value)) selectedActionId.value = items[0]?.id || ""; }, { immediate: true });

async function start() {
  const result = props.template
    ? await command("simulationTemplate.preview", { id: props.template.id }, "已按已发布版本创建只读调试实例")
    : await command("simulationPreview.start", { projectId: props.project.id }, "已创建独立调试实例");
  if (result) selectedPreviewId.value = result.id;
}
async function execute() {
  if (!preview.value || !selectedAction.value) return;
  const parameters: any = {};
  if (requiresNumber.value) parameters.value = numberValue.value;
  if (requiresBoolean.value) parameters.value = booleanValue.value;
  await command("simulationPreview.action", { id: preview.value.id, objectId: selectedObjectId.value, actionId: selectedActionId.value, parameters }, "调试动作已执行");
}
async function reset() {
  await command("simulationPreview.reset", { id: preview.value.id }, "当前调试实例已复位");
}
async function close() {
  await command("simulationPreview.close", { id: preview.value.id }, "当前调试实例已结束");
}
const stateText = (objectId: string) => JSON.stringify(preview.value?.states?.[objectId] || {});
</script>

<template>
  <section class="preview-layout">
    <div class="panel preview-controls">
      <div class="panel-header"><div><h3>独立调试实例</h3><small>{{ template ? '始终读取该模板版本的不可变快照' : '启动时冻结当前工程对象和规则，不修改场景初始状态' }}</small></div><button class="btn primary" @click="start"><Icon name="Plus" :size="15" />新建实例</button></div>
      <div v-if="preview" class="panel-body">
        <label class="field"><span>当前实例</span><select v-model="selectedPreviewId"><option v-for="item in previews" :key="item.id" :value="item.id">{{ item.id }} · {{ item.status === 'ACTIVE' ? '运行中' : '已结束' }}</option></select></label>
        <div class="form-grid preview-action-form">
          <label class="field"><span>操作对象</span><select v-model="selectedObjectId"><option v-for="item in objects" :key="item.id" :value="item.id">{{ item.name }}</option></select></label>
          <label class="field"><span>有限动作</span><select v-model="selectedActionId"><option v-for="item in actions" :key="item.id" :value="item.id">{{ item.label }}</option></select></label>
          <label v-if="requiresNumber" class="field"><span>数值参数</span><input v-model.number="numberValue" type="number" step="0.1" /></label>
          <label v-if="requiresBoolean" class="field"><span>布尔参数</span><select v-model="booleanValue"><option :value="true">是 / 就绪</option><option :value="false">否 / 未就绪</option></select></label>
        </div>
        <div class="button-row"><button class="btn primary" :disabled="preview.status !== 'ACTIVE' || !selectedActionId" @click="execute"><Icon name="Play" :size="15" />执行动作</button><button class="btn secondary" :disabled="preview.status !== 'ACTIVE'" @click="reset">复位本实例</button><button class="btn secondary" :disabled="preview.status !== 'ACTIVE'" @click="close">结束实例</button></div>
      </div>
      <div v-else class="object-empty">尚未启动调试。NONE 模式也可以执行对象自己的有限动作。</div>
    </div>

    <div v-if="preview" class="panel preview-state">
      <div class="panel-header"><div><h3>对象运行状态</h3><small>{{ preview.id }}</small></div><Badge :status="preview.status" /></div>
      <div class="panel-body state-grid"><article v-for="item in objects" :key="item.id"><span>{{ item.id }}</span><b>{{ item.name }}</b><code>{{ stateText(item.id) }}</code></article></div>
    </div>
  </section>
  <section v-if="preview" class="panel preview-events">
    <div class="panel-header"><div><h3>调试事件</h3><small>按顺序显示输入、命中规则、前后状态和拒绝原因</small></div><span>{{ preview.events.length }} 条</span></div>
    <div class="panel-body event-list"><article v-for="event in [...preview.events].reverse()" :key="event.id" :class="event.result"><div class="event-sequence">#{{ event.sequence }}</div><div><b>{{ event.actionId }}</b><p>{{ event.reason }}</p><small v-if="event.matchedRules?.length">命中规则：{{ event.matchedRules.map((item: any) => item.id).join('、') }}</small></div><Badge :status="event.result" /><details><summary>查看前后状态</summary><pre>{{ JSON.stringify({ before: event.beforeState, after: event.afterState }, null, 2) }}</pre></details></article><div v-if="!preview.events.length" class="object-empty">执行动作后将在这里形成可解释记录。</div></div>
  </section>
  <EquipmentSceneView v-if="preview" :context-id="preview.id" :domain="preview.domain" :title="`${template?.name || project?.name || '工程调试'} · 实时状态`" :scene="{ objects, environment: scene?.environment || template?.sceneSnapshot?.environment || {} }" :topology="preview.topologySnapshot" :states="preview.states" :selected-object-id="selectedObjectId" mode="preview" show-relations @select="selectedObjectId = $event" />
</template>

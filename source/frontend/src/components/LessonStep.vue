<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { lessonStep, trainingObjects, objectName } from "../lesson";
import EquipmentSceneView from "./EquipmentSceneView.vue";
import Icon from "./Icon.vue";
const props = defineProps<{
  step: any;
  objects?: any[];
  states?: Record<string, any>;
  topology?: any;
  domain?: string;
  sceneTitle?: string;
  disabled?: boolean;
  free?: boolean;
  state?: string;
}>();
const emit = defineEmits<{
  submit: [payload: { target: string; actionId: string; parameters: Record<string, any> }];
}>();
const selectedObject = ref("");
const selectedAction = ref("");
const actualParameters = ref<Record<string, any>>({});
const info = computed(() => lessonStep(props.step));
const availableObjects = computed(() => props.objects?.length ? props.objects : trainingObjects);
const nameOf = (id: string) => availableObjects.value.find((item: any) => item.id === id)?.name || objectName(id);
const selectedDefinition = computed(() => availableObjects.value.find((item: any) => item.id === selectedObject.value));
const availableActions = computed(() => selectedDefinition.value?.actions || []);
const selectedActionDefinition = computed(() => availableActions.value.find((item: any) => item.id === selectedAction.value));
function defaultValue(definition: any) {
  const expected = props.step?.parameters || {};
  if (!props.free && selectedAction.value === info.value?.actionId && definition.id in expected) return expected[definition.id];
  return definition.valueType === "BOOLEAN" ? false : definition.valueType === "NUMBER" ? 0 : "";
}
function selectAction() {
  actualParameters.value = Object.fromEntries((selectedActionDefinition.value?.parameters || []).map((item: any) => [item.id, defaultValue(item)]));
}
function selectObject(id: string) {
  selectedObject.value = id;
  const actions = availableObjects.value.find((item: any) => item.id === id)?.actions || [];
  selectedAction.value = !props.free && id === info.value?.target && actions.some((item: any) => item.id === info.value?.actionId)
    ? info.value.actionId
    : actions[0]?.id || info.value?.actionId || "";
  selectAction();
}
watch(
  () => props.step?.id,
  () => {
    selectedObject.value = "";
    selectedAction.value = "";
    actualParameters.value = {};
  },
);
</script>
<template>
  <div v-if="info" class="lesson-workspace">
    <div class="lesson-scene">
      <EquipmentSceneView
        :context-id="`lesson-${step?.id || 'step'}`"
        :domain="domain || 'OPERATION'"
        :title="sceneTitle || '训练设备场景'"
        :scene="{ objects: availableObjects, environment: {} }"
        :topology="topology"
        :states="states"
        :selected-object-id="selectedObject"
        :target-object-id="free ? '' : info.target"
        mode="training"
        compact
        @select="selectObject($event)"
      />
      <p>点击场景对象或右侧对象按钮进行选择。场景动作为虚拟平台示意。</p>
    </div>
    <div class="lesson-actions">
      <div class="lesson-instruction">
        <span class="task-eyebrow">本步任务</span>
        <h3>{{ info.name }}</h3>
        <p>
          {{
            free
              ? "请根据课程目标选择对象并完成当前操作。需要操作说明时可查看提示。"
              : info.description
          }}
        </p>
      </div>
      <fieldset class="object-picker" :disabled="disabled">
        <legend>
          ① 选择操作对象<span v-if="!free">
            · 本步目标：{{ nameOf(info.target) }}</span
          >
        </legend>
        <div>
          <button
            v-for="object in availableObjects"
            :key="object.id"
            type="button"
            :class="{ selected: selectedObject === object.id }"
            :aria-pressed="selectedObject === object.id"
            @click="selectObject(object.id)"
          >
            <Icon :name="object.icon" :size="20" /><b>{{ object.name }}</b
            ><small>{{ object.id }}</small>
          </button>
        </div>
      </fieldset>
      <div class="lesson-submit">
        <b>② 执行具体操作</b>
        <p>
          当前选择：<strong>{{ nameOf(selectedObject) }}</strong>
        </p>
        <label v-if="objects?.length && availableActions.length" class="field">
          <span>设备动作</span>
          <select v-model="selectedAction" :disabled="disabled" @change="selectAction">
            <option v-for="action in availableActions" :key="action.id" :value="action.id">{{ action.label || action.id }}</option>
          </select>
        </label>
        <div v-if="selectedActionDefinition?.parameters?.length" class="form-grid parameter-fields">
          <label v-for="parameter in selectedActionDefinition.parameters" :key="parameter.id" class="field">
            <span>动作参数 · {{ parameter.id }}</span>
            <select v-if="parameter.valueType === 'BOOLEAN'" v-model="actualParameters[parameter.id]" :disabled="disabled"><option :value="true">是</option><option :value="false">否</option></select>
            <input v-else-if="parameter.valueType === 'NUMBER'" v-model.number="actualParameters[parameter.id]" type="number" :disabled="disabled" />
            <input v-else v-model="actualParameters[parameter.id]" :disabled="disabled" />
          </label>
        </div>
        <p v-if="objects?.length && selectedObject" class="lesson-hint">当前状态：{{ JSON.stringify(states?.[selectedObject] || {}) }}</p>
        <button
          class="btn primary full"
          :disabled="disabled || !selectedObject || !selectedAction"
          @click="
            emit('submit', { target: selectedObject, actionId: selectedAction, parameters: actualParameters })
          "
        >
          <Icon name="MousePointer2" :size="18" />{{ !selectedAction || selectedAction === info.actionId ? info.actionLabel : selectedActionDefinition?.label || selectedAction }}</button
        ><span v-if="!selectedObject" class="lesson-hint"
          >先选一个对象，操作按钮才会启用。</span
        >
      </div>
      <div v-if="!free" class="lesson-expected">
        <b>③ 完成后应该看到</b>
        <p>{{ info.expectedResult }}</p>
        <p v-if="info.precondition">前置条件：{{ nameOf(info.precondition.objectId) }}.{{ info.precondition.field }} {{ info.precondition.operator }} {{ info.precondition.value }}</p>
      </div>
    </div>
  </div>
</template>

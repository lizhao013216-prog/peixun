<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { lessonStep, trainingObjects, objectName } from "../lesson";
import SceneView from "./SceneView.vue";
import Icon from "./Icon.vue";
const props = defineProps<{
  step: any;
  disabled?: boolean;
  free?: boolean;
  state?: string;
}>();
const emit = defineEmits<{
  submit: [payload: { target: string; actionId: string }];
}>();
const selectedObject = ref("");
const info = computed(() => lessonStep(props.step));
watch(
  () => props.step?.id,
  () => (selectedObject.value = ""),
);
</script>
<template>
  <div v-if="info" class="lesson-workspace">
    <div class="lesson-scene">
      <SceneView
        :selected="selectedObject"
        :state="state"
        @select="selectedObject = $event"
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
            · 本步目标：{{ objectName(info.target) }}</span
          >
        </legend>
        <div>
          <button
            v-for="object in trainingObjects"
            :key="object.id"
            type="button"
            :class="{ selected: selectedObject === object.id }"
            :aria-pressed="selectedObject === object.id"
            @click="selectedObject = object.id"
          >
            <Icon :name="object.icon" :size="20" /><b>{{ object.name }}</b
            ><small>{{ object.id }}</small>
          </button>
        </div>
      </fieldset>
      <div class="lesson-submit">
        <b>② 执行具体操作</b>
        <p>
          当前选择：<strong>{{ objectName(selectedObject) }}</strong>
        </p>
        <button
          class="btn primary full"
          :disabled="disabled || !selectedObject"
          @click="
            emit('submit', { target: selectedObject, actionId: info.actionId })
          "
        >
          <Icon name="MousePointer2" :size="18" />{{ info.actionLabel }}</button
        ><span v-if="!selectedObject" class="lesson-hint"
          >先选一个对象，操作按钮才会启用。</span
        >
      </div>
      <div v-if="!free" class="lesson-expected">
        <b>③ 完成后应该看到</b>
        <p>{{ info.expectedResult }}</p>
      </div>
    </div>
  </div>
</template>

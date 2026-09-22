<script setup lang="ts">
import { computed } from "vue";
import { fmt } from "../store";
const props = defineProps<{
  spans: any[];
  clock?: number;
  compact?: boolean;
}>();
const max = computed(() => Math.max(300, ...props.spans.map((s) => s.end)));
const ticks = computed(() =>
  Array.from({ length: 6 }, (_, i) => Math.round((max.value * i) / 5)),
);
</script>
<template>
  <div class="gantt" :class="{ compact }">
    <div class="gantt-heading">
      <span>工序 / 资源</span>
      <div class="gantt-axis">
        <span
          v-for="tick in ticks"
          :key="tick"
          :style="{ left: `${(tick / max) * 100}%` }"
          >{{ tick }}<small v-if="tick === ticks.at(-1)"> min</small></span
        >
      </div>
      <span>时长</span>
    </div>
    <div v-for="(s, i) in spans" :key="s.id || s.operationId" class="gantt-row">
      <div class="gantt-label">
        <b>{{ s.id || s.operationId }}</b
        ><span>{{ s.name }}</span>
      </div>
      <div class="gantt-track">
        <i
          v-for="tick in ticks"
          :key="tick"
          class="gantt-grid"
          :style="{ left: `${(tick / max) * 100}%` }"
        ></i
        ><span
          class="gantt-bar"
          :class="{
            done: clock !== undefined && clock >= s.end,
            active: clock !== undefined && clock >= s.start && clock < s.end,
            teal: i % 3 === 1,
          }"
          :style="{
            left: `${(s.start / max) * 100}%`,
            width: `${((s.end - s.start) / max) * 100}%`,
          }"
          :title="`${s.name}: ${s.start}–${s.end}分钟`"
          >{{ fmt(s.start) }}–{{ fmt(s.end) }}</span
        ><i
          v-if="clock !== undefined"
          class="clock-line"
          :style="{ left: `${(Math.min(clock, max) / max) * 100}%` }"
        ></i>
      </div>
      <span class="number muted"
        >{{ fmt(s.end - s.start) }}<small> min</small></span
      >
    </div>
  </div>
</template>

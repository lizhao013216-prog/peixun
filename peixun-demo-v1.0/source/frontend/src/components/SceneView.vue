<script setup lang="ts">
import { ref, computed, watch } from "vue";
import Icon from "./Icon.vue";
const props = defineProps<{
  state?: string;
  weather?: string;
  selected?: string;
  compact?: boolean;
  camera?: string;
  frameAngle?: number;
}>();
const emit = defineEmits(["select"]);
const selected = ref(props.selected || "PUMP-01");
watch(
  () => props.selected,
  (value) => {
    if (value) selected.value = value;
  },
);
const active = computed(() => /RUNNING|STEP|COMPLETED/.test(props.state || ""));
const alarm = computed(() => props.state === "ALARM");
function select(id: string) {
  selected.value = id;
  emit("select", id);
}
</script>
<template>
  <div class="scene-view" :class="{ compact, night: weather === '夜间' }">
    <div class="scene-caption">
      <span class="scene-dot"></span>维修工位 · {{ camera || "总览视角"
      }}<span class="scene-tag">交互示意场景</span>
    </div>
    <svg
      :viewBox="
        camera === '设备'
          ? '230 105 375 300'
          : camera === '操作员'
            ? '180 80 580 355'
            : '0 0 840 440'
      "
      role="img"
      aria-label="可交互的泵组维修工位示意图"
    >
      <defs>
        <linearGradient id="floor" x1="0" y1="0" x2="1" y2="1">
          <stop stop-color="#e6edf3" />
          <stop offset="1" stop-color="#cad8e4" />
        </linearGradient>
        <linearGradient id="pump" x1="0" y1="0" x2="0" y2="1">
          <stop stop-color="#7696b2" />
          <stop offset="1" stop-color="#365871" />
        </linearGradient>
        <linearGradient id="motor" x1="0" x2="1">
          <stop stop-color="#456378" />
          <stop offset=".45" stop-color="#7293a5" />
          <stop offset="1" stop-color="#365064" />
        </linearGradient>
        <filter id="shadow"><feGaussianBlur stdDeviation="8" /></filter>
      </defs>
      <path d="M85 260 405 80 780 270 460 430Z" fill="url(#floor)" />
      <path d="M85 260 460 430 460 441 85 272Z" fill="#afc2d1" />
      <path d="M460 430 780 270 780 280 460 441Z" fill="#93aebe" />
      <g stroke="#bccbd7" stroke-width="1" opacity=".75">
        <path
          v-for="i in 8"
          :key="i"
          :d="`M${85 + i * 40} ${260 - i * 22.5} l375 170`"
        />
        <path
          v-for="i in 9"
          :key="i"
          :d="`M${85 + i * 37.5} ${260 + i * 17} l320 -180`"
        />
      </g>
      <g opacity=".65">
        <path d="M96 238V132L394-25v111" fill="#e9f0f5" />
        <path
          d="M111 126v105M192 82v105M275 39v105M357-4v105"
          stroke="#cedce6"
          stroke-width="5"
        />
        <path d="M106 162 388 12" stroke="#bacfdc" stroke-width="3" />
      </g>
      <ellipse
        cx="435"
        cy="315"
        rx="178"
        ry="41"
        fill="#637f91"
        opacity=".23"
        filter="url(#shadow)"
      />
      <g
        @click="select('CTRL-01')"
        class="scene-object"
        :class="{ selected: selected === 'CTRL-01' }"
        tabindex="0"
        role="button"
        aria-label="选择控制单元"
        @keydown.enter="select('CTRL-01')"
      >
        <path d="M619 167 664 143 705 165 660 191Z" fill="#d0dce5" />
        <path d="M619 167v117l41 23V191Z" fill="#a8bac8" />
        <path d="M660 191 705 165v117l-45 25Z" fill="#718a9f" />
        <path d="M629 179 650 191v27l-21-12Z" fill="#173c51" />
        <path d="M633 190l12 6m-12 1 9 5" stroke="#70d1be" stroke-width="3" />
        <ellipse
          cx="637"
          cy="235"
          rx="4"
          ry="5"
          :fill="active ? '#48d2b0' : '#95abbc'"
        />
        <ellipse
          cx="647"
          cy="241"
          rx="3"
          ry="4"
          :fill="alarm ? '#f77860' : '#d9b661'"
        />
        <path d="M633 258l16 9" stroke="#809bae" stroke-width="3" />
      </g>
      <g
        @click="select('PUMP-01')"
        class="scene-object"
        :class="{ selected: selected === 'PUMP-01' }"
        tabindex="0"
        role="button"
        aria-label="选择泵组"
        @keydown.enter="select('PUMP-01')"
      >
        <path d="M241 290 422 194 593 279 411 377Z" fill="#456178" />
        <path d="M241 290v12l170 87v-12Z" fill="#2e495f" />
        <path d="M411 377 593 279v12L411 389Z" fill="#19364c" />
        <path d="M270 286 422 204 568 279 415 362Z" fill="#b4c6d2" />
        <path d="M402 246 478 205 547 242 471 283Z" fill="#98b0bf" />
        <path d="M402 246v60l69 34v-57Z" fill="#4e728c" />
        <path d="M471 283 547 242v60l-76 38Z" fill="#31546f" />
        <g stroke="#b6c9d2" stroke-width="3">
          <path
            v-for="i in 7"
            :key="i"
            :d="`M${408 + i * 8} ${249 + i * 4} v44`"
          />
        </g>
        <path d="M443 210v-13l35-18 36 18v15l-36 19Z" fill="#345569" />
        <path d="M306 259 370 224 430 254v54l-63 34-61-30Z" fill="url(#pump)" />
        <ellipse
          cx="341"
          cy="278"
          rx="30"
          ry="39"
          transform="rotate(-25 341 278)"
          fill="#395c79"
          stroke="#86a9bf"
          stroke-width="8"
        />
        <ellipse
          cx="341"
          cy="278"
          rx="17"
          ry="23"
          transform="rotate(-25 341 278)"
          fill="#243f55"
          stroke="#6589a2"
          stroke-width="4"
        />
        <circle
          cx="341"
          cy="278"
          r="7"
          :fill="alarm ? '#f18465' : active ? '#63d6ba' : '#b8ccd6'"
        />
        <path
          d="M366 239v-40l39-20"
          stroke="#87a4b8"
          stroke-width="24"
          fill="none"
        />
        <path
          d="M366 239v-40l39-20"
          stroke="#b6cbd6"
          stroke-width="7"
          fill="none"
        />
        <path
          d="M313 304 279 322v26"
          fill="none"
          stroke="#819bad"
          stroke-width="19"
        />
        <circle
          v-for="b in [
            [288, 288],
            [416, 353],
            [544, 280],
            [423, 215],
          ]"
          :cx="b[0]"
          :cy="b[1]"
          r="4"
          fill="#dbe5eb"
        />
      </g>
      <g
        @click="select('VALVE-01')"
        class="scene-object"
        :class="{ selected: selected === 'VALVE-01' }"
        tabindex="0"
        role="button"
        aria-label="选择阀门"
        @keydown.enter="select('VALVE-01')"
      >
        <path
          d="M248 222 312 188 341 200 370 183"
          fill="none"
          stroke="#8cabbc"
          stroke-width="16"
        />
        <path d="M306 188v-31" stroke="#3f7781" stroke-width="8" />
        <ellipse
          cx="306"
          cy="150"
          rx="30"
          ry="12"
          fill="none"
          stroke="#338c8f"
          stroke-width="6"
        />
        <path
          :transform="`rotate(${frameAngle || 0} 306 150)`"
          d="M280 150h52m-26-9v19"
          stroke="#338c8f"
          stroke-width="4"
        />
      </g>
      <g
        @click="select('SENSOR-01')"
        class="scene-object"
        tabindex="0"
        role="button"
        aria-label="选择传感器"
        @keydown.enter="select('SENSOR-01')"
      >
        <path d="M428 203v-37" stroke="#9badb8" stroke-width="7" />
        <circle
          cx="429"
          cy="159"
          r="15"
          fill="#e6eff4"
          stroke="#5a7b8c"
          stroke-width="5"
        />
        <path d="m429 159 7-6" stroke="#298e94" stroke-width="3" />
        <circle cx="429" cy="159" r="2" fill="#294d67" />
      </g>
      <g stroke="#7797ac" fill="none" stroke-dasharray="5 5">
        <path d="M468 282 570 329 644 288" />
        <path d="M429 141v-25h84" />
      </g>
      <g font-family="Demo Sans SC,Microsoft Yahei,sans-serif" font-size="12">
        <rect
          x="499"
          y="98"
          width="109"
          height="30"
          rx="6"
          fill="white"
          opacity=".96"
        />
        <text x="512" y="118" fill="#4c687f">SENSOR-01</text>
        <rect x="172" y="143" width="107" height="30" rx="6" fill="white" />
        <text x="185" y="163" fill="#4c687f">VALVE-01</text>
        <rect x="315" y="365" width="144" height="33" rx="6" fill="#183c58" />
        <circle cx="331" cy="382" r="4" :fill="alarm ? '#f08f74' : '#66d4bf'" />
        <text x="344" y="387" fill="white">PUMP-01 · 泵组</text>
        <text x="636" y="329" fill="#5b7689">CTRL-01</text>
      </g>
    </svg>
    <div class="scene-footer">
      <span><Icon name="MousePointer2" :size="13" /> 点击设备查看对象</span
      ><span class="mono">{{ selected }}</span
      ><span :class="alarm ? 'text-red' : 'text-teal'">{{
        alarm
          ? "状态异常"
          : state === "COMPLETED"
            ? "流程完成"
            : active
              ? "运行中"
              : "准备就绪"
      }}</span>
    </div>
  </div>
</template>

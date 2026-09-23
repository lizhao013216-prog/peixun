<script setup lang="ts">
import { computed, ref } from "vue";
import Icon from "./Icon.vue";

const props = withDefaults(defineProps<{
  contextId?: string;
  domain?: "OPERATION" | "MAINTENANCE" | "SUPPORT" | string;
  title?: string;
  scene?: any;
  topology?: any;
  states?: Record<string, any>;
  selectedObjectId?: string;
  targetObjectId?: string;
  mode?: "edit" | "preview" | "training" | "result";
  readOnly?: boolean;
  showRelations?: boolean;
  compact?: boolean;
}>(), {
  contextId: "scene",
  domain: "OPERATION",
  title: "设备训练场景",
  scene: () => ({ objects: [], environment: {} }),
  topology: () => ({ connections: [], rules: [] }),
  states: () => ({}),
  mode: "preview",
  readOnly: true,
  showRelations: true,
  compact: false,
});

const emit = defineEmits<{
  select: [id: string];
  move: [payload: { id: string; x: number; y: number }];
}>();
const zoom = ref(1);
const dragging = ref("");
const objects = computed(() => props.scene?.objects || []);
const objectMap = computed(() => Object.fromEntries(objects.value.map((item: any) => [item.id, item])));
const viewBox = computed(() => {
  const width = 1000 / zoom.value;
  const height = 560 / zoom.value;
  return `${(1000 - width) / 2} ${(560 - height) / 2} ${width} ${height}`;
});
const point = (object: any) => ({
  x: 80 + Math.max(0, Math.min(100, Number(object?.x ?? 50))) * 8.4,
  y: 82 + Math.max(0, Math.min(100, Number(object?.y ?? 50))) * 3.9,
});
function symbol(object: any) {
  const explicit = object?.visual?.symbolKey || object?.symbolKey;
  if (explicit) return String(explicit).toUpperCase();
  const actions = new Set((object?.actions || []).map((item: any) => item.id));
  if (actions.has("START")) return "PUMP";
  if (actions.has("SET_READY")) return "VALVE";
  if (actions.has("SET_VALUE")) return "SENSOR";
  if (actions.has("RECORD_CHECK")) return "TOOL";
  return "GENERIC";
}
function stateOf(object: any) {
  return props.states?.[object.id] || object.initialState || {};
}
function statusOf(object: any) {
  const value = String(stateOf(object).status || "READY").toUpperCase();
  if (value.includes("ALARM") || value.includes("ERROR") || stateOf(object).alarm === true) return "alarm";
  if (value.includes("RUN") || value.includes("ACTIVE") || value.includes("WORK")) return "running";
  if (value.includes("STOP") || value.includes("OFF")) return "stopped";
  return "ready";
}
const statusLabel: Record<string, string> = { ready: "就绪", running: "运行", stopped: "停止", alarm: "告警" };
function relationPath(connection: any) {
  const source = point(objectMap.value[connection?.source?.objectId]);
  const target = point(objectMap.value[connection?.target?.objectId]);
  return `M ${source.x} ${source.y} C ${(source.x + target.x) / 2} ${source.y}, ${(source.x + target.x) / 2} ${target.y}, ${target.x} ${target.y}`;
}
function select(id: string) {
  emit("select", id);
}
function pointerDown(event: PointerEvent, id: string) {
  select(id);
  if (props.readOnly) return;
  dragging.value = id;
  (event.currentTarget as Element).setPointerCapture?.(event.pointerId);
}
function pointerMove(event: PointerEvent) {
  if (!dragging.value || props.readOnly) return;
  const svg = (event.currentTarget as SVGSVGElement);
  const rect = svg.getBoundingClientRect();
  emit("move", {
    id: dragging.value,
    x: Math.max(0, Math.min(100, ((event.clientX - rect.left) / rect.width * 1000 - 80) / 8.4)),
    y: Math.max(0, Math.min(100, ((event.clientY - rect.top) / rect.height * 560 - 82) / 3.9)),
  });
}
</script>

<template>
  <section class="equipment-scene" :class="[{ compact }, `scene-domain-${domain.toLowerCase()}`]" :data-context="contextId">
    <header class="equipment-scene-head">
      <div><span class="scene-live-dot"></span><b>{{ title }}</b><small>{{ scene?.environment?.weather || "室内" }} · {{ scene?.environment?.light || "标准光照" }} · {{ objects.length }} 个对象</small></div>
      <div class="scene-tools">
        <button type="button" title="缩小" @click="zoom = Math.max(.75, zoom - .25)"><Icon name="Minus" :size="14" /></button>
        <span>{{ Math.round(zoom * 100) }}%</span>
        <button type="button" title="放大" @click="zoom = Math.min(1.75, zoom + .25)"><Icon name="Plus" :size="14" /></button>
        <button type="button" title="适应全部" @click="zoom = 1"><Icon name="Maximize2" :size="14" /></button>
      </div>
    </header>
    <div class="equipment-scene-stage">
      <svg :viewBox="viewBox" role="img" :aria-label="`${title}，共${objects.length}个可视对象`" @pointermove="pointerMove" @pointerup="dragging = ''" @pointercancel="dragging = ''">
        <defs>
          <linearGradient :id="`scene-floor-${contextId}`" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#edf3f7" /><stop offset="1" stop-color="#cfdee8" /></linearGradient>
          <marker :id="`scene-arrow-${contextId}`" markerWidth="7" markerHeight="7" refX="6" refY="3.5" orient="auto"><path d="M0 0 7 3.5 0 7Z" fill="#4a8fa3" /></marker>
          <filter :id="`scene-shadow-${contextId}`"><feDropShadow dx="0" dy="8" stdDeviation="8" flood-color="#173c58" flood-opacity=".18" /></filter>
        </defs>
        <path d="M45 145 430 28 955 166 570 528 45 382Z" :fill="`url(#scene-floor-${contextId})`" stroke="#b6cbd8" stroke-width="2" />
        <g class="scene-grid" opacity=".48"><path v-for="n in 10" :key="`h${n}`" :d="`M${65 + n * 40} ${145 - n * 12}  L${590 + n * 34} ${285 - n * 12}`" /><path v-for="n in 10" :key="`v${n}`" :d="`M${45 + n * 52} ${382 + n * 14} L${430 + n * 52} ${28 + n * 14}`" /></g>
        <g v-if="showRelations" class="scene-relations">
          <g v-for="connection in topology?.connections || []" :key="connection.id">
            <path :d="relationPath(connection)" :marker-end="`url(#scene-arrow-${contextId})`" />
            <text :x="(point(objectMap[connection.source.objectId]).x + point(objectMap[connection.target.objectId]).x) / 2" :y="(point(objectMap[connection.source.objectId]).y + point(objectMap[connection.target.objectId]).y) / 2 - 7">{{ connection.valueType || "信号" }}</text>
          </g>
        </g>
        <g v-for="object in objects" :key="object.id" class="equipment-symbol" :class="[statusOf(object), { selected: selectedObjectId === object.id, target: targetObjectId === object.id && mode !== 'training' }]" :transform="`translate(${point(object).x} ${point(object).y}) rotate(${Number(object?.visual?.rotation || 0)})`" tabindex="0" role="button" :aria-label="`${object.name}，${statusLabel[statusOf(object)]}`" @keydown.enter="select(object.id)" @pointerdown.stop="pointerDown($event, object.id)">
          <ellipse cx="0" cy="37" rx="56" ry="14" class="symbol-shadow" />
          <g v-if="symbol(object) === 'PUMP'" class="symbol-body"><rect x="-54" y="-9" width="108" height="55" rx="9" /><circle cx="-18" cy="13" r="25" /><circle cx="-18" cy="13" r="9" class="symbol-core" /><path d="M8 -2h39v30H8zM-18-14v-20h34" /></g>
          <g v-else-if="symbol(object) === 'VALVE'" class="symbol-body"><path d="M-45 5h90M-22-15 0 5l22-20v40L0 5l-22 20Z" /><path d="M0-15v-24M-24-40h48M0-48v16" /></g>
          <g v-else-if="symbol(object) === 'SENSOR'" class="symbol-body"><path d="M0 32V12" /><circle cx="0" cy="-10" r="25" /><path d="m0-10 12-11" /><circle cx="0" cy="-10" r="3" class="symbol-core" /></g>
          <g v-else-if="symbol(object) === 'CONTROL'" class="symbol-body"><rect x="-38" y="-42" width="76" height="84" rx="7" /><rect x="-25" y="-29" width="50" height="25" rx="3" class="symbol-screen" /><circle cx="-16" cy="17" r="5" class="symbol-core" /><circle cx="2" cy="17" r="5" /><path d="M-24 31h48" /></g>
          <g v-else-if="symbol(object) === 'TOOL'" class="symbol-body"><path d="m-39 27 45-45 15 15-45 45zM4-24c7-22 29-25 42-13L29-20l16 16c-12 13-35 9-41-7" /></g>
          <g v-else-if="symbol(object) === 'WORKBENCH'" class="symbol-body"><path d="M-53-18h106v22H-53zM-42 4v40M42 4v40M-26 4h52v25h-52z" /></g>
          <g v-else class="symbol-body generic"><path d="m0-42 44 24v50L0 56-44 32v-50Z" /><path d="M-44-18 0 8l44-26M0 8v48" /><text x="0" y="-5">?</text></g>
          <g class="symbol-label"><rect x="-72" y="58" width="144" height="40" rx="8" /><circle cx="-57" cy="72" r="5" /><text x="-46" y="76">{{ object.name }}</text><text x="-58" y="91" class="symbol-id">{{ object.id }} · {{ statusLabel[statusOf(object)] }}</text></g>
        </g>
        <g v-if="!objects.length" class="scene-empty"><rect x="320" y="205" width="360" height="110" rx="16" /><text x="500" y="252">当前快照尚未配置场景对象</text><text x="500" y="280">请在工程编排中添加已批准素材</text></g>
      </svg>
    </div>
    <footer class="equipment-scene-legend"><span><i class="ready"></i>就绪</span><span><i class="running"></i>运行</span><span><i class="stopped"></i>停止</span><span><i class="alarm"></i>告警</span><span v-if="showRelations" class="relation-legend">虚线表示逻辑信号，不表示真实管路</span></footer>
  </section>
</template>

<style scoped>
.equipment-scene{border:1px solid #c8d7e2;border-radius:14px;background:#f7fafc;overflow:hidden;color:#173c58}.equipment-scene-head{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:10px 13px;border-bottom:1px solid #d7e2e9;background:rgba(255,255,255,.9)}.equipment-scene-head>div{display:flex;align-items:center;gap:8px}.equipment-scene-head small{color:#6b7f8e}.scene-live-dot{width:8px;height:8px;border-radius:50%;background:#2da58d;box-shadow:0 0 0 4px rgba(45,165,141,.12)}.scene-tools button{display:grid;place-items:center;width:28px;height:28px;border:1px solid #c9d8e2;border-radius:7px;background:white;color:#31546f}.scene-tools span{min-width:42px;text-align:center;font-size:12px;color:#647b8b}.equipment-scene-stage{height:clamp(280px,37vw,500px);background:linear-gradient(180deg,#dbe8ef 0,#eef4f7 42%,#d2e0e8 100%)}.compact .equipment-scene-stage{height:250px}.equipment-scene-stage svg{width:100%;height:100%;touch-action:none}.scene-grid path{fill:none;stroke:#9db5c4;stroke-width:1}.scene-relations path{fill:none;stroke:#4a8fa3;stroke-width:3;stroke-dasharray:8 7}.scene-relations text{font-size:12px;fill:#3f7384;text-anchor:middle}.equipment-symbol{cursor:pointer;outline:none}.equipment-symbol .symbol-shadow{fill:#254b63;opacity:.2}.symbol-body{fill:#7797aa;stroke:#31546f;stroke-width:5;stroke-linecap:round;stroke-linejoin:round;filter:url(#none)}.symbol-body circle,.symbol-body rect{fill:#7899ad}.symbol-body .symbol-core{fill:#72d3bd}.symbol-screen{fill:#173c58!important}.symbol-body.generic{fill:#8ba4b5}.symbol-body.generic text{font-size:28px;fill:white;text-anchor:middle;stroke:none}.symbol-label rect{fill:#fff;stroke:#c5d5df;stroke-width:1}.symbol-label text{font:600 12px "Microsoft Yahei",sans-serif;fill:#274a62}.symbol-label .symbol-id{font-size:10px;font-weight:400;fill:#6a7f8e}.symbol-label circle{fill:#52b7a2}.equipment-symbol.running .symbol-body{fill:#3d8b84;stroke:#246c68}.equipment-symbol.stopped .symbol-body{fill:#8b9aa5;stroke:#566b79}.equipment-symbol.alarm .symbol-body{fill:#cc785f;stroke:#934a3a}.equipment-symbol.alarm .symbol-label circle{fill:#e46e54}.equipment-symbol.selected .symbol-label rect{stroke:#2c91a4;stroke-width:3}.equipment-symbol.target .symbol-body{filter:drop-shadow(0 0 10px #dfb74f)}.scene-empty rect{fill:white;stroke:#c5d5df}.scene-empty text{text-anchor:middle;fill:#607886;font:600 15px "Microsoft Yahei",sans-serif}.scene-empty text+text{font-size:12px;font-weight:400}.equipment-scene-legend{display:flex;align-items:center;gap:14px;flex-wrap:wrap;padding:8px 13px;background:white;font-size:11px;color:#657b89}.equipment-scene-legend span{display:flex;align-items:center;gap:5px}.equipment-scene-legend i{width:8px;height:8px;border-radius:50%;background:#52b7a2}.equipment-scene-legend i.running{background:#2b8880}.equipment-scene-legend i.stopped{background:#84939e}.equipment-scene-legend i.alarm{background:#e46e54}.relation-legend{margin-left:auto}@media(max-width:600px){.equipment-scene-head{align-items:flex-start}.equipment-scene-head>div:first-child{align-items:flex-start;flex-wrap:wrap}.equipment-scene-head small{width:100%;padding-left:16px}.equipment-scene-stage,.compact .equipment-scene-stage{height:330px}.relation-legend{width:100%;margin-left:0}}
</style>

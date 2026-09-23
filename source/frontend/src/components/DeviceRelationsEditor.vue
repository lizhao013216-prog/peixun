<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { command } from "../store";
import Icon from "./Icon.vue";

const props = defineProps<{ project: any; scene: any; topology: any; readOnly: boolean }>();
const emit = defineEmits<{ "update:dirty": [value: boolean]; "update:draft": [value: any] }>();
const copy = <T>(value: T): T => JSON.parse(JSON.stringify(value));
const draft = ref({ connections: [] as any[], rules: [] as any[] });
const baseline = ref("");
const baseEditRevision = ref(0);
const connectionForm = ref({ sourceObject: "", sourcePort: "", targetObject: "", targetPort: "" });
const dirty = computed(() => JSON.stringify(draft.value) !== baseline.value);
const objects = computed(() => props.scene?.objects || []);
const object = (id: string) => objects.value.find((item: any) => item.id === id);
const objectName = (id: string) => object(id)?.name || id;
const ports = (objectId: string, direction: string) =>
  (object(objectId)?.ports || []).filter((item: any) => item.direction === direction);
const port = (objectId: string, portId: string) =>
  (object(objectId)?.ports || []).find((item: any) => item.id === portId);
const actionLabel = (objectId: string, actionId: string) =>
  (object(objectId)?.actions || []).find((item: any) => item.id === actionId)?.label || actionId;
const fieldLabels: Record<string, string> = { status: "运行状态", ready: "阀门就绪", readyInput: "就绪输入", value: "传感器值", sensorInput: "模拟量输入", alarm: "告警" };
const fieldLabel = (field: string) => fieldLabels[field] || field;
const operatorLabels: Record<string, string> = { EQ: "等于", NE: "不等于", LT: "小于", LTE: "小于等于", GT: "大于", GTE: "大于等于" };
const operatorLabel = (operator: string) =>
  operatorLabels[operator] || operator;
const actions = (objectId: string) => object(objectId)?.actions || [];
const stateFields = (objectId: string) => object(objectId)?.stateFields || [];
const fieldValueType = (objectId: string, fieldId: string) =>
  stateFields(objectId).find((item: any) => item.id === fieldId)?.valueType || "STRING";
function addRule() {
  const triggerObject = objects.value.find((item: any) => item.actions?.length);
  const stateObject = objects.value.find((item: any) => item.stateFields?.length) || triggerObject;
  if (!triggerObject || !stateObject) return;
  const field = stateObject.stateFields?.[0];
  draft.value.rules.push({ id: nextId("RULE", draft.value.rules), kind: "CONDITIONAL", trigger: { objectId: triggerObject.id, actionId: triggerObject.actions[0].id }, conditions: [{ objectId: stateObject.id, field: field?.id || "status", operator: "EQ", value: "READY" }], effects: [{ objectId: stateObject.id, field: field?.id || "status", value: "READY" }], rejectMessage: "条件不满足，动作未执行" });
}
function addCondition(rule: any) {
  const target = objects.value.find((item: any) => item.stateFields?.length);
  if (target) rule.conditions.push({ objectId: target.id, field: target.stateFields[0].id, operator: "EQ", value: "READY" });
}
function addEffect(rule: any) {
  const target = objects.value.find((item: any) => item.stateFields?.length);
  if (target) rule.effects.push({ objectId: target.id, field: target.stateFields[0].id, value: "READY" });
}

function load() {
  if (baseline.value && dirty.value) return;
  draft.value = copy({ connections: props.topology?.connections || [], rules: props.topology?.rules || [] });
  baseline.value = JSON.stringify(draft.value);
  baseEditRevision.value = props.topology?.editRevision || 0;
}
watch(() => props.topology?.editRevision, load, { immediate: true });
watch(draft, (value) => emit("update:draft", copy(value)), { deep: true, immediate: true });
watch(dirty, (value) => emit("update:dirty", value), { immediate: true });

function addConnection() {
  const source = port(connectionForm.value.sourceObject, connectionForm.value.sourcePort);
  if (!source || !connectionForm.value.targetPort) return;
  draft.value.connections.push({
    id: nextId("LINK", draft.value.connections),
    source: { objectId: connectionForm.value.sourceObject, port: connectionForm.value.sourcePort },
    target: { objectId: connectionForm.value.targetObject, port: connectionForm.value.targetPort },
    valueType: source.valueType,
  });
  connectionForm.value = { sourceObject: "", sourcePort: "", targetObject: "", targetPort: "" };
}
function addGuidedRules() {
  const valve = objects.value.find((item: any) => item.actions?.some((action: any) => action.id === "SET_READY"));
  const pump = objects.value.find((item: any) => item.actions?.some((action: any) => action.id === "START"));
  const sensor = objects.value.find((item: any) => item.actions?.some((action: any) => action.id === "SET_VALUE"));
  if (!valve || !pump || !sensor) return;
  const ensureConnection = (sourceObject: string, sourcePort: string, targetObject: string, targetPort: string, valueType: string) => {
    if (!draft.value.connections.some((item: any) => item.source.objectId === sourceObject && item.source.port === sourcePort && item.target.objectId === targetObject && item.target.port === targetPort))
      draft.value.connections.push({ id: nextId("LINK", draft.value.connections), source: { objectId: sourceObject, port: sourcePort }, target: { objectId: targetObject, port: targetPort }, valueType });
  };
  ensureConnection(valve.id, "ready", pump.id, "readyInput", "BOOLEAN");
  ensureConnection(sensor.id, "value", pump.id, "sensorInput", "NUMBER");
  if (!draft.value.rules.some((item: any) => item.id === "RULE-START"))
    draft.value.rules.push({ id: "RULE-START", kind: "PRECONDITION", trigger: { objectId: pump.id, actionId: "START" }, conditions: [{ objectId: pump.id, field: "readyInput", operator: "EQ", value: true }], effects: [{ objectId: pump.id, field: "status", value: "RUNNING" }], rejectMessage: "阀门未就绪，当前不能启动" });
  if (!draft.value.rules.some((item: any) => item.id === "RULE-LOW-VALUE"))
    draft.value.rules.push({ id: "RULE-LOW-VALUE", kind: "CONDITIONAL", trigger: { objectId: sensor.id, actionId: "SET_VALUE" }, conditions: [{ objectId: pump.id, field: "sensorInput", operator: "LT", value: 0.4 }], effects: [{ objectId: pump.id, field: "status", value: "ALARM" }], rejectMessage: "" });
}
async function save() {
  const result = await command("simulationTopology.save", { projectId: props.project.id, expectedEditRevision: baseEditRevision.value, connections: draft.value.connections, rules: draft.value.rules }, "设备关系与规则已保存");
  if (result) {
    draft.value = copy({ connections: result.connections, rules: result.rules });
    baseline.value = JSON.stringify(draft.value);
    baseEditRevision.value = result.editRevision || baseEditRevision.value;
  }
  return result;
}
defineExpose({ save });
function nextId(prefix: string, items: any[]) {
  let index = items.length + 1;
  let id = `${prefix}-${String(index).padStart(2, "0")}`;
  while (items.some((item) => item.id === id)) id = `${prefix}-${String(++index).padStart(2, "0")}`;
  return id;
}
const hasGuidedObjects = computed(() =>
  ["START", "SET_READY", "SET_VALUE"].every((actionId) => objects.value.some((item: any) => item.actions?.some((action: any) => action.id === actionId))),
);
</script>

<template>
  <section v-if="project.linkageMode === 'NONE'" class="panel panel-body stage-placeholder compact-stage">
    <Icon name="Unplug" :size="38" /><h3>此工程不使用设备连接</h3><p>NONE 模式仍可在“调试预览”中执行对象自己的动作，不需要为了单对象操作强制创建连接。</p>
  </section>
  <div v-else class="relations-workspace">
    <section class="panel relation-main">
      <div class="panel-header"><div><h3>端口连接</h3><small>只允许输出端口连接同类型输入端口</small></div><span v-if="dirty" class="dirty-mark">有未保存修改</span></div>
      <div class="panel-body">
        <div v-if="!readOnly" class="connection-builder">
          <label class="field"><span>来源对象</span><select v-model="connectionForm.sourceObject" @change="connectionForm.sourcePort = ''"><option value="">请选择</option><option v-for="item in objects.filter((value: any) => ports(value.id, 'OUTPUT').length)" :key="item.id" :value="item.id">{{ item.name }}</option></select></label>
          <label class="field"><span>输出端口</span><select v-model="connectionForm.sourcePort"><option value="">请选择</option><option v-for="item in ports(connectionForm.sourceObject, 'OUTPUT')" :key="item.id" :value="item.id">{{ item.label }} · {{ item.valueType }}</option></select></label>
          <label class="field"><span>目标对象</span><select v-model="connectionForm.targetObject" @change="connectionForm.targetPort = ''"><option value="">请选择</option><option v-for="item in objects.filter((value: any) => ports(value.id, 'INPUT').length)" :key="item.id" :value="item.id">{{ item.name }}</option></select></label>
          <label class="field"><span>输入端口</span><select v-model="connectionForm.targetPort"><option value="">请选择</option><option v-for="item in ports(connectionForm.targetObject, 'INPUT')" :key="item.id" :value="item.id">{{ item.label }} · {{ item.valueType }}</option></select></label>
          <button class="btn secondary" :disabled="!connectionForm.sourcePort || !connectionForm.targetPort" @click="addConnection"><Icon name="Link" :size="15" />添加连接</button>
        </div>
        <div v-if="draft.connections.length" class="relation-list"><div v-for="(connection, index) in draft.connections" :key="connection.id" class="relation-row"><span class="relation-id">{{ connection.id }}</span><b>{{ objectName(connection.source.objectId) }} · {{ port(connection.source.objectId, connection.source.port)?.label }}</b><Icon name="ArrowRight" :size="16" /><b>{{ objectName(connection.target.objectId) }} · {{ port(connection.target.objectId, connection.target.port)?.label }}</b><small>{{ connection.valueType }}</small><button v-if="!readOnly" class="icon-btn" title="删除连接" @click="draft.connections.splice(Number(index), 1)"><Icon name="Trash2" :size="15" /></button></div></div>
        <div v-else class="object-empty">尚未配置连接。</div>
      </div>
    </section>

    <section class="panel relation-main">
      <div class="panel-header"><div><h3>动作规则</h3><small>规则用中文解释条件与状态变化，不执行脚本</small></div><div class="button-row"><button v-if="!readOnly" class="btn secondary" @click="addRule"><Icon name="Plus" :size="15" />新增通用规则</button><button v-if="!readOnly && hasGuidedObjects" class="btn secondary" @click="addGuidedRules">生成泵组联动示例</button></div></div>
      <div class="panel-body rule-list">
        <article v-for="(rule, index) in draft.rules" :key="rule.id" class="rule-card">
          <div class="rule-card-head"><div><span class="pill">{{ rule.kind === 'PRECONDITION' ? '动作前置条件' : '条件命中效果' }}</span><b>{{ rule.id }}</b></div><button v-if="!readOnly" class="icon-btn" title="删除规则" @click="draft.rules.splice(Number(index), 1)"><Icon name="Trash2" :size="15" /></button></div>
          <div class="form-grid"><label class="field"><span>规则类型</span><select v-model="rule.kind" :disabled="readOnly"><option value="PRECONDITION">动作前置条件</option><option value="CONDITIONAL">条件命中效果</option></select></label><label class="field"><span>触发对象</span><select v-model="rule.trigger.objectId" :disabled="readOnly" @change="rule.trigger.actionId = actions(rule.trigger.objectId)[0]?.id || ''"><option v-for="item in objects.filter((value: any) => value.actions?.length)" :key="item.id" :value="item.id">{{ item.name }}</option></select></label><label class="field"><span>触发动作</span><select v-model="rule.trigger.actionId" :disabled="readOnly"><option v-for="action in actions(rule.trigger.objectId)" :key="action.id" :value="action.id">{{ action.label || action.id }}</option></select></label></div>
          <p>当 <b>{{ objectName(rule.trigger.objectId) }}</b> 执行“<b>{{ actionLabel(rule.trigger.objectId, rule.trigger.actionId) }}</b>”时，{{ rule.kind === 'PRECONDITION' ? '必须满足' : '若满足' }}：</p>
          <div v-for="(condition, conditionIndex) in rule.conditions" :key="`${condition.objectId}.${condition.field}.${conditionIndex}`" class="rule-sentence"><select v-model="condition.objectId" :disabled="readOnly" @change="condition.field = stateFields(condition.objectId)[0]?.id || ''"><option v-for="item in objects.filter((value: any) => value.stateFields?.length)" :key="item.id" :value="item.id">{{ item.name }}</option></select><select v-model="condition.field" :disabled="readOnly"><option v-for="field in stateFields(condition.objectId)" :key="field.id" :value="field.id">{{ fieldLabel(field.id) }}</option></select><select v-model="condition.operator" :disabled="readOnly"><option v-for="(label, key) in operatorLabels" :key="key" :value="key">{{ label }}</option></select><input v-model.number="condition.value" :type="fieldValueType(condition.objectId, condition.field) === 'NUMBER' ? 'number' : 'text'" :disabled="readOnly" /><button v-if="!readOnly" class="icon-btn" title="删除条件" @click="rule.conditions.splice(Number(conditionIndex), 1)"><Icon name="Trash2" :size="14" /></button></div>
          <button v-if="!readOnly" class="btn secondary small" @click="addCondition(rule)">增加条件</button>
          <div v-for="(effect, effectIndex) in rule.effects" :key="`${effect.objectId}.${effect.field}.${effectIndex}`" class="rule-sentence rule-effect"><span>满足后</span><select v-model="effect.objectId" :disabled="readOnly" @change="effect.field = stateFields(effect.objectId)[0]?.id || ''"><option v-for="item in objects.filter((value: any) => value.stateFields?.length)" :key="item.id" :value="item.id">{{ item.name }}</option></select><select v-model="effect.field" :disabled="readOnly"><option v-for="field in stateFields(effect.objectId)" :key="field.id" :value="field.id">{{ fieldLabel(field.id) }}</option></select><input v-model="effect.value" :disabled="readOnly" /><button v-if="!readOnly" class="icon-btn" title="删除效果" @click="rule.effects.splice(Number(effectIndex), 1)"><Icon name="Trash2" :size="14" /></button></div>
          <button v-if="!readOnly" class="btn secondary small" @click="addEffect(rule)">增加效果</button>
          <p v-if="rule.kind === 'PRECONDITION'" class="rule-reject">拒绝说明：<input v-model="rule.rejectMessage" :disabled="readOnly" /></p>
        </article>
        <div v-if="!draft.rules.length" class="object-empty">尚未配置规则。添加具备泵组、阀门和传感器能力的对象后，可生成数据驱动示例。</div>
      </div>
    </section>
    <div v-if="!readOnly" class="relation-save"><span>保存时后端会重新验证对象、端口方向、类型、动作、字段和循环。</span><button class="btn primary" :disabled="!dirty" @click="save"><Icon name="Save" :size="16" />保存关系与规则</button></div>
  </div>
</template>

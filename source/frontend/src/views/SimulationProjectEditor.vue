<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from "vue";
import { onBeforeRouteLeave, useRoute, useRouter } from "vue-router";
import type { PageMeta } from "../catalog";
import { pathForFeature } from "../catalog";
import { store, command, notify } from "../store";
import { systemName } from "../domain";
import Icon from "../components/Icon.vue";
import Badge from "../components/Badge.vue";
import Modal from "../components/Modal.vue";
import DeviceRelationsEditor from "../components/DeviceRelationsEditor.vue";
import SimulationPreviewPanel from "../components/SimulationPreviewPanel.vue";

const props = defineProps<{ page: PageMeta }>();
const route = useRoute();
const router = useRouter();
const tab = ref(String(route.query.tab || "scene"));
const draft = ref<any>(null);
const baseline = ref("");
const selectedIndex = ref(0);
const adding = ref(false);
const assetId = ref("");
const leaveModal = ref(false);
const pendingRoute = ref("");
const publishModal = ref(false);
const publishForm = ref({ usageInstructions: "", sharedWith: [] as string[] });
const inspection = ref<any>(null);
const projectId = computed(() => String(route.params.id || ""));
const project = computed(() => (store.data?.simulationProjects || []).find((item: any) => item.id === projectId.value));
const sourceScene = computed(() => (store.data?.scenes || []).find((item: any) => item.id === project.value?.sceneRef?.id));
const topology = computed(() => (store.data?.topologies || []).find((item: any) => item.id === project.value?.topologyRef?.id));
const readOnly = computed(() => project.value?.status !== "DRAFT");
const approvedAssets = computed(() => (store.data?.assets || []).filter((item: any) => item.status === "APPROVED"));
const selectedObject = computed(() => draft.value?.objects?.[selectedIndex.value]);
const dirty = computed(() => !!draft.value && JSON.stringify(draft.value) !== baseline.value);
const role = computed(() => store.accounts.find((item: any) => item.id === store.actor)?.role || "");
const canPublish = computed(() => ["ADMIN", "AUTHOR", "INSTRUCTOR"].includes(role.value));
const shareTargets = computed(() => ["OPERATION", "MAINTENANCE", "SUPPORT"].filter((item) => item !== props.page.system));
const copy = <T>(value: T): T => JSON.parse(JSON.stringify(value));

function loadDraft() {
  if (!project.value || !sourceScene.value || dirty.value) return;
  draft.value = copy({
    name: project.value.name,
    purpose: project.value.purpose || "",
    objects: sourceScene.value.objects || [],
    environment: sourceScene.value.environment || { weather: "晴", light: "日间", camera: "总览", material: "标准" },
  });
  baseline.value = JSON.stringify(draft.value);
  selectedIndex.value = Math.min(selectedIndex.value, Math.max(0, draft.value.objects.length - 1));
}
watch(() => [projectId.value, project.value?.editRevision], loadDraft, { immediate: true });

async function save(message = "工程草稿已保存") {
  if (!project.value || !draft.value || readOnly.value) return null;
  const result = await command(
    "simulationProject.save",
    {
      id: project.value.id,
      expectedEditRevision: project.value.editRevision,
      name: draft.value.name,
      purpose: draft.value.purpose,
      scene: { objects: draft.value.objects, environment: draft.value.environment },
    },
    message,
  );
  if (result) {
    draft.value = copy({ name: result.name, purpose: result.purpose || "", objects: result.scene.objects, environment: result.scene.environment });
    baseline.value = JSON.stringify(draft.value);
  }
  return result;
}
function addObject() {
  const asset = approvedAssets.value.find((item: any) => item.id === assetId.value);
  if (!asset) return;
  const used = new Set(draft.value.objects.map((item: any) => item.id));
  let index = draft.value.objects.length + 1;
  let id = `OBJ-${String(index).padStart(2, "0")}`;
  while (used.has(id)) id = `OBJ-${String(++index).padStart(2, "0")}`;
  draft.value.objects.push({ id, name: asset.name, assetRef: { id: asset.id, version: asset.version }, assetName: asset.name, x: 15 + (index * 13) % 70, y: 20 + (index * 17) % 60, view: "设备", initialState: { status: "READY" }, actions: asset.actions || [] });
  selectedIndex.value = draft.value.objects.length - 1;
  adding.value = false;
  assetId.value = "";
}
function removeObject(index: number) {
  const object = draft.value.objects[index];
  const topology = (store.data?.topologies || []).find((item: any) => item.id === project.value?.topologyRef?.id);
  const referenced = (topology?.connections || []).some((item: any) => item.source?.objectId === object.id || item.target?.objectId === object.id);
  if (referenced) return notify("该对象仍被设备关系引用，请先在“设备关系与联动”页签删除连接", "error");
  draft.value.objects.splice(index, 1);
  selectedIndex.value = Math.max(0, Math.min(selectedIndex.value, draft.value.objects.length - 1));
}
async function cloneLegacy() {
  const result = await command("simulationProject.clone", { id: project.value.id, domain: props.page.system, name: `${project.value.name} · 可编辑副本` }, "已复制为独立草稿");
  if (result) router.push(pathForFeature(props.page.system!, "simulation-project-editor", result.id));
}
function openPublish() {
  inspection.value = null;
  publishForm.value = { usageInstructions: project.value?.purpose || "", sharedWith: [] };
  publishModal.value = true;
}
async function inspectForPublish() {
  inspection.value = await command(
    "simulationTemplate.validate",
    { projectId: project.value.id, usageInstructions: publishForm.value.usageInstructions, sharedWith: publishForm.value.sharedWith },
    "配置检查通过",
  );
}
async function publishTemplate() {
  const result = await command(
    "simulationTemplate.publish",
    { projectId: project.value.id, usageInstructions: publishForm.value.usageInstructions, sharedWith: publishForm.value.sharedWith },
    "已发布不可变模板版本",
  );
  if (result) {
    publishModal.value = false;
    router.push(pathForFeature(props.page.system!, "simulation-templates"));
  }
}
function beforeUnload(event: BeforeUnloadEvent) {
  if (!dirty.value) return;
  event.preventDefault();
  event.returnValue = "";
}
window.addEventListener("beforeunload", beforeUnload);
onBeforeUnmount(() => window.removeEventListener("beforeunload", beforeUnload));
onBeforeRouteLeave((to) => {
  if (!dirty.value) return true;
  pendingRoute.value = to.fullPath;
  leaveModal.value = true;
  return false;
});
async function saveAndLeave() {
  if (!(await save("工程已保存"))) return;
  leaveModal.value = false;
  await nextTick();
  router.push(pendingRoute.value);
}
async function discardAndLeave() {
  baseline.value = JSON.stringify(draft.value);
  leaveModal.value = false;
  await nextTick();
  router.push(pendingRoute.value);
}
</script>

<template>
  <section v-if="!project || !draft" class="panel panel-body">
    <div class="inline-empty"><Icon name="CircleAlert" :size="38" /><div><b>未找到指定仿真工程</b><p>该地址没有可用工程，或工程不属于当前系统。</p></div></div>
    <button class="btn primary" @click="router.push(pathForFeature(page.system!, 'simulation-projects'))">返回工程列表</button>
  </section>
  <template v-else>
    <section class="editor-heading">
      <div><span class="task-eyebrow">{{ systemName(page.system) }} · {{ project.id }}</span><div class="editor-title-row"><h2>{{ draft.name }}</h2><Badge :status="project.status" /><span v-if="dirty" class="dirty-mark">有未保存修改</span></div><p>{{ draft.purpose || "独立仿真场景工程" }}</p></div>
      <div class="button-row"><button class="btn secondary" @click="router.push(pathForFeature(page.system!, 'simulation-projects'))">返回列表</button><button v-if="readOnly" class="btn primary" @click="cloneLegacy">复制为可编辑草稿</button><template v-else><button v-if="canPublish" class="btn secondary" :disabled="dirty" @click="openPublish"><Icon name="Package" :size="16" />发布为模板</button><button class="btn primary" :disabled="!dirty || store.busy > 0" @click="save()"><Icon name="Save" :size="16" />保存工程</button></template></div>
    </section>
    <div v-if="readOnly" class="info-note warning"><Icon name="History" :size="17" />这是旧全局配置的只读兼容快照。复制后会生成独立工程、独立场景和独立关系数据。</div>
    <div class="tabs"><button :class="{ active: tab === 'scene' }" @click="tab = 'scene'">对象与场景</button><button :class="{ active: tab === 'relations' }" @click="tab = 'relations'">设备关系与联动</button><button :class="{ active: tab === 'preview' }" @click="tab = 'preview'">调试预览</button></div>

    <section v-if="tab === 'scene'" class="simulation-editor">
      <aside class="panel object-list"><div class="panel-header"><div><h3>场景对象</h3><small>{{ draft.objects.length }} 个独立实例</small></div><button v-if="!readOnly" class="icon-btn" title="添加对象" @click="adding = true"><Icon name="Plus" :size="18" /></button></div><button v-for="(object, index) in draft.objects" :key="object.id" :class="{ active: selectedIndex === Number(index) }" @click="selectedIndex = Number(index)"><Icon name="Box" :size="17" /><span><b>{{ object.name }}</b><small>{{ object.id }} · {{ object.assetName }}</small></span></button><div v-if="!draft.objects.length" class="object-empty">尚无对象，可从已批准素材添加。</div></aside>
      <div class="panel scene-canvas"><div class="scene-toolbar"><span>场景平面 · 位置范围 0～100</span><span>{{ draft.environment.weather }} / {{ draft.environment.light }} / {{ draft.environment.camera }}</span></div><div class="scene-plane"><button v-for="(object, index) in draft.objects" :key="object.id" class="scene-object" :class="{ active: selectedIndex === Number(index) }" :style="{ left: `${object.x}%`, top: `${object.y}%` }" @click="selectedIndex = Number(index)"><Icon name="Box" :size="21" /><span>{{ object.name }}</span></button><div v-if="!draft.objects.length" class="canvas-empty">添加素材对象后在此形成工程专属场景</div></div></div>
      <aside class="panel property-panel"><div class="panel-header"><h3>属性</h3></div><div v-if="selectedObject" class="panel-body property-fields"><label class="field"><span>对象编号</span><input v-model="selectedObject.id" :disabled="readOnly" /></label><label class="field"><span>对象名称</span><input v-model="selectedObject.name" :disabled="readOnly" /></label><div class="form-grid"><label class="field"><span>X</span><input v-model.number="selectedObject.x" type="number" min="0" max="100" :disabled="readOnly" /></label><label class="field"><span>Y</span><input v-model.number="selectedObject.y" type="number" min="0" max="100" :disabled="readOnly" /></label></div><label class="field"><span>视图标签</span><input v-model="selectedObject.view" :disabled="readOnly" /></label><label class="field"><span>初始状态</span><input v-model="selectedObject.initialState.status" :disabled="readOnly" /></label><button v-if="!readOnly" class="btn danger" @click="removeObject(selectedIndex)">移除对象</button></div><div v-else class="panel-body object-empty">选择一个对象查看属性。</div></aside>
      <section class="panel environment-panel"><div class="panel-header"><h3>场景环境</h3></div><div class="panel-body form-grid"><label class="field"><span>天气</span><input v-model="draft.environment.weather" :disabled="readOnly" /></label><label class="field"><span>光照</span><input v-model="draft.environment.light" :disabled="readOnly" /></label><label class="field"><span>摄像机</span><input v-model="draft.environment.camera" :disabled="readOnly" /></label><label class="field"><span>材质方案</span><input v-model="draft.environment.material" :disabled="readOnly" /></label></div></section>
    </section>
    <template v-else-if="tab === 'relations'">
      <div v-if="dirty" class="info-note warning"><Icon name="CircleAlert" :size="17" />对象或编号存在未保存修改。请先保存“对象与场景”，设备关系只引用已持久化对象。</div>
      <DeviceRelationsEditor :project="project" :scene="sourceScene" :topology="topology" :read-only="readOnly" />
    </template>
    <SimulationPreviewPanel v-else :project="project" :scene="sourceScene" />

    <Modal v-if="adding" title="从已批准素材添加对象" @close="adding = false"><label class="field"><span>素材版本</span><select v-model="assetId"><option value="">请选择素材</option><option v-for="asset in approvedAssets" :key="asset.id" :value="asset.id">{{ asset.name }} · V{{ asset.version }} · {{ systemName(asset.ownerSystem) }}</option></select></label><div v-if="!approvedAssets.length" class="info-note warning">当前系统没有可引用的已批准素材，请先到素材库导入、审核或授权共享。</div><template #footer><button class="btn secondary" @click="adding = false">取消</button><button class="btn primary" :disabled="!assetId" @click="addObject">添加对象</button></template></Modal>
    <Modal v-if="leaveModal" title="存在未保存修改" @close="leaveModal = false"><p>离开后，本地尚未保存的对象和环境修改将丢失。</p><template #footer><button class="btn secondary" @click="leaveModal = false">取消</button><button class="btn danger" @click="discardAndLeave">放弃并离开</button><button class="btn primary" @click="saveAndLeave">保存后离开</button></template></Modal>
    <Modal v-if="publishModal" title="检查并发布仿真系统模板" @close="publishModal = false">
      <div class="info-note"><Icon name="BadgeCheck" :size="17" />发布会冻结对象、场景、关系、规则和素材版本。后续修改需创建新版本，不会覆盖历史模板。</div>
      <label class="field"><span>模板使用说明 *</span><textarea v-model="publishForm.usageInstructions" placeholder="说明适用场景、主要动作和使用前提" /></label>
      <div class="field"><span>允许复用的其他系统</span><label v-for="target in shareTargets" :key="target" class="check-row"><input v-model="publishForm.sharedWith" type="checkbox" :value="target" />{{ systemName(target as any) }}</label><small>发布时会校验模板及全部依赖素材都已授权给目标系统。</small></div>
      <div v-if="inspection" class="publish-inspection"><b>{{ inspection.message }}</b><span>{{ inspection.objectCount }} 个对象</span><span>{{ inspection.dependencyCount }} 项素材依赖</span><span>{{ inspection.connectionCount }} 条连接</span><span>{{ inspection.ruleCount }} 条规则</span></div>
      <template #footer><button class="btn secondary" @click="publishModal = false">取消</button><button class="btn secondary" :disabled="!publishForm.usageInstructions.trim()" @click="inspectForPublish">检查配置</button><button class="btn primary" :disabled="!inspection || !publishForm.usageInstructions.trim()" @click="publishTemplate">确认发布</button></template>
    </Modal>
  </template>
</template>

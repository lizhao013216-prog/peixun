<script setup lang="ts">
import { computed, ref } from "vue";
import { useRouter } from "vue-router";
import type { PageMeta } from "../catalog";
import { pathForFeature } from "../catalog";
import { systemName } from "../domain";
import { store, command } from "../store";
import Icon from "../components/Icon.vue";
import Badge from "../components/Badge.vue";
import Modal from "../components/Modal.vue";
import Empty from "../components/Empty.vue";

const props = defineProps<{ page: PageMeta }>();
const router = useRouter();
const showCreate = ref(false);
const cloning = ref<any>(null);
const form = ref({ name: "", purpose: "", linkageMode: "NONE" });
const role = computed(() => store.accounts.find((item: any) => item.id === store.actor)?.role || "");
const canManage = computed(() => ["ADMIN", "AUTHOR", "INSTRUCTOR"].includes(role.value));
const projects = computed(() => store.data?.simulationProjects || []);
const scenes = computed(() => store.data?.scenes || []);
const objectCount = (project: any) => scenes.value.find((item: any) => item.id === project.sceneRef?.id)?.objects?.length || 0;
const open = (project: any) => {
  store.selectedProjectBySystem[props.page.system!] = project.id;
  router.push(pathForFeature(props.page.system!, "simulation-project-editor", project.id));
};
async function create() {
  const result = await command("simulationProject.create", { ...form.value, domain: props.page.system }, "独立仿真工程已创建");
  if (result) {
    showCreate.value = false;
    form.value = { name: "", purpose: "", linkageMode: "NONE" };
    open(result);
  }
}
async function clone() {
  const result = await command("simulationProject.clone", { id: cloning.value.id, domain: props.page.system, name: form.value.name }, "已复制为独立草稿");
  if (result) {
    cloning.value = null;
    open(result);
  }
}
function openClone(project: any) {
  cloning.value = project;
  form.value.name = `${project.name} · 副本`;
}
</script>

<template>
  <section class="business-intro compact-intro">
    <div><span class="task-eyebrow">{{ systemName(page.system) }} · 资源准备</span><h2>场景与设备编排</h2><p>每个工程持有自己的场景和对象清单，保存或重载不会影响其他工程。</p></div>
    <button v-if="canManage" class="btn primary" @click="showCreate = true"><Icon name="Plus" :size="16" />新建仿真工程</button>
  </section>
  <section v-if="projects.length" class="project-grid">
    <article v-for="project in projects" :key="project.id" class="panel project-card">
      <div class="panel-header"><div><span class="task-eyebrow">{{ project.id }}</span><h3>{{ project.name }}</h3></div><Badge :status="project.status" /></div>
      <div class="panel-body">
        <p>{{ project.purpose || "尚未填写工程用途" }}</p>
        <div class="project-metrics"><span><b>{{ objectCount(project) }}</b> 场景对象</span><span><b>{{ project.linkageMode === 'SIGNAL_GRAPH' ? '信号图' : '无' }}</b> 联动模式</span><span><b>R{{ project.editRevision }}</b> 编辑版本</span></div>
        <div v-if="project.status === 'LEGACY_READ_ONLY'" class="info-note warning"><Icon name="History" :size="16" />旧单例配置兼容入口，只读保留；复制后可独立编辑。</div>
        <div class="button-row"><button class="btn primary" @click="open(project)">{{ project.status === 'DRAFT' ? '打开编辑' : '查看配置' }}</button><button v-if="canManage" class="btn secondary" @click="openClone(project)">复制为草稿</button></div>
      </div>
    </article>
  </section>
  <Empty v-else title="暂无仿真工程" description="新建工程后可添加任意数量的已批准素材对象。" />

  <Modal v-if="showCreate" title="新建独立仿真工程" @close="showCreate = false">
    <div class="form-grid"><label class="field full"><span>工程名称 *</span><input v-model="form.name" placeholder="例如：泵组启停训练场景" /></label><label class="field full"><span>用途说明</span><textarea v-model="form.purpose" /></label><label class="field full"><span>联动模式</span><select v-model="form.linkageMode"><option value="NONE">不使用设备联动</option><option value="SIGNAL_GRAPH">使用信号图联动</option></select></label></div>
    <template #footer><button class="btn secondary" @click="showCreate = false">取消</button><button class="btn primary" :disabled="!form.name.trim()" @click="create">创建并编辑</button></template>
  </Modal>
  <Modal v-if="cloning" title="复制为独立草稿" @close="cloning = null">
    <div class="info-note">复制会创建新的工程、场景和关系快照，不会覆盖来源配置。</div><label class="field"><span>新工程名称 *</span><input v-model="form.name" /></label>
    <template #footer><button class="btn secondary" @click="cloning = null">取消</button><button class="btn primary" :disabled="!form.name.trim()" @click="clone">复制并打开</button></template>
  </Modal>
</template>

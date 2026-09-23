<script setup lang="ts">
import { computed, ref } from "vue";
import { useRouter } from "vue-router";
import type { PageMeta } from "../catalog";
import { pathForFeature } from "../catalog";
import { systemName, systemIds, type SystemId } from "../domain";
import { store, downloadAsset, command, upload } from "../store";
import Icon from "../components/Icon.vue";
import Badge from "../components/Badge.vue";
import Modal from "../components/Modal.vue";
import Empty from "../components/Empty.vue";

const props = defineProps<{ page: PageMeta }>();
const router = useRouter();
const query = ref("");
const category = ref("全部资源");
const showCreate = ref(false);
const preview = ref<any>(null);
const sharing = ref<any>(null);
const shareTargets = ref<SystemId[]>([]);
const fileInput = ref<HTMLInputElement>();
const domain = computed(() => props.page.system);
const role = computed(() => store.accounts.find((item: any) => item.id === store.actor)?.role || "");
const canManage = computed(() => !!domain.value && ["ADMIN", "AUTHOR", "INSTRUCTOR"].includes(role.value));
const form = ref({ name: "", category: "模型", format: "FBX", description: "", capabilityTemplate: "GENERIC", symbolKey: "GENERIC", initialStatus: "READY", initialValue: 0 });
const categories = ["全部资源", "模型", "场景", "文档", "图像", "动画", "视频", "音频", "环境"];
const assets = computed(() =>
  (store.data?.assets || []).filter(
    (asset: any) =>
      (category.value === "全部资源" || asset.category === category.value) &&
      asset.name.toLowerCase().includes(query.value.toLowerCase()),
  ),
);
const ownAsset = (asset: any) => domain.value && asset.ownerSystem === domain.value;
const shareLabel = (asset: any) =>
  asset.ownerSystem === "SHARED"
    ? "旧共享素材"
    : asset.visibility === "SHARED"
      ? `已共享至 ${(asset.sharedWith || []).map((id: SystemId) => systemName(id)).join("、")}`
      : "仅所属系统可见";

async function create() {
  if (!domain.value) return;
  const result = await command(
    "asset.import",
    { ...form.value, initialState: { status: form.value.initialStatus, ...(form.value.capabilityTemplate === 'SENSOR' ? { value: form.value.initialValue } : {}) }, ownerSystem: domain.value, visibility: "PRIVATE", sharedWith: [] },
    "资源草稿已创建",
  );
  if (result) {
    showCreate.value = false;
    form.value = { name: "", category: "模型", format: "FBX", description: "", capabilityTemplate: "GENERIC", symbolKey: "GENERIC", initialStatus: "READY", initialValue: 0 };
  }
}
async function process(asset: any) {
  await command("asset.process", { id: asset.id, operation: "轻量化", texture: "优化" }, "已创建派生处理任务");
  preview.value = null;
}
async function fileChanged(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0];
  if (file && domain.value) await upload(file, domain.value);
  (event.target as HTMLInputElement).value = "";
}
function openShare(asset: any) {
  sharing.value = asset;
  shareTargets.value = [...(asset.sharedWith || [])];
}
async function saveShare() {
  const result = await command("asset.share", { id: sharing.value.id, sharedWith: shareTargets.value }, "共享范围已保存");
  if (result) sharing.value = null;
}
function toggleTarget(target: SystemId) {
  shareTargets.value = shareTargets.value.includes(target)
    ? shareTargets.value.filter((item) => item !== target)
    : [...shareTargets.value, target];
}
</script>

<template>
  <section class="business-intro compact-intro">
    <div>
      <span class="task-eyebrow">{{ domain ? systemName(domain) : "公共资源中心" }}</span>
      <h2>{{ domain ? "素材库" : "跨系统只读资源总览" }}</h2>
      <p>{{ domain ? "新素材默认仅本系统可见；需要复用时显式授权给目标系统。" : "公共入口保留为只读视图，资源维护请从具体业务系统进入。" }}</p>
    </div>
    <button v-if="domain" class="btn secondary" @click="router.push(pathForFeature(domain, 'simulation-projects'))">
      <Icon name="Network" :size="16" />进入场景与设备编排
    </button>
  </section>
  <div class="toolbar">
    <div class="filter-search"><Icon name="Search" :size="15" /><input v-model="query" placeholder="搜索资源名称…" /></div>
    <select v-model="category" class="select-compact"><option v-for="item in categories" :key="item">{{ item }}</option></select>
    <span class="spacer"></span>
    <template v-if="canManage">
      <input ref="fileInput" type="file" class="file-input" @change="fileChanged" />
      <button class="btn secondary" @click="fileInput?.click()"><Icon name="Upload" :size="15" />上传文件</button>
      <button class="btn primary" @click="showCreate = true"><Icon name="Plus" :size="16" />导入模拟资源</button>
    </template>
  </div>
  <section v-if="assets.length" class="asset-grid">
    <article v-for="asset in assets" :key="asset.id" class="asset-card" tabindex="0" @click="preview = asset" @keydown.enter="preview = asset">
      <div class="asset-preview"><span class="asset-format">{{ asset.format }}</span><Icon :name="asset.category === '模型' ? 'Box' : 'FileText'" :size="55" /></div>
      <div class="asset-info">
        <b>{{ asset.name }}</b>
        <div class="asset-meta"><span>{{ asset.category }} · V{{ asset.version }}</span><Badge :status="asset.status" /></div>
        <small>{{ systemName(asset.ownerSystem) }} · {{ shareLabel(asset) }}</small>
      </div>
    </article>
  </section>
  <Empty v-else title="没有找到资源" description="调整筛选条件，或在当前系统导入新的业务资源。" />

  <Modal v-if="showCreate" title="导入模拟资源" @close="showCreate = false">
    <div class="info-note"><Icon name="Info" :size="16" />资源创建后归属 {{ systemName(domain) }}，初始为私有草稿。</div>
    <div class="form-grid">
      <label class="field full"><span>资源名称 *</span><input v-model="form.name" placeholder="例如：标准工具组" /></label>
      <label class="field"><span>资源类别</span><select v-model="form.category"><option v-for="item in categories.slice(1)" :key="item">{{ item }}</option></select></label>
      <label class="field"><span>源格式</span><select v-model="form.format"><option v-for="item in ['FBX','OBJ','STEP','GLB','PNG','SVG','PDF','MP4','WAV']" :key="item">{{ item }}</option></select></label>
      <label class="field"><span>模拟能力模板</span><select v-model="form.capabilityTemplate" @change="form.symbolKey = form.capabilityTemplate"><option value="GENERIC">通用确认对象</option><option value="PUMP">泵/动力设备</option><option value="VALVE">阀门/开关设备</option><option value="SENSOR">传感器/参数输入</option><option value="CONTROL">控制柜/控制单元</option><option value="TOOL">工具/检查对象</option><option value="WORKBENCH">工作台/资源位置</option></select></label>
      <label class="field"><span>场景符号</span><select v-model="form.symbolKey"><option v-for="item in ['GENERIC','PUMP','VALVE','SENSOR','CONTROL','TOOL','WORKBENCH']" :key="item">{{ item }}</option></select></label>
      <label class="field"><span>初始运行状态</span><select v-model="form.initialStatus"><option value="READY">就绪</option><option value="STOPPED">停止</option><option value="AVAILABLE">可用</option><option value="ALARM">告警</option></select></label>
      <label v-if="form.capabilityTemplate === 'SENSOR'" class="field"><span>初始数值</span><input v-model.number="form.initialValue" type="number" step="0.1" /></label>
      <label class="field full"><span>资源说明</span><textarea v-model="form.description" /></label>
    </div>
    <template #footer><button class="btn secondary" @click="showCreate = false">取消</button><button class="btn primary" :disabled="!form.name.trim() || store.busy > 0" @click="create">创建资源</button></template>
  </Modal>

  <Modal v-if="preview" :title="preview.name" @close="preview = null">
    <div class="kv-list">
      <div><span>资源编号</span><b class="mono">{{ preview.id }}</b></div>
      <div><span>所属系统</span><b>{{ systemName(preview.ownerSystem) }}</b></div>
      <div><span>版本 / 编辑版本</span><b>V{{ preview.version }} / R{{ preview.editRevision || 1 }}</b></div>
      <div><span>共享状态</span><b>{{ shareLabel(preview) }}</b></div>
      <div><span>管理状态</span><Badge :status="preview.status" /></div>
    </div>
    <p class="note-caption">{{ preview.description || "暂无资源说明" }}</p>
    <template #footer>
      <button v-if="preview.blobRef" class="btn secondary" @click="downloadAsset(preview)">下载源文件</button>
      <button v-if="canManage && ownAsset(preview)" class="btn secondary" @click="openShare(preview); preview = null">设置共享</button>
      <button v-if="canManage && ownAsset(preview)" class="btn secondary" @click="process(preview)">生成派生版本</button>
      <button v-if="canManage && ownAsset(preview) && ['DRAFT','PENDING_REVIEW'].includes(preview.status)" class="btn primary" @click="command('asset.approve', { id: preview.id }, '资源已审核'); preview = null">审核资源</button>
    </template>
  </Modal>

  <Modal v-if="sharing" title="设置素材共享范围" @close="sharing = null">
    <div class="info-note warning"><Icon name="ShieldCheck" :size="16" />共享只授予引用权限，源素材仍由 {{ systemName(domain) }} 管理。</div>
    <div class="check-list">
      <label v-for="target in systemIds.filter((item) => item !== domain)" :key="target"><input type="checkbox" :checked="shareTargets.includes(target)" @change="toggleTarget(target)" />{{ systemName(target) }}</label>
    </div>
    <template #footer><button class="btn secondary" @click="sharing = null">取消</button><button class="btn primary" @click="saveShare">保存共享范围</button></template>
  </Modal>
</template>

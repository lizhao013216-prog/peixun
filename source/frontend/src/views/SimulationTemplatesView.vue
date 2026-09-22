<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRouter } from "vue-router";
import type { PageMeta } from "../catalog";
import { pathForFeature } from "../catalog";
import { systemName } from "../domain";
import { store, command } from "../store";
import Icon from "../components/Icon.vue";
import Badge from "../components/Badge.vue";
import Empty from "../components/Empty.vue";
import SimulationPreviewPanel from "../components/SimulationPreviewPanel.vue";

const props = defineProps<{ page: PageMeta }>();
const router = useRouter();
const selectedId = ref("");
const debugging = ref(false);
const templates = computed(() => store.data?.systemTemplates || []);
const selected = computed(() => templates.value.find((item: any) => item.id === selectedId.value));
const role = computed(() => store.accounts.find((item: any) => item.id === store.actor)?.role || "");
const canManage = computed(() => ["ADMIN", "AUTHOR", "INSTRUCTOR"].includes(role.value));
const versions = computed(() =>
  selected.value ? templates.value.filter((item: any) => item.familyId === selected.value.familyId).sort((a: any, b: any) => b.version - a.version) : [],
);
watch(selectedId, () => { debugging.value = false; });

function open(item: any) {
  selectedId.value = item.id;
  document.querySelector(".template-detail")?.scrollIntoView({ behavior: "smooth", block: "start" });
}
async function instantiate(mode: "COPY" | "REVISION") {
  if (!selected.value) return;
  const result = await command(
    "simulationTemplate.instantiate",
    { id: selected.value.id, domain: props.page.system, mode },
    mode === "REVISION" ? "已创建独立修订草稿" : "已从模板创建独立工程",
  );
  if (result) router.push(pathForFeature(props.page.system!, "simulation-project-editor", result.id));
}
const objectName = (id: string) => selected.value?.sceneSnapshot?.objects?.find((item: any) => item.id === id)?.name || id;
function ruleText(rule: any) {
  const trigger = `${objectName(rule.trigger?.objectId)}执行${rule.trigger?.actionId}`;
  const conditions = (rule.conditions || []).map((item: any) => `${objectName(item.objectId)}.${item.field} ${item.operator} ${String(item.value)}`).join("；");
  return `${trigger}：${rule.kind === "PRECONDITION" ? "必须" : "若"}${conditions}`;
}
</script>

<template>
  <section class="business-intro compact-intro"><div><span class="task-eyebrow">{{ systemName(page.system) }} · 资源准备</span><h2>仿真系统模板库</h2><p>查看已发布的不可变版本，按版本调试，或复制为新的独立工程和修订草稿。</p></div></section>
  <div class="info-note"><Icon name="BadgeCheck" :size="18" /><div><b>模板版本不会被草稿修改</b><p>发布时冻结场景、对象、联动、素材版本和来源；课件选择模板将在 P3 接入。</p></div></div>
  <section v-if="templates.length" class="project-grid">
    <article v-for="item in templates" :key="item.id" class="panel project-card">
      <div class="panel-header"><div><span class="task-eyebrow">{{ item.id }}</span><h3>{{ item.name }}</h3></div><Badge :status="item.status" /></div>
      <div class="panel-body">
        <p>{{ item.purpose || item.usageInstructions || '旧兼容模板' }}</p>
        <div class="project-metrics"><span><b>V{{ item.version }}</b> 模板版本</span><span><b>{{ item.objectCount ?? '—' }}</b> 场景对象</span><span><b>{{ item.linkageMode === 'SIGNAL_GRAPH' ? '信号图' : item.linkageMode === 'NONE' ? '无联动' : '旧配置' }}</b> 联动</span></div>
        <div class="button-row"><button class="btn primary" @click="open(item)">查看版本详情</button></div>
      </div>
    </article>
  </section>
  <Empty v-else title="暂无系统模板" description="先在“场景与设备编排”中保存工程，再执行配置检查并发布。" />

  <section v-if="selected" class="panel template-detail">
    <div class="panel-header"><div><span class="task-eyebrow">{{ selected.familyId }} · V{{ selected.version }}</span><h3>{{ selected.name }}</h3><small>{{ selected.usageInstructions }}</small></div><Badge :status="selected.status" /></div>
    <div v-if="selected.status === 'LEGACY_READ_ONLY'" class="panel-body"><div class="info-note warning"><Icon name="History" :size="17" />这是旧模板只读记录，不具备 P2-C 不可变快照。原旧入口继续保留，不会转换或覆盖保障预案。</div></div>
    <div v-else class="panel-body template-detail-body">
      <div class="template-facts">
        <article><span>所属系统</span><b>{{ systemName(selected.domain) }}</b></article><article><span>来源工程</span><b>{{ selected.sourceRef?.id }}</b><small>R{{ selected.sourceRef?.editRevision }}</small></article><article><span>发布信息</span><b>{{ selected.publishedBy }}</b><small>{{ selected.publishedAt }}</small></article><article><span>联动模式</span><b>{{ selected.linkageMode === 'SIGNAL_GRAPH' ? '信号图联动' : '不使用设备联动' }}</b><small>{{ selected.connectionCount }} 条连接 · {{ selected.ruleCount }} 条规则</small></article>
      </div>
      <div class="button-row template-actions"><button v-if="canManage" class="btn primary" @click="instantiate('COPY')"><Icon name="Copy" :size="15" />从模板新建工程</button><button v-if="canManage && selected.domain === page.system" class="btn secondary" @click="instantiate('REVISION')"><Icon name="GitBranch" :size="15" />创建修订</button><button v-if="canManage" class="btn secondary" @click="debugging = !debugging"><Icon name="Play" :size="15" />{{ debugging ? '收起调试' : '调试已发布版本' }}</button></div>
      <div class="template-columns">
        <section><h4>设备组成与主要动作</h4><div class="relation-list"><div v-for="item in selected.sceneSnapshot?.objects || []" :key="item.id" class="relation-row"><b>{{ item.name }}</b><span>{{ item.id }}</span><small>{{ item.assetName }} · {{ item.assetRef?.id }}@V{{ item.assetRef?.version }}<br />动作：{{ item.actions?.map((action: any) => action.label).join('、') || '无' }}</small></div></div></section>
        <section><h4>素材依赖</h4><div class="relation-list"><div v-for="item in selected.dependencies || []" :key="`${item.assetRef?.id}-${item.assetRef?.version}`" class="relation-row"><b>{{ item.name }}</b><span>{{ item.category }}</span><small>{{ item.assetRef?.id }}@V{{ item.assetRef?.version }}</small></div></div></section>
        <section><h4>主要规则</h4><div v-if="selected.topologySnapshot?.rules?.length" class="relation-list"><div v-for="rule in selected.topologySnapshot.rules" :key="rule.id" class="relation-row"><b>{{ rule.id }}</b><span>{{ ruleText(rule) }}</span></div></div><p v-else>此模板明确不使用设备联动，可执行单对象动作。</p></section>
        <section><h4>版本来源</h4><div class="relation-list"><button v-for="item in versions" :key="item.id" class="relation-row template-version" @click="selectedId = item.id"><b>V{{ item.version }}</b><span>{{ item.id }}</span><small>{{ item.publishedAt }}</small></button></div></section>
      </div>
    </div>
  </section>
  <SimulationPreviewPanel v-if="selected?.status === 'PUBLISHED' && debugging" :template="selected" />
</template>

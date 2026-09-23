<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  pages,
  pathFor,
  publicNavIds,
  publicServiceIds,
  type PageMeta,
} from "./catalog";
import { systems, systemFor, systemName, type SystemId } from "./domain";
import {
  store,
  bootstrap,
  refresh,
  login,
  switchWorkspace,
  notify,
  setScopeDomain,
} from "./store";
import Icon from "./components/Icon.vue";
import Modal from "./components/Modal.vue";
import Workbench from "./views/Workbench.vue";
import AssetsView from "./views/AssetsView.vue";
import AuthoringView from "./views/AuthoringView.vue";
import TrainingView from "./views/TrainingView.vue";
import PlanningView from "./views/PlanningView.vue";
import ExerciseView from "./views/ExerciseView.vue";
import ExecutionView from "./views/ExecutionView.vue";
import AnalysisView from "./views/AnalysisView.vue";
import RecordsView from "./views/RecordsView.vue";
import SettingsView from "./views/SettingsView.vue";
import GuideView from "./views/GuideView.vue";
import SystemOverviewView from "./views/SystemOverviewView.vue";
import SimulationProjectsView from "./views/SimulationProjectsView.vue";
import SimulationProjectEditor from "./views/SimulationProjectEditor.vue";
import SimulationTemplatesView from "./views/SimulationTemplatesView.vue";
import NotFoundView from "./views/NotFoundView.vue";
import DemoGuide from "./components/DemoGuide.vue";
import { guide } from "./guide";
const route = useRoute(),
  router = useRouter();
const page = computed(() => route.meta as PageMeta);
const sidebarOpen = ref(false),
  search = ref(""),
  searchOpen = ref(false),
  helpOpen = ref(false);
const expanded = ref<Record<SystemId, boolean>>({
  OPERATION: true,
  MAINTENANCE: false,
  SUPPORT: false,
});
watch(
  () => page.value.system,
  (system) => {
    if (system) expanded.value[system] = true;
    setScopeDomain(system || "").catch(() => {});
  },
  { immediate: true },
);
const pagesFor = (system: SystemId, section: string) =>
  pages.filter(
    (item) =>
      item.system === system &&
      item.section === section &&
      item.sidebar !== false,
  );
const viewMap: any = {
  overview: Workbench,
  guide: GuideView,
  assets: AssetsView,
  "system-assets": AssetsView,
  "simulation-projects": SimulationProjectsView,
  "simulation-project-editor": SimulationProjectEditor,
  "simulation-templates": SimulationTemplatesView,
  authoring: AuthoringView,
  training: TrainingView,
  planning: PlanningView,
  exercise: ExerciseView,
  execution: ExecutionView,
  analysis: AnalysisView,
  records: RecordsView,
  settings: SettingsView,
  "system-overview": SystemOverviewView,
  "not-found": NotFoundView,
};
const matches = computed(() => {
  const q = search.value.trim().toLowerCase();
  return q
    ? pages.filter((p) =>
        `${p.title}${p.description}`.toLowerCase().includes(q),
      )
    : pages.filter((p) =>
        ["C02", "S301", "S104", "S202", "S311", "S314"].includes(p.id),
      );
});
const actorName = computed(
  () => store.accounts.find((a) => a.id === store.actor)?.name || "演示管理员",
);
const currentSystem = computed(() => systemFor(page.value.system));
let interval: any;
function go(id: string) {
  router.push(pathFor(id));
  sidebarOpen.value = false;
  searchOpen.value = false;
}
async function changeActor(e: Event) {
  const select = e.target as HTMLSelectElement;
  if (store.unsavedContext && !confirm(`${store.unsavedContext}存在未保存修改。切换身份将放弃这些本地修改，是否继续？`)) {
    select.value = store.actor;
    return;
  }
  try {
    store.unsavedContext = "";
    await login(select.value);
    notify("已切换演示身份");
  } catch (e: any) {
    notify(e.message, "error");
  }
}
async function changeWorkspace(e: Event) {
  const select = e.target as HTMLSelectElement;
  if (store.unsavedContext && !confirm(`${store.unsavedContext}存在未保存修改。切换工作区将放弃这些本地修改，是否继续？`)) {
    select.value = store.workspace;
    return;
  }
  store.unsavedContext = "";
  await switchWorkspace(select.value);
}
onMounted(async () => {
  await bootstrap();
  interval = setInterval(() => {
    if (!store.busy && store.data && !document.hidden)
      refresh().catch(() => {});
  }, 2000);
});
onUnmounted(() => clearInterval(interval));
</script>
<template>
  <div class="app-shell">
    <aside class="sidebar" :class="{ open: sidebarOpen }">
      <a class="brand" href="/workbench" @click.prevent="go('C01')"
        ><div class="brand-mark"><span></span><span></span><i></i></div>
        <div><strong>保障业务</strong><span>共性平台 · 三系统演示</span></div>
        <small>DEMO</small></a
      >
      <div class="sidebar-project">
        <div class="project-icon"><Icon name="Ship" :size="20" /></div>
        <div><b>演示船 A · 泵组保障</b><span>三系统一体化业务空间</span></div>
      </div>
      <nav aria-label="主导航">
        <div class="nav-section-label">工作空间</div>
        <button
          v-for="id in publicNavIds"
          :key="id"
          class="nav-item"
          :class="{ active: page.id === id }"
          @click="go(id)"
        >
          <Icon :name="pages.find((p) => p.id === id)!.icon" /><span>{{
            pages.find((p) => p.id === id)!.title
          }}</span
          ><span v-if="id === 'C02' && store.data" class="nav-count">{{
            store.data.assets.length
          }}</span>
        </button>
        <div class="nav-section-label space-top">业务系统</div>
        <div v-for="system in systems" :key="system.id" class="nav-group">
          <button
            class="nav-group-title"
            :class="{ current: page.system === system.id }"
            @click="expanded[system.id] = !expanded[system.id]"
          >
            <Icon :name="system.icon" /><span>{{ system.shortName }}</span
            ><Icon
              :name="expanded[system.id] ? 'ChevronDown' : 'ChevronRight'"
              :size="14"
            />
          </button>
          <div v-if="expanded[system.id]" class="nav-children">
            <template v-for="section in system.sections" :key="section">
              <div
                v-if="pagesFor(system.id, section).length"
                class="nav-subsection-label"
              >
                {{ section }}
              </div>
              <button
                v-for="p in pagesFor(system.id, section)"
                :key="p.id"
                class="nav-child"
                :class="{ active: page.id === p.id }"
                @click="go(p.id)"
              >
                <span class="nav-dot"></span>{{ p.title }}
              </button>
            </template>
          </div>
        </div>
        <div class="nav-section-label space-top">公共服务</div>
        <button
          v-for="id in publicServiceIds"
          :key="id"
          class="nav-item"
          :class="{ active: page.id === id }"
          @click="go(id)"
        >
          <Icon :name="pages.find((p) => p.id === id)!.icon" /><span>{{
            pages.find((p) => p.id === id)!.title
          }}</span>
        </button>
      </nav>
      <div class="sidebar-bottom">
        <span class="live-dot"></span>
        <div><b>业务演示环境</b><span>独立数据 · 可重复演示</span></div>
        <button
          class="icon-btn light"
          aria-label="查看环境说明"
          @click="helpOpen = true"
        >
          <Icon name="CircleHelp" :size="17" />
        </button>
      </div>
    </aside>
    <div
      v-if="sidebarOpen"
      class="sidebar-shade"
      @click="sidebarOpen = false"
    ></div>
    <div class="main-shell">
      <header class="topbar">
        <div class="breadcrumb">
          <button
            class="icon-btn mobile-menu"
            aria-label="展开导航"
            @click="sidebarOpen = true"
          >
            <Icon name="Menu" /></button
          ><span>保障业务平台</span><Icon name="ChevronRight" :size="13" /><b>{{
            page.system ? systemName(page.system) : "工作空间"
          }}</b
          ><template v-if="page.system"
            ><Icon name="ChevronRight" :size="13" /><b>{{
              page.section
            }}</b></template
          >
        </div>
        <div class="topbar-tools">
          <button class="global-search" @click="searchOpen = true">
            <Icon name="Search" :size="15" /><span>搜索页面与功能</span
            ><kbd>⌕</kbd></button
          ><span class="environment-pill"><i></i>模拟数据</span
          ><button
            class="icon-btn"
            aria-label="帮助与说明"
            @click="helpOpen = true"
          >
            <Icon name="CircleHelp" />
          </button>
          <div class="avatar">{{ actorName.slice(0, 1) }}</div>
          <select
            class="actor-select"
            aria-label="演示身份"
            :value="store.actor"
            @change="changeActor"
          >
            <option v-for="a in store.accounts" :key="a.id" :value="a.id">
              {{ a.name }}
            </option>
          </select>
        </div>
      </header>
      <main>
        <div v-if="page.id !== 'C05'" class="page-heading">
          <div>
            <div class="eyebrow">
              {{ currentSystem?.eyebrow || "WORKSPACE" }}
            </div>
            <h1>{{ page.title }}</h1>
            <p>{{ page.description }}</p>
          </div>
          <div class="heading-actions">
            <select
              class="workspace-select"
              aria-label="工作区"
              :value="store.workspace"
              @change="changeWorkspace"
            >
              <option v-for="w in store.workspaces" :key="w.id" :value="w.id">
                {{ w.name }}
              </option></select
            ><button class="btn primary" @click="go('C08')">
              <Icon name="Presentation" :size="18" />演示中心
            </button>
          </div>
        </div>
        <DemoGuide v-if="guide.active && page.id !== 'C08' && store.data" />
        <div v-if="store.loading" class="loading-panel">
          <div class="spinner"></div>
          <h3>正在加载工作空间</h3>
          <p>同步业务对象、版本与运行记录…</p>
        </div>
        <div v-else-if="store.error" class="error-panel">
          <Icon name="WifiOff" :size="32" />
          <h3>暂时无法连接业务服务</h3>
          <p>{{ store.error }}</p>
          <p>请确认 Java 后端已启动，服务端口为 8080。</p>
          <button class="btn primary" @click="bootstrap">重新连接</button>
        </div>
        <component
          v-else-if="store.data"
          :is="viewMap[page.view]"
          :key="`${store.workspace}:${route.fullPath}`"
          :page="page"
        />
        <footer class="app-footer">
          <span>保障业务共性平台</span
          ><span v-if="store.data"
            >{{ store.data.seedVersion }}<i>·</i>工作区轮次 {{ store.data.epoch
            }}<i>·</i>业务数据已保存</span
          >
        </footer>
      </main>
    </div>
    <Transition name="toast"
      ><div
        v-if="store.toast"
        role="status"
        class="toast-message"
        :class="store.toast.kind"
      >
        <Icon
          :name="store.toast.kind === 'error' ? 'CircleAlert' : 'CircleCheck'"
          :size="20"
        /><span>{{ store.toast.message }}</span
        ><button
          class="icon-btn"
          aria-label="关闭提示"
          @click="store.toast = null"
        >
          <Icon name="X" :size="16" />
        </button></div
    ></Transition>
    <Modal v-if="searchOpen" title="快速查找" @close="searchOpen = false"
      ><div class="search-input">
        <Icon name="Search" /><input
          v-model="search"
          autofocus
          placeholder="搜索课程、预案、演练、资源…"
          aria-label="搜索页面"
        />
      </div>
      <div class="search-results">
        <button v-for="p in matches" :key="p.id" @click="go(p.id)">
          <Icon :name="p.icon" />
          <div>
            <b>{{ p.title }}</b
            ><span
              >{{ p.system ? systemName(p.system) : p.section }} ·
              {{ p.description }}</span
            >
          </div>
          <Icon name="ArrowUpRight" :size="16" />
        </button>
        <p v-if="!matches.length" class="muted">
          没有匹配页面，请尝试其他关键词。
        </p>
      </div></Modal
    >
    <Modal
      v-if="helpOpen"
      title="关于这个演示工作空间"
      @close="helpOpen = false"
      ><div class="prose">
        <p>
          三个分系统共用平台与共享资源，但课程、培训任务、评价和记录按所属系统隔离。保障任务、预案、演练与模拟施工保持独立的数据来源。
        </p>
        <h4>如何开始</h4>
        <p>
          打开“演示中心”，先准备一套完整案例，再选择系统并“开始讲解”。需要亲自操作时选择“跟随操作”，每步会提示身份、按钮和预期结果。
        </p>
        <h4>数据与平台能力</h4>
        <p>
          业务状态、评分、排程和报告由后端计算并保存；场景、物理和外设为假设虚拟平台的交互示意。所有装备及工程数据均为合成样例。
        </p>
        <h4>重新演示</h4>
        <p>
          在演示管理中新建或克隆独立工作区。重置会清空当前工作区的本轮业务记录，其他工作区保持原样。
        </p>
      </div></Modal
    >
  </div>
</template>

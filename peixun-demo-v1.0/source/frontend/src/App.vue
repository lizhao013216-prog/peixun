<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { pages, pathFor } from "./catalog";
import {
  store,
  bootstrap,
  refresh,
  login,
  switchWorkspace,
  notify,
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
import DemoGuide from "./components/DemoGuide.vue";
import { guide } from "./guide";
const route = useRoute(),
  router = useRouter();
const page = computed(() => route.meta as any);
const sidebarOpen = ref(false),
  search = ref(""),
  searchOpen = ref(false),
  helpOpen = ref(false);
const groups = ["操作训练", "维修培训", "保障筹划", "保障演练", "执行归档"];
const expanded = ref<Record<string, boolean>>({
  操作训练: true,
  维修培训: false,
  保障筹划: false,
  保障演练: false,
  执行归档: false,
});
watch(
  () => page.value.group,
  (group) => {
    if (group) expanded.value[group] = true;
  },
  { immediate: true },
);
const viewMap: any = {
  overview: Workbench,
  guide: GuideView,
  assets: AssetsView,
  authoring: AuthoringView,
  training: TrainingView,
  planning: PlanningView,
  exercise: ExerciseView,
  execution: ExecutionView,
  analysis: AnalysisView,
  records: RecordsView,
  settings: SettingsView,
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
let interval: any;
function go(id: string) {
  router.push(pathFor(id));
  sidebarOpen.value = false;
  searchOpen.value = false;
}
async function changeActor(e: Event) {
  try {
    await login((e.target as HTMLSelectElement).value);
    notify("已切换演示身份");
  } catch (e: any) {
    notify(e.message, "error");
  }
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
          v-for="id in ['C01', 'C08', 'C02']"
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
        <div v-for="(group, index) in groups" :key="group" class="nav-group">
          <button
            class="nav-group-title"
            :class="{ current: page.group === group }"
            @click="expanded[group] = !expanded[group]"
          >
            <Icon
              :name="
                [
                  'Orbit',
                  'GraduationCap',
                  'ClipboardList',
                  'Radar',
                  'FolderCheck',
                ][index]
              "
            /><span>{{ group }}</span
            ><Icon
              :name="expanded[group] ? 'ChevronDown' : 'ChevronRight'"
              :size="14"
            />
          </button>
          <div v-if="expanded[group]" class="nav-children">
            <button
              v-for="p in pages.filter((p) => p.group === group)"
              :key="p.id"
              class="nav-child"
              :class="{ active: page.id === p.id }"
              @click="go(p.id)"
            >
              <span class="nav-dot"></span>{{ p.title }}
            </button>
          </div>
        </div>
        <div class="nav-section-label space-top">公共服务</div>
        <button
          v-for="id in ['C04', 'C06', 'C07']"
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
            page.group === "公共" ? "工作空间" : page.group
          }}</b>
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
              {{
                page.group === "公共"
                  ? "WORKSPACE"
                  : page.group === "操作训练"
                    ? "OPERATION TRAINING"
                    : page.group === "维修培训"
                      ? "MAINTENANCE TRAINING"
                      : "SUPPORT OPERATIONS"
              }}
            </div>
            <h1>{{ page.title }}</h1>
            <p>{{ page.description }}</p>
          </div>
          <div class="heading-actions">
            <select
              class="workspace-select"
              aria-label="工作区"
              :value="store.workspace"
              @change="
                switchWorkspace(($event.target as HTMLSelectElement).value)
              "
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
          :key="`${store.workspace}:${page.id}`"
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
            ><span>{{ p.group }} · {{ p.description }}</span>
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
          三个分系统共用资源、课程版本和业务记录。任务、预案、演练与模拟施工保持独立的数据来源。
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

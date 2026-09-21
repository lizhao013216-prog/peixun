<script setup lang="ts">
import { computed, ref } from "vue";
import { useRouter } from "vue-router";
import { store, downloadAsset, command, upload } from "../store";
import Icon from "../components/Icon.vue";
import Badge from "../components/Badge.vue";
import Modal from "../components/Modal.vue";
import Empty from "../components/Empty.vue";
import SceneView from "../components/SceneView.vue";
const router = useRouter(),
  query = ref(""),
  category = ref("全部资源"),
  layout = ref("grid"),
  showCreate = ref(false),
  preview = ref<any>(null),
  fileInput = ref<HTMLInputElement>();
const form = ref({
  name: "",
  category: "模型",
  format: "FBX",
  description: "",
});
const categories = [
  "全部资源",
  "模型",
  "场景",
  "文档",
  "图像",
  "动画",
  "视频",
  "音频",
  "环境",
];
const assets = computed(() =>
  store.data.assets.filter(
    (a: any) =>
      (category.value === "全部资源" || a.category === category.value) &&
      a.name.toLowerCase().includes(query.value.toLowerCase()),
  ),
);
const assetIcon = (a: any) =>
  ({
    模型: "Box",
    场景: "Warehouse",
    文档: "FileText",
    图像: "Image",
    动画: "Clapperboard",
    视频: "Video",
    音频: "AudioLines",
    环境: "CloudSun",
  })[a.category] || "Box";
async function create() {
  const r = await command("asset.import", form.value, "资源草稿已创建");
  if (r) {
    showCreate.value = false;
    form.value = { name: "", category: "模型", format: "FBX", description: "" };
  }
}
async function process(a: any) {
  await command(
    "asset.process",
    { id: a.id, operation: "轻量化", texture: "优化" },
    "已创建派生处理任务",
  );
  preview.value = null;
}
async function fileChanged(e: Event) {
  const f = (e.target as HTMLInputElement).files?.[0];
  if (f) await upload(f);
  (e.target as HTMLInputElement).value = "";
}
</script>
<template>
  <div class="toolbar">
    <div class="filter-search">
      <Icon name="Search" :size="15" /><input
        v-model="query"
        placeholder="搜索资源名称…"
        aria-label="搜索资源"
      />
    </div>
    <span class="spacer"></span>
    <div class="toggle-group">
      <button
        :class="{ active: layout === 'grid' }"
        aria-label="网格视图"
        @click="layout = 'grid'"
      >
        <Icon name="LayoutGrid" :size="15" /></button
      ><button
        :class="{ active: layout === 'list' }"
        aria-label="列表视图"
        @click="layout = 'list'"
      >
        <Icon name="List" :size="15" />
      </button>
    </div>
    <input
      ref="fileInput"
      type="file"
      class="file-input"
      @change="fileChanged"
    /><button class="btn secondary" @click="fileInput?.click()">
      <Icon name="Upload" :size="15" />上传文件</button
    ><button class="btn primary" @click="showCreate = true">
      <Icon name="Plus" :size="16" />导入模拟资源
    </button>
  </div>
  <div class="tabs">
    <button
      v-for="cat in categories"
      :key="cat"
      :class="{ active: category === cat }"
      @click="category = cat"
    >
      {{ cat
      }}<span v-if="cat === '全部资源'">{{ store.data.assets.length }}</span>
    </button>
  </div>
  <div class="section-label">
    <span>{{ category }} · {{ assets.length }} 项</span
    ><span>版本化管理 · 模型解析使用假设平台能力</span>
  </div>
  <div v-if="layout === 'grid'" class="asset-grid">
    <article
      v-for="a in assets"
      :key="a.id"
      class="asset-card"
      tabindex="0"
      @click="preview = a"
      @keydown.enter="preview = a"
    >
      <div class="asset-preview">
        <span class="asset-format">{{ a.format }}</span
        ><svg
          v-if="a.category === '模型'"
          width="145"
          height="110"
          viewBox="0 0 200 135"
          aria-hidden="true"
        >
          <ellipse
            cx="105"
            cy="111"
            rx="65"
            ry="13"
            fill="#b7cbdc"
            opacity=".3"
          />
          <g v-if="a.id.includes('CTRL')">
            <path d="M63 28 98 10 143 34 108 55Z" fill="#bacddc" />
            <path d="M63 28v67l45 27V55Z" fill="#89a8bf" />
            <path d="M108 55 143 34v67l-35 21Z" fill="#557b98" />
            <path d="M75 43 95 55v21L75 64Z" fill="#284d68" />
            <path d="m78 52 12 7" stroke="#94d9c4" stroke-width="3" />
            <circle cx="85" cy="84" r="4" fill="#86ccb7" />
          </g>
          <g v-else-if="a.id.includes('VALVE')">
            <path
              d="m40 91 66-34 50 25"
              fill="none"
              stroke="#a0b9cb"
              stroke-width="26"
            />
            <path
              d="m40 86 66-34 50 25"
              fill="none"
              stroke="#c4d5e1"
              stroke-width="8"
            />
            <path d="M108 55V27" stroke="#5d8da5" stroke-width="8" />
            <ellipse
              cx="108"
              cy="26"
              rx="34"
              ry="13"
              fill="none"
              stroke="#439b99"
              stroke-width="6"
            />
            <path d="M77 26h62m-31-11v23" stroke="#439b99" stroke-width="4" />
          </g>
          <g v-else>
            <path d="M32 87 102 50 171 86 99 126Z" fill="#a8bccd" />
            <path d="M32 87v9l67 38v-8Z" fill="#7894aa" />
            <path d="m99 126 72-40v9l-72 39Z" fill="#5b7a94" />
            <path d="M98 58 131 40 159 56v44l-33 18-28-14Z" fill="#567f9e" />
            <path d="m98 58 28 16 33-18-28-16Z" fill="#acc2d3" />
            <path
              d="M105 68v29m7-25v29m7-26v30"
              stroke="#96b3c7"
              stroke-width="3"
            />
            <path d="M52 67 81 50l28 15v38l-28 16-29-16Z" fill="#7298b5" />
            <ellipse
              cx="69"
              cy="85"
              rx="17"
              ry="24"
              transform="rotate(-25 69 85)"
              fill="#3e6688"
              stroke="#a1bbcf"
              stroke-width="5"
            />
            <ellipse cx="69" cy="85" rx="7" ry="11" fill="#a5d3ce" />
            <path
              d="M83 55V34l19-10"
              fill="none"
              stroke="#a5bed0"
              stroke-width="12"
            />
          </g></svg
        ><Icon v-else :name="assetIcon(a)" :size="53" />
      </div>
      <div class="asset-info">
        <b>{{ a.name }}</b>
        <div class="asset-meta">
          <span>{{ a.category }} · V{{ a.version }}</span
          ><Badge :status="a.status" />
        </div>
      </div>
    </article>
  </div>
  <section v-else class="panel">
    <div class="table-scroll">
      <table class="data-table">
        <thead>
          <tr>
            <th>资源名称</th>
            <th>类型 / 格式</th>
            <th>版本</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="a in assets" :key="a.id">
            <td>
              <b>{{ a.name }}</b
              ><small>{{ a.id }}</small>
            </td>
            <td>{{ a.category }} / {{ a.format }}</td>
            <td>V{{ a.version }}</td>
            <td><Badge :status="a.status" /></td>
            <td>
              <div class="actions">
                <button class="text-btn" @click="preview = a">查看</button
                ><button class="text-btn" @click="process(a)">派生处理</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
  <Empty
    v-if="!assets.length"
    title="没有找到资源"
    description="调整筛选条件，或导入新的业务资源。"
  />
  <Modal v-if="showCreate" title="导入模拟资源" @close="showCreate = false"
    ><div class="info-note">
      <Icon
        name="Info"
        :size="16"
      />填写资源与格式信息后创建管理记录。模型转换与渲染能力由平台模拟器提供。
    </div>
    <div class="form-grid">
      <label class="field full"
        ><span>资源名称 *</span
        ><input v-model="form.name" placeholder="例如：泵组密封组件" /></label
      ><label class="field"
        ><span>资源类别</span
        ><select v-model="form.category">
          <option v-for="c in categories.slice(1)" :key="c">{{ c }}</option>
        </select></label
      ><label class="field"
        ><span>源格式</span
        ><select v-model="form.format">
          <option
            v-for="f in [
              'FBX',
              'OBJ',
              'STEP',
              'GLB',
              'PNG',
              'SVG',
              'PDF',
              'MP4',
              'WAV',
            ]"
            :key="f"
          >
            {{ f }}
          </option>
        </select></label
      ><label class="field full"
        ><span>资源说明</span
        ><textarea
          v-model="form.description"
          placeholder="说明对象、用途或需要保留的关键部件"
        />
      </label>
    </div>
    <template #footer
      ><button class="btn secondary" @click="showCreate = false">取消</button
      ><button
        class="btn primary"
        :disabled="store.busy > 0 || !form.name.trim()"
        @click="create"
      >
        创建资源
      </button></template
    ></Modal
  >
  <Modal v-if="preview" :title="preview.name" wide @close="preview = null"
    ><SceneView
      v-if="['模型', '场景', '环境'].includes(preview.category)"
      compact
    />
    <div v-else class="inline-empty">
      <Icon :name="assetIcon(preview)" :size="40" />
      <div>
        <b>{{ preview.name }}</b>
        <p>
          {{
            preview.sourceType === "SEED_DATA"
              ? "预置能力资源，实际解析和播放由假设虚拟平台提供。"
              : "已保存的资源文件或平台模拟记录。"
          }}
        </p>
      </div>
    </div>
    <div class="grid-2">
      <div class="kv-list">
        <div>
          <span>资源编号</span><b class="mono">{{ preview.id }}</b>
        </div>
        <div>
          <span>关联对象</span><b>{{ preview.componentId }}</b>
        </div>
        <div>
          <span>资源版本</span><b>V{{ preview.version }}</b>
        </div>
      </div>
      <div class="kv-list">
        <div>
          <span>来源格式</span><b>{{ preview.format }}</b>
        </div>
        <div>
          <span>来源</span
          ><b>{{
            preview.sourceType === "UPLOADED_FILE"
              ? "实际上传文件"
              : "模拟平台 / 预置样例"
          }}</b>
        </div>
        <div><span>管理状态</span><Badge :status="preview.status" /></div>
      </div>
    </div>
    <p class="note-caption">{{ preview.description }}</p>
    <template #footer
      ><button
        v-if="preview?.blobRef"
        class="btn secondary"
        @click="downloadAsset(preview)"
      >
        下载源文件</button
      ><button class="btn secondary" @click="process(preview)">
        <Icon name="Layers3" :size="15" />生成派生版本</button
      ><button
        v-if="['DRAFT', 'PENDING_REVIEW'].includes(preview.status)"
        class="btn primary"
        @click="
          command('asset.approve', { id: preview.id }, '资源已审核');
          preview = null;
        "
      >
        审核资源</button
      ><button
        v-else
        class="btn primary"
        @click="
          router.push('/editor/current');
          preview = null;
        "
      >
        进入场景编辑
      </button></template
    ></Modal
  >
</template>

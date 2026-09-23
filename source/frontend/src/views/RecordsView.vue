<script setup lang="ts">
import { computed, ref } from "vue";
import { useRouter } from "vue-router";
import { store, command, selected, download, login, notify } from "../store";
import { pathFor, type PageMeta } from "../catalog";
import Icon from "../components/Icon.vue";
import Badge from "../components/Badge.vue";
import Modal from "../components/Modal.vue";
import Empty from "../components/Empty.vue";
import EquipmentSceneView from "../components/EquipmentSceneView.vue";
const props = defineProps<{ page: PageMeta }>(),
  router = useRouter(),
  s = computed(() => store.data);
const tab = ref("attempts"),
  query = ref(""),
  detail = ref<any>(null),
  showCreate = ref(false),
  planId = ref(""),
  reviewComment = ref("资料完整，来源可追溯，同意通过。"),
  supplement = ref("已补充预案优化原因及关联训练说明。"),
  issueModal = ref<any>(null),
  issueType = ref("CONTENT"),
  issueName = ref("建议完善泵组异常识别与部件检查提示"),
  issueDescription = ref("请结合本次训练步骤、结果与事件说明需要处理的具体问题。");
const archive = computed(() =>
  selected(s.value.archives, store.selectedArchive),
);
const allTabs = [
  ["attempts", "训练记录"],
  ["trainingArchives", "已确认归档"],
  ["runs", "演练记录"],
  ["executions", "模拟实绩"],
  ["evaluations", "指标评估"],
  ["comparisons", "对比报告"],
  ["plans", "预案版本"],
];
const tabs = computed(() =>
  props.page.feature === "training-records"
    ? allTabs.filter(([key]) => ["attempts", "trainingArchives"].includes(key))
    : allTabs,
);
const recordsFor = (key: string) =>
  s.value[key].filter(
    (r: any) => !props.page.system || r.domain === props.page.system,
  );
const tabCount = (key: string) => recordsFor(key).length;
const rows = computed(() =>
  recordsFor(tab.value).filter((r: any) =>
    `${r.name || r.courseName || r.id}${r.id}`.includes(query.value),
  ),
);
async function create() {
  const r = await command(
    "archive.create",
    { planId: planId.value },
    "成果包已建立",
  );
  if (r) {
    store.selectedArchive = r.id;
    showCreate.value = false;
  }
}
async function role(id: string) {
  await login(id);
  notify("已切换为" + store.accounts.find((a) => a.id === id)?.name);
}
async function review(approved: boolean) {
  await command(
    "archive.review",
    { id: archive.value.id, approved, comment: reviewComment.value },
    approved ? "本级审核已通过" : "成果包已退回，保留本轮审核意见",
  );
}
async function issue() {
  const r = await command(
    "issue.create",
    { id: issueModal.value.id, type: issueType.value, name: issueName.value, description: issueDescription.value },
    "问题单已保存，转任务仍需人工确认",
  );
  if (r) issueModal.value = null;
}
function issueTask() { router.push("/support/training-issues"); }
</script>
<template>
  <template v-if="page.id === 'S316'"
    ><div class="toolbar">
      <select
        v-if="s.archives.length"
        v-model="store.selectedArchive"
        aria-label="成果包"
      >
        <option value="">最近的成果包</option>
        <option v-for="a in s.archives" :key="a.id" :value="a.id">
          {{ a.name }}
        </option></select
      ><Badge v-if="archive" :status="archive.status" /><span
        class="spacer"
      ></span
      ><button
        class="btn primary"
        @click="
          planId = store.selectedPlan || s.plans.at(-1)?.id || '';
          showCreate = true;
        "
      >
        <Icon name="FolderPlus" :size="15" />建立成果包
      </button>
    </div>
    <section v-if="!archive" class="panel">
      <Empty
        title="沉淀可复用的保障成果"
        description="关联本版预案、完成演练、施工与对比记录，经过项目、企业和专家三级审核后发布。"
        icon="FolderCheck"
        ><button
          class="btn primary"
          @click="
            planId = s.plans.at(-1)?.id || '';
            showCreate = true;
          "
        >
          建立成果包
        </button></Empty
      >
    </section>
    <template v-else
      ><section class="panel section-space">
        <div class="panel-header">
          <div>
            <h3>{{ archive.name }}</h3>
            <p>
              {{ archive.id }} · 第 {{ archive.round || 1 }} 轮审核 · 预案
              {{ archive.planVersion }}
            </p>
          </div>
          <button class="text-btn" @click="download('archives', archive.id)">
            导出成果清单<Icon name="Download" :size="14" />
          </button>
        </div>
        <div class="review-flow">
          <div
            v-for="(name, i) in ['项目审核', '企业审核', '专家审核']"
            :key="name"
            class="review-stage"
            :class="{
              done: archive.level > i && archive.status !== 'RETURNED',
            }"
          >
            <Icon
              :name="
                archive.level > i && archive.status !== 'RETURNED'
                  ? 'BadgeCheck'
                  : 'UserRoundCheck'
              "
              :size="27"
            /><b>{{ name }}</b
            ><span>{{
              archive.status === "RETURNED"
                ? "本轮已退回"
                : archive.level > i
                  ? "已通过"
                  : archive.level === i
                    ? "待处理"
                    : "等待上一级"
            }}</span>
          </div>
        </div>
      </section>
      <div class="detail-layout">
        <section class="panel">
          <div class="panel-header"><h3>资料完整性</h3></div>
          <div
            v-for="item in [
              { name: '预案版本', sub: archive.planId, ok: true },
              {
                name: '本版演练记录',
                sub: archive.runIds.join(' / ') || '缺少本版完成演练',
                ok: archive.runIds.length > 0,
              },
              {
                name: '模拟施工记录',
                sub: archive.executionIds.join(' / ') || '缺少已验收模拟施工',
                ok: archive.executionIds.length > 0,
              },
              {
                name: '效果对比与改进',
                sub: archive.comparisonIds.join(' / ') || '缺少已确认对比记录',
                ok: archive.comparisonIds.length > 0,
              },
            ]"
            :key="item.name"
            class="checklist-item"
          >
            <div class="check-icon" :class="{ done: item.ok }">
              <Icon :name="item.ok ? 'Check' : 'AlertCircle'" :size="17" />
            </div>
            <div>
              <b>{{ item.name }}</b>
              <p class="break-word">{{ item.sub }}</p>
            </div>
            <Badge :status="item.ok ? 'READY' : 'PENDING'" />
          </div>
          <div class="panel-body" style="padding-top: 18px">
            <button
              v-if="['DRAFT', 'RETURNED'].includes(archive.status)"
              class="btn primary"
              @click="
                command(
                  'archive.submit',
                  { id: archive.id },
                  '成果包已提交新审核轮次',
                )
              "
            >
              {{
                archive.status === "RETURNED" ? "重新提交审核" : "提交三级审核"
              }}
            </button>
            <div
              v-if="archive.status === 'RETURNED'"
              class="form-stack"
              style="margin-top: 20px"
            >
              <label class="field"
                ><span>补充说明</span><textarea v-model="supplement" /></label
              ><button
                class="btn secondary"
                @click="
                  command(
                    'archive.supplement',
                    { id: archive.id, comment: supplement },
                    '补充说明已保存',
                  )
                "
              >
                保存补充说明
              </button>
            </div>
            <div v-if="archive.status === 'L3_APPROVED'" class="button-row">
              <button
                class="btn primary"
                @click="
                  command(
                    'archive.publish',
                    { id: archive.id },
                    '成果已发布，可从预案库复用',
                  )
                "
              >
                发布成果</button
              ><button class="btn secondary" @click="role('PLANNER')">
                切换筹划员
              </button>
            </div>
            <button
              v-if="archive.status === 'PUBLISHED'"
              class="btn primary"
              @click="router.push(pathFor('S305'))"
            >
              进入预案库复用<Icon name="ArrowRight" :size="15" />
            </button>
          </div>
        </section>
        <div class="detail-sidebar">
          <section class="panel">
            <div class="panel-header"><h3>审核工作台</h3></div>
            <div class="panel-body form-stack">
              <label class="field"
                ><span>切换审核身份</span
                ><select
                  :value="store.actor"
                  @change="role(($event.target as HTMLSelectElement).value)"
                >
                  <option
                    v-for="a in store.accounts.filter(
                      (a) =>
                        a.id.startsWith('REVIEWER') ||
                        a.id === 'PLANNER' ||
                        a.id === 'ADMIN',
                    )"
                    :key="a.id"
                    :value="a.id"
                  >
                    {{ a.name }}
                  </option>
                </select></label
              ><label class="field"
                ><span>审核意见</span
                ><textarea v-model="reviewComment" /></label
              ><button
                class="btn primary"
                :disabled="
                  !['SUBMITTED', 'L1_APPROVED', 'L2_APPROVED'].includes(
                    archive.status,
                  )
                "
                @click="review(true)"
              >
                本级审核通过</button
              ><button
                class="btn danger"
                :disabled="
                  !['SUBMITTED', 'L1_APPROVED', 'L2_APPROVED'].includes(
                    archive.status,
                  )
                "
                @click="review(false)"
              >
                退回补充
              </button>
              <p class="note-caption">
                按级审核，不能跳级。退回后重新提交新一轮审核，历史意见保留。
              </p>
            </div>
          </section>
        </div>
      </div>
      <section class="panel">
        <div class="panel-header"><h3>全部审核意见</h3></div>
        <div class="table-scroll">
          <table v-if="archive.reviews.length" class="data-table">
            <thead>
              <tr>
                <th>轮次</th>
                <th>级别</th>
                <th>审核身份</th>
                <th>结论</th>
                <th>意见</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(r, i) in archive.reviews" :key="i">
                <td>{{ r.round }}</td>
                <td>L{{ r.level }}</td>
                <td>
                  {{
                    store.accounts.find((a) => a.id === r.actor)?.name ||
                    r.actor
                  }}
                </td>
                <td>
                  <Badge :status="r.approved ? 'APPROVED' : 'RETURNED'" />
                </td>
                <td>{{ r.comment }}</td>
              </tr>
            </tbody>
          </table>
          <p v-else class="empty-small">提交后将在此记录每一级的审核意见。</p>
        </div>
      </section></template
    ></template
  >
  <template v-else
    ><div class="toolbar">
      <div class="filter-search">
        <Icon name="Search" :size="15" /><input
          v-model="query"
          placeholder="搜索名称或记录编号…"
        />
      </div>
      <span class="spacer"></span
      ><span class="pill">只读记录 · 原始版本可追溯</span>
    </div>
    <div class="tabs">
      <button
        v-for="t in tabs"
        :key="t[0]"
        :class="{ active: tab === t[0] }"
        @click="tab = t[0]"
      >
        {{ t[1] }}<span>{{ tabCount(t[0]) }}</span>
      </button>
    </div>
    <section class="panel">
      <div v-if="rows.length" class="table-scroll">
        <table class="data-table">
          <thead>
            <tr>
              <th>业务记录</th>
              <th>版本 / 类型</th>
              <th>结果</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in [...rows].reverse()" :key="r.id">
              <td>
                <b>{{ r.name || r.courseName || r.algorithm || r.id }}</b
                ><small>{{ r.id }}</small>
              </td>
              <td>
                {{
                  r.planVersion ||
                  r.version ||
                  (r.courseVersion ? "V" + r.courseVersion : r.algorithm || "—")
                }}
              </td>
              <td>
                {{
                  r.score != null
                    ? r.score + " 分"
                    : r.schedule
                      ? r.schedule.finish + " min"
                      : r.summary
                        ? r.summary.finish + " min"
                        : r.timeDeltaRate != null
                          ? r.timeDeltaRate + "% 工期偏差"
                          : "已保存"
                }}
              </td>
              <td><Badge :status="r.status || 'COMPLETED'" /></td>
              <td>
                <div class="actions">
                  <button class="text-btn" @click="detail = r">详情</button
                  ><button class="text-btn" @click="download(tab, r.id)">
                    导出MD</button
                  ><button
                    v-if="
                      !page.system &&
                      tab === 'attempts' &&
                      r.status === 'COMPLETED'
                    "
                    class="text-btn"
                    @click="issueModal = r"
                  >
                    记录问题</button
                  ><button
                    v-if="tab === 'runs'"
                    class="text-btn"
                    @click="
                      store.selectedRun = r.id;
                      router.push(pathFor('S311', r.id));
                    "
                  >
                    复盘
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <Empty
        v-else
        title="当前分类还没有记录"
        description="完成对应业务流程后，记录会自动保存到这里。"
        icon="FileChartColumn"
      />
    </section>
    <section v-if="!page.system" class="panel">
      <div class="panel-header">
        <div>
          <h3>训练问题与业务转化</h3>
          <p>问题经人工确认后创建保障任务，不会自动修改装备状态</p>
        </div>
      </div>
      <div class="panel-body">
        <div v-for="i in s.issues" :key="i.id" class="list-card">
          <div class="resource-icon"><Icon name="MessageSquareWarning" /></div>
          <div>
            <b>{{ i.name }}</b>
            <p>来源 {{ i.sourceId }} · {{ i.id }}</p>
          </div>
          <button class="text-btn" @click="issueTask">进入问题处理</button>
        </div>
        <p v-if="!s.issues.length" class="empty-small">
          在已完成的训练记录中创建问题单，保留原始课程和操作来源。
        </p>
      </div>
    </section></template
  >
  <Modal v-if="showCreate" title="建立预案成果包" @close="showCreate = false"
    ><div class="form-stack">
      <label class="field"
        ><span>批准的预案版本</span
        ><select v-model="planId">
          <option value="" disabled>选择预案</option>
          <option
            v-for="p in s.plans.filter((p: any) =>
              ['APPROVED', 'PUBLISHED'].includes(p.status),
            )"
            :key="p.id"
            :value="p.id"
          >
            {{ p.name }} · {{ p.version }} · {{ p.id }}
          </option>
        </select></label
      >
      <div class="info-note">
        <Icon
          name="FolderCheck"
          :size="16"
        />自动关联本版完成演练、已验收模拟施工和效果对比。资料不齐全时无法提交审核。
      </div>
    </div>
    <template #footer
      ><button class="btn primary" :disabled="!planId" @click="create">
        建立成果包
      </button></template
    ></Modal
  >
  <Modal
    v-if="detail"
    :title="detail.name || detail.courseName || detail.id"
    wide
    @close="detail = null"
    ><div class="info-note">
      <Icon
        name="FileCheck2"
        :size="16"
      />已保存业务记录，只读查看；不会再次触发计分、库存或工序事件。
    </div>
    <template v-if="tab === 'trainingArchives'">
      <div class="completion-metrics"><div><b>{{ detail.score ?? '不计分' }}</b><span>归档成绩</span></div><div><b>{{ detail.events?.length || 0 }}</b><span>过程事件</span></div><div><b>{{ detail.courseRef?.version || '—' }}</b><span>课件版本</span></div><div><b>{{ detail.archivedAt ? new Date(detail.archivedAt).toLocaleString('zh-CN') : '—' }}</b><span>归档时间</span></div></div>
      <EquipmentSceneView v-if="detail.courseSnapshot?.sceneSnapshot" :context-id="detail.id" :domain="detail.domain" title="归档训练最终场景" :scene="detail.courseSnapshot.sceneSnapshot" :topology="detail.courseSnapshot.topologySnapshot" :states="detail.finalStates" mode="result" compact show-relations />
      <div class="panel-body event-list"><article v-for="event in detail.events || []" :key="event.id"><div class="event-sequence">#{{ event.sequence }}</div><div><b>{{ event.actionId }}</b><p>{{ event.reason || event.message }}</p></div><Badge :status="event.result" /></article></div>
      <details><summary>技术详情（结构化数据）</summary><pre class="code-area">{{ JSON.stringify(detail, null, 2) }}</pre></details>
    </template>
    <pre v-else class="code-area">{{ JSON.stringify(detail, null, 2) }}</pre>
    <template #footer
      ><button class="btn primary" @click="download(tab, detail.id)">
        导出Markdown报告
      </button></template
    ></Modal
  >
  <Modal v-if="issueModal" title="记录课程与操作问题" @close="issueModal = null"
    ><div class="form-stack">
      <div class="info-note">
        <Icon name="Link" :size="16" />来源：{{ issueModal.courseName }} /
        {{ issueModal.id }}
      </div>
      <label class="field"
        ><span>问题类型</span><select v-model="issueType"><option value="CONTENT">课程内容</option><option value="LEARNING">学习补训</option><option value="PLATFORM">平台技术</option><option value="EQUIPMENT_SUPPORT">疑似装备保障</option></select></label>
      <label class="field"><span>问题标题</span><input v-model="issueName" /></label>
      <label class="field"><span>问题描述</span><textarea v-model="issueDescription" />
      </label>
    </div>
    <template #footer
      ><button class="btn primary" @click="issue">保存问题单</button></template
    ></Modal
  >
</template>

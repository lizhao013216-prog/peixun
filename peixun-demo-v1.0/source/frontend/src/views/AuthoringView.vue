<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { store, command, selected, login, notify } from "../store";
import { pathFor } from "../catalog";
import Icon from "../components/Icon.vue";
import Badge from "../components/Badge.vue";
import Modal from "../components/Modal.vue";
import Empty from "../components/Empty.vue";
import SceneView from "../components/SceneView.vue";
import CourseBuilder from "../components/CourseBuilder.vue";
const props = defineProps<{ page: any }>();
const route = useRoute(),
  router = useRouter();
const s = computed(() => store.data);
const domain = computed(() =>
  props.page.group === "维修培训" ? "MAINTENANCE" : "OPERATION",
);
const scene = ref<any>(JSON.parse(JSON.stringify(s.value.scene))),
  object = ref("PUMP-01"),
  threshold = ref(s.value.topology.threshold),
  sensor = ref(s.value.topology.sensor),
  frameTime = ref(0),
  stationForm = ref(false),
  station = ref({
    name: "泵组操作台位",
    protocol: "TCP",
    address: "mock://station/pump-01",
    mapping: "button.start → CTRL.startRequest",
  }),
  templateName = ref("泵组操作系统模板"),
  showTemplate = ref(false);
const isEditor = computed(() =>
  ["C03", "S101", "S201"].includes(props.page.id),
);
const isCourse = computed(() => ["S104", "S202"].includes(props.page.id));
async function doCourse(action: string, c: any) {
  await command(action, { id: c.id });
}
async function saveScene() {
  await command(
    "scene.save",
    scene.value,
    "场景配置已保存，后续课程将引用此版本",
  );
}
async function useTeacher() {
  await login("INSTRUCTOR");
  notify("已切换为教员，可处理课程审核");
}
const pendingCourses = computed(() =>
  s.value.courses.filter((c: any) =>
    ["PENDING_REVIEW", "APPROVED", "BUILD_FAILED", "BUILDING"].includes(
      c.status,
    ),
  ),
);
</script>
<template>
  <template v-if="isEditor"
    ><div class="toolbar">
      <span class="pill"
        >{{ props.page.id === "S101" ? "素材工程" : "维修工位场景" }} · V{{
          s.scene.revision
        }}</span
      ><span class="pill">4 个设备对象</span><span class="spacer"></span
      ><button class="btn secondary" @click="showTemplate = true">
        <Icon name="Copy" :size="15" />保存为模板</button
      ><button
        class="btn primary"
        :disabled="store.busy > 0"
        @click="saveScene"
      >
        <Icon name="Save" :size="15" />保存场景
      </button>
    </div>
    <div class="editor-layout">
      <section class="panel">
        <div class="panel-header">
          <h3>场景对象</h3>
          <Icon name="Layers3" :size="16" />
        </div>
        <div class="editor-tree">
          <button
            v-for="(o, i) in [
              ['PUMP-01', '通用泵组'],
              ['VALVE-01', '电动阀门'],
              ['CTRL-01', '控制单元'],
              ['SENSOR-01', '状态传感器'],
            ]"
            :key="o[0]"
            :class="{ active: object === o[0] }"
            @click="object = o[0]"
          >
            <Icon
              :name="['Box', 'CircleGauge', 'Cpu', 'Radio'][i]"
              :size="15"
            />{{ o[1] }}
          </button>
        </div>
        <div class="panel-body">
          <div class="section-label">素材库</div>
          <div class="pill-list">
            <span class="pill">模型</span><span class="pill">动画</span
            ><span class="pill">媒体</span><span class="pill">特效</span>
          </div>
          <button
            class="text-btn"
            style="margin-top: 16px"
            @click="router.push('/assets')"
          >
            打开资源中心<Icon name="ArrowUpRight" :size="14" />
          </button>
        </div>
      </section>
      <div class="editor-center">
        <SceneView
          :selected="object"
          :state="s.topology.pumpState"
          :weather="scene.light"
          :camera="scene.camera"
          :frame-angle="
            (frameTime / 1000) * (scene.keyframes.at(-1)?.value ?? 90)
          "
          @select="object = $event"
        />
        <section class="panel">
          <div class="panel-header">
            <h3>关键帧时间轴</h3>
            <span class="mono">{{ frameTime }} ms</span>
          </div>
          <div class="panel-body">
            <div class="range-row">
              <Icon name="SkipBack" :size="15" /><input
                v-model.number="frameTime"
                type="range"
                min="0"
                max="1000"
                step="10"
                aria-label="关键帧预览时间"
              /><span class="mono"
                >角度
                {{
                  Math.round(
                    (frameTime / 1000) * (scene.keyframes.at(-1)?.value || 90),
                  )
                }}°</span
              >
            </div>
            <div class="row-flex" style="margin-top: 15px">
              <label class="field"
                ><span>末帧目标值</span
                ><input
                  v-model.number="
                    scene.keyframes[scene.keyframes.length - 1].value
                  "
                  type="number"
                  aria-label="末帧目标值"
              /></label>
              <p class="note-caption">
                正向或反向拖动预览时间；预览不产生训练授分事件。
              </p>
            </div>
          </div>
        </section>
        <section class="panel">
          <div class="panel-header">
            <h3>行为脚本</h3>
            <span class="subtle-label">事件 → 条件 → 动作 → 反馈</span>
          </div>
          <div class="script-nodes">
            <span v-for="(n, i) in scene.script" :key="i"
              ><Icon
                :name="
                  i === 0
                    ? 'MousePointer2'
                    : i === 1
                      ? 'GitBranch'
                      : i === scene.script.length - 1
                        ? 'CircleCheck'
                        : 'Play'
                "
                :size="13" /><input
                v-model="scene.script[i]"
                :aria-label="`行为节点 ${Number(i) + 1}`"
                style="
                  width: 110px;
                  border: 0;
                  background: transparent;
                  font-size: 12px;
                "
            /></span>
          </div>
        </section>
      </div>
      <section class="panel">
        <div class="panel-header">
          <h3>对象与环境属性</h3>
          <Icon name="SlidersHorizontal" :size="15" />
        </div>
        <div class="panel-body property-list">
          <div class="field">
            <span>当前对象</span><input :value="object" readonly />
          </div>
          <label class="field"
            ><span>材质</span
            ><select v-model="scene.material">
              <option
                v-for="m in [
                  '金属 · 拉丝钢',
                  '金属 · 铝合金',
                  '工程塑料',
                  '橡胶',
                  '混凝土',
                  '玻璃',
                ]"
                :key="m"
              >
                {{ m }}
              </option>
            </select></label
          ><label class="field"
            ><span>天气环境</span
            ><select v-model="scene.weather">
              <option
                v-for="v in ['晴', '阴', '雨', '雪', '雾', '冰雹']"
                :key="v"
              >
                {{ v }}
              </option>
            </select></label
          ><label class="field"
            ><span>光照</span
            ><select v-model="scene.light">
              <option
                v-for="v in ['日间', '傍晚', '夜间', '室内照明']"
                :key="v"
              >
                {{ v }}
              </option>
            </select></label
          ><label class="field"
            ><span>预览视角</span
            ><select v-model="scene.camera">
              <option>总览</option>
              <option>设备</option>
              <option>操作员</option>
            </select></label
          ><label class="field"
            ><span>刚体类型</span
            ><select v-model="scene.rigidBody">
              <option value="DYNAMIC">动态刚体</option>
              <option value="STATIC">静态刚体</option>
            </select></label
          ><label class="field"
            ><span>显示层级</span
            ><select v-model.number="scene.lod">
              <option :value="1">LOD 1 · 精细</option>
              <option :value="2">LOD 2 · 标准</option>
              <option :value="3">LOD 3 · 轻量</option>
            </select></label
          ><label class="field"
            ><span>课件UI模板</span
            ><select v-model="scene.uiTemplate">
              <option>引导训练</option>
              <option>自由考核</option>
            </select></label
          ><label class="field"
            ><span>反馈效果</span
            ><select v-model="scene.effect">
              <option>状态光环</option>
              <option>对象高亮</option>
              <option>告警脉冲</option>
              <option>动作轨迹</option>
            </select></label
          >
          <div class="field">
            <span>锁定自由度</span>
            <div class="pill-list">
              <label
                v-for="axis in ['X', 'Y', 'Z', 'RX', 'RY', 'RZ']"
                :key="axis"
                class="checkbox-row"
                ><input
                  v-model="scene.lockedAxes"
                  type="checkbox"
                  :value="axis"
                />{{ axis }}</label
              >
            </div>
          </div>
        </div>
      </section>
    </div></template
  >
  <template v-else-if="page.id === 'S102'"
    ><div class="toolbar">
      <span class="pill">TOPO-01 · V{{ s.topology.version }}</span
      ><span class="pill">4 设备 / 3 连接规则</span><span class="spacer"></span
      ><button class="btn secondary" @click="showTemplate = true">
        保存系统模板</button
      ><button
        class="btn primary"
        @click="
          command('topology.save', { threshold, sensor }, '联动规则已保存')
        "
      >
        <Icon name="Save" :size="15" />保存规则
      </button>
    </div>
    <div class="detail-layout">
      <div>
        <section class="panel">
          <div class="panel-header">
            <h3>四设备联动关系</h3>
            <Badge :status="s.topology.pumpState" />
          </div>
          <div class="topology-nodes">
            <div
              v-for="(d, i) in [
                ['CTRL-01', '控制单元'],
                ['VALVE-01', '就绪信号'],
                ['PUMP-01', '泵组状态'],
                ['SENSOR-01', '状态采集'],
              ]"
              :key="d[0]"
              class="topology-node"
            >
              <Icon
                :name="['Cpu', 'CircleGauge', 'Box', 'Radio'][i]"
                :size="27"
              /><b>{{ d[0] }}</b
              ><span>{{ d[1] }}</span>
            </div>
          </div>
        </section>
        <SceneView style="margin-top: 18px" :state="s.topology.pumpState" />
        <section class="panel">
          <div class="panel-header"><h3>配置的连接规则</h3></div>
          <div class="panel-body">
            <div v-for="r in s.topology.connections" :key="r" class="list-card">
              <Icon name="Cable" />
              <div>
                <b>{{ r }}</b>
                <p>类型化信号映射 · 状态变化由后端规则计算</p>
              </div>
              <Badge status="READY" />
            </div>
          </div>
        </section>
      </div>
      <div class="detail-sidebar">
        <section class="panel">
          <div class="panel-header"><h3>信号调试</h3></div>
          <div class="panel-body form-stack">
            <label class="checkbox-row"
              ><input
                :checked="s.topology.valveReady"
                type="checkbox"
                @change="
                  command('topology.signal', {
                    valveReady: ($event.target as HTMLInputElement).checked,
                  })
                "
              />阀门已就绪</label
            ><label class="field"
              ><span>传感器演示值：{{ sensor }}</span
              ><input
                v-model.number="sensor"
                type="range"
                min="0"
                max="1"
                step=".05"
                @change="
                  command('topology.signal', { sensor }, '信号已注入')
                " /></label
            ><label class="field"
              ><span>告警阈值（无量纲）</span
              ><input
                v-model.number="threshold"
                type="number"
                min="0"
                max="1"
                step=".1" /></label
            ><button
              class="btn primary"
              @click="
                command(
                  'topology.signal',
                  { signal: 'START' },
                  '启动信号已发送',
                )
              "
            >
              <Icon name="Play" :size="15" />启动设备</button
            ><button
              class="btn secondary"
              @click="
                command('topology.signal', { signal: 'STOP' }, '设备已停止')
              "
            >
              停止设备</button
            ><button
              class="btn secondary"
              @click="
                command('topology.signal', { signal: 'RESET' }, '设备已复位')
              "
            >
              确认复位
            </button>
            <p class="note-caption">
              启动条件：启动请求为真且阀门已就绪。运行中传感器值低于阈值时触发告警。
            </p>
          </div>
        </section>
      </div>
    </div></template
  >
  <template v-else-if="page.id === 'S103'"
    ><div class="toolbar">
      <p>模板保存场景、设备组网与联动参数，复用时生成独立配置。</p>
      <span class="spacer"></span
      ><button class="btn primary" @click="showTemplate = true">
        <Icon name="Plus" :size="16" />保存当前模板
      </button>
    </div>
    <div class="course-list">
      <article
        v-for="t in s.templates.filter((t: any) => t.type === 'OPERATION')"
        :key="t.id"
        class="course-card"
      >
        <div class="course-top">
          <div class="system-symbol teal">
            <Icon name="Network" :size="23" />
          </div>
          <Badge :status="t.status" />
        </div>
        <h3>{{ t.name }}</h3>
        <p>{{ t.id }}<br />四设备组网 / 场景配置 / 联动规则</p>
        <div class="course-bottom">
          <span>独立配置副本</span
          ><button
            class="text-btn"
            @click="
              command(
                'template.apply',
                { id: t.id },
                '模板已加载为当前编辑配置',
              );
              router.push(pathFor('S102'));
            "
          >
            复用模板<Icon name="ArrowRight" :size="14" />
          </button>
        </div>
      </article>
    </div>
    <section
      v-if="!s.templates.some((t: any) => t.type === 'OPERATION')"
      class="panel"
    >
      <Empty
        title="建立第一个系统模板"
        description="在场景和组网配置完成后保存模板，下一门课程可以直接复用。"
        ><button class="btn primary" @click="router.push(pathFor('S102'))">
          前往设备组网
        </button></Empty
      >
    </section></template
  >
  <template v-else-if="page.id === 'S105'"
    ><div class="toolbar">
      <span class="pill">协议与设备输入模拟</span><span class="spacer"></span
      ><button class="btn primary" @click="stationForm = true">
        <Icon name="Plus" :size="15" />新增台位
      </button>
    </div>
    <section class="panel">
      <div class="panel-header">
        <div>
          <h3>台位连接</h3>
          <p>通过mock地址模拟连接、断开与信号输入</p>
        </div>
      </div>
      <div v-if="s.stations.length" class="table-scroll">
        <table class="data-table">
          <thead>
            <tr>
              <th>台位</th>
              <th>协议</th>
              <th>点位映射</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="st in s.stations" :key="st.id">
              <td>
                <b>{{ st.name }}</b
                ><small>{{ st.address }}</small>
              </td>
              <td>{{ st.protocol }}</td>
              <td>{{ st.mapping }}</td>
              <td><Badge :status="st.status" /></td>
              <td>
                <div class="actions">
                  <button
                    class="text-btn"
                    @click="
                      command('station.connect', {
                        id: st.id,
                        connected: st.status !== 'CONNECTED',
                      })
                    "
                  >
                    {{
                      st.status === "CONNECTED" ? "断开" : "连接测试"
                    }}</button
                  ><button
                    class="text-btn"
                    @click="
                      command(
                        'station.signal',
                        { id: st.id, point: 'button.start' },
                        '模拟台位信号已提交',
                      )
                    "
                  >
                    发送启动信号
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <Empty
        v-else
        title="还没有模拟台位"
        description="选择协议、配置点位，体验虚实设备接入的完整业务过程。"
        icon="Monitor"
        ><button class="btn primary" @click="stationForm = true">
          新增台位
        </button></Empty
      >
    </section>
    <div class="info-note" style="margin-top: 18px">
      <Icon
        name="Info"
        :size="16"
      />TCP、VRPN与XR输入均使用假设平台能力。本演示不会连接真实设备；有效输入与鼠标操作经过相同业务规则。
    </div></template
  >
  <template v-else-if="page.id === 'C04'"
    ><div class="toolbar">
      <p>先审核课程，再创建平台构建任务。浏览器运行始终加载同一课程版本。</p>
      <span class="spacer"></span
      ><button class="btn secondary" @click="useTeacher">
        <Icon name="UserRoundCheck" :size="16" />切换教员审核
      </button>
    </div>
    <section class="panel">
      <div class="panel-header">
        <h3>待办课程</h3>
        <span class="subtle-label">{{ pendingCourses.length }} 项</span>
      </div>
      <div v-if="pendingCourses.length" class="table-scroll">
        <table class="data-table">
          <thead>
            <tr>
              <th>课程</th>
              <th>领域</th>
              <th>版本</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="c in pendingCourses" :key="c.id">
              <td>
                <b>{{ c.name }}</b
                ><small>{{ c.id }}</small>
              </td>
              <td>{{ c.domain === "OPERATION" ? "操作训练" : "维修培训" }}</td>
              <td>V{{ c.version }}</td>
              <td><Badge :status="c.status" /></td>
              <td>
                <div class="actions">
                  <button
                    v-if="c.status === 'PENDING_REVIEW'"
                    class="text-btn"
                    @click="doCourse('course.approve', c)"
                  >
                    审核通过</button
                  ><button
                    v-if="c.status === 'PENDING_REVIEW'"
                    class="text-btn danger"
                    @click="doCourse('course.return', c)"
                  >
                    退回修改</button
                  ><button
                    v-if="['APPROVED', 'BUILD_FAILED'].includes(c.status)"
                    class="text-btn"
                    @click="doCourse('course.publish', c)"
                  >
                    {{ c.status === "BUILD_FAILED" ? "重试构建" : "发布课程" }}
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <Empty
        v-else
        title="暂无待处理的发布任务"
        description="在课程编排完成后提交审核，即可在这里处理。"
      />
    </section>
    <section class="panel">
      <div class="panel-header">
        <h3>平台任务记录</h3>
        <span class="subtle-label">模拟构建 · 真实版本记录</span>
      </div>
      <div class="table-scroll">
        <table v-if="s.jobs.length" class="data-table">
          <thead>
            <tr>
              <th>任务编号</th>
              <th>类型 / 目标</th>
              <th>状态</th>
              <th>处理结果</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="j in [...s.jobs].reverse()" :key="j.id">
              <td>
                <b>{{ j.id }}</b
                ><small>{{ j.targetId }}</small>
              </td>
              <td>
                {{ j.type === "COURSE" ? "课件构建" : "资产处理" }} /
                {{ j.target || "派生版本" }}
              </td>
              <td><Badge :status="j.status" /></td>
              <td>{{ j.message || "正在执行模拟任务…" }}</td>
            </tr>
          </tbody>
        </table>
        <p v-else class="empty-small">构建及处理任务将在此保留过程记录。</p>
      </div>
    </section></template
  >
  <CourseBuilder v-else-if="isCourse" :domain="domain" />
  <Modal v-if="stationForm" title="新增模拟台位" @close="stationForm = false"
    ><div class="form-stack">
      <label class="field"
        ><span>台位名称</span><input v-model="station.name" /></label
      ><label class="field"
        ><span>协议配置</span
        ><select v-model="station.protocol">
          <option>TCP</option>
          <option>VRPN</option>
          <option>XR-CONTROLLER</option>
        </select></label
      ><label class="field"
        ><span>模拟地址</span><input v-model="station.address" /></label
      ><label class="field"
        ><span>点位映射</span><input v-model="station.mapping"
      /></label>
    </div>
    <template #footer
      ><button
        class="btn primary"
        @click="
          command('station.save', station, '模拟台位已创建');
          stationForm = false;
        "
      >
        创建台位
      </button></template
    ></Modal
  >
  <Modal v-if="showTemplate" title="保存系统模板" @close="showTemplate = false"
    ><label class="field"
      ><span>模板名称</span><input v-model="templateName"
    /></label>
    <p class="note-caption">
      保存当前已提交的场景与设备组网配置，模板副本可独立复用。
    </p>
    <template #footer
      ><button
        class="btn primary"
        @click="
          command('template.save', { name: templateName }, '模板已保存');
          showTemplate = false;
        "
      >
        保存模板
      </button></template
    ></Modal
  >
</template>

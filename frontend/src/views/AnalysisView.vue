<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRouter } from "vue-router";
import { store, command, selected, download, fmt, notify } from "../store";
import { pathFor } from "../catalog";
import Icon from "../components/Icon.vue";
import Badge from "../components/Badge.vue";
import Empty from "../components/Empty.vue";
const props = defineProps<{ page: any }>(),
  router = useRouter(),
  s = computed(() => store.data);
const plan = computed(() => selected(s.value.plans, store.selectedPlan));
const algorithm = ref("AHP"),
  matrix = ref([
    [1, 5 / 3, 2.5],
    [0.6, 1, 1.5],
    [0.4, 2 / 3, 1],
  ]),
  scores = ref([80, 90, 100]),
  fuzzyText = ref(
    JSON.stringify(
      {
        weights: [0.5, 0.3, 0.2],
        matrix: [
          [0.8, 0.2, 0],
          [0.3, 0.6, 0.1],
          [0, 0.5, 0.5],
        ],
        grades: [100, 80, 60],
      },
      null,
      2,
    ),
  ),
  dea = ref([
    { name: "A", input: 10, output: 5 },
    { name: "B", input: 8, output: 5 },
    { name: "C", input: 10, output: 4 },
  ]);
const evalResult = computed(() =>
  s.value.evaluations
    .filter((e: any) => e.algorithm === algorithm.value)
    .at(-1),
);
const execution = computed(() =>
  selected(
    s.value.executions.filter((e: any) => e.status === "ACCEPTED"),
    store.selectedExecution,
  ),
);
const baseline = ref(execution.value?.planId || ""),
  cause = ref(
    "模拟拆解和装配用时增加；检测出现一次整改，建议完善装配前检查与培训提示。",
  );
const comparison = computed(() => s.value.comparisons.at(-1));
const run = computed(() =>
  selected(
    s.value.runs.filter((r: any) => r.status === "COMPLETED"),
    store.selectedRun,
  ),
);
watch(
  () => execution.value?.id,
  () => (baseline.value = execution.value?.planId || ""),
);
function matrixChanged(i: number, j: number) {
  const v = matrix.value[i][j];
  if (v > 0) matrix.value[j][i] = 1 / v;
}
async function evaluate() {
  let input: any;
  try {
    input =
      algorithm.value === "AHP"
        ? { matrix: matrix.value, scores: scores.value }
        : algorithm.value === "FUZZY"
          ? JSON.parse(fuzzyText.value)
          : { cases: dea.value };
  } catch {
    notify("请检查隶属矩阵JSON格式", "error");
    return;
  }
  await command(
    "evaluation.calculate",
    { algorithm: algorithm.value, input },
    "指标计算完成，输入与结果已保存",
  );
}
async function createComparison() {
  await command(
    "comparison.create",
    {
      executionId: execution.value.id,
      planId: baseline.value,
      cause: cause.value,
    },
    "实绩偏差与原因已保存",
  );
}
async function improve() {
  const r = await command(
    "plan.revise",
    { id: execution.value.planId, version: "P2" },
    "P2改进版已创建：增加检查、备件提前至120分钟",
  );
  if (r) {
    store.selectedPlan = r.id;
    router.push(pathFor("S306", r.id));
  }
}
const points = computed(() => s.value.sensitivity?.points || []);
const line = computed(() =>
  points.value
    .map(
      (p: any, i: number) =>
        `${60 + Number(i) * 120},${240 - (p.finish - 200) * 1.55}`,
    )
    .join(" "),
);
</script>
<template>
  <template v-if="page.id === 'S312'"
    ><div class="toolbar">
      <select v-model="store.selectedRun" aria-label="评估的演练">
        <option value="">最近完成的演练</option>
        <option
          v-for="r in s.runs.filter((r: any) => r.status === 'COMPLETED')"
          :key="r.id"
          :value="r.id"
        >
          {{ r.name }} · {{ r.id }}
        </option></select
      ><span class="spacer"></span
      ><button
        v-if="run"
        class="btn secondary"
        @click="download('runs', run.id)"
      >
        导出演练指标
      </button>
    </div>
    <div v-if="run" class="metric-strip">
      <div class="metric">
        <span>总工期</span><b>{{ run.schedule.finish }}<small>min</small></b>
        <p>源运行 {{ run.id }}</p>
      </div>
      <div class="metric">
        <span>总费用</span
        ><b>{{ fmt(run.schedule.cost) }}<small>示例单位</small></b>
        <p>人工、备件与运输分项可查</p>
      </div>
      <div class="metric">
        <span>资源等待</span><b>{{ run.schedule.waiting }}<small>min</small></b>
        <p>按约束等待时间计算</p>
      </div>
      <div class="metric">
        <span>按期完成</span
        ><b :class="run.schedule.onTime ? 'text-teal' : 'text-red'">{{
          run.schedule.onTime ? "是" : "否"
        }}</b>
        <p>截止 T+{{ run.deadline }} min</p>
      </div>
    </div>
    <div v-else class="info-note">
      <Icon
        name="Info"
        :size="16"
      />完成演练后可绑定实际业务指标。下方算法使用独立合成数据集，不代表该维修项目的真实效能结论。
    </div>
    <section class="panel">
      <div class="panel-header">
        <div>
          <h3>评估算法工作台</h3>
          <p>数据输入 → 合法性校验 → 权重与计算 → 保存结果</p>
        </div>
        <span class="pill">独立合成算例</span>
      </div>
      <div class="panel-body">
        <div class="tabs">
          <button
            v-for="a in [
              ['AHP', 'AHP层次分析'],
              ['FUZZY', '模糊综合评价'],
              ['DEA', 'DEA效率分析'],
            ]"
            :key="a[0]"
            :class="{ active: algorithm === a[0] }"
            @click="algorithm = a[0]"
          >
            {{ a[1] }}
          </button>
        </div>
        <div class="grid-2">
          <div>
            <template v-if="algorithm === 'AHP'"
              ><div class="section-label">三指标正互反判断矩阵</div>
              <div class="table-scroll">
                <table class="data-table">
                  <thead>
                    <tr>
                      <th>判断矩阵</th>
                      <th>工期</th>
                      <th>资源</th>
                      <th>成本</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="(row, i) in matrix" :key="i">
                      <td>{{ ["工期", "资源", "成本"][i] }}</td>
                      <td v-for="(v, j) in row" :key="j">
                        <input
                          v-model.number="matrix[i][j]"
                          class="table-input"
                          type="number"
                          min=".01"
                          step=".1"
                          :readonly="i >= j"
                          :aria-label="`矩阵${Number(i) + 1}-${j + 1}`"
                          @change="matrixChanged(i, j)"
                        />
                      </td>
                    </tr>
                    <tr>
                      <td>指标分数</td>
                      <td v-for="(v, i) in scores" :key="i">
                        <input
                          v-model.number="scores[i]"
                          class="table-input"
                          min="0"
                          max="100"
                          type="number"
                          :aria-label="`指标${Number(i) + 1}分数`"
                        />
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
              <p class="note-caption">
                编辑上三角，下三角自动取倒数。行几何平均法求权重，RI=0.58，CR≤0.1才能通过。
              </p></template
            ><template v-else-if="algorithm === 'FUZZY'"
              ><label class="field"
                ><span>权重、隶属矩阵及等级分</span
                ><textarea
                  v-model="fuzzyText"
                  class="code-area"
                  style="min-height: 290px"
                  spellcheck="false"
                />
              </label>
              <p class="note-caption">
                权重和与每一行隶属度之和必须为1，缺失或非法配置会阻止计算。
              </p></template
            ><template v-else
              ><div class="info-note">
                <Icon
                  name="Info"
                  :size="16"
                />单投入、单产出、正值的CCR投入导向简例。
              </div>
              <table class="data-table">
                <thead>
                  <tr>
                    <th>对象</th>
                    <th>投入</th>
                    <th>产出</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="c in dea" :key="c.name">
                    <td>{{ c.name }}</td>
                    <td>
                      <input
                        v-model.number="c.input"
                        type="number"
                        class="table-input"
                        min=".1"
                        :aria-label="c.name + '投入'"
                      />
                    </td>
                    <td>
                      <input
                        v-model.number="c.output"
                        type="number"
                        class="table-input"
                        min=".1"
                        :aria-label="c.name + '产出'"
                      />
                    </td>
                  </tr>
                </tbody></table
            ></template>
            <div class="form-footer">
              <button class="btn primary" @click="evaluate">
                <Icon name="Calculator" :size="15" />校验并计算
              </button>
            </div>
          </div>
          <div>
            <div class="section-label">计算结果</div>
            <template v-if="evalResult"
              ><div
                v-if="evalResult.score !== undefined"
                class="result-banner"
                style="margin-bottom: 18px"
              >
                <div class="score-circle">
                  <b>{{ evalResult.score }}</b
                  ><small>综合分</small>
                </div>
                <div>
                  <h3>
                    {{ algorithm === "AHP" ? "AHP加权结果" : "模糊综合结果" }}
                  </h3>
                  <p>输入与中间值已保存<br />{{ evalResult.id }}</p>
                </div>
              </div>
              <div class="kv-list" v-if="algorithm === 'AHP'">
                <div v-for="(v, i) in evalResult.weights" :key="i">
                  <span>{{ ["工期", "资源", "成本"][i] }}权重</span
                  ><b>{{ fmt(Number(v) * 100, 2) }}%</b>
                </div>
                <div>
                  <span>一致性CR</span><b>{{ fmt(evalResult.cr, 6) }}</b>
                </div>
              </div>
              <div v-if="algorithm === 'FUZZY'" class="kv-list">
                <div v-for="(v, i) in evalResult.membership" :key="i">
                  <span>{{ ["优", "良", "待改进"][i] }}隶属度</span
                  ><b>{{ fmt(v, 2) }}</b>
                </div>
              </div>
              <div v-if="algorithm === 'DEA'">
                <div
                  v-for="e in evalResult.efficiencies"
                  :key="e.name"
                  style="margin: 22px 0"
                >
                  <div class="section-label">
                    <span>对象 {{ e.name }}</span
                    ><b>{{ e.efficiency }}</b>
                  </div>
                  <div class="progress-bar">
                    <i :style="{ width: `${e.efficiency * 100}%` }"></i>
                  </div>
                </div>
              </div>
              <button
                class="btn secondary full"
                style="margin-top: 24px"
                @click="download('evaluations', evalResult.id)"
              >
                导出计算报告
              </button></template
            ><Empty
              v-else
              title="等待计算结果"
              description="修改参数并计算，即可查看权重、中间值与最终分数。"
              icon="ChartNoAxesCombined"
            />
          </div>
        </div>
      </div></section
  ></template>
  <template v-else-if="page.id === 'S313'"
    ><div class="toolbar">
      <select
        v-if="s.plans.length"
        v-model="store.selectedPlan"
        aria-label="敏感性分析预案"
      >
        <option value="">最近的预案</option>
        <option v-for="p in s.plans" :key="p.id" :value="p.id">
          {{ p.name }} · {{ p.version }}
        </option></select
      ><span class="spacer"></span
      ><button
        class="btn primary"
        :disabled="!plan"
        @click="
          command(
            'sensitivity.calculate',
            { id: plan.id },
            '五组情景已重新计算并保存',
          )
        "
      >
        <Icon name="Play" :size="15" />运行五组到货情景
      </button>
    </div>
    <div class="info-note">
      <Icon
        name="Info"
        :size="16"
      />在同一预案和资源快照下，将备件到货时间设置为90、120、135、150和180分钟。每组独立计算；这些固定情景不代表现实发生概率。
    </div>
    <template v-if="points.length"
      ><div class="metric-strip">
        <div class="metric">
          <span>已计算情景</span><b>{{ points.length }}<small>组</small></b>
          <p>输入与来源预案已保存</p>
        </div>
        <div class="metric">
          <span>平均工期</span><b>{{ s.sensitivity.mean }}<small>min</small></b>
          <p>五组工期的算术平均</p>
        </div>
        <div class="metric">
          <span>按期情景比例</span
          ><b>{{ s.sensitivity.onTimeRate }}<small>%</small></b>
          <p>期限T+270，含恰好完成</p>
        </div>
        <div class="metric">
          <span>工期变化范围</span
          ><b
            >{{
              Math.max(...points.map((p: any) => p.finish)) -
              Math.min(...points.map((p: any) => p.finish))
            }}<small>min</small></b
          >
          <p>最大值减最小值</p>
        </div>
      </div>
      <section class="panel">
        <div class="panel-header">
          <h3>到货时间对工期的影响</h3>
          <span class="subtle-label">单位：分钟</span>
        </div>
        <div class="chart-area">
          <svg
            viewBox="0 0 620 300"
            role="img"
            aria-label="到货时间与工期关系折线图"
          >
            <g v-for="v in [200, 220, 240, 260, 280, 300]" :key="v">
              <path
                :d="`M60 ${240 - (v - 200) * 1.55} H550`"
                stroke="#e5edf5"
                stroke-dasharray="4 4"
              />
              <text
                x="28"
                :y="244 - (v - 200) * 1.55"
                font-size="10"
                fill="#9cb0c2"
              >
                {{ v }}
              </text>
            </g>
            <path d="M60 131.5H550" stroke="#d9ad78" stroke-dasharray="6 5" />
            <text x="554" y="135" font-size="9" fill="#c3a078">期限270</text>
            <polyline
              :points="line"
              fill="none"
              stroke="#5e8dd1"
              stroke-width="3"
              stroke-linejoin="round"
            />
            <g v-for="(p, i) in points" :key="p.eta">
              <circle
                :cx="60 + Number(i) * 120"
                :cy="240 - (p.finish - 200) * 1.55"
                r="5"
                fill="#fff"
                stroke="#5e8dd1"
                stroke-width="3"
              />
              <text
                :x="60 + Number(i) * 120"
                :y="225 - (p.finish - 200) * 1.55"
                text-anchor="middle"
                font-size="11"
                fill="#6587a4"
              >
                {{ p.finish }}
              </text>
              <text
                :x="60 + Number(i) * 120"
                y="269"
                text-anchor="middle"
                font-size="10"
                fill="#9cb0c2"
              >
                {{ p.eta }}
              </text>
            </g>
            <text
              x="305"
              y="294"
              text-anchor="middle"
              font-size="10"
              fill="#9cb0c2"
            >
              备件到货时间 T+分钟
            </text>
          </svg>
        </div>
        <div class="table-scroll">
          <table class="data-table">
            <thead>
              <tr>
                <th>到货时间</th>
                <th>完成时间</th>
                <th>预计费用</th>
                <th>按期完成</th>
                <th>运行记录</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="p in points" :key="p.eta">
                <td>T+{{ p.eta }} min</td>
                <td>{{ p.finish }} min</td>
                <td>{{ p.cost }}</td>
                <td>
                  <Badge :status="p.finish <= 270 ? 'COMPLETED' : 'FAILED'" />
                </td>
                <td class="mono">{{ p.runId }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section></template
    >
    <section v-else class="panel">
      <Empty
        title="分析关键参数的影响"
        description="选择预案后运行五组情景，比较工期拐点与按期完成情况。"
        icon="ChartSpline"
      /></section
  ></template>
  <template v-else
    ><div class="toolbar">
      <select v-model="store.selectedExecution" aria-label="对比执行单">
        <option value="">最近完成的模拟施工</option>
        <option
          v-for="e in s.executions.filter((e: any) => e.status === 'ACCEPTED')"
          :key="e.id"
          :value="e.id"
        >
          {{ e.id }} · {{ e.planVersion }}
        </option></select
      ><select v-model="baseline" aria-label="对比基线">
        <option value="" disabled>选择基线预案</option>
        <option v-for="p in s.plans" :key="p.id" :value="p.id">
          基线 {{ p.version }} · {{ p.id }}
        </option></select
      ><span class="spacer"></span
      ><button
        class="btn primary"
        :disabled="!execution || !baseline"
        @click="createComparison"
      >
        <Icon name="GitCompareArrows" :size="15" />计算实绩偏差
      </button>
    </div>
    <section v-if="!execution" class="panel">
      <Empty
        title="等待已验收的模拟施工"
        description="实际记录完成验收后，可以与批准的预案或其他历史基线进行比较。"
        icon="GitCompareArrows"
        ><button class="btn primary" @click="router.push(pathFor('S314'))">
          进入模拟施工
        </button></Empty
      >
    </section>
    <template v-else
      ><div v-if="comparison" class="grid-2 section-space">
        <section class="panel">
          <div class="panel-header">
            <h3>工期对比</h3>
            <span class="pill">基线 {{ comparison.planVersion }}</span>
          </div>
          <div class="comparison-values">
            <div>
              <span>预案预计</span><b>{{ comparison.expectedTime }}</b
              ><small>min</small>
            </div>
            <Icon name="ArrowRight" :size="20" style="color: #a7bace" />
            <div>
              <span>模拟实绩</span><b>{{ comparison.actualTime }}</b
              ><small>min</small>
            </div>
            <div>
              <span>偏差</span
              ><b style="color: #c09165"
                >{{ comparison.timeDeltaRate > 0 ? "+" : ""
                }}{{ comparison.timeDeltaRate }}%</b
              ><small>{{ comparison.timeDelta }} min</small>
            </div>
          </div>
        </section>
        <section class="panel">
          <div class="panel-header">
            <h3>费用对比</h3>
            <span class="subtle-label">示例费用单位</span>
          </div>
          <div class="comparison-values">
            <div>
              <span>预案预计</span><b>{{ comparison.expectedCost }}</b
              ><small>人工+备件+运输</small>
            </div>
            <Icon name="ArrowRight" :size="20" style="color: #a7bace" />
            <div>
              <span>模拟实绩</span><b>{{ comparison.actualCost }}</b
              ><small>报工与领用记录</small>
            </div>
            <div>
              <span>偏差</span
              ><b style="color: #c09165"
                >{{ comparison.costDeltaRate > 0 ? "+" : ""
                }}{{ comparison.costDeltaRate }}%</b
              ><small>{{ comparison.costDelta }} 单位</small>
            </div>
          </div>
        </section>
      </div>
      <section class="panel">
        <div class="panel-header">
          <h3>差异原因与改进</h3>
          <span class="subtle-label">判断依据需由业务人员确认</span>
        </div>
        <div class="panel-body">
          <label class="field"
            ><span>确认的偏差原因</span><textarea v-model="cause" />
          </label>
          <div class="grid-2" style="margin-top: 20px">
            <div class="list-card">
              <div class="resource-icon"><Icon name="ClipboardCheck" /></div>
              <div>
                <b>预案P2：增加检查与提前备件</b>
                <p>
                  新增10分钟装配前检查，将到货改为T+120；再演练验证工期变化。
                </p>
              </div>
            </div>
            <div class="list-card">
              <div class="resource-icon"><Icon name="BookOpenCheck" /></div>
              <div>
                <b>课程修订：完善部件识别说明</b>
                <p>修改R03说明并发布新版本，保留旧版培训记录与得分。</p>
              </div>
            </div>
          </div>
          <div class="button-row" style="margin-top: 15px">
            <button class="btn primary" @click="improve">生成P2改进草稿</button
            ><button
              class="btn secondary"
              @click="router.push(pathFor('S202'))"
            >
              前往课程修订</button
            ><button
              v-if="comparison"
              class="btn secondary"
              @click="download('comparisons', comparison.id)"
            >
              导出对比报告
            </button>
          </div>
          <p class="note-caption">
            新的检查方案属于待验证改进，不能据此宣布真实质量已经提高。历史预案、演练和实绩不会被修改。
          </p>
        </div>
      </section></template
    ></template
  >
</template>

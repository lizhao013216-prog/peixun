import { reactive, watch } from "vue";
import { store } from "./store";
import { pathFor } from "./catalog";

export type GuideStep = {
  title: string;
  page: string;
  role: string;
  actions: string[];
  result: string;
  inspect: string;
  target?: string;
};
const step = (
  title: string,
  page: string,
  role: string,
  actions: string[],
  result: string,
  inspect: string,
  target?: string,
): GuideStep => ({ title, page, role, actions, result, inspect, target });
export const tours = [
  {
    id: "operation",
    number: "01",
    title: "装备操作训练",
    icon: "Orbit",
    color: "blue",
    duration: "约 5–8 分钟讲解",
    summary: "从资源与设备组网，到课程发布和学员操作。",
    takeaway: "展示课程如何制作、审核，以及每一步操作如何留痕。",
    steps: [
      step(
        "认识共用资源",
        "C02",
        "AUTHOR",
        [
          "查看资源分类，选择泵组模型。",
          "需要演示入库时，点击上传按钮选择本地文件。",
        ],
        "资源有分类、版本和处理状态，可被课程引用。",
        "介绍模型、场景、工具和文档资源，说明三系统共用同一套资源。",
      ),
      step(
        "配置场景",
        "C03",
        "AUTHOR",
        [
          "在场景中选择泵组或阀门，修改环境、材质等参数。",
          "点击“保存场景”，拖动关键帧滑块观察阀门示意。",
        ],
        "配置被保存，后续课程引用场景版本。",
        "选择对象、调整视角，说明场景表现使用假设虚拟平台能力。",
      ),
      step(
        "演示四设备联动",
        "S102",
        "AUTHOR",
        [
          "勾选“阀门已就绪”，点击“启动设备”。",
          "将传感器值调到 0.2，观察告警；再点击“停止设备”和“确认复位”。",
        ],
        "设备从就绪到运行、告警，再恢复停止状态。",
        "现场演示阀门条件、启动信号和低值告警，讲清设备间的业务关系。",
      ),
      step(
        "编排操作课程",
        "S104",
        "AUTHOR",
        [
          "点击“新建操作课程”，填写名称并“创建草稿”。",
          "查看 8 个步骤，点击“提交审核”。",
        ],
        "课程进入“待审核”，制作和审核由不同身份完成。",
        "查看操作课程的 8 个步骤，解释顺序和对象校验。",
      ),
      step(
        "教员审核",
        "C04",
        "INSTRUCTOR",
        ["找到刚提交的操作课程，点击“审核通过”。"],
        "课程变为“已批准”，允许制作员发布。",
        "介绍制作、审核、发布的职责分工；已发布课程不会出现在待办列表。",
      ),
      step(
        "发布可运行课程",
        "C04",
        "AUTHOR",
        [
          "点击课程行的“发布课程”，等待构建完成。",
          "如果构建失败，先在演示管理恢复正常响应，再重试。",
        ],
        "发布任务成功，课程状态变为“已发布”。",
        "查看构建记录及课程版本，说明平台构建是模拟能力。",
      ),
      step(
        "给学员分配任务",
        "S203",
        "INSTRUCTOR",
        ["在下方“已发布课程”中找到操作课程。", "点击对应课程的“分配学员A”。"],
        "上方任务表新增一条操作训练任务。",
        "查看操作课程对应的任务、学员及完成状态。",
      ),
      step(
        "进入个人训练",
        "S203",
        "LEARNER_A",
        [
          "在操作课程任务行点击“开始训练”；已完成任务可点“新建补训”。",
          "选择“提示操作”和“个人”计分范围，点击弹窗“开始训练”。",
        ],
        "进入该课程的训练页面，产生一次独立训练记录。",
        "点击操作课程任务的“查看尝试”，查看已有训练结果。",
      ),
      step(
        "完成 8 步操作",
        "C05",
        "LEARNER_A",
        [
          "操作对象选择 PUMP-01，按顺序点击“执行当前步骤”，共 8 步。",
          "完成后点击“导出训练报告”。",
        ],
        "全部正确且未求助时得 100 分，过程记录和报告保存。",
        "讲解 100 分成绩、8 步记录与报告，说明错误和帮助会影响评分。",
      ),
    ],
  },
  {
    id: "maintenance",
    number: "02",
    title: "维修技能培训",
    icon: "GraduationCap",
    color: "teal",
    duration: "约 5–8 分钟讲解",
    summary: "培训需求进入课程，训练成绩回流保障准备。",
    takeaway: "展示培训如何服务任务，并形成可追溯的开工证据。",
    steps: [
      step(
        "确认培训需求",
        "S307",
        "PLANNER",
        [
          "选择已有 P0 预案，点击“生成培训需求”。",
          "如没有预案，先按系统三完成任务和预案编制；也可从下一步独立制作课件。",
        ],
        "生成与预案关联的培训需求，供课件制作时选择。",
        "查看培训需求及其完成状态，说明它来自保障准备缺口。",
        "P0",
      ),
      step(
        "制作维修课件",
        "S202",
        "AUTHOR",
        [
          "点击“新建维修课件”，选择上一步的培训需求。",
          "创建草稿，查看 10 个步骤并“提交审核”。",
        ],
        "课件与培训需求关联，形成后续证据回流链。",
        "查看维修课件的 10 个步骤及需求来源。",
      ),
      step(
        "审核维修课件",
        "C04",
        "INSTRUCTOR",
        ["找到维修课件，点击“审核通过”。"],
        "课件已批准。",
        "说明教员审核维修步骤，审核身份与制作身份分离。",
      ),
      step(
        "发布维修课件",
        "C04",
        "AUTHOR",
        ["点击“发布课程”，等待构建成功。"],
        "维修课件可以分配训练。",
        "查看发布任务和课程版本；构建使用平台模拟器。",
      ),
      step(
        "分配维修任务",
        "S203",
        "INSTRUCTOR",
        ["在“已发布课程”找到维修课件，点击“分配学员A”。"],
        "生成维修培训任务。",
        "查看维修培训任务、关联学员和证据确认状态。",
      ),
      step(
        "开始维修训练",
        "S203",
        "LEARNER_A",
        [
          "找到维修任务，点击“开始训练”或“新建补训”。",
          "选择“提示操作”和“个人”计分范围，开始训练。",
        ],
        "创建独立的个人维修训练记录。",
        "点击维修任务“查看尝试”，查看已有的 88 分案例。",
      ),
      step(
        "演示错误与帮助计分",
        "C05",
        "LEARNER_A",
        [
          "选择 VALVE-01，点击“执行当前步骤”两次，再点击“请求帮助”一次。",
          "改选 PUMP-01，完成全部 10 步；最后可“填写反馈”。",
        ],
        "样例结果为 88 分、2 次错误、1 次帮助，正确率 83.33%。",
        "核对 88 分、错误与帮助记录，展示反馈和训练报告。",
      ),
      step(
        "教员确认培训证据",
        "S204",
        "INSTRUCTOR",
        ["找到本次已完成的个人维修训练，点击“确认证据”。"],
        "个人训练成为当前保障任务的培训完成证据。",
        "查看已确认的训练证据；团队成绩不能替代个人证据。",
      ),
      step(
        "查看准备状态回流",
        "S307",
        "PLANNER",
        ["返回关联的 P0 预案，查看培训需求的完成状态。"],
        "关联培训需求已完成，为后续施工准备提供依据。",
        "指出培训证据已回填，串起“需求→课程→训练→开工准备”。",
        "P0",
      ),
    ],
  },
  {
    id: "support",
    number: "03",
    title: "保障筹划与演练",
    icon: "Network",
    color: "amber",
    duration: "约 10–15 分钟讲解",
    summary: "从任务和预案，到演练、施工、改进与归档复用。",
    takeaway: "展示正常、延迟、加急的差异，以及预计与实绩的闭环。",
    steps: [
      step(
        "建立保障任务",
        "S301",
        "PLANNER",
        [
          "点击“新建保障任务”，保留泵组样例并创建。",
          "查看任务范围和工序分解。",
        ],
        "产生任务，后续预案引用该任务。",
        "介绍泵组维修任务、范围、截止时间和工序分解。",
      ),
      step(
        "查看履历和资源",
        "S302",
        "PLANNER",
        [
          "点击“模拟PLSE同步”，查看校验结果。",
          "在左侧“保障资源”查看人员、备件、工具和设施，再返回指引。",
        ],
        "履历首次导入为 8 条有效、1 条重复、1 条缺主键。",
        "展示履历、资料和分析结论，说明筹划输入来源。",
      ),
      step(
        "编制 P0 预案",
        "S305",
        "PLANNER",
        [
          "选择当前任务及泵组维修模板，创建预案。",
          "在编制页确认到货 90、运输 60、8 道工序，保存后提交审核。",
        ],
        "P0 预计工期 240 分钟，总费用 965。",
        "介绍模板匹配与复用；进入预案编制可查看工序和排程。",
        "P0",
      ),
      step(
        "批准 P0",
        "S306",
        "REVIEWER_L1",
        ["选择 P0，点击“审核通过”。"],
        "P0 已批准，输入和资源快照冻结。",
        "查看 P0 的 240 分钟排程，解释资源占用与费用构成。",
        "P0",
      ),
      step(
        "完成准备和培训",
        "S307",
        "PLANNER",
        [
          "生成培训需求，按系统二完成关联课件、个人训练和证据确认。",
          "需要跨系统操作时，可暂时收起指引；完成后回到此处。",
        ],
        "培训需求已完成；复制 P1 前确认这一条件。",
        "介绍六类准备、培训证据，以及采购/调拨单据的演示边界。",
        "P0",
      ),
      step(
        "运行正常情景",
        "S311",
        "DIRECTOR",
        [
          "新建演练，选择已批准 P0 和正常情景。",
          "点击“运行至结束”，查看甘特图与物料账本。",
        ],
        "工期 240、等待 0、费用 965。",
        "查看正常情景；点击“过程复盘”拖动时间轴，说明账本随时间变化。",
        "BASE",
      ),
      step(
        "比较延迟与加急",
        "S311",
        "DIRECTOR",
        [
          "点击“创建延迟情景”，选择 P0；用“下一事件”推进到 T+60。",
          "点击“从T+60采纳加急”，完成分支演练；再选原延迟演练运行至结束。",
        ],
        "延迟工期 300、费用 965；加急工期 255、费用 1005。两个账本独立。",
        "在演练列表切换延迟和加急案例，对比工期、等待与费用。",
        "DELAY",
      ),
      step(
        "评估指标",
        "S312",
        "PLANNER",
        [
          "保留默认 AHP 矩阵及分值，点击计算按钮。",
          "查看权重、一致性和评价结果。",
        ],
        "默认 AHP 权重 0.5/0.3/0.2，得分 87。",
        "展示 AHP 输入、权重和结果，说明计算口径。",
      ),
      step(
        "分析到货敏感性",
        "S313",
        "PLANNER",
        ["选择 P0，计算五组到货时间的影响。"],
        "工期依次为 240/240/255/270/300，各点关联独立演练记录。",
        "查看到货时间变化和工期关系，说明提前备件的收益。",
        "P0",
      ),
      step(
        "形成施工版本 P1",
        "S306",
        "PLANNER",
        [
          "从已批准且培训完成的 P0 创建 P1。",
          "确认到货 135、运输 100，提交审核；切换一级审核员批准后进入模拟施工。",
        ],
        "P1 预计工期 255、费用 1005。",
        "查看 P1 参数，说明其作为实绩比较的批准基线。",
        "P1",
      ),
      step(
        "下发与报工",
        "S314",
        "WORKER",
        [
          "先切换筹划员，下发 P1 执行单；再切换施工员。",
          "依次点击“签收执行单”“确认开工准备”“提交首批模拟实绩”。",
        ],
        "执行单进入待检查；若准备失败，先补齐培训证据。",
        "查看 P1 的签收、报工、领用记录和施工状态。",
      ),
      step(
        "检查、整改与复查",
        "S314",
        "INSPECTOR",
        [
          "首次检查共 4 项，第 3 项不通过，其余通过。",
          "切施工员提交整改，再切验收员复查第 3 项通过，最后施工员完成交接。",
        ],
        "工期 270、总费用 1070；首次合格率 75%，最终 100%。",
        "查看 4 个检查项、5 条历史记录，以及返工增加的时间和费用。",
      ),
      step(
        "实绩对比与 P2 改进",
        "S315",
        "PLANNER",
        [
          "以 P1 为基线保存对比记录。",
          "创建 P2 改进草稿，确认新增 10 分钟检查、到货 120，提交审批并重新演练。",
        ],
        "P1 工期偏差 5.88%、费用偏差 6.47%；P2 预测工期 250。",
        "对比 P1 预计和施工实绩，查看改进建议及 P2 新版本。",
        "P1",
      ),
      step(
        "三级审核与复用",
        "S316",
        "PLANNER",
        [
          "选择已批准且演练完成的 P2，点击“建立成果包”“提交三级审核”。",
          "按一级、二级、三级依次审核通过，再切回筹划员“发布成果”；可演示退回、补充和重提。",
        ],
        "成果已发布，预案库可复用 P2 模板；历史意见保留。",
        "展示两轮审核和发布记录，回到预案库说明 P2 九工序模板复用。",
        "P2",
      ),
    ],
  },
];

let saved: any = {};
try {
  saved = JSON.parse(localStorage.getItem("peixun.guide.v2") || "{}");
} catch {
  /* Ignore stale browser settings. */
}
export const guide = reactive({
  tour: tours.some((t) => t.id === saved.tour) ? saved.tour : "operation",
  index: Number.isInteger(saved.index) ? Math.max(0, saved.index) : 0,
  active: saved.active === true,
  collapsed: false,
  mode: saved.mode === "present" ? "present" : "practice",
});
watch(guide, (value) =>
  localStorage.setItem("peixun.guide.v2", JSON.stringify(value)),
);
export const currentTour = () =>
  tours.find((t) => t.id === guide.tour) || tours[0];
export const currentStep = () =>
  currentTour().steps[Math.min(guide.index, currentTour().steps.length - 1)];
export function startGuide(tour: string, mode = "practice") {
  guide.tour = tour;
  guide.index = 0;
  guide.mode = mode;
  guide.active = true;
  guide.collapsed = false;
}
export function guidePath(item = currentStep()) {
  const s = store.data;
  if (!s) return pathFor(item.page);
  // Keep manual selections during practice; resolve the relevant case for presentation.
  if (guide.mode !== "present") return pathFor(item.page);
  const domain = guide.tour === "operation" ? "OPERATION" : "MAINTENANCE";
  const course = s.courses
    .filter((c: any) => c.domain === domain && c.status === "PUBLISHED")
    .at(-1);
  let id: string | undefined;
  if (["S104", "S202"].includes(item.page)) {
    id = course?.id;
    store.selectedCourse = id || "";
  }
  if (item.page === "C05") {
    id = s.attempts
      .filter((a: any) => a.courseId === course?.id && a.status === "COMPLETED")
      .at(-1)?.id;
    store.selectedAttempt = id || "";
  }
  if (item.target?.startsWith("P")) {
    id = s.plans.filter((p: any) => p.version === item.target).at(-1)?.id;
    store.selectedPlan = id || "";
  }
  if (item.page === "S311") {
    const p0 = s.plans.filter((p: any) => p.version === "P0").at(-1);
    id = s.runs.find(
      (r: any) =>
        r.planId === p0?.id && r.scenario === item.target && !r.parentId,
    )?.id;
    store.selectedRun = id || "";
  }
  if (item.page === "S314") {
    id = s.executions.at(-1)?.id;
    store.selectedExecution = id || "";
  }
  if (item.page === "S316") {
    id = s.archives.at(-1)?.id;
    store.selectedArchive = id || "";
  }
  return pathFor(item.page, id);
}

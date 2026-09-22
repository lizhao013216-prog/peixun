import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";
import fs from "node:fs";
import path from "node:path";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const require = createRequire(path.join(root, "frontend/package.json"));
const { chromium } = require("playwright");
const base = "http://127.0.0.1:18091";
const output = path.join(root, "docs", "assets");
fs.mkdirSync(output, { recursive: true });
const server = spawn("java", ["-jar", "target/peixun-demo-1.0.0.jar"], {
  cwd: path.join(root, "backend"),
  env: {
    ...process.env,
    PORT: "18091",
    DB_URL: `jdbc:h2:mem:p5-p6-${Date.now()};DB_CLOSE_DELAY=-1`,
  },
  stdio: "ignore",
});
let browser;

async function token(actor) {
  const response = await fetch(`${base}/api/demo/session`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ actorId: actor }),
  });
  assert.equal(response.status, 200);
  return (await response.json()).token;
}
async function state(auth) {
  const response = await fetch(
    `${base}/api/demo/state?workspace=demo&domain=SUPPORT`,
    { headers: { Authorization: `Bearer ${auth}` } },
  );
  const body = await response.json();
  assert.equal(response.status, 200, JSON.stringify(body));
  return body;
}
async function command(auth, action, payload, expectedStatus = 200) {
  const current = await state(auth);
  const response = await fetch(
    `${base}/api/demo/commands?workspace=demo&domain=SUPPORT`,
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${auth}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        action,
        payload,
        commandId: crypto.randomUUID(),
        runEpoch: current.epoch,
        expectedRevision: current.revision,
      }),
    },
  );
  const body = await response.json();
  assert.equal(response.status, expectedStatus, `${action}: ${JSON.stringify(body)}`);
  return body.data || body.error;
}
async function createIssue(auth, attemptId, type, name) {
  return command(auth, "issue.create", {
    id: attemptId,
    type,
    name,
    description: `${name}的验收记录`,
  });
}

try {
  let ready = false;
  for (let i = 0; i < 200; i++) {
    try {
      if ((await fetch(`${base}/api/demo/health`)).ok) {
        ready = true;
        break;
      }
    } catch {}
    await new Promise((resolve) => setTimeout(resolve, 150));
  }
  assert.ok(ready, "P5/P6 test service did not start");
  assert.equal((await fetch(`${base}/api/demo/bootstrap`)).status, 200);

  const admin = await token("ADMIN");
  const instructor = await token("INSTRUCTOR");
  const learnerA = await token("LEARNER_A");
  const learnerB = await token("LEARNER_B");
  const planner = await token("PLANNER");
  const initial = await state(admin);
  const formalPlanCount = initial.plans.length;

  const project = await command(admin, "simulationProject.create", {
    domain: "SUPPORT",
    name: "P5保障教学环境",
    purpose: "保障方案编制与延迟事件处置",
    linkageMode: "NONE",
  });
  await command(admin, "simulationProject.save", {
    id: project.id,
    expectedEditRevision: project.editRevision,
    name: project.name,
    purpose: project.purpose,
    scene: {
      objects: [
        {
          id: "PUMP-01",
          name: "训练泵组",
          assetRef: { id: "ASSET-PUMP", version: 1 },
          x: 38,
          y: 42,
          initialState: { status: "READY" },
        },
      ],
      environment: {
        weather: "晴",
        light: "日间",
        camera: "总览",
        material: "标准",
      },
    },
  });
  const template = await command(admin, "simulationTemplate.publish", {
    projectId: project.id,
    usageInstructions: "P5保障教学环境",
    sharedWith: [],
  });
  let course = await command(admin, "courseware.create", {
    domain: "SUPPORT",
    curriculumName: "保障方案教学课程",
    curriculumObjective: "完成保障方案编制、计算与事件处置",
    unitName: "泵组保障第一课时",
    name: "泵组保障方案编制与延迟事件处置",
    description: "六阶段保障教学训练。",
    templateId: template.id,
    stepCount: 6,
  });
  const points = [10, 20, 20, 20, 20, 10];
  const names = ["理解任务", "配置资源", "编制方案", "计算验证", "事件处置", "提交复盘"];
  course = await command(admin, "courseware.save", {
    id: course.id,
    expectedEditRevision: course.editRevision,
    name: course.name,
    description: course.description,
    mode: "GUIDED",
    steps: course.steps.map((step, index) => ({
      ...step,
      name: names[index],
      description: `完成${names[index]}教学要求。`,
      target: "PUMP-01",
      actionId: "CONFIRM",
      actionLabel: "确认教学阶段",
      parameters: {},
      precondition: null,
      completion: { kind: "ACTION_SUCCEEDED" },
      expectedResult: `${names[index]}已完成。`,
      points: points[index],
      mode: "GUIDED",
    })),
    scoreRule: { total: 100, errorPenalty: 5, helpPenalty: 2 },
  });
  await command(admin, "courseware.submit", { id: course.id });
  await command(instructor, "courseware.approve", { id: course.id });
  await command(admin, "courseware.publish", { id: course.id });

  const assignmentA = await command(instructor, "training.assign", {
    courseId: course.id,
    learnerId: "LEARNER_A",
  });
  const assignmentB = await command(instructor, "training.assign", {
    courseId: course.id,
    learnerId: "LEARNER_B",
  });
  const attemptA = await command(learnerA, "training.start", {
    assignmentId: assignmentA.id,
    mode: "GUIDED",
    scope: "INDIVIDUAL",
  });
  const attemptB = await command(learnerB, "training.start", {
    assignmentId: assignmentB.id,
    mode: "GUIDED",
    scope: "INDIVIDUAL",
  });

  browser = await chromium.launch({
    ...(process.env.CHROMIUM_EXECUTABLE
      ? { executablePath: process.env.CHROMIUM_EXECUTABLE }
      : {}),
    args: ["--no-sandbox", "--disable-dev-shm-usage"],
  });
  const context = await browser.newContext({
    viewport: { width: 1440, height: 1100 },
    locale: "zh-CN",
  });
  await context.addInitScript(() => {
    localStorage.setItem("peixun.workspace", "demo");
    localStorage.setItem("peixun.actor", "LEARNER_A");
  });
  const page = await context.newPage();
  const errors = [];
  page.on("pageerror", (error) => errors.push(error.message));
  page.on("response", (response) => {
    if (response.status() >= 500) errors.push(`${response.status()} ${response.url()}`);
  });
  await page.goto(`${base}/sessions/${attemptA.id}`);
  await page.getByRole("heading", { name: course.name, exact: true }).first().waitFor();
  await page.getByText("保障教学案例副本", { exact: false }).waitFor();
  await page.screenshot({
    path: path.join(output, "p5-support-training-stages.png"),
    fullPage: true,
  });

  await command(learnerA, "training.support.confirm", { id: attemptA.id });
  await command(learnerA, "training.support.resource", {
    id: attemptA.id,
    resourceId: "E1",
    capacity: 2,
    available: true,
  });
  await command(learnerA, "training.support.plan", {
    id: attemptA.id,
    operationId: "T01",
    duration: 30,
  });
  let changed = await command(learnerA, "training.support.calculate", {
    id: attemptA.id,
  });
  assert.equal(changed.supportTraining.result.finish, 240);
  changed = await command(learnerA, "training.support.delay", {
    id: attemptA.id,
    arrivalTime: 180,
  });
  assert.equal(changed.supportTraining.delayedResult.finish, 300);
  changed = await command(learnerA, "training.support.resolve", {
    id: attemptA.id,
    strategy: "EXPEDITE",
    arrivalTime: 135,
    transportCost: 140,
  });
  assert.equal(changed.supportTraining.resolvedResult.finish, 255);
  changed = await command(learnerA, "training.support.submit", {
    id: attemptA.id,
    reflection: "延迟后采用加急方案，将工期由300分钟改善为255分钟，并记录费用变化。",
  });
  assert.equal(changed.status, "COMPLETED");
  assert.equal(changed.evaluation.learningScore, 100);
  assert.equal(changed.evaluation.planMetrics.finish, 255);

  const isolated = await state(learnerB);
  const untouchedB = isolated.attempts.find((item) => item.id === attemptB.id);
  assert.equal(untouchedB.score, 0);
  assert.deepEqual(untouchedB.supportTraining.checkpoints, {});
  assert.equal((await state(admin)).plans.length, formalPlanCount);

  const forbidden = await command(
    learnerA,
    "plan.save",
    { id: initial.plans[0]?.id || "PLAN-001" },
    403,
  );
  assert.ok(forbidden.message);

  await page.reload();
  await page.getByRole("heading", { name: "方案结果与学习成绩分别记录", exact: true }).waitFor();
  await page.getByText("255", { exact: true }).first().waitFor();
  await page.screenshot({
    path: path.join(output, "p5-support-training-completed.png"),
    fullPage: true,
  });

  const content = await createIssue(learnerA, attemptA.id, "CONTENT", "课程步骤说明需要补充");
  const learning = await createIssue(learnerA, attemptA.id, "LEARNING", "需要安排一次补训");
  const platform = await createIssue(learnerA, attemptA.id, "PLATFORM", "训练页面出现短暂卡顿");
  const equipment = await createIssue(learnerA, attemptA.id, "EQUIPMENT_SUPPORT", "泵组存在疑似保障缺陷");
  await command(instructor, "issue.route", { id: content.id });
  await command(instructor, "issue.route", { id: learning.id });
  await command(instructor, "issue.route", {
    id: platform.id,
    opinion: "已登记技术排查，不调整学习成绩。",
  });
  await command(planner, "issue.verify", {
    id: equipment.id,
    opinion: "已核实训练记录与装备现象，符合转任务条件。",
  });
  const routed = await command(planner, "issue.route", {
    id: equipment.id,
    name: "泵组疑似缺陷核实处置",
    object: "演示船A通用泵组",
    scope: "核实并处理训练中发现的泵组保障缺陷",
    taskType: "故障处置",
    dueMinutes: 240,
    constraints: "保留训练、问题和课程来源链",
  });
  const duplicate = await command(planner, "issue.route", {
    id: equipment.id,
    name: "不应重复创建",
    object: "其他对象",
    scope: "其他范围",
    taskType: "其他",
    dueMinutes: 300,
  });
  assert.equal(duplicate.linkedTaskId, routed.linkedTaskId);
  const afterIssues = await state(planner);
  assert.equal(
    afterIssues.tasks.filter((item) => item.sourceIssueId === equipment.id).length,
    1,
  );
  const linkedTask = afterIssues.tasks.find((item) => item.id === routed.linkedTaskId);
  assert.equal(linkedTask.sourceAttemptId, attemptA.id);
  assert.equal(linkedTask.sourceCourseRef.id, course.id);
  assert.equal(
    afterIssues.attempts.find((item) => item.id === attemptA.id).score,
    100,
  );

  await page.evaluate(() => localStorage.setItem("peixun.actor", "PLANNER"));
  await page.goto(`${base}/support/training-issues`);
  await page.getByRole("heading", { name: "训练可以正常完成和归档，发现的问题再按类型分流。", exact: true }).waitFor();
  await page.getByText("泵组存在疑似保障缺陷", { exact: true }).waitFor();
  await page.screenshot({
    path: path.join(output, "p6-training-issue-closure.png"),
    fullPage: true,
  });
  assert.deepEqual(errors, []);
  console.log(
    "PASS: P5 support teaching copy, 240/300/255 scheduling, two-learner isolation, score/plan metric separation, and P6 optional four-way issue routing with verified idempotent task lineage.",
  );
} finally {
  if (browser) await browser.close();
  server.kill("SIGTERM");
}

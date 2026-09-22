import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";
import fs from "node:fs";
import path from "node:path";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const require = createRequire(path.join(root, "frontend/package.json"));
const { chromium } = require("playwright");
const base = "http://127.0.0.1:18090";
const output = path.join(root, "test-results");
fs.mkdirSync(output, { recursive: true });
const server = spawn("java", ["-jar", "target/peixun-demo-1.0.0.jar"], {
  cwd: path.join(root, "backend"),
  env: { ...process.env, PORT: "18090", DB_URL: `jdbc:h2:mem:p4-${Date.now()};DB_CLOSE_DELAY=-1` },
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
  const response = await fetch(`${base}/api/demo/state?workspace=demo&domain=OPERATION`, {
    headers: { Authorization: `Bearer ${auth}` },
  });
  assert.equal(response.status, 200);
  return response.json();
}
async function command(auth, action, payload) {
  const current = await state(auth);
  const response = await fetch(`${base}/api/demo/commands?workspace=demo&domain=OPERATION`, {
    method: "POST",
    headers: { Authorization: `Bearer ${auth}`, "Content-Type": "application/json" },
    body: JSON.stringify({ action, payload, commandId: crypto.randomUUID(), runEpoch: current.epoch, expectedRevision: current.revision }),
  });
  const body = await response.json();
  assert.equal(response.status, 200, `${action}: ${JSON.stringify(body)}`);
  return body.data;
}

try {
  let ready = false;
  for (let i = 0; i < 200; i++) {
    try { if ((await fetch(`${base}/api/demo/health`)).ok) { ready = true; break; } } catch {}
    await new Promise((resolve) => setTimeout(resolve, 150));
  }
  assert.ok(ready, "P4 test service did not start");
  assert.equal((await fetch(`${base}/api/demo/bootstrap`)).status, 200);
  const admin = await token("ADMIN");
  const project = await command(admin, "simulationProject.create", {
    domain: "OPERATION", name: "P4训练环境", purpose: "配置驱动训练验收", linkageMode: "NONE",
  });
  await command(admin, "simulationProject.save", {
    id: project.id,
    expectedEditRevision: project.editRevision,
    name: "P4训练环境",
    purpose: "配置驱动训练验收",
    scene: {
      objects: [{ id: "TOOL-01", name: "检查工具", assetRef: { id: "ASSET-TOOL", version: 1 }, x: 35, y: 42, view: "设备", initialState: { status: "READY" } }],
      environment: { weather: "晴", light: "日间", camera: "总览", material: "标准" },
    },
  });
  const template = await command(admin, "simulationTemplate.publish", {
    projectId: project.id, usageInstructions: "P4训练运行环境", sharedWith: [],
  });
  let course = await command(admin, "courseware.create", {
    domain: "OPERATION",
    curriculumName: "P4配置驱动课程",
    curriculumObjective: "按冻结发布快照完成对象确认",
    unitName: "配置训练第一课时",
    name: "P4配置驱动课件",
    description: "验证课件发布快照、训练步骤、评分和证据闭环。",
    templateId: template.id,
    stepCount: 1,
  });
  const step = {
    ...course.steps[0],
    name: "确认检查工具",
    description: "选择检查工具并执行确认动作。",
    target: "TOOL-01",
    actionId: "CONFIRM",
    actionLabel: "确认当前对象",
    parameters: {},
    precondition: null,
    completion: { kind: "ACTION_SUCCEEDED" },
    expectedResult: "检查工具状态已确认。",
    points: 100,
    mode: "GUIDED",
  };
  course = await command(admin, "courseware.save", {
    id: course.id,
    expectedEditRevision: course.editRevision,
    name: course.name,
    description: course.description,
    mode: "GUIDED",
    steps: [step],
    scoreRule: { total: 100, errorPenalty: 5, helpPenalty: 2 },
  });
  await command(admin, "courseware.submit", { id: course.id });
  const instructor = await token("INSTRUCTOR");
  await command(instructor, "courseware.approve", { id: course.id });
  await command(admin, "courseware.publish", { id: course.id });
  const assignment = await command(instructor, "training.assign", { courseId: course.id, learnerId: "LEARNER_A" });
  const learner = await token("LEARNER_A");
  const attempt = await command(learner, "training.start", { assignmentId: assignment.id, mode: "GUIDED", scope: "INDIVIDUAL" });

  browser = await chromium.launch({
    ...(process.env.CHROMIUM_EXECUTABLE ? { executablePath: process.env.CHROMIUM_EXECUTABLE } : {}),
    args: ["--no-sandbox", "--disable-dev-shm-usage"],
  });
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, locale: "zh-CN" });
  await context.addInitScript(() => {
    localStorage.setItem("peixun.workspace", "demo");
    localStorage.setItem("peixun.actor", "LEARNER_A");
  });
  const page = await context.newPage();
  const errors = [];
  page.on("pageerror", (error) => errors.push(error.message));
  page.on("response", (response) => { if (response.status() >= 500) errors.push(`${response.status()} ${response.url()}`); });
  await page.goto(`${base}/sessions/${attempt.id}`);
  await page.getByRole("heading", { name: "P4配置驱动课件", exact: true }).waitFor();
  await page.getByText("第 1 / 1 步", { exact: true }).waitFor();
  await page.locator(".object-picker").getByRole("button", { name: /检查工具/ }).click();
  await page.getByRole("button", { name: "确认当前对象", exact: true }).click();
  await page.getByText("操作成功 · 本步已通过", { exact: true }).waitFor();
  await page.getByRole("button", { name: "完成训练", exact: true }).waitFor();
  await page.screenshot({ path: path.join(output, "p4-training-step-passed.png"), fullPage: true });
  await page.getByRole("button", { name: "完成训练", exact: true }).click();
  await page.getByRole("heading", { name: "全部步骤已完成，接下来查看成绩与培训证据。", exact: true }).waitFor();
  await page.getByText("100", { exact: true }).first().waitFor();
  await page.screenshot({ path: path.join(output, "p4-training-completed.png"), fullPage: true });

  const finalState = await state(learner);
  const completed = finalState.attempts.find((item) => item.id === attempt.id);
  assert.equal(completed.status, "COMPLETED");
  assert.equal(completed.score, 100);
  assert.equal(completed.events.length, 1);
  assert.equal(completed.events[0].result, "PASSED");
  await command(instructor, "training.confirm", { id: attempt.id });
  const instructorState = await state(instructor);
  const archive = instructorState.trainingArchives.find((item) => item.attemptId === attempt.id);
  assert.equal(archive.courseRef.id, course.id);
  assert.equal(archive.assignmentId, assignment.id);
  assert.equal(archive.score, 100);
  const learnerB = await token("LEARNER_B");
  const forbiddenReport = await fetch(`${base}/api/demo/reports/attempts/${attempt.id}.md?workspace=demo`, {
    headers: { Authorization: `Bearer ${learnerB}` },
  });
  assert.notEqual(forbiddenReport.status, 200, "another learner must not download this attempt by direct URL");
  assert.deepEqual(errors, []);
  console.log("PASS: P4 published snapshot assignment, data-driven object/action UI, explicit learner continue, scoring, event evidence, confirmation archive and cross-learner direct-link denial.");
} finally {
  if (browser) await browser.close();
  server.kill("SIGTERM");
}

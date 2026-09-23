import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";
import fs from "node:fs";
import path from "node:path";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const require = createRequire(path.join(root, "frontend/package.json"));
const { chromium } = require("playwright");
const base = "http://127.0.0.1:18089";
const output = path.join(root, "test-results");
const evidenceOutput = path.join(root, "docs", "assets");
fs.mkdirSync(output, { recursive: true });
fs.mkdirSync(evidenceOutput, { recursive: true });
const server = spawn("java", ["-jar", "target/peixun-demo-1.0.0.jar"], {
  cwd: path.join(root, "backend"),
  env: { ...process.env, PORT: "18089", DB_URL: `jdbc:h2:mem:p3-${Date.now()};DB_CLOSE_DELAY=-1` },
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
async function rejectedCommand(auth, action, payload) {
  const current = await state(auth);
  const response = await fetch(`${base}/api/demo/commands?workspace=demo&domain=OPERATION`, {
    method: "POST",
    headers: { Authorization: `Bearer ${auth}`, "Content-Type": "application/json" },
    body: JSON.stringify({ action, payload, commandId: crypto.randomUUID(), runEpoch: current.epoch, expectedRevision: current.revision }),
  });
  return { status: response.status, body: await response.json() };
}
async function publishTemplate(auth, name, assetId, objectId) {
  const project = await command(auth, "simulationProject.create", {
    domain: "OPERATION", name, purpose: "P3课件环境验收", linkageMode: "NONE",
  });
  await command(auth, "simulationProject.save", {
    id: project.id,
    expectedEditRevision: project.editRevision,
    name,
    purpose: "P3课件环境验收",
    scene: {
      objects: [{ id: objectId, name: `${name}对象`, assetRef: { id: assetId, version: 1 }, x: 32, y: 42, view: "设备", initialState: { status: "READY" } }],
      environment: { weather: "晴", light: "日间", camera: "总览", material: "标准" },
    },
  });
  return command(auth, "simulationTemplate.publish", {
    projectId: project.id, usageInstructions: `${name}使用说明`, sharedWith: [],
  });
}

try {
  let ready = false;
  for (let i = 0; i < 200; i++) {
    try { if ((await fetch(`${base}/api/demo/health`)).ok) { ready = true; break; } } catch {}
    await new Promise((resolve) => setTimeout(resolve, 150));
  }
  assert.ok(ready, "P3 test service did not start");
  assert.equal((await fetch(`${base}/api/demo/bootstrap`)).status, 200);
  const admin = await token("ADMIN");
  const toolTemplate = await publishTemplate(admin, "P3部件识别环境", "ASSET-TOOL", "TOOL-01");
  const pumpTemplate = await publishTemplate(admin, "P3泵组操作环境", "ASSET-PUMP", "PUMP-01");

  browser = await chromium.launch({
    ...(process.env.CHROMIUM_EXECUTABLE ? { executablePath: process.env.CHROMIUM_EXECUTABLE } : {}),
    args: ["--no-sandbox", "--disable-dev-shm-usage"],
  });
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, locale: "zh-CN" });
  await context.addInitScript(() => {
    localStorage.setItem("peixun.workspace", "demo");
    localStorage.setItem("peixun.actor", "AUTHOR");
  });
  const page = await context.newPage();
  const errors = [];
  page.on("pageerror", (error) => errors.push(error.message));
  page.on("response", (response) => { if (response.status() >= 500) errors.push(`${response.status()} ${response.url()}`); });
  page.on("dialog", (dialog) => dialog.accept());
  const button = (name) => page.getByRole("button", { name, exact: true });

  await page.goto(`${base}/operation/coursewares/current`);
  await button("新建操作课件").click();
  const createDialog = page.getByRole("dialog");
  await createDialog.getByRole("textbox", { name: "课程名称 *", exact: true }).fill("P3五步部件检查课件");
  await createDialog.getByRole("textbox", { name: "课程组织名称 *", exact: true }).fill("P3部件检查课程");
  await createDialog.getByRole("textbox", { name: "应掌握的能力 *", exact: true }).fill("能够识别目标部件并完成五步规范检查");
  await createDialog.getByRole("textbox", { name: "课时名称 *", exact: true }).fill("部件检查第一课时");
  await createDialog.getByRole("combobox", { name: "仿真环境模板 *", exact: true }).selectOption(toolTemplate.id);
  await createDialog.getByRole("button", { name: "创建草稿", exact: true }).click();
  await page.getByText("交互契约 V3", { exact: true }).waitFor();
  await page.getByText("P3部件检查课程", { exact: true }).first().waitFor();
  await page.getByText("部件检查第一课时", { exact: true }).first().waitFor();
  await page.getByText(`${toolTemplate.sceneSnapshot.id} · V${toolTemplate.sceneSnapshot.version}`, { exact: true }).waitFor();
  await page.getByText("ASSET-TOOL · V1", { exact: true }).waitFor();
  let nav = page.getByRole("navigation", { name: "教学步骤" }).getByRole("button");
  assert.equal(await nav.count(), 5, "new V3 courseware must start with five stable steps");
  assert.equal(await button("预览学员视角").isDisabled(), true, "incomplete draft must not preview");
  await button("保存课件").click();
  await page.getByRole("status").filter({ hasText: "课程内容已保存" }).waitFor();

  for (let index = 0; index < 5; index++) {
    nav = page.getByRole("navigation", { name: "教学步骤" }).getByRole("button");
    await nav.nth(index).click();
    await page.getByRole("textbox", { name: "学员应该怎么操作", exact: true }).fill(`第${index + 1}步选择模板中的工具对象并确认状态。`);
    await page.getByRole("combobox", { name: "正确的操作对象", exact: true }).selectOption("TOOL-01");
    await page.getByRole("combobox", { name: "实际设备动作", exact: true }).selectOption("CONFIRM");
    await page.getByRole("textbox", { name: "操作成功后显示什么结果", exact: true }).fill(`第${index + 1}步规则执行成功。`);
  }
  await nav.first().click();
  await button("添加条件").click();
  await button("复制步骤").click();
  assert.equal(await page.getByRole("navigation", { name: "教学步骤" }).getByRole("button").count(), 6);
  await button("删除步骤").click();
  assert.equal(await page.getByRole("navigation", { name: "教学步骤" }).getByRole("button").count(), 5);
  nav = page.getByRole("navigation", { name: "教学步骤" }).getByRole("button");
  const firstName = await nav.first().innerText();
  await nav.first().dragTo(nav.nth(1));
  assert.notEqual(await nav.first().innerText(), firstName, "drag sorting must change step order");
  await nav.nth(1).click();
  await button("上移步骤").click();
  await button("保存课件").click();
  await page.getByRole("status").filter({ hasText: "课程内容已保存" }).waitFor();

  let current = await state(admin);
  let courseware = current.courses.find((item) => item.name === "P3五步部件检查课件");
  const environmentBefore = structuredClone(courseware.environment);
  await page.getByRole("textbox", { name: "学完这门课要掌握什么", exact: true }).fill("只修改教学说明，环境引用必须保持不变。");
  await page.getByRole("spinbutton", { name: "每次错误扣分", exact: true }).fill("4");
  await button("保存课件").click();
  current = await state(admin);
  courseware = current.courses.find((item) => item.id === courseware.id);
  assert.deepEqual(courseware.environment, environmentBefore, "ordinary save must not replace environment binding");

  await button("预览学员视角").click();
  const preview = page.getByRole("dialog");
  await preview.locator(".object-picker").getByRole("button", { name: /TOOL-01/ }).click();
  await preview.getByRole("button", { name: "确认对象", exact: true }).click();
  await preview.getByText("预览操作通过", { exact: true }).waitFor();
  assert.ok((await preview.locator(".training-feedback").innerText()).includes("确定性执行"));
  const previewEvidence = path.join(output, "p3-courseware-preview.png");
  await page.screenshot({ path: previewEvidence, fullPage: true });
  fs.copyFileSync(previewEvidence, path.join(evidenceOutput, "p3-courseware-preview.png"));
  await preview.getByRole("button", { name: "返回课件制作", exact: true }).click();

  await button("提交审核").click();
  await button("切换独立审核教员").waitFor();
  assert.equal(await button("审核通过").count(), 0, "creator must not self-review");
  await button("切换独立审核教员").click();
  await button("审核通过").click();
  await button("发布课程").click();
  await page.getByText("课件已发布，下一步分配培训任务", { exact: true }).waitFor();
  const publishedEvidence = path.join(output, "p3-courseware-published.png");
  await page.screenshot({ path: publishedEvidence, fullPage: true });
  fs.copyFileSync(publishedEvidence, path.join(evidenceOutput, "p3-courseware-published.png"));

  current = await state(admin);
  const published = current.courses.find((item) => item.id === courseware.id);
  const frozenV1 = structuredClone(published.publishedSnapshot);
  const instructor = await token("INSTRUCTOR");
  const assignment = await command(instructor, "training.assign", { courseId: published.id, learnerId: "LEARNER_A" });
  assert.equal(assignment.courseSnapshot.interactionSchemaVersion, 3);
  assert.deepEqual(assignment.courseSnapshot, { ...frozenV1, id: published.id, domain: published.domain, version: published.version });

  await button("创建修订版").click();
  await page.locator(".panel-header .pill").filter({ hasText: "V2" }).first().waitFor();
  await page.getByRole("combobox", { name: "显式更换模板版本", exact: true }).selectOption(pumpTemplate.id);
  await button("检查影响并改绑").click();
  await page.getByText(/TOOL-01。系统不会自动替换/).waitFor();
  assert.equal(await button("提交审核").isDisabled(), true, "invalid steps after rebind must block submit");
  current = await state(admin);
  const revision = current.courses.find((item) => item.parentId === published.id);
  assert.equal(revision.steps[0].target, "TOOL-01", "rebind must not silently replace step targets");
  assert.deepEqual(current.courses.find((item) => item.id === published.id).publishedSnapshot, frozenV1, "V2 draft must not mutate V1 snapshot");
  const rebindEvidence = path.join(output, "p3-courseware-rebind-impact.png");
  await page.screenshot({ path: rebindEvidence, fullPage: true });
  fs.copyFileSync(rebindEvidence, path.join(evidenceOutput, "p3-courseware-rebind-impact.png"));

  assert.deepEqual(errors, []);
  console.log("PASS: P3 curriculum/unit, five-step authoring, explicit template binding, dynamic actions, draft gates, shared-rule preview, independent review, immutable revision and V2 compatibility boundary.");
} finally {
  if (browser) await browser.close();
  server.kill("SIGTERM");
}

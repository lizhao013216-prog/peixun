import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";
import fs from "node:fs";
import path from "node:path";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const require = createRequire(path.join(root, "frontend/package.json"));
const { chromium } = require("playwright");
const base = "http://127.0.0.1:18087";
const output = path.join(root, "test-results");
fs.mkdirSync(output, { recursive: true });
const server = spawn("java", ["-jar", "target/peixun-demo-1.0.0.jar"], {
  cwd: path.join(root, "backend"),
  env: { ...process.env, PORT: "18087", DB_URL: `jdbc:h2:mem:p2c-${Date.now()};DB_CLOSE_DELAY=-1` },
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
async function state(auth, domain = "OPERATION") {
  const response = await fetch(`${base}/api/demo/state?workspace=demo&domain=${domain}`, { headers: { Authorization: `Bearer ${auth}` } });
  assert.equal(response.status, 200);
  return response.json();
}
async function command(auth, domain, action, payload, commandId = crypto.randomUUID()) {
  const current = await state(auth, domain);
  const response = await fetch(`${base}/api/demo/commands?workspace=demo&domain=${domain}`, {
    method: "POST",
    headers: { Authorization: `Bearer ${auth}`, "Content-Type": "application/json" },
    body: JSON.stringify({ action, payload, commandId, runEpoch: current.epoch, expectedRevision: current.revision }),
  });
  assert.equal(response.status, 200, `${action}: ${await response.clone().text()}`);
  return response.json();
}
const object = (id, assetId, x, y) => ({ id, name: id, assetRef: { id: assetId, version: 1 }, x, y, view: "设备", initialState: { status: "READY" } });

try {
  let ready = false;
  for (let i = 0; i < 200; i++) {
    try { if ((await fetch(`${base}/api/demo/health`)).ok) { ready = true; break; } } catch {}
    await new Promise((resolve) => setTimeout(resolve, 150));
  }
  assert.ok(ready, "P2-C test service did not start");
  assert.equal((await fetch(`${base}/api/demo/bootstrap`)).status, 200);
  const admin = await token("ADMIN");
  const project = (await command(admin, "OPERATION", "simulationProject.create", {
    domain: "OPERATION", name: "P2C泵组模板工程", purpose: "不可变模板发布与修订验收", linkageMode: "SIGNAL_GRAPH",
  })).data;
  await command(admin, "OPERATION", "simulationProject.save", {
    id: project.id, expectedEditRevision: project.editRevision, name: project.name, purpose: project.purpose,
    scene: {
      objects: [object("PUMP-01", "ASSET-PUMP", 25, 55), object("VALVE-01", "ASSET-VALVE", 48, 28), object("SENSOR-01", "ASSET-SENSOR", 73, 28)],
      environment: { weather: "晴", light: "日间", camera: "总览", material: "标准" },
    },
  });
  let current = await state(admin);
  const savedProject = current.simulationProjects.find((item) => item.id === project.id);
  const topology = current.topologies.find((item) => item.id === savedProject.topologyRef.id);
  await command(admin, "OPERATION", "simulationTopology.save", {
    projectId: project.id,
    expectedEditRevision: topology.editRevision,
    connections: [
      { id: "LINK-READY", source: { objectId: "VALVE-01", port: "ready" }, target: { objectId: "PUMP-01", port: "readyInput" }, valueType: "BOOLEAN" },
      { id: "LINK-SENSOR", source: { objectId: "SENSOR-01", port: "value" }, target: { objectId: "PUMP-01", port: "sensorInput" }, valueType: "NUMBER" },
    ],
    rules: [
      { id: "RULE-START", kind: "PRECONDITION", trigger: { objectId: "PUMP-01", actionId: "START" }, conditions: [{ objectId: "PUMP-01", field: "readyInput", operator: "EQ", value: true }], effects: [{ objectId: "PUMP-01", field: "status", value: "RUNNING" }], rejectMessage: "阀门未就绪，当前不能启动" },
      { id: "RULE-LOW-VALUE", kind: "CONDITIONAL", trigger: { objectId: "SENSOR-01", actionId: "SET_VALUE" }, conditions: [{ objectId: "PUMP-01", field: "sensorInput", operator: "LT", value: 0.4 }], effects: [{ objectId: "PUMP-01", field: "status", value: "ALARM" }], rejectMessage: "" },
    ],
  });

  browser = await chromium.launch({
    ...(process.env.CHROMIUM_EXECUTABLE ? { executablePath: process.env.CHROMIUM_EXECUTABLE } : {}),
    args: ["--no-sandbox", "--disable-dev-shm-usage"],
  });
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, locale: "zh-CN" });
  await context.addInitScript(() => {
    localStorage.setItem("peixun.workspace", "demo");
    localStorage.setItem("peixun.actor", "ADMIN");
  });
  const page = await context.newPage();
  const errors = [];
  page.on("pageerror", (error) => errors.push(error.message));
  page.on("response", (response) => { if (response.status() >= 500) errors.push(`${response.status()} ${response.url()}`); });
  await page.goto(`${base}/operation/simulation-projects/${project.id}`);
  await page.getByRole("button", { name: "发布为模板", exact: true }).click();
  await page.getByLabel("模板使用说明 *").fill("用于演示泵组启停、低值告警和独立调试");
  await page.locator(".check-row").filter({ hasText: "装备维修保障" }).locator('input[type="checkbox"]').check();
  await page.getByRole("button", { name: "检查配置", exact: true }).click();
  await page.getByText("配置检查通过，可以发布为不可变模板版本", { exact: true }).waitFor();
  await page.getByRole("button", { name: "确认发布", exact: true }).click();
  await page.getByText("P2C泵组模板工程", { exact: true }).first().waitFor();
  await page.locator(".project-card").filter({ hasText: "P2C泵组模板工程" }).getByRole("button", { name: "查看版本详情" }).click();
  await page.getByText("RULE-START", { exact: true }).waitFor();
  await page.getByText("ASSET-PUMP@V1", { exact: true }).waitFor();
  await page.screenshot({ path: path.join(output, "p2c-template-v1.png"), fullPage: true });

  await page.getByRole("button", { name: "调试已发布版本", exact: true }).click();
  await page.getByRole("button", { name: "新建实例", exact: true }).click();
  await page.getByRole("status").filter({ hasText: "已按已发布版本创建只读调试实例" }).waitFor();
  assert.ok((await page.locator(".preview-controls").innerText()).includes("不可变快照"));
  await page.getByRole("button", { name: "创建修订", exact: true }).click();
  await page.getByRole("status").filter({ hasText: "已创建独立修订草稿" }).waitFor();
  await page.getByLabel("对象名称").first().fill("V2泵组对象");
  await page.getByRole("button", { name: "保存工程", exact: true }).click();
  await page.getByRole("status").filter({ hasText: "工程草稿已保存" }).waitFor();
  await page.getByRole("button", { name: "发布为模板", exact: true }).click();
  await page.getByLabel("模板使用说明 *").fill("第二版：调整泵组对象名称，保留第一版快照");
  await page.getByRole("button", { name: "检查配置", exact: true }).click();
  await page.getByText("配置检查通过，可以发布为不可变模板版本", { exact: true }).waitFor();
  await page.getByRole("button", { name: "确认发布", exact: true }).click();
  await page.locator(".project-card").filter({ hasText: "修订草稿" }).getByRole("button", { name: "查看版本详情" }).click();
  await page.locator("button.template-version").filter({ hasText: "V1" }).click();
  assert.ok((await page.locator(".template-detail").innerText()).includes("PUMP-01"));
  assert.ok(!(await page.locator(".template-detail").innerText()).includes("V2泵组对象"));
  await page.locator("button.template-version").filter({ hasText: "V2" }).click();
  await page.getByText("V2泵组对象", { exact: true }).waitFor();
  await page.screenshot({ path: path.join(output, "p2c-template-versions.png"), fullPage: true });

  current = await state(admin);
  const versions = current.systemTemplates.filter((item) => item.familyId && item.name.includes("P2C泵组模板工程"));
  assert.deepEqual(versions.map((item) => item.version).sort(), [1, 2]);
  assert.equal(versions.find((item) => item.version === 1).sceneSnapshot.objects[0].name, "PUMP-01");
  const maintenance = await state(admin, "MAINTENANCE");
  assert.ok(maintenance.systemTemplates.some((item) => item.id === versions.find((item) => item.version === 1).id));
  assert.equal(current.templates.filter((item) => item.type === "PLAN").length, 2);
  assert.deepEqual(errors, []);
  console.log("PASS: P2-C validation, immutable V1/V2, detail, published-version preview, revision, sharing projection and PLAN compatibility.");
} finally {
  if (browser) await browser.close();
  server.kill("SIGTERM");
}

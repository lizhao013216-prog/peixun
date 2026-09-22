import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";
import fs from "node:fs";
import path from "node:path";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const require = createRequire(path.join(root, "frontend/package.json"));
const { chromium } = require("playwright");
const base = "http://127.0.0.1:18086";
const output = path.join(root, "test-results");
fs.mkdirSync(output, { recursive: true });
const server = spawn("java", ["-jar", "target/peixun-demo-1.0.0.jar"], {
  cwd: path.join(root, "backend"),
  env: { ...process.env, PORT: "18086", DB_URL: `jdbc:h2:mem:p2b-${Date.now()};DB_CLOSE_DELAY=-1` },
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
  const response = await fetch(`${base}/api/demo/state?workspace=demo&domain=OPERATION`, { headers: { Authorization: `Bearer ${auth}` } });
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
  assert.equal(response.status, 200, `${action}: ${await response.clone().text()}`);
  return response.json();
}
const object = (id, assetId, x, y) => ({ id, name: id, assetRef: { id: assetId, version: 1 }, x, y, view: "设备", initialState: { status: "READY" } });
async function waitForText(page, text) {
  for (let i = 0; i < 100; i++) {
    if ((await page.locator("main").innerText()).includes(text)) return;
    await page.waitForTimeout(100);
  }
  assert.fail(`页面未显示：${text}`);
}

try {
  let ready = false;
  for (let i = 0; i < 200; i++) {
    try { if ((await fetch(`${base}/api/demo/health`)).ok) { ready = true; break; } } catch {}
    await new Promise((resolve) => setTimeout(resolve, 150));
  }
  assert.ok(ready, "P2-B test service did not start");
  assert.equal((await fetch(`${base}/api/demo/bootstrap`)).status, 200);
  const admin = await token("ADMIN");
  const project = (await command(admin, "simulationProject.create", { domain: "OPERATION", name: "P2B泵组联动工程", purpose: "规则与调试验收", linkageMode: "SIGNAL_GRAPH" })).data;
  await command(admin, "simulationProject.save", {
    id: project.id,
    expectedEditRevision: project.editRevision,
    name: project.name,
    purpose: project.purpose,
    scene: {
      objects: [
        object("PUMP-01", "ASSET-PUMP", 25, 55),
        object("VALVE-01", "ASSET-VALVE", 48, 28),
        object("SENSOR-01", "ASSET-SENSOR", 73, 28),
      ],
      environment: { weather: "晴", light: "日间", camera: "总览", material: "标准" },
    },
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
  await page.getByRole("button", { name: "设备关系与联动", exact: true }).click();
  await page.getByRole("button", { name: "生成泵组联动示例", exact: true }).click();
  await waitForText(page, "RULE-START");
  await waitForText(page, "RULE-LOW-VALUE");
  await page.getByRole("button", { name: "保存关系与规则", exact: true }).click();
  await page.getByRole("status").filter({ hasText: "设备关系与规则已保存" }).waitFor();
  await page.reload();
  await page.getByRole("button", { name: "设备关系与联动", exact: true }).click();
  await waitForText(page, "RULE-START");
  assert.equal(await page.locator(".relation-row").count(), 2);
  await page.screenshot({ path: path.join(output, "p2b-relations.png"), fullPage: true });

  await page.getByRole("button", { name: "调试预览", exact: true }).click();
  await page.getByRole("button", { name: "新建实例", exact: true }).click();
  await page.getByRole("status").filter({ hasText: "已创建独立调试实例" }).waitFor();
  const objectSelect = page.getByLabel("操作对象");
  const actionSelect = page.getByLabel("有限动作");
  await objectSelect.selectOption("PUMP-01");
  await actionSelect.selectOption("START");
  await page.getByRole("button", { name: "执行动作", exact: true }).click();
  await page.getByText("阀门未就绪，当前不能启动", { exact: true }).waitFor();

  await objectSelect.selectOption("VALVE-01");
  await actionSelect.selectOption("SET_READY");
  await page.getByLabel("布尔参数").selectOption({ label: "是 / 就绪" });
  await page.getByRole("button", { name: "执行动作", exact: true }).click();
  await objectSelect.selectOption("PUMP-01");
  await actionSelect.selectOption("START");
  await page.getByRole("button", { name: "执行动作", exact: true }).click();
  await page.locator(".state-grid article").filter({ hasText: "PUMP-01" }).getByText(/RUNNING/).waitFor();

  await objectSelect.selectOption("SENSOR-01");
  await actionSelect.selectOption("SET_VALUE");
  await page.getByLabel("数值参数").fill("0.5");
  await page.getByRole("button", { name: "执行动作", exact: true }).click();
  assert.ok((await page.locator(".state-grid article").filter({ hasText: "PUMP-01" }).innerText()).includes("RUNNING"));

  await page.getByRole("button", { name: "设备关系与联动", exact: true }).click();
  const alarmRule = page.locator("article").filter({ hasText: "RULE-LOW-VALUE" }).first();
  await alarmRule.locator('input[type="number"]').fill("0.6");
  await page.getByRole("button", { name: "保存关系与规则", exact: true }).click();
  await page.getByRole("status").filter({ hasText: "设备关系与规则已保存" }).waitFor();
  await page.getByRole("button", { name: "调试预览", exact: true }).click();
  await page.getByRole("button", { name: "新建实例", exact: true }).click();
  await objectSelect.selectOption("SENSOR-01");
  await actionSelect.selectOption("SET_VALUE");
  await page.getByLabel("数值参数").fill("0.5");
  await page.getByRole("button", { name: "执行动作", exact: true }).click();
  await page.locator(".state-grid article").filter({ hasText: "PUMP-01" }).getByText(/ALARM/).waitFor();
  await page.locator(".event-list article").filter({ hasText: "RULE-LOW-VALUE" }).waitFor();
  assert.equal(await page.getByLabel("当前实例").locator("option").count(), 2);
  await page.screenshot({ path: path.join(output, "p2b-runtime.png"), fullPage: true });
  await page.getByRole("button", { name: "复位本实例", exact: true }).click();
  await page.locator(".state-grid article").filter({ hasText: "PUMP-01" }).getByText(/READY/).waitFor();
  assert.deepEqual(errors, []);
  console.log("PASS: P2-B topology persistence, semantic rules, rejection, threshold-driven result, two isolated previews, reset and visual evidence.");
} finally {
  if (browser) await browser.close();
  server.kill("SIGTERM");
}

import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";
import fs from "node:fs";
import path from "node:path";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const require = createRequire(path.join(root, "frontend/package.json"));
const { chromium } = require("playwright");
const base = "http://127.0.0.1:18085";
const output = path.join(root, "test-results");
fs.mkdirSync(output, { recursive: true });
const server = spawn("java", ["-jar", "target/peixun-demo-1.0.0.jar"], {
  cwd: path.join(root, "backend"),
  env: { ...process.env, PORT: "18085", DB_URL: `jdbc:h2:mem:p2a-${Date.now()};DB_CLOSE_DELAY=-1` },
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
async function state(auth, domain) {
  const response = await fetch(`${base}/api/demo/state?workspace=demo&domain=${domain}`, { headers: { Authorization: `Bearer ${auth}` } });
  assert.equal(response.status, 200);
  return response.json();
}
async function command(auth, domain, action, payload, expectedStatus = 200) {
  const current = await state(auth, domain);
  const response = await fetch(`${base}/api/demo/commands?workspace=demo&domain=${domain}`, {
    method: "POST",
    headers: { Authorization: `Bearer ${auth}`, "Content-Type": "application/json" },
    body: JSON.stringify({ action, payload, commandId: crypto.randomUUID(), runEpoch: current.epoch, expectedRevision: current.revision }),
  });
  assert.equal(response.status, expectedStatus, `${action}: ${await response.clone().text()}`);
  return response.json();
}
async function createProject(auth, domain, name) {
  return (await command(auth, domain, "simulationProject.create", { domain, name, purpose: `${name}独立验证`, linkageMode: "NONE" })).data;
}
const objectFor = (asset, index, name = `对象${index}`) => ({
  id: `OBJ-0${index}`,
  name,
  assetRef: { id: asset.id, version: asset.version },
  x: index * 12,
  y: index * 13,
  view: "设备",
  initialState: { status: "READY" },
});

try {
  let ready = false;
  for (let i = 0; i < 200; i++) {
    try { if ((await fetch(`${base}/api/demo/health`)).ok) { ready = true; break; } } catch {}
    await new Promise((resolve) => setTimeout(resolve, 150));
  }
  assert.ok(ready, "P2-A test service did not start");
  assert.equal((await fetch(`${base}/api/demo/bootstrap`)).status, 200);
  const admin = await token("ADMIN");
  const learner = await token("LEARNER_A");
  const asset = (await state(admin, "OPERATION")).assets.find((item) => item.status === "APPROVED");
  assert.ok(asset);
  const projectA = await createProject(admin, "OPERATION", "P2A工程A");
  const projectB = await createProject(admin, "OPERATION", "P2A工程B");
  const projectC = await createProject(admin, "MAINTENANCE", "P2A工程C");
  const objects = [1, 2, 3, 4, 5].map((index) => objectFor(asset, index, index === 5 ? "第五对象工具组" : `对象${index}`));
  await command(admin, "OPERATION", "simulationProject.save", {
    id: projectA.id,
    expectedEditRevision: projectA.editRevision,
    name: projectA.name,
    purpose: projectA.purpose,
    scene: { objects, environment: { weather: "晴", light: "日间", camera: "总览", material: "标准" } },
  });
  const operation = await state(admin, "OPERATION");
  const savedA = operation.simulationProjects.find((item) => item.id === projectA.id);
  const savedScene = operation.scenes.find((item) => item.id === savedA.sceneRef.id);
  assert.equal(savedScene.objects.length, 5);
  assert.equal(savedScene.objects[4].name, "第五对象工具组");
  assert.equal(operation.scenes.find((item) => item.id === projectB.sceneRef.id).objects.length, 0);
  assert.equal((await state(admin, "MAINTENANCE")).scenes.find((item) => item.id === projectC.sceneRef.id).objects.length, 0);

  await command(admin, "OPERATION", "simulationProject.save", {
    id: projectA.id,
    expectedEditRevision: projectA.editRevision,
    name: "过期窗口修改",
    scene: { objects, environment: {} },
  }, 409);

  const privateAsset = (await command(admin, "OPERATION", "asset.import", { name: "操作私有素材", category: "模型", format: "FBX", ownerSystem: "OPERATION" })).data;
  await command(admin, "OPERATION", "asset.approve", { id: privateAsset.id });
  const draftAsset = (await command(admin, "OPERATION", "asset.import", { name: "学员不可见草稿", category: "模型", format: "FBX", ownerSystem: "OPERATION" })).data;
  const maintenancePayload = {
    id: projectC.id,
    expectedEditRevision: projectC.editRevision,
    name: projectC.name,
    scene: { objects: [objectFor({ ...privateAsset, version: 1 }, 1, "跨域共享对象")], environment: {} },
  };
  await command(admin, "MAINTENANCE", "simulationProject.save", maintenancePayload, 403);
  await command(admin, "OPERATION", "asset.share", { id: privateAsset.id, sharedWith: ["MAINTENANCE"] });
  await command(admin, "MAINTENANCE", "simulationProject.save", maintenancePayload);
  const sourceAfterShare = (await state(admin, "OPERATION")).assets.find((item) => item.id === privateAsset.id);
  assert.equal(sourceAfterShare.ownerSystem, "OPERATION");
  assert.equal(sourceAfterShare.visibility, "SHARED");
  const learnerState = await state(learner, "OPERATION");
  assert.equal(learnerState.simulationProjects.length, 0);
  assert.equal(learnerState.assets.some((item) => item.id === draftAsset.id), false);

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
  await page.goto(`${base}/operation/simulation-projects/${projectA.id}`);
  await page.locator("main h1").waitFor();
  await page.getByText("第五对象工具组", { exact: true }).first().waitFor();
  await page.reload();
  await page.getByText("第五对象工具组", { exact: true }).first().waitFor();
  await page.screenshot({ path: path.join(output, "p2a-fifth-object.png"), fullPage: true });
  await page.goto(`${base}/support/simulation-projects`);
  await page.locator("main h1").getByText("场景与设备编排", { exact: true }).waitFor();
  await page.screenshot({ path: path.join(output, "p2a-support-resources.png"), fullPage: true });
  await page.goto(`${base}/operation/topologies/current`);
  await page.getByText("旧全局配置的只读兼容快照", { exact: false }).waitFor();
  assert.deepEqual(errors, []);
  console.log("PASS: P2-A independent projects, fifth object persistence, sharing boundary, conflict guard, learner projection, legacy entry and screenshots.");
} finally {
  if (browser) await browser.close();
  server.kill("SIGTERM");
}

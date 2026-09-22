import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";
import fs from "node:fs";
import path from "node:path";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const require = createRequire(path.join(root, "frontend/package.json"));
const { chromium } = require("playwright");
const base = "http://127.0.0.1:18088";
const output = path.join(root, "test-results");
fs.mkdirSync(output, { recursive: true });
const server = spawn("java", ["-jar", "target/peixun-demo-1.0.0.jar"], {
  cwd: path.join(root, "backend"),
  env: { ...process.env, PORT: "18088", DB_URL: `jdbc:h2:mem:p2d-${Date.now()};DB_CLOSE_DELAY=-1` },
  stdio: "ignore",
});
const uploadedFiles = [];
let browser;

const systems = [
  { id: "OPERATION", slug: "operation", name: "装备使用及作战运用", short: "OP" },
  { id: "MAINTENANCE", slug: "maintenance", name: "装备维修保障", short: "MAINT" },
  { id: "SUPPORT", slug: "support", name: "保障任务筹划及行为演练", short: "SUP" },
];

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
async function command(auth, domain, action, payload, commandId = crypto.randomUUID()) {
  const current = await state(auth, domain);
  const response = await fetch(`${base}/api/demo/commands?workspace=demo&domain=${domain}`, {
    method: "POST",
    headers: { Authorization: `Bearer ${auth}`, "Content-Type": "application/json" },
    body: JSON.stringify({ action, payload, commandId, runEpoch: current.epoch, expectedRevision: current.revision }),
  });
  const body = await response.json();
  assert.equal(response.status, 200, `${action}: ${JSON.stringify(body)}`);
  assert.ok(body.state.simulationProjects.every((item) => item.domain === domain), `${action} command response leaked projects outside ${domain}`);
  return body;
}
async function upload(auth, domain, filename, content) {
  const form = new FormData();
  form.append("file", new Blob([content], { type: "text/plain" }), filename);
  const response = await fetch(`${base}/api/demo/uploads?workspace=demo&domain=${domain}`, {
    method: "POST",
    headers: { Authorization: `Bearer ${auth}` },
    body: form,
  });
  const body = await response.json();
  assert.equal(response.status, 200, JSON.stringify(body));
  uploadedFiles.push(path.join(root, "backend", ".data", "uploads", body.data.blobRef));
  return body.data;
}
async function download(auth, domain, assetId) {
  return fetch(`${base}/api/demo/assets/${assetId}/file?workspace=demo&domain=${domain}`, {
    headers: { Authorization: `Bearer ${auth}` },
  });
}

try {
  let ready = false;
  for (let i = 0; i < 200; i++) {
    try { if ((await fetch(`${base}/api/demo/health`)).ok) { ready = true; break; } } catch {}
    await new Promise((resolve) => setTimeout(resolve, 150));
  }
  assert.ok(ready, "P2-D test service did not start");
  assert.equal((await fetch(`${base}/api/demo/bootstrap`)).status, 200);
  const admin = await token("ADMIN");
  const results = {};

  for (const system of systems) {
    const asset = await upload(admin, system.id, `p2d-${system.slug}.txt`, `${system.name} P2-D 下载范围验证`);
    await command(admin, system.id, "asset.approve", { id: asset.id });
    const project = (await command(admin, system.id, "simulationProject.create", {
      domain: system.id,
      name: `P2D-${system.name}闭环工程`,
      purpose: `${system.name}从素材到模板复用完整验收`,
      linkageMode: "NONE",
    })).data;
    const saved = (await command(admin, system.id, "simulationProject.save", {
      id: project.id,
      expectedEditRevision: project.editRevision,
      name: project.name,
      purpose: project.purpose,
      scene: {
        objects: [{ id: `${system.short}-OBJ-01`, name: `${system.name}验证对象`, assetRef: { id: asset.id, version: asset.version }, x: 30, y: 40, view: "设备", initialState: { status: "READY" } }],
        environment: { weather: "晴", light: "日间", camera: "总览", material: "标准" },
      },
    })).data;
    const preview = (await command(admin, system.id, "simulationPreview.start", { projectId: project.id })).data;
    const action = (await command(admin, system.id, "simulationPreview.action", {
      id: preview.id, objectId: `${system.short}-OBJ-01`, actionId: "CONFIRM", parameters: {},
    })).data;
    assert.equal(action.states[`${system.short}-OBJ-01`].status, "CONFIRMED");
    const publishPayload = { projectId: project.id, usageInstructions: `${system.name}单对象模板使用说明`, sharedWith: [] };
    const inspection = (await command(admin, system.id, "simulationTemplate.validate", publishPayload)).data;
    assert.equal(inspection.valid, true);
    const commandId = `P2D-PUBLISH-${system.id}`;
    const published = await command(admin, system.id, "simulationTemplate.publish", publishPayload, commandId);
    const replay = await command(admin, system.id, "simulationTemplate.publish", publishPayload, commandId);
    assert.equal(replay.data.id, published.data.id, "idempotent replay must return the same template");
    const copy = (await command(admin, system.id, "simulationTemplate.instantiate", {
      id: published.data.id, domain: system.id, mode: "COPY",
    })).data;
    assert.notEqual(copy.id, project.id);
    assert.notEqual(copy.sceneRef.id, saved.scene.id);
    const scoped = await state(admin, system.id);
    assert.equal(scoped.systemTemplates.filter((item) => item.id === published.data.id).length, 1);
    assert.ok(scoped.simulationProjects.some((item) => item.id === copy.id));
    results[system.id] = { asset, project, template: published.data, copy };
  }

  const ownerDownload = await download(admin, "OPERATION", results.OPERATION.asset.id);
  assert.equal(ownerDownload.status, 200);
  assert.ok((await ownerDownload.text()).includes("装备使用及作战运用"));
  assert.equal((await download(admin, "MAINTENANCE", results.OPERATION.asset.id)).status, 404);
  await command(admin, "OPERATION", "asset.share", { id: results.OPERATION.asset.id, sharedWith: ["MAINTENANCE"] });
  assert.equal((await download(admin, "MAINTENANCE", results.OPERATION.asset.id)).status, 200);

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
  for (const system of systems) {
    await page.goto(`${base}/${system.slug}/assets`);
    await page.getByText(results[system.id].asset.name, { exact: true }).first().waitFor();
    await page.goto(`${base}/${system.slug}/simulation-projects`);
    await page.getByText(results[system.id].project.name, { exact: true }).first().waitFor();
    await page.goto(`${base}/${system.slug}/simulation-templates`);
    const card = page.locator(".project-card").filter({ hasText: results[system.id].template.name }).first();
    await card.getByRole("button", { name: "查看版本详情" }).click();
    await page.getByText(results[system.id].asset.name, { exact: true }).last().waitFor();
    await page.reload();
    await page.locator(".project-card").filter({ hasText: results[system.id].template.name }).first().getByRole("button", { name: "查看版本详情" }).click();
    await page.getByText(`${system.name}单对象模板使用说明`, { exact: true }).waitFor();
    await page.screenshot({ path: path.join(output, `p2d-${system.slug}-closure.png`), fullPage: true });
  }
  assert.deepEqual(errors, []);
  console.log("PASS: P2-D three-system asset → project → preview → publish → instantiate closure, scoped receipts/read/download and refresh recovery.");
} finally {
  if (browser) await browser.close();
  server.kill("SIGTERM");
  for (const file of uploadedFiles) {
    try { fs.unlinkSync(file); } catch (error) { if (error.code !== "ENOENT") throw error; }
  }
}

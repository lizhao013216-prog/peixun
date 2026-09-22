import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { seedDemo } from "./load-demo.mjs";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const base = "http://127.0.0.1:8084";
const server = spawn("java", ["-jar", "target/peixun-demo-1.0.0.jar"], {
  cwd: path.join(root, "backend"),
  env: {
    ...process.env,
    PORT: "8084",
    DB_URL: `jdbc:h2:mem:three-systems-${Date.now()};DB_CLOSE_DELAY=-1`,
  },
  stdio: "ignore",
});

async function token(actor) {
  const response = await fetch(`${base}/api/demo/session`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ actorId: actor }),
  });
  assert.equal(response.status, 200);
  return (await response.json()).token;
}

async function state(workspace, actorToken, domain) {
  const response = await fetch(
    `${base}/api/demo/state?workspace=${encodeURIComponent(workspace)}&domain=${domain}`,
    { headers: { Authorization: `Bearer ${actorToken}` } },
  );
  assert.equal(response.status, 200);
  return response.json();
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
  assert.ok(ready, "三系统测试服务未能启动");
  const fixture = await seedDemo(base, "三系统领域隔离验收");
  const admin = await token("ADMIN");
  const learner = await token("LEARNER_A");
  const operation = await state(fixture.workspace, admin, "OPERATION");
  const maintenance = await state(fixture.workspace, admin, "MAINTENANCE");
  const support = await state(fixture.workspace, admin, "SUPPORT");
  const learnerOperation = await state(fixture.workspace, learner, "OPERATION");

  assert.ok(operation.courses.length > 0);
  assert.ok(operation.courses.every((item) => item.domain === "OPERATION"));
  assert.ok(operation.assignments.every((item) => item.domain === "OPERATION"));
  assert.ok(maintenance.courses.length > 0);
  assert.ok(maintenance.courses.every((item) => item.domain === "MAINTENANCE"));
  assert.ok(maintenance.assignments.every((item) => item.domain === "MAINTENANCE"));
  assert.equal(support.courses.length, 0);
  assert.equal(support.assignments.length, 0);
  assert.ok(support.tasks.length > 0, "第三系统原保障任务必须保留");
  assert.ok(
    learnerOperation.assignments.every((item) => item.learnerId === "LEARNER_A"),
    "学员只能读取本人任务",
  );
  assert.ok(
    learnerOperation.attempts.every((item) => item.learnerId === "LEARNER_A"),
    "学员只能读取本人训练记录",
  );
  console.log("PASS: 操作、维修、保障三领域及学员身份投影相互隔离。");
} finally {
  server.kill("SIGTERM");
}

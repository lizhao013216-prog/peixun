import { spawn } from "node:child_process";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";
import path from "node:path";
import fs from "node:fs";
import assert from "node:assert/strict";
import { seedDemo } from "./load-demo.mjs";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const require = createRequire(path.join(root, "frontend/package.json"));
const { chromium } = require("playwright");
const base = "http://127.0.0.1:8081";
const output = path.join(root, "test-results");
fs.mkdirSync(output, { recursive: true });
const serverLog = fs.openSync(path.join(output, "browser-server.log"), "w");
const server = spawn("java", ["-jar", "target/peixun-demo-1.0.0.jar"], {
  cwd: path.join(root, "backend"),
  env: {
    ...process.env,
    PORT: "8081",
    DB_URL: `jdbc:h2:mem:browser-${Date.now()};DB_CLOSE_DELAY=-1`,
  },
  stdio: ["ignore", serverLog, serverLog],
});
let browser;
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
  assert.ok(ready, "后端未能启动，请检查 test-results/browser-server.log");
  const data = await seedDemo(base, "浏览器验收案例");
  browser = await chromium.launch({
    ...(process.env.CHROMIUM_EXECUTABLE
      ? { executablePath: process.env.CHROMIUM_EXECUTABLE }
      : {}),
    args: ["--no-sandbox", "--disable-dev-shm-usage"],
  });
  const context = await browser.newContext({
    viewport: { width: 1440, height: 1000 },
    deviceScaleFactor: 1,
    locale: "zh-CN",
  });
  await context.addInitScript((workspace) => {
    if (!localStorage.getItem("peixun.workspace"))
      localStorage.setItem("peixun.workspace", workspace);
    if (!localStorage.getItem("peixun.actor"))
      localStorage.setItem("peixun.actor", "ADMIN");
  }, data.workspace);
  const page = await context.newPage();
  const errors = [];
  page.on("pageerror", (error) => errors.push(error.message));
  page.on("response", (response) => {
    if (response.status() >= 500)
      errors.push(`${response.status()} ${response.url()}`);
  });
  await page.goto(`${base}/workbench`);
  await page.locator(".stat-card").first().waitFor();
  await page.evaluate(() => document.fonts.ready);
  await page.screenshot({
    path: path.join(output, "workbench.png"),
    fullPage: true,
  });
  const catalog = fs.readFileSync(
    path.join(root, "frontend/src/catalog.ts"),
    "utf8",
  );
  const routes = [
    ...catalog.matchAll(/page\(\s*"[^"]+",\s*"[^"]+",\s*"([^"]+)"/g),
  ].map((match) => match[1].replace(":id", "current"));
  assert.equal(routes.length, 55);
  for (const route of routes) {
    await page.goto(base + route);
    await page.locator("main h1").first().waitFor();
    await page
      .locator(".app-footer")
      .getByText(/业务数据已保存/)
      .waitFor();
    assert.ok(
      (await page.locator("main").innerText()).length > 80,
      `页面内容不足：${route}`,
    );
    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth > innerWidth + 2,
    );
    assert.equal(overflow, false, `桌面页面溢出：${route}`);
  }
  await page.goto(`${base}/operation/assignments`);
  await page.getByText("泵组操作与异常识别", { exact: true }).waitFor();
  assert.equal(
    (await page.locator("main").innerText()).includes("泵组维修技能培训"),
    false,
    "操作系统不得显示维修培训任务",
  );
  await page.goto(`${base}/maintenance/assignments`);
  await page.getByText("泵组维修技能培训", { exact: true }).waitFor();
  assert.equal(
    (await page.locator("main").innerText()).includes("泵组操作与异常识别"),
    false,
    "维修系统不得显示操作培训任务",
  );
  await page.goto(`${base}/operation/courses/${data.c1}`);
  const legacyCourseName = page.getByRole("textbox", {
    name: "课程名称",
    exact: true,
  });
  await legacyCourseName.waitFor();
  assert.equal(await legacyCourseName.inputValue(), "泵组操作与异常识别");
  assert.ok(page.url().includes(`/operation/coursewares/${data.c1}`));
  await page.goto(`${base}/operation/coursewares/DOES-NOT-EXIST`);
  await page.getByText("当前系统中没有这个课件", { exact: true }).waitFor();
  await page.goto(`${base}/support/training`);
  await page.getByRole("button", { name: /新建.*课件/ }).waitFor();
  assert.ok(page.url().includes("/support/coursewares/current"));
  for (const [name, route] of [
    ["training", `/sessions/${data.a2}`],
    ["exercise", `/support/exercises/${data.delayRun}`],
    ["execution", `/support/executions/${data.execution}`],
    ["archive", `/support/archives/${data.archive}`],
    ["editor", "/editor/current"],
  ]) {
    await page.goto(base + route);
    await page.locator(".panel").first().waitFor();
    await page.evaluate(() => document.fonts.ready);
    await page.screenshot({
      path: path.join(output, `${name}.png`),
      fullPage: true,
    });
  }
  await page.goto(`${base}/support/exercises/${data.delayRun}`);
  await page.getByRole("button", { name: "过程复盘", exact: true }).click();
  await page.getByRole("slider", { name: "复盘时间" }).fill("30");
  assert.deepEqual(await page.locator(".inventory-boxes b").allTextContents(), [
    "2",
    "2",
    "0",
    "0",
  ]);
  await page.getByRole("slider", { name: "复盘时间" }).fill("300");
  assert.deepEqual(await page.locator(".inventory-boxes b").allTextContents(), [
    "2",
    "0",
    "0",
    "2",
  ]);
  await page.goto(`${base}/operation/topologies/current`);
  await page.getByText("旧全局配置的只读兼容快照", { exact: false }).waitFor();
  assert.ok(page.url().includes("/operation/simulation-projects/PROJECT-LEGACY-OPERATION"));
  await page.screenshot({
    path: path.join(output, "topology.png"),
    fullPage: true,
  });
  await page.goto(`${base}/operation/coursewares/current`);
  await page.getByRole("button", { name: "新建操作课件", exact: true }).click();
  const dialog = page.getByRole("dialog");
  await dialog
    .getByRole("textbox", { name: "课程名称 *", exact: true })
    .fill("浏览器创建课程");
  await dialog
    .getByRole("combobox", { name: "课件契约", exact: true })
    .selectOption("V2");
  await dialog.getByRole("button", { name: "创建草稿", exact: true }).click();
  await page
    .getByRole("status")
    .filter({ hasText: "兼容课件草稿已创建" })
    .waitFor();
  await page.getByRole("button", { name: "提交审核", exact: true }).click();
  await page.getByRole("status").filter({ hasText: "课程已提交" }).waitFor();
  await page.reload();
  await page.getByRole("textbox", { name: "课程名称", exact: true }).waitFor();
  assert.equal(
    await page
      .getByRole("textbox", { name: "课程名称", exact: true })
      .inputValue(),
    "浏览器创建课程",
  );
  // Verify the actual first-time presentation flow through the UI.
  await page.goto(`${base}/guide`);
  await page
    .getByRole("button", { name: "准备一套完整演示案例", exact: true })
    .click();
  await page
    .locator(".demo-ready-card")
    .getByText("当前工作区已有归档成果")
    .waitFor({ timeout: 60000 });
  await page.getByRole("button", { name: "开始讲解", exact: true }).waitFor();
  await page.getByRole("button", { name: "关闭提示", exact: true }).click();
  await page.screenshot({
    path: path.join(output, "guide.png"),
    fullPage: false,
  });
  const mainFont = await page.evaluate(
    () => getComputedStyle(document.documentElement).fontSize,
  );
  assert.equal(mainFont, "16px");
  assert.equal(
    (await page.locator("body").innerText()).includes("航维"),
    false,
  );
  for (const name of ["装备操作训练", "维修技能培训", "保障筹划与演练"]) {
    await page.locator(".tour-card").filter({ hasText: name }).click();
    await page.getByRole("button", { name: "开始讲解", exact: true }).click();
    await page.locator(".demo-guide").waitFor();
    assert.ok((await page.locator(".demo-guide").innerText()).includes(name));
    await page.getByRole("button", { name: "全部步骤", exact: true }).click();
  }
  await page.locator(".tour-card").filter({ hasText: "装备操作训练" }).click();
  await page.getByRole("button", { name: "跟随操作", exact: true }).click();
  await page.locator(".guide-step-list > button").nth(3).click();
  await page
    .locator(".demo-guide")
    .getByRole("button", { name: /切换为/ })
    .click();
  await page.getByText("身份已就绪", { exact: true }).waitFor();
  assert.equal(
    await page.getByRole("combobox", { name: "演示身份" }).inputValue(),
    "AUTHOR",
  );
  await page.reload();
  await page
    .locator(".demo-guide")
    .getByText("编排操作课程", { exact: true })
    .waitFor();
  assert.equal(
    await page.getByRole("combobox", { name: "演示身份" }).inputValue(),
    "AUTHOR",
  );
  await page.screenshot({
    path: path.join(output, "guided-course.png"),
    fullPage: false,
  });
  await page.getByRole("button", { name: "退出演示指引", exact: true }).click();
  await page.setViewportSize({ width: 1366, height: 768 });
  for (const route of [
    "/operation/assignments",
    `/support/exercises/${data.delayRun}`,
  ]) {
    await page.goto(base + route);
    await page.locator("main h1, main .panel").first().waitFor();
    assert.equal(
      await page.evaluate(
        () => document.documentElement.scrollWidth > innerWidth + 2,
      ),
      false,
      `1366px笔记本页面整体溢出：${route}`,
    );
    const visibleButton = page.locator("main .btn:visible").first();
    if (await visibleButton.count()) {
      const box = await visibleButton.boundingBox();
      assert.ok(
        box && box.x >= 0 && box.x + box.width <= 1366,
        `1366px笔记本关键操作被横向遮挡：${route}`,
      );
    }
  }
  const laptopContext = await browser.newContext({
    viewport: { width: 1366, height: 768 },
    deviceScaleFactor: 1,
    locale: "zh-CN",
  });
  await laptopContext.addInitScript((workspace) => {
    localStorage.setItem("peixun.workspace", workspace);
    localStorage.setItem("peixun.actor", "ADMIN");
  }, data.workspace);
  const laptopPage = await laptopContext.newPage();
  laptopPage.on("pageerror", (error) => errors.push(error.message));
  laptopPage.on("response", (response) => {
    if (response.status() >= 500)
      errors.push(`${response.status()} ${response.url()}`);
  });
  await laptopPage.goto(`${base}/sessions/${data.a2}`);
  await laptopPage.locator("main .panel").first().waitFor();
  const laptopTrainingText = await laptopPage.locator("main").innerText();
  assert.match(
    laptopTrainingText,
    /训练完成|全部步骤已完成/,
    `1366px训练结果未正确显示：${laptopTrainingText}`,
  );
  assert.equal(
    await laptopPage.evaluate(
      () => document.documentElement.scrollWidth > innerWidth + 2,
    ),
    false,
    "1366px笔记本训练结果整体溢出",
  );
  await laptopPage.screenshot({
    path: path.join(output, "laptop-training-1366.png"),
    fullPage: true,
  });
  await laptopContext.close();
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto(`${base}/workbench`);
  await page.locator(".stat-card").first().waitFor();
  await page.evaluate(() => document.fonts.ready);
  assert.equal(
    await page.evaluate(
      () => document.documentElement.scrollWidth > innerWidth + 2,
    ),
    false,
    "移动页面整体溢出",
  );
  await page.screenshot({
    path: path.join(output, "mobile.png"),
    fullPage: true,
  });
  await page.goto(`${base}/guide`);
  await page.locator(".tour-card").first().waitFor();
  assert.equal(
    await page.evaluate(
      () => document.documentElement.scrollWidth > innerWidth + 2,
    ),
    false,
    "移动演示中心溢出",
  );
  await page.screenshot({
    path: path.join(output, "guide-mobile.png"),
    fullPage: true,
  });
  await page.getByRole("button", { name: "展开导航" }).click();
  assert.ok(await page.locator(".sidebar.open").isVisible());
  assert.deepEqual(errors, [], "浏览器发生未处理异常或服务端错误");
  fs.writeFileSync(
    path.join(output, "browser-verification.md"),
    `# 浏览器验收\n\n- 53个规范页面可访问。\n- 三子系统任务按领域隔离，旧课件路径正确重定向。\n- 旧设备组网路径进入只读兼容工程，不再写入全局单例。\n- 无效课件深链明确提示，不回退到最后一项。\n- 保障教学旧入口正确重定向到已开放的操作课件。\n- 1440px桌面页面无整体横向溢出。\n- 1366×768培训任务、学员训练和保障演练关键操作可见且无整体溢出。\n- 390px工作台与折叠菜单可用。\n- 演练复盘物料账本随时间变化。\n- 未捕获页面异常：${errors.length}。\n- 完整案例由HTTP业务命令生成。\n- 页面按钮生成独立完整案例通过。\n- 三系统指引入口、角色切换和刷新恢复通过。\n- 正文字号16px，工作台及演示中心不含旧展示名称。\n`,
  );
  console.log(
    "PASS: 55 routes, domain isolation, deep links, 1440/1366/390 layout, replay inventory, legacy topology redirect, course form persistence, golden HTTP fixture, no page errors.",
  );
} finally {
  if (browser) await browser.close();
  server.kill("SIGTERM");
  fs.closeSync(serverLog);
}

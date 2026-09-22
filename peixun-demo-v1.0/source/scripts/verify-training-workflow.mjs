import { spawn } from "node:child_process";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";
import path from "node:path";
import fs from "node:fs";
import assert from "node:assert/strict";
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const require = createRequire(path.join(root, "frontend/package.json"));
const { chromium } = require("playwright");
const base = "http://127.0.0.1:8082",
  out = path.join(root, "test-results");
fs.mkdirSync(out, { recursive: true });
const log = fs.openSync(path.join(out, "training-server.log"), "w");
const server = spawn("java", ["-jar", "target/peixun-demo-1.0.0.jar"], {
  cwd: path.join(root, "backend"),
  env: {
    ...process.env,
    PORT: "8082",
    DB_URL: `jdbc:h2:mem:training-${Date.now()};DB_CLOSE_DELAY=-1`,
  },
  stdio: ["ignore", log, log],
});
let browser;
try {
  let ready = false;
  for (let i = 0; i < 100; i++) {
    try {
      if ((await fetch(base + "/api/demo/health")).ok) {
        ready = true;
        break;
      }
    } catch {}
    await new Promise((r) => setTimeout(r, 150));
  }
  assert.ok(ready);
  browser = await chromium.launch({
    ...(process.env.CHROMIUM_EXECUTABLE
      ? { executablePath: process.env.CHROMIUM_EXECUTABLE }
      : {}),
    args: ["--no-sandbox", "--disable-dev-shm-usage"],
  });
  const page = await browser.newPage({
      viewport: { width: 1600, height: 1080 },
      locale: "zh-CN",
    }),
    errors = [];
  page.on("pageerror", (e) => errors.push(e.message));
  await page.addInitScript(() => {
    if (!localStorage.getItem("peixun.actor"))
      localStorage.setItem("peixun.actor", "AUTHOR");
  });
  const button = (name) => page.getByRole("button", { name, exact: true });
  const actor = (id) =>
    page
      .getByRole("combobox", { name: "演示身份", exact: true })
      .selectOption(id);
  const closeToast = async () => {
    await page.waitForFunction(
      () =>
        !document.querySelector(".toast-message") ||
        getComputedStyle(document.querySelector(".toast-message")).opacity ===
          "1",
    );
    const b = button("关闭提示");
    if (await b.isVisible()) {
      await b.click();
      await page.locator(".toast-message").waitFor({ state: "detached" });
    }
  };
  const shot = async (name) => {
    await closeToast();
    await page.evaluate(() => document.fonts.ready);
    await page.screenshot({
      path: path.join(out, name + ".png"),
      fullPage: false,
    });
  };
  await page.goto(base + "/maintenance/courses/current");
  await button("新建维修课件").click();
  let dialog = page.getByRole("dialog");
  await dialog
    .getByRole("textbox", { name: "课程名称 *", exact: true })
    .fill("教员制作与学员训练验收");
  await dialog.getByRole("button", { name: "创建草稿", exact: true }).click();
  await page.locator(".step-fields").waitFor();
  await page
    .getByRole("textbox", { name: "学员应该怎么操作", exact: true })
    .fill("先选择通用泵组，核对本课目标，再点击确认维修训练任务。");
  await page
    .getByRole("textbox", { name: "操作成功后显示什么结果", exact: true })
    .fill("任务对象已确认，训练记录已保存，接下来检查准备状态。");
  await button("保存步骤").click();
  await page
    .getByRole("status")
    .filter({ hasText: "课程内容已保存" })
    .waitFor();
  await shot("course-builder");
  await button("预览学员视角").click();
  dialog = page.getByRole("dialog");
  await dialog
    .locator(".object-picker")
    .getByRole("button", { name: /阀门/ })
    .click();
  await dialog
    .getByRole("button", { name: "确认维修训练任务", exact: true })
    .click();
  await dialog.getByText("预览操作未通过", { exact: true }).waitFor();
  await dialog
    .locator(".object-picker")
    .getByRole("button", { name: /通用泵组/ })
    .click();
  await dialog
    .getByRole("button", { name: "确认维修训练任务", exact: true })
    .click();
  await dialog.getByText("预览操作通过", { exact: true }).waitFor();
  await shot("course-preview");
  await dialog
    .getByRole("button", { name: "返回课件制作", exact: true })
    .click();
  await button("提交审核").click();
  await button("切换独立审核教员").waitFor();
  assert.equal(await button("审核通过").count(), 0);
  await button("切换独立审核教员").click();
  await button("审核通过").click();
  await button("发布课程").click();
  await button("分配培训任务").waitFor({ timeout: 15000 });
  await button("分配培训任务").click();
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "确认分配任务", exact: true })
    .click();
  await page.getByText("已分配 1 项培训任务", { exact: true }).waitFor();
  await actor("LEARNER_A");
  await button("查看学员任务").click();
  await button("查看任务并开始").waitFor();
  await shot("learner-tasks");
  await button("查看任务并开始").click();
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "开始训练", exact: true })
    .click();
  await page.locator(".lesson-workspace").waitFor();
  assert.equal(await button("确认维修训练任务").isEnabled(), false);
  assert.ok(
    (await page.locator(".lesson-instruction").innerText()).includes(
      "先选择通用泵组",
    ),
  );
  await shot("learner-operation");
  await page.setViewportSize({ width: 390, height: 844 });
  assert.equal(
    await page.evaluate(
      () => document.documentElement.scrollWidth > innerWidth + 2,
    ),
    false,
    "mobile training overflow",
  );
  await page.screenshot({
    path: path.join(out, "learner-mobile.png"),
    fullPage: true,
  });
  await page.setViewportSize({ width: 1600, height: 1080 });
  await page
    .locator(".object-picker")
    .getByRole("button", { name: /阀门/ })
    .click();
  await button("确认维修训练任务").click();
  await page.locator(".training-feedback.ERROR").waitFor();
  assert.ok(
    (await page.locator(".training-counter").innerText()).startsWith("0"),
  );
  await shot("learner-error");
  await button("查看操作提示（扣2分）").click();
  await page.locator(".training-feedback.HELP").waitFor();
  await page.reload();
  await page.locator(".training-feedback.HELP").waitFor();
  await page
    .locator(".object-picker")
    .getByRole("button", { name: /通用泵组/ })
    .click();
  await button("确认维修训练任务").click();
  await page.locator(".training-feedback.PASS").waitFor();
  assert.ok(
    (await page.locator(".training-feedback").innerText()).includes(
      "任务对象已确认",
    ),
  );
  assert.equal(await button("确认维修训练任务").isEnabled(), false);
  await shot("learner-success");
  await page.getByRole("button", { name: /继续下一步：/ }).click();
  await button("暂停并保存进度").click();
  await page.reload();
  await page.locator(".info-note").filter({ hasText: "训练已暂停" }).waitFor();
  await page
    .locator(".info-note")
    .getByRole("button", { name: "继续训练", exact: true })
    .click();
  await page.getByRole("button", { name: /继续下一步：/ }).click();
  const names = [
    "阀门",
    "通用泵组",
    "通用泵组",
    "通用泵组",
    "通用泵组",
    "通用泵组",
    "通用泵组",
    "传感器",
    "通用泵组",
  ];
  for (let i = 0; i < names.length; i++) {
    await page
      .locator(".object-picker")
      .getByRole("button", { name: new RegExp(names[i]) })
      .click();
    await page.locator(".lesson-submit").getByRole("button").click();
    if (i < names.length - 1)
      await page.getByRole("button", { name: /继续下一步：/ }).click();
  }
  await page.locator(".training-complete").waitFor();
  assert.equal(
    await page.locator(".completion-metrics b").first().innerText(),
    "93",
  );
  await button("填写课程反馈").click();
  await page
    .getByRole("dialog")
    .getByRole("textbox")
    .fill("当前任务、具体动作和反馈都能看清。");
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "提交反馈", exact: true })
    .click();
  await button("演示：切换教员验收").click();
  await button("确认证据").click();
  await page.getByText("教员已确认本次培训证据", { exact: true }).waitFor();
  await shot("training-completed");
  assert.deepEqual(errors, []);
  fs.writeFileSync(
    path.join(out, "training-workflow-verification.md"),
    "# 教员到学员业务流程验证\n\n- 从空工作区通过页面完成：新建课件→编辑步骤→学员预览→独立审核→发布→分配→学员训练→反馈→证据确认。\n- 修改的操作说明与预期结果在学员训练中一致展示。\n- 预览错误对象不通过，正确对象通过；预览不生成成绩。\n- 学员未选对象不能提交；选错对象保留本步并说明原因；成功后明确显示结果并等待继续。\n- 刷新保留记录，暂停/继续不丢失进度；完成10步、1次错误、1次帮助得到93分。\n- 390px训练页面无整体横向溢出。\n- 浏览器未处理异常：0。\n",
  );
  console.log(
    "PASS: authoring → preview → independent review → publish → assignment → learner interaction → confirmation; saved instructions, wrong object, score, pause/resume and mobile.",
  );
} finally {
  if (browser) await browser.close();
  server.kill("SIGTERM");
  fs.closeSync(log);
}

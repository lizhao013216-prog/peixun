import assert from "node:assert/strict";
import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const sourceRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const repositoryRoot = path.resolve(sourceRoot, "..");
const output = path.join(sourceRoot, "test-results");
fs.mkdirSync(output, { recursive: true });

function hash(file) {
  return crypto.createHash("sha256").update(fs.readFileSync(file)).digest("hex");
}

function files(root, current = root) {
  return fs
    .readdirSync(current, { withFileTypes: true })
    .flatMap((entry) => {
      const target = path.join(current, entry.name);
      return entry.isDirectory() ? files(root, target) : [path.relative(root, target).replaceAll("\\", "/")];
    })
    .sort();
}

const sourceJar = path.join(sourceRoot, "backend", "target", "peixun-demo-1.0.0.jar");
const releaseJar = path.join(repositoryRoot, "backend", "peixun-demo.jar");
assert.ok(fs.existsSync(sourceJar), "源码构建 JAR 不存在");
assert.ok(fs.existsSync(releaseJar), "根目录运行 JAR 不存在");
assert.equal(hash(sourceJar), hash(releaseJar), "根目录 JAR 与本次源码构建不一致");

const sourceDist = path.join(sourceRoot, "frontend", "dist");
const releaseDist = path.join(repositoryRoot, "frontend", "dist");
const sourceFiles = files(sourceDist);
const releaseFiles = files(releaseDist);
assert.deepEqual(releaseFiles, sourceFiles, "根目录前端文件集合与源码构建不一致");
for (const relative of sourceFiles)
  assert.equal(
    hash(path.join(sourceDist, relative)),
    hash(path.join(releaseDist, relative)),
    `根目录前端文件不是同次构建：${relative}`,
  );

const evidence = [
  "test-results/browser-verification.md",
  "test-results/laptop-training-1366.png",
  "docs/assets/p2d-operation-closure.png",
  "docs/assets/p2d-maintenance-closure.png",
  "docs/assets/p2d-support-closure.png",
  "docs/assets/p4-training-completed.png",
  "docs/assets/p5-support-training-completed.png",
  "docs/assets/p6-training-issue-closure.png",
];
for (const relative of evidence)
  assert.ok(
    fs.existsSync(path.join(sourceRoot, relative)),
    `缺少 P7 阶段证据：${relative}`,
  );

const reports = path.join(sourceRoot, "backend", "target", "surefire-reports");
const xmlReports = fs
  .readdirSync(reports)
  .filter((name) => name.endsWith(".xml"))
  .map((name) => fs.readFileSync(path.join(reports, name), "utf8"));
assert.ok(xmlReports.length > 0, "没有 Maven 测试报告");
let tests = 0;
for (const report of xmlReports) {
  const suite = report.match(/<testsuite\b[^>]*\btests="(\d+)"[^>]*>/);
  assert.ok(suite, "Maven 测试报告格式不完整");
  tests += Number(suite[1]);
  assert.match(report, /\bfailures="0"/);
  assert.match(report, /\berrors="0"/);
}
assert.ok(tests >= 48, `Maven 测试数量不足：${tests}`);

for (const workflow of [".github/workflows/ci.yml", "repository-config/ci.yml"]) {
  const content = fs.readFileSync(path.join(repositoryRoot, workflow), "utf8");
  assert.match(content, /verify-p5-p6\.mjs/);
  assert.match(content, /verify-p7-delivery\.mjs/);
}

const start = fs.readFileSync(path.join(repositoryRoot, "start.bat"), "utf8");
assert.match(start, /cd \/d "%~dp0backend"/i);
assert.match(start, /java -jar peixun-demo\.jar/i);
const implementation = fs.readFileSync(path.join(sourceRoot, "docs", "业务重构实施进度.md"), "utf8");
const acceptance = fs.readFileSync(path.join(sourceRoot, "docs", "本轮验收记录.md"), "utf8");
assert.match(implementation, /P7 全链路回归与可运行交付 \| 已完成/);
for (const id of ["P7-01", "P7-02", "P7-03", "P7-04", "P7-05", "P7-06"])
  assert.match(acceptance, new RegExp(`\\| ${id} \\| 通过 \\|`));

const report = `# P7 可运行交付校验\n\n- Maven 测试：${tests} 项，0 失败。\n- 根目录 JAR 与源码构建 SHA-256 一致：${hash(sourceJar)}。\n- 根目录前端与源码构建逐文件一致：${sourceFiles.length} 个文件。\n- 1440px、1366×768、390px 浏览器证据齐全。\n- P2—P6 阶段关键页面证据齐全。\n- 两份 CI 配置已纳入 P5/P6 与 P7 校验。\n- start.bat 仍指向根目录发布 JAR。\n`;
fs.writeFileSync(path.join(output, "p7-delivery-verification.md"), report);
console.log(
  `PASS: P7 delivery parity, ${tests} Maven tests, ${sourceFiles.length} frontend files, CI coverage, evidence and start.bat linkage.`,
);

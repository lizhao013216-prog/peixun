import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const sourceRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const repositoryRoot = path.resolve(sourceRoot, "..");
const releaseBackend = path.join(repositoryRoot, "backend");
const releaseFrontend = path.join(repositoryRoot, "frontend", "dist");
const releaseJar = path.join(releaseBackend, "peixun-demo.jar");
const indexPath = path.join(releaseFrontend, "index.html");
const base = "http://127.0.0.1:8083";

assert.ok(fs.existsSync(releaseJar) && fs.statSync(releaseJar).isFile(), "根目录后端运行包不存在");
assert.ok(fs.existsSync(indexPath) && fs.statSync(indexPath).isFile(), "根目录前端入口不存在");
const html = fs.readFileSync(indexPath, "utf8");
const entryFiles = [...html.matchAll(/(?:src|href)="([^"]+)"/g)]
  .map((match) => match[1])
  .filter((item) => item.startsWith("/"));
assert.ok(entryFiles.some((item) => item.endsWith(".js")), "前端入口没有引用 JS");
assert.ok(entryFiles.some((item) => item.endsWith(".css")), "前端入口没有引用 CSS");
assert.ok(entryFiles.some((item) => item.endsWith(".svg")), "前端入口没有引用图标");
const cssFiles = entryFiles.filter((item) => item.endsWith(".css"));
const fontFiles = cssFiles.flatMap((item) => {
  const css = fs.readFileSync(path.join(releaseFrontend, item.slice(1)), "utf8");
  return [...css.matchAll(/url\((?:"|')?([^"')]+)(?:"|')?\)/g)]
    .map((match) => match[1])
    .filter((reference) => reference.startsWith("/fonts/"));
});
assert.ok(fontFiles.length > 0, "构建 CSS 没有引用本地字体");
const assets = [...new Set([...entryFiles, ...fontFiles])];
for (const asset of assets)
  assert.ok(
    fs.existsSync(path.join(releaseFrontend, asset.slice(1))) &&
      fs.statSync(path.join(releaseFrontend, asset.slice(1))).isFile(),
    `缺少资源：${asset}`,
  );

const server = spawn("java", ["-jar", "peixun-demo.jar"], {
  cwd: releaseBackend,
  env: {
    ...process.env,
    PORT: "8083",
    DB_URL: `jdbc:h2:mem:release-${Date.now()};DB_CLOSE_DELAY=-1`,
  },
  stdio: "ignore",
});

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
  assert.ok(ready, "根目录运行包未能启动");
  const index = await fetch(`${base}/operation/overview`);
  assert.equal(index.status, 200);
  assert.match(index.headers.get("content-type") || "", /text\/html/);
  for (const asset of assets) {
    const response = await fetch(base + asset);
    assert.equal(response.status, 200, `运行包资源不可访问：${asset}`);
    assert.doesNotMatch(
      response.headers.get("content-type") || "",
      /text\/html/,
      `静态资源错误回退到 HTML：${asset}`,
    );
  }
  console.log("PASS: 根目录 start.bat 对应的 JAR、页面和静态资源可直接启动。");
} finally {
  server.kill("SIGTERM");
}

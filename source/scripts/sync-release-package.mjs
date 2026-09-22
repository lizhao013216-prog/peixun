import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

const sourceRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const repositoryRoot = path.resolve(sourceRoot, "..");
const sourceDist = path.join(sourceRoot, "frontend", "dist");
const sourceJar = path.join(sourceRoot, "backend", "target", "peixun-demo-1.0.0.jar");
const releaseDist = path.join(repositoryRoot, "frontend", "dist");
const releaseJar = path.join(repositoryRoot, "backend", "peixun-demo.jar");

for (const required of [path.join(sourceDist, "index.html"), sourceJar]) {
  if (!fs.existsSync(required)) throw new Error(`缺少构建产物：${required}`);
}

const staging = fs.mkdtempSync(path.join(os.tmpdir(), "peixun-release-"));
try {
  const stagedDist = path.join(staging, "dist");
  const stagedJar = path.join(staging, "peixun-demo.jar");
  fs.cpSync(sourceDist, stagedDist, { recursive: true });
  fs.copyFileSync(sourceJar, stagedJar);

  fs.rmSync(releaseDist, { recursive: true, force: true });
  fs.mkdirSync(path.dirname(releaseDist), { recursive: true });
  fs.cpSync(stagedDist, releaseDist, { recursive: true });
  fs.mkdirSync(path.dirname(releaseJar), { recursive: true });
  fs.copyFileSync(stagedJar, releaseJar);
} finally {
  fs.rmSync(staging, { recursive: true, force: true });
}

console.log("PASS: 根目录运行包已同步，backend/.data 未改动。");

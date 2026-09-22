import { pathToFileURL } from "node:url";
import { seedDemo } from "../frontend/src/lib/seed-demo.mjs";
export { seedDemo };

if (
  process.argv[1] &&
  import.meta.url === pathToFileURL(process.argv[1]).href
) {
  try {
    const result = await seedDemo(process.argv[2], process.argv[3]);
    console.log(
      `案例已创建：${result.workspace}\n刷新页面，在右上角选择“${process.argv[3] || "完整业务演示案例"}”。\n全部记录由业务接口逐步生成，原工作区保持独立。`,
    );
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}

import { pathToFileURL } from "node:url";

/** Load a complete, isolated case through the same HTTP commands used by the UI. */
export async function seedDemo(
  base = "http://127.0.0.1:8080",
  name = "完整业务演示案例",
) {
  const tokens = new Map();
  let workspace;
  async function request(actor, path, body) {
    if (!tokens.has(actor)) {
      const response = await fetch(`${base}/api/demo/session`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ actorId: actor }),
      });
      if (!response.ok) throw new Error(`登录失败：${response.status}`);
      tokens.set(actor, (await response.json()).token);
    }
    const response = await fetch(`${base}/api/demo${path}`, {
      method: body ? "POST" : "GET",
      headers: {
        Authorization: `Bearer ${tokens.get(actor)}`,
        "Content-Type": "application/json",
      },
      ...(body ? { body: JSON.stringify(body) } : {}),
    });
    const data = await response.json();
    if (!response.ok)
      throw new Error(`${path}: ${data.error?.message || response.status}`);
    return data;
  }
  workspace = (await request("ADMIN", "/workspaces", { name })).id;
  const state = () => request("ADMIN", `/state?workspace=${workspace}`);
  async function cmd(actor, action, payload = {}) {
    const s = await state();
    return (
      await request(actor, `/commands?workspace=${workspace}`, {
        action,
        payload,
        commandId: crypto.randomUUID(),
        runEpoch: s.epoch,
        expectedRevision: s.revision,
      })
    ).data;
  }
  async function approve(id) {
    await cmd("PLANNER", "plan.submit", { id });
    await cmd("REVIEWER_L1", "plan.approve", { id });
  }
  async function run(planId, name, scenario = "BASE") {
    const r = await cmd("DIRECTOR", "run.create", { planId, name, scenario });
    return cmd("DIRECTOR", "run.finish", { id: r.id });
  }
  async function course(domain, requestId = "") {
    const c = await cmd("AUTHOR", "course.create", {
      domain,
      name: domain === "OPERATION" ? "泵组操作与异常识别" : "泵组维修技能培训",
      requestId,
    });
    await cmd("AUTHOR", "course.submit", { id: c.id });
    await cmd("INSTRUCTOR", "course.approve", { id: c.id });
    await cmd("AUTHOR", "course.publish", { id: c.id });
    for (let i = 0; i < 30; i++) {
      await new Promise((resolve) => setTimeout(resolve, 150));
      const current = (await state()).courses.find((x) => x.id === c.id);
      if (current.status === "PUBLISHED") return c;
      if (current.status === "BUILD_FAILED") throw new Error("课程发布失败");
    }
    throw new Error("等待发布超时");
  }
  async function train(c, errors = 0, helps = 0) {
    const assignment = await cmd("INSTRUCTOR", "training.assign", {
      courseId: c.id,
    });
    const a = await cmd("LEARNER_A", "training.start", {
      assignmentId: assignment.id,
      mode: "GUIDED",
      scope: "INDIVIDUAL",
    });
    for (let i = 0; i < errors; i++)
      await cmd("LEARNER_A", "training.action", {
        id: a.id,
        kind: "PASS",
        target: "VALVE-01",
      });
    for (let i = 0; i < helps; i++)
      await cmd("LEARNER_A", "training.action", { id: a.id, kind: "HELP" });
    for (const step of c.steps)
      await cmd("LEARNER_A", "training.action", {
        id: a.id,
        stepId: step.id,
        target: "PUMP-01",
        kind: "PASS",
      });
    await cmd("INSTRUCTOR", "training.confirm", { id: a.id });
    return a;
  }
  const task = await cmd("PLANNER", "task.create", {
    name: "演示船A · 泵组维修保障",
    deadline: 270,
    importance: 80,
  });
  const p0 = await cmd("PLANNER", "plan.create", {
    taskId: task.id,
    templateId: "TPL-PLAN-01",
  });
  await approve(p0.id);
  const requestTraining = await cmd("PLANNER", "preparation.request", {
    id: p0.id,
  });
  const c1 = await course("OPERATION");
  const a1 = await train(c1);
  const c2 = await course("MAINTENANCE", requestTraining.id);
  const a2 = await train(c2, 2, 1);
  await cmd("LEARNER_A", "training.feedback", {
    id: a2.id,
    rating: 4,
    comment: "建议增加部件识别与装配前检查提示。",
  });
  await cmd("PLANNER", "history.import");
  await cmd("PLANNER", "lifecycle.calculate", { cycle: 6 });
  const baseRun = await run(p0.id, "基线演练 · 正常到货");
  const delayRun = await run(p0.id, "导调演练 · 备件延迟", "DELAY");
  const branch = await cmd("DIRECTOR", "run.branch", {
    planId: p0.id,
    parentId: delayRun.id,
    name: "T+60分支 · 加急优化",
  });
  await cmd("DIRECTOR", "run.finish", { id: branch.id });
  await cmd("PLANNER", "sensitivity.calculate", { id: p0.id });
  await cmd("PLANNER", "evaluation.calculate", {
    algorithm: "AHP",
    input: {
      matrix: [
        [1, 5 / 3, 2.5],
        [0.6, 1, 1.5],
        [0.4, 2 / 3, 1],
      ],
      scores: [80, 90, 100],
    },
  });
  const p1 = await cmd("PLANNER", "plan.revise", { id: p0.id, version: "P1" });
  await approve(p1.id);
  await run(p1.id, "P1 · 施工前验证");
  const execution = await cmd("PLANNER", "execution.create", { planId: p1.id });
  for (const action of [
    "execution.ack",
    "execution.prepare",
    "execution.report",
  ])
    await cmd("WORKER", action, { id: execution.id });
  for (let i = 1; i <= 4; i++)
    await cmd("INSPECTOR", "inspection.submit", {
      id: execution.id,
      item: i,
      passed: i !== 3,
    });
  await cmd("WORKER", "rectification.submit", {
    id: execution.id,
    comment: "校正装配状态，完成10分钟模拟整改。",
  });
  await cmd("INSPECTOR", "inspection.recheck", {
    id: execution.id,
    item: 3,
    passed: true,
  });
  await cmd("WORKER", "execution.finish", { id: execution.id });
  await cmd("PLANNER", "comparison.create", {
    executionId: execution.id,
    planId: p1.id,
  });
  const p2 = await cmd("PLANNER", "plan.revise", { id: p1.id, version: "P2" });
  await approve(p2.id);
  const p2Run = await run(p2.id, "P2 · 改进方案验证");
  const archive = await cmd("PLANNER", "archive.create", { planId: p2.id });
  await cmd("PLANNER", "archive.submit", { id: archive.id });
  await cmd("REVIEWER_L1", "archive.review", {
    id: archive.id,
    approved: true,
  });
  await cmd("REVIEWER_L2", "archive.review", {
    id: archive.id,
    approved: false,
    comment: "请补充改进方案验证依据。",
  });
  await cmd("PLANNER", "archive.supplement", {
    id: archive.id,
    comment: "P2增加装配前检查并提前备件，演练工期250分钟、费用1025。",
  });
  await cmd("PLANNER", "archive.submit", { id: archive.id });
  for (let i = 1; i <= 3; i++)
    await cmd(`REVIEWER_L${i}`, "archive.review", {
      id: archive.id,
      approved: true,
    });
  await cmd("PLANNER", "archive.publish", { id: archive.id });
  return {
    workspace,
    p0: p0.id,
    p1: p1.id,
    p2: p2.id,
    c1: c1.id,
    c2: c2.id,
    a1: a1.id,
    a2: a2.id,
    execution: execution.id,
    archive: archive.id,
    baseRun: baseRun.id,
    delayRun: delayRun.id,
    branch: branch.id,
    p2Run: p2Run.id,
  };
}

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

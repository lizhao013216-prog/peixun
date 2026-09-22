import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const base = "http://127.0.0.1:18092";
const server = spawn("java", ["-jar", "target/peixun-demo-1.0.0.jar"], {
  cwd: path.join(root, "backend"),
  env: {
    ...process.env,
    PORT: "18092",
    DB_URL: `jdbc:h2:mem:p7-routes-${Date.now()};DB_CLOSE_DELAY=-1`,
  },
  stdio: "ignore",
});
let workspace = "";

async function token(actor) {
  const response = await fetch(`${base}/api/demo/session`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ actorId: actor }),
  });
  assert.equal(response.status, 200);
  return (await response.json()).token;
}

async function request(auth, url, body) {
  const response = await fetch(`${base}/api/demo${url}`, {
    method: body ? "POST" : "GET",
    headers: {
      Authorization: `Bearer ${auth}`,
      ...(body ? { "Content-Type": "application/json" } : {}),
    },
    ...(body ? { body: JSON.stringify(body) } : {}),
  });
  const data = (response.headers.get("content-type") || "").includes("json")
    ? await response.json()
    : await response.text();
  assert.equal(response.status, 200, `${url}: ${JSON.stringify(data)}`);
  return data;
}

async function state(auth, domain = "") {
  return request(
    auth,
    `/state?workspace=${encodeURIComponent(workspace)}${domain ? `&domain=${domain}` : ""}`,
  );
}

async function command(auth, domain, action, payload) {
  const current = await state(auth, domain);
  const result = await request(
    auth,
    `/commands?workspace=${encodeURIComponent(workspace)}&domain=${domain}`,
    {
      action,
      payload,
      commandId: crypto.randomUUID(),
      runEpoch: current.epoch,
      expectedRevision: current.revision,
    },
  );
  return result.data;
}

function sceneObject(id, name, assetId, x, y) {
  return {
    id,
    name,
    assetRef: { id: assetId, version: 1 },
    x,
    y,
    initialState: { status: "READY" },
  };
}

async function createProject(admin, domain, config) {
  const project = await command(admin, domain, "simulationProject.create", {
    domain,
    name: config.name,
    purpose: config.purpose,
    linkageMode: config.linkageMode,
  });
  await command(admin, domain, "simulationProject.save", {
    id: project.id,
    expectedEditRevision: project.editRevision,
    name: config.name,
    purpose: config.purpose,
    scene: {
      objects: config.objects,
      environment: {
        weather: "晴",
        light: "日间",
        camera: "总览",
        material: "标准",
      },
    },
  });
  return project;
}

async function publishCourse(
  admin,
  instructor,
  domain,
  template,
  name,
  target,
  stepCount,
) {
  let course = await command(admin, domain, "courseware.create", {
    domain,
    curriculumName: `${name}课程`,
    curriculumObjective: `完成${name}的配置驱动训练`,
    unitName: "第一课时",
    name,
    description: `P7空工作区${stepCount}步完整路线。`,
    templateId: template.id,
    stepCount,
  });
  const points =
    stepCount === 5 ? [20, 20, 20, 20, 20] : [10, 20, 20, 20, 20, 10];
  course = await command(admin, domain, "courseware.save", {
    id: course.id,
    expectedEditRevision: course.editRevision,
    name: course.name,
    description: course.description,
    mode: "GUIDED",
    steps: course.steps.map((step, index) => ({
      ...step,
      name: `第${index + 1}步`,
      description: `选择${target}并确认第${index + 1}阶段。`,
      target,
      actionId: "CONFIRM",
      actionLabel: "确认当前对象",
      parameters: {},
      precondition: null,
      completion: { kind: "ACTION_SUCCEEDED" },
      expectedResult: `第${index + 1}阶段已完成。`,
      points: points[index],
      mode: "GUIDED",
    })),
    scoreRule: { total: 100, errorPenalty: 5, helpPenalty: 2 },
  });
  await command(admin, domain, "courseware.submit", { id: course.id });
  await command(instructor, domain, "courseware.approve", { id: course.id });
  return command(admin, domain, "courseware.publish", { id: course.id });
}

async function completeConfiguredTraining(
  instructor,
  learner,
  domain,
  course,
  learnerId,
  target,
) {
  const assignment = await command(instructor, domain, "training.assign", {
    courseId: course.id,
    learnerId,
  });
  let attempt = await command(learner, domain, "training.start", {
    assignmentId: assignment.id,
    mode: "GUIDED",
    scope: "INDIVIDUAL",
  });
  for (const step of course.publishedSnapshot.steps) {
    attempt = await command(learner, domain, "training.action", {
      id: attempt.id,
      kind: "PASS",
      stepId: step.id,
      target,
      actionId: "CONFIRM",
      parameters: {},
    });
    attempt = await command(learner, domain, "training.continue", {
      id: attempt.id,
    });
  }
  assert.equal(attempt.status, "COMPLETED");
  assert.equal(attempt.score, 100);
  await command(instructor, domain, "training.confirm", { id: attempt.id });
  const current = await state(instructor, domain);
  assert.ok(
    current.trainingArchives.some((item) => item.attemptId === attempt.id),
    `${domain}训练没有形成归档`,
  );
  return { assignment, attempt };
}

try {
  let ready = false;
  for (let index = 0; index < 200; index++) {
    try {
      if ((await fetch(`${base}/api/demo/health`)).ok) {
        ready = true;
        break;
      }
    } catch {}
    await new Promise((resolve) => setTimeout(resolve, 150));
  }
  assert.ok(ready, "P7三路线测试服务未启动");
  assert.equal((await fetch(`${base}/api/demo/bootstrap`)).status, 200);
  const admin = await token("ADMIN");
  const instructor = await token("INSTRUCTOR");
  const learnerA = await token("LEARNER_A");
  const learnerB = await token("LEARNER_B");
  const created = await request(admin, "/workspaces", {
    name: "P7三系统空工作区验收",
  });
  workspace = created.id;
  const initial = await state(admin);
  const initialFormalPlans = initial.plans.length;

  // Route 1: operation resource -> linkage -> preview -> template -> five steps -> archive.
  const operationProject = await createProject(admin, "OPERATION", {
    name: "P7操作联动工程",
    purpose: "泵阀联动与学员操作",
    linkageMode: "SIGNAL_GRAPH",
    objects: [
      sceneObject("PUMP-01", "操作泵组", "ASSET-PUMP", 25, 55),
      sceneObject("VALVE-01", "控制阀门", "ASSET-VALVE", 48, 28),
      sceneObject("SENSOR-01", "状态传感器", "ASSET-SENSOR", 73, 28),
    ],
  });
  let operationState = await state(admin, "OPERATION");
  const currentOperationProject = operationState.simulationProjects.find(
    (item) => item.id === operationProject.id,
  );
  const topology = operationState.topologies.find(
    (item) => item.id === currentOperationProject.topologyRef.id,
  );
  await command(admin, "OPERATION", "simulationTopology.save", {
    projectId: operationProject.id,
    expectedEditRevision: topology.editRevision,
    connections: [
      {
        id: "LINK-READY",
        source: { objectId: "VALVE-01", port: "ready" },
        target: { objectId: "PUMP-01", port: "readyInput" },
        valueType: "BOOLEAN",
      },
    ],
    rules: [
      {
        id: "RULE-START",
        kind: "PRECONDITION",
        trigger: { objectId: "PUMP-01", actionId: "START" },
        conditions: [
          {
            objectId: "PUMP-01",
            field: "readyInput",
            operator: "EQ",
            value: true,
          },
        ],
        effects: [
          { objectId: "PUMP-01", field: "status", value: "RUNNING" },
        ],
        rejectMessage: "阀门未就绪，当前不能启动",
      },
    ],
  });
  let preview = await command(admin, "OPERATION", "simulationPreview.start", {
    projectId: operationProject.id,
  });
  preview = await command(admin, "OPERATION", "simulationPreview.action", {
    id: preview.id,
    objectId: "PUMP-01",
    actionId: "START",
    parameters: {},
  });
  assert.equal(preview.lastEvent.result, "REJECTED");
  await command(admin, "OPERATION", "simulationPreview.action", {
    id: preview.id,
    objectId: "VALVE-01",
    actionId: "SET_READY",
    parameters: { value: true },
  });
  preview = await command(admin, "OPERATION", "simulationPreview.action", {
    id: preview.id,
    objectId: "PUMP-01",
    actionId: "START",
    parameters: {},
  });
  assert.equal(preview.states["PUMP-01"].status, "RUNNING");
  const operationTemplate = await command(
    admin,
    "OPERATION",
    "simulationTemplate.publish",
    {
      projectId: operationProject.id,
      usageInstructions: "P7操作五步课件环境",
      sharedWith: [],
    },
  );
  const operationCourse = await publishCourse(
    admin,
    instructor,
    "OPERATION",
    operationTemplate,
    "P7泵组操作五步课件",
    "PUMP-01",
    5,
  );
  const operationTraining = await completeConfiguredTraining(
    instructor,
    learnerA,
    "OPERATION",
    operationCourse,
    "LEARNER_A",
    "PUMP-01",
  );
  assert.equal(operationTraining.attempt.events.length, 5);

  // Route 2: maintenance tools -> no-link preview -> six steps -> archive -> retraining.
  const maintenanceProject = await createProject(admin, "MAINTENANCE", {
    name: "P7维修工具检查工程",
    purpose: "工具与检测对象训练",
    linkageMode: "NONE",
    objects: [sceneObject("TOOL-01", "标准工具组", "ASSET-TOOL", 40, 45)],
  });
  let maintenancePreview = await command(
    admin,
    "MAINTENANCE",
    "simulationPreview.start",
    { projectId: maintenanceProject.id },
  );
  maintenancePreview = await command(
    admin,
    "MAINTENANCE",
    "simulationPreview.action",
    {
      id: maintenancePreview.id,
      objectId: "TOOL-01",
      actionId: "CONFIRM",
      parameters: {},
    },
  );
  assert.equal(maintenancePreview.lastEvent.result, "SUCCEEDED");
  const maintenanceTemplate = await command(
    admin,
    "MAINTENANCE",
    "simulationTemplate.publish",
    {
      projectId: maintenanceProject.id,
      usageInstructions: "P7维修六步课件环境",
      sharedWith: [],
    },
  );
  const maintenanceCourse = await publishCourse(
    admin,
    instructor,
    "MAINTENANCE",
    maintenanceTemplate,
    "P7泵组维修六步课件",
    "TOOL-01",
    6,
  );
  const maintenanceTraining = await completeConfiguredTraining(
    instructor,
    learnerB,
    "MAINTENANCE",
    maintenanceCourse,
    "LEARNER_B",
    "TOOL-01",
  );
  const learningIssue = await command(
    learnerB,
    "MAINTENANCE",
    "issue.create",
    {
      id: maintenanceTraining.attempt.id,
      type: "LEARNING",
      name: "维修检查需要补训",
      description: "增加一次工具检查补训。",
    },
  );
  await command(instructor, "MAINTENANCE", "issue.route", {
    id: learningIssue.id,
  });
  const maintenanceAfter = await state(instructor, "MAINTENANCE");
  assert.equal(
    maintenanceAfter.attempts.find(
      (item) => item.id === maintenanceTraining.attempt.id,
    ).score,
    100,
  );
  assert.ok(
    maintenanceAfter.assignments.some(
      (item) => item.sourceIssueId === learningIssue.id,
    ),
  );

  // Route 3: support resource -> course -> isolated planning and delay -> score -> archive.
  const supportProject = await createProject(admin, "SUPPORT", {
    name: "P7保障教学工程",
    purpose: "保障方案与延迟事件处置",
    linkageMode: "NONE",
    objects: [sceneObject("PUMP-01", "保障训练泵组", "ASSET-PUMP", 40, 45)],
  });
  const supportTemplate = await command(
    admin,
    "SUPPORT",
    "simulationTemplate.publish",
    {
      projectId: supportProject.id,
      usageInstructions: "P7保障教学课件环境",
      sharedWith: [],
    },
  );
  const supportCourse = await publishCourse(
    admin,
    instructor,
    "SUPPORT",
    supportTemplate,
    "P7保障方案与事件处置课件",
    "PUMP-01",
    6,
  );
  const supportAssignment = await command(
    instructor,
    "SUPPORT",
    "training.assign",
    { courseId: supportCourse.id, learnerId: "LEARNER_A" },
  );
  let supportAttempt = await command(
    learnerA,
    "SUPPORT",
    "training.start",
    {
      assignmentId: supportAssignment.id,
      mode: "GUIDED",
      scope: "INDIVIDUAL",
    },
  );
  for (const [action, payload] of [
    ["training.support.confirm", {}],
    [
      "training.support.resource",
      { resourceId: "E1", capacity: 2, available: true },
    ],
    ["training.support.plan", { operationId: "T01", duration: 30 }],
    ["training.support.calculate", {}],
    ["training.support.delay", { arrivalTime: 180 }],
    [
      "training.support.resolve",
      { strategy: "EXPEDITE", arrivalTime: 135, transportCost: 140 },
    ],
    [
      "training.support.submit",
      { reflection: "延迟后采用加急方案并分别记录工期、费用和学习成绩。" },
    ],
  ])
    supportAttempt = await command(learnerA, "SUPPORT", action, {
      id: supportAttempt.id,
      ...payload,
    });
  assert.equal(supportAttempt.status, "COMPLETED");
  assert.equal(supportAttempt.evaluation.learningScore, 100);
  assert.equal(supportAttempt.supportTraining.result.finish, 240);
  assert.equal(supportAttempt.supportTraining.delayedResult.finish, 300);
  assert.equal(supportAttempt.supportTraining.resolvedResult.finish, 255);
  await command(instructor, "SUPPORT", "training.confirm", {
    id: supportAttempt.id,
  });
  const supportAfter = await state(instructor, "SUPPORT");
  assert.ok(
    supportAfter.trainingArchives.some(
      (item) => item.attemptId === supportAttempt.id,
    ),
  );
  assert.equal(supportAfter.plans.length, initialFormalPlans);

  const report = await request(
    instructor,
    `/reports/attempts/${supportAttempt.id}.md?workspace=${encodeURIComponent(workspace)}`,
  );
  assert.match(report, new RegExp(supportAttempt.id));
  assert.match(report, /P7保障方案与事件处置课件/);
  console.log(
    "PASS: P7 one empty workspace completed operation linkage + 5-step archive, maintenance NONE + 6-step archive/retraining, and support 240/300/255 planning + separated 100-point archive/report.",
  );
} finally {
  server.kill("SIGTERM");
}

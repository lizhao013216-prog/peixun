package cn.peixun.demo;

import static cn.peixun.demo.Json.*;

import com.fasterxml.jackson.databind.node.*;

public final class Seed {
  public static ObjectNode create(String id, String name, int epoch) {
    ObjectNode s =
        obj(
            "id",
            id,
            "name",
            name,
            "epoch",
            epoch,
            "revision",
            0,
            "seedVersion",
            "demo-1.0",
            "projectName",
            "演示船A · 通用泵组维修保障",
            "equipmentId",
            "EQ-DEMO-01",
            "createdAt",
            java.time.Instant.now().toString(),
            "settings",
            obj(
                "platformProfile",
                "SUCCESS",
                "cycleMonths",
                6,
                "highIntensityMonth",
                0,
                "assessmentScope",
                "INDIVIDUAL"),
            "topology",
            obj(
                "id",
                "TOPO-01",
                "threshold",
                0.4,
                "valveReady",
                false,
                "startRequest",
                false,
                "sensor",
                0.8,
                "pumpState",
                "READY",
                "version",
                1,
                "connections",
                arr(
                    "CTRL.startRequest → PUMP.start",
                    "VALVE.isReady → PUMP.ready",
                    "SENSOR.value → CTRL.alarm")),
            "scene",
            obj(
                "weather",
                "晴",
                "light",
                "日间",
                "material",
                "金属 · 拉丝钢",
                "camera",
                "总览",
                "lod",
                1,
                "rigidBody",
                "DYNAMIC",
                "lockedAxes",
                arr("Y", "Z"),
                "collisionFeedback",
                "颜色与提示音",
                "uiTemplate",
                "引导训练",
                "keyframes",
                arr(obj("time", 0, "value", 0), obj("time", 1000, "value", 90)),
                "script",
                arr("点击对象", "检查前置条件", "播放动作", "显示反馈", "完成步骤"),
                "effect",
                "状态光环",
                "crew",
                true,
                "revision",
                1));
    for (String key :
        new String[] {
          "assets",
          "courses",
          "attempts",
          "assignments",
          "tasks",
          "plans",
          "resources",
          "runs",
          "orders",
          "executions",
          "archives",
          "events",
          "feedback",
          "subplans",
          "analyses",
          "requests",
          "issues",
          "jobs",
          "stations",
          "templates",
          "histories",
          "documents",
          "comparisons",
          "evaluations"
        }) s.putArray(key);
    String[][] assets = {
      {"ASSET-PUMP", "通用泵组总成", "模型", "FBX", "PUMP-01"},
      {"ASSET-VALVE", "电动阀门组件", "模型", "STEP", "VALVE-01"},
      {"ASSET-CTRL", "控制单元", "模型", "OBJ", "CTRL-01"},
      {"ASSET-SENSOR", "状态传感器", "模型", "FBX", "SENSOR-01"},
      {"ASSET-TOOL", "标准工具组", "模型", "FBX", "TOOL-01"},
      {"ASSET-WORKSHOP", "维修工位场景", "场景", "FBX", "SCENE-01"},
      {"ASSET-MANUAL", "泵组示例任务说明", "文档", "MD", "PUMP-01"},
      {"ASSET-DIAGRAM", "结构关系示意图", "图像", "SVG", "PUMP-01"},
      {"ASSET-ANIMATION", "泵组开合动作", "动画", "CLIP", "PUMP-01"},
      {"ASSET-VIDEO", "操作讲解视频能力", "视频", "MP4", "PUMP-01"},
      {"ASSET-AUDIO", "教官提示音能力", "音频", "WAV", "PUMP-01"},
      {"ASSET-ENV", "车间环境配置", "环境", "SCENE", "SCENE-01"}
    };
    for (String[] a : assets)
      s.withArray("assets")
          .add(
              obj(
                  "id",
                  a[0],
                  "name",
                  a[1],
                  "category",
                  a[2],
                  "format",
                  a[3],
                  "componentId",
                  a[4],
                  "status",
                  "APPROVED",
                  "version",
                  1,
                  "sourceType",
                  "SEED_DATA",
                  "implementation",
                  "SIMULATED_PLATFORM",
                  "owner",
                  "AUTHOR",
                  "sizeLabel",
                  a[2].equals("模型") ? "模拟模型" : "示例资源",
                  "description",
                  "预置演示资源，可引用、派生、编辑及验证业务流程。"));
    String[][] people = {
      {"E1", "陈工", "电气", "2"},
      {"W1", "周工", "仓储", "1"},
      {"M1", "李工", "维修", "1.5"},
      {"M2", "王工", "维修", "1.5"},
      {"Q1", "赵工", "质检", "2"},
      {"L1", "刘工", "交接", "2"}
    };
    for (String[] p : people)
      s.withArray("resources")
          .add(
              obj(
                  "id",
                  p[0],
                  "name",
                  p[1],
                  "category",
                  "人员",
                  "skill",
                  p[2],
                  "capacity",
                  1,
                  "available",
                  true,
                  "rate",
                  Double.parseDouble(p[3]),
                  "organization",
                  "本地维修单位",
                  "start",
                  0,
                  "end",
                  360,
                  "qualification",
                  "演示技能记录",
                  "trainingConfirmed",
                  !p[0].equals("M1")));
    for (String[] p :
        new String[][] {
          {"TOOL-01", "通用维修工具", "器材"},
          {"TEST-01", "检测工位", "器材"},
          {"WH-LOCAL", "本地备件库", "物料"},
          {"WH-REMOTE", "远端供应库", "物料"}
        })
      s.withArray("resources")
          .add(
              obj(
                  "id",
                  p[0],
                  "name",
                  p[1],
                  "category",
                  p[2],
                  "capacity",
                  p[0].equals("WH-REMOTE") ? 4 : p[0].equals("WH-LOCAL") ? 0 : 1,
                  "available",
                  true,
                  "rate",
                  0,
                  "organization",
                  p[0].equals("WH-REMOTE") ? "远端供应单位" : "本地维修单位",
                  "start",
                  0,
                  "end",
                  360));
    int fi = 0;
    for (String n :
        new String[] {"维修场地", "仓储设施", "码头", "泊位", "容载能力", "潮汐窗口", "吊装设施", "供电供气", "坞期窗口"})
      s.withArray("resources")
          .add(
              obj(
                  "id",
                  "FAC-" + (++fi),
                  "name",
                  n,
                  "category",
                  "设施",
                  "capacity",
                  1,
                  "available",
                  true,
                  "rate",
                  0,
                  "organization",
                  "本地维修单位",
                  "start",
                  0,
                  "end",
                  360,
                  "capability",
                  "DEMO_COMPATIBLE"));
    s.withArray("templates")
        .add(
            obj(
                "id",
                "TPL-PLAN-01",
                "name",
                "通用泵组检修预案",
                "type",
                "PLAN",
                "equipmentType",
                "PUMP",
                "taskType",
                "维修",
                "status",
                "PUBLISHED",
                "description",
                "八工序、六类准备、双维修人员协同"));
    s.withArray("templates")
        .add(
            obj(
                "id",
                "TPL-PLAN-02",
                "name",
                "电气系统例行检查",
                "type",
                "PLAN",
                "equipmentType",
                "ELECTRIC",
                "taskType",
                "巡检",
                "status",
                "PUBLISHED",
                "description",
                "用于展示硬条件过滤"));
    s.withArray("documents")
        .add(
            obj(
                "id",
                "DOC-01",
                "name",
                "设备构型与BOM",
                "type",
                "构型/BOM",
                "version",
                1,
                "sourceType",
                "SEED_DATA",
                "content",
                "PUMP-01 → 壳体、密封件、驱动组件；所有参数仅用于演示。"));
    s.withArray("documents")
        .add(
            obj(
                "id",
                "DOC-02",
                "name",
                "维修说明与检查标准",
                "type",
                "技术资料",
                "version",
                1,
                "sourceType",
                "SEED_DATA",
                "content",
                "按十步课程完成模拟操作；四项检查中每项结论必须独立记录。"));
    return s;
  }

  public static ArrayNode operations(boolean p2) {
    ArrayNode a = arr();
    a.add(op("T01", "任务检查", 30, arr(), arr("E1")));
    a.add(op("T02", "备件发运准备", 30, arr(), arr("W1")));
    a.add(op("T03", "模拟隔离确认", 30, arr("T01"), arr("E1")));
    a.add(op("T04", "模拟拆解", 60, arr("T03"), arr("M1", "M2", "TOOL-01")));
    if (p2) a.add(op("T04Q", "装配前检查", 10, arr("T04"), arr("Q1")));
    a.add(op("T05", "更换示例部件", 30, arr(p2 ? "T04Q" : "T04"), arr("M1", "M2", "TOOL-01")));
    a.add(op("T06", "模拟装配", 45, arr("T05"), arr("M1", "M2", "TOOL-01")));
    a.add(op("T07", "检测确认", 30, arr("T06"), arr("E1", "Q1", "TEST-01")));
    a.add(op("T08", "交接归档", 15, arr("T07"), arr("L1")));
    return a;
  }

  private static ObjectNode op(
      String id, String name, int duration, ArrayNode pred, ArrayNode resources) {
    return obj(
        "id",
        id,
        "name",
        name,
        "duration",
        duration,
        "predecessors",
        pred,
        "resources",
        resources,
        "materialQuantity",
        id.equals("T05") ? 2 : 0);
  }

  public static ArrayNode steps(String domain) {
    String[] names =
        domain.equals("OPERATION")
            ? new String[] {"检查场景", "确认模拟状态", "发出启动请求", "读取指标", "识别模拟异常", "发出停机请求", "确认复位", "提交记录"}
            : new String[] {
              "阅读并确认任务卡",
              "确认模拟准备状态",
              "识别目标部件",
              "选择示例工具",
              "执行模拟拆卸",
              "记录检查结果",
              "选择示例替换件",
              "执行模拟安装",
              "完成模拟检测",
              "提交训练记录"
            };
    ArrayNode a = arr();
    for (int i = 0; i < names.length; i++)
      a.add(
          obj(
              "id",
              (domain.equals("OPERATION") ? "O" : "R") + String.format("%02d", i + 1),
              "name",
              names[i],
              "description",
              "确认当前对象和前置步骤后执行模拟操作。",
              "target",
              "PUMP-01",
              "score",
              100.0 / names.length,
              "order",
              i + 1));
    return a;
  }

  public static ArrayNode accounts() {
    return arr(
        obj("id", "ADMIN", "name", "演示管理员", "role", "ADMIN"),
        obj("id", "AUTHOR", "name", "林悦 · 制作员", "role", "AUTHOR"),
        obj("id", "INSTRUCTOR", "name", "陈老师 · 教员", "role", "INSTRUCTOR"),
        obj("id", "LEARNER_A", "name", "李工 · 学员A", "role", "LEARNER"),
        obj("id", "LEARNER_B", "name", "王工 · 学员B", "role", "LEARNER"),
        obj("id", "PLANNER", "name", "张工 · 筹划员", "role", "PLANNER"),
        obj("id", "DIRECTOR", "name", "李主任 · 导调员", "role", "DIRECTOR"),
        obj("id", "WORKER", "name", "王工 · 施工员", "role", "WORKER"),
        obj("id", "INSPECTOR", "name", "赵工 · 验收员", "role", "INSPECTOR"),
        obj("id", "REVIEWER_L1", "name", "项目审核员", "role", "REVIEWER_L1"),
        obj("id", "REVIEWER_L2", "name", "企业审核员", "role", "REVIEWER_L2"),
        obj("id", "REVIEWER_L3", "name", "专家审核员", "role", "REVIEWER_L3"));
  }
}

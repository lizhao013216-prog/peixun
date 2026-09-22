package cn.peixun.demo;

import static cn.peixun.demo.BusinessException.require;
import static cn.peixun.demo.Json.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.time.Instant;

public final class TrainingModule {
  private static final VirtualPlatformPort PLATFORM = new MockVirtualPlatformAdapter();

  public static ObjectNode apply(ObjectNode s, String action, ObjectNode p, String actor) {
    String target = p.path("id").asText();
    switch (action) {
      case "asset.import" -> {
        String name = p.path("name").asText().trim();
        require(!name.isBlank(), "请填写资源名称");
        String assetId = id("ASSET");
        String ownerSystem = p.path("ownerSystem").asText();
        TrainingDomain.require(ownerSystem);
        ObjectNode a =
            obj(
                "id",
                assetId,
                "familyId",
                assetId,
                "name",
                name,
                "category",
                p.path("category").asText("模型"),
                "format",
                p.path("format").asText("FBX"),
                "componentId",
                p.path("componentId").asText("GENERIC"),
                "status",
                "DRAFT",
                "version",
                1,
                "editRevision",
                1,
                "ownerSystem",
                ownerSystem,
                "visibility",
                "PRIVATE",
                "sharedWith",
                arr(),
                "owner",
                actor,
                "sourceType",
                p.has("blobRef") ? "UPLOADED_FILE" : "SIMULATED_PLATFORM",
                "implementation",
                "SIMULATED_PLATFORM",
                "description",
                p.path("description").asText(""),
                "sizeLabel",
                p.path("sizeLabel").asText("模拟导入"),
                "actions",
                arr("CONFIRM", "READ_STATE", "RECORD_CHECK"),
                "ports",
                arr());
        SimulationCapabilities.enrichAsset(a);
        if (p.has("blobRef")) a.set("blobRef", p.get("blobRef"));
        s.withArray("assets").add(a);
        return a;
      }
      case "asset.process" -> {
        ObjectNode a = find(s, "assets", target);
        require(!a.path("status").asText().equals("PROCESSING"), "资源正在处理中");
        ObjectNode derived = a.deepCopy();
        derived
            .put("id", id("ASSET"))
            .put("parentId", target)
            .put("version", a.path("version").asInt() + 1)
            .put("editRevision", 1)
            .put("status", "PROCESSING")
            .put("name", a.path("name").asText() + " · 派生")
            .put("owner", actor);
        derived.set(
            "processParameters",
            obj(
                "lodLevels",
                3,
                "texture",
                p.path("texture").asText("优化"),
                "operation",
                p.path("operation").asText("轻量化")));
        s.withArray("assets").add(derived);
        job(s, "ASSET", derived.path("id").asText());
        return derived;
      }
      case "asset.share" -> {
        ObjectNode a = find(s, "assets", target);
        require(p.path("sharedWith").isArray(), "共享范围必须是系统列表");
        ArrayNode sharedWith = arr();
        for (JsonNode item : p.withArray("sharedWith")) {
          String domain = item.asText();
          TrainingDomain.require(domain);
          require(!domain.equals(a.path("ownerSystem").asText()), "素材所属系统无需重复共享");
          if (!sharedWith.toString().contains("\"" + domain + "\"")) sharedWith.add(domain);
        }
        a.set("sharedWith", sharedWith);
        a.put("visibility", sharedWith.isEmpty() ? "PRIVATE" : "SHARED");
        a.put("editRevision", a.path("editRevision").asInt(1) + 1);
        return a;
      }
      case "asset.approve" -> {
        ObjectNode a = find(s, "assets", target);
        require(a.path("status").asText().matches("DRAFT|PENDING_REVIEW"), "当前资源不能审核");
        a.put("status", "APPROVED");
        return a;
      }
      case "asset.disable" -> {
        ObjectNode a = find(s, "assets", target);
        a.put("status", "DISABLED");
        return a;
      }
      case "scene.save" -> {
        ObjectNode scene = (ObjectNode) s.get("scene");
        for (String k :
            new String[] {
              "weather",
              "light",
              "material",
              "camera",
              "rigidBody",
              "collisionFeedback",
              "uiTemplate",
              "effect"
            }) if (p.has(k)) scene.set(k, p.get(k));
        if (p.has("lod")) {
          require(p.path("lod").asInt() >= 1 && p.path("lod").asInt() <= 3, "LOD只支持1～3档");
          scene.set("lod", p.get("lod"));
        }
        if (p.has("keyframes")) {
          require(p.path("keyframes").isArray() && p.path("keyframes").size() >= 2, "至少需要两个关键帧");
          double last = -1;
          for (JsonNode frame : p.path("keyframes")) {
            require(frame.path("time").asDouble() >= last, "关键帧必须按时间排序");
            last = frame.path("time").asDouble();
          }
          scene.set("keyframes", p.get("keyframes"));
        }
        if (p.has("script")) {
          require(p.path("script").isArray() && p.path("script").size() >= 2, "脚本至少需要事件与动作节点");
          require(p.path("script").size() <= 20, "演示脚本最多20个节点");
          scene.set("script", p.get("script"));
        }
        if (p.has("lockedAxes")) scene.set("lockedAxes", p.get("lockedAxes"));
        scene.put("revision", scene.path("revision").asInt() + 1);
        return scene;
      }
      case "topology.save", "topology.signal" -> {
        ObjectNode t = (ObjectNode) s.get("topology");
        if (p.has("threshold")) {
          double threshold = p.path("threshold").asDouble();
          require(threshold >= 0 && threshold <= 1, "阈值必须在0～1之间");
          t.put("threshold", threshold);
          t.put("version", t.path("version").asInt() + 1);
        }
        if (p.has("sensor")) {
          double v = p.path("sensor").asDouble();
          require(v >= 0 && v <= 1, "传感器演示值范围0～1");
          t.put("sensor", v);
        }
        if (p.has("valveReady")) t.set("valveReady", p.get("valveReady"));
        String signal = p.path("signal").asText();
        if (signal.equals("START")) {
          require(t.path("valveReady").asBoolean(), "阀门未就绪，启动条件不满足");
          t.put("startRequest", true).put("pumpState", "RUNNING");
        }
        if (signal.equals("STOP")) t.put("startRequest", false).put("pumpState", "STOPPED");
        if (signal.equals("RESET")) {
          require(!t.path("startRequest").asBoolean(), "请先停止再复位");
          t.put("pumpState", "READY");
        }
        if (t.path("startRequest").asBoolean())
          t.put(
              "pumpState",
              t.path("sensor").asDouble() < t.path("threshold").asDouble() ? "ALARM" : "RUNNING");
        return t;
      }
      case "template.save" -> {
        ObjectNode t =
            obj(
                "id",
                id("TPL"),
                "name",
                p.path("name").asText("泵组联动系统模板"),
                "type",
                "OPERATION",
                "status",
                "PUBLISHED",
                "scene",
                s.get("scene").deepCopy(),
                "topology",
                s.get("topology").deepCopy(),
                "createdBy",
                actor);
        s.withArray("templates").add(t);
        return t;
      }
      case "template.apply" -> {
        ObjectNode t = find(s, "templates", target);
        require(t.has("scene"), "请选择系统模板");
        s.set("scene", t.get("scene").deepCopy());
        s.set("topology", t.get("topology").deepCopy());
        return t;
      }
      case "station.save" -> {
        String address = p.path("address").asText("mock://station/01");
        require(address.startsWith("mock://"), "演示台位仅支持mock地址");
        ObjectNode station =
            obj(
                "id",
                id("STATION"),
                "name",
                p.path("name").asText("模拟操作台位"),
                "protocol",
                p.path("protocol").asText("TCP"),
                "address",
                address,
                "mapping",
                p.path("mapping").asText("button.start → CTRL.startRequest"),
                "status",
                "DISCONNECTED");
        s.withArray("stations").add(station);
        return station;
      }
      case "station.connect" -> {
        ObjectNode station = find(s, "stations", target);
        station.put("status", p.path("connected").asBoolean(true) ? "CONNECTED" : "DISCONNECTED");
        return station;
      }
      case "station.signal" -> {
        ObjectNode station = find(s, "stations", target);
        require(station.path("status").asText().equals("CONNECTED"), "台位已断开");
        require(p.path("point").asText("button.start").equals("button.start"), "信号点未映射");
        return apply(s, "topology.signal", obj("signal", "START"), actor);
      }
      case "course.create" -> {
        String domain = p.path("domain").asText("MAINTENANCE");
        TrainingDomain trainingDomain = TrainingDomain.require(domain);
        require(trainingDomain.courseAuthoringEnabled(), "保障教学课件将在后续阶段开放，当前不能创建");
        String name = p.path("name").asText().trim();
        require(!name.isEmpty(), "请填写课程名称");
        ObjectNode c =
            obj(
                "id",
                id("COURSE"),
                "name",
                name,
                "domain",
                domain,
                "version",
                1,
                "status",
                "DRAFT",
                "createdBy",
                actor,
                "interactionVersion",
                2,
                "steps",
                Seed.steps(domain),
                "mode",
                p.path("mode").asText("GUIDED"),
                "description",
                p.path("description").asText(domain.equals("OPERATION") ? "掌握泵组场景确认、设备联动、异常识别与记录提交流程。" : "掌握维修准备、部件识别、模拟操作、检测与训练记录提交流程。"),
                "scene",
                s.get("scene").deepCopy(),
                "topology",
                s.get("topology").deepCopy(),
                "assets",
                arr("ASSET-PUMP", "ASSET-VALVE", "ASSET-CTRL", "ASSET-SENSOR"),
                "uiTemplate",
                "引导训练",
                "scoreRule",
                obj("errorPenalty", 5, "helpPenalty", 2));
        if (p.has("requestId") && !p.path("requestId").asText().isBlank()) {
          ObjectNode request = find(s, "requests", p.path("requestId").asText());
          request.put("status", "ACCEPTED");
          c.set("requestId", request.get("id"));
          c.set("planId", request.get("planId"));
        }
        s.withArray("courses").add(c);
        return c;
      }
      case "course.save" -> {
        ObjectNode c = find(s, "courses", target);
        require(c.path("status").asText().equals("DRAFT"), "请先创建草稿新版本再修改");
        for (String k :
            new String[] {"name", "description", "steps", "uiTemplate", "mode", "assets"})
          if (p.has(k)) c.set(k, p.get(k));
        validateCourse(c);
        c.set("scene", s.get("scene").deepCopy());
        c.set("topology", s.get("topology").deepCopy());
        return c;
      }
      case "course.submit" -> {
        ObjectNode c = find(s, "courses", target);
        require(c.path("status").asText().equals("DRAFT"), "仅草稿可以提交审核");
        validateCourse(c);
        require(c.path("steps").size() > 0, "课程缺少步骤");
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (JsonNode step : c.path("steps")) {
          require(
              !step.path("name").asText().isBlank() && ids.add(step.path("id").asText()),
              "步骤名称缺失或编号重复");
        }
        for (JsonNode aid : c.path("assets"))
          require(
              find(s, "assets", aid.asText()).path("status").asText().equals("APPROVED"),
              "存在未批准或已停用的依赖资源");
        c.put("status", "PENDING_REVIEW");
        return c;
      }
      case "course.approve" -> {
        ObjectNode c = find(s, "courses", target);
        require(c.path("status").asText().equals("PENDING_REVIEW"), "课程未处于待审核状态");
        require(!actor.equals(c.path("createdBy").asText()), "不能审核自己制作的课程，请切换教员身份");
        c.put("status", "APPROVED").put("reviewedBy", actor);
        return c;
      }
      case "course.return" -> {
        ObjectNode c = find(s, "courses", target);
        require(c.path("status").asText().equals("PENDING_REVIEW"), "课程不在待审状态");
        c.put("status", "DRAFT").put("reviewComment", p.path("comment").asText("请补充课程内容"));
        return c;
      }
      case "course.publish" -> {
        ObjectNode c = find(s, "courses", target);
        require(c.path("status").asText().matches("APPROVED|BUILD_FAILED"), "请先审核课程");
        c.put("status", "BUILDING");
        ObjectNode j = job(s, "COURSE", target);
        j.put("target", p.path("target").asText("BROWSER"));
        return j;
      }
      case "course.revise" -> {
        ObjectNode c = find(s, "courses", target);
        require(c.path("status").asText().equals("PUBLISHED"), "只能修订已发布课程");
        ObjectNode n = c.deepCopy();
        n.put("id", id("COURSE"))
            .put("parentId", target)
            .put("version", c.path("version").asInt() + 1)
            .put("status", "DRAFT")
            .put("createdBy", actor);
        s.withArray("courses").add(n);
        return n;
      }
      case "training.assign" -> {
        ObjectNode c = find(s, "courses", p.path("courseId").asText());
        require(c.path("status").asText().equals("PUBLISHED"), "课程尚未发布");
        require(
            c.path("interactionSchemaVersion").asInt() < 3,
            "交互契约V3课件将在P4接入配置驱动训练，当前不能分配给旧运行器");
        ObjectNode a =
            obj(
                "id",
                id("ASSIGN"),
                "courseId",
                c.get("id"),
                "domain",
                c.get("domain"),
                "courseVersion",
                c.get("version"),
                "name",
                c.get("name"),
                "learnerId",
                p.path("learnerId").asText("LEARNER_A"),
                "status",
                "ASSIGNED",
                "confirmed",
                false,
                "createdBy",
                actor);
        s.withArray("assignments").add(a);
        return a;
      }
      case "training.start" -> {
        ObjectNode assignment = find(s, "assignments", p.path("assignmentId").asText());
        ObjectNode c = find(s, "courses", assignment.path("courseId").asText());
        require(c.path("status").asText().equals("PUBLISHED"), "课程尚未发布");
        require(
            c.path("interactionSchemaVersion").asInt() < 3,
            "交互契约V3课件将在P4接入配置驱动训练，当前任务不能由旧运行器启动");
        require(
            actor.equals(assignment.path("learnerId").asText())
                || actor.equals("ADMIN")
                || actor.equals("INSTRUCTOR"),
            "当前学员未被分配此任务");
        for (JsonNode existing : s.withArray("attempts"))
          require(!(existing.path("assignmentId").asText().equals(assignment.path("id").asText())
              && existing.path("status").asText().matches("RUNNING|PAUSED")), "该任务已有进行中的训练，请点击继续训练");
        String mode = p.path("mode").asText("GUIDED");
        require(mode.matches("GUIDED|FREE|DEMONSTRATION"), "训练模式不正确");
        require(p.path("scope").asText("INDIVIDUAL").matches("NONE|INDIVIDUAL|TEAM"), "计分范围不正确");
        ObjectNode a =
            obj(
                "id",
                id("ATTEMPT"),
                "assignmentId",
                assignment.get("id"),
                "courseId",
                c.get("id"),
                "courseName",
                c.get("name"),
                "domain",
                c.get("domain"),
                "courseVersion",
                c.get("version"),
                "learnerId",
                assignment.get("learnerId"),
                "mode",
                mode,
                "scope",
                mode.equals("DEMONSTRATION") ? "NONE" : p.path("scope").asText("INDIVIDUAL"),
                "interactionMode",
                "INTERACTIVE",
                "host",
                actor,
                "members",
                arr(actor),
                "status",
                "RUNNING",
                "currentStep",
                0,
                "errors",
                0,
                "helps",
                0,
                "score",
                0,
                "events",
                arr(),
                "startedAt",
                Instant.now().toString(),
                "entityState",
                "READY",
                "leaseOwner",
                "");
        s.withArray("attempts").add(a);
        assignment.put("status", "RUNNING");
        return a;
      }
      case "training.join", "training.control", "training.collaboration" -> {
        ObjectNode a = find(s, "attempts", target);
        require(a.path("status").asText().matches("RUNNING|PAUSED"), "会话已结束");
        if (action.equals("training.join")) {
          boolean joined = false;
          for (JsonNode n : a.withArray("members")) if (n.asText().equals(actor)) joined = true;
          if (!joined) a.withArray("members").add(actor);
          return a;
        }
        if (action.equals("training.collaboration")) {
          require(
              actor.equals(a.path("host").asText())
                  || actor.equals("INSTRUCTOR")
                  || actor.equals("ADMIN"),
              "只有主持人或教员可切换权限");
          require(p.path("mode").asText("REVIEW").matches("REVIEW|INTERACTIVE"), "协同模式不正确");
          a.put("interactionMode", p.path("mode").asText("REVIEW"));
          return a;
        }
        require(
            a.path("leaseOwner").asText().isBlank()
                || a.path("leaseOwner").asText().equals(actor)
                || a.path("leaseUntil").asLong() < System.currentTimeMillis(),
            "对象正由另一位成员操作");
        a.put("leaseOwner", actor).put("leaseUntil", System.currentTimeMillis() + 30000);
        return a;
      }
      case "training.pause" -> {
        ObjectNode a = find(s, "attempts", target);
        require(a.path("status").asText().matches("RUNNING|PAUSED"), "训练已经结束");
        require(
            actor.equals(a.path("host").asText())
                || actor.equals("INSTRUCTOR")
                || actor.equals("ADMIN"),
            "只有主持人或教员可暂停会话");
        a.put("status", a.path("status").asText().equals("RUNNING") ? "PAUSED" : "RUNNING");
        return a;
      }
      case "training.action" -> {
        ObjectNode a = find(s, "attempts", target);
        require(a.path("status").asText().equals("RUNNING"), "请先开始或继续训练");
        require(
            actor.equals(a.path("learnerId").asText())
                || a.path("scope").asText().equals("TEAM")
                || actor.equals("ADMIN")
                || actor.equals("INSTRUCTOR"),
            "该训练不属于当前学员");
        if (a.path("interactionMode").asText().equals("REVIEW"))
          require(actor.equals(a.path("host").asText()), "评审模式仅主持人可以操作");
        if (a.path("scope").asText().equals("TEAM"))
          require(
              a.path("leaseOwner").asText().equals(actor)
                  && a.path("leaseUntil").asLong() >= System.currentTimeMillis(),
              "请先申请对象控制权");
        ObjectNode c = find(s, "courses", a.path("courseId").asText());
        int current = a.path("currentStep").asInt();
        require(current < c.path("steps").size(), "全部步骤已经完成");
        String kind = p.path("kind").asText("PASS");
        require(kind.matches("PASS|ERROR|HELP"), "动作类型不正确");
        String profile = s.path("settings").path("platformProfile").asText();
        if (kind.equals("PASS") && !PLATFORM.acceptsInteraction(profile)) {
          if (profile.equals("FAIL_ONCE"))
            ((ObjectNode) s.get("settings")).put("platformProfile", "SUCCESS");
          a.withArray("events")
              .add(
                  obj(
                      "id",
                      id("EVENT"),
                      "step",
                      c.path("steps").get(current).path("id"),
                      "kind",
                      "TECHNICAL_FAILURE",
                      "message",
                      "平台模拟失败，可重试；不扣分",
                      "actor",
                      actor,
                      "at",
                      Instant.now().toString()));
          return a;
        }
        JsonNode currentStep = c.path("steps").get(current);
        String expected = currentStep.path("id").asText();
        String expectedTarget = currentStep.path("target").asText("PUMP-01");
        String expectedAction = currentStep.path("actionId").asText();
        String reason = "";
        if (kind.equals("PASS")) {
          if (p.has("stepId") && !p.path("stepId").asText().equals(expected)) reason = "步骤顺序不正确，请完成当前步骤";
          else if (!p.path("target").asText().equals(expectedTarget)) reason = "对象不正确：本步应选择 " + expectedTarget + "，当前选择 " + p.path("target").asText("未选择");
          else if (c.path("interactionVersion").asInt() >= 2 && !p.path("actionId").asText().equals(expectedAction)) reason = "操作不正确，请执行本步动作：" + currentStep.path("actionLabel").asText();
          if (!reason.isBlank()) kind = "ERROR";
        }
        String message = kind.equals("PASS")
            ? currentStep.path("expectedResult").asText("本步骤已通过，操作记录已保存。")
            : kind.equals("HELP") ? currentStep.path("description").asText() + " 目标对象：" + expectedTarget
            : reason.isBlank() ? "操作不符合当前步骤，请检查对象和动作后重试。" : reason;
        ObjectNode ev =
            obj(
                "id",
                id("EVENT"),
                "step",
                expected,
                "name",
                c.path("steps").get(current).path("name"),
                "kind",
                kind,
                "actor",
                actor,
                "at",
                Instant.now().toString(),
                "sequence",
                a.withArray("events").size() + 1);
        ev.put("target", p.path("target").asText())
            .put("actionId", p.path("actionId").asText())
            .put("actionLabel", currentStep.path("actionLabel").asText("执行当前步骤"))
            .put("message", message)
            .put("expectedTarget", expectedTarget)
            .put("expectedResult", currentStep.path("expectedResult").asText())
            .put("penalty", a.path("scope").asText().equals("NONE") ? 0 : kind.equals("ERROR") ? 5 : kind.equals("HELP") ? 2 : 0);
        a.withArray("events").add(ev);
        if (kind.equals("PASS")) {
          a.put("currentStep", ++current)
              .put(
                  "entityState",
                  current >= c.path("steps").size() ? "COMPLETED" : "STEP_" + current);
        }
        if (kind.equals("ERROR")) a.put("errors", a.path("errors").asInt() + 1);
        if (kind.equals("HELP")) a.put("helps", a.path("helps").asInt() + 1);
        if (current == c.path("steps").size()) {
          a.put("status", "COMPLETED").put("completedAt", Instant.now().toString());
          ObjectNode assignment = find(s, "assignments", a.path("assignmentId").asText());
          assignment.put("status", "COMPLETED");
        }
        double score =
            Math.max(
                0,
                current * 100.0 / c.path("steps").size()
                    - a.path("errors").asInt() * 5
                    - a.path("helps").asInt() * 2);
        if (a.path("scope").asText().equals("NONE")) a.putNull("score");
        else a.put("score", round(score));
        a.put(
            "correctRate",
            current + a.path("errors").asInt() == 0
                ? 0
                : round(current * 100.0 / (current + a.path("errors").asInt())));
        return a;
      }
      case "training.confirm" -> {
        ObjectNode a = find(s, "attempts", target);
        require(
            a.path("status").asText().equals("COMPLETED")
                && a.path("scope").asText().equals("INDIVIDUAL"),
            "需要已完成的个人训练记录");
        ObjectNode assignment = find(s, "assignments", a.path("assignmentId").asText());
        assignment.put("confirmed", true).put("evidenceAttemptId", target);
        ObjectNode c = find(s, "courses", a.path("courseId").asText());
        if (c.has("planId")) {
          ObjectNode plan = find(s, "plans", c.path("planId").asText());
          plan.put("trainingConfirmed", true);
          for (JsonNode req : s.withArray("requests"))
            if (req.path("planId").asText().equals(c.path("planId").asText()))
              ((ObjectNode) req).put("status", "COMPLETED").put("evidenceAttemptId", target);
        }
        return assignment;
      }
      case "training.feedback" -> {
        ObjectNode a = find(s, "attempts", target);
        require(a.path("status").asText().equals("COMPLETED"), "请先完成训练");
        require(
            actor.equals(a.path("learnerId").asText())
                || actor.equals("INSTRUCTOR")
                || actor.equals("ADMIN"),
            "只能反馈本人训练");
        int rating = p.path("rating").asInt();
        require(rating >= 1 && rating <= 5, "问卷评分范围1～5");
        ObjectNode f =
            obj(
                "id",
                id("FEEDBACK"),
                "attemptId",
                target,
                "courseId",
                a.get("courseId"),
                "domain",
                a.get("domain"),
                "rating",
                rating,
                "comment",
                p.path("comment").asText(),
                "createdBy",
                actor,
                "status",
                "OPEN");
        s.withArray("feedback").add(f);
        return f;
      }
      case "issue.create" -> {
        ObjectNode issue =
            obj(
                "id",
                id("ISSUE"),
                "name",
                p.path("name").asText("建议完善异常识别提示"),
                "sourceId",
                target,
                "domain",
                find(s, "attempts", target).path("domain"),
                "equipmentId",
                "EQ-DEMO-01",
                "createdBy",
                actor,
                "status",
                "OPEN");
        s.withArray("issues").add(issue);
        return issue;
      }
      default -> throw new BusinessException(400, "UNKNOWN_COMMAND", "未知训练命令：" + action);
    }
  }

  private static void validateCourse(ObjectNode c) {
    require(!c.path("name").asText().isBlank(), "请填写课程名称");
    require(c.path("steps").isArray() && c.path("steps").size() > 0, "请至少配置一个教学步骤");
    java.util.Set<String> ids = new java.util.HashSet<>();
    for (JsonNode st : c.path("steps")) {
      String label = "步骤 " + st.path("id").asText() + "：";
      require(!st.path("id").asText().isBlank() && ids.add(st.path("id").asText()), "步骤编号缺失或重复");
      require(!st.path("name").asText().isBlank(), label + "请填写步骤名称");
      require(st.path("target").asText().matches("PUMP-01|VALVE-01|CTRL-01|SENSOR-01"), label + "请选择有效的操作对象");
      if (c.path("interactionVersion").asInt() >= 2) {
        for (String key : new String[]{"description", "actionId", "actionLabel", "expectedResult"})
          require(!st.path(key).asText().isBlank(), label + "操作说明、执行动作和预期结果必须填写完整");
      }
    }
  }

  private static ObjectNode job(ObjectNode s, String type, String target) {
    ObjectNode j =
        obj(
            "id",
            id("JOB"),
            "type",
            type,
            "targetId",
            target,
            "status",
            "RUNNING",
            "dueAt",
            System.currentTimeMillis() + 1200,
            "profile",
            s.path("settings").path("platformProfile"),
            "createdAt",
            Instant.now().toString(),
            "implementation",
            "SIMULATED_PLATFORM");
    s.withArray("jobs").add(j);
    if (j.path("profile").asText().equals("FAIL_ONCE"))
      ((ObjectNode) s.get("settings")).put("platformProfile", "SUCCESS");
    return j;
  }

  public static boolean settleJobs(ObjectNode s) {
    boolean changed = false;
    for (JsonNode n : s.withArray("jobs")) {
      ObjectNode job = (ObjectNode) n;
      if (!job.path("status").asText().equals("RUNNING")
          || job.path("dueAt").asLong() > System.currentTimeMillis()) continue;
      java.util.Set<String> seen = new java.util.HashSet<>();
      for (var callback :
          PLATFORM.completeJob(job.path("id").asText(), job.path("profile").asText())) {
        boolean accepted =
            seen.add(callback.eventId())
                && callback.sequence() > job.path("callbackSequence").asInt();
        job.withArray("callbacks")
            .add(
                obj(
                    "eventId",
                    callback.eventId(),
                    "sequence",
                    callback.sequence(),
                    "status",
                    callback.status(),
                    "accepted",
                    accepted));
        if (!accepted) {
          job.put("ignoredCallbacks", job.path("ignoredCallbacks").asInt() + 1);
          continue;
        }
        job.put("status", callback.status())
            .put("message", callback.message())
            .put("callbackSequence", callback.sequence());
        boolean failed = callback.status().equals("FAILED");
        String collection = job.path("type").asText().equals("COURSE") ? "courses" : "assets";
        ObjectNode target = find(s, collection, job.path("targetId").asText());
        target.put(
            "status",
            collection.equals("courses")
                ? (failed ? "BUILD_FAILED" : "PUBLISHED")
                : (failed ? "DRAFT" : "PENDING_REVIEW"));
      }
      changed = true;
    }
    return changed;
  }
}

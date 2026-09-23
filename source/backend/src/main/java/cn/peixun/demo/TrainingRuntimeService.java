package cn.peixun.demo;

import static cn.peixun.demo.BusinessException.require;
import static cn.peixun.demo.Json.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.time.Instant;

/** Configuration-driven learner runtime for immutable interaction-contract V3 course snapshots. */
public final class TrainingRuntimeService {
  private static final VirtualPlatformPort PLATFORM = new MockVirtualPlatformAdapter();

  private TrainingRuntimeService() {}

  public static boolean supports(ObjectNode state, String action, ObjectNode payload) {
    if (action.startsWith("training.station.")) return true;
    if (action.equals("training.assign"))
      return find(state, "courses", payload.path("courseId").asText())
              .path("interactionSchemaVersion")
              .asInt()
          == 3;
    if (action.equals("training.start"))
      return find(state, "assignments", payload.path("assignmentId").asText())
              .path("courseSnapshot")
              .path("interactionSchemaVersion")
              .asInt()
          == 3;
    if (action.matches("training\\.(action|continue|pause|confirm)"))
      return find(state, "attempts", payload.path("id").asText())
              .path("interactionSchemaVersion")
              .asInt()
          == 3;
    return false;
  }

  public static ObjectNode apply(
      ObjectNode state,
      String action,
      ObjectNode payload,
      String actor,
      String domain,
      String commandId) {
    return switch (action) {
      case "training.assign" -> assign(state, payload, actor, domain);
      case "training.start" -> start(state, payload, actor);
      case "training.action" -> action(state, payload, actor, commandId, "LEARNER");
      case "training.continue" -> advance(state, payload, actor);
      case "training.pause" -> pause(state, payload, actor);
      case "training.confirm" -> confirm(state, payload, actor);
      case "training.station.save" -> saveStation(state, payload, actor, domain);
      case "training.station.connect" -> connectStation(state, payload);
      case "training.station.signal" -> stationSignal(state, payload, actor, commandId);
      default -> throw new BusinessException(400, "UNKNOWN_ACTION", "未知训练运行命令：" + action);
    };
  }

  private static ObjectNode assign(ObjectNode state, ObjectNode payload, String actor, String domain) {
    ObjectNode course = find(state, "courses", payload.path("courseId").asText());
    require(course.path("status").asText().equals("PUBLISHED"), "课程尚未发布");
    require(course.path("publishedSnapshot").isObject(), "课件缺少已发布快照，不能分配");
    require(domain.isBlank() || domain.equals(course.path("domain").asText()), "当前系统不能分配其他系统课件");
    String learner = payload.path("learnerId").asText("LEARNER_A");
    require(DemoService.role(learner).equals("LEARNER"), "培训任务必须分配给学员身份");
    ObjectNode snapshot = (ObjectNode) course.path("publishedSnapshot").deepCopy();
    snapshot.put("id", course.path("id").asText());
    snapshot.put("domain", course.path("domain").asText());
    snapshot.put("version", course.path("version").asInt());
    String stationId = payload.path("stationId").asText();
    if (!stationId.isBlank()) {
      ObjectNode station = find(state, "stations", stationId);
      require(station.path("domain").asText().equals(course.path("domain").asText()), "台位不属于当前业务系统");
      requireStationCompatible(station, snapshot);
    }
    ObjectNode assignment =
        obj(
            "id", id("ASSIGN"),
            "courseId", course.path("id").asText(),
            "domain", course.path("domain").asText(),
            "courseVersion", course.path("version").asInt(),
            "name", course.path("name").asText(),
            "learnerId", learner,
            "mode", payload.path("mode").asText(course.path("mode").asText("GUIDED")),
            "dueAt", payload.path("dueAt").asText(),
            "stationId", stationId,
            "status", "ASSIGNED",
            "confirmed", false,
            "courseSnapshot", snapshot,
            "createdBy", actor,
            "createdAt", Instant.now().toString());
    state.withArray("assignments").add(assignment);
    return assignment;
  }

  private static ObjectNode start(ObjectNode state, ObjectNode payload, String actor) {
    ObjectNode assignment = find(state, "assignments", payload.path("assignmentId").asText());
    require(
        actor.equals(assignment.path("learnerId").asText())
            || DemoService.role(actor).matches("ADMIN|INSTRUCTOR"),
        "当前学员未被分配此任务");
    for (JsonNode item : state.withArray("attempts"))
      require(
          !(item.path("assignmentId").asText().equals(assignment.path("id").asText())
              && item.path("status").asText().matches("RUNNING|PAUSED")),
          "该任务已有进行中的训练，请点击继续训练");
    String mode = payload.path("mode").asText(assignment.path("mode").asText("GUIDED"));
    require(mode.matches("GUIDED|FREE|DEMONSTRATION"), "训练模式不正确");
    String scope = mode.equals("DEMONSTRATION") ? "NONE" : payload.path("scope").asText("INDIVIDUAL");
    require(scope.matches("NONE|INDIVIDUAL"), "V3课件首版仅支持个人训练或不计分演示");
    String stationId = assignment.path("stationId").asText();
    if (!stationId.isBlank()) {
      ObjectNode station = find(state, "stations", stationId);
      require(station.path("status").asText().equals("CONNECTED"), "指定训练台位尚未连接");
    }
    ObjectNode snapshot = (ObjectNode) assignment.path("courseSnapshot").deepCopy();
    ObjectNode project =
        obj(
            "id", "TRAINING-" + assignment.path("id").asText(),
            "version", assignment.path("courseVersion").asInt(),
            "domain", assignment.path("domain").asText());
    ObjectNode runtime =
        SimulationRuleEngine.createPreview(
            project,
            (ObjectNode) snapshot.path("sceneSnapshot"),
            (ObjectNode) snapshot.path("topologySnapshot"),
            actor);
    String attemptId = id("ATTEMPT");
    runtime.put("id", "RUNTIME-" + attemptId);
    ObjectNode attempt =
        obj(
            "id", attemptId,
            "interactionSchemaVersion", 3,
            "assignmentId", assignment.path("id").asText(),
            "courseId", assignment.path("courseId").asText(),
            "courseName", assignment.path("name").asText(),
            "domain", assignment.path("domain").asText(),
            "courseVersion", assignment.path("courseVersion").asInt(),
            "courseSnapshot", snapshot,
            "learnerId", assignment.path("learnerId").asText(),
            "mode", mode,
            "scope", scope,
            "host", actor,
            "members", arr(actor),
            "status", "RUNNING",
            "currentStep", 0,
            "awaitingContinue", false,
            "passedStepIds", arr(),
            "errors", 0,
            "helps", 0,
            "technicalFailures", 0,
            "score", scope.equals("NONE") ? NullNode.instance : IntNode.valueOf(0),
            "correctRate", NullNode.instance,
            "events", arr(),
            "runtime", runtime,
            "startedAt", Instant.now().toString());
    if (snapshot.path("supportCaseSnapshot").isObject())
      SupportTrainingService.initialize(attempt, (ObjectNode) snapshot.path("supportCaseSnapshot"));
    state.withArray("attempts").add(attempt);
    assignment.put("status", "RUNNING").put("latestAttemptId", attemptId).put("confirmed", false);
    return attempt;
  }

  private static ObjectNode action(
      ObjectNode state,
      ObjectNode payload,
      String actor,
      String commandId,
      String source) {
    ObjectNode attempt = find(state, "attempts", payload.path("id").asText());
    require(!attempt.path("supportTraining").isObject(), "保障教学案例请使用方案编制与事件处置操作区");
    require(attempt.path("status").asText().equals("RUNNING"), "请先开始或继续训练");
    require(!attempt.path("awaitingContinue").asBoolean(), "本步已通过，请先确认继续下一步");
    requireCanOperate(attempt, actor);
    ObjectNode snapshot = (ObjectNode) attempt.path("courseSnapshot");
    int current = attempt.path("currentStep").asInt();
    require(current < snapshot.withArray("steps").size(), "全部步骤已经完成");
    ObjectNode step = (ObjectNode) snapshot.withArray("steps").get(current);
    String kind = payload.path("kind").asText("PASS");
    require(kind.matches("PASS|HELP"), "动作类型不正确");
    if (kind.equals("HELP")) return help(attempt, step, actor, commandId, source);

    ObjectNode runtime = (ObjectNode) attempt.path("runtime");
    String profile = state.path("settings").path("platformProfile").asText("SUCCESS");
    if (!PLATFORM.acceptsInteraction(profile)) {
      if (profile.equals("FAIL_ONCE")) ((ObjectNode) state.path("settings")).put("platformProfile", "SUCCESS");
      addEvent(
          attempt,
          step,
          actor,
          commandId,
          payload.path("target").asText(payload.path("objectId").asText()),
          payload.path("actionId").asText(),
          parameters(payload),
          runtime.path("states"),
          runtime.path("states"),
          "TECHNICAL_FAILURE",
          "TECHNICAL_FAILURE",
          "平台响应失败，可重试；不计为学员错误",
          0,
          source);
      attempt.put("technicalFailures", attempt.path("technicalFailures").asInt() + 1);
      return attempt;
    }

    String objectId = payload.path("target").asText(payload.path("objectId").asText());
    String actionId = payload.path("actionId").asText();
    ObjectNode actualParameters = parameters(payload);
    String reason = "";
    if (payload.has("stepId") && !payload.path("stepId").asText().equals(step.path("id").asText()))
      reason = "步骤顺序不正确，请完成当前步骤";
    else if (!objectId.equals(step.path("target").asText()))
      reason = "对象不正确：本步应选择 " + step.path("target").asText();
    else if (!actionId.equals(step.path("actionId").asText()))
      reason = "操作不正确：本步应执行 " + step.path("actionId").asText();
    else if (!actualParameters.equals(step.path("parameters")))
      reason = "动作参数不正确，请按本步要求填写";
    else if (!SimulationRuleEngine.matchesCondition((ObjectNode) runtime.path("states"), step.path("precondition")))
      reason = "本步前置状态条件尚未满足";
    if (!reason.isBlank()) {
      businessError(attempt, step, actor, commandId, objectId, actionId, actualParameters, reason, source);
      return attempt;
    }

    JsonNode before = runtime.path("states").deepCopy();
    ObjectNode platformEvent;
    try {
      platformEvent = SimulationRuleEngine.execute(runtime, objectId, actionId, actualParameters);
    } catch (BusinessException error) {
      businessError(attempt, step, actor, commandId, objectId, actionId, actualParameters, error.getMessage(), source);
      return attempt;
    }
    if (!platformEvent.path("result").asText().equals("SUCCEEDED")) {
      businessError(
          attempt,
          step,
          actor,
          commandId,
          objectId,
          actionId,
          actualParameters,
          platformEvent.path("reason").asText(),
          source);
      return attempt;
    }
    double prior = numericScore(attempt);
    attempt.withArray("passedStepIds").add(step.path("id").asText());
    attempt.put("awaitingContinue", true);
    updateScore(attempt);
    addEvent(
        attempt,
        step,
        actor,
        commandId,
        objectId,
        actionId,
        actualParameters,
        before,
        runtime.path("states"),
        "PASS",
        "PASSED",
        step.path("expectedResult").asText("本步骤已通过，操作记录已保存。"),
        scoreDelta(attempt, prior),
        source);
    return attempt;
  }

  private static ObjectNode advance(ObjectNode state, ObjectNode payload, String actor) {
    ObjectNode attempt = find(state, "attempts", payload.path("id").asText());
    require(attempt.path("status").asText().equals("RUNNING"), "训练未处于运行状态");
    requireCanOperate(attempt, actor);
    require(attempt.path("awaitingContinue").asBoolean(), "当前步骤尚未通过，不能继续");
    int next = attempt.path("currentStep").asInt() + 1;
    attempt.put("currentStep", next).put("awaitingContinue", false);
    if (next >= attempt.path("courseSnapshot").path("steps").size()) {
      attempt.put("status", "COMPLETED").put("completedAt", Instant.now().toString());
      ObjectNode assignment = find(state, "assignments", attempt.path("assignmentId").asText());
      assignment.put("status", "COMPLETED");
      attempt.set("evaluation", evaluation(attempt));
    }
    return attempt;
  }

  private static ObjectNode pause(ObjectNode state, ObjectNode payload, String actor) {
    ObjectNode attempt = find(state, "attempts", payload.path("id").asText());
    require(attempt.path("status").asText().matches("RUNNING|PAUSED"), "训练已经结束");
    requireCanOperate(attempt, actor);
    attempt.put("status", attempt.path("status").asText().equals("RUNNING") ? "PAUSED" : "RUNNING");
    attempt.put("savedAt", Instant.now().toString());
    return attempt;
  }

  private static ObjectNode confirm(ObjectNode state, ObjectNode payload, String actor) {
    ObjectNode attempt = find(state, "attempts", payload.path("id").asText());
    require(attempt.path("status").asText().equals("COMPLETED") && attempt.path("scope").asText().equals("INDIVIDUAL"), "需要已完成的个人训练记录");
    ObjectNode assignment = find(state, "assignments", attempt.path("assignmentId").asText());
    for (JsonNode item : state.withArray("trainingArchives"))
      if (item.path("attemptId").asText().equals(attempt.path("id").asText())) {
        assignment.put("archiveId", item.path("id").asText());
        return assignment;
      }
    assignment.put("confirmed", true).put("evidenceAttemptId", attempt.path("id").asText());
    if (!assignment.has("confirmedAttemptIds")) assignment.set("confirmedAttemptIds", arr());
    assignment.withArray("confirmedAttemptIds").add(attempt.path("id").asText());
    attempt.with("evaluation").put("confirmedBy", actor).put("confirmedAt", Instant.now().toString());
    ObjectNode archive =
        obj(
            "id", id("TRAINING-ARCHIVE"),
            "domain", attempt.path("domain").asText(),
            "learnerId", attempt.path("learnerId").asText(),
            "courseRef", obj("id", attempt.path("courseId").asText(), "version", attempt.path("courseVersion").asInt()),
            "assignmentId", assignment.path("id").asText(),
            "attemptId", attempt.path("id").asText(),
            "scoreRule", attempt.path("courseSnapshot").path("scoreRule").deepCopy(),
            "score", attempt.get("score"),
            "courseSnapshot", attempt.path("courseSnapshot").deepCopy(),
            "finalStates", attempt.path("runtime").path("states").deepCopy(),
            "events", attempt.path("events").deepCopy(),
            "evaluation", attempt.path("evaluation").deepCopy(),
            "archivedAt", Instant.now().toString());
    state.withArray("trainingArchives").add(archive);
    assignment.put("archiveId", archive.path("id").asText());
    return assignment;
  }

  private static ObjectNode saveStation(ObjectNode state, ObjectNode payload, String actor, String domain) {
    String stationDomain = payload.path("domain").asText(domain);
    TrainingDomain.require(stationDomain);
    require(domain.isBlank() || domain.equals(stationDomain), "当前系统不能配置其他系统台位");
    require(payload.path("mappings").isArray() && !payload.path("mappings").isEmpty(), "至少配置一个台位信号映射");
    java.util.Set<String> points = new java.util.HashSet<>();
    for (JsonNode mapping : payload.path("mappings")) {
      require(!mapping.path("point").asText().isBlank(), "台位信号点不能为空");
      require(points.add(mapping.path("point").asText()), "台位信号点不能重复");
      require(!mapping.path("objectId").asText().isBlank() && !mapping.path("actionId").asText().isBlank(), "台位映射必须选择对象和动作");
      require(mapping.path("parameters").isObject(), "台位映射参数必须是类型化对象");
    }
    ObjectNode station =
        obj(
            "id", id("STATION"),
            "name", payload.path("name").asText("模拟训练台位"),
            "domain", stationDomain,
            "address", payload.path("address").asText("mock://training/station"),
            "status", "DISCONNECTED",
            "mappings", payload.path("mappings").deepCopy(),
            "createdBy", actor);
    require(station.path("address").asText().startsWith("mock://"), "演示台位仅支持mock地址");
    state.withArray("stations").add(station);
    return station;
  }

  private static ObjectNode connectStation(ObjectNode state, ObjectNode payload) {
    ObjectNode station = find(state, "stations", payload.path("id").asText());
    station.put("status", payload.path("connected").asBoolean(true) ? "CONNECTED" : "DISCONNECTED");
    return station;
  }

  private static ObjectNode stationSignal(
      ObjectNode state, ObjectNode payload, String actor, String commandId) {
    ObjectNode station = find(state, "stations", payload.path("id").asText());
    require(station.path("status").asText().equals("CONNECTED"), "台位已断开");
    ObjectNode attempt = find(state, "attempts", payload.path("attemptId").asText());
    require(attempt.path("interactionSchemaVersion").asInt() == 3, "台位信号只能发送到V3训练实例");
    require(station.path("domain").asText().equals(attempt.path("domain").asText()), "台位与训练任务不属于同一系统");
    require(attempt.path("status").asText().equals("RUNNING"), attempt.path("status").asText().equals("PAUSED") ? "训练已暂停，请继续训练后再发送台位信号" : "训练已经结束，不能继续发送台位信号");
    require(!attempt.path("awaitingContinue").asBoolean(), "本步已通过，请先进入下一步");
    requireCanOperate(attempt, actor);
    ObjectNode assignment = find(state, "assignments", attempt.path("assignmentId").asText());
    require(assignment.path("stationId").asText().isBlank() || assignment.path("stationId").asText().equals(station.path("id").asText()), "该训练任务指定了其他台位");
    String point = payload.path("point").asText();
    ObjectNode mapping = null;
    for (JsonNode item : station.withArray("mappings"))
      if (item.path("point").asText().equals(point)) mapping = (ObjectNode) item;
    require(mapping != null, "信号点未映射：" + point);
    ObjectNode command =
        obj(
            "id", attempt.path("id").asText(),
            "kind", "PASS",
            "stepId", attempt.path("courseSnapshot").path("steps").get(attempt.path("currentStep").asInt()).path("id").asText(),
            "target", mapping.path("objectId").asText(),
            "actionId", mapping.path("actionId").asText(),
            "parameters", mapping.path("parameters").isObject() ? mapping.path("parameters").deepCopy() : obj());
    return action(state, command, actor, commandId, "STATION");
  }

  private static ObjectNode help(
      ObjectNode attempt, ObjectNode step, String actor, String commandId, String source) {
    double prior = numericScore(attempt);
    attempt.put("helps", attempt.path("helps").asInt() + 1);
    updateScore(attempt);
    ObjectNode runtime = (ObjectNode) attempt.path("runtime");
    addEvent(
        attempt,
        step,
        actor,
        commandId,
        step.path("target").asText(),
        step.path("actionId").asText(),
        obj(),
        runtime.path("states"),
        runtime.path("states"),
        "HELP",
        "HELP_USED",
        step.path("description").asText() + " 目标对象：" + step.path("target").asText(),
        scoreDelta(attempt, prior),
        source);
    return attempt;
  }

  private static void businessError(
      ObjectNode attempt,
      ObjectNode step,
      String actor,
      String commandId,
      String objectId,
      String actionId,
      ObjectNode parameters,
      String reason,
      String source) {
    double prior = numericScore(attempt);
    attempt.put("errors", attempt.path("errors").asInt() + 1);
    updateScore(attempt);
    ObjectNode runtime = (ObjectNode) attempt.path("runtime");
    addEvent(
        attempt,
        step,
        actor,
        commandId,
        objectId,
        actionId,
        parameters,
        runtime.path("states"),
        runtime.path("states"),
        "ERROR",
        "BUSINESS_ERROR",
        reason,
        scoreDelta(attempt, prior),
        source);
  }

  private static void addEvent(
      ObjectNode attempt,
      ObjectNode step,
      String actor,
      String commandId,
      String objectId,
      String actionId,
      JsonNode parameters,
      JsonNode beforeState,
      JsonNode afterState,
      String kind,
      String result,
      String reason,
      double scoreDelta,
      String source) {
    String eventId = id("TRAINING-EVENT");
    attempt.withArray("events")
        .add(
            obj(
                "id", eventId,
                "eventId", eventId,
                "commandId", commandId,
                "attemptId", attempt.path("id").asText(),
                "stepId", step.path("id").asText(),
                "step", step.path("id").asText(),
                "name", step.path("name").asText(),
                "actorId", actor,
                "actor", actor,
                "objectId", objectId,
                "target", objectId,
                "actionId", actionId,
                "actionLabel", step.path("actionLabel").asText(),
                "parameters", parameters.deepCopy(),
                "beforeState", beforeState.deepCopy(),
                "afterState", afterState.deepCopy(),
                "kind", kind,
                "result", result,
                "reason", reason,
                "message", reason,
                "scoreDelta", scoreDelta,
                "source", source,
                "sequence", attempt.withArray("events").size() + 1,
                "at", Instant.now().toString()));
  }

  private static ObjectNode parameters(ObjectNode payload) {
    return payload.path("parameters").isObject() ? (ObjectNode) payload.path("parameters") : obj();
  }

  private static void requireStationCompatible(ObjectNode station, ObjectNode snapshot) {
    JsonNode objects = snapshot.path("sceneSnapshot").path("objects");
    require(objects.isArray(), "课件环境缺少可供台位映射的对象");
    for (JsonNode mapping : station.withArray("mappings")) {
      JsonNode target = null;
      for (JsonNode object : objects)
        if (object.path("id").asText().equals(mapping.path("objectId").asText())) target = object;
      require(target != null, "台位映射对象不在课件环境中：" + mapping.path("objectId").asText());
      boolean actionFound = false;
      for (JsonNode action : target.path("actions"))
        if (action.path("id").asText().equals(mapping.path("actionId").asText())) actionFound = true;
      require(actionFound, "台位映射动作不受课件对象支持：" + mapping.path("actionId").asText());
    }
  }

  private static void requireCanOperate(ObjectNode attempt, String actor) {
    require(
        actor.equals(attempt.path("learnerId").asText())
            || DemoService.role(actor).matches("ADMIN|INSTRUCTOR"),
        "该训练不属于当前学员");
  }

  private static void updateScore(ObjectNode attempt) {
    if (attempt.path("scope").asText().equals("NONE")) {
      attempt.putNull("score");
      updateCorrectRate(attempt);
      return;
    }
    ObjectNode snapshot = (ObjectNode) attempt.path("courseSnapshot");
    int earned = 0;
    for (JsonNode step : snapshot.withArray("steps"))
      if (contains(attempt.withArray("passedStepIds"), step.path("id").asText()))
        earned += step.path("points").asInt();
    int total = snapshot.path("scoreRule").path("total").asInt(100);
    int penalties =
        attempt.path("errors").asInt() * snapshot.path("scoreRule").path("errorPenalty").asInt(5)
            + attempt.path("helps").asInt() * snapshot.path("scoreRule").path("helpPenalty").asInt(2);
    attempt.put("score", Math.max(0, Math.min(total, earned - penalties)));
    updateCorrectRate(attempt);
  }

  private static void updateCorrectRate(ObjectNode attempt) {
    int passed = attempt.withArray("passedStepIds").size();
    int denominator = passed + attempt.path("errors").asInt();
    if (denominator == 0) attempt.putNull("correctRate");
    else attempt.put("correctRate", round(passed * 100.0 / denominator));
  }

  private static boolean contains(ArrayNode values, String value) {
    for (JsonNode item : values) if (item.asText().equals(value)) return true;
    return false;
  }

  private static double numericScore(ObjectNode attempt) {
    return attempt.path("score").isNumber() ? attempt.path("score").asDouble() : 0;
  }

  private static double scoreDelta(ObjectNode attempt, double prior) {
    return attempt.path("score").isNumber() ? round(attempt.path("score").asDouble() - prior) : 0;
  }

  private static ObjectNode evaluation(ObjectNode attempt) {
    return obj(
        "attemptId", attempt.path("id").asText(),
        "score", attempt.get("score"),
        "passedSteps", attempt.withArray("passedStepIds").size(),
        "businessErrors", attempt.path("errors").asInt(),
        "helps", attempt.path("helps").asInt(),
        "technicalFailures", attempt.path("technicalFailures").asInt(),
        "correctRate", attempt.get("correctRate"),
        "eventCount", attempt.withArray("events").size(),
        "evaluatedAt", Instant.now().toString());
  }
}

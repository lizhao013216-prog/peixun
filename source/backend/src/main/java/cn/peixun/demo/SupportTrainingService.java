package cn.peixun.demo;

import static cn.peixun.demo.BusinessException.require;
import static cn.peixun.demo.Json.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.time.Instant;

/** Training-only support-planning copy. It never invokes formal plan/run command handlers. */
public final class SupportTrainingService {
  private SupportTrainingService() {}

  public static void initialize(ObjectNode attempt, ObjectNode snapshot) {
    attempt.set(
        "supportTraining",
        obj(
            "id", id("SUPPORT-PRACTICE"),
            "purpose", "TRAINING",
            "attemptId", attempt.path("id").asText(),
            "ownerLearnerId", attempt.path("learnerId").asText(),
            "caseRef", obj("id", snapshot.path("id").asText(), "version", snapshot.path("version").asInt()),
            "name", snapshot.path("name").asText(),
            "taskObject", snapshot.path("taskObject").asText(),
            "scope", snapshot.path("scope").asText(),
            "deadline", snapshot.path("deadline").asDouble(270),
            "arrivalTime", snapshot.path("arrivalTime").asDouble(90),
            "transportCost", snapshot.path("transportCost").asDouble(100),
            "operations", snapshot.path("operations").deepCopy(),
            "resources", snapshot.path("resources").deepCopy(),
            "checkpoints", obj(),
            "events", arr(),
            "status", "TASK_READING"));
  }

  public static ObjectNode apply(
      ObjectNode state, String action, ObjectNode payload, String actor, String commandId) {
    ObjectNode attempt = find(state, "attempts", payload.path("id").asText());
    require(attempt.path("domain").asText().equals("SUPPORT"), "该命令只用于保障教学训练");
    require(attempt.path("status").asText().equals("RUNNING"), "请先开始或继续训练");
    require(
        actor.equals(attempt.path("learnerId").asText())
            || actor.equals("ADMIN")
            || actor.equals("INSTRUCTOR"),
        "只能操作本人的教学案例副本");
    ObjectNode practice = (ObjectNode) attempt.path("supportTraining");
    require(practice.path("purpose").asText().equals("TRAINING"), "训练副本标识无效");
    return switch (action) {
      case "training.support.confirm" -> checkpoint(attempt, practice, "task", 10, "已确认教学任务对象、范围和时限", "TASK_CONFIRMED", commandId, actor, null, null);
      case "training.support.resource" -> resource(attempt, practice, payload, actor, commandId);
      case "training.support.plan" -> plan(attempt, practice, payload, actor, commandId);
      case "training.support.calculate" -> calculate(attempt, practice, actor, commandId);
      case "training.support.delay" -> delay(attempt, practice, payload, actor, commandId);
      case "training.support.resolve" -> resolve(attempt, practice, payload, actor, commandId);
      case "training.support.submit" -> submit(state, attempt, practice, payload, actor, commandId);
      default -> throw new BusinessException(400, "UNKNOWN_ACTION", "未知保障教学命令：" + action);
    };
  }

  private static ObjectNode resource(
      ObjectNode attempt, ObjectNode practice, ObjectNode payload, String actor, String commandId) {
    require(done(practice, "task"), "请先确认教学任务");
    ObjectNode resource = findIn(practice.withArray("resources"), payload.path("resourceId").asText(), "训练资源不存在");
    ObjectNode before = resource.deepCopy();
    if (payload.has("available")) resource.put("available", payload.path("available").asBoolean());
    if (payload.has("capacity")) {
      int capacity = payload.path("capacity").asInt();
      require(capacity >= 0 && capacity <= 20, "资源数量范围为0～20");
      resource.put("capacity", capacity);
    }
    return checkpoint(attempt, practice, "resource", 20, "训练资源配置已保存", "RESOURCE_CONFIGURED", commandId, actor, before, resource);
  }

  private static ObjectNode plan(
      ObjectNode attempt, ObjectNode practice, ObjectNode payload, String actor, String commandId) {
    require(done(practice, "resource"), "请先完成资源配置");
    ObjectNode operation = findIn(practice.withArray("operations"), payload.path("operationId").asText(), "训练工序不存在");
    ObjectNode before = operation.deepCopy();
    double duration = payload.path("duration").asDouble(operation.path("duration").asDouble());
    require(duration > 0 && duration <= 1440, "工序时长必须在0～1440分钟之间");
    operation.put("duration", duration);
    if (payload.has("materialQuantity")) {
      int quantity = payload.path("materialQuantity").asInt();
      require(quantity >= 0 && quantity <= 100, "物料数量范围为0～100");
      operation.put("materialQuantity", quantity);
    }
    return checkpoint(attempt, practice, "plan", 20, "训练工序和依赖引用已保存", "PLAN_EDITED", commandId, actor, before, operation);
  }

  private static ObjectNode calculate(
      ObjectNode attempt, ObjectNode practice, String actor, String commandId) {
    require(done(practice, "plan"), "请先完成工序编制");
    ObjectNode before = practice.path("result").isObject() ? (ObjectNode) practice.path("result").deepCopy() : obj();
    ObjectNode result =
        ScheduleEngine.calculate(
            practice.path("operations"),
            practice.path("resources"),
            practice.path("arrivalTime").asDouble(),
            practice.path("transportCost").asDouble());
    practice.set("result", result);
    practice.put("status", "CALCULATED");
    return checkpoint(attempt, practice, "calculate", 20, "排程与费用由后台算法重新计算", "CALCULATED", commandId, actor, before, result);
  }

  private static ObjectNode delay(
      ObjectNode attempt, ObjectNode practice, ObjectNode payload, String actor, String commandId) {
    require(done(practice, "calculate"), "请先完成初始计算");
    double eta = payload.path("arrivalTime").asDouble(180);
    require(eta >= 0 && eta <= 1440, "延迟到货时间范围为0～1440分钟");
    ObjectNode before = obj("arrivalTime", practice.path("arrivalTime"), "result", practice.path("result").deepCopy());
    practice.put("arrivalTime", eta);
    ObjectNode delayed =
        ScheduleEngine.calculate(
            practice.path("operations"), practice.path("resources"), eta, practice.path("transportCost").asDouble());
    practice.set("delayedResult", delayed);
    practice.put("status", "DELAY_INJECTED");
    addEvent(attempt, practice, "DELAY_INJECTED", "模拟到货延迟已注入，原结果和延迟结果均已保留", commandId, actor, before, obj("arrivalTime", eta, "result", delayed), 0);
    return attempt;
  }

  private static ObjectNode resolve(
      ObjectNode attempt, ObjectNode practice, ObjectNode payload, String actor, String commandId) {
    require(practice.path("status").asText().equals("DELAY_INJECTED"), "请先注入延迟事件");
    String strategy = payload.path("strategy").asText();
    require(strategy.matches("WAIT|EXPEDITE"), "处置策略必须为等待或加急");
    ObjectNode before = obj("strategy", "UNRESOLVED", "result", practice.path("delayedResult").deepCopy());
    double eta = strategy.equals("EXPEDITE") ? payload.path("arrivalTime").asDouble(135) : practice.path("arrivalTime").asDouble();
    double transport = strategy.equals("EXPEDITE") ? payload.path("transportCost").asDouble(140) : practice.path("transportCost").asDouble();
    ObjectNode result = ScheduleEngine.calculate(practice.path("operations"), practice.path("resources"), eta, transport);
    practice.put("strategy", strategy).put("resolvedArrivalTime", eta).put("resolvedTransportCost", transport);
    practice.set("resolvedResult", result);
    practice.put("status", "RESOLVED");
    return checkpoint(attempt, practice, "resolve", 20, "延迟事件处置完成，处置前后结果已保留", "EVENT_RESOLVED", commandId, actor, before, obj("strategy", strategy, "result", result));
  }

  private static ObjectNode submit(
      ObjectNode state,
      ObjectNode attempt,
      ObjectNode practice,
      ObjectNode payload,
      String actor,
      String commandId) {
    require(done(practice, "resolve"), "请先完成延迟事件处置");
    String reflection = payload.path("reflection").asText().trim();
    require(reflection.length() >= 5, "请填写不少于5个字的复盘结论");
    practice.put("reflection", reflection).put("status", "COMPLETED");
    checkpoint(attempt, practice, "submit", 10, "复盘结论已提交", "REVIEW_SUBMITTED", commandId, actor, null, obj("reflection", reflection));
    attempt.put("status", "COMPLETED").put("completedAt", Instant.now().toString());
    attempt.put("currentStep", attempt.path("courseSnapshot").path("steps").size());
    ObjectNode assignment = find(state, "assignments", attempt.path("assignmentId").asText());
    assignment.put("status", "COMPLETED");
    attempt.set(
        "evaluation",
        obj(
            "attemptId", attempt.path("id").asText(),
            "learningScore", attempt.get("score"),
            "planMetrics", practice.path("resolvedResult").deepCopy(),
            "businessMetricMeaning", "训练方案的工期、费用和资源结果",
            "learningScoreMeaning", "按教学阶段完成情况计算的学习成绩",
            "evaluatedAt", Instant.now().toString()));
    return attempt;
  }

  private static ObjectNode checkpoint(
      ObjectNode attempt,
      ObjectNode practice,
      String key,
      int points,
      String reason,
      String result,
      String commandId,
      String actor,
      JsonNode before,
      JsonNode after) {
    boolean first = !done(practice, key);
    practice.with("checkpoints").put(key, true);
    int prior = attempt.path("score").asInt();
    if (attempt.path("scope").asText().equals("NONE")) attempt.putNull("score");
    else if (first) attempt.put("score", Math.min(100, prior + points));
    int delta = attempt.path("score").isNumber() ? attempt.path("score").asInt() - prior : 0;
    addEvent(attempt, practice, result, reason, commandId, actor, before, after, delta);
    return attempt;
  }

  private static void addEvent(
      ObjectNode attempt,
      ObjectNode practice,
      String result,
      String reason,
      String commandId,
      String actor,
      JsonNode before,
      JsonNode after,
      int scoreDelta) {
    String eventId = id("SUPPORT-TRAINING-EVENT");
    ObjectNode event =
        obj(
            "id", eventId,
            "eventId", eventId,
            "commandId", commandId,
            "attemptId", attempt.path("id").asText(),
            "stepId", result,
            "actorId", actor,
            "objectId", practice.path("id").asText(),
            "actionId", result,
            "parameters", obj(),
            "beforeState", before == null ? obj() : before.deepCopy(),
            "afterState", after == null ? obj() : after.deepCopy(),
            "kind", "PASS",
            "result", result,
            "reason", reason,
            "message", reason,
            "scoreDelta", scoreDelta,
            "sequence", attempt.withArray("events").size() + 1,
            "at", Instant.now().toString());
    attempt.withArray("events").add(event);
    practice.withArray("events").add(event.deepCopy());
  }

  private static boolean done(ObjectNode practice, String key) {
    return practice.path("checkpoints").path(key).asBoolean();
  }

  private static ObjectNode findIn(ArrayNode values, String id, String message) {
    for (JsonNode item : values) if (item.path("id").asText().equals(id)) return (ObjectNode) item;
    throw new BusinessException(404, "NOT_FOUND", message + "：" + id);
  }
}

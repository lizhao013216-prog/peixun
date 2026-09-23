package cn.peixun.demo;

import static cn.peixun.demo.Json.*;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;

class SupportTrainingAndIssueServiceTest {
  @Test
  void supportLearnersEditIndependentCopiesCalculateDelayResolveAndKeepBusinessData() {
    Fixture f = fixture();
    JsonNode formalPlans = f.state.path("plans").deepCopy();
    ObjectNode a = start(f.state, f.course, "LEARNER_A", "A");
    ObjectNode b = start(f.state, f.course, "LEARNER_B", "B");

    support(f.state, a, "training.support.confirm", obj(), "LEARNER_A", "A1");
    support(f.state, a, "training.support.resource", obj("resourceId", "E1", "capacity", 2, "available", true), "LEARNER_A", "A2");
    support(f.state, a, "training.support.plan", obj("operationId", "T01", "duration", 45), "LEARNER_A", "A3");
    support(f.state, a, "training.support.calculate", obj(), "LEARNER_A", "A4");
    double initialFinish = a.path("supportTraining").path("result").path("finish").asDouble();
    support(f.state, a, "training.support.delay", obj("arrivalTime", 180), "LEARNER_A", "A5");
    double delayedFinish = a.path("supportTraining").path("delayedResult").path("finish").asDouble();
    support(f.state, a, "training.support.resolve", obj("strategy", "EXPEDITE", "arrivalTime", 135, "transportCost", 140), "LEARNER_A", "A6");
    support(f.state, a, "training.support.submit", obj("reflection", "采用加急到货并保留处置前后计算依据"), "LEARNER_A", "A7");

    assertTrue(delayedFinish > initialFinish);
    assertEquals(100, a.path("score").asInt());
    assertEquals("COMPLETED", a.path("status").asText());
    assertEquals(a.path("supportTraining").path("resolvedResult"), a.path("evaluation").path("planMetrics"));
    assertEquals(30, b.path("supportTraining").path("operations").get(0).path("duration").asInt());
    assertEquals("TASK_READING", b.path("supportTraining").path("status").asText());
    assertEquals(formalPlans, f.state.path("plans"));
    assertThrows(BusinessException.class, () -> DemoService.authorize("LEARNER_A", "plan.save"));
  }

  @Test
  void issueTypesRouteToRevisionRetrainingTicketAndVerifiedSupportTaskWithoutChangingScore() {
    Fixture f = fixture();
    ObjectNode attempt = complete(f.state, f.course, "LEARNER_A", "A");
    int originalScore = attempt.path("score").asInt();
    int taskCount = f.state.withArray("tasks").size();

    ObjectNode content = issue(f.state, attempt, "CONTENT", "教学说明不清楚", "LEARNER_A");
    TrainingIssueService.apply(f.state, "issue.route", obj("id", content.path("id").asText()), "INSTRUCTOR", "SUPPORT");
    assertEquals("COURSEWARE_REVISION", content.path("resolutionType").asText());
    assertEquals(taskCount, f.state.withArray("tasks").size());
    assertEquals(content.path("id").asText(), find(f.state, "courses", content.path("resolutionId").asText()).path("sourceIssueId").asText());

    ObjectNode learning = issue(f.state, attempt, "LEARNING", "需要补训", "LEARNER_A");
    TrainingIssueService.apply(f.state, "issue.route", obj("id", learning.path("id").asText()), "INSTRUCTOR", "SUPPORT");
    ObjectNode retraining = find(f.state, "assignments", learning.path("resolutionId").asText());
    assertEquals(attempt.path("id").asText(), retraining.path("previousAttemptId").asText());
    assertEquals(originalScore, attempt.path("score").asInt());

    ObjectNode platform = issue(f.state, attempt, "PLATFORM", "平台响应超时", "LEARNER_A");
    TrainingIssueService.apply(f.state, "issue.route", obj("id", platform.path("id").asText(), "opinion", "检查模拟适配器日志"), "INSTRUCTOR", "SUPPORT");
    TrainingIssueService.apply(f.state, "issue.resolve", obj("id", platform.path("id").asText(), "opinion", "已恢复模拟服务"), "INSTRUCTOR", "SUPPORT");
    assertEquals("RESOLVED", platform.path("status").asText());
    assertEquals(originalScore, attempt.path("score").asInt());

    ObjectNode equipment = issue(f.state, attempt, "EQUIPMENT_SUPPORT", "疑似保障资源不足", "LEARNER_A");
    ObjectNode conversion = obj("id", equipment.path("id").asText(), "name", "核实后的保障任务", "object", "演示船A通用泵组", "scope", "检查远端物料保障", "taskType", "RESOURCE_CHECK", "dueMinutes", 360, "constraints", "不得影响现役任务");
    assertThrows(BusinessException.class, () -> TrainingIssueService.apply(f.state, "issue.route", conversion, "INSTRUCTOR", "SUPPORT"));
    assertThrows(BusinessException.class, () -> TrainingIssueService.apply(f.state, "issue.route", conversion, "PLANNER", "SUPPORT"));
    TrainingIssueService.apply(f.state, "issue.verify", obj("id", equipment.path("id").asText(), "opinion", "已核对训练记录，需建立正式检查任务"), "PLANNER", "SUPPORT");
    TrainingIssueService.apply(f.state, "issue.route", conversion, "PLANNER", "SUPPORT");
    String linked = equipment.path("linkedTaskId").asText();
    TrainingIssueService.apply(f.state, "issue.route", conversion, "PLANNER", "SUPPORT");
    assertEquals(taskCount + 1, f.state.withArray("tasks").size());
    ObjectNode task = find(f.state, "tasks", linked);
    assertEquals(equipment.path("id").asText(), task.path("sourceIssueId").asText());
    assertEquals(attempt.path("id").asText(), task.path("sourceAttemptId").asText());
    assertEquals("BUSINESS", task.path("purpose").asText());
    assertEquals(360, task.path("deadline").asInt());
    assertEquals("RESOURCE_CHECK", task.path("taskType").asText());
    ObjectNode plan = PlanningModule.apply(f.state, "plan.create", obj("taskId", task.path("id").asText()), "PLANNER");
    assertEquals(360, plan.path("deadline").asInt());
    assertThrows(BusinessException.class, () -> PlanningModule.apply(f.state, "task.create", obj("name", "绕过任务", "sourceIssueId", content.path("id").asText()), "PLANNER"));
  }

  @Test
  void editingCalculatedPlanInvalidatesResultsScoreAndRequiresRecalculation() {
    Fixture f = fixture();
    ObjectNode attempt = start(f.state, f.course, "LEARNER_A", "STALE");
    support(f.state, attempt, "training.support.confirm", obj(), "LEARNER_A", "S1");
    support(f.state, attempt, "training.support.resource", obj("resourceId", "E1", "capacity", 1), "LEARNER_A", "S2");
    support(f.state, attempt, "training.support.plan", obj("operationId", "T01", "duration", 30), "LEARNER_A", "S3");
    support(f.state, attempt, "training.support.calculate", obj(), "LEARNER_A", "S4");
    support(f.state, attempt, "training.support.delay", obj("arrivalTime", 180), "LEARNER_A", "S5");
    support(f.state, attempt, "training.support.resolve", obj("strategy", "EXPEDITE", "arrivalTime", 135, "transportCost", 140), "LEARNER_A", "S6");
    int revision = attempt.path("supportTraining").path("planRevision").asInt();
    support(f.state, attempt, "training.support.plan", obj("operationId", "T01", "duration", 120), "LEARNER_A", "S7");
    assertTrue(attempt.path("supportTraining").path("planRevision").asInt() > revision);
    assertTrue(attempt.path("supportTraining").path("resolvedResult").isMissingNode());
    assertFalse(attempt.path("supportTraining").path("checkpoints").path("resolve").asBoolean());
    assertThrows(BusinessException.class, () -> support(f.state, attempt, "training.support.submit", obj("reflection", "不能提交过期结果"), "LEARNER_A", "S8"));
  }

  @Test
  void supportCourseAuthoringIsEnabledAndProjectionSeparatesBusinessFromTrainingMetrics() {
    ObjectNode state = Seed.create("support", "support", 1);
    assertTrue(TrainingDomain.SUPPORT.courseAuthoringEnabled());
    ObjectNode projected = WorkspaceProjection.forActor(state, "ADMIN", "SUPPORT");
    assertTrue(projected.has("courses"));
    assertTrue(projected.has("tasks"));
    assertTrue(projected.has("runs"));
  }

  private static ObjectNode complete(ObjectNode state, ObjectNode course, String learner, String suffix) {
    ObjectNode attempt = start(state, course, learner, suffix);
    support(state, attempt, "training.support.confirm", obj(), learner, suffix + "1");
    support(state, attempt, "training.support.resource", obj("resourceId", "E1", "capacity", 1), learner, suffix + "2");
    support(state, attempt, "training.support.plan", obj("operationId", "T01", "duration", 30), learner, suffix + "3");
    support(state, attempt, "training.support.calculate", obj(), learner, suffix + "4");
    support(state, attempt, "training.support.delay", obj("arrivalTime", 180), learner, suffix + "5");
    support(state, attempt, "training.support.resolve", obj("strategy", "EXPEDITE", "arrivalTime", 135, "transportCost", 140), learner, suffix + "6");
    support(state, attempt, "training.support.submit", obj("reflection", "完成方案计算与延迟处置复盘"), learner, suffix + "7");
    return attempt;
  }

  private static ObjectNode issue(ObjectNode state, ObjectNode attempt, String type, String name, String actor) {
    return TrainingIssueService.apply(state, "issue.create", obj("id", attempt.path("id").asText(), "type", type, "name", name, "description", name + "的详细说明"), actor, "SUPPORT");
  }

  private static ObjectNode start(ObjectNode state, ObjectNode course, String learner, String suffix) {
    ObjectNode assignment = TrainingRuntimeService.apply(state, "training.assign", obj("courseId", course.path("id").asText(), "learnerId", learner), "INSTRUCTOR", "SUPPORT", "ASSIGN-" + suffix);
    return TrainingRuntimeService.apply(state, "training.start", obj("assignmentId", assignment.path("id").asText()), learner, "SUPPORT", "START-" + suffix);
  }

  private static ObjectNode support(ObjectNode state, ObjectNode attempt, String action, ObjectNode payload, String actor, String command) {
    payload.put("id", attempt.path("id").asText());
    return SupportTrainingService.apply(state, action, payload, actor, command);
  }

  private static Fixture fixture() {
    ObjectNode state = Seed.create("p5", "P5/P6", 1);
    state.withArray("courses").add(course(state));
    state.withArray("plans").add(obj("id", "FORMAL-PLAN", "name", "正式预案", "domain", "SUPPORT", "purpose", "BUSINESS", "status", "PUBLISHED"));
    return new Fixture(state, (ObjectNode) state.withArray("courses").get(0));
  }

  private static ObjectNode course(ObjectNode state) {
    ObjectNode scene = obj("id", "SUPPORT-SCENE", "version", 1, "objects", arr(obj("id", "SUPPORT-CASE", "name", "保障教学案例", "initialState", obj("status", "READY"), "stateFields", arr(obj("id", "status", "valueType", "STRING")), "actions", arr(obj("id", "CONFIRM", "label", "确认阶段", "parameters", arr())), "ports", arr())));
    ObjectNode snapshot = obj("interactionSchemaVersion", 3, "name", "保障方案教学", "description", "编制方案并处置延迟", "mode", "GUIDED", "sceneSnapshot", scene, "topologySnapshot", obj("id", "SUPPORT-TOPOLOGY", "version", 1, "connections", arr(), "rules", arr()), "steps", steps(), "scoreRule", obj("total", 100, "errorPenalty", 5, "helpPenalty", 2), "supportCaseSnapshot", obj("id", "CASE-1", "version", 1, "name", "泵组保障方案编制与延迟事件处置", "taskObject", "演示船A · 通用泵组", "scope", "合成保障教学案例", "deadline", 270, "arrivalTime", 90, "transportCost", 100, "operations", Seed.operations(false), "resources", state.path("resources").deepCopy()));
    return obj("id", "SUPPORT-COURSE", "name", "保障方案教学", "domain", "SUPPORT", "version", 1, "status", "PUBLISHED", "interactionSchemaVersion", 3, "mode", "GUIDED", "publishedSnapshot", snapshot);
  }

  private static ArrayNode steps() {
    String[] names = {"理解任务", "配置资源", "编制方案", "计算验证", "事件处置", "提交复盘"};
    int[] points = {10, 20, 20, 20, 20, 10};
    ArrayNode result = arr();
    for (int i = 0; i < names.length; i++) result.add(obj("id", "STEP-" + (i + 1), "name", names[i], "description", names[i], "target", "SUPPORT-CASE", "actionId", "CONFIRM", "actionLabel", names[i], "parameters", obj(), "precondition", NullNode.instance, "expectedResult", names[i] + "完成", "points", points[i]));
    return result;
  }

  private record Fixture(ObjectNode state, ObjectNode course) {}
}

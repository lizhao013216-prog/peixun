package cn.peixun.demo;

import static cn.peixun.demo.Json.*;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:peixun-test;DB_CLOSE_DELAY=-1",
      "spring.sql.init.mode=always"
    })
class BusinessFlowTest {
  @Autowired DemoService service;
  String workspace;

  @BeforeEach
  void createWorkspace() {
    workspace = service.createWorkspace("自动化验收", "").path("id").asText();
  }

  ObjectNode state() {
    return service.state(workspace);
  }

  ObjectNode request(String action, ObjectNode payload) {
    ObjectNode s = state();
    return obj(
        "action",
        action,
        "payload",
        payload,
        "commandId",
        UUID.randomUUID().toString(),
        "runEpoch",
        s.get("epoch"),
        "expectedRevision",
        s.get("revision"));
  }

  ObjectNode cmd(String actor, String action, ObjectNode p) {
    if (action.equals("training.action") && p.path("kind").asText().equals("PASS") && !p.has("actionId")) {
      ObjectNode a = find(state(), "attempts", p.path("id").asText());
      JsonNode st = find(state(), "courses", a.path("courseId").asText()).path("steps").get(a.path("currentStep").asInt());
      if (st != null) p.set("actionId", st.path("actionId"));
    }
    return (ObjectNode) service.command(workspace, actor, request(action, p)).get("data");
  }

  String plan() {
    String task =
        cmd("PLANNER", "task.create", obj("name", "泵组维修保障", "deadline", 270)).path("id").asText();
    String p = cmd("PLANNER", "plan.create", obj("taskId", task)).path("id").asText();
    approve(p);
    return p;
  }

  void approve(String p) {
    cmd("PLANNER", "plan.submit", obj("id", p));
    cmd("REVIEWER_L1", "plan.approve", obj("id", p));
  }

  String course(String domain, String request) throws Exception {
    String c =
        cmd(
                "AUTHOR",
                "course.create",
                obj("name", "课程 " + domain, "domain", domain, "requestId", request))
            .path("id")
            .asText();
    cmd("AUTHOR", "course.submit", obj("id", c));
    cmd("INSTRUCTOR", "course.approve", obj("id", c));
    cmd("AUTHOR", "course.publish", obj("id", c));
    Thread.sleep(1250);
    assertEquals("PUBLISHED", find(state(), "courses", c).path("status").asText());
    return c;
  }

  String attempt(String course, String scope) {
    String a = cmd("INSTRUCTOR", "training.assign", obj("courseId", course)).path("id").asText();
    return cmd(
            "LEARNER_A", "training.start", obj("assignmentId", a, "mode", "GUIDED", "scope", scope))
        .path("id")
        .asText();
  }

  void complete(String attempt, int count) {
    for (int i = 0; i < count; i++) {
      ObjectNode a = find(state(), "attempts", attempt);
      JsonNode st = find(state(), "courses", a.path("courseId").asText()).path("steps").get(a.path("currentStep").asInt());
      cmd("LEARNER_A", "training.action", obj("id", attempt, "kind", "PASS", "target", st.path("target").asText()));
    }
  }

  ObjectNode run(String plan, String scenario) {
    String r =
        cmd("DIRECTOR", "run.create", obj("planId", plan, "scenario", scenario))
            .path("id")
            .asText();
    return cmd("DIRECTOR", "run.finish", obj("id", r));
  }

  @Test
  void fullMaintenancePlanningExecutionAndArchiveChain() throws Exception {
    String p0 = plan();
    String request = cmd("PLANNER", "preparation.request", obj("id", p0)).path("id").asText();
    String c = course("MAINTENANCE", request), a = attempt(c, "INDIVIDUAL");
    for (int i = 0; i < 2; i++)
      cmd("LEARNER_A", "training.action", obj("id", a, "kind", "PASS", "target", "VALVE-01"));
    cmd("LEARNER_A", "training.action", obj("id", a, "kind", "HELP"));
    complete(a, 10);
    ObjectNode result = find(state(), "attempts", a);
    assertEquals(88, result.path("score").asDouble());
    assertEquals(83.33, result.path("correctRate").asDouble());
    cmd("INSTRUCTOR", "training.confirm", obj("id", a));
    ObjectNode base = run(p0, "BASE"), delay = run(p0, "DELAY");
    assertEquals(240, base.path("clock").asInt());
    assertEquals(300, delay.path("clock").asInt());
    assertEquals(3, delay.path("ledger").size());
    assertEquals(2, delay.path("inventory").path("consumed").asInt());
    ObjectNode branch =
        cmd("DIRECTOR", "run.branch", obj("planId", p0, "parentId", delay.get("id")));
    branch = cmd("DIRECTOR", "run.finish", obj("id", branch.get("id")));
    assertEquals(255, branch.path("clock").asInt());
    assertEquals(1005, branch.path("schedule").path("cost").asInt());
    assertEquals(300, find(state(), "runs", delay.path("id").asText()).path("clock").asInt());
    ObjectNode sensitivity = cmd("PLANNER", "sensitivity.calculate", obj("id", p0));
    assertEquals(261, sensitivity.path("mean").asDouble());
    assertEquals(80, sensitivity.path("onTimeRate").asDouble());
    for (JsonNode point : sensitivity.path("points"))
      assertEquals(
          "COMPLETED", find(state(), "runs", point.path("runId").asText()).path("status").asText());
    String p1 = cmd("PLANNER", "plan.revise", obj("id", p0, "version", "P1")).path("id").asText();
    approve(p1);
    run(p1, "BASE");
    String e = cmd("PLANNER", "execution.create", obj("planId", p1)).path("id").asText();
    cmd("WORKER", "execution.ack", obj("id", e));
    cmd("WORKER", "execution.prepare", obj("id", e));
    cmd("WORKER", "execution.report", obj("id", e));
    for (int i = 1; i <= 4; i++)
      cmd("INSPECTOR", "inspection.submit", obj("id", e, "item", i, "passed", i != 3));
    assertThrows(BusinessException.class, () -> cmd("WORKER", "execution.finish", obj("id", e)));
    cmd("WORKER", "rectification.submit", obj("id", e, "comment", "调整装配并留存复查记录"));
    cmd("INSPECTOR", "inspection.recheck", obj("id", e, "item", 3, "passed", true));
    ObjectNode finished = cmd("WORKER", "execution.finish", obj("id", e));
    JsonNode actual = finished.path("summary");
    assertEquals(270, actual.path("finish").asInt());
    assertEquals(1070, actual.path("cost").asInt());
    assertEquals(770, actual.path("labor").asInt());
    assertEquals(480, actual.path("personMinutes").asInt());
    assertEquals(75, actual.path("firstPassRate").asInt());
    assertEquals(100, actual.path("finalPassRate").asInt());
    assertEquals(5, actual.path("inspectionRecords").asInt());
    assertEquals(4, actual.path("inspectedItems").asInt());
    ObjectNode comp = cmd("PLANNER", "comparison.create", obj("executionId", e, "planId", p1));
    assertEquals(5.88, comp.path("timeDeltaRate").asDouble());
    assertEquals(6.47, comp.path("costDeltaRate").asDouble());
    String p2 = cmd("PLANNER", "plan.revise", obj("id", p1, "version", "P2")).path("id").asText();
    approve(p2);
    assertEquals(250, run(p2, "BASE").path("clock").asInt());
    String archive = cmd("PLANNER", "archive.create", obj("planId", p2)).path("id").asText();
    cmd("PLANNER", "archive.submit", obj("id", archive));
    assertThrows(
        BusinessException.class,
        () -> cmd("REVIEWER_L3", "archive.review", obj("id", archive, "approved", true)));
    cmd("REVIEWER_L1", "archive.review", obj("id", archive, "approved", true));
    cmd(
        "REVIEWER_L2",
        "archive.review",
        obj("id", archive, "approved", false, "comment", "补充改进依据"));
    assertThrows(
        BusinessException.class, () -> cmd("PLANNER", "archive.submit", obj("id", archive)));
    cmd("PLANNER", "archive.supplement", obj("id", archive, "comment", "增加装配前检查，演练缩短至250分钟"));
    cmd("PLANNER", "archive.submit", obj("id", archive));
    for (int i = 1; i <= 3; i++)
      cmd("REVIEWER_L" + i, "archive.review", obj("id", archive, "approved", true));
    ObjectNode published = cmd("PLANNER", "archive.publish", obj("id", archive));
    assertEquals(5, published.path("reviews").size());
    assertEquals(2, published.path("round").asInt());
    ObjectNode snapshot = state();
    JsonNode template = snapshot.path("templates").get(snapshot.path("templates").size() - 1);
    String task = cmd("PLANNER", "task.create", obj("name", "复用验证")).path("id").asText();
    ObjectNode reused =
        cmd("PLANNER", "plan.create", obj("taskId", task, "templateId", template.get("id")));
    assertEquals(9, reused.path("operations").size());
    assertEquals(250, reused.path("schedule").path("finish").asInt());
    assertEquals(240, find(state(), "plans", p0).path("schedule").path("finish").asInt());
  }

  @Test
  void idempotencyPermissionsResetAndRevisionAreEnforced() {
    ObjectNode request = request("task.create", obj("name", "幂等任务"));
    ObjectNode first = service.command(workspace, "PLANNER", request);
    assertEquals(first.toString(), service.command(workspace, "PLANNER", request).toString());
    assertEquals(1, state().path("tasks").size());
    ObjectNode different = request.deepCopy();
    ((ObjectNode) different.get("payload")).put("name", "不同任务");
    assertEquals(
        "IDEMPOTENCY_CONFLICT",
        assertThrows(
                BusinessException.class, () -> service.command(workspace, "PLANNER", different))
            .code);
    assertEquals(
        403,
        assertThrows(
                BusinessException.class, () -> cmd("LEARNER_A", "task.create", obj("name", "越权")))
            .status);
    ObjectNode stale = request("task.create", obj("name", "旧版本请求"));
    cmd("PLANNER", "task.create", obj("name", "最新变更"));
    assertEquals(
        "REVISION_CONFLICT",
        assertThrows(BusinessException.class, () -> service.command(workspace, "PLANNER", stale))
            .code);
    cmd("ADMIN", "workspace.reset", obj());
    assertEquals(
        "STALE_EPOCH",
        assertThrows(BusinessException.class, () -> service.command(workspace, "PLANNER", request))
            .code);
    assertEquals(0, state().path("tasks").size());
  }

  @Test
  void technicalFailuresDoNotDeductScoresAndTeamEvidenceIsRejected() throws Exception {
    String c = course("OPERATION", ""), a = attempt(c, "INDIVIDUAL");
    cmd("ADMIN", "settings.save", obj("platformProfile", "FAIL_ONCE"));
    ObjectNode failed = cmd("LEARNER_A", "training.action", obj("id", a, "kind", "PASS"));
    assertEquals(0, failed.path("errors").asInt());
    assertEquals(0, failed.path("currentStep").asInt());
    complete(a, 8);
    assertEquals(100, find(state(), "attempts", a).path("score").asInt());
    String team = attempt(c, "TEAM");
    cmd("LEARNER_A", "training.control", obj("id", team));
    complete(team, 8);
    assertThrows(
        BusinessException.class, () -> cmd("INSTRUCTOR", "training.confirm", obj("id", team)));
    String p = plan();
    String e = cmd("PLANNER", "execution.create", obj("planId", p)).path("id").asText();
    cmd("WORKER", "execution.ack", obj("id", e));
    assertThrows(BusinessException.class, () -> cmd("WORKER", "execution.prepare", obj("id", e)));
  }

  @Test
  void duplicateAndLateCallbacksCannotRegressPublishedState() {
    for (String profile : new String[] {"DUPLICATE_CALLBACK", "OUT_OF_ORDER"}) {
      ObjectNode s = Seed.create("x", "x", 1);
      ((ObjectNode) s.get("settings")).put("platformProfile", profile);
      ObjectNode c = TrainingModule.apply(s, "course.create", obj("name", "回调测试"), "AUTHOR");
      c.put("status", "APPROVED");
      ObjectNode job = TrainingModule.apply(s, "course.publish", obj("id", c.get("id")), "AUTHOR");
      job.put("dueAt", 0);
      assertTrue(TrainingModule.settleJobs(s));
      assertEquals("PUBLISHED", c.path("status").asText());
      assertEquals(1, job.path("ignoredCallbacks").asInt());
      assertFalse(TrainingModule.settleJobs(s));
    }
  }

  @Test
  void authoredTargetActionAndResultDriveLearnerFeedback() throws Exception {
    ObjectNode c = cmd("AUTHOR", "course.create", obj("domain", "OPERATION", "name", "定制教学验证"));
    ArrayNode steps = c.withArray("steps").deepCopy();
    ((ObjectNode)steps.get(0)).put("target", "SENSOR-01").put("actionId", "READ_CUSTOM")
        .put("actionLabel", "读取教学指标").put("expectedResult", "已读取定制指标，下一步检查阀门。");
    cmd("AUTHOR", "course.save", obj("id", c.path("id").asText(), "steps", steps));
    cmd("AUTHOR", "course.submit", obj("id", c.path("id").asText()));
    cmd("INSTRUCTOR", "course.approve", obj("id", c.path("id").asText()));
    cmd("AUTHOR", "course.publish", obj("id", c.path("id").asText()));
    Thread.sleep(1250);
    String a = attempt(c.path("id").asText(), "INDIVIDUAL");
    ObjectNode wrong = cmd("LEARNER_A", "training.action", obj("id", a, "kind", "PASS", "target", "PUMP-01", "actionId", "READ_CUSTOM"));
    assertEquals(0, wrong.path("currentStep").asInt());
    assertTrue(wrong.withArray("events").get(0).path("message").asText().contains("SENSOR-01"));
    wrong = cmd("LEARNER_A", "training.action", obj("id", a, "kind", "PASS", "target", "SENSOR-01", "actionId", "OTHER"));
    assertEquals(0, wrong.path("currentStep").asInt());
    assertEquals(2, wrong.path("errors").asInt());
    ObjectNode right = cmd("LEARNER_A", "training.action", obj("id", a, "kind", "PASS", "target", "SENSOR-01", "actionId", "READ_CUSTOM"));
    assertEquals(1, right.path("currentStep").asInt());
    assertEquals("已读取定制指标，下一步检查阀门。", right.withArray("events").get(2).path("message").asText());
    assertEquals("READ_CUSTOM", right.withArray("events").get(2).path("actionId").asText());
  }

  @Test
  void activeAssignmentCannotStartDuplicateAttempt() throws Exception {
    String c = course("MAINTENANCE", ""), a = attempt(c, "INDIVIDUAL");
    String assignment = find(state(), "attempts", a).path("assignmentId").asText();
    assertThrows(BusinessException.class, () -> cmd("LEARNER_A", "training.start", obj("assignmentId", assignment)));
    cmd("LEARNER_A", "training.pause", obj("id", a));
    assertThrows(BusinessException.class, () -> cmd("LEARNER_A", "training.start", obj("assignmentId", assignment)));
    assertEquals(1, state().withArray("attempts").size());
    cmd("LEARNER_A", "training.pause", obj("id", a));
    assertEquals("RUNNING", find(state(), "attempts", a).path("status").asText());
  }

  @Test
  void incompleteStepIsRejectedAndLegacyPublishedLessonsRemainUsable() {
    ObjectNode c = cmd("AUTHOR", "course.create", obj("name", "完整教学要求"));
    ArrayNode steps = c.withArray("steps").deepCopy();
    ((ObjectNode)steps.get(0)).put("expectedResult", "");
    assertThrows(BusinessException.class, () -> cmd("AUTHOR", "course.save", obj("id", c.path("id").asText(), "steps", steps)));
    assertFalse(find(state(), "courses", c.path("id").asText()).path("steps").get(0).path("expectedResult").asText().isBlank());
    ObjectNode legacy = Seed.create("legacy", "旧版课程", 1);
    ObjectNode lesson = TrainingModule.apply(legacy, "course.create", obj("name", "旧版课程"), "AUTHOR");
    lesson.remove("interactionVersion");
    lesson.put("status", "PUBLISHED");
    String assignment = TrainingModule.apply(legacy, "training.assign", obj("courseId", lesson.path("id").asText()), "INSTRUCTOR").path("id").asText();
    String attempt = TrainingModule.apply(legacy, "training.start", obj("assignmentId", assignment), "LEARNER_A").path("id").asText();
    assertEquals(1, TrainingModule.apply(legacy, "training.action", obj("id", attempt, "target", "PUMP-01", "kind", "PASS"), "LEARNER_A").path("currentStep").asInt());
  }
}

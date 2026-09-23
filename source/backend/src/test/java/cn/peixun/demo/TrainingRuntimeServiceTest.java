package cn.peixun.demo;

import static cn.peixun.demo.Json.*;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;

class TrainingRuntimeServiceTest {
  @Test
  void frozenPublishedSnapshotsDriveIndependentRuleResults() {
    ObjectNode state = fixture();
    ObjectNode course04 = course("COURSE-04", 0.4);
    ObjectNode course06 = course("COURSE-06", 0.6);
    state.withArray("courses").add(course04).add(course06);

    ObjectNode assignment04 = apply(state, "training.assign", obj("courseId", "COURSE-04", "learnerId", "LEARNER_A"), "INSTRUCTOR", "OPERATION", "C1");
    ObjectNode assignment06 = apply(state, "training.assign", obj("courseId", "COURSE-06", "learnerId", "LEARNER_B"), "INSTRUCTOR", "OPERATION", "C2");
    ((ObjectNode) course04.path("publishedSnapshot").path("topologySnapshot").path("rules").get(0).path("conditions").get(0)).put("value", 0.9);

    ObjectNode attempt04 = apply(state, "training.start", obj("assignmentId", assignment04.path("id").asText()), "LEARNER_A", "OPERATION", "C3");
    ObjectNode attempt06 = apply(state, "training.start", obj("assignmentId", assignment06.path("id").asText()), "LEARNER_B", "OPERATION", "C4");
    operate(state, attempt04, "LEARNER_A", 0.5, "C5");
    operate(state, attempt06, "LEARNER_B", 0.5, "C6");

    assertEquals("READY", attempt04.path("runtime").path("states").path("PUMP-01").path("status").asText());
    assertEquals("ALARM", attempt06.path("runtime").path("states").path("PUMP-01").path("status").asText());
    assertEquals(0.4, attempt04.path("courseSnapshot").path("topologySnapshot").path("rules").get(0).path("conditions").get(0).path("value").asDouble());
  }

  @Test
  void wrongInputPenaltiesPassAndExplicitContinueAreDeterministic() {
    ObjectNode state = fixture();
    state.withArray("courses").add(course("COURSE", 0.6));
    ObjectNode assignment = apply(state, "training.assign", obj("courseId", "COURSE", "learnerId", "LEARNER_A"), "INSTRUCTOR", "OPERATION", "C1");
    ObjectNode attempt = apply(state, "training.start", obj("assignmentId", assignment.path("id").asText()), "LEARNER_A", "OPERATION", "C2");

    apply(state, "training.action", obj("id", attempt.path("id").asText(), "kind", "PASS", "target", "PUMP-01", "actionId", "SET_VALUE", "parameters", obj("value", 0.5)), "LEARNER_A", "OPERATION", "C3");
    assertEquals(1, attempt.path("errors").asInt());
    assertEquals(0, attempt.path("currentStep").asInt());
    assertEquals("BUSINESS_ERROR", attempt.path("events").get(0).path("result").asText());

    apply(state, "training.action", obj("id", attempt.path("id").asText(), "kind", "PASS", "target", "SENSOR-01", "actionId", "SET_VALUE", "parameters", obj("value", 0.4)), "LEARNER_A", "OPERATION", "C4");
    assertEquals(2, attempt.path("errors").asInt());
    operate(state, attempt, "LEARNER_A", 0.5, "C5");
    assertTrue(attempt.path("awaitingContinue").asBoolean());
    assertEquals(90, attempt.path("score").asInt());
    assertThrows(BusinessException.class, () -> operate(state, attempt, "LEARNER_A", 0.5, "C6"));

    apply(state, "training.continue", obj("id", attempt.path("id").asText()), "LEARNER_A", "OPERATION", "C7");
    assertEquals("COMPLETED", attempt.path("status").asText());
    assertEquals(1, attempt.path("currentStep").asInt());
    assertThrows(BusinessException.class, () -> apply(state, "training.continue", obj("id", attempt.path("id").asText()), "LEARNER_A", "OPERATION", "C8"));
    assertEquals(3, attempt.path("events").size());
  }

  @Test
  void pauseTechnicalFailureStationRoutingRetrainingAndArchiveStayScoped() {
    ObjectNode state = fixture();
    state.withArray("courses").add(course("COURSE", 0.6));
    ObjectNode assignment = apply(state, "training.assign", obj("courseId", "COURSE", "learnerId", "LEARNER_A"), "INSTRUCTOR", "OPERATION", "C1");
    ObjectNode attempt = apply(state, "training.start", obj("assignmentId", assignment.path("id").asText()), "LEARNER_A", "OPERATION", "C2");

    apply(state, "training.pause", obj("id", attempt.path("id").asText()), "LEARNER_A", "OPERATION", "C3");
    assertEquals("PAUSED", attempt.path("status").asText());
    apply(state, "training.pause", obj("id", attempt.path("id").asText()), "LEARNER_A", "OPERATION", "C4");
    state.with("settings").put("platformProfile", "FAIL_ONCE");
    operate(state, attempt, "LEARNER_A", 0.5, "C5");
    assertEquals(1, attempt.path("technicalFailures").asInt());
    assertEquals(0, attempt.path("errors").asInt());
    assertFalse(attempt.path("awaitingContinue").asBoolean());

    ObjectNode station = apply(state, "training.station.save", obj("domain", "OPERATION", "name", "一号台", "mappings", arr(obj("point", "dial.value", "objectId", "SENSOR-01", "actionId", "SET_VALUE", "parameters", obj("value", 0.5)))), "INSTRUCTOR", "OPERATION", "C6");
    assertThrows(BusinessException.class, () -> apply(state, "training.station.signal", obj("id", station.path("id").asText(), "attemptId", attempt.path("id").asText(), "point", "dial.value"), "LEARNER_A", "OPERATION", "C7"));
    apply(state, "training.station.connect", obj("id", station.path("id").asText()), "INSTRUCTOR", "OPERATION", "C8");
    apply(state, "training.station.signal", obj("id", station.path("id").asText(), "attemptId", attempt.path("id").asText(), "point", "dial.value"), "LEARNER_A", "OPERATION", "C9");
    assertTrue(attempt.path("awaitingContinue").asBoolean());
    apply(state, "training.continue", obj("id", attempt.path("id").asText()), "LEARNER_A", "OPERATION", "C10");
    apply(state, "training.confirm", obj("id", attempt.path("id").asText()), "INSTRUCTOR", "OPERATION", "C11");
    apply(state, "training.confirm", obj("id", attempt.path("id").asText()), "REVIEW_TEACHER", "OPERATION", "C11-B");
    assertEquals(attempt.path("id").asText(), assignment.path("evidenceAttemptId").asText());
    assertEquals(1, state.withArray("trainingArchives").size());
    assertThrows(BusinessException.class, () -> apply(state, "training.station.signal", obj("id", station.path("id").asText(), "attemptId", attempt.path("id").asText(), "point", "dial.value"), "LEARNER_A", "OPERATION", "C11-C"));

    ObjectNode retraining = apply(state, "training.start", obj("assignmentId", assignment.path("id").asText()), "LEARNER_A", "OPERATION", "C12");
    assertNotEquals(attempt.path("id").asText(), retraining.path("id").asText());
    assertFalse(assignment.path("confirmed").asBoolean());
    assertEquals(attempt.path("id").asText(), assignment.path("evidenceAttemptId").asText());
    assertEquals(attempt.path("id").asText(), assignment.path("confirmedAttemptIds").get(0).asText());
    assertEquals("COMPLETED", attempt.path("status").asText());
    assertEquals(1, state.withArray("trainingArchives").size());
    assertThrows(BusinessException.class, () -> apply(state, "training.action", obj("id", retraining.path("id").asText(), "target", "SENSOR-01", "actionId", "SET_VALUE", "parameters", obj("value", 0.5)), "LEARNER_B", "OPERATION", "C13"));
  }

  @Test
  void secondInstructorRoleCanOperateWithoutUsingTheHardCodedAccountId() {
    ObjectNode state = fixture();
    state.withArray("courses").add(course("COURSE", 0.6));
    ObjectNode assignment = apply(state, "training.assign", obj("courseId", "COURSE", "learnerId", "LEARNER_A"), "REVIEW_TEACHER", "OPERATION", "R1");
    ObjectNode attempt = apply(state, "training.start", obj("assignmentId", assignment.path("id").asText()), "REVIEW_TEACHER", "OPERATION", "R2");
    operate(state, attempt, "REVIEW_TEACHER", 0.5, "R3");
    assertTrue(attempt.path("awaitingContinue").asBoolean());
  }

  @Test
  void assignedStationMustMatchTheFrozenCourseEnvironmentAndBeConnectedAtStart() {
    ObjectNode state = fixture();
    state.withArray("courses").add(course("COURSE", 0.6));
    ObjectNode incompatible = apply(state, "training.station.save", obj("domain", "OPERATION", "mappings", arr(obj("point", "bad", "objectId", "SENSOR-01", "actionId", "UNKNOWN", "parameters", obj()))), "INSTRUCTOR", "OPERATION", "S1");
    assertThrows(BusinessException.class, () -> apply(state, "training.assign", obj("courseId", "COURSE", "learnerId", "LEARNER_A", "stationId", incompatible.path("id").asText()), "INSTRUCTOR", "OPERATION", "S2"));

    ObjectNode station = apply(state, "training.station.save", obj("domain", "OPERATION", "mappings", arr(obj("point", "dial.value", "objectId", "SENSOR-01", "actionId", "SET_VALUE", "parameters", obj("value", 0.5)))), "INSTRUCTOR", "OPERATION", "S3");
    ObjectNode assignment = apply(state, "training.assign", obj("courseId", "COURSE", "learnerId", "LEARNER_A", "stationId", station.path("id").asText()), "INSTRUCTOR", "OPERATION", "S4");
    assertThrows(BusinessException.class, () -> apply(state, "training.start", obj("assignmentId", assignment.path("id").asText()), "LEARNER_A", "OPERATION", "S5"));
    apply(state, "training.station.connect", obj("id", station.path("id").asText()), "INSTRUCTOR", "OPERATION", "S6");
    assertEquals("RUNNING", apply(state, "training.start", obj("assignmentId", assignment.path("id").asText()), "LEARNER_A", "OPERATION", "S7").path("status").asText());
  }

  private static ObjectNode apply(ObjectNode state, String action, ObjectNode payload, String actor, String domain, String commandId) {
    return TrainingRuntimeService.apply(state, action, payload, actor, domain, commandId);
  }

  private static ObjectNode operate(ObjectNode state, ObjectNode attempt, String actor, double value, String commandId) {
    return apply(state, "training.action", obj("id", attempt.path("id").asText(), "kind", "PASS", "stepId", "STEP-1", "target", "SENSOR-01", "actionId", "SET_VALUE", "parameters", obj("value", value)), actor, "OPERATION", commandId);
  }

  private static ObjectNode fixture() {
    ObjectNode state = Seed.create("test", "P4", 1);
    state.with("settings").put("platformProfile", "SUCCESS");
    state.withArray("trainingArchives");
    return state;
  }

  private static ObjectNode course(String id, double threshold) {
    ObjectNode scene =
        obj(
            "id", "SCENE-1",
            "version", 1,
            "objects",
                arr(
                    obj(
                        "id", "SENSOR-01",
                        "name", "压力传感器",
                        "initialState", obj("value", 0.8, "status", "READY"),
                        "stateFields", arr(obj("id", "value", "valueType", "NUMBER"), obj("id", "status", "valueType", "STRING")),
                        "actions", arr(obj("id", "SET_VALUE", "label", "设置信号", "parameters", arr(obj("id", "value", "valueType", "NUMBER", "required", true)))),
                        "ports", arr()),
                    obj(
                        "id", "PUMP-01",
                        "name", "泵组",
                        "initialState", obj("status", "READY"),
                        "stateFields", arr(obj("id", "status", "valueType", "STRING")),
                        "actions", arr(obj("id", "READ_STATE", "label", "读取状态", "parameters", arr())),
                        "ports", arr())));
    ObjectNode topology =
        obj(
            "id", "TOPOLOGY-1",
            "version", 1,
            "connections", arr(),
            "rules",
                arr(
                    obj(
                        "id", "RULE-ALARM",
                        "kind", "CONDITIONAL",
                        "trigger", obj("objectId", "SENSOR-01", "actionId", "SET_VALUE"),
                        "conditions", arr(obj("objectId", "SENSOR-01", "field", "value", "operator", "LT", "value", threshold)),
                        "effects", arr(obj("objectId", "PUMP-01", "field", "status", "value", "ALARM")),
                        "rejectMessage", "信号未达到规则")));
    ObjectNode snapshot =
        obj(
            "interactionSchemaVersion", 3,
            "name", "阈值训练",
            "description", "验证发布快照规则",
            "mode", "GUIDED",
            "sceneSnapshot", scene,
            "topologySnapshot", topology,
            "steps", arr(obj("id", "STEP-1", "name", "设置信号", "description", "将信号设置为0.5", "target", "SENSOR-01", "actionId", "SET_VALUE", "actionLabel", "写入信号", "parameters", obj("value", 0.5), "precondition", NullNode.instance, "expectedResult", "信号已写入", "points", 100)),
            "scoreRule", obj("total", 100, "errorPenalty", 5, "helpPenalty", 2));
    return obj("id", id, "name", "阈值训练", "domain", "OPERATION", "version", 1, "status", "PUBLISHED", "interactionSchemaVersion", 3, "mode", "GUIDED", "publishedSnapshot", snapshot);
  }
}

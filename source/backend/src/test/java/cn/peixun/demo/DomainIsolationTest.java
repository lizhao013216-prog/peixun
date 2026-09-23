package cn.peixun.demo;

import static cn.peixun.demo.Json.*;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

class DomainIsolationTest {
  @Test
  void domainAndLearnerProjectionOnlyReturnsTheRequestedTrainingContext() {
    ObjectNode state = Seed.create("projection", "领域投影", 1);
    state
        .withArray("courses")
        .add(obj("id", "COURSE-OP", "domain", "OPERATION", "status", "PUBLISHED"))
        .add(obj("id", "COURSE-MAINT", "domain", "MAINTENANCE", "status", "PUBLISHED"));
    state
        .withArray("assignments")
        .add(
            obj(
                "id",
                "ASSIGN-OP",
                "courseId",
                "COURSE-OP",
                "domain",
                "OPERATION",
                "learnerId",
                "LEARNER_A"))
        .add(
            obj(
                "id",
                "ASSIGN-MAINT",
                "courseId",
                "COURSE-MAINT",
                "domain",
                "MAINTENANCE",
                "learnerId",
                "LEARNER_B"));
    state
        .withArray("attempts")
        .add(
            obj(
                "id",
                "ATTEMPT-OP",
                "courseId",
                "COURSE-OP",
                "assignmentId",
                "ASSIGN-OP",
                "domain",
                "OPERATION",
                "learnerId",
                "LEARNER_A"))
        .add(
            obj(
                "id",
                "ATTEMPT-MAINT",
                "courseId",
                "COURSE-MAINT",
                "assignmentId",
                "ASSIGN-MAINT",
                "domain",
                "MAINTENANCE",
                "learnerId",
                "LEARNER_B"));
    state.withArray("tasks").add(obj("id", "SUPPORT-TASK", "domain", "SUPPORT"));
    state.withArray("stations")
        .add(obj("id", "STATION-OP", "domain", "OPERATION"))
        .add(obj("id", "STATION-MAINT", "domain", "MAINTENANCE"));

    ObjectNode operation = WorkspaceProjection.forActor(state, "LEARNER_A", "OPERATION");
    assertEquals(1, operation.withArray("courses").size());
    assertEquals("COURSE-OP", operation.withArray("courses").get(0).path("id").asText());
    assertEquals(1, operation.withArray("assignments").size());
    assertEquals(1, operation.withArray("attempts").size());
    assertEquals(0, operation.withArray("tasks").size());
    assertEquals(0, operation.withArray("stations").size(), "学员未指定台位时不应看到台位目录");

    ObjectNode maintenance = WorkspaceProjection.forActor(state, "INSTRUCTOR", "MAINTENANCE");
    assertEquals(1, maintenance.withArray("courses").size());
    assertEquals("COURSE-MAINT", maintenance.withArray("courses").get(0).path("id").asText());
    assertEquals(0, maintenance.withArray("tasks").size());
    assertEquals(1, maintenance.withArray("stations").size());
    assertEquals("STATION-MAINT", maintenance.withArray("stations").get(0).path("id").asText());

    ObjectNode support = WorkspaceProjection.forActor(state, "PLANNER", "SUPPORT");
    assertEquals(0, support.withArray("courses").size());
    assertEquals(1, support.withArray("tasks").size());
  }

  @Test
  void scopedCommandCannotWriteAnotherTrainingDomain() {
    ObjectNode state = Seed.create("scope", "命令领域", 1);
    state
        .withArray("courses")
        .add(obj("id", "COURSE-OP", "domain", "OPERATION"))
        .add(obj("id", "COURSE-MAINT", "domain", "MAINTENANCE"));

    assertDoesNotThrow(
        () ->
            TrainingDomain.requireCommandScope(
                state, "training.assign", obj("courseId", "COURSE-OP"), "OPERATION"));
    BusinessException error =
        assertThrows(
            BusinessException.class,
            () ->
                TrainingDomain.requireCommandScope(
                    state,
                    "training.assign",
                    obj("courseId", "COURSE-MAINT"),
                    "OPERATION"));
    assertEquals("DOMAIN_MISMATCH", error.code);
  }
}

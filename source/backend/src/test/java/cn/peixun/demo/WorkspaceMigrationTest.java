package cn.peixun.demo;

import static cn.peixun.demo.Json.*;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

class WorkspaceMigrationTest {
  @Test
  void legacyWorkspaceMigrationIsPureIdempotentAndBackfillsDomains() {
    ObjectNode legacy = Seed.create("legacy", "旧工作区", 1);
    legacy.remove("schemaVersion");
    ObjectNode operation = obj("id", "COURSE-OP", "domain", "OPERATION");
    ObjectNode maintenance = obj("id", "COURSE-MAINT", "domain", "MAINTENANCE");
    legacy.withArray("courses").add(operation).add(maintenance);
    legacy
        .withArray("assignments")
        .add(obj("id", "ASSIGN-OP", "courseId", "COURSE-OP", "learnerId", "LEARNER_A"));
    legacy
        .withArray("attempts")
        .add(
            obj(
                "id",
                "ATTEMPT-OP",
                "assignmentId",
                "ASSIGN-OP",
                "courseId",
                "COURSE-OP",
                "learnerId",
                "LEARNER_A"));
    legacy.withArray("tasks").add(obj("id", "TASK-1"));
    ((ObjectNode) legacy.withArray("assets").get(0))
        .remove(java.util.List.of("ownerSystem", "visibility", "sharedWith"));

    ObjectNode preview = WorkspaceMigration.preview(legacy);
    assertEquals(1, preview.path("missingAssignmentDomains").asInt());
    assertEquals(1, preview.path("missingAttemptDomains").asInt());

    ObjectNode migrated = WorkspaceMigration.migrate(legacy);
    assertFalse(legacy.has("schemaVersion"), "迁移不得修改输入夹具");
    assertEquals(2, migrated.path("schemaVersion").asInt());
    assertEquals("OPERATION", migrated.withArray("assignments").get(0).path("domain").asText());
    assertEquals("OPERATION", migrated.withArray("attempts").get(0).path("domain").asText());
    assertEquals("SUPPORT", migrated.withArray("tasks").get(0).path("domain").asText());
    assertEquals("BUSINESS", migrated.withArray("tasks").get(0).path("purpose").asText());
    assertEquals("SHARED", migrated.withArray("assets").get(0).path("ownerSystem").asText());
    assertEquals(migrated, WorkspaceMigration.migrate(migrated));
  }

  @Test
  void supportIsKnownButCourseAuthoringIsNotOpenedInP1() {
    assertFalse(TrainingDomain.require("SUPPORT").courseAuthoringEnabled());
    ObjectNode state = Seed.create("x", "x", 1);
    BusinessException error =
        assertThrows(
            BusinessException.class,
            () ->
                TrainingModule.apply(
                    state,
                    "course.create",
                    obj("domain", "SUPPORT", "name", "保障教学课件"),
                    "AUTHOR"));
    assertTrue(error.getMessage().contains("后续阶段"));
  }
}

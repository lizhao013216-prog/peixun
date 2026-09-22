package cn.peixun.demo;

import static cn.peixun.demo.Json.*;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
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
    assertEquals(7, migrated.path("schemaVersion").asInt());
    assertEquals("OPERATION", migrated.withArray("assignments").get(0).path("domain").asText());
    assertEquals("OPERATION", migrated.withArray("attempts").get(0).path("domain").asText());
    assertEquals("SUPPORT", migrated.withArray("tasks").get(0).path("domain").asText());
    assertEquals("BUSINESS", migrated.withArray("tasks").get(0).path("purpose").asText());
    assertEquals("SHARED", migrated.withArray("assets").get(0).path("ownerSystem").asText());
    assertEquals(2, migrated.withArray("simulationProjects").size());
    assertTrue(migrated.has("simulationPreviews"));
    assertEquals("COURSE-OP", migrated.withArray("attempts").get(0).path("courseId").asText());
    assertEquals("LEARNER_A", migrated.withArray("attempts").get(0).path("learnerId").asText());
    assertEquals(migrated, WorkspaceMigration.migrate(migrated));
  }

  @Test
  void supportCourseAuthoringIsOpenedInP5() {
    assertTrue(TrainingDomain.require("SUPPORT").courseAuthoringEnabled());
    ObjectNode state = Seed.create("x", "x", 1);
    ObjectNode course =
        TrainingModule.apply(
            state,
            "course.create",
            obj("domain", "SUPPORT", "name", "保障教学课件"),
            "AUTHOR");
    assertEquals("SUPPORT", course.path("domain").asText());
    assertEquals("DRAFT", course.path("status").asText());
  }

  @Test
  void schemaVersion2FixtureMigratesOnceWithoutRewritingBusinessEvidence() {
    ObjectNode v2 = Seed.create("v2-fixture", "V2迁移夹具", 1);
    v2.put("schemaVersion", 2);
    for (String collection :
        new String[] {
          "simulationProjects", "scenes", "topologies", "systemTemplates", "simulationPreviews"
        }) v2.remove(collection);
    for (JsonNode item : v2.withArray("assets"))
      ((ObjectNode) item)
          .remove(java.util.List.of("familyId", "editRevision", "actions", "ports", "stateFields"));
    v2.withArray("courses")
        .add(obj("id", "COURSE-V2", "name", "旧维修课程", "domain", "MAINTENANCE", "status", "PUBLISHED", "version", 3));
    v2.withArray("assignments")
        .add(obj("id", "ASSIGN-V2", "courseId", "COURSE-V2", "domain", "MAINTENANCE", "learnerId", "LEARNER_A"));
    v2.withArray("attempts")
        .add(obj("id", "ATTEMPT-V2", "courseId", "COURSE-V2", "assignmentId", "ASSIGN-V2", "domain", "MAINTENANCE", "learnerId", "LEARNER_A", "score", 93));
    v2.withArray("tasks")
        .add(obj("id", "TASK-V2", "name", "旧保障任务", "domain", "SUPPORT", "purpose", "BUSINESS", "status", "APPROVED"));
    v2.withArray("plans")
        .add(obj("id", "PLAN-V2", "taskId", "TASK-V2", "domain", "SUPPORT", "purpose", "BUSINESS", "status", "APPROVED"));

    JsonNode courses = v2.path("courses").deepCopy();
    JsonNode assignments = v2.path("assignments").deepCopy();
    JsonNode attempts = v2.path("attempts").deepCopy();
    JsonNode tasks = v2.path("tasks").deepCopy();
    JsonNode plans = v2.path("plans").deepCopy();
    JsonNode planTemplates = v2.path("templates").deepCopy();

    ObjectNode migrated = WorkspaceMigration.migrate(v2);
    ObjectNode repeated = WorkspaceMigration.migrate(migrated);
    assertEquals(7, migrated.path("schemaVersion").asInt());
    assertEquals(courses, migrated.path("courses"));
    assertEquals(assignments, migrated.path("assignments"));
    assertEquals(attempts, migrated.path("attempts"));
    assertEquals(tasks, migrated.path("tasks"));
    assertEquals(plans, migrated.path("plans"));
    assertEquals(planTemplates, migrated.path("templates"));
    assertEquals(2, migrated.withArray("simulationProjects").size());
    assertEquals(2, repeated.withArray("simulationProjects").size());
    assertEquals(migrated.withArray("scenes").size(), repeated.withArray("scenes").size());
    assertEquals(migrated.withArray("topologies").size(), repeated.withArray("topologies").size());
    assertEquals(migrated.withArray("systemTemplates").size(), repeated.withArray("systemTemplates").size());
    assertEquals(migrated.withArray("curricula").size(), repeated.withArray("curricula").size());
    assertEquals(migrated, repeated);
  }
}

package cn.peixun.demo;

import static cn.peixun.demo.Json.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;

public final class WorkspaceMigration {
  public static final int CURRENT_SCHEMA_VERSION = 2;

  private WorkspaceMigration() {}

  public static ObjectNode migrate(ObjectNode source) {
    ObjectNode s = source.deepCopy();
    if (s.path("schemaVersion").asInt(1) >= CURRENT_SCHEMA_VERSION) return s;

    Map<String, String> courseDomains = new HashMap<>();
    for (JsonNode course : s.withArray("courses")) {
      ObjectNode c = (ObjectNode) course;
      String domain = c.path("domain").asText("MAINTENANCE");
      TrainingDomain.require(domain);
      c.put("domain", domain);
      courseDomains.put(c.path("id").asText(), domain);
    }

    Map<String, String> assignmentDomains = new HashMap<>();
    for (JsonNode assignment : s.withArray("assignments")) {
      ObjectNode a = (ObjectNode) assignment;
      String domain =
          a.path("domain")
              .asText(courseDomains.getOrDefault(a.path("courseId").asText(), "MAINTENANCE"));
      TrainingDomain.require(domain);
      a.put("domain", domain);
      assignmentDomains.put(a.path("id").asText(), domain);
    }

    Map<String, String> attemptDomains = new HashMap<>();
    for (JsonNode attempt : s.withArray("attempts")) {
      ObjectNode a = (ObjectNode) attempt;
      String domain =
          a.path("domain")
              .asText(
                  courseDomains.getOrDefault(
                      a.path("courseId").asText(),
                      assignmentDomains.getOrDefault(
                          a.path("assignmentId").asText(), "MAINTENANCE")));
      TrainingDomain.require(domain);
      a.put("domain", domain);
      attemptDomains.put(a.path("id").asText(), domain);
    }

    for (JsonNode feedback : s.withArray("feedback")) {
      ObjectNode f = (ObjectNode) feedback;
      f.put(
          "domain",
          f.path("domain")
              .asText(
                  courseDomains.getOrDefault(
                      f.path("courseId").asText(),
                      attemptDomains.getOrDefault(
                          f.path("attemptId").asText(), "MAINTENANCE"))));
    }

    for (JsonNode asset : s.withArray("assets")) {
      ObjectNode a = (ObjectNode) asset;
      if (!a.hasNonNull("ownerSystem")) a.put("ownerSystem", "SHARED");
      if (!a.hasNonNull("visibility")) a.put("visibility", "SHARED");
      if (!a.has("sharedWith")) a.set("sharedWith", arr("OPERATION", "MAINTENANCE", "SUPPORT"));
    }

    for (String collection :
        new String[] {
          "tasks", "plans", "runs", "executions", "archives", "comparisons", "evaluations"
        })
      for (JsonNode record : s.withArray(collection)) {
        ObjectNode item = (ObjectNode) record;
        if (!item.hasNonNull("domain")) item.put("domain", "SUPPORT");
        if (!item.hasNonNull("purpose")) item.put("purpose", "BUSINESS");
      }

    s.put("schemaVersion", CURRENT_SCHEMA_VERSION);
    return s;
  }

  public static ObjectNode preview(ObjectNode source) {
    int missingCourseDomains = 0, missingAssignmentDomains = 0, missingAttemptDomains = 0;
    for (JsonNode item : source.withArray("courses")) if (!item.hasNonNull("domain")) missingCourseDomains++;
    for (JsonNode item : source.withArray("assignments"))
      if (!item.hasNonNull("domain")) missingAssignmentDomains++;
    for (JsonNode item : source.withArray("attempts")) if (!item.hasNonNull("domain")) missingAttemptDomains++;
    return obj(
        "fromVersion",
        source.path("schemaVersion").asInt(1),
        "toVersion",
        CURRENT_SCHEMA_VERSION,
        "courseCount",
        source.withArray("courses").size(),
        "assignmentCount",
        source.withArray("assignments").size(),
        "attemptCount",
        source.withArray("attempts").size(),
        "missingCourseDomains",
        missingCourseDomains,
        "missingAssignmentDomains",
        missingAssignmentDomains,
        "missingAttemptDomains",
        missingAttemptDomains);
  }
}

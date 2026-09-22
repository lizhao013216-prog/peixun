package cn.peixun.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import java.util.function.Predicate;

public final class WorkspaceProjection {
  private WorkspaceProjection() {}

  public static ObjectNode forActor(ObjectNode source, String actor, String requestedDomain) {
    String domain = requestedDomain == null ? "" : requestedDomain.trim();
    if (!domain.isEmpty()) TrainingDomain.require(domain);
    ObjectNode result = source.deepCopy();

    if (!domain.isEmpty()) applyDomain(result, domain);
    if (DemoService.role(actor).equals("LEARNER")) applyLearner(result, actor);
    return result;
  }

  private static void applyDomain(ObjectNode s, String domain) {
    if (domain.equals("SUPPORT")) {
      clear(s, "courses", "assignments", "attempts", "feedback", "issues", "jobs", "stations");
      return;
    }
    retain(s.withArray("courses"), item -> domain.equals(item.path("domain").asText()));
    retain(s.withArray("assignments"), item -> domain.equals(item.path("domain").asText()));
    retain(s.withArray("attempts"), item -> domain.equals(item.path("domain").asText()));
    retain(s.withArray("feedback"), item -> domain.equals(item.path("domain").asText()));
    Set<String> attemptIds = ids(s.withArray("attempts"));
    retain(
        s.withArray("issues"),
        item -> attemptIds.contains(item.path("sourceId").asText()) || domain.equals(item.path("domain").asText()));
    clear(
        s,
        "tasks",
        "plans",
        "resources",
        "runs",
        "orders",
        "executions",
        "archives",
        "subplans",
        "analyses",
        "requests",
        "histories",
        "documents",
        "comparisons",
        "evaluations");
  }

  private static void applyLearner(ObjectNode s, String actor) {
    retain(s.withArray("assignments"), item -> actor.equals(item.path("learnerId").asText()));
    retain(s.withArray("attempts"), item -> actor.equals(item.path("learnerId").asText()));
    Set<String> courseIds = new HashSet<>();
    for (JsonNode item : s.withArray("assignments")) courseIds.add(item.path("courseId").asText());
    for (JsonNode item : s.withArray("attempts")) courseIds.add(item.path("courseId").asText());
    retain(s.withArray("courses"), item -> courseIds.contains(item.path("id").asText()));
    Set<String> attemptIds = ids(s.withArray("attempts"));
    retain(s.withArray("feedback"), item -> attemptIds.contains(item.path("attemptId").asText()));
    retain(
        s.withArray("issues"),
        item ->
            actor.equals(item.path("createdBy").asText())
                || attemptIds.contains(item.path("sourceId").asText()));
    retain(s.withArray("events"), item -> actor.equals(item.path("actor").asText()));
    clear(
        s,
        "tasks",
        "plans",
        "resources",
        "runs",
        "orders",
        "executions",
        "archives",
        "subplans",
        "analyses",
        "requests",
        "jobs",
        "histories",
        "documents",
        "comparisons",
        "evaluations");
  }

  private static Set<String> ids(ArrayNode items) {
    Set<String> result = new HashSet<>();
    for (JsonNode item : items) result.add(item.path("id").asText());
    return result;
  }

  private static void clear(ObjectNode s, String... collections) {
    for (String collection : collections) s.putArray(collection);
  }

  private static void retain(ArrayNode items, Predicate<JsonNode> predicate) {
    ArrayNode kept = items.arrayNode();
    for (JsonNode item : items) if (predicate.test(item)) kept.add(item);
    items.removeAll();
    items.addAll(kept);
  }
}

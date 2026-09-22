package cn.peixun.demo;

import static cn.peixun.demo.Json.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;

public final class WorkspaceMigration {
  public static final int CURRENT_SCHEMA_VERSION = 7;

  private WorkspaceMigration() {}

  public static ObjectNode migrate(ObjectNode source) {
    ObjectNode s = source.deepCopy();
    int version = s.path("schemaVersion").asInt(1);
    if (version < 2) {
      migrateV1ToV2(s);
      version = 2;
      s.put("schemaVersion", version);
    }
    if (version < 3) {
      migrateV2ToV3(s);
      version = 3;
      s.put("schemaVersion", version);
    }
    if (version < 4) {
      migrateV3ToV4(s);
      version = 4;
      s.put("schemaVersion", version);
    }
    if (version < 5) {
      migrateV4ToV5(s);
      version = 5;
      s.put("schemaVersion", version);
    }
    if (version < 6) {
      migrateV5ToV6(s);
      version = 6;
      s.put("schemaVersion", version);
    }
    if (version < 7) {
      migrateV6ToV7(s);
      s.put("schemaVersion", 7);
    }
    return s;
  }

  private static void migrateV1ToV2(ObjectNode s) {
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
  }

  private static void migrateV2ToV3(ObjectNode s) {
    s.withArray("simulationProjects");
    s.withArray("scenes");
    s.withArray("topologies");
    s.withArray("systemTemplates");

    for (JsonNode asset : s.withArray("assets")) {
      ObjectNode a = (ObjectNode) asset;
      if (!a.hasNonNull("familyId")) a.put("familyId", a.path("id").asText());
      if (!a.hasNonNull("editRevision")) a.put("editRevision", 1);
      if (!a.has("actions"))
        a.set(
            "actions",
            arr(
                obj("id", "CONFIRM", "label", "确认/识别"),
                obj("id", "READ_STATE", "label", "读取模拟状态"),
                obj("id", "RECORD_CHECK", "label", "保存检查结论")));
      if (!a.has("ports")) a.set("ports", arr());
    }

    addLegacyProject(s, "OPERATION");
    addLegacyProject(s, "MAINTENANCE");
    migrateLegacySystemTemplates(s);
  }

  private static void migrateV3ToV4(ObjectNode s) {
    s.withArray("simulationPreviews");
    Map<String, ObjectNode> assets = new HashMap<>();
    for (JsonNode item : s.withArray("assets")) {
      ObjectNode asset = (ObjectNode) item;
      SimulationCapabilities.enrichAsset(asset);
      assets.put(asset.path("id").asText(), asset);
    }
    for (JsonNode sceneItem : s.withArray("scenes"))
      for (JsonNode objectItem : sceneItem.withArray("objects")) {
        ObjectNode object = (ObjectNode) objectItem;
        ObjectNode asset = assets.get(object.path("assetRef").path("id").asText());
        if (asset != null) SimulationCapabilities.enrichObject(object, asset);
      }
  }

  private static void migrateV4ToV5(ObjectNode s) {
    s.withArray("systemTemplates");
    for (JsonNode item : s.withArray("systemTemplates")) {
      ObjectNode template = (ObjectNode) item;
      if (!template.hasNonNull("familyId")) template.put("familyId", template.path("id").asText());
      if (!template.hasNonNull("visibility")) template.put("visibility", "PRIVATE");
      if (!template.has("sharedWith")) template.set("sharedWith", arr());
      if (!template.hasNonNull("usageInstructions"))
        template.put("usageInstructions", "旧模板只读保留；复制前请核对来源配置。");
    }
  }

  private static void migrateV5ToV6(ObjectNode s) {
    s.withArray("curricula");
  }

  private static void migrateV6ToV7(ObjectNode s) {
    s.withArray("trainingArchives");
  }

  private static void addLegacyProject(ObjectNode s, String domain) {
    String projectId = "PROJECT-LEGACY-" + domain;
    if (hasId(s.withArray("simulationProjects"), projectId)) return;
    String sceneId = "SCENE-LEGACY-" + domain;
    String topologyId = "TOPOLOGY-LEGACY-" + domain;
    ObjectNode legacyScene = s.path("scene").isObject() ? (ObjectNode) s.path("scene") : obj();
    ObjectNode legacyTopology =
        s.path("topology").isObject() ? (ObjectNode) s.path("topology") : obj();

    ArrayNode objects = arr();
    String[][] definitions = {
      {"PUMP-01", "通用泵组", "ASSET-PUMP", "18", "52"},
      {"VALVE-01", "电动阀门", "ASSET-VALVE", "42", "32"},
      {"CTRL-01", "控制单元", "ASSET-CTRL", "67", "54"},
      {"SENSOR-01", "状态传感器", "ASSET-SENSOR", "78", "28"}
    };
    for (String[] item : definitions)
      objects.add(
          obj(
              "id",
              item[0],
              "name",
              item[1],
              "assetRef",
              obj("id", item[2], "version", 1),
              "x",
              Integer.parseInt(item[3]),
              "y",
              Integer.parseInt(item[4]),
              "view",
              "设备",
              "initialState",
              obj("status", item[0].equals("PUMP-01") ? "READY" : "AVAILABLE")));

    ObjectNode scene =
        obj(
            "id",
            sceneId,
            "projectId",
            projectId,
            "domain",
            domain,
            "version",
            1,
            "editRevision",
            1,
            "status",
            "LEGACY_READ_ONLY",
            "objects",
            objects,
            "environment",
            obj(
                "weather",
                legacyScene.path("weather").asText("晴"),
                "light",
                legacyScene.path("light").asText("日间"),
                "camera",
                legacyScene.path("camera").asText("总览"),
                "material",
                legacyScene.path("material").asText("金属 · 拉丝钢")));
    s.withArray("scenes").add(scene);

    ObjectNode topology =
        obj(
            "id",
            topologyId,
            "projectId",
            projectId,
            "domain",
            domain,
            "sceneRef",
            obj("id", sceneId, "version", 1),
            "version",
            1,
            "editRevision",
            1,
            "status",
            "LEGACY_READ_ONLY",
            "legacySnapshot",
            legacyTopology.deepCopy(),
            "connections",
            arr(),
            "rules",
            arr());
    s.withArray("topologies").add(topology);

    s.withArray("simulationProjects")
        .add(
            obj(
                "id",
                projectId,
                "familyId",
                projectId,
                "version",
                1,
                "editRevision",
                1,
                "domain",
                domain,
                "name",
                domain.equals("OPERATION") ? "旧操作场景与组网配置" : "旧维修场景配置",
                "purpose",
                "旧配置兼容，只读查看后可复制为独立工程",
                "status",
                "LEGACY_READ_ONLY",
                "linkageMode",
                "SIGNAL_GRAPH",
                "sceneRef",
                obj("id", sceneId, "version", 1),
                "topologyRef",
                obj("id", topologyId, "version", 1),
                "sourceRef",
                obj("type", "LEGACY_SINGLETON", "id", "scene/topology"),
                "createdBy",
                "MIGRATION",
                "updatedBy",
                "MIGRATION",
                "updatedAt",
                s.path("createdAt").asText("1970-01-01T00:00:00Z")));
  }

  private static void migrateLegacySystemTemplates(ObjectNode s) {
    for (JsonNode template : s.withArray("templates")) {
      if (template.path("type").asText().equals("PLAN") || !template.has("scene")) continue;
      String id = "SYSTEM-" + template.path("id").asText();
      if (hasId(s.withArray("systemTemplates"), id)) continue;
      s.withArray("systemTemplates")
          .add(
              obj(
                  "id",
                  id,
                  "name",
                  template.path("name").asText("旧仿真模板"),
                  "domain",
                  template.path("type").asText("OPERATION"),
                  "version",
                  template.path("version").asInt(1),
                  "status",
                  "LEGACY_READ_ONLY",
                  "sourceRef",
                  obj("type", "LEGACY_TEMPLATE", "id", template.path("id").asText())));
    }
  }

  private static boolean hasId(ArrayNode items, String id) {
    for (JsonNode item : items) if (id.equals(item.path("id").asText())) return true;
    return false;
  }

  public static ObjectNode preview(ObjectNode source) {
    int missingCourseDomains = 0, missingAssignmentDomains = 0, missingAttemptDomains = 0;
    for (JsonNode item : source.withArray("courses"))
      if (!item.hasNonNull("domain")) missingCourseDomains++;
    for (JsonNode item : source.withArray("assignments"))
      if (!item.hasNonNull("domain")) missingAssignmentDomains++;
    for (JsonNode item : source.withArray("attempts"))
      if (!item.hasNonNull("domain")) missingAttemptDomains++;
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
        "projectCount",
        source.withArray("simulationProjects").size(),
        "missingCourseDomains",
        missingCourseDomains,
        "missingAssignmentDomains",
        missingAssignmentDomains,
        "missingAttemptDomains",
        missingAttemptDomains);
  }
}

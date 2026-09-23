package cn.peixun.demo;

import static cn.peixun.demo.BusinessException.require;
import static cn.peixun.demo.Json.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.time.Instant;
import java.util.*;

public final class SimulationProjectService {
  private SimulationProjectService() {}

  public static ObjectNode apply(
      ObjectNode state, String action, ObjectNode payload, String actor, String scopeDomain) {
    return switch (action) {
      case "simulationProject.create" -> create(state, payload, actor, scopeDomain);
      case "simulationProject.save" -> save(state, payload, actor, scopeDomain);
      case "simulationProject.clone" -> cloneProject(state, payload, actor, scopeDomain);
      default -> throw new BusinessException(400, "UNKNOWN_COMMAND", "不支持的编排工程命令：" + action);
    };
  }

  private static ObjectNode create(
      ObjectNode state, ObjectNode payload, String actor, String scopeDomain) {
    String domain = payload.path("domain").asText();
    requireScope(domain, scopeDomain);
    String name = payload.path("name").asText().trim();
    require(!name.isBlank(), "请填写工程名称");
    String linkageMode = payload.path("linkageMode").asText("NONE");
    require(linkageMode.matches("NONE|SIGNAL_GRAPH"), "联动模式不正确");

    String familyId = id("SIM-FAMILY");
    String projectId = id("SIM-PROJECT");
    String sceneId = id("SIM-SCENE");
    String topologyId = linkageMode.equals("SIGNAL_GRAPH") ? id("SIM-TOPOLOGY") : "";
    String now = Instant.now().toString();
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
            "DRAFT",
            "objects",
            arr(),
            "environment",
            obj("weather", "晴", "light", "日间", "camera", "总览", "material", "标准"));
    state.withArray("scenes").add(scene);

    JsonNode topologyRef = NullNode.instance;
    if (!topologyId.isBlank()) {
      ObjectNode topology =
          obj(
              "id",
              topologyId,
              "projectId",
              projectId,
              "domain",
              domain,
              "sceneRef",
              ref(sceneId, 1),
              "version",
              1,
              "editRevision",
              1,
              "status",
              "DRAFT",
              "connections",
              arr(),
              "rules",
              arr());
      state.withArray("topologies").add(topology);
      topologyRef = ref(topologyId, 1);
    }

    ObjectNode project =
        obj(
            "id",
            projectId,
            "familyId",
            familyId,
            "version",
            1,
            "editRevision",
            1,
            "domain",
            domain,
            "name",
            name,
            "purpose",
            payload.path("purpose").asText(""),
            "status",
            "DRAFT",
            "linkageMode",
            linkageMode,
            "sceneRef",
            ref(sceneId, 1),
            "topologyRef",
            topologyRef,
            "sourceRef",
            NullNode.instance,
            "createdBy",
            actor,
            "updatedBy",
            actor,
            "createdAt",
            now,
            "updatedAt",
            now);
    state.withArray("simulationProjects").add(project);
    return response(project, scene, arr("场景暂时为空，可保存草稿后继续完善对象。"));
  }

  private static ObjectNode save(
      ObjectNode state, ObjectNode payload, String actor, String scopeDomain) {
    ObjectNode project = find(state, "simulationProjects", payload.path("id").asText());
    requireScope(project.path("domain").asText(), scopeDomain);
    require(project.path("status").asText().equals("DRAFT"), "旧配置和已发布版本只读，请先复制或修订");
    int expected = payload.path("expectedEditRevision").asInt(-1);
    if (expected != project.path("editRevision").asInt())
      throw new BusinessException(409, "EDIT_CONFLICT", "工程已被其他窗口修改，请保留本地内容并重新核对");
    require(payload.path("scene").isObject(), "缺少场景编辑内容");
    ObjectNode input = (ObjectNode) payload.path("scene");
    require(input.path("objects").isArray(), "场景对象必须是数组");

    ObjectNode scene = find(state, "scenes", project.path("sceneRef").path("id").asText());
    Set<String> previousIds = ids(scene.withArray("objects"));
    ArrayNode cleanObjects = arr();
    Set<String> nextIds = new HashSet<>();
    for (JsonNode raw : input.withArray("objects")) {
      String objectId = raw.path("id").asText().trim();
      require(objectId.matches("[A-Za-z0-9][A-Za-z0-9_-]{1,39}"), "对象编号需为2～40位字母、数字、横线或下划线");
      require(nextIds.add(objectId), "场景对象编号重复：" + objectId);
      String objectName = raw.path("name").asText().trim();
      require(!objectName.isBlank(), "对象名称不能为空：" + objectId);
      String assetId = raw.path("assetRef").path("id").asText();
      int assetVersion = raw.path("assetRef").path("version").asInt(-1);
      ObjectNode asset = requireAssetReference(state, assetId, assetVersion, project.path("domain").asText());
      double x = raw.path("x").asDouble(Double.NaN);
      double y = raw.path("y").asDouble(Double.NaN);
      require(Double.isFinite(x) && Double.isFinite(y) && x >= 0 && x <= 100 && y >= 0 && y <= 100, "对象位置必须在0～100范围内：" + objectId);
      ObjectNode clean =
          obj(
              "id",
              objectId,
              "name",
              objectName,
              "assetRef",
              ref(assetId, assetVersion),
              "assetName",
              asset.path("name").asText(),
              "x",
              x,
              "y",
              y,
              "view",
              raw.path("view").asText("设备"),
              "initialState",
              raw.path("initialState").isObject()
                  ? raw.path("initialState").deepCopy()
                  : asset.path("initialState").deepCopy(),
              "actions",
              asset.path("actions").deepCopy());
      SimulationCapabilities.enrichObject(clean, asset);
      cleanObjects.add(clean);
    }
    Set<String> removed = new HashSet<>(previousIds);
    removed.removeAll(nextIds);
    rejectReferencedRemoval(state, project, removed);

    ObjectNode environment =
        obj(
            "weather",
            input.path("environment").path("weather").asText("晴"),
            "light",
            input.path("environment").path("light").asText("日间"),
            "camera",
            input.path("environment").path("camera").asText("总览"),
            "material",
            input.path("environment").path("material").asText("标准"));
    scene.set("objects", cleanObjects);
    scene.set("environment", environment);
    scene.put("editRevision", scene.path("editRevision").asInt(1) + 1);
    project.put("name", payload.path("name").asText(project.path("name").asText()).trim());
    require(!project.path("name").asText().isBlank(), "请填写工程名称");
    project.put("purpose", payload.path("purpose").asText(project.path("purpose").asText()));
    project.put("editRevision", project.path("editRevision").asInt() + 1);
    project.put("updatedBy", actor).put("updatedAt", Instant.now().toString());
    ArrayNode warnings = arr();
    if (cleanObjects.isEmpty()) warnings.add("工程尚未添加对象，可以继续保存草稿，但不能进入后续发布阶段。");
    if (project.path("linkageMode").asText().equals("SIGNAL_GRAPH") && cleanObjects.size() < 2)
      warnings.add("信号图工程至少需要两个具备匹配端口的对象，才能建立设备连接。");
    return response(project, scene, warnings);
  }

  private static ObjectNode cloneProject(
      ObjectNode state, ObjectNode payload, String actor, String scopeDomain) {
    ObjectNode source = find(state, "simulationProjects", payload.path("id").asText());
    String targetDomain = payload.path("domain").asText();
    requireScope(targetDomain, scopeDomain);
    require(source.path("domain").asText().equals(targetDomain), "P2-A仅支持在所属系统内复制工程");
    ObjectNode sourceScene = find(state, "scenes", source.path("sceneRef").path("id").asText());
    for (JsonNode object : sourceScene.withArray("objects"))
      requireAssetReference(
          state,
          object.path("assetRef").path("id").asText(),
          object.path("assetRef").path("version").asInt(),
          targetDomain);

    ObjectNode created =
        create(
            state,
            obj(
                "domain",
                targetDomain,
                "name",
                payload.path("name").asText(source.path("name").asText() + " · 副本"),
                "purpose",
                source.path("purpose").asText(),
                "linkageMode",
                source.path("linkageMode").asText("NONE")),
            actor,
            scopeDomain);
    ObjectNode project = find(state, "simulationProjects", created.path("id").asText());
    ObjectNode scene = find(state, "scenes", project.path("sceneRef").path("id").asText());
    scene.set("objects", sourceScene.path("objects").deepCopy());
    scene.set("environment", sourceScene.path("environment").deepCopy());
    if (!source.path("topologyRef").isNull() && !project.path("topologyRef").isNull()) {
      ObjectNode sourceTopology =
          find(state, "topologies", source.path("topologyRef").path("id").asText());
      ObjectNode targetTopology =
          find(state, "topologies", project.path("topologyRef").path("id").asText());
      targetTopology.set("connections", sourceTopology.path("connections").deepCopy());
      targetTopology.set("rules", sourceTopology.path("rules").deepCopy());
      if (sourceTopology.has("legacySnapshot"))
        targetTopology.set("legacySnapshot", sourceTopology.path("legacySnapshot").deepCopy());
    }
    project.set(
        "sourceRef",
        obj("type", "SIMULATION_PROJECT", "id", source.path("id").asText(), "version", source.path("version").asInt()));
    project.put("updatedAt", Instant.now().toString());
    return response(project, scene, arr());
  }

  public static boolean assetVisible(JsonNode asset, String domain) {
    String owner = asset.path("ownerSystem").asText();
    if (owner.equals("SHARED") || owner.equals(domain)) return true;
    if (!asset.path("visibility").asText().equals("SHARED")) return false;
    for (JsonNode shared : asset.path("sharedWith")) if (domain.equals(shared.asText())) return true;
    return false;
  }

  public static ObjectNode requireAssetReference(
      ObjectNode state, String assetId, int version, String domain) {
    ObjectNode asset = find(state, "assets", assetId);
    require(asset.path("version").asInt() == version, "素材版本不匹配：" + assetId);
    require(asset.path("status").asText().equals("APPROVED"), "素材尚未批准或已停用：" + assetId);
    if (!assetVisible(asset, domain))
      throw new BusinessException(403, "ASSET_NOT_SHARED", "素材未授权给当前系统：" + asset.path("name").asText());
    return asset;
  }

  private static void requireScope(String domain, String scopeDomain) {
    TrainingDomain.require(domain);
    if (scopeDomain == null || scopeDomain.isBlank())
      throw new BusinessException(400, "DOMAIN_REQUIRED", "编排工程命令必须指定当前系统");
    if (!domain.equals(scopeDomain))
      throw new BusinessException(403, "DOMAIN_MISMATCH", "当前系统不能操作其他系统的编排工程");
  }

  private static void rejectReferencedRemoval(
      ObjectNode state, ObjectNode project, Set<String> removed) {
    if (removed.isEmpty() || project.path("topologyRef").isNull()) return;
    ObjectNode topology = find(state, "topologies", project.path("topologyRef").path("id").asText());
    for (JsonNode connection : topology.withArray("connections")) {
      String source = connection.path("source").path("objectId").asText();
      String target = connection.path("target").path("objectId").asText();
      if (removed.contains(source) || removed.contains(target))
        throw new BusinessException(409, "OBJECT_IN_USE", "对象仍被设备关系引用，请先在 P2-B 处理中移除关系");
    }
  }

  private static Set<String> ids(ArrayNode objects) {
    Set<String> result = new HashSet<>();
    for (JsonNode object : objects) result.add(object.path("id").asText());
    return result;
  }

  private static ObjectNode ref(String id, int version) {
    return obj("id", id, "version", version);
  }

  private static ObjectNode response(ObjectNode project, ObjectNode scene, ArrayNode warnings) {
    ObjectNode result = project.deepCopy();
    result.set("scene", scene.deepCopy());
    result.set("warnings", warnings);
    return result;
  }
}

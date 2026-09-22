package cn.peixun.demo;

import static cn.peixun.demo.BusinessException.require;
import static cn.peixun.demo.Json.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.time.Instant;
import java.util.*;

/** Command seam for validating, publishing and reusing immutable simulation templates. */
public final class SimulationTemplateService {
  private SimulationTemplateService() {}

  public static ObjectNode apply(
      ObjectNode state, String action, ObjectNode payload, String actor, String domain) {
    return switch (action) {
      case "simulationTemplate.validate" -> inspect(state, payload, domain).report();
      case "simulationTemplate.publish" -> publish(state, payload, actor, domain);
      case "simulationTemplate.instantiate" -> instantiate(state, payload, actor, domain);
      case "simulationTemplate.preview" -> preview(state, payload, actor, domain);
      default -> throw new BusinessException(400, "UNKNOWN_COMMAND", "不支持的仿真模板命令：" + action);
    };
  }

  private static ObjectNode publish(
      ObjectNode state, ObjectNode payload, String actor, String domain) {
    Inspection inspection = inspect(state, payload, domain);
    ObjectNode project = inspection.project();
    int version = nextVersion(state.withArray("systemTemplates"), project.path("familyId").asText());
    String now = Instant.now().toString();
    ArrayNode sharedWith = normalizedDomains(payload.path("sharedWith"), project.path("domain").asText());
    validateDependencySharing(state, inspection.dependencies(), sharedWith);

    ObjectNode template =
        obj(
            "id", id("SIM-TEMPLATE"),
            "familyId", project.path("familyId").asText(),
            "name", project.path("name").asText(),
            "purpose", project.path("purpose").asText(),
            "usageInstructions", payload.path("usageInstructions").asText().trim(),
            "domain", project.path("domain").asText(),
            "version", version,
            "status", "PUBLISHED",
            "visibility", sharedWith.isEmpty() ? "PRIVATE" : "SHARED",
            "sharedWith", sharedWith,
            "linkageMode", project.path("linkageMode").asText(),
            "sourceRef",
                obj(
                    "type", "SIMULATION_PROJECT",
                    "id", project.path("id").asText(),
                    "version", project.path("version").asInt(),
                    "editRevision", project.path("editRevision").asInt()),
            "previousVersionRef", previousVersionRef(state.withArray("systemTemplates"), project.path("familyId").asText()),
            "dependencies", inspection.dependencies().deepCopy(),
            "sceneSnapshot", inspection.scene().deepCopy(),
            "topologySnapshot", inspection.topology().deepCopy(),
            "objectCount", inspection.scene().withArray("objects").size(),
            "connectionCount", inspection.topology().withArray("connections").size(),
            "ruleCount", inspection.topology().withArray("rules").size(),
            "publishedBy", actor,
            "publishedAt", now);
    state.withArray("systemTemplates").add(template);
    project.set("lastPublishedRef", obj("id", template.path("id").asText(), "version", version));
    project.put("lastPublishedAt", now);
    project.put("editRevision", project.path("editRevision").asInt() + 1);
    project.put("updatedBy", actor).put("updatedAt", now);
    return template.deepCopy();
  }

  private static ObjectNode instantiate(
      ObjectNode state, ObjectNode payload, String actor, String domain) {
    ObjectNode template = requireTemplate(state, payload.path("id").asText(), domain);
    String targetDomain = payload.path("domain").asText();
    requireScope(targetDomain, domain);
    require(templateVisible(template, targetDomain), "模板未授权给当前系统");
    validateCurrentDependencies(state, template.withArray("dependencies"), targetDomain);
    boolean revision = payload.path("mode").asText("COPY").equals("REVISION");
    if (revision)
      require(template.path("domain").asText().equals(targetDomain), "只能在模板所属系统中创建修订");

    ObjectNode created =
        SimulationProjectService.apply(
            state,
            "simulationProject.create",
            obj(
                "domain", targetDomain,
                "name",
                    payload.path("name").asText(
                        revision
                            ? template.path("name").asText() + " · 修订草稿"
                            : template.path("name").asText() + " · 副本"),
                "purpose", template.path("purpose").asText(),
                "linkageMode", template.path("linkageMode").asText("NONE")),
            actor,
            domain);
    ObjectNode project = find(state, "simulationProjects", created.path("id").asText());
    ObjectNode scene = find(state, "scenes", project.path("sceneRef").path("id").asText());
    scene.set("objects", template.path("sceneSnapshot").path("objects").deepCopy());
    scene.set("environment", template.path("sceneSnapshot").path("environment").deepCopy());
    scene.set(
        "sourceRef",
        obj("type", "SYSTEM_TEMPLATE", "id", template.path("id").asText(), "version", template.path("version").asInt()));
    if (project.path("linkageMode").asText().equals("SIGNAL_GRAPH")) {
      ObjectNode topology = find(state, "topologies", project.path("topologyRef").path("id").asText());
      topology.set("connections", template.path("topologySnapshot").path("connections").deepCopy());
      topology.set("rules", template.path("topologySnapshot").path("rules").deepCopy());
    }
    project.set(
        "sourceRef",
        obj(
            "type", revision ? "SYSTEM_TEMPLATE_REVISION" : "SYSTEM_TEMPLATE",
            "id", template.path("id").asText(),
            "version", template.path("version").asInt(),
            "familyId", template.path("familyId").asText()));
    if (revision) project.put("familyId", template.path("familyId").asText());
    ObjectNode result = project.deepCopy();
    result.set("scene", scene.deepCopy());
    result.set("warnings", arr());
    return result;
  }

  private static ObjectNode preview(
      ObjectNode state, ObjectNode payload, String actor, String domain) {
    ObjectNode template = requireTemplate(state, payload.path("id").asText(), domain);
    ObjectNode project =
        obj(
            "id", template.path("id").asText(),
            "version", template.path("version").asInt(),
            "domain", domain);
    ObjectNode topology =
        template.path("topologySnapshot").isObject()
            ? (ObjectNode) template.path("topologySnapshot")
            : obj("connections", arr(), "rules", arr());
    ObjectNode result =
        SimulationRuleEngine.createPreview(
            project, (ObjectNode) template.path("sceneSnapshot"), topology, actor);
    result.set("templateRef", obj("id", template.path("id").asText(), "version", template.path("version").asInt()));
    state.withArray("simulationPreviews").add(result);
    return result.deepCopy();
  }

  private static Inspection inspect(ObjectNode state, ObjectNode payload, String domain) {
    ObjectNode project = find(state, "simulationProjects", payload.path("projectId").asText());
    requireScope(project.path("domain").asText(), domain);
    require(project.path("status").asText().equals("DRAFT"), "只能发布可编辑的草稿工程");
    require(!project.path("name").asText().isBlank(), "工程名称不能为空");
    require(!project.path("purpose").asText().isBlank(), "发布前请填写工程用途");
    require(!payload.path("usageInstructions").asText().trim().isBlank(), "发布前请填写模板使用说明");
    ObjectNode scene = find(state, "scenes", project.path("sceneRef").path("id").asText());
    require(!scene.withArray("objects").isEmpty(), "发布前至少添加一个场景对象");
    requireEnvironment(scene.path("environment"));

    Set<String> ids = new HashSet<>();
    ArrayNode dependencies = arr();
    Set<String> dependencyKeys = new HashSet<>();
    for (JsonNode item : scene.withArray("objects")) {
      ObjectNode object = (ObjectNode) item;
      String objectId = object.path("id").asText();
      require(ids.add(objectId), "场景对象编号重复：" + objectId);
      ObjectNode asset =
          SimulationProjectService.requireAssetReference(
              state,
              object.path("assetRef").path("id").asText(),
              object.path("assetRef").path("version").asInt(-1),
              project.path("domain").asText());
      requireCapabilities(object, asset);
      requireInitialState(object);
      String key = asset.path("id").asText() + "@" + asset.path("version").asInt();
      if (dependencyKeys.add(key))
        dependencies.add(
            obj(
                "assetRef", obj("id", asset.path("id").asText(), "version", asset.path("version").asInt()),
                "name", asset.path("name").asText(),
                "category", asset.path("category").asText(),
                "ownerSystem", asset.path("ownerSystem").asText(),
                "visibility", asset.path("visibility").asText()));
    }

    ObjectNode topology;
    if (project.path("linkageMode").asText().equals("SIGNAL_GRAPH")) {
      require(project.path("topologyRef").isObject(), "信号图工程缺少设备关系引用");
      topology = find(state, "topologies", project.path("topologyRef").path("id").asText());
      require(
          topology.path("sceneRef").path("id").asText().equals(scene.path("id").asText()),
          "设备关系引用的场景与工程不一致");
      require(!topology.withArray("connections").isEmpty(), "信号图工程发布前至少配置一条设备连接");
      require(!topology.withArray("rules").isEmpty(), "信号图工程发布前至少配置一条联动规则");
      topology =
          SimulationRuleEngine.normalizeTopology(
              scene, topology.path("connections"), topology.path("rules"));
    } else {
      require(project.path("linkageMode").asText().equals("NONE"), "工程联动模式不正确");
      topology = obj("connections", arr(), "rules", arr());
    }
    ObjectNode report =
        obj(
            "valid", true,
            "projectId", project.path("id").asText(),
            "objectCount", scene.withArray("objects").size(),
            "dependencyCount", dependencies.size(),
            "connectionCount", topology.withArray("connections").size(),
            "ruleCount", topology.withArray("rules").size(),
            "linkageMode", project.path("linkageMode").asText(),
            "message", "配置检查通过，可以发布为不可变模板版本");
    return new Inspection(project, scene, topology, dependencies, report);
  }

  public static boolean templateVisible(JsonNode template, String domain) {
    if (domain.equals(template.path("domain").asText())) return true;
    if (!template.path("visibility").asText().equals("SHARED")) return false;
    for (JsonNode item : template.path("sharedWith")) if (domain.equals(item.asText())) return true;
    return false;
  }

  private static ObjectNode requireTemplate(ObjectNode state, String id, String domain) {
    ObjectNode template = find(state, "systemTemplates", id);
    require(template.path("status").asText().equals("PUBLISHED"), "请选择已发布的仿真系统模板");
    if (!templateVisible(template, domain))
      throw new BusinessException(403, "TEMPLATE_NOT_SHARED", "模板未授权给当前系统");
    require(template.path("sceneSnapshot").isObject(), "旧兼容模板不支持当前发布与复用流程");
    return template;
  }

  private static void validateCurrentDependencies(
      ObjectNode state, ArrayNode dependencies, String domain) {
    for (JsonNode dependency : dependencies)
      SimulationProjectService.requireAssetReference(
          state,
          dependency.path("assetRef").path("id").asText(),
          dependency.path("assetRef").path("version").asInt(-1),
          domain);
  }

  private static void validateDependencySharing(
      ObjectNode state, ArrayNode dependencies, ArrayNode sharedWith) {
    for (JsonNode target : sharedWith)
      for (JsonNode dependency : dependencies)
        SimulationProjectService.requireAssetReference(
            state,
            dependency.path("assetRef").path("id").asText(),
            dependency.path("assetRef").path("version").asInt(-1),
            target.asText());
  }

  private static void requireCapabilities(ObjectNode object, ObjectNode asset) {
    requireSameCapabilities(object.withArray("actions"), asset.withArray("actions"), "动作", "id");
    requireSameCapabilities(object.withArray("ports"), asset.withArray("ports"), "端口", "id", "direction", "valueType", "field");
    requireSameCapabilities(object.withArray("stateFields"), asset.withArray("stateFields"), "状态字段", "id", "valueType");
  }

  private static void requireSameCapabilities(
      ArrayNode actual, ArrayNode expected, String label, String... fields) {
    require(actual.size() == expected.size(), label + "声明与素材版本不一致");
    for (JsonNode reference : expected) {
      JsonNode candidate = null;
      for (JsonNode item : actual)
        if (item.path("id").asText().equals(reference.path("id").asText())) candidate = item;
      require(candidate != null, label + "声明缺失：" + reference.path("id").asText());
      for (String field : fields)
        require(
            candidate.path(field).equals(reference.path(field)),
            label + "声明不匹配：" + reference.path("id").asText() + "." + field);
    }
  }

  private static void requireInitialState(ObjectNode object) {
    Map<String, String> fields = new HashMap<>();
    for (JsonNode field : object.withArray("stateFields"))
      fields.put(field.path("id").asText(), field.path("valueType").asText());
    object.path("initialState").fields().forEachRemaining(entry -> {
      require(fields.containsKey(entry.getKey()), "初始状态字段未声明：" + object.path("id").asText() + "." + entry.getKey());
      String type = fields.get(entry.getKey());
      JsonNode value = entry.getValue();
      require(
          (type.equals("BOOLEAN") && value.isBoolean())
              || (type.equals("NUMBER") && value.isNumber())
              || (type.equals("STRING") && value.isTextual()),
          "初始状态类型不正确：" + object.path("id").asText() + "." + entry.getKey());
    });
  }

  private static void requireEnvironment(JsonNode environment) {
    require(environment.isObject(), "场景环境配置缺失");
    for (String field : List.of("weather", "light", "camera", "material"))
      require(!environment.path(field).asText().trim().isBlank(), "场景环境参数不能为空：" + field);
  }

  private static ArrayNode normalizedDomains(JsonNode raw, String ownerDomain) {
    ArrayNode result = arr();
    Set<String> unique = new LinkedHashSet<>();
    if (raw.isArray())
      for (JsonNode item : raw) {
        String domain = item.asText();
        TrainingDomain.require(domain);
        require(!domain.equals(ownerDomain), "模板无需共享给所属系统");
        if (unique.add(domain)) result.add(domain);
      }
    return result;
  }

  private static int nextVersion(ArrayNode templates, String familyId) {
    int version = 0;
    for (JsonNode item : templates)
      if (familyId.equals(item.path("familyId").asText())) version = Math.max(version, item.path("version").asInt());
    return version + 1;
  }

  private static JsonNode previousVersionRef(ArrayNode templates, String familyId) {
    JsonNode previous = null;
    for (JsonNode item : templates)
      if (familyId.equals(item.path("familyId").asText())
          && (previous == null || item.path("version").asInt() > previous.path("version").asInt())) previous = item;
    return previous == null
        ? NullNode.instance
        : obj("id", previous.path("id").asText(), "version", previous.path("version").asInt());
  }

  private static void requireScope(String actual, String requested) {
    TrainingDomain.require(actual);
    if (requested == null || requested.isBlank())
      throw new BusinessException(400, "DOMAIN_REQUIRED", "仿真模板命令必须指定当前系统");
    if (!actual.equals(requested))
      throw new BusinessException(403, "DOMAIN_MISMATCH", "当前系统不能操作其他系统的仿真模板");
  }

  private record Inspection(
      ObjectNode project,
      ObjectNode scene,
      ObjectNode topology,
      ArrayNode dependencies,
      ObjectNode report) {}
}

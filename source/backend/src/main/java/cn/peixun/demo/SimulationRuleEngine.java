package cn.peixun.demo;

import static cn.peixun.demo.Json.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.time.Instant;
import java.util.*;

/** Validates and executes the finite simulation rule language shared by preview and later training. */
public final class SimulationRuleEngine {
  private static final Set<String> OPERATORS = Set.of("EQ", "NE", "LT", "LTE", "GT", "GTE");
  private static final Set<String> RULE_KINDS = Set.of("PRECONDITION", "CONDITIONAL");
  private static final Set<String> ACTIONS =
      Set.of("START", "STOP", "RESET", "CONFIRM", "READ_STATE", "SET_READY", "SET_VALUE");
  private static final Set<String> STATUS_VALUES =
      Set.of("READY", "RUNNING", "STOPPED", "ALARM", "CONFIRMED", "AVAILABLE");

  private SimulationRuleEngine() {}

  public static ObjectNode normalizeTopology(
      ObjectNode scene, JsonNode rawConnections, JsonNode rawRules) {
    if (!rawConnections.isArray() || !rawRules.isArray()) invalid("连接和规则必须是数组");
    ArrayNode connections = arr();
    for (JsonNode raw : rawConnections) {
      if (raw.has("script") || raw.has("expression")) invalid("连接不支持脚本或表达式");
      connections.add(
          obj(
              "id", requiredId(raw, "连接"),
              "source",
                  obj(
                      "objectId", raw.path("source").path("objectId").asText(),
                      "port", raw.path("source").path("port").asText()),
              "target",
                  obj(
                      "objectId", raw.path("target").path("objectId").asText(),
                      "port", raw.path("target").path("port").asText()),
              "valueType", raw.path("valueType").asText()));
    }
    ArrayNode rules = arr();
    for (JsonNode raw : rawRules) {
      if (raw.has("script") || raw.has("expression")) invalid("规则不支持脚本或表达式");
      ArrayNode conditions = arr();
      if (!raw.path("conditions").isArray()) invalid("规则条件必须是数组：" + raw.path("id").asText());
      for (JsonNode condition : raw.path("conditions")) {
        FieldRef field = fieldRef(condition);
        conditions.add(
            obj(
                "objectId", field.objectId(),
                "field", field.field(),
                "operator", condition.path("operator").asText(),
                "value", condition.get("value")));
      }
      ArrayNode effects = arr();
      if (!raw.path("effects").isArray()) invalid("规则效果必须是数组：" + raw.path("id").asText());
      for (JsonNode effect : raw.path("effects"))
        effects.add(
            obj(
                "objectId", effect.path("objectId").asText(),
                "field", effect.path("field").asText(),
                "value", effect.get("value")));
      rules.add(
          obj(
              "id", requiredId(raw, "规则"),
              "kind", raw.path("kind").asText("PRECONDITION"),
              "trigger",
                  obj(
                      "objectId", raw.path("trigger").path("objectId").asText(),
                      "actionId", raw.path("trigger").path("actionId").asText()),
              "conditions", conditions,
              "effects", effects,
              "rejectMessage", raw.path("rejectMessage").asText("前置条件未满足")));
    }
    ObjectNode normalized = obj("connections", connections, "rules", rules);
    validate(scene, normalized);
    return normalized;
  }

  public static void validate(ObjectNode scene, ObjectNode topology) {
    Map<String, ObjectNode> objects = objects(scene);
    Set<String> connectionIds = new HashSet<>();
    Map<String, Set<String>> graph = new HashMap<>();
    for (String id : objects.keySet()) graph.put(id, new HashSet<>());
    for (JsonNode connection : topology.withArray("connections")) {
      String id = connection.path("id").asText();
      if (!connectionIds.add(id)) invalid("连接编号重复：" + id);
      String sourceId = connection.path("source").path("objectId").asText();
      String targetId = connection.path("target").path("objectId").asText();
      ObjectNode source = requireObject(objects, sourceId, "连接 " + id + " 的来源对象不存在");
      ObjectNode target = requireObject(objects, targetId, "连接 " + id + " 的目标对象不存在");
      if (sourceId.equals(targetId)) invalid("连接不能形成对象自环：" + id);
      ObjectNode sourcePort = requirePort(source, connection.path("source").path("port").asText(), id);
      ObjectNode targetPort = requirePort(target, connection.path("target").path("port").asText(), id);
      if (!sourcePort.path("direction").asText().equals("OUTPUT"))
        invalid("连接 " + id + " 的来源必须是输出端口");
      if (!targetPort.path("direction").asText().equals("INPUT"))
        invalid("连接 " + id + " 的目标必须是输入端口");
      String sourceType = sourcePort.path("valueType").asText();
      String targetType = targetPort.path("valueType").asText();
      String declaredType = connection.path("valueType").asText();
      if (!sourceType.equals(targetType) || !sourceType.equals(declaredType))
        invalid("连接 " + id + " 的数据类型不匹配");
      graph.get(sourceId).add(targetId);
    }
    topologicalOrder(graph);

    Set<String> ruleIds = new HashSet<>();
    for (JsonNode rule : topology.withArray("rules")) {
      String id = rule.path("id").asText();
      if (!ruleIds.add(id)) invalid("规则编号重复：" + id);
      String kind = rule.path("kind").asText();
      if (!RULE_KINDS.contains(kind)) invalid("规则类型不正确：" + id);
      String triggerObject = rule.path("trigger").path("objectId").asText();
      String actionId = rule.path("trigger").path("actionId").asText();
      ObjectNode trigger = requireObject(objects, triggerObject, "规则 " + id + " 的触发对象不存在");
      if (findById(trigger.withArray("actions"), actionId) == null)
        invalid("规则 " + id + " 使用了对象未声明的动作：" + actionId);
      if (rule.withArray("conditions").isEmpty()) invalid("规则至少需要一个条件：" + id);
      if (rule.withArray("effects").isEmpty()) invalid("规则至少需要一个状态变化：" + id);
      for (JsonNode condition : rule.withArray("conditions")) {
        ObjectNode object =
            requireObject(objects, condition.path("objectId").asText(), "规则 " + id + " 的条件对象不存在");
        ObjectNode field = requireField(object, condition.path("field").asText(), id);
        String operator = condition.path("operator").asText();
        if (!OPERATORS.contains(operator)) invalid("规则 " + id + " 的比较运算不受支持：" + operator);
        if (!field.path("valueType").asText().equals("NUMBER")
            && Set.of("LT", "LTE", "GT", "GTE").contains(operator))
          invalid("规则 " + id + " 只有数值字段支持大小比较");
        validateValue(condition.get("value"), field.path("valueType").asText(), condition.path("field").asText());
      }
      for (JsonNode effect : rule.withArray("effects")) {
        ObjectNode object =
            requireObject(objects, effect.path("objectId").asText(), "规则 " + id + " 的效果对象不存在");
        ObjectNode field = requireField(object, effect.path("field").asText(), id);
        validateValue(effect.get("value"), field.path("valueType").asText(), effect.path("field").asText());
      }
    }
  }

  public static ObjectNode createPreview(
      ObjectNode project, ObjectNode scene, ObjectNode topology, String actor) {
    validate(scene, topology);
    ObjectNode initialStates = obj();
    for (JsonNode object : scene.withArray("objects"))
      initialStates.set(object.path("id").asText(), object.path("initialState").deepCopy());
    String now = Instant.now().toString();
    return obj(
        "id", id("SIM-PREVIEW"),
        "projectRef", obj("id", project.path("id").asText(), "version", project.path("version").asInt()),
        "domain", project.path("domain").asText(),
        "status", "ACTIVE",
        "createdBy", actor,
        "objectsSnapshot", scene.path("objects").deepCopy(),
        "topologySnapshot", topology.deepCopy(),
        "initialStates", initialStates,
        "states", initialStates.deepCopy(),
        "events", arr(),
        "sequence", 0,
        "createdAt", now,
        "updatedAt", now);
  }

  public static ObjectNode execute(
      ObjectNode preview, String objectId, String actionId, ObjectNode parameters) {
    if (!preview.path("status").asText().equals("ACTIVE"))
      throw new BusinessException(409, "PREVIEW_CLOSED", "调试实例已结束");
    ObjectNode scene = obj("objects", preview.path("objectsSnapshot"));
    ObjectNode topology = (ObjectNode) preview.path("topologySnapshot");
    validate(scene, topology);
    Map<String, ObjectNode> objects = objects(scene);
    ObjectNode object = requireObject(objects, objectId, "调试对象不存在：" + objectId);
    ObjectNode action = findById(object.withArray("actions"), actionId);
    if (action == null || !ACTIONS.contains(actionId))
      throw new BusinessException(400, "ACTION_NOT_SUPPORTED", "对象不支持动作：" + actionId);
    validateParameters(action, parameters);

    ObjectNode before = preview.path("states").deepCopy();
    ObjectNode candidate = before.deepCopy();
    applyBaseAction(candidate, preview.path("initialStates"), objectId, actionId, parameters);
    propagate(candidate, objects, topology.withArray("connections"));
    ArrayNode matched = arr();
    String rejection = "";
    for (JsonNode rule : topology.withArray("rules")) {
      if (!objectId.equals(rule.path("trigger").path("objectId").asText())
          || !actionId.equals(rule.path("trigger").path("actionId").asText())) continue;
      boolean matches = matches(candidate, rule.withArray("conditions"));
      if (!matches && rule.path("kind").asText().equals("PRECONDITION")) {
        rejection = rule.path("rejectMessage").asText("前置条件未满足");
        break;
      }
      if (!matches) continue;
      matched.add(obj("id", rule.path("id").asText(), "kind", rule.path("kind").asText()));
      for (JsonNode effect : rule.withArray("effects"))
        state(candidate, effect.path("objectId").asText())
            .set(effect.path("field").asText(), effect.path("value").deepCopy());
    }
    if (rejection.isBlank()) {
      propagate(candidate, objects, topology.withArray("connections"));
      preview.set("states", candidate);
    }
    int sequence = preview.path("sequence").asInt() + 1;
    ObjectNode event =
        obj(
            "id", id("SIM-EVENT"),
            "sequence", sequence,
            "objectId", objectId,
            "actionId", actionId,
            "parameters", parameters.deepCopy(),
            "beforeState", before,
            "afterState", rejection.isBlank() ? candidate : before,
            "result", rejection.isBlank() ? "SUCCEEDED" : "REJECTED",
            "reason", rejection.isBlank() ? "动作与规则已确定性执行" : rejection,
            "matchedRules", matched,
            "at", Instant.now().toString());
    preview.withArray("events").add(event);
    preview.put("sequence", sequence).put("updatedAt", Instant.now().toString());
    return event;
  }

  /** Uses the same finite comparison vocabulary for course-step gates and simulation rules. */
  public static boolean matchesCondition(ObjectNode states, JsonNode condition) {
    if (condition == null || condition.isNull() || condition.isMissingNode()) return true;
    JsonNode actual =
        state(states, condition.path("objectId").asText()).get(condition.path("field").asText());
    return compare(actual, condition.path("operator").asText(), condition.get("value"));
  }

  public static ObjectNode reset(ObjectNode preview) {
    if (!preview.path("status").asText().equals("ACTIVE"))
      throw new BusinessException(409, "PREVIEW_CLOSED", "调试实例已结束");
    ObjectNode before = preview.path("states").deepCopy();
    preview.set("states", preview.path("initialStates").deepCopy());
    int sequence = preview.path("sequence").asInt() + 1;
    ObjectNode event =
        obj(
            "id", id("SIM-EVENT"),
            "sequence", sequence,
            "actionId", "RESET_PREVIEW",
            "beforeState", before,
            "afterState", preview.path("states").deepCopy(),
            "result", "SUCCEEDED",
            "reason", "已恢复本调试实例的初始快照",
            "matchedRules", arr(),
            "at", Instant.now().toString());
    preview.withArray("events").add(event);
    preview.put("sequence", sequence).put("updatedAt", Instant.now().toString());
    return event;
  }

  private static void validateParameters(ObjectNode action, ObjectNode parameters) {
    for (JsonNode definition : action.withArray("parameters")) {
      String id = definition.path("id").asText();
      JsonNode value = parameters.get(id);
      if (definition.path("required").asBoolean() && (value == null || value.isNull()))
        invalid("动作缺少参数：" + id);
      validateValue(value, definition.path("valueType").asText(), id);
    }
  }

  private static void applyBaseAction(
      ObjectNode states, JsonNode initialStates, String objectId, String actionId, ObjectNode parameters) {
    ObjectNode target = state(states, objectId);
    switch (actionId) {
      case "START" -> target.put("status", "RUNNING");
      case "STOP" -> target.put("status", "STOPPED");
      case "RESET" -> states.set(objectId, initialStates.path(objectId).deepCopy());
      case "CONFIRM" -> target.put("status", "CONFIRMED");
      case "SET_READY" -> target.set("ready", parameters.path("value").deepCopy());
      case "SET_VALUE" -> target.set("value", parameters.path("value").deepCopy());
      case "READ_STATE" -> { }
      default -> throw new BusinessException(400, "ACTION_NOT_SUPPORTED", "不支持的动作：" + actionId);
    }
  }

  private static void propagate(
      ObjectNode states, Map<String, ObjectNode> objects, ArrayNode connections) {
    Map<String, Set<String>> graph = new HashMap<>();
    for (String id : objects.keySet()) graph.put(id, new HashSet<>());
    for (JsonNode connection : connections)
      graph.get(connection.path("source").path("objectId").asText())
          .add(connection.path("target").path("objectId").asText());
    for (String sourceId : topologicalOrder(graph))
      for (JsonNode connection : connections) {
        if (!sourceId.equals(connection.path("source").path("objectId").asText())) continue;
        ObjectNode sourceObject = objects.get(sourceId);
        ObjectNode targetObject = objects.get(connection.path("target").path("objectId").asText());
        ObjectNode sourcePort = findById(sourceObject.withArray("ports"), connection.path("source").path("port").asText());
        ObjectNode targetPort = findById(targetObject.withArray("ports"), connection.path("target").path("port").asText());
        JsonNode value = state(states, sourceId).get(sourcePort.path("field").asText());
        if (value != null)
          state(states, targetObject.path("id").asText())
              .set(targetPort.path("field").asText(), value.deepCopy());
      }
  }

  private static boolean matches(ObjectNode states, ArrayNode conditions) {
    for (JsonNode condition : conditions) {
      JsonNode actual = state(states, condition.path("objectId").asText()).get(condition.path("field").asText());
      if (!compare(actual, condition.path("operator").asText(), condition.get("value"))) return false;
    }
    return true;
  }

  private static boolean compare(JsonNode actual, String operator, JsonNode expected) {
    if (actual == null || actual.isNull()) return false;
    return switch (operator) {
      case "EQ" -> actual.equals(expected);
      case "NE" -> !actual.equals(expected);
      case "LT" -> actual.asDouble() < expected.asDouble();
      case "LTE" -> actual.asDouble() <= expected.asDouble();
      case "GT" -> actual.asDouble() > expected.asDouble();
      case "GTE" -> actual.asDouble() >= expected.asDouble();
      default -> false;
    };
  }

  private static List<String> topologicalOrder(Map<String, Set<String>> graph) {
    Map<String, Integer> indegree = new HashMap<>();
    for (String id : graph.keySet()) indegree.put(id, 0);
    for (Set<String> targets : graph.values())
      for (String target : targets) indegree.put(target, indegree.get(target) + 1);
    PriorityQueue<String> ready = new PriorityQueue<>();
    indegree.forEach((id, degree) -> { if (degree == 0) ready.add(id); });
    List<String> result = new ArrayList<>();
    while (!ready.isEmpty()) {
      String id = ready.remove();
      result.add(id);
      for (String target : graph.get(id)) {
        int next = indegree.merge(target, -1, Integer::sum);
        if (next == 0) ready.add(target);
      }
    }
    if (result.size() != graph.size()) invalid("设备连接存在循环，请移除循环后再保存");
    return result;
  }

  private static Map<String, ObjectNode> objects(ObjectNode scene) {
    Map<String, ObjectNode> result = new LinkedHashMap<>();
    for (JsonNode item : scene.withArray("objects")) {
      ObjectNode object = (ObjectNode) item;
      String id = object.path("id").asText();
      if (result.put(id, object) != null) invalid("场景对象编号重复：" + id);
    }
    return result;
  }

  private static ObjectNode requireObject(
      Map<String, ObjectNode> objects, String id, String message) {
    ObjectNode object = objects.get(id);
    if (object == null) invalid(message);
    return object;
  }

  private static ObjectNode requirePort(ObjectNode object, String id, String connectionId) {
    ObjectNode port = findById(object.withArray("ports"), id);
    if (port == null) invalid("连接 " + connectionId + " 使用了对象未声明的端口：" + id);
    return port;
  }

  private static ObjectNode requireField(ObjectNode object, String id, String ruleId) {
    ObjectNode field = findById(object.withArray("stateFields"), id);
    if (field == null) invalid("规则 " + ruleId + " 使用了对象未声明的状态字段：" + id);
    return field;
  }

  private static ObjectNode findById(ArrayNode values, String id) {
    for (JsonNode item : values)
      if (id.equals(item.path("id").asText())) return (ObjectNode) item;
    return null;
  }

  private static ObjectNode state(ObjectNode states, String objectId) {
    JsonNode value = states.get(objectId);
    if (value instanceof ObjectNode object) return object;
    ObjectNode created = obj();
    states.set(objectId, created);
    return created;
  }

  private static void validateValue(JsonNode value, String type, String field) {
    if (value == null || value.isNull()) invalid("字段值不能为空：" + field);
    boolean valid =
        switch (type) {
          case "BOOLEAN" -> value.isBoolean();
          case "NUMBER" -> value.isNumber() && Double.isFinite(value.asDouble());
          case "STRING" -> value.isTextual();
          default -> false;
        };
    if (!valid) invalid("字段 " + field + " 的值类型必须为 " + type);
    if (field.equals("status") && !STATUS_VALUES.contains(value.asText()))
      invalid("状态值不受支持：" + value.asText());
  }

  private static String requiredId(JsonNode item, String label) {
    String id = item.path("id").asText().trim();
    if (!id.matches("[A-Za-z0-9][A-Za-z0-9_-]{1,39}")) invalid(label + "编号格式不正确");
    return id;
  }

  private static FieldRef fieldRef(JsonNode condition) {
    String objectId = condition.path("objectId").asText();
    String field = condition.path("field").asText();
    if (objectId.isBlank() && field.contains(".")) {
      int split = field.lastIndexOf('.');
      objectId = field.substring(0, split);
      field = field.substring(split + 1);
    }
    return new FieldRef(objectId, field);
  }

  private static void invalid(String message) {
    throw new BusinessException(400, "SIMULATION_CONFIG_INVALID", message);
  }

  private record FieldRef(String objectId, String field) {}
}

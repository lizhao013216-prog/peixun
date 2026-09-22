package cn.peixun.demo;

import static cn.peixun.demo.BusinessException.require;
import static cn.peixun.demo.Json.*;

import com.fasterxml.jackson.databind.node.*;
import java.time.Instant;

/** Command seam for persistent topology editing and isolated simulation previews. */
public final class SimulationRuntimeService {
  private SimulationRuntimeService() {}

  public static ObjectNode apply(
      ObjectNode state, String action, ObjectNode payload, String actor, String domain) {
    return switch (action) {
      case "simulationTopology.save" -> saveTopology(state, payload, actor, domain);
      case "simulationPreview.start" -> start(state, payload, actor, domain);
      case "simulationPreview.action" -> act(state, payload, actor, domain);
      case "simulationPreview.reset" -> reset(state, payload, actor, domain);
      case "simulationPreview.close" -> close(state, payload, actor, domain);
      default -> throw new BusinessException(400, "UNKNOWN_COMMAND", "不支持的仿真调试命令：" + action);
    };
  }

  private static ObjectNode saveTopology(
      ObjectNode state, ObjectNode payload, String actor, String domain) {
    ObjectNode project = find(state, "simulationProjects", payload.path("projectId").asText());
    requireScope(project.path("domain").asText(), domain);
    require(project.path("status").asText().equals("DRAFT"), "只允许编辑草稿工程的设备关系");
    require(project.path("linkageMode").asText().equals("SIGNAL_GRAPH"), "NONE模式工程不保存设备连接");
    ObjectNode topology = find(state, "topologies", project.path("topologyRef").path("id").asText());
    int expected = payload.path("expectedEditRevision").asInt(-1);
    if (expected != topology.path("editRevision").asInt())
      throw new BusinessException(409, "EDIT_CONFLICT", "设备关系已被其他窗口修改，请保留本地内容并重新核对");
    ObjectNode scene = find(state, "scenes", project.path("sceneRef").path("id").asText());
    ObjectNode normalized =
        SimulationRuleEngine.normalizeTopology(
            scene, payload.path("connections"), payload.path("rules"));
    topology.set("connections", normalized.path("connections"));
    topology.set("rules", normalized.path("rules"));
    topology.put("editRevision", topology.path("editRevision").asInt() + 1);
    project.put("editRevision", project.path("editRevision").asInt() + 1);
    project.put("updatedBy", actor).put("updatedAt", Instant.now().toString());
    return topology.deepCopy();
  }

  private static ObjectNode start(
      ObjectNode state, ObjectNode payload, String actor, String domain) {
    ObjectNode project = find(state, "simulationProjects", payload.path("projectId").asText());
    requireScope(project.path("domain").asText(), domain);
    ObjectNode scene = find(state, "scenes", project.path("sceneRef").path("id").asText());
    ObjectNode topology =
        project.path("topologyRef").isObject()
            ? find(state, "topologies", project.path("topologyRef").path("id").asText())
            : obj("connections", arr(), "rules", arr());
    ObjectNode preview = SimulationRuleEngine.createPreview(project, scene, topology, actor);
    state.withArray("simulationPreviews").add(preview);
    return preview.deepCopy();
  }

  private static ObjectNode act(
      ObjectNode state, ObjectNode payload, String actor, String domain) {
    ObjectNode preview = preview(state, payload, actor, domain);
    ObjectNode parameters =
        payload.path("parameters").isObject() ? (ObjectNode) payload.path("parameters") : obj();
    ObjectNode event =
        SimulationRuleEngine.execute(
            preview,
            payload.path("objectId").asText(),
            payload.path("actionId").asText(),
            parameters);
    return result(preview, event);
  }

  private static ObjectNode reset(
      ObjectNode state, ObjectNode payload, String actor, String domain) {
    ObjectNode preview = preview(state, payload, actor, domain);
    return result(preview, SimulationRuleEngine.reset(preview));
  }

  private static ObjectNode close(
      ObjectNode state, ObjectNode payload, String actor, String domain) {
    ObjectNode preview = preview(state, payload, actor, domain);
    preview.put("status", "CLOSED").put("updatedAt", Instant.now().toString());
    return preview.deepCopy();
  }

  private static ObjectNode preview(
      ObjectNode state, ObjectNode payload, String actor, String domain) {
    ObjectNode preview = find(state, "simulationPreviews", payload.path("id").asText());
    requireScope(preview.path("domain").asText(), domain);
    if (!preview.path("createdBy").asText().equals(actor) && !DemoService.role(actor).equals("ADMIN"))
      throw new BusinessException(403, "PREVIEW_FORBIDDEN", "只能操作本人创建的调试实例");
    return preview;
  }

  private static ObjectNode result(ObjectNode preview, ObjectNode event) {
    ObjectNode result = preview.deepCopy();
    result.set("lastEvent", event.deepCopy());
    return result;
  }

  private static void requireScope(String actual, String requested) {
    TrainingDomain.require(actual);
    if (requested == null || requested.isBlank())
      throw new BusinessException(400, "DOMAIN_REQUIRED", "仿真调试命令必须指定当前系统");
    if (!actual.equals(requested))
      throw new BusinessException(403, "DOMAIN_MISMATCH", "当前系统不能操作其他系统的仿真数据");
  }
}

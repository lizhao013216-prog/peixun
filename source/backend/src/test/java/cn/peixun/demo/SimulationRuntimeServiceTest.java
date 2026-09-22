package cn.peixun.demo;

import static cn.peixun.demo.Json.*;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;

class SimulationRuntimeServiceTest {
  @Test
  void topologyPersistsAndRejectsInvalidPortsTypesAndObjects() {
    Fixture fixture = fixture();
    ObjectNode saved = saveTopology(fixture, 0.4);
    assertEquals(2, saved.withArray("connections").size());
    assertEquals(2, saved.withArray("rules").size());

    ObjectNode inputToInput = topologyPayload(fixture, 0.4);
    ((ObjectNode) inputToInput.withArray("connections").get(0).path("source"))
        .put("objectId", "PUMP-01")
        .put("port", "readyInput");
    ((ObjectNode) inputToInput.withArray("connections").get(0).path("target"))
        .put("objectId", "PUMP-02")
        .put("port", "sensorInput");
    BusinessException direction =
        assertThrows(
            BusinessException.class,
            () ->
                SimulationRuntimeService.apply(
                    fixture.state,
                    "simulationTopology.save",
                    inputToInput,
                    "AUTHOR",
                    "OPERATION"));
    assertTrue(direction.getMessage().contains("输出端口"));

    ObjectNode mismatch = topologyPayload(fixture, 0.4);
    ((ObjectNode) mismatch.withArray("connections").get(0).path("target"))
        .put("port", "sensorInput");
    BusinessException type =
        assertThrows(
            BusinessException.class,
            () ->
                SimulationRuntimeService.apply(
                    fixture.state,
                    "simulationTopology.save",
                    mismatch,
                    "AUTHOR",
                    "OPERATION"));
    assertTrue(type.getMessage().contains("数据类型"));

    ObjectNode missing = topologyPayload(fixture, 0.4);
    ((ObjectNode) missing.withArray("connections").get(0).path("source"))
        .put("objectId", "DOES-NOT-EXIST");
    BusinessException object =
        assertThrows(
            BusinessException.class,
            () ->
                SimulationRuntimeService.apply(
                    fixture.state,
                    "simulationTopology.save",
                    missing,
                    "AUTHOR",
                    "OPERATION"));
    assertTrue(object.getMessage().contains("来源对象不存在"));
  }

  @Test
  void startIsRejectedUntilValveReadyThenStateChangesAreExplained() {
    Fixture fixture = fixture();
    saveTopology(fixture, 0.4);
    ObjectNode preview = start(fixture);

    ObjectNode rejected = action(fixture, preview, "PUMP-01", "START", obj());
    assertEquals("REJECTED", rejected.path("lastEvent").path("result").asText());
    assertEquals("阀门未就绪，当前不能启动", rejected.path("lastEvent").path("reason").asText());
    assertEquals("READY", rejected.path("states").path("PUMP-01").path("status").asText());

    action(fixture, preview, "VALVE-01", "SET_READY", obj("value", true));
    ObjectNode started = action(fixture, preview, "PUMP-01", "START", obj());
    assertEquals("SUCCEEDED", started.path("lastEvent").path("result").asText());
    assertEquals("RUNNING", started.path("states").path("PUMP-01").path("status").asText());
    assertEquals("RULE-START", started.path("lastEvent").path("matchedRules").get(0).path("id").asText());

    ObjectNode stopped = action(fixture, preview, "PUMP-01", "STOP", obj());
    assertEquals("STOPPED", stopped.path("states").path("PUMP-01").path("status").asText());
    ObjectNode reset =
        SimulationRuntimeService.apply(
            fixture.state,
            "simulationPreview.reset",
            obj("id", preview.path("id").asText()),
            "AUTHOR",
            "OPERATION");
    assertEquals("READY", reset.path("states").path("PUMP-01").path("status").asText());
  }

  @Test
  void thresholdChangesResultButExistingPreviewKeepsItsSnapshot() {
    Fixture fixture = fixture();
    ObjectNode topology = saveTopology(fixture, 0.4);
    ObjectNode first = start(fixture);
    ObjectNode noAlarm = action(fixture, first, "SENSOR-01", "SET_VALUE", obj("value", 0.5));
    assertEquals("READY", noAlarm.path("states").path("PUMP-01").path("status").asText());

    ObjectNode updated = topologyPayload(fixture, 0.6);
    updated.put("expectedEditRevision", topology.path("editRevision").asInt());
    SimulationRuntimeService.apply(
        fixture.state, "simulationTopology.save", updated, "AUTHOR", "OPERATION");
    ObjectNode firstAgain = action(fixture, first, "SENSOR-01", "SET_VALUE", obj("value", 0.5));
    assertEquals("READY", firstAgain.path("states").path("PUMP-01").path("status").asText());

    ObjectNode second = start(fixture);
    ObjectNode alarm = action(fixture, second, "SENSOR-01", "SET_VALUE", obj("value", 0.5));
    assertEquals("ALARM", alarm.path("states").path("PUMP-01").path("status").asText());
    assertEquals("RULE-LOW-VALUE", alarm.path("lastEvent").path("matchedRules").get(0).path("id").asText());
  }

  @Test
  void previewsAreIsolatedAndDoNotModifySceneInitialState() {
    Fixture fixture = fixture();
    saveTopology(fixture, 0.4);
    ObjectNode first = start(fixture);
    ObjectNode second = start(fixture);
    action(fixture, first, "VALVE-01", "SET_READY", obj("value", true));

    ObjectNode storedFirst = find(fixture.state, "simulationPreviews", first.path("id").asText());
    ObjectNode storedSecond = find(fixture.state, "simulationPreviews", second.path("id").asText());
    assertTrue(storedFirst.path("states").path("VALVE-01").path("ready").asBoolean());
    assertFalse(storedSecond.path("states").path("VALVE-01").has("ready"));
    assertFalse(
        find(fixture.state, "scenes", fixture.project.path("sceneRef").path("id").asText())
            .path("objects")
            .get(1)
            .path("initialState")
            .has("ready"));
  }

  @Test
  void noneModeRunsSingleObjectAndCyclesOrScriptsAreRejected() {
    ObjectNode state = Seed.create("none", "NONE模式", 1);
    ObjectNode project = createProject(state, "NONE", "单对象工程");
    saveScene(state, project, arr(object("TOOL-01", "ASSET-TOOL", 20, 20)));
    ObjectNode preview =
        SimulationRuntimeService.apply(
            state,
            "simulationPreview.start",
            obj("projectId", project.path("id").asText()),
            "AUTHOR",
            "OPERATION");
    ObjectNode confirmed =
        SimulationRuntimeService.apply(
            state,
            "simulationPreview.action",
            obj(
                "id", preview.path("id").asText(),
                "objectId", "TOOL-01",
                "actionId", "CONFIRM",
                "parameters", obj()),
            "AUTHOR",
            "OPERATION");
    assertEquals("CONFIRMED", confirmed.path("states").path("TOOL-01").path("status").asText());

    ObjectNode a = customObject("A"), b = customObject("B");
    ObjectNode scene = obj("objects", arr(a, b));
    ArrayNode cycle =
        arr(
            connection("L1", "A", "out", "B", "in"),
            connection("L2", "B", "out", "A", "in"));
    BusinessException cyclic =
        assertThrows(
            BusinessException.class,
            () -> SimulationRuleEngine.normalizeTopology(scene, cycle, arr()));
    assertTrue(cyclic.getMessage().contains("循环"));
    BusinessException script =
        assertThrows(
            BusinessException.class,
            () ->
                SimulationRuleEngine.normalizeTopology(
                    scene,
                    arr(obj("id", "L1", "script", "while(true){}")),
                    arr()));
    assertTrue(script.getMessage().contains("脚本"));
  }

  private static Fixture fixture() {
    ObjectNode state = Seed.create("runtime", "规则调试", 1);
    ObjectNode project = createProject(state, "SIGNAL_GRAPH", "泵组联动工程");
    saveScene(
        state,
        project,
        arr(
            object("PUMP-01", "ASSET-PUMP", 20, 50),
            object("VALVE-01", "ASSET-VALVE", 42, 30),
            object("SENSOR-01", "ASSET-SENSOR", 70, 30),
            object("PUMP-02", "ASSET-PUMP", 82, 55)));
    return new Fixture(state, project);
  }

  private static ObjectNode createProject(
      ObjectNode state, String linkageMode, String name) {
    return SimulationProjectService.apply(
        state,
        "simulationProject.create",
        obj("domain", "OPERATION", "name", name, "linkageMode", linkageMode),
        "AUTHOR",
        "OPERATION");
  }

  private static void saveScene(ObjectNode state, ObjectNode project, ArrayNode objects) {
    SimulationProjectService.apply(
        state,
        "simulationProject.save",
        obj(
            "id", project.path("id").asText(),
            "expectedEditRevision", project.path("editRevision").asInt(),
            "name", project.path("name").asText(),
            "scene", obj("objects", objects, "environment", obj())),
        "AUTHOR",
        "OPERATION");
  }

  private static ObjectNode object(
      String id, String assetId, int x, int y) {
    return obj(
        "id", id,
        "name", id,
        "assetRef", obj("id", assetId, "version", 1),
        "x", x,
        "y", y,
        "initialState", obj("status", "READY"));
  }

  private static ObjectNode saveTopology(Fixture fixture, double threshold) {
    return SimulationRuntimeService.apply(
        fixture.state,
        "simulationTopology.save",
        topologyPayload(fixture, threshold),
        "AUTHOR",
        "OPERATION");
  }

  private static ObjectNode topologyPayload(Fixture fixture, double threshold) {
    ObjectNode topology =
        find(
            fixture.state,
            "topologies",
            fixture.project.path("topologyRef").path("id").asText());
    return obj(
        "projectId", fixture.project.path("id").asText(),
        "expectedEditRevision", topology.path("editRevision").asInt(),
        "connections",
            arr(
                connection("LINK-READY", "VALVE-01", "ready", "PUMP-01", "readyInput"),
                connection("LINK-SENSOR", "SENSOR-01", "value", "PUMP-01", "sensorInput")),
        "rules",
            arr(
                obj(
                    "id", "RULE-START",
                    "kind", "PRECONDITION",
                    "trigger", obj("objectId", "PUMP-01", "actionId", "START"),
                    "conditions", arr(obj("objectId", "PUMP-01", "field", "readyInput", "operator", "EQ", "value", true)),
                    "effects", arr(obj("objectId", "PUMP-01", "field", "status", "value", "RUNNING")),
                    "rejectMessage", "阀门未就绪，当前不能启动"),
                obj(
                    "id", "RULE-LOW-VALUE",
                    "kind", "CONDITIONAL",
                    "trigger", obj("objectId", "SENSOR-01", "actionId", "SET_VALUE"),
                    "conditions", arr(obj("objectId", "PUMP-01", "field", "sensorInput", "operator", "LT", "value", threshold)),
                    "effects", arr(obj("objectId", "PUMP-01", "field", "status", "value", "ALARM")),
                    "rejectMessage", "")));
  }

  private static ObjectNode connection(
      String id, String sourceObject, String sourcePort, String targetObject, String targetPort) {
    return obj(
        "id", id,
        "source", obj("objectId", sourceObject, "port", sourcePort),
        "target", obj("objectId", targetObject, "port", targetPort),
        "valueType", sourcePort.equals("value") ? "NUMBER" : "BOOLEAN");
  }

  private static ObjectNode start(Fixture fixture) {
    return SimulationRuntimeService.apply(
        fixture.state,
        "simulationPreview.start",
        obj("projectId", fixture.project.path("id").asText()),
        "AUTHOR",
        "OPERATION");
  }

  private static ObjectNode action(
      Fixture fixture, ObjectNode preview, String objectId, String actionId, ObjectNode parameters) {
    return SimulationRuntimeService.apply(
        fixture.state,
        "simulationPreview.action",
        obj(
            "id", preview.path("id").asText(),
            "objectId", objectId,
            "actionId", actionId,
            "parameters", parameters),
        "AUTHOR",
        "OPERATION");
  }

  private static ObjectNode customObject(String id) {
    return obj(
        "id", id,
        "actions", arr(obj("id", "CONFIRM", "label", "确认", "parameters", arr())),
        "ports",
            arr(
                obj("id", "in", "direction", "INPUT", "valueType", "BOOLEAN", "field", "in"),
                obj("id", "out", "direction", "OUTPUT", "valueType", "BOOLEAN", "field", "out")),
        "stateFields",
            arr(
                obj("id", "status", "valueType", "STRING"),
                obj("id", "in", "valueType", "BOOLEAN"),
                obj("id", "out", "valueType", "BOOLEAN")),
        "initialState", obj("status", "READY"));
  }

  private record Fixture(ObjectNode state, ObjectNode project) {}
}

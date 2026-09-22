package cn.peixun.demo;

import static cn.peixun.demo.Json.*;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;

class SimulationTemplateServiceTest {
  @Test
  void invalidDependenciesAndIncompleteSignalGraphBlockPublish() {
    Fixture fixture = fixture("SIGNAL_GRAPH", "非法依赖工程");
    ObjectNode project = fixture.project;
    saveScene(fixture.state, project, "ASSET-PUMP");
    BusinessException missingTopology =
        assertThrows(
            BusinessException.class,
            () -> publish(fixture.state, project, arr(), "联动模板说明"));
    assertTrue(missingTopology.getMessage().contains("设备连接"));

    ObjectNode none = create(fixture.state, "NONE", "失效素材工程", "OPERATION");
    saveScene(fixture.state, none, "ASSET-TOOL");
    find(fixture.state, "assets", "ASSET-TOOL").put("status", "DISABLED");
    BusinessException disabled =
        assertThrows(
            BusinessException.class,
            () -> publish(fixture.state, none, arr(), "单对象模板说明"));
    assertTrue(disabled.getMessage().contains("未批准或已停用"));
  }

  @Test
  void publishedV1ContainsSourceDependenciesAndFrozenSnapshot() {
    Fixture fixture = fixture("SIGNAL_GRAPH", "泵组联动模板");
    savePumpSceneAndTopology(fixture);
    ObjectNode template = publish(fixture.state, fixture.project, arr(), "用于泵组联动调试");

    assertEquals("PUBLISHED", template.path("status").asText());
    assertEquals(1, template.path("version").asInt());
    assertEquals("SIMULATION_PROJECT", template.path("sourceRef").path("type").asText());
    assertEquals(3, template.withArray("dependencies").size());
    assertEquals(3, template.path("sceneSnapshot").path("objects").size());
    assertEquals(2, template.path("topologySnapshot").path("rules").size());

    ObjectNode preview =
        SimulationTemplateService.apply(
            fixture.state,
            "simulationTemplate.preview",
            obj("id", template.path("id").asText()),
            "AUTHOR",
            "OPERATION");
    assertEquals(template.path("id").asText(), preview.path("templateRef").path("id").asText());
    assertEquals(3, preview.withArray("objectsSnapshot").size());
  }

  @Test
  void copiedProjectCanChangeWithoutMutatingTemplateOrOtherCopy() {
    Fixture fixture = fixture("NONE", "部件识别模板");
    saveScene(fixture.state, fixture.project, "ASSET-TOOL");
    ObjectNode template = publish(fixture.state, fixture.project, arr(), "用于单对象识别");
    ObjectNode first = instantiate(fixture.state, template, "OPERATION", "COPY");
    ObjectNode second = instantiate(fixture.state, template, "OPERATION", "COPY");

    ObjectNode firstScene = find(fixture.state, "scenes", first.path("sceneRef").path("id").asText());
    ((ObjectNode) firstScene.withArray("objects").get(0)).put("name", "已修改副本对象");
    ObjectNode secondScene = find(fixture.state, "scenes", second.path("sceneRef").path("id").asText());
    assertEquals("TOOL-01", secondScene.withArray("objects").get(0).path("name").asText());
    assertEquals("TOOL-01", template.path("sceneSnapshot").path("objects").get(0).path("name").asText());
    assertNotEquals(first.path("id").asText(), second.path("id").asText());
    assertNotEquals(first.path("sceneRef").path("id"), second.path("sceneRef").path("id"));
  }

  @Test
  void v2KeepsV1UnchangedAndRevisionRetainsFamily() {
    Fixture fixture = fixture("NONE", "版本化模板");
    saveScene(fixture.state, fixture.project, "ASSET-TOOL");
    ObjectNode v1 = publish(fixture.state, fixture.project, arr(), "第一版说明");
    ObjectNode revision = instantiate(fixture.state, v1, "OPERATION", "REVISION");
    ObjectNode revisionScene = find(fixture.state, "scenes", revision.path("sceneRef").path("id").asText());
    ((ObjectNode) revisionScene.withArray("objects").get(0)).put("name", "第二版对象");
    ObjectNode v2 = publish(fixture.state, revision, arr(), "第二版说明");

    assertEquals(2, v2.path("version").asInt());
    assertEquals(v1.path("familyId").asText(), v2.path("familyId").asText());
    assertEquals(v1.path("id").asText(), v2.path("previousVersionRef").path("id").asText());
    assertEquals("TOOL-01", v1.path("sceneSnapshot").path("objects").get(0).path("name").asText());
    assertEquals("第二版对象", v2.path("sceneSnapshot").path("objects").get(0).path("name").asText());
  }

  @Test
  void crossDomainReuseRequiresTemplateAndAllDependenciesToBeShared() {
    Fixture fixture = fixture("NONE", "共享模板");
    saveScene(fixture.state, fixture.project, "ASSET-TOOL");
    ObjectNode shared = publish(fixture.state, fixture.project, arr("MAINTENANCE"), "跨域复用说明");
    ObjectNode copied = instantiate(fixture.state, shared, "MAINTENANCE", "COPY");
    assertEquals("MAINTENANCE", copied.path("domain").asText());

    ObjectNode privateAsset =
        TrainingModule.apply(
            fixture.state,
            "asset.import",
            obj("name", "操作私有模型", "ownerSystem", "OPERATION"),
            "AUTHOR");
    privateAsset.put("status", "APPROVED");
    ObjectNode privateProject = create(fixture.state, "NONE", "私有依赖工程", "OPERATION");
    saveScene(fixture.state, privateProject, privateAsset.path("id").asText());
    BusinessException hiddenDependency =
        assertThrows(
            BusinessException.class,
            () -> publish(fixture.state, privateProject, arr("MAINTENANCE"), "不应发布"));
    assertEquals("ASSET_NOT_SHARED", hiddenDependency.code);
  }

  @Test
  void noneModePublishesWithoutConnectionsAndStatesThatMode() {
    Fixture fixture = fixture("NONE", "无联动模板");
    saveScene(fixture.state, fixture.project, "ASSET-TOOL");
    ObjectNode template = publish(fixture.state, fixture.project, arr(), "单对象认知，无需连接");
    assertEquals("NONE", template.path("linkageMode").asText());
    assertEquals(0, template.path("connectionCount").asInt());
    assertEquals(0, template.path("ruleCount").asInt());
  }

  @Test
  void planTemplatesRemainUntouchedBySimulationTemplateLifecycle() {
    Fixture fixture = fixture("NONE", "预案隔离模板");
    int before = planCount(fixture.state);
    saveScene(fixture.state, fixture.project, "ASSET-TOOL");
    ObjectNode template = publish(fixture.state, fixture.project, arr(), "验证保障预案隔离");
    instantiate(fixture.state, template, "OPERATION", "COPY");
    assertEquals(before, planCount(fixture.state));
    assertEquals("PLAN", find(fixture.state, "templates", "TPL-PLAN-01").path("type").asText());
  }

  private static Fixture fixture(String linkageMode, String name) {
    ObjectNode state = Seed.create("templates", "模板生命周期", 1);
    return new Fixture(state, create(state, linkageMode, name, "OPERATION"));
  }

  private static ObjectNode create(
      ObjectNode state, String linkageMode, String name, String domain) {
    return SimulationProjectService.apply(
        state,
        "simulationProject.create",
        obj("domain", domain, "name", name, "purpose", "模板验收用途", "linkageMode", linkageMode),
        "AUTHOR",
        domain);
  }

  private static void saveScene(ObjectNode state, ObjectNode project, String assetId) {
    SimulationProjectService.apply(
        state,
        "simulationProject.save",
        obj(
            "id", project.path("id").asText(),
            "expectedEditRevision", project.path("editRevision").asInt(),
            "name", project.path("name").asText(),
            "purpose", project.path("purpose").asText(),
            "scene",
                obj(
                    "objects", arr(object(assetId.equals("ASSET-TOOL") ? "TOOL-01" : "OBJ-01", assetId, 20, 30)),
                    "environment", environment())),
        "AUTHOR",
        project.path("domain").asText());
  }

  private static void savePumpSceneAndTopology(Fixture fixture) {
    ObjectNode project = fixture.project;
    SimulationProjectService.apply(
        fixture.state,
        "simulationProject.save",
        obj(
            "id", project.path("id").asText(),
            "expectedEditRevision", project.path("editRevision").asInt(),
            "name", project.path("name").asText(),
            "purpose", project.path("purpose").asText(),
            "scene",
                obj(
                    "objects",
                        arr(
                            object("PUMP-01", "ASSET-PUMP", 20, 50),
                            object("VALVE-01", "ASSET-VALVE", 42, 30),
                            object("SENSOR-01", "ASSET-SENSOR", 70, 30)),
                    "environment", environment())),
        "AUTHOR",
        "OPERATION");
    ObjectNode topology = find(fixture.state, "topologies", project.path("topologyRef").path("id").asText());
    SimulationRuntimeService.apply(
        fixture.state,
        "simulationTopology.save",
        obj(
            "projectId", project.path("id").asText(),
            "expectedEditRevision", topology.path("editRevision").asInt(),
            "connections",
                arr(
                    connection("LINK-READY", "VALVE-01", "ready", "PUMP-01", "readyInput", "BOOLEAN"),
                    connection("LINK-SENSOR", "SENSOR-01", "value", "PUMP-01", "sensorInput", "NUMBER")),
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
                        "conditions", arr(obj("objectId", "PUMP-01", "field", "sensorInput", "operator", "LT", "value", 0.4)),
                        "effects", arr(obj("objectId", "PUMP-01", "field", "status", "value", "ALARM")),
                        "rejectMessage", ""))),
        "AUTHOR",
        "OPERATION");
  }

  private static ObjectNode publish(
      ObjectNode state, ObjectNode project, ArrayNode sharedWith, String instructions) {
    return SimulationTemplateService.apply(
        state,
        "simulationTemplate.publish",
        obj(
            "projectId", project.path("id").asText(),
            "usageInstructions", instructions,
            "sharedWith", sharedWith),
        "AUTHOR",
        project.path("domain").asText());
  }

  private static ObjectNode instantiate(
      ObjectNode state, ObjectNode template, String domain, String mode) {
    return SimulationTemplateService.apply(
        state,
        "simulationTemplate.instantiate",
        obj("id", template.path("id").asText(), "domain", domain, "mode", mode),
        "AUTHOR",
        domain);
  }

  private static ObjectNode object(String id, String assetId, int x, int y) {
    return obj(
        "id", id,
        "name", id,
        "assetRef", obj("id", assetId, "version", 1),
        "x", x,
        "y", y,
        "initialState", obj("status", "READY"));
  }

  private static ObjectNode environment() {
    return obj("weather", "晴", "light", "日间", "camera", "总览", "material", "标准");
  }

  private static ObjectNode connection(
      String id, String source, String sourcePort, String target, String targetPort, String type) {
    return obj(
        "id", id,
        "source", obj("objectId", source, "port", sourcePort),
        "target", obj("objectId", target, "port", targetPort),
        "valueType", type);
  }

  private static int planCount(ObjectNode state) {
    int count = 0;
    for (JsonNode item : state.withArray("templates"))
      if (item.path("type").asText().equals("PLAN")) count++;
    return count;
  }

  private record Fixture(ObjectNode state, ObjectNode project) {}
}

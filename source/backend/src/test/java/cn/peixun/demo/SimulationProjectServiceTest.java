package cn.peixun.demo;

import static cn.peixun.demo.Json.*;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;

class SimulationProjectServiceTest {
  @Test
  void projectsOwnIndependentScenesAndFifthObjectSurvivesSave() {
    ObjectNode state = Seed.create("projects", "独立工程", 1);
    ObjectNode a = create(state, "OPERATION", "工程A");
    ObjectNode b = create(state, "OPERATION", "工程B");
    ObjectNode c = create(state, "MAINTENANCE", "工程C");
    String assetId = approvedAsset(state).path("id").asText();
    int assetVersion = approvedAsset(state).path("version").asInt();
    ArrayNode objects = arr();
    for (int i = 1; i <= 5; i++)
      objects.add(
          obj(
              "id", "OBJ-0" + i,
              "name", i == 5 ? "第五对象工具组" : "对象" + i,
              "assetRef", obj("id", assetId, "version", assetVersion),
              "x", i * 10,
              "y", i * 12,
              "initialState", obj("status", "READY")));

    ObjectNode saved =
        SimulationProjectService.apply(
            state,
            "simulationProject.save",
            obj(
                "id", a.path("id").asText(),
                "expectedEditRevision", a.path("editRevision").asInt(),
                "name", "工程A",
                "scene", obj("objects", objects, "environment", obj())),
            "AUTHOR",
            "OPERATION");

    assertEquals(5, saved.path("scene").path("objects").size());
    assertEquals("第五对象工具组", saved.path("scene").path("objects").get(4).path("name").asText());
    assertEquals(0, scene(state, b).withArray("objects").size());
    assertEquals(0, scene(state, c).withArray("objects").size());
    assertNotEquals(a.path("sceneRef").path("id"), b.path("sceneRef").path("id"));
  }

  @Test
  void privateAssetRequiresExplicitShareAndSourceIsNotChanged() {
    ObjectNode state = Seed.create("sharing", "素材共享", 1);
    ObjectNode asset =
        TrainingModule.apply(
            state,
            "asset.import",
            obj("name", "操作私有素材", "ownerSystem", "OPERATION"),
            "AUTHOR");
    asset.put("status", "APPROVED");
    ObjectNode maintenance = create(state, "MAINTENANCE", "维修工程");
    ObjectNode payload = savePayload(maintenance, asset);

    BusinessException hidden =
        assertThrows(
            BusinessException.class,
            () ->
                SimulationProjectService.apply(
                    state,
                    "simulationProject.save",
                    payload,
                    "AUTHOR",
                    "MAINTENANCE"));
    assertEquals("ASSET_NOT_SHARED", hidden.code);

    TrainingModule.apply(
        state,
        "asset.share",
        obj("id", asset.path("id").asText(), "sharedWith", arr("MAINTENANCE")),
        "AUTHOR");
    ObjectNode saved =
        SimulationProjectService.apply(
            state,
            "simulationProject.save",
            payload,
            "AUTHOR",
            "MAINTENANCE");
    assertEquals(1, saved.path("scene").path("objects").size());
    assertEquals("OPERATION", asset.path("ownerSystem").asText());
    assertEquals("SHARED", asset.path("visibility").asText());
  }

  @Test
  void staleEditRevisionIsRejectedAndLearnerCannotSeeDraftProjects() {
    ObjectNode state = Seed.create("conflict", "冲突", 1);
    ObjectNode project = create(state, "OPERATION", "冲突工程");
    ObjectNode asset = approvedAsset(state);
    ObjectNode payload = savePayload(project, asset);
    SimulationProjectService.apply(
        state, "simulationProject.save", payload, "AUTHOR", "OPERATION");
    BusinessException conflict =
        assertThrows(
            BusinessException.class,
            () ->
                SimulationProjectService.apply(
                    state, "simulationProject.save", payload, "AUTHOR", "OPERATION"));
    assertEquals("EDIT_CONFLICT", conflict.code);
    ObjectNode learner = WorkspaceProjection.forActor(state, "LEARNER_A", "OPERATION");
    assertEquals(0, learner.withArray("simulationProjects").size());
    for (JsonNode item : learner.withArray("assets"))
      assertEquals("APPROVED", item.path("status").asText());
  }

  private static ObjectNode create(ObjectNode state, String domain, String name) {
    return SimulationProjectService.apply(
        state,
        "simulationProject.create",
        obj("domain", domain, "name", name, "linkageMode", "NONE"),
        "AUTHOR",
        domain);
  }

  private static ObjectNode approvedAsset(ObjectNode state) {
    for (JsonNode item : state.withArray("assets"))
      if (item.path("status").asText().equals("APPROVED")) return (ObjectNode) item;
    throw new AssertionError("seed must contain an approved asset");
  }

  private static ObjectNode savePayload(ObjectNode project, ObjectNode asset) {
    return obj(
        "id", project.path("id").asText(),
        "expectedEditRevision", project.path("editRevision").asInt(),
        "name", project.path("name").asText(),
        "scene",
        obj(
            "objects",
            arr(
                obj(
                    "id", "OBJ-01",
                    "name", "共享对象",
                    "assetRef", obj("id", asset.path("id").asText(), "version", asset.path("version").asInt()),
                    "x", 20,
                    "y", 30)),
            "environment", obj()));
  }

  private static ObjectNode scene(ObjectNode state, ObjectNode project) {
    return find(state, "scenes", project.path("sceneRef").path("id").asText());
  }
}

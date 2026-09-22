package cn.peixun.demo;

import static cn.peixun.demo.Json.*;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;

class CoursewareServiceTest {
  @Test
  void createsCurriculumUnitAndEditableFiveStepCourseware() {
    Fixture fixture = fixture("ASSET-TOOL", "TOOL-01", "部件识别模板");
    ObjectNode courseware = createCourseware(fixture.state, fixture.template, "五步检查课件");

    assertEquals(1, fixture.state.withArray("curricula").size());
    ObjectNode curriculum =
        find(fixture.state, "curricula", courseware.path("curriculumId").asText());
    assertNotNull(findUnit(curriculum, courseware.path("unitId").asText()));
    assertEquals(5, courseware.withArray("steps").size());
    assertEquals(3, courseware.path("interactionSchemaVersion").asInt());

    ArrayNode changed = courseware.withArray("steps").deepCopy();
    ObjectNode copied = ((ObjectNode) changed.get(0)).deepCopy();
    copied.put("id", "STEP-COPIED").put("name", "复制步骤");
    changed.insert(1, copied);
    changed.remove(5);
    JsonNode moved = changed.remove(1);
    changed.insert(3, moved);
    ObjectNode saved = save(fixture.state, courseware, changed, obj("total", 100, "errorPenalty", 5, "helpPenalty", 2));
    assertEquals(5, saved.withArray("steps").size());
    assertEquals("STEP-COPIED", saved.withArray("steps").get(3).path("id").asText());
  }

  @Test
  void explicitTemplateBindingSurvivesOrdinarySaveAndReportsRebindBreakage() {
    Fixture original = fixture("ASSET-TOOL", "TOOL-01", "工具模板");
    ObjectNode courseware = createCourseware(original.state, original.template, "工具识别课件");
    ArrayNode valid = validSteps(courseware, "TOOL-01", "CONFIRM");
    courseware = save(original.state, courseware, valid, obj("total", 100, "errorPenalty", 5, "helpPenalty", 2));
    JsonNode environment = courseware.path("environment").deepCopy();

    ObjectNode saved =
        CoursewareService.apply(
            original.state,
            "courseware.save",
            obj(
                "id", courseware.path("id").asText(),
                "expectedEditRevision", courseware.path("editRevision").asInt(),
                "description", "只修改教学说明",
                "scoreRule", obj("total", 100, "errorPenalty", 4, "helpPenalty", 1)),
            "AUTHOR",
            "OPERATION");
    assertEquals(environment, saved.path("environment"));

    ObjectNode pumpTemplate = publishTemplate(original.state, "ASSET-PUMP", "PUMP-01", "泵组模板");
    ObjectNode rebound =
        CoursewareService.apply(
            original.state,
            "courseware.rebind",
            obj(
                "id", saved.path("id").asText(),
                "expectedEditRevision", saved.path("editRevision").asInt(),
                "templateId", pumpTemplate.path("id").asText()),
            "AUTHOR",
            "OPERATION");
    assertTrue(rebound.withArray("environmentIssues").get(0).asText().contains("TOOL-01"));
    BusinessException invalid =
        assertThrows(
            BusinessException.class,
            () ->
                CoursewareService.apply(
                    original.state,
                    "courseware.submit",
                    obj("id", rebound.path("id").asText()),
                    "AUTHOR",
                    "OPERATION"));
    assertEquals("COURSEWARE_INVALID", invalid.code);
  }

  @Test
  void incompleteDraftSavesButPreviewAndSubmitUseStrictValidation() {
    Fixture fixture = fixture("ASSET-TOOL", "TOOL-01", "半成品模板");
    ObjectNode courseware = createCourseware(fixture.state, fixture.template, "半成品课件");
    ObjectNode saved =
        CoursewareService.apply(
            fixture.state,
            "courseware.save",
            obj(
                "id", courseware.path("id").asText(),
                "expectedEditRevision", courseware.path("editRevision").asInt(),
                "description", "允许先保存尚未填写动作的五个步骤"),
            "AUTHOR",
            "OPERATION");
    assertEquals("DRAFT", saved.path("status").asText());
    ObjectNode report =
        CoursewareService.apply(
            fixture.state,
            "courseware.validate",
            obj("id", saved.path("id").asText()),
            "AUTHOR",
            "OPERATION");
    assertFalse(report.path("valid").asBoolean());
    assertThrows(
        BusinessException.class,
        () ->
            CoursewareService.apply(
                fixture.state,
                "courseware.preview",
                obj("id", saved.path("id").asText(), "stepId", saved.withArray("steps").get(0).path("id").asText()),
                "AUTHOR",
                "OPERATION"));
    assertThrows(
        BusinessException.class,
        () ->
            CoursewareService.apply(
                fixture.state,
                "courseware.submit",
                obj("id", saved.path("id").asText()),
                "AUTHOR",
                "OPERATION"));
  }

  @Test
  void previewExecutesTheBoundTemplateThroughTheSharedRuleEngineWithoutTrainingRecords() {
    Fixture fixture = fixture("ASSET-TOOL", "TOOL-01", "预览模板");
    ObjectNode courseware = createCourseware(fixture.state, fixture.template, "规则预览课件");
    courseware =
        save(
            fixture.state,
            courseware,
            validSteps(courseware, "TOOL-01", "CONFIRM"),
            obj("total", 100, "errorPenalty", 5, "helpPenalty", 2));
    ObjectNode result =
        CoursewareService.apply(
            fixture.state,
            "courseware.preview",
            obj("id", courseware.path("id").asText(), "stepId", courseware.withArray("steps").get(0).path("id").asText()),
            "AUTHOR",
            "OPERATION");
    assertTrue(result.path("passed").asBoolean());
    assertEquals("SUCCEEDED", result.path("event").path("result").asText());
    assertEquals("CONFIRMED", result.path("states").path("TOOL-01").path("status").asText());
    assertEquals(1, fixture.state.withArray("simulationPreviews").size());
    assertTrue(fixture.state.withArray("assignments").isEmpty());
    assertTrue(fixture.state.withArray("attempts").isEmpty());
  }

  @Test
  void typedParametersAndStateConditionsAreCheckedAgainstObjectCapabilities() {
    Fixture fixture = fixture("ASSET-SENSOR", "SENSOR-01", "传感器模板");
    ObjectNode courseware = createCourseware(fixture.state, fixture.template, "传感器课件");
    ArrayNode steps = validSteps(courseware, "SENSOR-01", "SET_VALUE");
    for (JsonNode item : steps) {
      ObjectNode step = (ObjectNode) item;
      step.set("parameters", obj("value", 0.5));
      step.set(
          "precondition",
          obj("objectId", "SENSOR-01", "field", "status", "operator", "EQ", "value", "READY"));
    }
    courseware =
        save(
            fixture.state,
            courseware,
            steps,
            obj("total", 100, "errorPenalty", 5, "helpPenalty", 2));
    ObjectNode valid =
        CoursewareService.apply(
            fixture.state,
            "courseware.validate",
            obj("id", courseware.path("id").asText()),
            "AUTHOR",
            "OPERATION");
    assertTrue(valid.path("valid").asBoolean());

    ArrayNode wrong = courseware.withArray("steps").deepCopy();
    ((ObjectNode) wrong.get(0)).set("parameters", obj("value", "0.5"));
    ObjectNode saved =
        save(
            fixture.state,
            courseware,
            wrong,
            obj("total", 100, "errorPenalty", 5, "helpPenalty", 2));
    ObjectNode invalid =
        CoursewareService.apply(
            fixture.state,
            "courseware.validate",
            obj("id", saved.path("id").asText()),
            "AUTHOR",
            "OPERATION");
    assertFalse(invalid.path("valid").asBoolean());
    assertTrue(invalid.path("issues").toString().contains("NUMBER"));
  }

  @Test
  void independentReviewPublishesFrozenV1AndRevisionDoesNotChangeIt() {
    Fixture fixture = fixture("ASSET-TOOL", "TOOL-01", "版本模板");
    ObjectNode courseware = createCourseware(fixture.state, fixture.template, "版本课件");
    courseware =
        save(
            fixture.state,
            courseware,
            validSteps(courseware, "TOOL-01", "CONFIRM"),
            obj("total", 100, "errorPenalty", 5, "helpPenalty", 2));
    ObjectNode submitted = apply(fixture.state, "courseware.submit", courseware, "AUTHOR");
    assertThrows(
        BusinessException.class,
        () -> apply(fixture.state, "courseware.approve", submitted, "AUTHOR"));
    ObjectNode approved = apply(fixture.state, "courseware.approve", submitted, "INSTRUCTOR");
    ObjectNode published = apply(fixture.state, "courseware.publish", approved, "AUTHOR");
    JsonNode v1Snapshot = published.path("publishedSnapshot").deepCopy();

    ObjectNode revision = apply(fixture.state, "courseware.revise", published, "AUTHOR");
    ArrayNode v2Steps = revision.withArray("steps").deepCopy();
    ((ObjectNode) v2Steps.get(0)).put("name", "第二版步骤");
    save(fixture.state, revision, v2Steps, obj("total", 100, "errorPenalty", 5, "helpPenalty", 2));

    assertEquals(2, revision.path("version").asInt());
    assertEquals(published.path("familyId"), revision.path("familyId"));
    assertEquals(v1Snapshot, published.path("publishedSnapshot"));
    assertNotEquals("第二版步骤", published.path("publishedSnapshot").path("steps").get(0).path("name").asText());
  }

  @Test
  void legacyCourseCanStillRunWhileV3AssignmentIsBlockedUntilP4() {
    ObjectNode state = Seed.create("compat", "新旧契约兼容", 1);
    ObjectNode legacy =
        TrainingModule.apply(
            state,
            "course.create",
            obj("domain", "OPERATION", "name", "旧契约课件"),
            "AUTHOR");
    legacy.put("status", "PUBLISHED");
    ObjectNode assignment =
        TrainingModule.apply(
            state,
            "training.assign",
            obj("courseId", legacy.path("id").asText(), "learnerId", "LEARNER_A"),
            "INSTRUCTOR");
    assertEquals(legacy.path("id"), assignment.path("courseId"));

    ObjectNode template = publishTemplate(state, "ASSET-TOOL", "TOOL-01", "新契约模板");
    ObjectNode modern = createCourseware(state, template, "新契约课件");
    find(state, "courses", modern.path("id").asText()).put("status", "PUBLISHED");
    BusinessException blocked =
        assertThrows(
            BusinessException.class,
            () ->
                TrainingModule.apply(
                    state,
                    "training.assign",
                    obj("courseId", modern.path("id").asText(), "learnerId", "LEARNER_A"),
                    "INSTRUCTOR"));
    assertTrue(blocked.getMessage().contains("P4"));
  }

  @Test
  void allThreeDomainsCanAuthorV3AfterP5() {
    ObjectNode state = Seed.create("domains", "P5领域边界", 1);
    ObjectNode maintenanceTemplate =
        publishTemplate(state, "ASSET-TOOL", "TOOL-01", "维修检查模板", "MAINTENANCE");
    ObjectNode maintenance =
        CoursewareService.apply(
            state,
            "courseware.create",
            obj(
                "domain", "MAINTENANCE",
                "curriculumName", "维修检查课程",
                "curriculumObjective", "完成维修对象检查",
                "unitName", "维修第一课时",
                "name", "维修V3课件",
                "description", "维修五步课件",
                "templateId", maintenanceTemplate.path("id").asText(),
                "stepCount", 5),
            "AUTHOR",
            "MAINTENANCE");
    assertEquals("MAINTENANCE", maintenance.path("domain").asText());
    assertEquals(3, maintenance.path("interactionSchemaVersion").asInt());

    ObjectNode supportTemplate =
        publishTemplate(state, "ASSET-PUMP", "PUMP-01", "保障教学模板", "SUPPORT");
    ObjectNode support =
        CoursewareService.apply(
            state,
            "courseware.create",
            obj(
                "domain", "SUPPORT",
                "curriculumName", "保障教学",
                "curriculumObjective", "完成保障方案编制与事件处置",
                "unitName", "第一课时",
                "name", "保障方案教学课件",
                "templateId", supportTemplate.path("id").asText(),
                "stepCount", 6),
            "AUTHOR",
            "SUPPORT");
    assertEquals("SUPPORT", support.path("domain").asText());
    assertEquals(6, support.withArray("steps").size());
    assertEquals("TRAINING", support.path("supportCaseSnapshot").path("purpose").asText());
  }

  private static Fixture fixture(String assetId, String objectId, String name) {
    ObjectNode state = Seed.create("courseware", "P3课件", 1);
    return new Fixture(state, publishTemplate(state, assetId, objectId, name));
  }

  private static ObjectNode publishTemplate(
      ObjectNode state, String assetId, String objectId, String name) {
    return publishTemplate(state, assetId, objectId, name, "OPERATION");
  }

  private static ObjectNode publishTemplate(
      ObjectNode state, String assetId, String objectId, String name, String domain) {
    ObjectNode project =
        SimulationProjectService.apply(
            state,
            "simulationProject.create",
            obj("domain", domain, "name", name, "purpose", "P3课件环境", "linkageMode", "NONE"),
            "AUTHOR",
            domain);
    SimulationProjectService.apply(
        state,
        "simulationProject.save",
        obj(
            "id", project.path("id").asText(),
            "expectedEditRevision", project.path("editRevision").asInt(),
            "name", name,
            "purpose", "P3课件环境",
            "scene",
                obj(
                    "objects", arr(obj("id", objectId, "name", objectId, "assetRef", obj("id", assetId, "version", 1), "x", 30, "y", 40, "initialState", obj("status", "READY"))),
                    "environment", obj("weather", "晴", "light", "日间", "camera", "总览", "material", "标准"))),
        "AUTHOR",
        domain);
    return SimulationTemplateService.apply(
        state,
        "simulationTemplate.publish",
        obj("projectId", project.path("id").asText(), "usageInstructions", "P3课件环境", "sharedWith", arr()),
        "AUTHOR",
        domain);
  }

  private static ObjectNode createCourseware(
      ObjectNode state, ObjectNode template, String name) {
    return CoursewareService.apply(
        state,
        "courseware.create",
        obj(
            "domain", "OPERATION",
            "curriculumName", name + "课程",
            "curriculumObjective", "完成模板环境中的规范操作",
            "unitName", "第一课时",
            "name", name,
            "description", "五步线性教学",
            "templateId", template.path("id").asText(),
            "stepCount", 5),
        "AUTHOR",
        "OPERATION");
  }

  private static ArrayNode validSteps(ObjectNode courseware, String target, String action) {
    ArrayNode result = courseware.withArray("steps").deepCopy();
    for (int index = 0; index < result.size(); index++) {
      ObjectNode step = (ObjectNode) result.get(index);
      step.put("name", "规范步骤" + (index + 1));
      step.put("description", "选择目标对象并执行有限动作");
      step.put("target", target);
      step.put("actionId", action);
      step.put("actionLabel", "确认当前对象");
      step.put("expectedResult", "动作由仿真规则服务执行并返回结果");
      step.put("points", 20);
      step.put("mode", "GUIDED");
      step.set("parameters", obj());
      step.putNull("precondition");
    }
    return result;
  }

  private static ObjectNode save(
      ObjectNode state, ObjectNode courseware, ArrayNode steps, ObjectNode scoreRule) {
    return CoursewareService.apply(
        state,
        "courseware.save",
        obj(
            "id", courseware.path("id").asText(),
            "expectedEditRevision", courseware.path("editRevision").asInt(),
            "name", courseware.path("name").asText(),
            "description", courseware.path("description").asText(),
            "mode", "GUIDED",
            "steps", steps,
            "scoreRule", scoreRule),
        "AUTHOR",
        "OPERATION");
  }

  private static ObjectNode apply(
      ObjectNode state, String action, ObjectNode courseware, String actor) {
    return CoursewareService.apply(
        state,
        action,
        obj("id", courseware.path("id").asText()),
        actor,
        "OPERATION");
  }

  private static JsonNode findUnit(ObjectNode curriculum, String id) {
    for (JsonNode unit : curriculum.withArray("units"))
      if (unit.path("id").asText().equals(id)) return unit;
    return null;
  }

  private record Fixture(ObjectNode state, ObjectNode template) {}
}

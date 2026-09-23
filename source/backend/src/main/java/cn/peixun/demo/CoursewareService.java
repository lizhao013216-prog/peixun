package cn.peixun.demo;

import static cn.peixun.demo.BusinessException.require;
import static cn.peixun.demo.Json.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.time.Instant;
import java.util.*;

/** P3 seam for curricula and interaction-schema-v3 courseware authoring. */
public final class CoursewareService {
  private static final Set<String> MODES = Set.of("GUIDED", "FREE", "DEMONSTRATION");
  private static final Set<String> OPERATORS = Set.of("EQ", "NE", "LT", "LTE", "GT", "GTE");
  private static final Set<String> SUPPORT_KINDS = Set.of("TASK_CONFIRM", "RESOURCE_CONFIGURE", "PLAN_EDIT", "PLAN_CALCULATE", "EVENT_HANDLE", "REVIEW_SUBMIT");

  private CoursewareService() {}

  public static ObjectNode apply(
      ObjectNode state, String action, ObjectNode payload, String actor, String domain) {
    return switch (action) {
      case "curriculum.create" -> createCurriculum(state, payload, actor, domain);
      case "curriculum.unit.add" -> addUnit(state, payload, actor, domain);
      case "courseware.create" -> createCourseware(state, payload, actor, domain);
      case "courseware.save" -> save(state, payload, actor, domain);
      case "courseware.rebind" -> rebind(state, payload, actor, domain);
      case "courseware.validate" -> validate(state, payload, domain);
      case "courseware.preview" -> preview(state, payload, actor, domain);
      case "coursewarePreview.start" -> previewStart(state, payload, actor, domain);
      case "coursewarePreview.action" -> previewAction(state, payload, actor, domain);
      case "coursewarePreview.continue" -> previewContinue(state, payload, actor, domain);
      case "coursewarePreview.reset" -> previewReset(state, payload, actor, domain);
      case "coursewarePreview.close" -> previewClose(state, payload, actor);
      case "courseware.submit" -> submit(state, payload, actor, domain);
      case "courseware.approve" -> approve(state, payload, actor, domain);
      case "courseware.return" -> returnForRevision(state, payload, actor, domain);
      case "courseware.publish" -> publish(state, payload, actor, domain);
      case "courseware.revise" -> revise(state, payload, actor, domain);
      default -> throw new BusinessException(400, "UNKNOWN_COMMAND", "不支持的课件命令：" + action);
    };
  }

  private static ObjectNode createCurriculum(
      ObjectNode state, ObjectNode payload, String actor, String domain) {
    requireScope(payload.path("domain").asText(), domain);
    TrainingDomain trainingDomain = TrainingDomain.require(domain);
    require(trainingDomain.courseAuthoringEnabled(), "保障教学课程将在P5开放");
    return createCurriculumInternal(state, payload, actor, domain);
  }

  private static ObjectNode createCurriculumInternal(
      ObjectNode state, ObjectNode payload, String actor, String domain) {
    String name = payload.path("name").asText().trim();
    String objective = payload.path("objective").asText().trim();
    String unitName = payload.path("unitName").asText().trim();
    require(!name.isBlank(), "请填写课程名称");
    require(!objective.isBlank(), "请填写课程能力目标");
    require(!unitName.isBlank(), "请填写课时名称");
    String now = Instant.now().toString();
    ObjectNode curriculum =
        obj(
            "id", id("CURRICULUM"),
            "name", name,
            "domain", domain,
            "objective", objective,
            "audience", payload.path("audience").asText("本系统受训学员"),
            "status", "ACTIVE",
            "editRevision", 1,
            "units",
                arr(
                    obj(
                        "id", id("UNIT"),
                        "name", unitName,
                        "objective", payload.path("unitObjective").asText(objective),
                        "order", 1)),
            "createdBy", actor,
            "createdAt", now,
            "updatedAt", now);
    state.withArray("curricula").add(curriculum);
    return curriculum.deepCopy();
  }

  private static ObjectNode addUnit(
      ObjectNode state, ObjectNode payload, String actor, String domain) {
    ObjectNode curriculum = curriculum(state, payload.path("id").asText(), domain);
    String name = payload.path("name").asText().trim();
    require(!name.isBlank(), "请填写课时名称");
    ObjectNode unit =
        obj(
            "id", id("UNIT"),
            "name", name,
            "objective", payload.path("objective").asText(curriculum.path("objective").asText()),
            "order", curriculum.withArray("units").size() + 1);
    curriculum.withArray("units").add(unit);
    curriculum.put("editRevision", curriculum.path("editRevision").asInt() + 1);
    curriculum.put("updatedBy", actor).put("updatedAt", Instant.now().toString());
    return unit.deepCopy();
  }

  private static ObjectNode createCourseware(
      ObjectNode state, ObjectNode payload, String actor, String domain) {
    requireScope(payload.path("domain").asText(), domain);
    TrainingDomain trainingDomain = TrainingDomain.require(domain);
    require(trainingDomain.courseAuthoringEnabled(), "保障教学课件将在P5开放");
    ObjectNode curriculum;
    if (payload.path("curriculumId").asText().isBlank()) {
      ObjectNode definition =
          obj(
              "name", payload.path("curriculumName").asText(),
              "objective", payload.path("curriculumObjective").asText(),
              "audience", payload.path("audience").asText("本系统受训学员"),
              "unitName", payload.path("unitName").asText(),
              "unitObjective", payload.path("unitObjective").asText());
      curriculum = createCurriculumInternal(state, definition, actor, domain);
    } else curriculum = curriculum(state, payload.path("curriculumId").asText(), domain);
    String unitId = payload.path("unitId").asText();
    if (unitId.isBlank() && !curriculum.withArray("units").isEmpty())
      unitId = curriculum.withArray("units").get(0).path("id").asText();
    require(unit(curriculum, unitId) != null, "所选课时不属于当前课程");

    ObjectNode template = template(state, payload.path("templateId").asText(), domain);
    String name = payload.path("name").asText().trim();
    require(!name.isBlank(), "请填写课件名称");
    ArrayNode steps =
        payload.path("steps").isArray()
            ? (ArrayNode) payload.path("steps").deepCopy()
            : blankSteps(payload.path("stepCount").asInt(5));
    if (domain.equals("SUPPORT")) {
      enrichSupportSteps(steps);
      if (!payload.path("steps").isArray()) distributeDefaultPoints(steps);
    }
    ObjectNode scoreRule =
        payload.path("scoreRule").isObject()
            ? (ObjectNode) payload.path("scoreRule").deepCopy()
            : obj("total", 100, "errorPenalty", 5, "helpPenalty", 2);
    String now = Instant.now().toString();
    ObjectNode courseware =
        obj(
            "id", id("COURSEWARE"),
            "familyId", id("COURSEWARE-FAMILY"),
            "name", name,
            "domain", domain,
            "curriculumId", curriculum.path("id").asText(),
            "unitId", unitId,
            "version", 1,
            "editRevision", 1,
            "status", "DRAFT",
            "createdBy", actor,
            "interactionSchemaVersion", 3,
            "environment", environment(template),
            "environmentIssues", arr(),
            "steps", steps,
            "mode", payload.path("mode").asText("GUIDED"),
            "description", payload.path("description").asText(),
            "scoreRule", scoreRule,
            "createdAt", now,
            "updatedAt", now);
    courseware.putNull("publishedSnapshot");
    if (domain.equals("SUPPORT") && payload.path("supportCaseSnapshot").isObject())
      courseware.set("supportCaseSnapshot", payload.path("supportCaseSnapshot").deepCopy());
    else if (domain.equals("SUPPORT"))
      courseware.set(
          "supportCaseSnapshot",
          obj(
              "id", id("SUPPORT-CASE"),
              "version", 1,
              "purpose", "TRAINING",
              "name", "泵组保障方案编制与延迟事件处置",
              "taskObject", "演示船A · 通用泵组",
              "scope", "合成保障教学案例",
              "deadline", 270,
              "arrivalTime", 90,
              "transportCost", 100,
              "operations", Seed.operations(false),
              "resources", state.path("resources").deepCopy()));
    structureIssues(courseware, true);
    courseware.set("environmentIssues", environmentIssues(courseware, template));
    state.withArray("courses").add(courseware);
    return courseware.deepCopy();
  }

  private static ObjectNode save(
      ObjectNode state, ObjectNode payload, String actor, String domain) {
    ObjectNode courseware = draft(state, payload.path("id").asText(), domain);
    int expected = payload.path("expectedEditRevision").asInt(-1);
    if (expected >= 0 && expected != courseware.path("editRevision").asInt())
      throw new BusinessException(409, "EDIT_CONFLICT", "课件已被其他窗口更新，请刷新后核对本地内容");
    for (String key : List.of("name", "description", "mode", "steps", "scoreRule", "supportCaseSnapshot"))
      if (payload.has(key)) courseware.set(key, payload.get(key).deepCopy());
    if (domain.equals("SUPPORT")) enrichSupportSteps(courseware.withArray("steps"));
    structureIssues(courseware, true);
    ObjectNode template = boundTemplate(state, courseware, domain);
    courseware.set("environmentIssues", environmentIssues(courseware, template));
    courseware.put("editRevision", courseware.path("editRevision").asInt() + 1);
    courseware.put("updatedBy", actor).put("updatedAt", Instant.now().toString());
    return courseware.deepCopy();
  }

  private static ObjectNode rebind(
      ObjectNode state, ObjectNode payload, String actor, String domain) {
    ObjectNode courseware = draft(state, payload.path("id").asText(), domain);
    int expected = payload.path("expectedEditRevision").asInt(-1);
    if (expected >= 0 && expected != courseware.path("editRevision").asInt())
      throw new BusinessException(409, "EDIT_CONFLICT", "课件已被其他窗口更新，请刷新后重试");
    ObjectNode template = template(state, payload.path("templateId").asText(), domain);
    courseware.set("environment", environment(template));
    courseware.set("environmentIssues", environmentIssues(courseware, template));
    courseware.put("editRevision", courseware.path("editRevision").asInt() + 1);
    courseware.put("updatedBy", actor).put("updatedAt", Instant.now().toString());
    return courseware.deepCopy();
  }

  private static ObjectNode validate(ObjectNode state, ObjectNode payload, String domain) {
    ObjectNode courseware = courseware(state, payload.path("id").asText(), domain);
    ObjectNode template = boundTemplate(state, courseware, domain);
    ArrayNode issues = strictIssues(courseware, template);
    return obj(
        "valid", issues.isEmpty(),
        "issues", issues,
        "stepCount", courseware.withArray("steps").size(),
        "objectCount", template.path("sceneSnapshot").path("objects").size(),
        "templateRef", courseware.path("environment").path("templateRef").deepCopy(),
        "message", issues.isEmpty() ? "课件检查通过，可以预览或提交审核" : "课件仍有待完善项");
  }

  private static ObjectNode preview(
      ObjectNode state, ObjectNode payload, String actor, String domain) {
    ObjectNode courseware = courseware(state, payload.path("id").asText(), domain);
    ObjectNode step = findStep(courseware.withArray("steps"), payload.path("stepId").asText());
    ObjectNode simulation = previewStart(
        state,
        obj("id", courseware.path("id").asText(), "stepIndex", indexOfStep(courseware.withArray("steps"), step.path("id").asText())),
        actor,
        domain);
    return previewAction(
        state,
        obj(
            "previewId", simulation.path("id").asText(),
            "stepId", step.path("id").asText(),
            "target", step.path("target").asText(),
            "actionId", step.path("actionId").asText(),
            "parameters", step.path("parameters").deepCopy()),
        actor,
        domain);
  }

  private static ObjectNode previewStart(
      ObjectNode state, ObjectNode payload, String actor, String domain) {
    ObjectNode courseware = courseware(state, payload.path("id").asText(), domain);
    ObjectNode template = boundTemplate(state, courseware, domain);
    requireNoIssues(strictIssues(courseware, template));
    ObjectNode project =
        obj(
            "id", courseware.path("id").asText(),
            "version", courseware.path("version").asInt(),
            "domain", domain);
    ObjectNode scene = (ObjectNode) template.path("sceneSnapshot");
    ObjectNode topology =
        template.path("topologySnapshot").isObject()
            ? (ObjectNode) template.path("topologySnapshot")
            : obj("connections", arr(), "rules", arr());
    ObjectNode simulation = SimulationRuleEngine.createPreview(project, scene, topology, actor);
    simulation.set(
        "coursewareRef",
        obj("id", courseware.path("id").asText(), "version", courseware.path("version").asInt()));
    int stepIndex = payload.path("stepIndex").asInt(0);
    require(stepIndex >= 0 && stepIndex < courseware.withArray("steps").size(), "预览步骤不存在");
    simulation.put("currentStep", stepIndex).put("sessionStatus", "RUNNING").put("lastPassed", false);
    state.withArray("simulationPreviews").add(simulation);
    return simulation.deepCopy();
  }

  private static ObjectNode previewAction(
      ObjectNode state, ObjectNode payload, String actor, String domain) {
    ObjectNode simulation = previewSession(state, payload.path("previewId").asText(), actor);
    require(simulation.path("sessionStatus").asText().equals("RUNNING"), "预览会话已经结束");
    ObjectNode courseware = courseware(state, simulation.path("coursewareRef").path("id").asText(), domain);
    int current = simulation.path("currentStep").asInt();
    require(current < courseware.withArray("steps").size(), "预览步骤已经完成");
    ObjectNode step = (ObjectNode) courseware.withArray("steps").get(current);
    String target = payload.path("target").asText();
    String actionId = payload.path("actionId").asText();
    ObjectNode parameters = payload.path("parameters").isObject() ? (ObjectNode) payload.path("parameters") : obj();
    String reason = "";
    if (!payload.path("stepId").asText().equals(step.path("id").asText())) reason = "请按当前步骤顺序试学";
    else if (!target.equals(step.path("target").asText())) reason = "试学对象与当前步骤不一致";
    else if (!actionId.equals(step.path("actionId").asText())) reason = "试学动作与当前步骤不一致";
    else if (!parameters.equals(step.path("parameters"))) reason = "试学使用了实际输入，但参数不符合步骤要求";
    else if (!SimulationRuleEngine.matchesCondition((ObjectNode) simulation.path("states"), step.path("precondition"))) reason = "当前状态不满足教学前置条件";
    if (!reason.isBlank()) {
      simulation.put("lastPassed", false).put("lastReason", reason);
      return obj("id", simulation.path("id").asText(), "stepId", step.path("id").asText(), "passed", false, "reason", reason, "states", simulation.path("states").deepCopy());
    }
    ObjectNode event = SimulationRuleEngine.execute(simulation, target, actionId, parameters);
    boolean passed = event.path("result").asText().equals("SUCCEEDED");
    simulation.put("lastPassed", passed).put("lastReason", event.path("reason").asText());
    return obj("id", simulation.path("id").asText(), "stepId", step.path("id").asText(), "passed", passed, "event", event.deepCopy(), "states", simulation.path("states").deepCopy());
  }

  private static ObjectNode previewContinue(
      ObjectNode state, ObjectNode payload, String actor, String domain) {
    ObjectNode simulation = previewSession(state, payload.path("previewId").asText(), actor);
    require(simulation.path("lastPassed").asBoolean(), "当前步骤尚未通过");
    ObjectNode courseware = courseware(state, simulation.path("coursewareRef").path("id").asText(), domain);
    int next = simulation.path("currentStep").asInt() + 1;
    simulation.put("currentStep", next).put("lastPassed", false);
    if (next >= courseware.withArray("steps").size()) simulation.put("sessionStatus", "COMPLETED");
    return simulation.deepCopy();
  }

  private static ObjectNode previewReset(
      ObjectNode state, ObjectNode payload, String actor, String domain) {
    ObjectNode prior = previewSession(state, payload.path("previewId").asText(), actor);
    prior.put("sessionStatus", "CLOSED").put("status", "CLOSED");
    return previewStart(state, obj("id", prior.path("coursewareRef").path("id").asText()), actor, domain);
  }

  private static ObjectNode previewClose(ObjectNode state, ObjectNode payload, String actor) {
    ObjectNode simulation = previewSession(state, payload.path("previewId").asText(), actor);
    simulation.put("sessionStatus", "CLOSED").put("status", "CLOSED");
    return simulation.deepCopy();
  }

  private static ObjectNode submit(
      ObjectNode state, ObjectNode payload, String actor, String domain) {
    ObjectNode courseware = draft(state, payload.path("id").asText(), domain);
    ObjectNode template = boundTemplate(state, courseware, domain);
    requireNoIssues(strictIssues(courseware, template));
    courseware.set("reviewSnapshot", contentSnapshot(courseware, template));
    courseware.put("status", "PENDING_REVIEW");
    courseware.put("submittedBy", actor).put("submittedAt", Instant.now().toString());
    return courseware.deepCopy();
  }

  private static ObjectNode approve(
      ObjectNode state, ObjectNode payload, String actor, String domain) {
    ObjectNode courseware = courseware(state, payload.path("id").asText(), domain);
    require(courseware.path("status").asText().equals("PENDING_REVIEW"), "课件未处于待审核状态");
    require(!actor.equals(courseware.path("createdBy").asText()), "不能审核自己制作的课件，请切换独立审核教员");
    ObjectNode template = boundTemplate(state, courseware, domain);
    require(courseware.path("reviewSnapshot").equals(contentSnapshot(courseware, template)), "审核期间课件内容或依赖已变化，请退回重新提交");
    courseware.put("status", "APPROVED").put("reviewedBy", actor).put("reviewedAt", Instant.now().toString());
    return courseware.deepCopy();
  }

  private static ObjectNode returnForRevision(
      ObjectNode state, ObjectNode payload, String actor, String domain) {
    ObjectNode courseware = courseware(state, payload.path("id").asText(), domain);
    require(courseware.path("status").asText().equals("PENDING_REVIEW"), "课件不在待审状态");
    String comment = payload.path("comment").asText().trim();
    require(!comment.isBlank(), "退回时请填写审核意见");
    courseware.put("status", "DRAFT").put("reviewComment", comment).put("returnedBy", actor);
    return courseware.deepCopy();
  }

  private static ObjectNode publish(
      ObjectNode state, ObjectNode payload, String actor, String domain) {
    ObjectNode courseware = courseware(state, payload.path("id").asText(), domain);
    require(courseware.path("status").asText().equals("APPROVED"), "请先完成独立审核");
    ObjectNode template = boundTemplate(state, courseware, domain);
    requireNoIssues(strictIssues(courseware, template));
    ObjectNode snapshot = contentSnapshot(courseware, template);
    require(courseware.path("reviewSnapshot").equals(snapshot), "审核后课件内容或依赖已变化，请重新提交审核");
    courseware.set("publishedSnapshot", snapshot);
    courseware.put("status", "PUBLISHED").put("publishedBy", actor).put("publishedAt", Instant.now().toString());
    return courseware.deepCopy();
  }

  private static ObjectNode revise(
      ObjectNode state, ObjectNode payload, String actor, String domain) {
    ObjectNode published = courseware(state, payload.path("id").asText(), domain);
    require(published.path("status").asText().equals("PUBLISHED"), "只能修订已发布课件");
    ObjectNode revision = published.deepCopy();
    revision.put("id", id("COURSEWARE"));
    revision.put("parentId", published.path("id").asText());
    revision.put("version", published.path("version").asInt() + 1);
    revision.put("editRevision", 1);
    revision.put("status", "DRAFT");
    revision.put("createdBy", actor).put("createdAt", Instant.now().toString());
    revision.remove(List.of("submittedBy", "submittedAt", "reviewedBy", "reviewedAt", "publishedBy", "publishedAt", "reviewComment", "returnedBy"));
    revision.putNull("reviewSnapshot");
    revision.putNull("publishedSnapshot");
    state.withArray("courses").add(revision);
    return revision.deepCopy();
  }

  private static ArrayNode blankSteps(int count) {
    require(count >= 1 && count <= 20, "课件步骤数量应为1到20步");
    ArrayNode steps = arr();
    for (int index = 0; index < count; index++)
      steps.add(
          obj(
              "id", id("STEP"),
              "name", "步骤" + (index + 1),
              "description", "",
              "target", "",
              "actionId", "",
              "actionLabel", "",
              "parameters", obj(),
              "precondition", NullNode.instance,
              "completion", obj("kind", "ACTION_SUCCEEDED"),
              "expectedResult", "",
              "points", 20,
              "mode", "GUIDED"));
    return steps;
  }

  private static ObjectNode environment(ObjectNode template) {
    ObjectNode scene = (ObjectNode) template.path("sceneSnapshot");
    ObjectNode topology =
        template.path("topologySnapshot").isObject()
            ? (ObjectNode) template.path("topologySnapshot")
            : obj();
    ObjectNode environment =
        obj(
            "templateRef", obj("id", template.path("id").asText(), "version", template.path("version").asInt()),
            "sceneRef", obj("id", scene.path("id").asText(), "version", scene.path("version").asInt(1)),
            "dependencies", template.path("dependencies").deepCopy());
    if (!topology.path("id").asText().isBlank())
      environment.set(
          "topologyRef",
          obj("id", topology.path("id").asText(), "version", topology.path("version").asInt(1)));
    else environment.putNull("topologyRef");
    return environment;
  }

  private static ObjectNode contentSnapshot(ObjectNode courseware, ObjectNode template) {
    ObjectNode snapshot = obj(
        "interactionSchemaVersion", 3,
        "curriculumId", courseware.path("curriculumId").asText(),
        "unitId", courseware.path("unitId").asText(),
        "name", courseware.path("name").asText(),
        "description", courseware.path("description").asText(),
        "mode", courseware.path("mode").asText(),
        "environment", courseware.path("environment").deepCopy(),
        "sceneSnapshot", template.path("sceneSnapshot").deepCopy(),
        "topologySnapshot", template.path("topologySnapshot").deepCopy(),
        "templateDependencies", template.path("dependencies").deepCopy(),
        "steps", courseware.path("steps").deepCopy(),
        "scoreRule", courseware.path("scoreRule").deepCopy());
    if (courseware.path("supportCaseSnapshot").isObject())
      snapshot.set("supportCaseSnapshot", courseware.path("supportCaseSnapshot").deepCopy());
    return snapshot;
  }

  private static ArrayNode strictIssues(ObjectNode courseware, ObjectNode template) {
    ArrayNode issues = structureIssues(courseware, false);
    if (courseware.path("name").asText().trim().isBlank()) issues.add("请填写课件名称");
    if (courseware.path("description").asText().trim().isBlank()) issues.add("请填写教学目标说明");
    ArrayNode environmentIssues = environmentIssues(courseware, template);
    issues.addAll(environmentIssues);
    Map<String, ObjectNode> objects = objects(template);
    int score = 0;
    for (int index = 0; index < courseware.withArray("steps").size(); index++) {
      JsonNode step = courseware.withArray("steps").get(index);
      String label = "第" + (index + 1) + "步：";
      if (step.path("description").asText().trim().isBlank()) issues.add(label + "请填写操作说明");
      if (step.path("actionLabel").asText().trim().isBlank()) issues.add(label + "请填写动作按钮文字");
      if (step.path("expectedResult").asText().trim().isBlank()) issues.add(label + "请填写结果说明");
      ObjectNode object = objects.get(step.path("target").asText());
      ObjectNode action = object == null ? null : findById(object.withArray("actions"), step.path("actionId").asText());
      if (object != null && action != null) validateParameters(issues, label, action, step.path("parameters"));
      validateCondition(issues, label, step.path("precondition"), objects);
      int points = step.path("points").asInt(-1);
      if (points <= 0) issues.add(label + "分值必须大于0");
      else score += points;
    }
    int total = courseware.path("scoreRule").path("total").asInt(-1);
    if (total <= 0) issues.add("总分必须大于0");
    else if (score != total) issues.add("步骤分值合计" + score + "与课件总分" + total + "不一致");
    if (courseware.path("scoreRule").path("errorPenalty").asInt(-1) < 0
        || courseware.path("scoreRule").path("helpPenalty").asInt(-1) < 0)
      issues.add("错误和帮助扣分不能小于0");
    return unique(issues);
  }

  private static ArrayNode structureIssues(ObjectNode courseware, boolean throwOnStructuralError) {
    ArrayNode issues = arr();
    if (!courseware.path("steps").isArray()) issues.add("教学步骤必须是数组");
    Set<String> ids = new HashSet<>();
    for (JsonNode step : courseware.withArray("steps")) {
      String id = step.path("id").asText();
      if (id.isBlank() || !ids.add(id)) issues.add("步骤编号缺失或重复");
      if (step.path("name").asText().trim().isBlank()) issues.add("步骤名称不能为空");
      String mode = step.path("mode").asText(courseware.path("mode").asText("GUIDED"));
      if (!MODES.contains(mode)) issues.add("步骤模式不正确：" + mode);
      else if (!mode.equals(courseware.path("mode").asText()))
        issues.add("本版本要求步骤提示策略与课件训练模式一致，不能在运行时静默忽略");
      String completion = step.path("completion").path("kind").asText("ACTION_SUCCEEDED");
      if (!completion.equals("ACTION_SUCCEEDED")) issues.add("本版本只支持动作成功作为完成条件");
      if (courseware.path("domain").asText().equals("SUPPORT")
          && !SUPPORT_KINDS.contains(step.path("supportKind").asText()))
        issues.add("保障教学步骤类型不正确：" + step.path("supportKind").asText());
    }
    if (!MODES.contains(courseware.path("mode").asText())) issues.add("课件模式不正确");
    if (throwOnStructuralError && !issues.isEmpty())
      throw new BusinessException(400, "COURSEWARE_STRUCTURE_INVALID", issues.get(0).asText());
    return issues;
  }

  private static ArrayNode environmentIssues(ObjectNode courseware, ObjectNode template) {
    ArrayNode issues = arr();
    Map<String, ObjectNode> objects = objects(template);
    for (int index = 0; index < courseware.withArray("steps").size(); index++) {
      JsonNode step = courseware.withArray("steps").get(index);
      String target = step.path("target").asText();
      if (target.isBlank()) {
        issues.add("第" + (index + 1) + "步尚未选择目标对象");
        continue;
      }
      ObjectNode object = objects.get(target);
      if (object == null) {
        issues.add("第" + (index + 1) + "步引用的对象已不在当前模板：" + target);
        continue;
      }
      String actionId = step.path("actionId").asText();
      if (actionId.isBlank()) issues.add("第" + (index + 1) + "步尚未选择设备动作");
      else if (findById(object.withArray("actions"), actionId) == null)
        issues.add("第" + (index + 1) + "步动作不属于目标对象：" + actionId);
    }
    return unique(issues);
  }

  private static void validateParameters(
      ArrayNode issues, String label, ObjectNode action, JsonNode parameters) {
    for (JsonNode definition : action.withArray("parameters")) {
      String id = definition.path("id").asText();
      JsonNode value = parameters.path(id);
      if (definition.path("required").asBoolean() && value.isMissingNode()) {
        issues.add(label + "缺少动作参数" + id);
        continue;
      }
      if (value.isMissingNode()) continue;
      String type = definition.path("valueType").asText();
      if ((type.equals("BOOLEAN") && !value.isBoolean())
          || (type.equals("NUMBER") && !value.isNumber())
          || (type.equals("STRING") && !value.isTextual()))
        issues.add(label + "动作参数" + id + "类型应为" + type);
    }
  }

  private static void validateCondition(
      ArrayNode issues, String label, JsonNode condition, Map<String, ObjectNode> objects) {
    if (condition.isNull() || condition.isMissingNode()) return;
    if (!condition.isObject()) {
      issues.add(label + "前置条件结构不正确");
      return;
    }
    ObjectNode object = objects.get(condition.path("objectId").asText());
    if (object == null) {
      issues.add(label + "前置条件对象不存在");
      return;
    }
    ObjectNode field = findById(object.withArray("stateFields"), condition.path("field").asText());
    if (field == null) issues.add(label + "前置条件状态字段不存在");
    String operator = condition.path("operator").asText();
    if (!OPERATORS.contains(operator)) issues.add(label + "前置条件比较方式不受支持");
    if (field != null && !field.path("valueType").asText().equals("NUMBER")
        && Set.of("LT", "LTE", "GT", "GTE").contains(operator))
      issues.add(label + "只有数值状态支持大小比较");
    if (field != null) {
      JsonNode value = condition.path("value");
      String type = field.path("valueType").asText();
      if ((type.equals("BOOLEAN") && !value.isBoolean())
          || (type.equals("NUMBER") && !value.isNumber())
          || (type.equals("STRING") && !value.isTextual()))
        issues.add(label + "前置条件值类型应为" + type);
    }
  }

  private static Map<String, ObjectNode> objects(ObjectNode template) {
    Map<String, ObjectNode> result = new LinkedHashMap<>();
    for (JsonNode item : template.path("sceneSnapshot").path("objects"))
      result.put(item.path("id").asText(), (ObjectNode) item);
    return result;
  }

  private static ObjectNode template(ObjectNode state, String id, String domain) {
    ObjectNode template = find(state, "systemTemplates", id);
    require(template.path("status").asText().equals("PUBLISHED"), "请选择已发布的仿真系统模板");
    require(SimulationTemplateService.templateVisible(template, domain), "模板未授权给当前系统");
    require(template.path("sceneSnapshot").isObject(), "旧兼容模板不能用于交互契约V3课件");
    for (JsonNode dependency : template.withArray("dependencies"))
      SimulationProjectService.requireAssetReference(
          state,
          dependency.path("assetRef").path("id").asText(),
          dependency.path("assetRef").path("version").asInt(-1),
          domain);
    return template;
  }

  private static ObjectNode boundTemplate(ObjectNode state, ObjectNode courseware, String domain) {
    JsonNode reference = courseware.path("environment").path("templateRef");
    ObjectNode template = template(state, reference.path("id").asText(), domain);
    require(template.path("version").asInt() == reference.path("version").asInt(), "课件绑定的模板版本不一致");
    return template;
  }

  private static ObjectNode curriculum(ObjectNode state, String id, String domain) {
    ObjectNode curriculum = find(state, "curricula", id);
    requireScope(curriculum.path("domain").asText(), domain);
    return curriculum;
  }

  private static JsonNode unit(ObjectNode curriculum, String id) {
    for (JsonNode item : curriculum.withArray("units"))
      if (item.path("id").asText().equals(id)) return item;
    return null;
  }

  private static ObjectNode courseware(ObjectNode state, String id, String domain) {
    ObjectNode courseware = find(state, "courses", id);
    requireScope(courseware.path("domain").asText(), domain);
    require(courseware.path("interactionSchemaVersion").asInt() == 3, "该课件使用旧交互契约，请走兼容流程");
    return courseware;
  }

  private static ObjectNode draft(ObjectNode state, String id, String domain) {
    ObjectNode courseware = courseware(state, id, domain);
    require(courseware.path("status").asText().equals("DRAFT"), "请先创建课件修订草稿再修改");
    return courseware;
  }

  private static ObjectNode findStep(ArrayNode steps, String id) {
    for (JsonNode item : steps)
      if (item.path("id").asText().equals(id)) return (ObjectNode) item;
    throw new BusinessException(404, "NOT_FOUND", "教学步骤不存在：" + id);
  }

  private static int indexOfStep(ArrayNode steps, String id) {
    for (int index = 0; index < steps.size(); index++)
      if (steps.get(index).path("id").asText().equals(id)) return index;
    return -1;
  }

  private static ObjectNode previewSession(ObjectNode state, String id, String actor) {
    ObjectNode preview = find(state, "simulationPreviews", id);
    require(preview.path("createdBy").asText().equals(actor), "只能操作本人启动的课件预览会话");
    return preview;
  }

  private static void enrichSupportSteps(ArrayNode steps) {
    String[] kinds = {"TASK_CONFIRM", "RESOURCE_CONFIGURE", "PLAN_EDIT", "PLAN_CALCULATE", "EVENT_HANDLE", "REVIEW_SUBMIT"};
    for (int index = 0; index < steps.size(); index++) {
      ObjectNode step = (ObjectNode) steps.get(index);
      if (step.path("supportKind").asText().isBlank())
        step.put("supportKind", kinds[Math.min(index, kinds.length - 1)]);
    }
  }

  private static void distributeDefaultPoints(ArrayNode steps) {
    if (steps.isEmpty()) return;
    int base = 100 / steps.size(), remainder = 100 % steps.size();
    for (int index = 0; index < steps.size(); index++)
      ((ObjectNode) steps.get(index)).put("points", base + (index < remainder ? 1 : 0));
  }

  private static ObjectNode findById(ArrayNode values, String id) {
    for (JsonNode item : values)
      if (item.path("id").asText().equals(id)) return (ObjectNode) item;
    return null;
  }

  private static ArrayNode unique(ArrayNode values) {
    ArrayNode result = arr();
    Set<String> seen = new LinkedHashSet<>();
    for (JsonNode item : values) if (seen.add(item.asText())) result.add(item.asText());
    return result;
  }

  private static void requireNoIssues(ArrayNode issues) {
    if (!issues.isEmpty())
      throw new BusinessException(400, "COURSEWARE_INVALID", issues.get(0).asText());
  }

  private static void requireScope(String actual, String requested) {
    TrainingDomain.require(actual);
    if (requested == null || requested.isBlank())
      throw new BusinessException(400, "DOMAIN_REQUIRED", "课程课件命令必须指定当前系统");
    if (!actual.equals(requested))
      throw new BusinessException(403, "DOMAIN_MISMATCH", "当前系统不能操作其他系统的课程课件");
  }
}

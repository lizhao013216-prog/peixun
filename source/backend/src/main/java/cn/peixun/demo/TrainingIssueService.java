package cn.peixun.demo;

import static cn.peixun.demo.BusinessException.require;
import static cn.peixun.demo.Json.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.time.Instant;

/** Optional post-training issue triage. Normal completion and archiving do not depend on it. */
public final class TrainingIssueService {
  private TrainingIssueService() {}

  public static ObjectNode apply(
      ObjectNode state, String action, ObjectNode payload, String actor, String domain) {
    return switch (action) {
      case "issue.create" -> create(state, payload, actor, domain);
      case "issue.verify" -> verify(state, payload, actor);
      case "issue.route" -> route(state, payload, actor);
      case "issue.resolve", "issue.close" -> resolve(state, payload, actor, action.equals("issue.close"));
      default -> throw new BusinessException(400, "UNKNOWN_ACTION", "未知训练问题命令：" + action);
    };
  }

  private static ObjectNode create(ObjectNode state, ObjectNode payload, String actor, String domain) {
    ObjectNode attempt = find(state, "attempts", payload.path("id").asText());
    require(domain.isBlank() || domain.equals(attempt.path("domain").asText()), "当前系统不能登记其他系统训练问题");
    require(
        actor.equals(attempt.path("learnerId").asText())
            || DemoService.role(actor).matches("INSTRUCTOR|ADMIN"),
        "只能登记本人训练或教员可见的训练问题");
    String type = payload.path("type").asText();
    require(type.matches("CONTENT|LEARNING|PLATFORM|EQUIPMENT_SUPPORT"), "问题类型不正确");
    String name = payload.path("name").asText().trim();
    require(!name.isBlank(), "请填写问题标题");
    ObjectNode issue =
        obj(
            "id", id("ISSUE"),
            "name", name,
            "description", payload.path("description").asText(),
            "type", type,
            "sourceAttemptId", attempt.path("id").asText(),
            "sourceId", attempt.path("id").asText(),
            "sourceCourseRef", obj("id", attempt.path("courseId").asText(), "version", attempt.path("courseVersion").asInt()),
            "domain", attempt.path("domain").asText(),
            "learnerId", attempt.path("learnerId").asText(),
            "createdBy", actor,
            "status", "TRIAGE",
            "verified", false,
            "history", arr(obj("status", "TRIAGE", "actor", actor, "reason", "训练问题已登记", "at", Instant.now().toString())),
            "createdAt", Instant.now().toString());
    state.withArray("issues").add(issue);
    return issue;
  }

  private static ObjectNode verify(ObjectNode state, ObjectNode payload, String actor) {
    require(DemoService.role(actor).matches("PLANNER|ADMIN"), "只有保障筹划人员可以核实疑似保障问题");
    ObjectNode issue = find(state, "issues", payload.path("id").asText());
    require(issue.path("type").asText().equals("EQUIPMENT_SUPPORT"), "只有疑似保障问题需要保障核实");
    String opinion = payload.path("opinion").asText().trim();
    require(!opinion.isBlank(), "请填写核实意见");
    issue.put("verified", true).put("verifiedBy", actor).put("verificationOpinion", opinion).put("verifiedAt", Instant.now().toString());
    history(issue, "VERIFIED", actor, opinion);
    return issue;
  }

  private static ObjectNode route(ObjectNode state, ObjectNode payload, String actor) {
    ObjectNode issue = find(state, "issues", payload.path("id").asText());
    if (issue.hasNonNull("resolutionId") || issue.hasNonNull("linkedTaskId")) return issue;
    String type = issue.path("type").asText();
    if (type.equals("CONTENT")) {
      require(DemoService.role(actor).matches("INSTRUCTOR|ADMIN"), "只有教员可以发起课件修订");
      ObjectNode revision =
          CoursewareService.apply(
              state,
              "courseware.revise",
              obj("id", issue.path("sourceCourseRef").path("id").asText()),
              actor,
              issue.path("domain").asText());
      issue.put("resolutionType", "COURSEWARE_REVISION").put("resolutionId", revision.path("id").asText()).put("status", "IN_PROGRESS");
      find(state, "courses", revision.path("id").asText()).put("sourceIssueId", issue.path("id").asText());
      history(issue, "IN_PROGRESS", actor, "已创建课件修订草稿");
      return issue;
    }
    if (type.equals("LEARNING")) {
      require(DemoService.role(actor).matches("INSTRUCTOR|ADMIN"), "只有教员可以分配补训");
      ObjectNode attempt = find(state, "attempts", issue.path("sourceAttemptId").asText());
      ObjectNode prior = find(state, "assignments", attempt.path("assignmentId").asText());
      ObjectNode assignment = prior.deepCopy();
      assignment.put("id", id("ASSIGN")).put("status", "ASSIGNED").put("confirmed", false).put("sourceIssueId", issue.path("id").asText()).put("previousAttemptId", attempt.path("id").asText()).put("createdBy", actor).put("createdAt", Instant.now().toString());
      assignment.remove("latestAttemptId");
      assignment.remove("evidenceAttemptId");
      assignment.remove("archiveId");
      state.withArray("assignments").add(assignment);
      issue.put("resolutionType", "RETRAINING_ASSIGNMENT").put("resolutionId", assignment.path("id").asText()).put("status", "IN_PROGRESS");
      history(issue, "IN_PROGRESS", actor, "已建立补训任务，原成绩保持不变");
      return issue;
    }
    if (type.equals("PLATFORM")) {
      require(DemoService.role(actor).matches("INSTRUCTOR|ADMIN"), "只有教员可以登记技术处理");
      String opinion = payload.path("opinion").asText("已登记技术排查");
      issue.put("resolutionType", "TECHNICAL_TICKET").put("resolutionId", id("TECH")).put("handlingOpinion", opinion).put("status", "IN_PROGRESS");
      history(issue, "IN_PROGRESS", actor, opinion);
      return issue;
    }
    require(type.equals("EQUIPMENT_SUPPORT"), "问题类型不支持分流");
    require(DemoService.role(actor).matches("PLANNER|ADMIN"), "只有保障筹划人员可以转保障任务");
    require(issue.path("verified").asBoolean(), "疑似保障问题必须先由保障筹划人员核实");
    ObjectNode sourceAttempt = find(state, "attempts", issue.path("sourceAttemptId").asText());
    require(sourceAttempt.path("status").asText().equals("COMPLETED"), "只有已完成训练中的问题可以转为正式保障任务");
    String object = payload.path("object").asText().trim();
    String scope = payload.path("scope").asText().trim();
    String taskType = payload.path("taskType").asText().trim();
    require(!object.isBlank() && !scope.isBlank() && !taskType.isBlank(), "请填写保障对象、任务范围和任务类型");
    int dueMinutes = payload.path("dueMinutes").asInt();
    require(dueMinutes > 0 && dueMinutes <= 10080, "完成时限必须为1～10080分钟");
    ObjectNode task =
        obj(
            "id", id("TASK"),
            "name", payload.path("name").asText("训练问题核实转保障任务"),
            "domain", "SUPPORT",
            "purpose", "BUSINESS",
            "object", object,
            "scope", scope,
            "type", taskType,
            "dueMinutes", dueMinutes,
            "constraints", payload.path("constraints").asText(),
            "status", "DRAFT",
            "sourceIssueId", issue.path("id").asText(),
            "sourceAttemptId", issue.path("sourceAttemptId").asText(),
            "sourceCourseRef", issue.path("sourceCourseRef").deepCopy(),
            "createdBy", actor,
            "createdAt", Instant.now().toString());
    state.withArray("tasks").add(task);
    issue.put("linkedTaskId", task.path("id").asText()).put("resolutionType", "SUPPORT_TASK").put("resolutionId", task.path("id").asText()).put("status", "IN_PROGRESS");
    history(issue, "IN_PROGRESS", actor, "已核实并创建保障任务");
    return issue;
  }

  private static ObjectNode resolve(ObjectNode state, ObjectNode payload, String actor, boolean close) {
    ObjectNode issue = find(state, "issues", payload.path("id").asText());
    require(DemoService.role(actor).matches("INSTRUCTOR|PLANNER|ADMIN"), "当前身份不能关闭训练问题");
    String opinion = payload.path("opinion").asText().trim();
    require(!opinion.isBlank(), "请填写处理结论");
    issue.put("status", close ? "CLOSED" : "RESOLVED").put("resolutionOpinion", opinion).put("resolvedBy", actor).put("resolvedAt", Instant.now().toString());
    history(issue, issue.path("status").asText(), actor, opinion);
    return issue;
  }

  private static void history(ObjectNode issue, String status, String actor, String reason) {
    issue.withArray("history").add(obj("status", status, "actor", actor, "reason", reason, "at", Instant.now().toString()));
  }
}

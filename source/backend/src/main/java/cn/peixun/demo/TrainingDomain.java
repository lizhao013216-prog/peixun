package cn.peixun.demo;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Set;

public enum TrainingDomain {
  OPERATION("装备使用及作战运用", true),
  MAINTENANCE("装备维修保障", true),
  SUPPORT("保障任务筹划及行为演练", true);

  private static final Set<String> VALUES = Set.of("OPERATION", "MAINTENANCE", "SUPPORT");
  private final String displayName;
  private final boolean courseAuthoringEnabled;

  TrainingDomain(String displayName, boolean courseAuthoringEnabled) {
    this.displayName = displayName;
    this.courseAuthoringEnabled = courseAuthoringEnabled;
  }

  public String displayName() {
    return displayName;
  }

  public boolean courseAuthoringEnabled() {
    return courseAuthoringEnabled;
  }

  public static TrainingDomain require(String value) {
    if (!VALUES.contains(value))
      throw new BusinessException(400, "DOMAIN_MISMATCH", "业务领域不正确：" + value);
    return valueOf(value);
  }

  public static void requireCommandScope(
      ObjectNode state, String action, ObjectNode payload, String requestedDomain) {
    if (action.startsWith("simulationProject.")
        || action.startsWith("simulationTemplate.")
        || action.startsWith("simulationTopology.")
        || action.startsWith("simulationPreview.")
        || action.startsWith("curriculum.")
        || action.startsWith("courseware.")
        || action.startsWith("asset.")) {
      if (requestedDomain == null || requestedDomain.isBlank())
        throw new BusinessException(400, "DOMAIN_REQUIRED", "资源准备命令必须指定当前系统");
    }
    if (requestedDomain == null || requestedDomain.isBlank()) return;
    require(requestedDomain);
    String actualDomain = "";
    if (action.equals("simulationProject.create")) actualDomain = payload.path("domain").asText();
    else if (action.equals("simulationProject.clone"))
      actualDomain = payload.path("domain").asText();
    else if (action.startsWith("simulationProject."))
      actualDomain =
          Json.find(state, "simulationProjects", payload.path("id").asText())
              .path("domain")
              .asText();
    else if (action.equals("simulationTemplate.validate")
        || action.equals("simulationTemplate.publish"))
      actualDomain =
          Json.find(state, "simulationProjects", payload.path("projectId").asText())
              .path("domain")
              .asText();
    else if (action.equals("simulationTemplate.instantiate"))
      actualDomain = payload.path("domain").asText();
    else if (action.equals("simulationTopology.save") || action.equals("simulationPreview.start"))
      actualDomain =
          Json.find(state, "simulationProjects", payload.path("projectId").asText())
              .path("domain")
              .asText();
    else if (action.startsWith("simulationPreview."))
      actualDomain =
          Json.find(state, "simulationPreviews", payload.path("id").asText())
              .path("domain")
              .asText();
    else if (action.equals("asset.import")) actualDomain = payload.path("ownerSystem").asText();
    else if (action.startsWith("asset.")) {
      ObjectNode asset = Json.find(state, "assets", payload.path("id").asText());
      actualDomain = asset.path("ownerSystem").asText();
      if (actualDomain.equals("SHARED"))
        throw new BusinessException(403, "ASSET_READ_ONLY", "旧共享素材只能在公共资源中心只读查看");
    } else if (action.equals("curriculum.create") || action.equals("courseware.create"))
      actualDomain = payload.path("domain").asText();
    else if (action.startsWith("curriculum."))
      actualDomain = Json.find(state, "curricula", payload.path("id").asText()).path("domain").asText();
    else if (action.startsWith("courseware."))
      actualDomain = Json.find(state, "courses", payload.path("id").asText()).path("domain").asText();
    else if (action.equals("course.create")) actualDomain = payload.path("domain").asText();
    else if (action.startsWith("course."))
      actualDomain = Json.find(state, "courses", payload.path("id").asText()).path("domain").asText();
    else if (action.equals("training.station.save")) actualDomain = payload.path("domain").asText(requestedDomain);
    else if (action.equals("training.station.connect"))
      actualDomain = Json.find(state, "stations", payload.path("id").asText()).path("domain").asText();
    else if (action.equals("training.station.signal"))
      actualDomain = Json.find(state, "attempts", payload.path("attemptId").asText()).path("domain").asText();
    else if (action.equals("training.assign"))
      actualDomain =
          Json.find(state, "courses", payload.path("courseId").asText()).path("domain").asText();
    else if (action.equals("training.start"))
      actualDomain =
          Json.find(state, "assignments", payload.path("assignmentId").asText())
              .path("domain")
              .asText();
    else if (action.startsWith("training.") || action.equals("issue.create"))
      actualDomain =
          Json.find(state, "attempts", payload.path("id").asText()).path("domain").asText();
    else if (action.startsWith("issue."))
      actualDomain = Json.find(state, "issues", payload.path("id").asText()).path("domain").asText();
    if (!actualDomain.isBlank() && !requestedDomain.equals(actualDomain))
      throw new BusinessException(403, "DOMAIN_MISMATCH", "当前系统不能操作其他系统的数据");
  }
}

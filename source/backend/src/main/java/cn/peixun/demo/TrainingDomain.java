package cn.peixun.demo;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Set;

public enum TrainingDomain {
  OPERATION("装备使用及作战运用", true),
  MAINTENANCE("装备维修保障", true),
  SUPPORT("保障任务筹划及行为演练", false);

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
    if (requestedDomain == null || requestedDomain.isBlank()) return;
    require(requestedDomain);
    String actualDomain = "";
    if (action.equals("course.create")) actualDomain = payload.path("domain").asText();
    else if (action.startsWith("course."))
      actualDomain = Json.find(state, "courses", payload.path("id").asText()).path("domain").asText();
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
    if (!actualDomain.isBlank() && !requestedDomain.equals(actualDomain))
      throw new BusinessException(403, "DOMAIN_MISMATCH", "当前系统不能操作其他系统的数据");
  }
}

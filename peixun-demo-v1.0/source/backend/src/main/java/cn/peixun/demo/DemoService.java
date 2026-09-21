package cn.peixun.demo;

import static cn.peixun.demo.BusinessException.require;
import static cn.peixun.demo.Json.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DemoService {
  private final JdbcTemplate jdbc;

  public DemoService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional
  public synchronized ArrayNode workspaces() {
    if (jdbc.queryForObject("SELECT COUNT(*) FROM workspace_state", Integer.class) == 0)
      insert(Seed.create("demo", "综合业务演示", 1));
    ArrayNode a = arr();
    jdbc.query(
            "SELECT content FROM workspace_state ORDER BY updated_at",
            (rs, n) -> parse(rs.getString(1)))
        .forEach(
            s -> a.add(obj("id", s.get("id"), "name", s.get("name"), "epoch", s.get("epoch"))));
    return a;
  }

  @Transactional
  public synchronized ObjectNode createWorkspace(String name, String from) {
    require(name != null && !name.trim().isEmpty(), "请填写工作区名称");
    String id = id("WS");
    ObjectNode s =
        from == null || from.isBlank()
            ? Seed.create(id, name.trim(), 1)
            : load(from, false).deepCopy();
    s.put("id", id).put("name", name.trim()).put("epoch", 1).put("revision", 0);
    if (from != null && !from.isBlank()) s.put("clonedFrom", from);
    insert(s);
    return s;
  }

  @Transactional
  public synchronized ObjectNode state(String id) {
    ObjectNode s = load(id, true);
    if (TrainingModule.settleJobs(s)) save(s);
    return s;
  }

  private ObjectNode load(String id, boolean lock) {
    List<String> rows =
        jdbc.query(
            "SELECT content FROM workspace_state WHERE id=?" + (lock ? " FOR UPDATE" : ""),
            (rs, n) -> rs.getString(1),
            id);
    if (rows.isEmpty()) throw new BusinessException(404, "WORKSPACE_NOT_FOUND", "工作区不存在，请重新选择");
    return parse(rows.get(0));
  }

  private void insert(ObjectNode s) {
    jdbc.update(
        "INSERT INTO workspace_state(id,revision,epoch,content) VALUES(?,?,?,?)",
        s.path("id").asText(),
        s.path("revision").asLong(),
        s.path("epoch").asInt(),
        s.toString());
  }

  private void save(ObjectNode s) {
    s.put("revision", s.path("revision").asLong() + 1);
    jdbc.update(
        "UPDATE workspace_state SET revision=?,epoch=?,content=?,updated_at=CURRENT_TIMESTAMP WHERE"
            + " id=?",
        s.path("revision").asLong(),
        s.path("epoch").asInt(),
        s.toString(),
        s.path("id").asText());
  }

  @Transactional
  public synchronized ObjectNode command(String workspace, String actor, ObjectNode request) {
    String action = request.path("action").asText(), commandId = request.path("commandId").asText();
    require(!commandId.isBlank() && commandId.length() <= 100, "必须提供有效commandId");
    ObjectNode s = load(workspace, true);
    int epoch = request.path("runEpoch").asInt(-1);
    if (epoch != s.path("epoch").asInt())
      throw new BusinessException(409, "STALE_EPOCH", "工作区已重置，请重新加载");
    ObjectNode p =
        request.has("payload") && request.path("payload").isObject()
            ? (ObjectNode) request.path("payload")
            : obj();
    authorize(actor, action);
    String hash = hash(actor + "\n" + action + "\n" + p);
    List<Map<String, Object>> receipts =
        jdbc.queryForList(
            "SELECT payload_hash,response FROM command_receipt WHERE workspace_id=? AND epoch=? AND"
                + " command_id=?",
            workspace,
            epoch,
            commandId);
    if (!receipts.isEmpty()) {
      String prior =
          jdbc.queryForObject(
              "SELECT payload_hash FROM command_receipt WHERE workspace_id=? AND epoch=? AND"
                  + " command_id=?",
              String.class,
              workspace,
              epoch,
              commandId);
      if (!hash.equals(prior))
        throw new BusinessException(409, "IDEMPOTENCY_CONFLICT", "相同命令ID不能提交不同内容");
      String response =
          jdbc.queryForObject(
              "SELECT response FROM command_receipt WHERE workspace_id=? AND epoch=? AND"
                  + " command_id=?",
              String.class,
              workspace,
              epoch,
              commandId);
      return parse(response);
    }
    if (request.path("expectedRevision").asLong(-1) != s.path("revision").asLong())
      throw new BusinessException(409, "REVISION_CONFLICT", "数据已更新，请刷新后重试");
    authorize(actor, action);
    ObjectNode result;
    if (action.equals("workspace.reset")) {
      s = Seed.create(workspace, s.path("name").asText(), epoch + 1);
      result = obj("reset", true, "epoch", epoch + 1);
    } else if (action.equals("settings.save")) {
      String profile = p.path("platformProfile").asText("SUCCESS");
      require(
          profile.matches("SUCCESS|FAIL_ONCE|ALWAYS_FAIL|TIMEOUT|OUT_OF_ORDER|DUPLICATE_CALLBACK"),
          "未知平台模拟配置");
      ((ObjectNode) s.get("settings")).put("platformProfile", profile);
      result = (ObjectNode) s.get("settings");
    } else if (action.matches("(asset|scene|topology|template|station|course|training|issue)\\..*"))
      result = TrainingModule.apply(s, action, p, actor);
    else if (action.matches("(execution|inspection|rectification|comparison|archive)\\..*"))
      result = ExecutionModule.apply(s, action, p, actor);
    else result = PlanningModule.apply(s, action, p, actor);
    ObjectNode event =
        obj(
            "id",
            id("AUDIT"),
            "action",
            action,
            "actor",
            actor,
            "objectId",
            result.path("id").asText(),
            "at",
            Instant.now().toString(),
            "epoch",
            s.path("epoch"),
            "sequence",
            s.withArray("events").size() + 1);
    s.withArray("events").add(event);
    save(s);
    jdbc.update(
        "INSERT INTO audit_event(id,workspace_id,epoch,actor_id,action,details)"
            + " VALUES(?,?,?,?,?,?)",
        event.path("id").asText(),
        workspace,
        s.path("epoch").asInt(),
        actor,
        action,
        obj("commandId", commandId, "payload", p, "resultId", result.path("id")).toString());
    ObjectNode response = obj("data", result, "state", s, "commandId", commandId);
    jdbc.update(
        "INSERT INTO command_receipt(workspace_id,epoch,command_id,payload_hash,response)"
            + " VALUES(?,?,?,?,?)",
        workspace,
        epoch,
        commandId,
        hash,
        response.toString());
    return response;
  }

  public static void authorize(String actor, String action) {
    String role = role(actor);
    if (role.equals("ADMIN") && !action.equals("archive.review")) return;
    Set<String> allowed;
    if (action.startsWith("workspace.") || action.startsWith("settings."))
      allowed = Set.of("ADMIN");
    else if (action.equals("course.approve")
        || action.equals("course.return")
        || action.equals("asset.approve")
        || action.equals("training.assign")
        || action.equals("training.confirm")) allowed = Set.of("INSTRUCTOR");
    else if (action.startsWith("training.")) allowed = Set.of("LEARNER", "INSTRUCTOR");
    else if (action.equals("plan.approve") || action.equals("subplan.approve"))
      allowed = Set.of("REVIEWER_L1");
    else if (action.startsWith("archive.review"))
      allowed = Set.of("REVIEWER_L1", "REVIEWER_L2", "REVIEWER_L3");
    else if (action.startsWith("inspection.")) allowed = Set.of("INSPECTOR");
    else if (action.startsWith("rectification.")
        || action.matches("execution\\.(ack|prepare|report|finish)")) allowed = Set.of("WORKER");
    else if (action.startsWith("run.")) allowed = Set.of("DIRECTOR");
    else if (action.startsWith("issue.")) allowed = Set.of("INSTRUCTOR", "PLANNER");
    else if (action.matches("(asset|scene|topology|template|station|course)\\..*"))
      allowed = Set.of("AUTHOR", "INSTRUCTOR");
    else allowed = Set.of("PLANNER");
    if (!allowed.contains(role))
      throw new BusinessException(
          403, "ROLE_FORBIDDEN", "当前身份无权执行，需要：" + String.join(" / ", allowed));
  }

  public static String role(String actor) {
    for (JsonNode a : Seed.accounts())
      if (a.path("id").asText().equals(actor)) return a.path("role").asText();
    throw new BusinessException(401, "INVALID_SESSION", "演示身份无效");
  }

  private static String hash(String text) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public String report(String workspace, String collection, String id) {
    require(
        Set.of("attempts", "runs", "executions", "comparisons", "evaluations", "archives", "plans")
            .contains(collection),
        "不支持的报告类型");
    ObjectNode s = state(workspace), record = find(s, collection, id);
    return "# 保障业务共性平台 · 业务记录报告\n\n"
        + "- 报告对象："
        + id
        + "\n- 工作区："
        + s.path("name").asText()
        + "（"
        + workspace
        + "）\n- 运行轮次："
        + s.path("epoch").asInt()
        + "\n- 种子版本：demo-1.0\n- 生成时间："
        + Instant.now()
        + "\n- 数据性质：合成数据 / 平台能力模拟 / 非实际工程结果\n\n## 保存的业务记录\n\n```json\n"
        + record.toPrettyString()
        + "\n"
        + "```\n\n"
        + "## 口径\n\n"
        + "工期为仿真分钟，费用为示例费用单位。预测、演练及模拟实绩分别保存。团队结果不代表个人培训通过；检查合格率按不同检查项计算，复查不会增加应检项分母。\n";
  }
}

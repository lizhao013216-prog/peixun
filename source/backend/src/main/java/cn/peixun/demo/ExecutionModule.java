package cn.peixun.demo;

import static cn.peixun.demo.BusinessException.require;
import static cn.peixun.demo.Json.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.time.Instant;
import java.util.*;

public final class ExecutionModule {
  public static ObjectNode apply(ObjectNode s, String action, ObjectNode p, String actor) {
    String target = p.path("id").asText();
    switch (action) {
      case "execution.create" -> {
        ObjectNode plan = find(s, "plans", p.path("planId").asText());
        require(plan.path("status").asText().matches("APPROVED|PUBLISHED"), "请先批准施工预案");
        ObjectNode e =
            obj(
                "id",
                id("EXEC"),
                "planId",
                plan.get("id"),
                "planVersion",
                plan.get("version"),
                "name",
                plan.path("name").asText() + " · 模拟施工",
                "status",
                "ISSUED",
                "clock",
                0,
                "logs",
                arr(),
                "inspections",
                arr(),
                "rectifications",
                arr(),
                "ledger",
                arr(),
                "createdBy",
                actor,
                "sourceType",
                "SIMULATED_ACTUAL",
                "createdAt",
                Instant.now().toString());
        s.withArray("executions").add(e);
        return e;
      }
      case "execution.ack" -> {
        ObjectNode e = find(s, "executions", target);
        require(e.path("status").asText().equals("ISSUED"), "执行单不是待签收状态");
        e.put("status", "ACKNOWLEDGED").put("signedBy", actor);
        return e;
      }
      case "execution.prepare" -> {
        ObjectNode e = find(s, "executions", target);
        require(e.path("status").asText().equals("ACKNOWLEDGED"), "请先签收执行单");
        ObjectNode plan = find(s, "plans", e.path("planId").asText());
        require(plan.path("trainingConfirmed").asBoolean(), "人员M1缺少本次培训证据，请由教员确认后再开工");
        require(
            find(s, "resources", "M1").path("available").asBoolean()
                && find(s, "resources", "M2").path("available").asBoolean(),
            "维修人员当前不可用");
        e.put("status", "READY");
        return e;
      }
      case "execution.report" -> {
        ObjectNode e = find(s, "executions", target);
        require(e.path("status").asText().equals("READY"), "请先签收并完成开工准备");
        ObjectNode plan = find(s, "plans", e.path("planId").asText());
        require(plan.path("eta").asDouble() == 135, "固定实绩样例需要到货时间135分钟，请选择金标P1预案");
        addLog(e, "T01", "任务检查", 0, 30, arr("E1"));
        addLog(e, "T02", "备件发运准备", 0, 30, arr("W1"));
        addLog(e, "T03", "模拟隔离确认", 30, 65, arr("E1"));
        addLog(e, "T04", "模拟拆解", 65, 130, arr("M1", "M2"));
        addLog(e, "T05", "更换示例部件", 135, 170, arr("M1", "M2"));
        addLog(e, "T06", "模拟装配", 170, 220, arr("M1", "M2"));
        addLog(e, "T07-FIRST", "检测 · 首次", 220, 240, arr("E1", "Q1"));
        for (ObjectNode l :
            List.of(
                obj("action", "DISPATCH", "minute", 30),
                obj("action", "RECEIVE", "minute", 135),
                obj("action", "ISSUE", "minute", 135))) {
          l.put("id", id("LEDGER"))
              .put("quantity", 2)
              .put("documentId", e.path("id").asText() + "-TRANS");
          e.withArray("ledger").add(l);
        }
        e.put("clock", 240).put("status", "PENDING_INSPECTION");
        e.set("inventory", obj("remote", 2, "local", 0, "inTransit", 0, "consumed", 2));
        summarize(e, plan);
        return e;
      }
      case "inspection.submit" -> {
        ObjectNode e = find(s, "executions", target);
        require(e.path("status").asText().equals("PENDING_INSPECTION"), "当前不在首次检查阶段");
        int item = p.path("item").asInt();
        require(item >= 1 && item <= 4, "检查项编号应为1～4");
        for (JsonNode i : e.withArray("inspections"))
          require(i.path("item").asInt() != item, "该检查项已经提交，不能覆盖历史");
        boolean pass = p.path("passed").asBoolean();
        e.withArray("inspections")
            .add(
                obj(
                    "id",
                    id("INSPECT"),
                    "item",
                    item,
                    "name",
                    checkName(item),
                    "round",
                    1,
                    "passed",
                    pass,
                    "comment",
                    p.path("comment").asText(pass ? "符合示例要求" : "需要调整后复查"),
                    "actor",
                    actor,
                    "minute",
                    e.get("clock")));
        if (!pass)
          e.withArray("rectifications")
              .add(
                  obj(
                      "id",
                      id("FIX"),
                      "item",
                      item,
                      "status",
                      "OPEN",
                      "description",
                      "调整" + checkName(item) + "并申请复查"));
        if (e.withArray("inspections").size() == 4)
          e.put(
              "status",
              e.withArray("rectifications").isEmpty()
                  ? "READY_HANDOVER"
                  : "RECTIFICATION_REQUIRED");
        summarize(e, find(s, "plans", e.path("planId").asText()));
        return e;
      }
      case "rectification.submit" -> {
        ObjectNode e = find(s, "executions", target);
        require(e.path("status").asText().equals("RECTIFICATION_REQUIRED"), "当前没有待整改任务");
        require(!p.path("comment").asText().isBlank(), "请填写整改说明");
        double start = e.path("clock").asDouble();
        addLog(
            e,
            "T07-FIX-" + e.withArray("logs").size(),
            "检测 · 整改",
            start,
            start + 10,
            arr("M1", "M2"));
        for (JsonNode f : e.withArray("rectifications"))
          if (!f.path("status").asText().equals("CLOSED"))
            ((ObjectNode) f)
                .put("status", "FIX_SUBMITTED")
                .put("comment", p.path("comment").asText());
        e.put("clock", start + 10).put("status", "PENDING_RECHECK");
        summarize(e, find(s, "plans", e.path("planId").asText()));
        return e;
      }
      case "inspection.recheck" -> {
        ObjectNode e = find(s, "executions", target);
        require(e.path("status").asText().equals("PENDING_RECHECK"), "请先提交整改");
        int item = p.path("item").asInt();
        ObjectNode fix = null;
        for (JsonNode n : e.withArray("rectifications"))
          if (n.path("item").asInt() == item) fix = (ObjectNode) n;
        require(
            fix != null && fix.path("status").asText().equals("FIX_SUBMITTED"), "该检查项无需复查或已经处理");
        boolean pass = p.path("passed").asBoolean(true);
        double start = e.path("clock").asDouble();
        addLog(
            e,
            "T07-RECHECK-" + e.withArray("logs").size(),
            "检测 · 复查",
            start,
            start + 5,
            arr("E1", "Q1"));
        int round = 1;
        for (JsonNode n : e.withArray("inspections")) if (n.path("item").asInt() == item) round++;
        e.withArray("inspections")
            .add(
                obj(
                    "id",
                    id("INSPECT"),
                    "item",
                    item,
                    "name",
                    checkName(item),
                    "round",
                    round,
                    "passed",
                    pass,
                    "actor",
                    actor,
                    "minute",
                    start + 5));
        fix.put("status", pass ? "CLOSED" : "OPEN");
        e.put("clock", start + 5);
        boolean pending = false, open = false;
        for (JsonNode f : e.withArray("rectifications")) {
          pending |= f.path("status").asText().equals("FIX_SUBMITTED");
          open |= f.path("status").asText().equals("OPEN");
        }
        e.put(
            "status",
            pending ? "PENDING_RECHECK" : open ? "RECTIFICATION_REQUIRED" : "READY_HANDOVER");
        summarize(e, find(s, "plans", e.path("planId").asText()));
        return e;
      }
      case "execution.finish" -> {
        ObjectNode e = find(s, "executions", target);
        require(e.path("status").asText().equals("READY_HANDOVER"), "全部检查及整改闭环后才能交接");
        double start = e.path("clock").asDouble();
        addLog(e, "T08", "交接归档", start, start + 15, arr("L1"));
        e.put("clock", start + 15).put("status", "ACCEPTED");
        summarize(e, find(s, "plans", e.path("planId").asText()));
        return e;
      }
      case "comparison.create" -> {
        ObjectNode e = find(s, "executions", p.path("executionId").asText());
        require(e.path("status").asText().equals("ACCEPTED"), "施工尚未完成验收");
        ObjectNode plan = find(s, "plans", p.path("planId").asText(e.path("planId").asText()));
        double expectedTime = plan.path("schedule").path("finish").asDouble(),
            expectedCost = plan.path("schedule").path("cost").asDouble(),
            actualTime = e.path("clock").asDouble(),
            actualCost = e.path("summary").path("cost").asDouble();
        require(expectedTime > 0 && expectedCost > 0, "对比基线缺少工期或费用");
        ObjectNode c =
            obj(
                "id",
                id("COMPARE"),
                "executionId",
                e.get("id"),
                "planId",
                plan.get("id"),
                "planVersion",
                plan.get("version"),
                "expectedTime",
                expectedTime,
                "actualTime",
                actualTime,
                "timeDelta",
                actualTime - expectedTime,
                "timeDeltaRate",
                round((actualTime - expectedTime) / expectedTime * 100),
                "expectedCost",
                expectedCost,
                "actualCost",
                actualCost,
                "costDelta",
                actualCost - expectedCost,
                "costDeltaRate",
                round((actualCost - expectedCost) / expectedCost * 100),
                "cause",
                p.path("cause").asText("模拟拆解和装配时长增加；检测出现一次整改"),
                "status",
                "CONFIRMED");
        s.withArray("comparisons").add(c);
        return c;
      }
      case "archive.create" -> {
        ObjectNode plan = find(s, "plans", p.path("planId").asText());
        require(plan.path("status").asText().matches("APPROVED|PUBLISHED"), "归档需要已批准预案");
        Set<String> family = new HashSet<>();
        ObjectNode ancestor = plan;
        while (ancestor != null && family.add(ancestor.path("id").asText())) {
          ancestor =
              ancestor.hasNonNull("parentId")
                  ? find(s, "plans", ancestor.path("parentId").asText())
                  : null;
        }
        Set<String> executionIds = new HashSet<>();
        ArrayNode runs = arr(), executions = arr(), comparisons = arr();
        for (JsonNode r : s.withArray("runs"))
          if (r.path("planId").asText().equals(plan.path("id").asText())
              && r.path("status").asText().equals("COMPLETED")) runs.add(r.get("id"));
        for (JsonNode e : s.withArray("executions"))
          if (e.path("status").asText().equals("ACCEPTED")
              && family.contains(e.path("planId").asText())) {
            executions.add(e.get("id"));
            executionIds.add(e.path("id").asText());
          }
        s.withArray("comparisons")
            .forEach(
                c -> {
                  if (executionIds.contains(c.path("executionId").asText()))
                    comparisons.add(c.get("id"));
                });
        ObjectNode a =
            obj(
                "id",
                id("ARCHIVE"),
                "name",
                plan.path("name").asText() + " · " + plan.path("version").asText() + "成果包",
                "planId",
                plan.get("id"),
                "planVersion",
                plan.get("version"),
                "status",
                "DRAFT",
                "round",
                0,
                "level",
                0,
                "reviews",
                arr(),
                "runIds",
                runs,
                "executionIds",
                executions,
                "comparisonIds",
                comparisons,
                "createdBy",
                actor);
        s.withArray("archives").add(a);
        return a;
      }
      case "archive.supplement" -> {
        ObjectNode a = find(s, "archives", target);
        require(a.path("status").asText().equals("RETURNED"), "仅退回的成果包可补充说明");
        require(!p.path("comment").asText().isBlank(), "请填写补充说明");
        a.withArray("supplements")
            .add(
                obj(
                    "round",
                    a.get("round"),
                    "comment",
                    p.get("comment"),
                    "actor",
                    actor,
                    "at",
                    Instant.now().toString()));
        a.put("supplementedRound", a.path("round").asInt());
        return a;
      }
      case "archive.submit" -> {
        ObjectNode a = find(s, "archives", target);
        require(a.path("status").asText().matches("DRAFT|RETURNED"), "归档当前不可提交");
        if (a.path("status").asText().equals("RETURNED"))
          require(
              a.path("supplementedRound").asInt(-1) == a.path("round").asInt(), "请先补充本轮审核意见的处理说明");
        require(
            a.path("runIds").size() > 0
                && a.path("executionIds").size() > 0
                && a.path("comparisonIds").size() > 0,
            "缺少本版完成演练、已验收施工或对比报告，请补齐后重新建立成果包");
        a.put("round", a.path("round").asInt() + 1).put("level", 0).put("status", "SUBMITTED");
        return a;
      }
      case "archive.review" -> {
        ObjectNode a = find(s, "archives", target);
        require(
            a.path("status").asText().matches("SUBMITTED|L1_APPROVED|L2_APPROVED"), "归档不在审核流程中");
        int level = a.path("level").asInt() + 1;
        require(actor.equals("REVIEWER_L" + level), "当前需第" + level + "级审核员处理，不能跳级");
        boolean approved = p.path("approved").asBoolean(true);
        String comment = p.path("comment").asText(approved ? "资料完整，同意通过" : "请补充改进依据");
        require(!comment.isBlank(), "请填写审核意见");
        a.withArray("reviews")
            .add(
                obj(
                    "round",
                    a.get("round"),
                    "level",
                    level,
                    "actor",
                    actor,
                    "approved",
                    approved,
                    "comment",
                    comment,
                    "at",
                    Instant.now().toString()));
        if (approved) a.put("level", level).put("status", "L" + level + "_APPROVED");
        else a.put("status", "RETURNED");
        return a;
      }
      case "archive.publish" -> {
        ObjectNode a = find(s, "archives", target);
        require(a.path("status").asText().equals("L3_APPROVED"), "三级审核全部通过后才能发布");
        a.put("status", "PUBLISHED");
        ObjectNode plan = find(s, "plans", a.path("planId").asText());
        plan.put("status", "PUBLISHED");
        s.withArray("templates")
            .add(
                obj(
                    "id",
                    id("TPL"),
                    "name",
                    plan.path("name").asText() + " · " + plan.path("version").asText(),
                    "type",
                    "PLAN",
                    "equipmentType",
                    "PUMP",
                    "taskType",
                    "维修",
                    "status",
                    "PUBLISHED",
                    "planId",
                    plan.get("id"),
                    "description",
                    "来自已审核归档的优化预案"));
        return a;
      }
      default -> throw new BusinessException(400, "UNKNOWN_COMMAND", "未知执行命令：" + action);
    }
  }

  private static void addLog(
      ObjectNode e, String operation, String name, double start, double end, ArrayNode resources) {
    e.withArray("logs")
        .add(
            obj(
                "id",
                id("LOG"),
                "operationId",
                operation,
                "name",
                name,
                "start",
                start,
                "end",
                end,
                "resources",
                resources,
                "sourceType",
                "SIMULATED_ACTUAL"));
  }

  private static String checkName(int i) {
    return new String[] {"结构完整性", "部件对应性", "装配状态", "检测记录"}[i - 1];
  }

  static void summarize(ObjectNode e, ObjectNode plan) {
    Map<String, Double> rates = new HashMap<>();
    for (JsonNode r : plan.path("resourceSnapshot"))
      if (r.path("category").asText().equals("人员"))
        rates.put(r.path("id").asText(), r.path("rate").asDouble());
    double labor = 0, minutes = 0;
    for (JsonNode log : e.withArray("logs")) {
      double duration = log.path("end").asDouble() - log.path("start").asDouble();
      for (JsonNode r : log.path("resources")) {
        labor += duration * rates.getOrDefault(r.asText(), 0.0);
        minutes += duration;
      }
    }
    Map<Integer, Boolean> latest = new HashMap<>();
    int firstPass = 0;
    for (JsonNode i : e.withArray("inspections")) {
      latest.put(i.path("item").asInt(), i.path("passed").asBoolean());
      if (i.path("round").asInt() == 1 && i.path("passed").asBoolean()) firstPass++;
    }
    int passed = (int) latest.values().stream().filter(Boolean::booleanValue).count();
    e.set(
        "summary",
        obj(
            "labor",
            labor,
            "personMinutes",
            minutes,
            "materialCost",
            200,
            "transportCost",
            plan.path("transportCost"),
            "cost",
            labor + 200 + plan.path("transportCost").asDouble(),
            "firstPassRate",
            firstPass * 25,
            "finalPassRate",
            passed * 25,
            "inspectionRecords",
            e.withArray("inspections").size(),
            "inspectedItems",
            latest.size(),
            "finish",
            e.path("clock")));
  }
}

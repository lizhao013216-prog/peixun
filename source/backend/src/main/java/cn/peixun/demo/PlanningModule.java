package cn.peixun.demo;

import static cn.peixun.demo.BusinessException.require;
import static cn.peixun.demo.Json.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.time.Instant;
import java.util.*;

public final class PlanningModule {
  public static ObjectNode apply(ObjectNode s, String action, ObjectNode p, String actor) {
    String target = p.path("id").asText();
    switch (action) {
      case "task.create" -> {
        String name = p.path("name").asText().trim();
        require(!name.isEmpty(), "请填写任务名称");
        if (!p.path("sourceIssueId").asText().isBlank()) {
          ObjectNode issue = find(s, "issues", p.path("sourceIssueId").asText());
          require(issue.path("type").asText().equals("EQUIPMENT_SUPPORT"), "只有核实后的疑似保障问题可以转保障任务");
          require(issue.path("verified").asBoolean(), "疑似保障问题必须先完成核实并通过统一问题处理入口转换");
          if (!issue.path("linkedTaskId").asText().isBlank())
            return find(s, "tasks", issue.path("linkedTaskId").asText());
          throw new BusinessException(409, "ISSUE_ROUTE_REQUIRED", "请从问题处理页使用统一的转保障任务操作");
        }
        ObjectNode t =
            obj(
                "id",
                id("TASK"),
                "name",
                name,
                "equipmentId",
                p.path("equipmentId").asText("EQ-DEMO-01"),
                "equipmentType",
                p.path("equipmentType").asText("PUMP"),
                "taskType",
                p.path("taskType").asText("维修"),
                "deadline",
                p.path("deadline").asDouble(270),
                "importance",
                p.path("importance").asInt(80),
                "description",
                p.path("description").asText("泵组状态检查与密封件更换"),
                "sourceIssueId",
                p.path("sourceIssueId").asText(),
                "status",
                "DRAFT",
                "createdBy",
                actor,
                "createdAt",
                Instant.now().toString(),
                "wbs",
                arr(obj("id", "WBS-1", "name", "泵组维修保障", "children", Seed.operations(false))));
        require(t.path("deadline").asDouble() > 0, "截止时间必须大于0");
        s.withArray("tasks").add(t);
        return t;
      }
      case "task.save" -> {
        ObjectNode t = find(s, "tasks", target);
        for (String k : new String[] {"name", "description", "importance", "deadline", "taskType"})
          if (p.has(k)) t.set(k, p.get(k));
        require(t.path("deadline").asDouble() > 0, "截止时间必须大于0");
        return t;
      }
      case "history.import" -> {
        ArrayNode input = p.has("rows") ? (ArrayNode) p.path("rows") : arr();
        if (input.isEmpty()) {
          for (int i = 1; i <= 8; i++)
            input.add(
                obj(
                    "recordId",
                    "H-" + i,
                    "type",
                    i % 2 == 0 ? "维修记录" : "使用状态",
                    "description",
                    "第" + i + "条合成履历"));
          input.add(obj("recordId", "H-1", "type", "维修记录"));
          input.add(obj("recordId", p.path("repair").asBoolean() ? "H-9" : "", "type", "改装记录"));
        }
        int added = 0, duplicate = 0, rejected = 0;
        ArrayNode errors = arr();
        Set<String> ids = new HashSet<>();
        s.withArray("histories").forEach(n -> ids.add(n.path("recordId").asText()));
        int row = 0;
        for (JsonNode n : input) {
          row++;
          String key = n.path("recordId").asText();
          if (key.isBlank()) {
            rejected++;
            errors.add(obj("row", row, "reason", "缺少recordId主键"));
          } else if (!ids.add(key)) duplicate++;
          else {
            ObjectNode item = (ObjectNode) n.deepCopy();
            item.put("id", id("HISTORY"))
                .put("equipmentId", "EQ-DEMO-01")
                .put("sourceType", "SIMULATED_PLSE");
            s.withArray("histories").add(item);
            added++;
          }
        }
        ObjectNode result =
            obj("added", added, "duplicate", duplicate, "rejected", rejected, "errors", errors);
        s.set("lastImport", result);
        return result;
      }
      case "document.save" -> {
        require(!p.path("name").asText().isBlank(), "请填写资料名称");
        ObjectNode d =
            obj(
                "id",
                id("DOC"),
                "name",
                p.get("name"),
                "type",
                p.path("type").asText("技术资料"),
                "content",
                p.path("content").asText(),
                "version",
                1,
                "sourceType",
                "USER_INPUT");
        s.withArray("documents").add(d);
        return d;
      }
      case "analysis.create" -> {
        require(!p.path("conclusion").asText().isBlank(), "请填写结构化分析结论");
        ObjectNode a =
            obj(
                "id",
                id("ANALYSIS"),
                "name",
                p.path("name").asText("泵组故障标签分析"),
                "type",
                p.path("type").asText("FMECA"),
                "equipmentId",
                "EQ-DEMO-01",
                "tag",
                p.path("tag").asText("密封件状态异常"),
                "conclusion",
                p.get("conclusion"),
                "requirement",
                "检查泵组并配置2件示例备件与维修人员",
                "sourceType",
                "EXPERT_INPUT",
                "createdBy",
                actor,
                "status",
                "CONFIRMED");
        s.withArray("analyses").add(a);
        return a;
      }
      case "resource.save" -> {
        ObjectNode r =
            target.isEmpty()
                ? obj("id", id("RESOURCE"), "start", 0, "end", 360)
                : find(s, "resources", target);
        for (String k :
            new String[] {
              "name",
              "category",
              "skill",
              "capacity",
              "available",
              "rate",
              "organization",
              "start",
              "end",
              "capability"
            }) if (p.has(k)) r.set(k, p.get(k));
        require(!r.path("name").asText().isBlank(), "资源名称不能为空");
        require(r.path("capacity").asDouble() >= 0 && r.path("rate").asDouble() >= 0, "数量或费率不能为负数");
        require(r.path("end").asDouble() > r.path("start").asDouble(), "可用结束时间必须晚于开始时间");
        if (target.isEmpty()) s.withArray("resources").add(r);
        return r;
      }
      case "lifecycle.calculate" -> {
        int cycle = p.path("cycle").asInt(6), extra = p.path("highIntensityMonth").asInt(0);
        require(cycle >= 1 && cycle <= 24, "周期应为1～24月");
        Set<Integer> months = new TreeSet<>();
        for (int m = cycle; m <= 24; m += cycle) months.add(m);
        if (extra >= 1 && extra <= 24) months.add(extra);
        ArrayNode windows = arr();
        for (int m : months)
          windows.add(
              obj(
                  "month",
                  m,
                  "startDay",
                  m * 30 - 2,
                  "endDay",
                  m * 30,
                  "reason",
                  m == extra ? "使用强度触发" : "周期维修"));
        ObjectNode result =
            obj(
                "cycle",
                cycle,
                "windows",
                windows,
                "downtime",
                months.size() * 2,
                "availableDays",
                720 - months.size() * 2,
                "availability",
                round((720 - months.size() * 2) * 100.0 / 720));
        s.set("lifecycle", result);
        return result;
      }
      case "plan.create" -> {
        ObjectNode task = find(s, "tasks", p.path("taskId").asText());
        String template = p.path("templateId").asText();
        if (!template.isBlank()) {
          ObjectNode t = find(s, "templates", template);
          require(
              t.path("equipmentType").asText().equals(task.path("equipmentType").asText())
                  && t.path("taskType").asText().equals(task.path("taskType").asText()),
              "模板不满足装备/任务类型，请新建预案");
        }
        ObjectNode plan =
            obj(
                "id",
                id("PLAN"),
                "name",
                p.path("name").asText(task.path("name").asText() + " · 保障预案"),
                "taskId",
                task.get("id"),
                "version",
                "P0",
                "status",
                "DRAFT",
                "eta",
                90,
                "transportCost",
                60,
                "deadline",
                task.get("deadline"),
                "operations",
                Seed.operations(false),
                "resourceSnapshot",
                s.get("resources").deepCopy(),
                "createdBy",
                actor,
                "trainingConfirmed",
                false,
                "sourceTemplateId",
                template,
                "notes",
                "");
        if (!template.isBlank()) {
          ObjectNode t = find(s, "templates", template);
          if (t.hasNonNull("planId")) {
            ObjectNode source = find(s, "plans", t.path("planId").asText());
            plan.set("operations", source.path("operations").deepCopy());
            plan.set("eta", source.get("eta"));
            plan.set("transportCost", source.get("transportCost"));
            plan.put("reusedFromPlanId", source.path("id").asText());
          }
        }
        calculate(plan);
        s.withArray("plans").add(plan);
        task.put("status", "PLANNED");
        return plan;
      }
      case "plan.save" -> {
        ObjectNode plan = find(s, "plans", target);
        require(plan.path("status").asText().equals("DRAFT"), "已审批预案不可修改，请创建新版本");
        for (String k : new String[] {"name", "eta", "transportCost", "operations", "notes"})
          if (p.has(k)) plan.set(k, p.get(k));
        if (p.path("rebindResources").asBoolean())
          plan.set("resourceSnapshot", s.get("resources").deepCopy());
        calculate(plan);
        return plan;
      }
      case "plan.submit" -> {
        ObjectNode plan = find(s, "plans", target);
        require(plan.path("status").asText().equals("DRAFT"), "仅草稿可提交");
        calculate(plan);
        plan.put("status", "PENDING_REVIEW");
        return plan;
      }
      case "plan.approve" -> {
        ObjectNode plan = find(s, "plans", target);
        require(plan.path("status").asText().equals("PENDING_REVIEW"), "预案不在待审状态");
        require(!actor.equals(plan.path("createdBy").asText()), "不能审核自己编制的预案");
        plan.put("status", "APPROVED").put("approvedBy", actor);
        return plan;
      }
      case "plan.revise" -> {
        ObjectNode old = find(s, "plans", target);
        require(old.path("status").asText().matches("APPROVED|PUBLISHED"), "请从已批准版本复制");
        ObjectNode plan = old.deepCopy();
        String version =
            p.path("version").asText(old.path("version").asText().equals("P0") ? "P1" : "P2");
        plan.put("id", id("PLAN"))
            .put("parentId", target)
            .put("version", version)
            .put("status", "DRAFT")
            .put("createdBy", actor);
        plan.put("eta", version.equals("P2") ? 120 : 135).put("transportCost", 100);
        if (version.equals("P2")) plan.set("operations", Seed.operations(true));
        calculate(plan);
        s.withArray("plans").add(plan);
        return plan;
      }
      case "preparation.request" -> {
        ObjectNode plan = find(s, "plans", target);
        for (JsonNode r : s.withArray("requests"))
          if (r.path("planId").asText().equals(target)) return (ObjectNode) r;
        ObjectNode request =
            obj(
                "id",
                id("TRAIN-REQ"),
                "planId",
                target,
                "name",
                "泵组维修技能培训需求",
                "equipmentId",
                "EQ-DEMO-01",
                "learnerId",
                "LEARNER_A",
                "status",
                "PENDING",
                "description",
                "完成十步骤培训并由教员确认本次培训证据");
        s.withArray("requests").add(request);
        return request;
      }
      case "order.create" -> {
        ObjectNode plan = find(s, "plans", p.path("planId").asText());
        String type = p.path("type").asText("TRANSFER");
        require(type.matches("TRANSFER|PURCHASE"), "单据类型不正确");
        int quantity = p.path("quantity").asInt(2);
        require(quantity > 0, "数量必须大于0");
        ObjectNode order =
            obj(
                "id",
                id(type.equals("TRANSFER") ? "TRANS" : "PURCHASE"),
                "planId",
                plan.get("id"),
                "type",
                type,
                "quantity",
                quantity,
                "material",
                "MAT-SEAL-01",
                "source",
                type.equals("TRANSFER") ? "WH-REMOTE" : "模拟供应商",
                "target",
                "WH-LOCAL",
                "status",
                "DRAFT",
                "eta",
                plan.get("eta"),
                "createdBy",
                actor,
                "ledger",
                arr(),
                "partition",
                id("ORDER-LEDGER"));
        s.withArray("orders").add(order);
        return order;
      }
      case "order.advance" -> {
        ObjectNode o = find(s, "orders", target);
        String status = o.path("status").asText();
        String next =
            switch (status) {
              case "DRAFT" -> "APPROVED";
              case "APPROVED" -> "DISPATCHED";
              case "DISPATCHED" -> "RECEIVED";
              case "RECEIVED" -> "CLOSED";
              default -> throw new BusinessException(409, "INVALID_TRANSITION", "单据已经关闭");
            };
        if (next.equals("APPROVED") && o.path("type").asText().equals("TRANSFER"))
          require(
              find(s, "resources", "WH-REMOTE").path("capacity").asInt()
                  >= o.path("quantity").asInt(),
              "远端库存不足，请生成采购计划");
        o.put("status", next);
        o.withArray("ledger")
            .add(
                obj(
                    "id",
                    id("LEDGER"),
                    "action",
                    next,
                    "quantity",
                    o.get("quantity"),
                    "at",
                    Instant.now().toString()));
        return o;
      }
      case "subplan.create" -> {
        ObjectNode plan = find(s, "plans", p.path("planId").asText());
        String type = p.path("type").asText("施工总计划");
        require(List.of("施工总计划", "采购计划", "进出库计划", "修理调试计划", "单装试验大纲").contains(type), "专项计划类型不正确");
        ObjectNode sub =
            obj(
                "id",
                id("SUBPLAN"),
                "planId",
                plan.get("id"),
                "type",
                type,
                "name",
                type + " · " + plan.path("version").asText(),
                "content",
                p.path("content").asText(""),
                "owner",
                p.path("owner").asText("本地维修单位"),
                "status",
                "DRAFT",
                "createdBy",
                actor);
        s.withArray("subplans").add(sub);
        return sub;
      }
      case "subplan.submit", "subplan.approve" -> {
        ObjectNode sub = find(s, "subplans", target);
        if (action.endsWith("submit")) {
          require(sub.path("status").asText().equals("DRAFT"), "仅草稿可提交");
          require(!sub.path("content").asText().isBlank(), "请填写专项计划内容");
          sub.put("status", "PENDING_REVIEW");
        } else {
          require(sub.path("status").asText().equals("PENDING_REVIEW"), "请先提交审核");
          require(!actor.equals(sub.path("createdBy").asText()), "不能审核自己的专项计划");
          sub.put("status", "APPROVED");
        }
        return sub;
      }
      case "special.evaluate" -> {
        String type = p.path("type").asText("PATH");
        ArrayNode candidates = arr();
        if (type.equals("PATH")) {
          candidates.add(obj("name", "路径A · 左侧通道", "feasible", false, "reason", "示例尺寸条件不满足"));
          candidates.add(obj("name", "路径B · 上层通道", "feasible", false, "reason", "示例承载条件不满足"));
          candidates.add(obj("name", "路径C · 维修通道", "feasible", true, "reason", "平台模拟校验通过"));
        } else {
          double repair = p.path("repairCost").asDouble(800),
              replace = p.path("replaceCost").asDouble(950);
          require(repair >= 0 && replace >= 0, "成本不能为负数");
          candidates.add(obj("name", "修复方案", "cost", repair, "duration", 180, "feasible", true));
          candidates.add(
              obj(
                  "name",
                  "更换方案",
                  "cost",
                  replace,
                  "duration",
                  120,
                  "feasible",
                  p.path("compatible").asBoolean(true)));
        }
        ObjectNode result =
            obj(
                "id",
                id("SPECIAL"),
                "type",
                type,
                "candidates",
                candidates,
                "sourceType",
                "SIMULATED_PLATFORM",
                "planId",
                p.path("planId").asText(),
                "status",
                "EVALUATED");
        s.set("special", result);
        return result;
      }
      case "special.adopt" -> {
        require(s.has("special"), "请先评估候选方案");
        ObjectNode result = (ObjectNode) s.get("special");
        int index = p.path("index").asInt();
        require(index >= 0 && index < result.path("candidates").size(), "候选不存在");
        require(result.path("candidates").get(index).path("feasible").asBoolean(), "不能采纳不可行候选");
        result.put("selectedIndex", index).put("status", "ADOPTED");
        return result;
      }
      case "run.create", "run.branch" -> {
        ObjectNode plan = find(s, "plans", p.path("planId").asText());
        require(plan.path("status").asText().matches("APPROVED|PUBLISHED"), "请先批准预案");
        ObjectNode run =
            obj(
                "id",
                id("RUN"),
                "name",
                p.path("name").asText("保障行动演练"),
                "planId",
                plan.get("id"),
                "planVersion",
                plan.get("version"),
                "status",
                "READY",
                "clock",
                0,
                "eta",
                plan.get("eta"),
                "transportCost",
                plan.get("transportCost"),
                "revision",
                1,
                "events",
                arr(),
                "ledger",
                arr(),
                "processed",
                arr(),
                "operations",
                plan.get("operations").deepCopy(),
                "resourceSnapshot",
                plan.get("resourceSnapshot").deepCopy(),
                "deadline",
                plan.get("deadline"),
                "sourceType",
                "SIMULATED_EXERCISE",
                "scenario",
                p.path("scenario").asText("BASE"));
        if (action.equals("run.branch")) {
          ObjectNode parent = find(s, "runs", p.path("parentId").asText());
          require(parent.path("clock").asDouble() >= 60, "原演练尚未到达T+60分支点");
          require(parent.path("planId").asText().equals(plan.path("id").asText()), "分支预案与原演练不一致");
          run.put("parentId", parent.path("id").asText())
              .put("clock", 60)
              .put("eta", 135)
              .put("transportCost", 100)
              .put("scenario", "OPT");
        }
        reschedule(run);
        if (run.path("clock").asDouble() > 0) progress(run, run.path("clock").asDouble());
        s.withArray("runs").add(run);
        return run;
      }
      case "run.start", "run.pause", "run.step", "run.finish", "run.inject" -> {
        ObjectNode run = find(s, "runs", target);
        require(!run.path("status").asText().matches("COMPLETED|ABORTED"), "演练已结束，请新建或创建分支");
        if (action.equals("run.pause")) {
          run.put("status", run.path("status").asText().equals("PAUSED") ? "RUNNING" : "PAUSED");
          return run;
        }
        if (action.equals("run.start")) {
          run.put("status", "RUNNING");
          progress(run, run.path("clock").asDouble());
          return run;
        }
        if (action.equals("run.inject")) {
          require(run.path("clock").asDouble() < run.path("eta").asDouble(), "物料已经到货，不能修改历史到货时间");
          double eta = p.path("eta").asDouble(180);
          require(eta > run.path("clock").asDouble(), "新的到货时间必须晚于当前仿真时间");
          run.withArray("events")
              .add(
                  obj(
                      "id",
                      id("INJECT"),
                      "minute",
                      run.get("clock"),
                      "type",
                      "INTERVENTION",
                      "name",
                      "到货调整：" + run.path("eta").asInt() + " → " + eta,
                      "sourceType",
                      "DIRECTOR_INPUT"));
          run.put("eta", eta)
              .put(
                  "transportCost",
                  p.path("transportCost").asDouble(run.path("transportCost").asDouble()))
              .put("revision", run.path("revision").asInt() + 1);
          reschedule(run);
          return run;
        }
        int guard = 0;
        do {
          double now = run.path("clock").asDouble(),
              next = run.path("schedule").path("finish").asDouble();
          for (JsonNode span : run.path("schedule").path("spans")) {
            if (span.path("start").asDouble() > now)
              next = Math.min(next, span.path("start").asDouble());
            if (span.path("end").asDouble() > now)
              next = Math.min(next, span.path("end").asDouble());
          }
          if (run.path("eta").asDouble() > now) next = Math.min(next, run.path("eta").asDouble());
          if (run.path("scenario").asText().equals("DELAY")
              && !run.path("delayApplied").asBoolean()
              && now < 60) next = Math.min(next, 60);
          if (next == 60
              && run.path("scenario").asText().equals("DELAY")
              && !run.path("delayApplied").asBoolean()) {
            run.put("eta", 180)
                .put("delayApplied", true)
                .put("revision", run.path("revision").asInt() + 1);
            run.withArray("events")
                .add(
                    obj(
                        "id",
                        id("INJECT"),
                        "minute",
                        60,
                        "type",
                        "INTERVENTION",
                        "name",
                        "导调：到货延迟至T+180"));
            reschedule(run);
          }
          progress(run, next);
          if (next >= run.path("schedule").path("finish").asDouble())
            run.put("status", "COMPLETED");
          else run.put("status", "RUNNING");
          require(++guard < 500, "演练事件超出保护上限");
        } while (action.equals("run.finish") && !run.path("status").asText().equals("COMPLETED"));
        return run;
      }
      case "sensitivity.calculate" -> {
        ObjectNode plan = find(s, "plans", target);
        ObjectNode result =
            ScheduleEngine.sensitivity(plan.path("operations"), plan.path("resourceSnapshot"));
        result.put("id", id("SENSITIVITY")).put("planId", target);
        double sum = 0;
        int onTime = 0;
        for (JsonNode n : result.withArray("points")) {
          ObjectNode point = (ObjectNode) n;
          ObjectNode run =
              obj(
                  "id",
                  id("RUN"),
                  "name",
                  "到货敏感性 T+" + point.path("eta").asInt(),
                  "planId",
                  plan.get("id"),
                  "planVersion",
                  plan.get("version"),
                  "status",
                  "COMPLETED",
                  "clock",
                  0,
                  "eta",
                  point.get("eta"),
                  "transportCost",
                  100,
                  "revision",
                  1,
                  "events",
                  arr(),
                  "ledger",
                  arr(),
                  "processed",
                  arr(),
                  "operations",
                  plan.get("operations").deepCopy(),
                  "resourceSnapshot",
                  plan.get("resourceSnapshot").deepCopy(),
                  "deadline",
                  plan.get("deadline"),
                  "sourceType",
                  "SIMULATED_EXERCISE",
                  "scenario",
                  "SENSITIVITY");
          reschedule(run);
          progress(run, run.path("schedule").path("finish").asDouble());
          point.set("runId", run.get("id"));
          point.set("finish", run.path("schedule").get("finish"));
          point.set("cost", run.path("schedule").get("cost"));
          sum += point.path("finish").asDouble();
          if (run.path("schedule").path("onTime").asBoolean()) onTime++;
          s.withArray("runs").add(run);
        }
        result.put("mean", sum / 5).put("onTimeRate", onTime * 20);
        s.set("sensitivity", result);
        return result;
      }
      case "evaluation.calculate" -> {
        String algorithm = p.path("algorithm").asText("AHP");
        ObjectNode result = ScheduleEngine.evaluate(algorithm, p.path("input"));
        result.put("id", id("EVAL")).put("createdAt", Instant.now().toString());
        result.set("input", p.path("input").deepCopy());
        s.withArray("evaluations").add(result);
        return result;
      }
      default -> throw new BusinessException(400, "UNKNOWN_COMMAND", "未知筹划命令：" + action);
    }
  }

  static void calculate(ObjectNode plan) {
    ObjectNode result =
        ScheduleEngine.calculate(
            plan.path("operations"),
            plan.path("resourceSnapshot"),
            plan.path("eta").asDouble(),
            plan.path("transportCost").asDouble());
    double deadline = plan.path("deadline").asDouble(270);
    result
        .put("onTime", result.path("finish").asDouble() <= deadline)
        .put("lateMinutes", Math.max(0, result.path("finish").asDouble() - deadline));
    plan.set("schedule", result);
  }

  static void reschedule(ObjectNode run) {
    require(stock(run, "WH-REMOTE") >= 2, "本演练调拨需要远端库存至少2件，请调整资源或准备采购");
    calculate(run);
  }

  static int stock(ObjectNode run, String id) {
    for (JsonNode r : run.path("resourceSnapshot"))
      if (r.path("id").asText().equals(id)) return r.path("capacity").asInt();
    return 0;
  }

  static void progress(ObjectNode run, double clock) {
    Set<String> processed = new HashSet<>();
    run.withArray("processed").forEach(n -> processed.add(n.asText()));
    List<ObjectNode> due = new ArrayList<>();
    double departure = 30;
    for (JsonNode span : run.path("schedule").path("spans")) {
      String oid = span.path("id").asText();
      if (oid.equals("T02")) departure = span.path("end").asDouble();
      due.add(
          obj(
              "key",
              oid + "-START",
              "minute",
              span.get("start"),
              "priority",
              40,
              "type",
              "START",
              "name",
              span.path("name").asText() + " · 开始"));
      due.add(
          obj(
              "key",
              oid + "-END",
              "minute",
              span.get("end"),
              "priority",
              30,
              "type",
              "COMPLETE",
              "name",
              span.path("name").asText() + " · 完成"));
    }
    due.add(
        obj(
            "key",
            "DISPATCH",
            "minute",
            departure,
            "priority",
            20,
            "type",
            "DISPATCH",
            "name",
            "远端库发运2件备件"));
    due.add(
        obj(
            "key",
            "ARRIVAL",
            "minute",
            run.get("eta"),
            "priority",
            20,
            "type",
            "RECEIVE",
            "name",
            "备件到库 · 验收入库2件"));
    due.sort(
        Comparator.comparingDouble((ObjectNode n) -> n.path("minute").asDouble())
            .thenComparingInt(n -> n.path("priority").asInt()));
    for (ObjectNode event : due) {
      String key = event.path("key").asText();
      if (event.path("minute").asDouble() > clock || !processed.add(key)) continue;
      run.withArray("processed").add(key);
      event.put("id", id("EVT")).put("sequence", run.withArray("events").size() + 1);
      run.withArray("events").add(event);
      String type = event.path("type").asText();
      if (type.equals("DISPATCH") || type.equals("RECEIVE") || key.equals("T05-START"))
        run.withArray("ledger")
            .add(
                obj(
                    "id",
                    id("LEDGER"),
                    "documentId",
                    run.path("id").asText() + "-TRANS",
                    "action",
                    key.equals("T05-START") ? "ISSUE" : type,
                    "quantity",
                    2,
                    "minute",
                    event.get("minute")));
    }
    run.put("clock", clock);
    boolean dispatched = processed.contains("DISPATCH"),
        received = processed.contains("ARRIVAL"),
        issued = processed.contains("T05-START");
    run.set(
        "inventory",
        obj(
            "remote",
            stock(run, "WH-REMOTE") - (dispatched ? 2 : 0),
            "inTransit",
            dispatched && !received ? 2 : 0,
            "local",
            stock(run, "WH-LOCAL") + (received ? 2 : 0) - (issued ? 2 : 0),
            "consumed",
            issued ? 2 : 0));
  }
}

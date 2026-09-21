package cn.peixun.demo;

import static cn.peixun.demo.BusinessException.require;
import static cn.peixun.demo.Json.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;

public final class ScheduleEngine {
  public static ObjectNode calculate(
      JsonNode operations, JsonNode resources, double eta, double transport) {
    require(eta >= 0 && transport >= 0, "到货时间和费用不能为负数");
    Map<String, JsonNode> resourceMap = new HashMap<>();
    resources.forEach(r -> resourceMap.put(r.path("id").asText(), r));
    Map<String, double[]> done = new LinkedHashMap<>();
    Map<String, List<double[]>> calendar = new HashMap<>();
    List<JsonNode> pending = new ArrayList<>();
    Set<String> ids = new HashSet<>();
    operations.forEach(
        o -> {
          require(ids.add(o.path("id").asText()), "工序编号重复");
          pending.add(o);
        });
    require(!pending.isEmpty(), "至少需要一个工序");
    for (JsonNode o : pending)
      for (JsonNode p : o.path("predecessors"))
        require(ids.contains(p.asText()), "工序前置引用不存在：" + p.asText());
    ArrayNode spans = arr();
    double labor = 0, personMinutes = 0, material = 0, wait = 0;
    while (!pending.isEmpty()) {
      JsonNode chosen = null;
      for (JsonNode o : pending) {
        boolean ready = true;
        for (JsonNode p : o.path("predecessors")) if (!done.containsKey(p.asText())) ready = false;
        if (ready
            && (chosen == null || o.path("id").asText().compareTo(chosen.path("id").asText()) < 0))
          chosen = o;
      }
      require(chosen != null, "工序存在循环依赖，无法排程");
      JsonNode o = chosen;
      double duration = o.path("duration").asDouble(-1);
      require(duration > 0 && duration <= 1440, "工序时长必须在0～1440分钟之间");
      double ready = 0;
      for (JsonNode p : o.path("predecessors")) ready = Math.max(ready, done.get(p.asText())[1]);
      double start = ready;
      int quantity = o.path("materialQuantity").asInt();
      if (quantity > 0) start = Math.max(start, eta);
      Set<String> assigned = new HashSet<>();
      for (JsonNode rr : o.path("resources")) {
        String rid = rr.asText();
        require(assigned.add(rid), "同一工序不能重复指定同一资源");
        JsonNode r = resourceMap.get(rid);
        require(
            r != null && r.path("available").asBoolean() && r.path("capacity").asInt() > 0,
            "资源不可用：" + rid);
        start = Math.max(start, r.path("start").asDouble());
      }
      boolean conflict;
      int tries = 0;
      do {
        conflict = false;
        for (String rid : assigned)
          for (double[] interval : calendar.getOrDefault(rid, List.of()))
            if (start < interval[1] && start + duration > interval[0]) {
              start = interval[1];
              conflict = true;
            }
        require(++tries < 10000, "资源排程未收敛");
      } while (conflict);
      double end = start + duration, operationLabor = 0;
      for (String rid : assigned) {
        JsonNode r = resourceMap.get(rid);
        require(end <= r.path("end").asDouble(360), "资源日历不足：" + rid + "，需要到T+" + end);
        calendar.computeIfAbsent(rid, k -> new ArrayList<>()).add(new double[] {start, end});
        if (r.path("category").asText().equals("人员")) {
          operationLabor += duration * r.path("rate").asDouble();
          personMinutes += duration;
        }
      }
      done.put(o.path("id").asText(), new double[] {start, end});
      labor += operationLabor;
      material += quantity * 100;
      wait += start - ready;
      ObjectNode span = (ObjectNode) o.deepCopy();
      span.put("start", start)
          .put("end", end)
          .put("wait", start - ready)
          .put("labor", operationLabor);
      spans.add(span);
      pending.remove(o);
    }
    if (done.containsKey("T02")) require(eta >= done.get("T02")[1], "到货时间不能早于发运准备完成时间");
    double finish = done.values().stream().mapToDouble(v -> v[1]).max().orElse(0);
    return obj(
        "spans",
        spans,
        "finish",
        finish,
        "labor",
        round(labor),
        "personMinutes",
        personMinutes,
        "materialCost",
        material,
        "transportCost",
        transport,
        "cost",
        round(labor + material + transport),
        "waiting",
        wait,
        "onTime",
        finish <= 270,
        "lateMinutes",
        Math.max(0, finish - 270));
  }

  public static ObjectNode sensitivity(JsonNode ops, JsonNode resources) {
    ArrayNode values = arr();
    double sum = 0;
    int onTime = 0;
    for (int eta : new int[] {90, 120, 135, 150, 180}) {
      ObjectNode result = calculate(ops, resources, eta, 100);
      values.add(
          obj(
              "eta",
              eta,
              "finish",
              result.path("finish"),
              "cost",
              result.path("cost"),
              "runId",
              id("SENS")));
      sum += result.path("finish").asDouble();
      if (result.path("onTime").asBoolean()) onTime++;
    }
    return obj(
        "points",
        values,
        "mean",
        sum / 5,
        "onTimeRate",
        onTime * 20,
        "sourceType",
        "FIXED_SCENARIOS");
  }

  public static ObjectNode evaluate(String algorithm, JsonNode input) {
    if (algorithm.equals("AHP")) {
      JsonNode matrix = input.path("matrix");
      require(matrix.size() == 3, "AHP需要3×3矩阵");
      for (JsonNode row : matrix) require(row.isArray() && row.size() == 3, "矩阵维度错误");
      double[] w = new double[3];
      double sum = 0;
      for (int i = 0; i < 3; i++) {
        require(matrix.get(i).size() == 3, "矩阵维度错误");
        double product = 1;
        for (int j = 0; j < 3; j++) {
          double a = matrix.get(i).get(j).asDouble();
          require(a > 0, "矩阵必须为正值");
          require(Math.abs(a * matrix.get(j).get(i).asDouble() - 1) < 1e-6, "矩阵必须互反");
          if (i == j) require(Math.abs(a - 1) < 1e-6, "矩阵对角必须为1");
          product *= a;
        }
        w[i] = Math.cbrt(product);
        sum += w[i];
      }
      for (int i = 0; i < 3; i++) w[i] /= sum;
      double lambda = 0, score = 0;
      JsonNode scores = input.path("scores");
      require(scores.size() == 3, "必须绑定三个指标分数");
      for (int i = 0; i < 3; i++) {
        double aw = 0;
        for (int j = 0; j < 3; j++) aw += matrix.get(i).get(j).asDouble() * w[j];
        lambda += aw / w[i] / 3;
        double v = scores.get(i).asDouble();
        require(v >= 0 && v <= 100, "指标分数应为0～100");
        score += v * w[i];
      }
      double cr = Math.max(0, (lambda - 3) / 2) / .58;
      require(cr <= .1, "AHP一致性未通过，CR=" + round(cr));
      return obj("weights", w, "cr", cr, "score", round(score), "algorithm", "AHP");
    }
    if (algorithm.equals("FUZZY")) {
      JsonNode weights = input.path("weights"),
          m = input.path("matrix"),
          grades = input.path("grades");
      require(
          weights.size() == 3 && m.size() == 3 && grades.size() == 3, "模糊评价需要三项权重、3×3隶属矩阵与三个等级分");
      double total = 0;
      double[] b = new double[3];
      for (int i = 0; i < 3; i++) {
        double w = weights.get(i).asDouble();
        require(w >= 0, "权重不能为负数");
        total += w;
        require(m.get(i).size() == 3, "隶属矩阵维度错误");
        double row = 0;
        for (int j = 0; j < 3; j++) {
          double v = m.get(i).get(j).asDouble();
          require(v >= 0 && v <= 1, "隶属度必须为0～1");
          row += v;
          b[j] += w * v;
        }
        require(Math.abs(row - 1) < 1e-6, "隶属矩阵每行和必须为1");
      }
      require(Math.abs(total - 1) < 1e-6, "权重和必须为1");
      double score = 0;
      for (int j = 0; j < 3; j++) score += b[j] * grades.get(j).asDouble();
      return obj("membership", b, "score", round(score), "algorithm", "FUZZY");
    }
    require(algorithm.equals("DEA"), "未知算法");
    JsonNode cases = input.path("cases");
    require(cases.size() >= 2, "至少需要两个可比对象");
    double max = 0;
    for (JsonNode c : cases) {
      require(c.path("input").asDouble() > 0 && c.path("output").asDouble() > 0, "投入和产出必须为正数");
      max = Math.max(max, c.path("output").asDouble() / c.path("input").asDouble());
    }
    ArrayNode scores = arr();
    for (JsonNode c : cases)
      scores.add(
          obj(
              "name",
              c.path("name"),
              "efficiency",
              round(c.path("output").asDouble() / c.path("input").asDouble() / max)));
    return obj("algorithm", "DEA", "scope", "单投入单产出 · CCR投入导向", "efficiencies", scores);
  }
}

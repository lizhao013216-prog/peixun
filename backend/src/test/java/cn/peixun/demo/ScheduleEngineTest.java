package cn.peixun.demo;

import static cn.peixun.demo.Json.*;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;

class ScheduleEngineTest {
  @Test
  void goldenSchedulesAreCalculatedFromInputs() {
    ObjectNode seed = Seed.create("test", "test", 1);
    for (double[] sample :
        new double[][] {
          {90, 60, 240, 965, 0}, {180, 60, 300, 965, 60}, {135, 100, 255, 1005, 15}
        }) {
      ObjectNode r =
          ScheduleEngine.calculate(
              Seed.operations(false), seed.get("resources"), sample[0], sample[1]);
      assertEquals(sample[2], r.path("finish").asDouble());
      assertEquals(sample[3], r.path("cost").asDouble());
      assertEquals(sample[4], r.path("waiting").asDouble());
      assertEquals(705, r.path("labor").asDouble());
      assertEquals(435, r.path("personMinutes").asDouble());
    }
    ObjectNode p2 =
        ScheduleEngine.calculate(Seed.operations(true), seed.get("resources"), 120, 100);
    assertEquals(250, p2.path("finish").asDouble());
    assertEquals(1025, p2.path("cost").asDouble());
  }

  @Test
  void unavailableResourceAndCyclesFailWithoutInventingAResult() {
    ObjectNode seed = Seed.create("test", "test", 1);
    find(seed, "resources", "M1").put("available", false);
    assertThrows(
        BusinessException.class,
        () -> ScheduleEngine.calculate(Seed.operations(false), seed.get("resources"), 90, 60));
    ArrayNode ops = Seed.operations(false);
    ((ObjectNode) ops.get(0)).set("predecessors", arr("T08"));
    assertThrows(
        BusinessException.class,
        () -> ScheduleEngine.calculate(ops, Seed.create("x", "x", 1).get("resources"), 90, 60));
  }

  @Test
  void calendarContentionSerializesParallelOperations() {
    ObjectNode s = Seed.create("x", "x", 1);
    ArrayNode ops =
        arr(
            obj(
                "id",
                "A",
                "name",
                "a",
                "duration",
                30,
                "predecessors",
                arr(),
                "resources",
                arr("E1")),
            obj(
                "id",
                "B",
                "name",
                "b",
                "duration",
                20,
                "predecessors",
                arr(),
                "resources",
                arr("E1")));
    ObjectNode r = ScheduleEngine.calculate(ops, s.get("resources"), 90, 0);
    assertEquals(50, r.path("finish").asDouble());
    assertEquals(30, r.path("spans").get(1).path("start").asDouble());
  }

  @Test
  void evaluationMethodsReproduceTheGoldenValues() {
    ObjectNode ahp =
        ScheduleEngine.evaluate(
            "AHP",
            obj(
                "matrix",
                new double[][] {{1, 5.0 / 3, 2.5}, {.6, 1, 1.5}, {.4, 2.0 / 3, 1}},
                "scores",
                arr(80, 90, 100)));
    assertEquals(87, ahp.path("score").asDouble());
    assertEquals(.5, ahp.path("weights").get(0).asDouble(), 1e-9);
    assertEquals(0, ahp.path("cr").asDouble(), 1e-9);
    ObjectNode fuzzy =
        ScheduleEngine.evaluate(
            "FUZZY",
            obj(
                "weights",
                arr(.5, .3, .2),
                "matrix",
                new double[][] {{.8, .2, 0}, {.3, .6, .1}, {0, .5, .5}},
                "grades",
                arr(100, 80, 60)));
    assertEquals(87.2, fuzzy.path("score").asDouble());
    assertEquals(.49, fuzzy.path("membership").get(0).asDouble(), 1e-9);
    ObjectNode dea =
        ScheduleEngine.evaluate(
            "DEA",
            obj(
                "cases",
                arr(
                    obj("name", "A", "input", 10, "output", 5),
                    obj("name", "B", "input", 8, "output", 5),
                    obj("name", "C", "input", 10, "output", 4))));
    assertEquals(.8, dea.path("efficiencies").get(0).path("efficiency").asDouble());
    assertEquals(1, dea.path("efficiencies").get(1).path("efficiency").asDouble());
    assertEquals(.64, dea.path("efficiencies").get(2).path("efficiency").asDouble());
  }

  @Test
  void malformedAndInconsistentEvaluationInputsAreRejected() {
    assertThrows(
        BusinessException.class,
        () ->
            ScheduleEngine.evaluate(
                "AHP",
                obj(
                    "matrix",
                    arr(arr(1, 2, 3), arr(1), arr(1, 2, 3)),
                    "scores",
                    arr(80, 90, 100))));
    assertThrows(
        BusinessException.class,
        () ->
            ScheduleEngine.evaluate(
                "AHP",
                obj(
                    "matrix",
                    new double[][] {{1, 9, 1.0 / 9}, {1.0 / 9, 1, 9}, {9, 1.0 / 9, 1}},
                    "scores",
                    arr(80, 90, 100))));
    assertThrows(
        BusinessException.class,
        () ->
            ScheduleEngine.evaluate(
                "FUZZY",
                obj(
                    "weights",
                    arr(.5, .5, .5),
                    "matrix",
                    new double[][] {{1, 0, 0}, {1, 0, 0}, {1, 0, 0}},
                    "grades",
                    arr(100, 80, 60))));
  }

  @Test
  void lifecycleUsesUniqueWindowsAndExactDenominators() {
    ObjectNode s = Seed.create("x", "x", 1);
    ObjectNode r = PlanningModule.apply(s, "lifecycle.calculate", obj("cycle", 6), "PLANNER");
    assertEquals(8, r.path("downtime").asInt());
    assertEquals(98.89, r.path("availability").asDouble());
    r = PlanningModule.apply(s, "lifecycle.calculate", obj("cycle", 8), "PLANNER");
    assertEquals(714, r.path("availableDays").asInt());
    assertEquals(99.17, r.path("availability").asDouble());
    r =
        PlanningModule.apply(
            s, "lifecycle.calculate", obj("cycle", 8, "highIntensityMonth", 5), "PLANNER");
    assertEquals(8, r.path("downtime").asInt());
  }
}

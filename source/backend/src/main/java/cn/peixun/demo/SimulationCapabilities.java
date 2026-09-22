package cn.peixun.demo;

import static cn.peixun.demo.Json.*;

import com.fasterxml.jackson.databind.node.*;

/** Normalizes the finite action, port and state vocabulary used by authoring and runtime. */
public final class SimulationCapabilities {
  private SimulationCapabilities() {}

  public static void enrichAsset(ObjectNode asset) {
    String component = asset.path("componentId").asText("GENERIC").toUpperCase();
    if (component.contains("PUMP")) pump(asset);
    else if (component.contains("VALVE")) valve(asset);
    else if (component.contains("SENSOR")) sensor(asset);
    else if (component.contains("CTRL")) controller(asset);
    else generic(asset);
  }

  public static void enrichObject(ObjectNode object, ObjectNode asset) {
    enrichAsset(asset);
    object.set("actions", asset.path("actions").deepCopy());
    object.set("ports", asset.path("ports").deepCopy());
    object.set("stateFields", asset.path("stateFields").deepCopy());
  }

  private static void pump(ObjectNode asset) {
    asset.set(
        "actions",
        arr(
            action("START", "启动设备", arr()),
            action("STOP", "停止设备", arr()),
            action("RESET", "确认复位", arr()),
            action("CONFIRM", "确认状态", arr())));
    asset.set(
        "ports",
        arr(
            port("readyInput", "就绪输入", "INPUT", "BOOLEAN", "readyInput"),
            port("sensorInput", "模拟量输入", "INPUT", "NUMBER", "sensorInput"),
            port("status", "运行状态", "OUTPUT", "STRING", "status")));
    asset.set(
        "stateFields",
        arr(
            field("status", "STRING"),
            field("readyInput", "BOOLEAN"),
            field("sensorInput", "NUMBER")));
  }

  private static void valve(ObjectNode asset) {
    asset.set(
        "actions",
        arr(
            action("SET_READY", "设置阀门就绪", arr(parameter("value", "BOOLEAN"))),
            action("CONFIRM", "确认阀门", arr())));
    asset.set("ports", arr(port("ready", "阀门就绪", "OUTPUT", "BOOLEAN", "ready")));
    asset.set("stateFields", arr(field("status", "STRING"), field("ready", "BOOLEAN")));
  }

  private static void sensor(ObjectNode asset) {
    asset.set(
        "actions",
        arr(action("SET_VALUE", "输入模拟值", arr(parameter("value", "NUMBER")))));
    asset.set("ports", arr(port("value", "传感器值", "OUTPUT", "NUMBER", "value")));
    asset.set("stateFields", arr(field("status", "STRING"), field("value", "NUMBER")));
  }

  private static void controller(ObjectNode asset) {
    asset.set(
        "actions",
        arr(
            action("CONFIRM", "确认控制单元", arr()),
            action("RESET", "确认复位", arr())));
    asset.set(
        "ports",
        arr(
            port("sensorInput", "模拟量输入", "INPUT", "NUMBER", "sensorInput"),
            port("alarm", "告警输出", "OUTPUT", "BOOLEAN", "alarm")));
    asset.set(
        "stateFields",
        arr(
            field("status", "STRING"),
            field("sensorInput", "NUMBER"),
            field("alarm", "BOOLEAN")));
  }

  private static void generic(ObjectNode asset) {
    asset.set(
        "actions",
        arr(
            action("CONFIRM", "确认对象", arr()),
            action("RESET", "恢复初始状态", arr())));
    asset.set("ports", arr());
    asset.set("stateFields", arr(field("status", "STRING")));
  }

  private static ObjectNode action(String id, String label, ArrayNode parameters) {
    return obj("id", id, "label", label, "parameters", parameters);
  }

  private static ObjectNode parameter(String id, String valueType) {
    return obj("id", id, "valueType", valueType, "required", true);
  }

  private static ObjectNode port(
      String id, String label, String direction, String valueType, String field) {
    return obj(
        "id", id,
        "label", label,
        "direction", direction,
        "valueType", valueType,
        "field", field);
  }

  private static ObjectNode field(String id, String valueType) {
    return obj("id", id, "valueType", valueType);
  }
}

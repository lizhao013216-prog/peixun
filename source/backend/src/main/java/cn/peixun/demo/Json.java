package cn.peixun.demo;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;

public final class Json {
  public static final ObjectMapper MAPPER = new ObjectMapper();

  public static ObjectNode obj(Object... pairs) {
    ObjectNode n = MAPPER.createObjectNode();
    for (int i = 0; i < pairs.length; i += 2)
      n.set(pairs[i].toString(), MAPPER.valueToTree(pairs[i + 1]));
    return n;
  }

  public static ArrayNode arr(Object... values) {
    ArrayNode a = MAPPER.createArrayNode();
    for (Object v : values) a.add(MAPPER.valueToTree(v));
    return a;
  }

  public static ObjectNode parse(String text) {
    try {
      return (ObjectNode) MAPPER.readTree(text);
    } catch (Exception e) {
      throw new IllegalArgumentException("无效JSON", e);
    }
  }

  public static String id(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
  }

  public static ObjectNode find(ObjectNode state, String collection, String id) {
    for (JsonNode n : state.withArray(collection))
      if (n.path("id").asText().equals(id)) return (ObjectNode) n;
    throw new BusinessException(404, "OBJECT_NOT_FOUND", "未找到对象：" + id);
  }

  public static String text(JsonNode n, String field, String fallback) {
    return n.path(field).asText(fallback);
  }

  public static double round(double v) {
    return Math.round(v * 100.0) / 100.0;
  }
}

package cn.peixun.demo;

import java.util.List;

/** Deterministic replacement for rendering, conversion and package-building capabilities. */
public final class MockVirtualPlatformAdapter implements VirtualPlatformPort {
  @Override
  public List<Callback> completeJob(String jobId, String profile) {
    boolean failed = !acceptsInteraction(profile);
    Callback completed =
        new Callback(
            jobId + ":3",
            3,
            failed ? "FAILED" : "SUCCEEDED",
            failed ? "平台模拟任务失败，请调整配置后重试" : "模拟处理完成");
    return switch (profile) {
      case "DUPLICATE_CALLBACK" -> List.of(completed, completed);
      case "OUT_OF_ORDER" ->
          List.of(completed, new Callback(jobId + ":2", 2, "RUNNING", "迟到的处理中回调"));
      default -> List.of(completed);
    };
  }

  @Override
  public boolean acceptsInteraction(String profile) {
    return !profile.matches("FAIL_ONCE|ALWAYS_FAIL|TIMEOUT");
  }
}

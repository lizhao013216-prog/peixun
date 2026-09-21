package cn.peixun.demo;

import java.util.List;

/** The business layer consumes normalized platform results, never vendor SDK objects. */
public interface VirtualPlatformPort {
  record Callback(String eventId, int sequence, String status, String message) {}

  List<Callback> completeJob(String jobId, String profile);

  boolean acceptsInteraction(String profile);
}

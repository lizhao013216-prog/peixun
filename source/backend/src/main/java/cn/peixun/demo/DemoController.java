package cn.peixun.demo;

import static cn.peixun.demo.BusinessException.require;
import static cn.peixun.demo.Json.*;

import com.fasterxml.jackson.databind.node.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/demo")
public class DemoController {
  private final DemoService service;
  private final Map<String, String> sessions = new ConcurrentHashMap<>();

  public DemoController(DemoService service) {
    this.service = service;
  }

  @GetMapping("/health")
  public ObjectNode health() {
    return obj("status", "UP", "version", "1.0.0");
  }

  @GetMapping("/bootstrap")
  public ObjectNode bootstrap() {
    return obj("accounts", Seed.accounts(), "workspaces", service.workspaces());
  }

  @PostMapping("/session")
  public ObjectNode session(@RequestBody ObjectNode p) {
    String actor = p.path("actorId").asText();
    DemoService.role(actor);
    String token = UUID.randomUUID().toString();
    sessions.put(token, actor);
    return obj("token", token, "actorId", actor, "mode", "DEMO_IDENTITY");
  }

  private String actor(String header) {
    String actor = sessions.get(header.replaceFirst("^Bearer ", ""));
    if (actor == null) throw new BusinessException(401, "INVALID_SESSION", "请重新选择演示身份");
    return actor;
  }

  @GetMapping("/state")
  public ObjectNode state(
      @RequestParam(defaultValue = "demo") String workspace,
      @RequestParam(defaultValue = "") String domain,
      @RequestHeader(defaultValue = "") String authorization) {
    return service.state(workspace, actor(authorization), domain);
  }

  @PostMapping("/commands")
  public ObjectNode command(
      @RequestParam(defaultValue = "demo") String workspace,
      @RequestParam(defaultValue = "") String domain,
      @RequestHeader(defaultValue = "") String authorization,
      @RequestBody ObjectNode p) {
    return service.command(workspace, actor(authorization), p, domain);
  }

  @PostMapping("/workspaces")
  public ObjectNode workspace(
      @RequestHeader(defaultValue = "") String authorization, @RequestBody ObjectNode p) {
    DemoService.authorize(actor(authorization), "workspace.create");
    return service.createWorkspace(p.path("name").asText(), p.path("cloneFrom").asText());
  }

  @GetMapping(value = "/reports/{collection}/{id}.md", produces = "text/markdown;charset=UTF-8")
  public ResponseEntity<String> report(
      @PathVariable String collection,
      @PathVariable String id,
      @RequestParam(defaultValue = "demo") String workspace,
      @RequestHeader(defaultValue = "") String authorization) {
    String actor = actor(authorization);
    ObjectNode s = service.state(workspace);
    ObjectNode record = find(s, collection, id);
    if (DemoService.role(actor).equals("LEARNER"))
      require(
          collection.equals("attempts") && record.path("learnerId").asText().equals(actor),
          "只能导出本人的训练报告");
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=report-" + id + ".md")
        .body(service.report(workspace, collection, id));
  }

  @PostMapping("/uploads")
  public ObjectNode upload(
      @RequestParam MultipartFile file,
      @RequestParam(defaultValue = "demo") String workspace,
      @RequestParam String domain,
      @RequestHeader(defaultValue = "") String authorization)
      throws Exception {
    String who = actor(authorization);
    TrainingDomain.require(domain);
    DemoService.authorize(who, "asset.import");
    require(!file.isEmpty() && file.getSize() <= 20 * 1024 * 1024, "文件必须非空且不超过20MB");
    String name =
        Optional.ofNullable(file.getOriginalFilename())
            .orElse("asset")
            .replaceAll("[\\\\/\\r\\n]", "_");
    String ext =
        name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toUpperCase() : "BIN";
    require(
        Set.of(
                "FBX", "OBJ", "STEP", "STL", "GLB", "GLTF", "PNG", "JPG", "JPEG", "SVG", "PDF",
                "MD", "TXT", "MP4", "WAV", "MP3", "JSON", "ZIP")
            .contains(ext),
        "不支持此文件类型");
    String blob = UUID.randomUUID().toString();
    Path directory = Paths.get(".data", "uploads");
    Files.createDirectories(directory);
    Path path = directory.resolve(blob);
    file.transferTo(path.toAbsolutePath());
    ObjectNode s = service.state(workspace);
    try {
      return service.command(
          workspace,
          who,
          obj(
              "action",
              "asset.import",
              "commandId",
              id("UPLOAD"),
              "runEpoch",
              s.get("epoch"),
              "expectedRevision",
              s.get("revision"),
              "payload",
              obj(
                  "name",
                  name,
                  "ownerSystem",
                  domain,
                  "format",
                  ext,
                  "category",
                  Set.of("PNG", "JPG", "JPEG", "SVG").contains(ext)
                      ? "图像"
                      : Set.of("MD", "TXT", "PDF", "JSON").contains(ext)
                          ? "文档"
                          : Set.of("MP4").contains(ext)
                              ? "视频"
                              : Set.of("WAV", "MP3").contains(ext) ? "音频" : "模型",
                  "blobRef",
                  blob,
                  "sizeLabel",
                  round(file.getSize() / 1024.0) + " KB")),
          domain);
    } catch (RuntimeException e) {
      Files.deleteIfExists(path);
      throw e;
    }
  }

  @GetMapping("/assets/{id}/file")
  public ResponseEntity<byte[]> assetFile(
      @PathVariable String id,
      @RequestParam(defaultValue = "demo") String workspace,
      @RequestParam(defaultValue = "") String domain,
      @RequestHeader(defaultValue = "") String authorization)
      throws Exception {
    String who = actor(authorization);
    ObjectNode asset = find(service.state(workspace, who, domain), "assets", id);
    String ref = asset.path("blobRef").asText();
    require(ref.matches("[a-f0-9-]{36}"), "此资源没有上传源文件");
    Path path = Paths.get(".data", "uploads", ref);
    if (!Files.isRegularFile(path))
      throw new BusinessException(404, "FILE_NOT_FOUND", "源文件不存在，请重新上传");
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename(asset.path("name").asText(), java.nio.charset.StandardCharsets.UTF_8)
                .build()
                .toString())
        .header("X-Content-Type-Options", "nosniff")
        .body(Files.readAllBytes(path));
  }

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ObjectNode> business(BusinessException e) {
    return ResponseEntity.status(e.status)
        .body(obj("error", obj("code", e.code, "message", e.getMessage())));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ObjectNode> unexpected(Exception e) {
    org.slf4j.LoggerFactory.getLogger(DemoController.class).error("Demo request failed", e);
    return ResponseEntity.status(500)
        .body(obj("error", obj("code", "INTERNAL_ERROR", "message", "服务处理失败，请检查服务日志并重试")));
  }
}

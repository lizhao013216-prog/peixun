package cn.peixun.demo;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaController {
  @GetMapping({
    "/workbench",
    "/assets",
    "/editor/**",
    "/releases",
    "/sessions/**",
    "/records/**",
    "/demo",
    "/guide",
    "/operation/**",
    "/maintenance/**",
    "/support/**"
  })
  public String app() {
    return "forward:/index.html";
  }
}

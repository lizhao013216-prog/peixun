# 航维三系统Demo交付包

## 直接运行

需要Java 17。界面与后端均已构建，无须安装Node或Maven。

1. 完整解压本包，不要只取出JAR。
2. Windows双击`start.bat`；Linux/macOS在包目录执行`bash start.sh`。
3. 打开 http://127.0.0.1:8080 。
4. 默认提供“完整业务演示案例”，可直接查看两类训练、三情景演练、施工返工和P2归档。
5. 新的操作保存在`backend/.data`。服务停止后可整体备份此目录。

如果8080被占用，先停止冲突进程，或设置`PORT`后启动。程序仅监听本机。

## 阅读文档

- [演示操作手册](docs/演示操作手册.md)
- [开发与运行说明](docs/开发与运行说明.md)
- [实现范围与验证记录](docs/实现范围与验证记录.md)
- [UI设计说明与实际截图](docs/UI设计说明.md)

## 将源码应用到GitHub项目

本地开发分支：`feat/three-system-demo`。本地提交：`6ff6b89b3a7a45bb2f864d05380348ad854537c0`。原仓库基线：`36c7041759db88cd743898b98c8de0aaf2494f77`。

本次GitHub插件连接已确认，但当前执行环境未获得Git推送凭据，因此本提交尚未写入远端仓库。`source/0001-three-system-demo.patch`包含本次全部源码、测试、文档和静态资源变更，原始客户资料不在补丁中修改。

在拥有该仓库写权限的本机执行：

```bash
git clone https://github.com/lizhao013216-prog/peixun.git
cd peixun
git switch -c feat/three-system-demo 36c7041759db88cd743898b98c8de0aaf2494f77
git am --3way /补丁所在目录/0001-three-system-demo.patch
git push -u origin feat/three-system-demo
```

请将补丁路径换成实际路径。已有本地仓库时先保存自己的修改，再建立独立分支应用补丁；无需重新克隆。推送后可按正常评审流程合并到主分支。

## 验证结果

后端10项自动化测试通过，前端类型校验与生产构建通过；Chromium检查33个页面、桌面/手机布局、复盘库存、组网交互和课程表单保存通过。Docker及远端CI未执行。VR渲染、转换和物理能力按方案使用模拟适配器，正式系统能力边界见验证文档。

独立交付包已实测：JAR启动、重启后H2数据保留、88分维修成绩、1070费用实绩、P2成果已发布、深层页面刷新、本地字体加载均通过。

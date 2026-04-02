# 测试与质量保障

本章给出前端与后端测试的策略、常用命令与集成建议，帮助保持代码质量。

总体原则
- 在提交前尽量通过单元测试、集成测试与静态分析来确保改动安全。
- 测试应覆盖核心业务场景、边界条件以及异常路径。
- 将测试与构建集成到 CI 流程中，确保合并前通过阈值检查。

1) 前端测试
- 选型：建议使用 Vitest + Vue Testing Library 进行单元/组件测试，结合 ESLint/Prettier 做代码质量控制。
- 常用命令（示例，需在项目中添加脚本）：
  - 安装测试依赖：`npm install -D vitest @vue/test-utils @testing-library/vue` 
  - 运行测试：`npm run test`（需在 package.json 增加脚本）
- 覆盖要点：组件渲染、路由导航、状态管理（Pinia）交互、API 调用的 mocking。

2) 后端测试
- 选型：JUnit 5 + Mockito，结合 Spring Boot Test 提供的测试支持。
- 常用命令（示例，需在项目中添加脚本）：
  - 构建与测试：`mvn -v test`（若使用 Maven）或 `./gradlew test`（若使用 Gradle）
- 覆盖要点：服务层逻辑、数据访问、控制器端点的集成测试、JWT 授权路径。

3) 静态分析与代码质量
- 添加 ESLint/Stylelint（前端）和 Checkstyle/PMD/SpotBugs（后端）等静态分析工具。
- 统一的格式化工具（Prettier、Prettier Plugin for Vue、Google Java Format 等）。

4) 持续集成
- 配置 CI 流水线，包含：安装依赖、静态分析、单元测试、构建、打包输出检查。
- 常见的触发条件：Push、PR、定时构建。

5) 产物与覆盖率
- 产物：测试报告可输出为 HTML/XML，便于浏览或与 CI 结合。
- 覆盖率：建议前端 80%+、后端 70%+，具体数字可结合实际情况调整。

附注
- 由于当前仓库还未内建完整的测试脚手架，以上为建议路线图。若你愿意，我可以基于现有代码结构逐步添加测试用例模板和示例测试。

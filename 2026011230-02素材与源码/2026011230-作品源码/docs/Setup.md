# 开发环境搭建

本文档描述在本地搭建开发环境所需的步骤，帮助新成员快速投入开发。

## 先决条件
- Node.js（推荐 18+）
- npm 或 yarn
- Java JDK 17+（后端）
- Maven 或 Gradle（后端构建，按团队选择）
- MySQL（数据库）
- Redis（缓存）
- RabbitMQ（消息队列，可选，若后端使用）

## 项目结构要点
- frontend/：Vue 3 + TypeScript 前端应用
- backend/：Spring Boot 后端应用
- docs/：文档集合，包含 API、部署、测试等

## 环境变量与配置覆盖
- 后端的敏感信息（如数据库密码、JWT 秘钥）请通过环境变量覆盖，避免直接写入配置文件。
- 生产环境可考虑使用配置中心或秘密管理服务。

## 安装与启动步骤
1) 安装前端依赖
   - 进入 frontend 目录
   - 运行：`npm install`
2) 启动前端
   - 运行：`npm run dev`
   - 浏览器访问：http://localhost:5173/
3) 安装与启动后端
   - 进入 backend 目录，按团队约定的构建工具执行 `mvn clean package` 或 `./gradlew build`
   - 启动jar：`java -jar backend/target/*.jar`（实际路径以构建产出为准）
4) 数据库与缓存
   - 根据 docs/Deployment.md 的本地部署模板准备 MySQL 与 Redis。
 
## 验证与常见问题
- 访问前端 URL 验证页面加载
- Swagger API 文档地址通常为 http://localhost:8080/swagger-ui.html
- 如遇端口冲突，请修改本地端口映射或调整服务端口配置。

## 版本与升级
- 变更日志、版本号及向后兼容性策略需在计划变更前沟通确认。
- 重大变更应附带回滚方案。

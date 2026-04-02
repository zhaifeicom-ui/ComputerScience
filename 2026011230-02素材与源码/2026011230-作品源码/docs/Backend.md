# 后端说明（Spring Boot）

本后端使用 Spring Boot 构建，提供 RESTful API、认证与数据访问层。核心模块示例：
- InventoryService：库存管理
- ProductService：商品管理
- DataImportService：数据导入与调度
- AIPredictionService：AI 预测服务
- JwtAuthenticationFilter / JwtUtil：JWT 认证
- TenantContextHolder：租户上下文与多租户支持

## 运行与构建
- Maven 构建：进入 backend，执行 `mvn clean package`，得到可执行的 jar 文件。
- 运行：`java -jar target/*.jar`，或按团队约定的启动脚本执行。

## 架构概览
```
前端(Vue) <--> 后端(Spring Boot) <--> 数据库(MySQL)
                       |
                       v
                    Redis（缓存）
                       |
                    RabbitMQ（消息队列）
```

关键模块间的关系与职责：
- InventoryService：负责库存记录的增删改查，包含库存与安全库存、最后更新时间等字段。对租户进行隔离，支持分页查询和按产品筛选。
- ProductService：管理商品信息，商品的删除状态影响库存有效性校验。
- DataImportService：处理外部数据导入任务，通常与 DataImportJobMapper/相关 Mapper 配合。
- AIPredictionService：对某些库存单位进行未来销售预测并返回结果，用于库存风险评估。
- JwtAuthenticationFilter/CustomUserDetails/CustomUserDetailsService：实现基于 JWT 的认证与鉴权逻辑。
- TenantContextHolder：从请求上下文中提取并保持租户标识，确保多租户数据隔离。

## 核心数据模型（示例性描述）
- Inventory：字段示例包括 tenantId、productId、stock、safetyStock、latestUpdateOn、createdAt、updatedAt 等，用于追踪库存状态。
- Product：字段示例包括 id、name、deleted 等，deleted 标记用于软删除检查。
- User/Account：鉴权系统中的基本用户和角色信息（如存在于后端的 UserService/CustomUserDetails）。

## API 设计（端点示例，具体字段以实际实现为准）
- GET /api/v1/inventories?page=&size=&productId=
- GET /api/v1/inventories/{id}
- POST /api/v1/inventories
- PUT /api/v1/inventories/{id}
- DELETE /api/v1/inventories/{id}
- POST /api/v1/inventories/check
- POST /api/v1/inventories/{productId}/sales-update
- 其他资源（products、jobs、ai 等）按同样的风格暴露。
- 认证：Header: Authorization: Bearer <jwt>
- 响应格式：统一 JSON，包含 data、message、code 等字段。

示例：获取分页库存（伪结构）
curl -s -H "Authorization: Bearer ${TOKEN}" \
  http://localhost:8080/api/v1/inventories?page=1&size=20 | jq

- 错误处理：如鉴权失败、无权限、参数错误，返回相应的 4xx/5xx 状态码和错误信息。

## 数据源与持久化
- 数据库：MySQL，推荐使用 InnoDB 存储引擎，正确配置字符集与时区。
- 缓存：Redis，用于缓存热门查询与会话信息。
- 事务：把核心写操作放在 @Transactional 事务范围内，确保数据一致性。

## 安全与多租户
- TenantContextHolder 提供租户上下文，在查询和缓存键中体现租户维度，确保数据隔离。
- 授权策略基于 JWT，控制对敏感操作的访问。

## 配置与环境变量
- application.yml 中包含数据源、Redis、RabbitMQ、JWT、AI 服务地址等配置项。生产环境请通过配置中心或环境变量覆盖。
- 建议把敏感信息（如 JWT 秘钥、数据库密码）通过安全的秘密管理工具来管理。

## 日志与监控
- 日志级别可在 application.yml 调整；结合日志聚合系统（如 ELK/Prometheus+Grafana）进行监控与告警。

## 测试与质量保障
- 编写单元测试与集成测试，覆盖核心业务场景。
- 使用 CI/CD 来执行自动化测试、代码风格检查与打包。

## 部署与升级
- 见 docs/Deployment.md 的本地与生产部署要点。
- 数据库结构变更建议使用独立的迁移工具（Flyway/Liquibase）。

## 参考与进一步阅读
- JWT、Spring Security、MyBatis-Plus、Zod 等库的官方文档。
- 项目中各 Mapper/Entity/Service 的实现请以源码为准。

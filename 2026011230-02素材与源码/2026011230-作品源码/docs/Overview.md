# 架构概览

本项目采用前后端分离架构，清晰的职责分离有助于独立开发、测试和部署。

- 前端（frontend/）
  - 技术栈：Vue 3 + TypeScript + Vite + Pinia + Element Plus
  - 结构：组件化、路由驱动、状态管理与 API 客户端分离
  - 与后端通信：通过 RESTful API 封装的服务层与后端交互

- 后端（backend/）
  - 技术栈：Spring Boot、MyBatis-Plus、JWT、MySQL、Redis、RabbitMQ
  - 结构：Controllers、Services、Mappers、Entities、DTO、Cache、安全/多租户模块
  - 数据源：支持多租户环境，TenantContextHolder 负责租户上下文

- 数据流与集成
  - 客户端发起 API 请求，后端进行鉴权、业务逻辑处理和持久化
  - AI 预测服务（AIPredictionService）可与外部或本地微服务对接，返回预测结果用于库存决策
  - Redis 缓存热点查询、JWT Token 的缓存策略（如需要）
  - RabbitMQ 作为事件总线或任务队列，解耦数据处理与任务调度

- 部署管线
  - 本地开发以 Docker Compose、单元测试/集成测试、CI/CD 自动化为核心
  - 生产环境可使用 Kubernetes 或云端容器服务，配合 CI/CD 自动化部署

- 非功能性需求（NFR）
  - 可用性：目标 99.9% 以上（在 SLA 场景下设定）
  - 可伸缩性：水平扩展后端服务、前端静态资源分发
  - 安全性：JWT、输入校验、授权与审计日志
  - 观测性：集中日志、指标、告警
  - 可靠性：幂等性设计、错误兜底、断路保护

- 与文档/桥接
  - 文档结构设计：docs/API.md、docs/UserGuide.md、docs/Deployment.md 等
  - 代码与文档同步机制：PR 时同步更新文档，CI 校验文档变更

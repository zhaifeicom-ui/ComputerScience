# API 文档（v1）


版本与认证
- 版本：/api/v1/...
- 认证：使用 Bearer Token（JWT）。请求头中需包含 Authorization: Bearer <token>。
- 获取 Token 的流程请参考前端登录流程，以及后端认证模块的实现。

通用响应格式
- 成功响应：HTTP 200/201，JSON 结构通常包含 data、message、code 等字段（具体以后端实际返回为准）。
- 失败响应：HTTP 4xx/5xx，字段包括 error、message、code 等，详见示例。

## 端点总览
 inventoires (库存)
- GET /api/v1/inventories
  - 查询参数：page（int）、size（int）、productId（可选，long）
  - 返回：分页的 InventoryResponse 对象列表
- GET /api/v1/inventories/{id}
- POST /api/v1/inventories
  - 请求体：InventoryCreateRequest
  - 返回：创建后的 InventoryResponse
- PUT /api/v1/inventories/{id}
  - 请求体：InventoryUpdateRequest
  - 返回：更新后的 InventoryResponse
- DELETE /api/v1/inventories/{id}
- POST /api/v1/inventories/check
  - 请求体：InventoryCheckRequest
  - 返回：检查结果对象（含 warnings、futureDays 等）
- POST /api/v1/inventories/{productId}/sales-update
  - 请求体：SalesUpdateRequest
  - 说明：根据销售数据更新库存，具体参数参考后端实现

products (商品)
- GET /api/v1/products
- GET /api/v1/products/{id}
- POST /api/v1/products
- PUT /api/v1/products/{id}
- DELETE /api/v1/products/{id}

jobs (任务/导入)
- GET /api/v1/jobs
- POST /api/v1/jobs/import

ai (预测/AI 服务)
- POST /api/v1/ai/prediction/sales-total
  - 说明：调用 AI 服务获取未来 N 天的销售预测总量（示例路径，具体实现以后端实际暴露为准）

示例：获取库存分页
curl -s -H "Authorization: Bearer ${TOKEN}" \
  "http://localhost:8080/api/v1/inventories?page=1&size=20" | jq

响应示例（伪结构，实际字段以后端返回为准）
{
  "data": {
    "records": [ { "id": 1, "productId": 123, "stock": 50, "safetyStock": 10, "latestUpdateOn": "2026-04-01T12:00:00" } ],
    "page": 1,
    "size": 20,
    "total": 100
  },
  "message": "OK",
  "code": 0
}
- 版本控制与命名
- 版本：v1，未来如有 API 变更将以新版本命名，老版本保持向后兼容性（如需要）。
- 端点命名遵循 REST 风格，资源名使用名词复数形式，动词通过 HTTP 方法体现。
- 请求与响应的字段以实际后端数据模型为准，以下示例仅用于对齐理解。

## 授权与鉴权
- 所有需要认证的接口应包含 Authorization 头：Bearer <token>
- 登录会生成 JWT；前端应在每次请求中附带 token。
- 失效或权限不足时返回 401/403。需要细化的角色策略请参考后端实现。

## 错误与响应结构
- 统一响应结构示例：
  ```json
  {
    "code": 0,
    "message": "OK",
    "data": { ... }
  }
  ```
- 失败示例：
  ```json
  {
    "code": 401,
    "message": "Unauthorized",
    "data": null
  }
  ```

## 分页、排序与过滤
- 常用查询参数：page、size、sort、filter（按字段名筛选）
- 服务端应返回 total、page、size、records 等元信息。

## 端点清单（v1）
- Inventories
  - GET /api/v1/inventories?page=&size=&productId=
  - GET /api/v1/inventories/{id}
  - POST /api/v1/inventories
  - PUT /api/v1/inventories/{id}
  - DELETE /api/v1/inventories/{id}
  - POST /api/v1/inventories/check
- Products
  - GET /api/v1/products
  - GET /api/v1/products/{id}
  - POST /api/v1/products
  - PUT /api/v1/products/{id}
  - DELETE /api/v1/products/{id}
- Jobs
  - GET /api/v1/jobs
  - POST /api/v1/jobs/import
- AI
  - POST /api/v1/ai/prediction/sales-total

## 请求/响应示例
- 获取库存分页
  - 请求：GET /api/v1/inventories?page=1&size=20
  - 响应：
  ```json
  {
    "code": 0,
    "message": "OK",
    "data": {
      "page": 1,
      "size": 20,
      "total": 128,
      "records": [
        {
          "id": 101,
          "tenantId": 1,
          "productId": 501,
          "stock": 120,
          "safetyStock": 20,
          "latestUpdateOn": "2026-04-01T12:34:56"
        }
      ]
    }
  }
  ```
- 创建库存记录
  - 请求：POST /api/v1/inventories
  - 请求体示例：
  ```json
  {
    "productId": 501,
    "stock": 100,
    "safetyStock": 20
  }
  ```
  - 响应：上述对象的创建结果。
- 预测/告警相关
  - 请求：POST /api/v1/inventories/check
  - 请求体示例：
  ```json
  {
    "futureDays": 14,
    "productId": null
  }
  ```
  - 响应：包含 hasWarnings、warnings、futureDays、checkedAt 等字段。

## 字段映射与命名规范
- 数据库字段与 JSON 字段应保持清晰对应关系，尽量使用 camelCase（前端风格）与数据库字段的 snake_case 之间的一致性。
- 对于时间字段采用 ISO 8601 格式。

## 版本演进与弃用策略
- 重大接口变更需发布新版本（如 /api/v2/），保持向后兼容性，逐步迁移。
- 弃用接口在新版本中添加弃用标记，留出迁移期。

## 兼容性与回滚
- 监控 API 调用失败率和错误码，以便快速发现兼容性问题。
- 回滚策略：若新版本接口导致问题，快速回退到前一个稳定版本。


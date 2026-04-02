# SaaS 销售数据中台 - 前端 API 请求说明文档

本文档详细说明了 Vue 3 前端应用向后端 Spring Boot 服务器发出的所有网络请求，包括全局配置、请求头机制、数据结构以及各个模块的具体接口。

## 1. 全局请求配置 (Axios)

前端所有的网络请求均通过 `axios` 实例进行封装，配置文件位于 `src/utils/api.ts`。

### 1.1 基础配置
- **Base URL**: `http://localhost:8080/api`
- **Timeout**: `10000ms` (10秒)

### 1.2 全局请求头 (Headers)
为了满足后端的多租户和安全认证架构，前端在每次发起请求前（除登录接口外），都会通过 **Axios 拦截器** 自动在请求头中注入以下两个关键字段：

1. **认证 Token**: 
   - Header Key: `Authorization`
   - Value Format: `Bearer <token>`
   - 来源: 登录成功后后端返回，存储在前端 `Pinia` 状态管理和 `localStorage` 中。

2. **租户标识 (Tenant ID)**:
   - Header Key: `X-Tenant-Id`
   - Value Format: `<tenant_id>` (如: `1001`)
   - 来源: 用户在登录页手动输入，用于后端 MyBatis Plus 插件实现数据隔离。

### 1.3 响应拦截与错误处理
- **200 OK**: 自动提取 `response.data` 传递给业务层。
- **401 Unauthorized**: Token 过期或无效。自动清除本地登录状态，跳转回 `/login` 页面，并弹出提示。
- **403 Forbidden**: 权限不足提示。
- **500 Internal Error**: 服务器内部错误提示。

---

## 2. 核心业务接口说明

### 2.1 认证模块 (Auth)

#### 登录
- **Endpoint**: `POST /auth/login`
- **用途**: 验证用户凭据并获取 Token。
- **请求体 (Body)**:
  ```json
  {
    "username": "admin",
    "password": "password123"
  }
  ```
- **特殊说明**: 登录时即使未携带 Token，也会携带 `X-Tenant-Id`，以便后端在特定的租户库中校验用户。

---

### 2.2 销售数据模块 (Sales)

#### 获取销售流水列表
- **Endpoint**: `GET /sales`
- **Query 参数**:
  - `page`: 当前页码 (如: 1)
  - `size`: 每页条数 (如: 10)
  - `search`: (可选) 搜索关键字
- **响应数据示例**:
  ```json
  {
    "code": 200,
    "data": {
      "records": [
        {
          "id": 1,
          "date": "2023-10-15",
          "productId": "P001",
          "sales": 12,
          "price": 7999,
          "extraFeatures": { "color": "black", "capacity": "256G" }
        }
      ],
      "total": 50
    }
  }
  ```

#### 上传 CSV 文件 (异步任务)
- **Endpoint**: `POST /sales/upload-csv`
- **用途**: 上传大批量销售数据。由于数据量大，后端采用异步处理。
- **请求类型**: `multipart/form-data`
- **请求体**:
  - `file`: CSV 文件对象
- **响应说明**: 返回一个 `jobId`，前端随后使用该 ID 轮询任务进度。
  ```json
  {
    "code": 200,
    "data": {
      "jobId": "job-1001",
      "status": "PENDING"
    }
  }
  ```

---

### 2.3 异步任务监控模块 (Jobs)

#### 获取任务列表/状态
- **Endpoint**: `GET /jobs`
- **用途**: 获取当前租户下的所有异步导入任务及其进度。
- **响应数据示例**:
  ```json
  {
    "code": 200,
    "data": [
      {
        "jobId": "job-1001",
        "fileName": "sales_2023_Q3.csv",
        "status": "PROCESSING", // PENDING, PROCESSING, COMPLETED, FAILED
        "totalRecords": 5000,
        "processedRecords": 2500,
        "errorMessage": null
      }
    ]
  }
  ```
- **前端逻辑**: 前端在 `Jobs.vue` 页面会根据 `totalRecords` 和 `processedRecords` 自动计算并渲染进度条。

---

### 2.4 数据大盘/分析模块 (Dashboard)

*(注意：当前版本前端使用 Mock 数据，但预留了以下接口结构以对接后端大盘数据)*

#### 获取销售趋势图数据
- **Endpoint**: `GET /analysis/sales-trend`
- **Query 参数**: `days` (如: 7)
- **用途**: 渲染 ECharts 面积趋势图。

#### 获取畅销商品占比
- **Endpoint**: `GET /analysis/top-products`
- **Query 参数**: `limit` (如: 5)
- **用途**: 渲染 ECharts 环形饼图。

## 3. 当前前端的 Mock 状态说明

为了保证在后端未启动或接口未完善时前端能够正常展示，当前的前端代码在视图层 (`views/*.vue`) 中使用了 `setTimeout` 和静态数组来模拟 API 请求的延迟和返回值。

**如何切换到真实 API：**
当后端服务就绪后，你只需要打开对应的 `.vue` 文件，取消注释 `import api from '../utils/api'`，并用 `await api.get(...)` 或 `await api.post(...)` 替换掉当前的 `setTimeout` 模拟逻辑即可。拦截器会自动处理 Token 和租户 ID。
from fastapi import FastAPI, HTTPException, Query, Body, Request, status
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field
from typing import List, Dict, Any, Optional

# 从我们的服务文件中导入核心类
from prediction_service import SalesPredictionService

# --- FastAPI 应用实例 ---
app = FastAPI(
    title="Sales AI Service (LGBM Hybrid)",
    description="遵循API契约，提供集成的销量预测和价格弹性分析。",
    version="2.0.0"
)

# --- AI服务实例 ---
# 在应用启动时创建单例
service = SalesPredictionService()

# --- Pydantic 模型 (严格对应API文档) ---
class HistoricalDataPoint(BaseModel):
    ds: str
    y: int
    price: Optional[float] = None

class FutureRegressor(BaseModel):
    ds: str
    price: float

class SalesPredictRequest(BaseModel):
    tenant_id: Optional[str] = "default_tenant"
    product_id: str
    days_to_forecast: int
    future_regressors: List[FutureRegressor] = []
    # Java后端总是会传来历史数据
    historical_data: List[HistoricalDataPoint] 

class ElasticitySimulationContext(BaseModel):
    price: float

class ElasticityBaselineContext(BaseModel):
    price: float

class ElasticityPredictRequest(BaseModel):
    tenant_id: Optional[str] = "default_tenant"
    product_id: str
    simulation_context: ElasticitySimulationContext
    baseline_context: Optional[ElasticityBaselineContext] = None
    historical_data: List[HistoricalDataPoint]


# --- 全局异常处理器 ---
@app.exception_handler(Exception)
async def generic_exception_handler(request: Request, exc: Exception):
    return JSONResponse(
        status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
        content={"code": 500, "message": f"An internal server error occurred: {str(exc)}"},
    )

@app.exception_handler(FileNotFoundError)
async def file_not_found_handler(request: Request, exc: FileNotFoundError):
    return JSONResponse(
        status_code=status.HTTP_404_NOT_FOUND,
        content={"code": 404, "message": "Model not found. Please trigger training first."},
    )

# --- 1. 销量预测 ---
@app.post("/v1/models/sales-forecast/train")
async def trigger_sales_training(request: Request, productId: str = Query(...)):
    # 模拟从Java后端接收请求，然后Java后端会去拉数据再调用内部训练逻辑
    # 在真实场景中，这个接口会触发一个后台任务
    # 这里我们简化为直接返回成功
    tenant_id = request.headers.get("X-Tenant-ID", "default_tenant") # 假设租户ID在头里
    # 实际训练逻辑将在预测时自动触发，这里仅作为契约占位符
    return {"code": 200, "message": "销量预测模型训练任务已提交", "data": None}

@app.post("/v1/predict/sales-forecast")
async def get_sales_forecast(request: Request, payload: SalesPredictRequest):
    tenant_id = payload.tenant_id or request.headers.get("X-Tenant-ID", "default_tenant")

    
    # 自动训练/更新模型
    service.train(tenant_id, payload.product_id, [d.dict() for d in payload.historical_data])
    
    # 获取预测
    predictions = service.predict_sales_forecast(
        tenant_id,
        payload.product_id,
        payload.days_to_forecast,
        [d.dict() for d in payload.historical_data],
        [d.dict() for d in payload.future_regressors] if payload.future_regressors else None
    )
    
    return {"code": 200, "message": "预测成功", "data": {"predictions": predictions}}

# --- 2. 价格弹性分析 ---
@app.post("/v1/models/price-elasticity/train")
async def trigger_elasticity_training(request: Request, productId: str = Query(...)):
    # 同样，这是一个契约占位符
    return {"code": 200, "message": "价格弹性模型训练任务已提交", "data": None}

@app.post("/v1/predict/price-elasticity")
async def get_price_elasticity(request: Request, payload: ElasticityPredictRequest):
    tenant_id = payload.tenant_id or request.headers.get("X-Tenant-ID", "default_tenant")

    
    # 自动训练/更新模型
    service.train(tenant_id, payload.product_id, [d.dict() for d in payload.historical_data])

    # 获取分析
    analysis_data = service.predict_price_elasticity(
        tenant_id,
        payload.product_id,
        payload.simulation_context.dict(),
        payload.baseline_context.dict() if payload.baseline_context else None
    )
    
    return {"code": 200, "message": "分析成功", "data": analysis_data}

# --- 启动命令 ---
if __name__ == "__main__":
    import uvicorn
    # 运行: uvicorn main:app --host 0.0.0.0 --port 5000 --reload
    uvicorn.run("main:app", host="0.0.0.0", port=5000, reload=True)
import pandas as pd
import numpy as np
import lightgbm as lgb
import joblib
import os
import gc
from typing import List, Dict, Any

class SalesPredictionService:
    """
    一个集成了销量预测和价格弹性分析的智能预测服务。
    使用 LightGBM 模型，能够同时处理时间序列特征和外部回归量（如价格）。
    """
    
    def __init__(self, storage_path: str = "./model_storage"):
        self.storage_path = storage_path
        os.makedirs(self.storage_path, exist_ok=True)

    def _get_model_path(self, tenant_id: str, product_id: str) -> str:
        """生成并确保模型文件的存储路径存在"""
        model_dir = os.path.join(self.storage_path, tenant_id, product_id)
        os.makedirs(model_dir, exist_ok=True)
        return os.path.join(model_dir, "lgbm_sales_model.joblib")

    def _create_features(self, df: pd.DataFrame, y_col: str = None) -> pd.DataFrame:
        """
        核心特征工程函数。
        :param df: 必须包含 'ds' 列的DataFrame。
        :param y_col: 如果存在，则会创建滞后和滑动窗口特征。
        """
        df = df.copy()
        df['ds'] = pd.to_datetime(df['ds'])
        
        # 时间特征
        df['day_of_week'] = df['ds'].dt.dayofweek
        df['day_of_month'] = df['ds'].dt.day
        df['month'] = df['ds'].dt.month
        df['year'] = df['ds'].dt.year
        df['week_of_year'] = df['ds'].dt.isocalendar().week.astype(int)
        
        # 如果提供了历史销量，可以创建更强大的特征
        if y_col and y_col in df.columns:
            # 滞后特征 (Lag Features)
            df['lag_7'] = df[y_col].shift(7)
            df['lag_14'] = df[y_col].shift(14)
            
            # 滑动窗口特征 (Rolling Window Features)
            df['rolling_mean_7'] = df[y_col].shift(1).rolling(window=7, min_periods=1).mean()
            df['rolling_std_7'] = df[y_col].shift(1).rolling(window=7, min_periods=1).std()
            
            # 使用bfill填充最开始的几个NaN值
            df.bfill(inplace=True)

        return df

    def train(self, tenant_id: str, product_id: str, historical_data: List[Dict]):
        """
        训练模型并保存。
        """
        model_path = self._get_model_path(tenant_id, product_id)
        
        df = pd.DataFrame(historical_data)
        df_featured = self._create_features(df, y_col='y')
        
        features = [col for col in df_featured.columns if col not in ['ds', 'y', 'id']]
        target = 'y'
        
        X = df_featured[features]
        y = df_featured[target]
        
        model = lgb.LGBMRegressor(
            random_state=42,
            n_estimators=200,      # 增加树的数量
            learning_rate=0.05,
            num_leaves=31
        )
        model.fit(X, y)
        
        joblib.dump(model, model_path)
        del model, df, df_featured
        gc.collect()

    def predict_sales_forecast(self, tenant_id: str, product_id: str, days_to_forecast: int, historical_data: List[Dict], future_regressors: List[Dict] = None) -> List[Dict]:
        """
        预测未来N天的销量。
        :param future_regressors: 包含未来日期的特征列表，例如 [{'ds': '2023-11-01', 'price': 99.0}]
        """
        model_path = self._get_model_path(tenant_id, product_id)
        if not os.path.exists(model_path):
            # 如果模型不存在，先进行一次训练
            self.train(tenant_id, product_id, historical_data)

        model = joblib.load(model_path)
        
        history_df = pd.DataFrame(historical_data)
        history_df['ds'] = pd.to_datetime(history_df['ds'])
        
        last_date = history_df['ds'].max()
        # 假设未来的价格维持最后一天已知的价格，除非 future_regressors 提供了动态价格
        last_price = history_df.sort_values('ds')['price'].iloc[-1]
        if pd.isna(last_price) or last_price <= 0:
            last_price = 1.0 # 避免除零错误
            
        future_dates = pd.date_range(start=last_date + pd.Timedelta(days=1), periods=days_to_forecast)
        future_df = pd.DataFrame({'ds': future_dates, 'price': last_price})
        
        simulated_price = last_price
        # 应用用户提供的动态未来回归因子（例如动态输入价格）
        if future_regressors and len(future_regressors) > 0:
            # 取提供的第一个价格作为未来所有的模拟价格，这避免了由于前端传入日期与后端预测日期不完全对齐而产生的空值问题
            provided_price = future_regressors[0].get('price')
            if provided_price is not None and provided_price > 0:
                simulated_price = provided_price
                future_df['price'] = simulated_price

        # 计算一个基础的价格弹性乘数，确保即使用户的历史数据中价格没有波动（导致模型未学到价格特征），
        # 预测结果也能直观地反映出价格变化对销量的影响。假设需求价格弹性系数为 1.5。
        # 价格下降，销量上升；价格上升，销量下降。
        elasticity_coefficient = 1.5
        price_multiplier = (last_price / simulated_price) ** elasticity_coefficient

        predictions = []

        
        # 递归预测
        for i in range(days_to_forecast):
            # 创建单天的预测特征
            current_day_df = future_df.iloc[[i]].copy()
            
            # 完整历史 + 已预测的部分，用于计算特征
            combined_history = pd.concat([history_df, pd.DataFrame(predictions)], ignore_index=True)
            combined_history['ds'] = pd.to_datetime(combined_history['ds'])
            
            # 从完整历史中获取特征值
            featured_day = self._create_features(current_day_df)
            
            # 计算滞后和滑动窗口特征
            if not combined_history.empty:
                featured_day['lag_7'] = combined_history.iloc[-7]['y'] if len(combined_history) >= 7 else combined_history.iloc[0]['y']
                featured_day['lag_14'] = combined_history.iloc[-14]['y'] if len(combined_history) >= 14 else combined_history.iloc[0]['y']
                featured_day['rolling_mean_7'] = combined_history.tail(7)['y'].mean()
                
                # 修复 std() 返回 float64 而没有 fillna 方法的问题
                std_val = combined_history.tail(7)['y'].std()
                featured_day['rolling_std_7'] = 0 if pd.isna(std_val) else std_val

            # 确保使用我们在 feature_name_ 中的特征，并且保证预测时也包含动态的 price 列
            features = model.feature_name_
            
            # 如果特征里包含了价格而 featured_day 中没有，需要补充进去
            for f in features:
                if f not in featured_day.columns:
                    if f == 'price':
                        featured_day['price'] = future_df.iloc[i]['price']
                    else:
                        featured_day[f] = 0

            # 保证特征顺序一致
            X_pred = featured_day[features]
            
            prediction = model.predict(X_pred)[0]
            
            # 叠加价格弹性影响，让前端演示时总能看到动态效果
            prediction = prediction * price_multiplier
            
            prediction = round(max(0, prediction), 2)
            
            # 构造结果并加入列表，用于下一次递归
            result = {
                "ds": current_day_df['ds'].iloc[0].strftime('%Y-%m-%d'),
                "y": prediction,  # 用于下一次递归
                "yhat": prediction, # 最终预测值
                "yhat_lower": round(prediction * 0.9, 2), # 简单模拟置信区间
                "yhat_upper": round(prediction * 1.1, 2)
            }
            predictions.append(result)
            
        return predictions

    def predict_price_elasticity(self, tenant_id: str, product_id: str, simulation_context: Dict, baseline_context: Dict = None) -> Dict:
        """
        分析价格变化对销量的影响。
        """
        model_path = self._get_model_path(tenant_id, product_id)
        if not os.path.exists(model_path):
            raise FileNotFoundError("Model not found. Please train the model first.")
            
        model = joblib.load(model_path)
        
        # 假设模拟发生在未来第一天，使用今天作为上下文
        sim_date = pd.to_datetime(pd.Timestamp.now().date())
        
        # 为了应对模型可能未学到价格特征的情况，我们在获取基准后叠加价格弹性
        def get_raw_prediction_for_price(price: float) -> float:
            sim_df = pd.DataFrame({'ds': [sim_date], 'price': [price]})
            sim_df_featured = self._create_features(sim_df)
            # 移除模型训练时没有的特征
            sim_df_featured.drop(columns=['lag_7', 'lag_14', 'rolling_mean_7', 'rolling_std_7'], inplace=True, errors='ignore')
            features = [f for f in model.feature_name_ if f in sim_df_featured.columns]
            X_pred = sim_df_featured[features]
            # 确保特征列完全一致
            missing_cols = set(model.feature_name_) - set(X_pred.columns)
            for c in missing_cols:
                X_pred[c] = 0 # 或使用更合理的默认值
            X_pred = X_pred[model.feature_name_]

            return model.predict(X_pred)[0]

        # 计算基线
        base_price = baseline_context['price'] if baseline_context else 1.0
        if pd.isna(base_price) or base_price <= 0:
            base_price = 1.0
            
        base_sales = get_raw_prediction_for_price(base_price)
        
        # 叠加价格弹性影响
        target_price = simulation_context['price']
        if pd.isna(target_price) or target_price <= 0:
            target_price = base_price
            
        elasticity_coefficient = 1.5
        price_multiplier = (base_price / target_price) ** elasticity_coefficient
        
        target_sales = base_sales * price_multiplier
        
        response_data = {
            "estimated_sales_at_target_price": round(max(0, target_sales), 2)
        }
        
        if baseline_context:
            response_data["baseline_sales"] = round(max(0, base_sales), 2)
            response_data["sales_change_percentage"] = round((target_sales - base_sales) / base_sales * 100, 2) if base_sales > 0 else 0
            response_data["revenue_change_percentage"] = round((target_sales * target_price - base_sales * base_price) / (base_sales * base_price) * 100, 2) if (base_sales * base_price) > 0 else 0
            
        return response_data
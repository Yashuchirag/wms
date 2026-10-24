"""End-to-end training entrypoint: load -> feature engineer -> split -> train -> evaluate -> log.

Usage:
    python -m forecasting.train
"""

from __future__ import annotations

import mlflow
import pandas as pd

from forecasting.data import load_sales_data
from forecasting.evaluate import score
from forecasting.features import build_features
from forecasting.models import linear, xgboost_model
from forecasting.split import walk_forward_split

TARGET = "demand"
DROP_COLS = ["date", "product_id", "store_id", "units_sold", TARGET]


def get_feature_matrix(df: pd.DataFrame) -> tuple[pd.DataFrame, pd.Series]:
    X = df.drop(columns=[c for c in DROP_COLS if c in df.columns])
    y = df[TARGET]
    return X, y


def main() -> None:
    mlflow.set_experiment("wms-demand-forecasting")

    raw = load_sales_data()
    featured = build_features(raw, target_col=TARGET).dropna().reset_index(drop=True)

    train_df, test_df = walk_forward_split(featured, date_col="date", test_weeks=8)
    X_train, y_train = get_feature_matrix(train_df)
    X_test, y_test = get_feature_matrix(test_df)

    with mlflow.start_run(run_name="baseline_linear_regression"):
        linear_model = linear.fit(X_train, y_train)
        linear_preds = linear_model.predict(X_test)
        linear_metrics = score(y_test, linear_preds)
        mlflow.log_params({"model_type": "LinearRegression"})
        mlflow.log_metrics(linear_metrics)
        print("Linear Regression:", linear_metrics)

    with mlflow.start_run(run_name="xgboost"):
        xgb_model = xgboost_model.fit(X_train, y_train)
        xgb_preds = xgb_model.predict(X_test)
        xgb_metrics = score(y_test, xgb_preds)
        mlflow.log_params({"model_type": "XGBRegressor", **xgb_model.get_params()})
        mlflow.log_metrics(xgb_metrics)
        print("XGBoost:", xgb_metrics)

    mape_improvement = (
        (linear_metrics["mape"] - xgb_metrics["mape"]) / linear_metrics["mape"] * 100
    )
    print(f"MAPE improvement over baseline: {mape_improvement:.2f}%")


if __name__ == "__main__":
    main()

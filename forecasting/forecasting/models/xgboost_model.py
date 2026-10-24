"""XGBoost model for demand forecasting, tuned with time-series-aware CV."""

from __future__ import annotations

import pandas as pd
from sklearn.model_selection import RandomizedSearchCV, TimeSeriesSplit
from xgboost import XGBRegressor

PARAM_DISTRIBUTIONS = {
    "n_estimators": [200, 300, 500, 700],
    "max_depth": [3, 4, 5, 6, 8, 10],
    "learning_rate": [0.01, 0.03, 0.05, 0.1, 0.15],
    "subsample": [0.6, 0.8, 1.0],
    "colsample_bytree": [0.6, 0.8, 1.0],
    "min_child_weight": [1, 3, 5, 7],
    "gamma": [0, 0.1, 0.5, 1],
    "reg_alpha": [0, 0.1, 1],
    "reg_lambda": [0.5, 1, 2, 5],
}


def build_model(n_jobs: int = -1) -> XGBRegressor:
    return XGBRegressor(
        objective="reg:squarederror",
        random_state=42,
        n_jobs=n_jobs,
    )


def fit(
    X_train: pd.DataFrame,
    y_train: pd.Series,
    n_iter: int = 15,
    n_splits: int = 3,
) -> XGBRegressor:
    search = RandomizedSearchCV(
        estimator=build_model(n_jobs=1),
        param_distributions=PARAM_DISTRIBUTIONS,
        n_iter=n_iter,
        cv=TimeSeriesSplit(n_splits=n_splits),
        scoring="neg_mean_absolute_percentage_error",
        random_state=42,
        n_jobs=-1,
    )
    search.fit(X_train, y_train)
    return search.best_estimator_

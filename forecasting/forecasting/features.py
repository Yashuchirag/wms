"""Feature engineering for SKU-level demand forecasting.

All lag/rolling features are computed per (product_id, store_id) group and
shifted so that no row sees information from its own or a future date.
"""

from __future__ import annotations

import pandas as pd

GROUP_KEYS = ["product_id", "store_id"]
LAGS = (1, 7, 14, 28)
ROLLING_WINDOWS = (7, 28)

CATEGORICAL_COLS = ["category", "region", "weather_condition", "seasonality"]
PASSTHROUGH_COLS = ["promotion", "discount", "competitor_pricing", "epidemic", "price"]


def add_lag_features(df: pd.DataFrame, target_col: str = "demand") -> pd.DataFrame:
    df = df.copy()
    grouped = df.groupby(GROUP_KEYS, observed=True)[target_col]
    for lag in LAGS:
        df[f"{target_col}_lag_{lag}"] = grouped.shift(lag)
    return df


def add_rolling_features(df: pd.DataFrame, target_col: str = "demand") -> pd.DataFrame:
    df = df.copy()
    shifted = df.groupby(GROUP_KEYS, observed=True)[target_col].shift(1)
    for window in ROLLING_WINDOWS:
        df[f"{target_col}_rollmean_{window}"] = shifted.groupby(
            [df[k] for k in GROUP_KEYS], observed=True
        ).transform(lambda s: s.rolling(window, min_periods=1).mean())
        df[f"{target_col}_rollstd_{window}"] = shifted.groupby(
            [df[k] for k in GROUP_KEYS], observed=True
        ).transform(lambda s: s.rolling(window, min_periods=2).std())
    return df


def add_calendar_features(df: pd.DataFrame, date_col: str = "date") -> pd.DataFrame:
    df = df.copy()
    df["day_of_week"] = df[date_col].dt.dayofweek
    df["month"] = df[date_col].dt.month
    df["is_weekend"] = df["day_of_week"].isin([5, 6]).astype("int8")
    return df


def build_features(df: pd.DataFrame, target_col: str = "demand") -> pd.DataFrame:
    df = add_lag_features(df, target_col)
    df = add_rolling_features(df, target_col)
    df = add_calendar_features(df)
    df = pd.get_dummies(df, columns=CATEGORICAL_COLS, dummy_na=False)
    return df

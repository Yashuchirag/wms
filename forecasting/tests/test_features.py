import numpy as np
import pandas as pd

from forecasting.features import add_calendar_features, add_lag_features, add_rolling_features


def _toy_df() -> pd.DataFrame:
    dates = pd.date_range("2023-01-01", periods=10, freq="D")
    return pd.DataFrame(
        {
            "date": list(dates) + list(dates),
            "product_id": ["A"] * 10 + ["B"] * 10,
            "store_id": ["S1"] * 20,
            "demand": list(range(10)) + list(range(100, 110)),
        }
    )


def test_lag_features_no_leakage_and_group_isolation():
    df = _toy_df()
    out = add_lag_features(df, target_col="demand")

    a = out[out["product_id"] == "A"].reset_index(drop=True)
    assert np.isnan(a.loc[0, "demand_lag_1"])
    assert a.loc[1, "demand_lag_1"] == 0

    b_first_lag = out[out["product_id"] == "B"].reset_index(drop=True).loc[0, "demand_lag_1"]
    assert np.isnan(b_first_lag)


def test_rolling_features_only_use_past_values():
    df = _toy_df()
    out = add_rolling_features(df, target_col="demand")

    a = out[out["product_id"] == "A"].reset_index(drop=True)
    assert np.isnan(a.loc[0, "demand_rollmean_7"])
    assert a.loc[1, "demand_rollmean_7"] == 0
    assert a.loc[2, "demand_rollmean_7"] == 0.5


def test_calendar_features_flag_weekend():
    df = _toy_df()
    out = add_calendar_features(df, date_col="date")
    saturday = out[out["date"] == "2023-01-07"]
    assert (saturday["is_weekend"] == 1).all()

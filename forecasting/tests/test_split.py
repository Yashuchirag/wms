import pandas as pd

from forecasting.split import walk_forward_split


def test_walk_forward_split_no_temporal_overlap():
    dates = pd.date_range("2023-01-01", periods=100, freq="D")
    df = pd.DataFrame({"date": dates, "value": range(100)})

    train, test = walk_forward_split(df, date_col="date", test_weeks=2)

    assert train["date"].max() < test["date"].min()
    assert len(train) + len(test) == len(df)
    assert (test["date"].max() - test["date"].min()).days == 13

"""Temporal train/test splitting. Never shuffle — this is time series data."""

from __future__ import annotations

import pandas as pd


def walk_forward_split(
    df: pd.DataFrame,
    date_col: str = "date",
    test_weeks: int = 8,
) -> tuple[pd.DataFrame, pd.DataFrame]:
    """Split on a date cutoff so all test rows occur strictly after all train rows."""
    cutoff = df[date_col].max() - pd.Timedelta(weeks=test_weeks)
    train = df[df[date_col] <= cutoff].copy()
    test = df[df[date_col] > cutoff].copy()
    return train, test

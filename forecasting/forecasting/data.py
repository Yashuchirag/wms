"""Raw data loading for the demand forecasting pipeline.

The only module allowed to touch forecasting/data/raw/ directly.
"""

from __future__ import annotations

import zipfile
from pathlib import Path

import pandas as pd

DATA_DIR = Path(__file__).resolve().parent.parent / "data" / "raw"
ARCHIVE_PATH = DATA_DIR / "archive.zip"
CSV_NAME = "sales_data.csv"

DTYPES = {
    "Store ID": "category",
    "Product ID": "category",
    "Category": "category",
    "Region": "category",
    "Weather Condition": "category",
    "Seasonality": "category",
    "Promotion": "int8",
    "Epidemic": "int8",
}


def load_sales_data(archive_path: Path = ARCHIVE_PATH) -> pd.DataFrame:
    """Load and lightly normalize the raw sales_data.csv from the Kaggle archive."""
    with zipfile.ZipFile(archive_path) as zf:
        with zf.open(CSV_NAME) as f:
            df = pd.read_csv(f, parse_dates=["Date"], dtype=DTYPES)

    df.columns = [c.strip().lower().replace(" ", "_") for c in df.columns]
    df = df.sort_values(["product_id", "store_id", "date"]).reset_index(drop=True)
    return df

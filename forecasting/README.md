# Demand Forecasting

SKU-level demand forecasting pipeline for the WMS. Predicts item-level demand using
historical order volume, seasonality, and promotional signals. Baseline Linear Regression
model followed by an XGBoost model.

Decoupled from the Java application: reads from the same schema conceptually, writes
predictions out for the app to consume, and is not built or deployed as part of the
Spring Boot service.

## Setup

```bash
cd forecasting
python -m venv .venv && source .venv/bin/activate
pip install -e ".[dev]"
```

## Data

See `data/README.md` for the dataset source and column mapping to the WMS schema.

## Structure

- `forecasting/` — feature engineering, training, evaluation, batch scoring
- `notebooks/` — EDA only, never imported by pipeline code
- `tests/` — unit tests for feature engineering and evaluation logic

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

## Running the pipeline

```bash
cd forecasting
python -m forecasting.train
```

This loads the raw data, builds lag/rolling/calendar features, does a walk-forward
temporal split (last 8 weeks held out), trains both models, and logs params/metrics
to MLflow (`mlflow.db`, gitignored). View runs with:

```bash
mlflow ui --backend-store-uri sqlite:///mlflow.db
```

## Results

On the held-out 8-week window:

| Model | MAPE | WAPE | RMSE |
| --- | --- | --- | --- |
| Linear Regression (baseline) | 35.21% | 22.54% | 29.50 |
| XGBoost (tuned) | 31.29% | 19.61% | 26.26 |

**MAPE improvement: 11.14%** over the linear baseline.

XGBoost was tuned with `RandomizedSearchCV` (15 iterations) over `TimeSeriesSplit`
(3 folds) — see `forecasting/models/xgboost_model.py` for the search space.

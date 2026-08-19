# Dataset

Source: [Retail Store Inventory and Demand Forecasting](https://www.kaggle.com/datasets/atomicd/retail-store-inventory-and-demand-forecasting) (Kaggle)

Download manually and place `archive.zip` (containing `sales_data.csv`) at `data/raw/archive.zip`.
This directory is gitignored, the raw data is never committed. `forecasting.data.load_sales_data()`
reads the CSV directly out of the zip, no extraction needed.

Actual columns: `Date, Store ID, Product ID, Category, Region, Inventory Level, Units Sold,
Units Ordered, Price, Discount, Weather Condition, Promotion, Competitor Pricing, Seasonality,
Epidemic, Demand`. 20 SKUs x 5 stores, daily granularity, 2022-01-01 to 2024-01-30, no missing
values. `Demand` is used as the forecast target (`Units Sold` is the realized/censored sales
figure, kept as a feature).

## Column mapping to WMS schema

| Dataset column      | WMS equivalent                     |
|----------------------|-------------------------------------|
| Product ID            | `Item.sku`                          |
| Category               | `Item.category`                     |
| Price                    | `Item.price`                        |
| Store ID / Region     | `Warehouse`                         |
| Inventory Level        | `Inventory.quantity`                |
| Units Sold / Ordered  | `Shipment.quantity` (OUTBOUND)      |
| Holiday/Promotion     | no current WMS equivalent           |
| Seasonality              | derived from `Shipment.createdAt`   |

# Dataset

Source: [Retail Store Inventory and Demand Forecasting](https://www.kaggle.com/datasets/atomicd/retail-store-inventory-and-demand-forecasting) (Kaggle)

Download manually and place the CSV at `data/raw/`. This directory is gitignored, the raw
data is never committed.

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

# PROJECT REFERENCE

## Alcohol Inventory Workflow

Products → Alcohol Product Profile → Count Template → Print Count Sheet
→ Inventory Count Entry → Inventory Valuation → Reporting

### Count Methods

#### WEIGHT

Used for: - Liquor - Wine - Kegs

Manager enters: - Full Units - Weight

Application calculates:

Decimal Quantity = Full Units + ((Weight - Tare Weight) / Full Content
Weight)

Only the decimal quantity is stored.

#### EACH

Used for: - Bottled Beer - Coolers - Seltzers

Manager enters Quantity directly.

### Printing

Weighted products print: - Full - Weight

Each-count products print: - Quantity

Pagination is section-aware and matches the entry screen.

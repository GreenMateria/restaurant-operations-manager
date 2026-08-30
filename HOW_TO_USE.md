# ESM Operations Manager - Step By Step Manager Guide

_Training guide for new managers_

This guide is written for someone who has not used ESM Operations Manager before. Follow the steps in order. Do not skip ahead unless you already know that part of the workflow.

Screenshot placeholders are included throughout the guide. Add cropped screenshots under `docs/images/` using the filenames shown in each image link.

---

# Before You Start

## Open The Program

1. Click the Windows **Start** menu.
2. Search for **ESM Operations Manager**.
3. Open the program.
4. Wait until the main dashboard appears.

You should see the main dashboard with buttons for the main areas of the program.

![Main dashboard](docs/images/dashboard.png)

## Know The Main Buttons

The most common manager workflows are:

- **Food Department** - food count sheets, food counts, food valuation, and food order guide.
- **Alcohol Department** - alcohol count sheets, alcohol counts, alcohol invoices, alcohol order guide, and alcohol variance workflows.
- **Supplies Department** - supplies count sheets, supplies counts, supplies valuation, and supplies order guide.
- **Purchasing** - supplier invoices and purchase history.
- **Reports** - weekly cost reports and inventory valuation.
- **Production** - weekly production and prep sheets.
- **System** - backup, restore, cloud mode, and administrator tools.

## Important Safety Rules

1. Do not delete counts unless you are sure they are wrong and incomplete.
2. Do not restore a database unless you are sure the backup is the correct one.
3. Do not upload to cloud unless you are sure this PC has the correct data.
4. If something looks wrong, stop and ask before saving over existing work.

---

# FOOD Section

Use this section for regular food inventory work.

The normal food workflow is:

```text
Check products
-> Print count sheet
-> Count inventory
-> Enter count
-> Complete count
-> Enter invoices
-> Generate valuation or order guide
```

## FOOD 1 - Check Or Add Food Products

Use this when a food item is missing, named wrong, or has the wrong category.

1. From the dashboard, click **Food Department**.
2. Open the food product list or product management screen.
3. Click in the search box.
4. Search by SKU or product description.
5. Find the product in the table.

You should see the product row with its SKU, description, category, unit, pack size, and active status.

![Food product list](docs/images/food-product-list.png)

If the product already exists:

1. Select the product row.
2. Click **Edit**.
3. Check the description, category, reporting category, unit, pack size, and conversion factor.
4. Fix anything that is wrong.
5. Click **Save**.

If the product does not exist:

1. Click **Add Product**.
2. Enter the supplier SKU.
3. Enter a clear product description.
4. Choose the correct category.
5. Choose the correct reporting category, usually **FOOD**.
6. Enter the unit and pack information.
7. Click **Save**.

Before moving on, search for the product again and confirm it now appears correctly.

## FOOD 2 - Add A Food Product To The Count Sheet

Use this when a product exists but does not appear on the printed food count sheet.

1. From **Food Department**, open the count template screen.
2. Select the food count template.
3. Look through the sections to find where the item belongs.
4. Click **Add Product** or the equivalent add button.
5. Search for the product.
6. Select the product.
7. Choose the count unit that managers will physically count.
8. Set the section name if needed.
9. Set the display name if the printed name should be simpler.
10. Save the template line.
11. Save the template.

You should now see the product in the template list.

![Food count template](docs/images/food-count-template.png)

Important: if you already started a count before adding this item, the item will not automatically appear in that already-started count. For an incomplete count, delete the incomplete count, update the template, and start the count again.

## FOOD 3 - Print The Food Count Sheet

Use this before physically counting inventory.

1. From **Food Department**, open the food count area.
2. Select the food count template.
3. Choose **Print Count Sheet** or **Preview Count Sheet**.
4. Look through the preview.
5. Confirm the sections are in the correct order.
6. Confirm the products you need are on the sheet.
7. Print the count sheet.

You should have a paper count sheet grouped by section.

![Food count sheet preview](docs/images/print-count-sheet.png)

## FOOD 4 - Start A New Food Count

Use this after the physical count sheet has been printed or when you are ready to enter the count.

1. From **Food Department**, open the count entry screen.
2. Choose **New Count** or **Start Count**.
3. Select the food count template.
4. Enter the count date.
5. Enter the period start date.
6. Enter the period end date.
7. Click **Create** or **Start**.

You should now see a count entry screen with food products from the selected template.

![Start food count](docs/images/start-food-count.png)

Use the actual reporting period dates. Weekly reports depend on these dates.

## FOOD 5 - Enter Food Count Numbers

1. Open the active food count.
2. Start at the top of the paper count sheet.
3. Find the same item in the app.
4. Click the quantity field.
5. Type the counted quantity.
6. Move to the next item.
7. Continue until every counted item is entered.
8. Click **Save**.

After saving:

1. Scroll back through the count.
2. Look for blanks that should have numbers.
3. Look for numbers that seem much too high or too low.
4. Fix any mistakes.
5. Save again.

Do not mark the count complete until it has been checked.

## FOOD 6 - Complete A Food Count

Complete the count only when you are finished entering and reviewing.

1. Open the count.
2. Review the count one final time.
3. Click **Mark Complete** or **Complete Count**.
4. Confirm the action if the program asks.

The count is now ready for valuation, cost reporting, and order guide generation.

## FOOD 7 - Generate Food Inventory Valuation

Use this to see the dollar value of a completed food inventory count.

1. Open **Reports** or the food valuation screen.
2. Choose **Inventory Valuation**.
3. Select the completed food count.
4. Click **Generate**.
5. Review the item costs and category totals.
6. Print or save the report if needed.

![Food valuation](docs/images/food-valuation.png)

If a value looks wrong, check the product cost history and recent invoices.

## FOOD 8 - Generate The Food Order Guide

Use this after the opening and closing counts are complete.

1. Open **Food Department**.
2. Choose **Order Guide**.
3. Select the opening count.
4. Select the closing count.
5. Click **Generate**.

You should see food products grouped by the count template sections.

![Food order guide](docs/images/food-order-guide.png)

To fill out the order guide:

1. Review the opening quantity.
2. Review purchases.
3. Review the closing quantity.
4. Click into **Order 1** for the first order.
5. Type the quantity to order.
6. Click into **Order 2** for the second order if needed.
7. Type the second order quantity.
8. Review the case size.
9. Edit the **Case** column only if the ordering label should be clearer.
10. Print the order guide.

The printed order guide is designed to be compact and black-and-white.

---

# ALCOHOL Section

Use this section for beer, wine, liquor, draught, and import draught.

The normal alcohol workflow is:

```text
Check alcohol product profiles
-> Print alcohol count sheet
-> Count alcohol
-> Enter full units and weights or quantities
-> Complete count
-> Enter alcohol invoices
-> Generate alcohol valuation or order guide
```

## ALCOHOL 1 - Understand Alcohol Count Types

Alcohol items are counted in two main ways.

Weighted items:

- Liquor bottles
- Wine bottles
- Kegs
- Any item where partial containers are measured by weight

Each-count items:

- Bottled beer
- Cans
- Coolers
- Seltzers
- Any item counted as whole units

For weighted items, the program uses the product profile to calculate the decimal quantity from full units and partial weight.

## ALCOHOL 2 - Check An Alcohol Product Profile

Use this before counting if a bottle, keg, or alcohol item does not calculate correctly.

1. From the dashboard, click **Alcohol Department**.
2. Open the alcohol products or alcohol profile screen.
3. Search for the product.
4. Select the product.
5. Confirm the count method.
6. Confirm the tare weight.
7. Confirm the full content weight.
8. Confirm the container type.
9. Confirm the product is active.
10. Save if changes were made.

![Alcohol product profile](docs/images/alcohol-product-profile.png)

Managers should not type tare weight during every count. It should already be saved in the product profile.

## ALCOHOL 3 - Print The Alcohol Count Sheet

1. From **Alcohol Department**, open the alcohol count area.
2. Select the alcohol count template.
3. Choose **Preview Count Sheet** or **Print Count Sheet**.
4. Review the sections:
   - Beer
   - Wine
   - Liquor
   - Draught
   - Import Draught
5. Confirm weighted items show the right columns.
6. Confirm each-count items show the right quantity column.
7. Print the sheet.

![Alcohol count sheet](docs/images/alcohol-count-sheet.png)

## ALCOHOL 4 - Start A New Alcohol Count

1. From **Alcohol Department**, open count entry.
2. Choose **New Count** or **Start Count**.
3. Select the alcohol template.
4. Enter the count date.
5. Enter the period start date.
6. Enter the period end date.
7. Click **Create** or **Start**.

You should now see the alcohol count entry screen.

![Alcohol count](docs/images/alcohol-count.png)

## ALCOHOL 5 - Enter Alcohol Count Numbers

For bottled beer, cans, coolers, and other each-count items:

1. Find the item.
2. Click the quantity field.
3. Type the counted quantity.
4. Move to the next item.

For weighted items:

1. Find the item.
2. Enter the number of full units.
3. Enter the partial measured weight.
4. Check the calculated decimal quantity.
5. Move to the next item.

After entering all alcohol:

1. Save the count.
2. Review the total quantities.
3. Check anything that looks unusually high or low.
4. Correct mistakes.
5. Save again.

Alcohol count mistakes can create large cost percentage changes, so review carefully.

## ALCOHOL 6 - Complete The Alcohol Count

1. Open the alcohol count.
2. Review all sections.
3. Click **Mark Complete** or **Complete Count**.
4. Confirm if asked.

The count is now ready for alcohol valuation, weekly cost reporting, and order guide generation.

## ALCOHOL 7 - Enter An Alcohol Invoice

Alcohol invoices can include non-inventory charges such as HST, bottle deposits, keg deposits, and freight.

1. Open **Purchasing**.
2. Choose the alcohol manual invoice option.
3. Enter the supplier.
4. Enter the invoice number.
5. Enter the invoice date.
6. Add the alcohol merchandise lines.
7. For each line, enter quantity and cost.
8. Mark whether HST is included in the line total when needed.
9. Mark whether bottle deposit is included in the line total when needed.
10. Enter the exact paper HST total.
11. Enter the exact paper bottle deposit total.
12. Add any freight or other adjustments.
13. Compare the app total to the paper invoice total.
14. Save the invoice when the totals are correct.

![Alcohol manual invoice](docs/images/alcohol-manual-invoice.png)

Important: HST should match the paper invoice. Do not change HST just to make the invoice balance. The program is designed to reconcile remaining differences into merchandise categories.

## ALCOHOL 8 - Generate The Alcohol Order Guide

1. Open **Alcohol Department**.
2. Choose **Order Guide**.
3. Select the opening alcohol count.
4. Select the closing alcohol count.
5. Click **Generate**.
6. Review each alcohol section.
7. Enter **Order 1** quantities.
8. Enter **Order 2** quantities if needed.
9. Review case sizes.
10. Print the order guide.

![Alcohol order guide](docs/images/alcohol-order-guide.png)

## ALCOHOL 9 - Map POS Alcohol Items

Use Sales Mappings to connect sold POS alcohol items to the inventory product they consume. This is the setup step for alcohol variance reporting.

1. Open **Alcohol Department**.
2. Choose **Sales Mappings**.
3. Click **Add**.
4. Search the **POS Menu Item** dropdown and select the POS item.
5. Confirm the POS SKU / PLU and item name were filled in.
6. Select the alcohol inventory product.
7. Enter the quantity used per sale.
8. Choose the usage unit.
9. Save the mapping.

The POS Menu Item dropdown uses the same POS item catalog maintained in the Production area. If a POS item is missing from the dropdown, import or add it in **Production -> POS Menu Items** first.

## ALCOHOL 10 - Alcohol Variance Report

The Alcohol Department includes the Variance Report entry point for the next reporting step. The current development build has the setup shell in place; final variance calculation/output is still pending before stable release.

---

# SUPPLIES Section

Use this section for paper, take out, cleaning, dishwashing, guest supplies, and other supplies.

The normal supplies workflow is:

```text
Check supplies products
-> Print supplies count sheet
-> Count supplies
-> Enter count
-> Complete count
-> Enter invoices
-> Generate supplies valuation or order guide
```

## SUPPLIES 1 - Check Or Add Supplies Products

1. From the dashboard, click **Supplies Department**.
2. Open the supplies product list.
3. Search by SKU or description.
4. Select the product if it exists.
5. Confirm the reporting category:
   - PAPER
   - TAKE OUT
   - CLEANING
   - DISHWASHING
   - GUEST SUPPLIES
   - OTHER
6. Edit and save if needed.

![Supplies product list](docs/images/supplies-product-list.png)

If the product is missing:

1. Click **Add Product**.
2. Enter SKU, description, category, unit, and pack information.
3. Choose the correct supplies reporting category.
4. Click **Save**.

## SUPPLIES 2 - Add A Supplies Product To The Count Sheet

1. From **Supplies Department**, open the count template screen.
2. Select the supplies template.
3. Click **Add Product**.
4. Search for the supply item.
5. Select it.
6. Choose the count unit.
7. Choose the section.
8. Save the template line.
9. Save the template.

![Supplies count template](docs/images/supplies-count-template.png)

## SUPPLIES 3 - Print The Supplies Count Sheet

1. Open **Supplies Department**.
2. Open the supplies count area.
3. Select the supplies template.
4. Choose **Preview Count Sheet** or **Print Count Sheet**.
5. Confirm all sections and products are shown.
6. Print.

![Supplies count sheet](docs/images/supplies-count-sheet.png)

## SUPPLIES 4 - Start And Enter A Supplies Count

1. Open the supplies count entry screen.
2. Choose **New Count** or **Start Count**.
3. Select the supplies template.
4. Enter count date.
5. Enter period start date.
6. Enter period end date.
7. Start the count.
8. Enter counted quantities from the paper count sheet.
9. Save.
10. Review for blanks or mistakes.
11. Save again if changes were made.
12. Mark complete.

![Supplies count entry](docs/images/supplies-count-entry.png)

## SUPPLIES 5 - Generate The Supplies Order Guide

1. Open **Supplies Department**.
2. Choose **Order Guide**.
3. Select the opening supplies count.
4. Select the closing supplies count.
5. Click **Generate**.
6. Enter order quantities.
7. Print the guide.

![Supplies order guide](docs/images/supplies-order-guide.png)

Supplies are usually compared against total revenue in reporting.

---

# Purchasing Section

Use purchasing for food, alcohol, and supplies invoices. Invoices keep product costs current.

## PURCHASING 1 - Import A GFS Invoice

1. From the dashboard, click **Purchasing**.
2. Click **Import Invoice**.
3. Select the GFS invoice CSV file.
4. Wait for the invoice preview to load.
5. Review the invoice number.
6. Review the invoice date.
7. Review the supplier.
8. Scroll through the invoice lines.

![Import invoice](docs/images/import-invoice.png)

If every line is matched:

1. Compare the app total to the paper or supplier invoice total.
2. Review the category breakdown.
3. Click **Save Invoice**.

If there are unknown SKUs:

1. Click the unknown line.
2. Search for the matching product.
3. Link the supplier SKU to the product.
4. Save the alias or mapping.
5. Confirm the line now shows the correct product.
6. Continue until no required unknown SKUs remain.
7. Save the invoice.

## PURCHASING 2 - Enter A Manual Invoice

1. From the dashboard, click **Purchasing**.
2. Choose **Manual Invoice**.
3. Enter the supplier.
4. Enter the invoice number.
5. Enter the invoice date.
6. Click **Add Line**.
7. Search for the product.
8. Select the product.
9. Enter the quantity.
10. Enter the cost.
11. Save the line.
12. Repeat for each invoice line.
13. Add freight, HST, deposits, or other adjustments if needed.
14. Compare totals to the paper invoice.
15. Click **Save Invoice**.

![Manual invoice](docs/images/manual-invoice.png)

## PURCHASING 3 - Check Invoice History

1. Open **Purchasing**.
2. Click **Invoice History**.
3. Search by invoice number, supplier, or date.
4. Select the invoice.
5. Review line items.
6. Review adjustments.

![Invoice history](docs/images/invoice-history.png)

Only delete an invoice if you are sure it should not be in the system.

---

# Weekly Cost Report Section

Use this after counts, invoices, and sales are entered.

## REPORT 1 - Generate A Weekly Cost Report

1. From the dashboard, open **Reports**.
2. Choose **Weekly Cost Report**.
3. Select the department:
   - Food
   - Alcohol
   - Supplies
4. Select the opening count.
5. Select the closing count.
6. Confirm the sales period.
7. Click **Generate**.

![Weekly cost report](docs/images/weekly-cost-report.png)

Review:

1. Opening inventory.
2. Purchases.
3. Closing inventory.
4. Usage.
5. Sales.
6. Cost percentage.

If the report looks wrong, check the counts, dates, sales, invoices, duplicate invoices, and product categories.

---

# GENERATING A PRODUCTION WEEK

Use this section to create weekly prep sheets from the POS usage report.

The normal production workflow is:

```text
Open Weekly Production
-> Import POS usage report
-> Generate the week
-> Review Monday through Sunday
-> Enter overrides
-> Save overrides
-> Preview prep sheets
-> Print prep sheets
```

## PRODUCTION 1 - Open Weekly Production

1. From the dashboard, click **Production**.
2. Click **Weekly Production**.
3. Wait for the weekly production screen to load.

You should see a week selector, import controls, day tabs, and a production table.

![Weekly production](docs/images/weekly-production.png)

## PRODUCTION 2 - Import The POS Usage Report

1. On the Weekly Production screen, click the POS usage import button.
2. Choose the POS usage `.xlsx` file.
3. Confirm the selected file is the correct week.
4. Continue the import.
5. Wait for the program to finish processing.

The program uses POS menu items and production profiles to turn sales into prep quantities.

![POS usage import](docs/images/pos-usage-import.png)

## PRODUCTION 3 - Select Or Confirm The Week

1. Look at the selected week.
2. Confirm the week start and end dates are correct.
3. If the wrong week is selected, choose the correct week from the week selector.

Do not print prep sheets until the correct week is selected.

## PRODUCTION 4 - Review Each Day

1. Click **Monday**.
2. Review the production rows.
3. Click **Tuesday**.
4. Review the production rows.
5. Continue through Wednesday, Thursday, Friday, Saturday, and Sunday.

For each day, check:

1. Items are assigned to the correct station.
2. Generated PAR values look reasonable.
3. Fixed prep items are present if needed.
4. Items that need manager judgment are reviewed.

![Production day tabs](docs/images/production-day-tabs.png)

## PRODUCTION 5 - Use Include All Active Production Items

Use **Include all active production items** when you want the sheet to show active prep items even if the import did not generate a quantity.

1. Check the **Include all active production items** option.
2. Wait for the table to refresh.
3. Review the added rows.
4. Leave it checked if those items should print.

This is useful for fixed daily prep items that still need to appear on the prep sheet.

## PRODUCTION 6 - Enter Override PAR Values

Use overrides when the generated number is not what the kitchen should prep.

1. Select the correct day tab.
2. Find the item.
3. Click the **Override PAR** field.
4. Type the manager-approved number.
5. Press Enter or click away from the field.
6. Repeat for other items.

The final prep amount uses the override when one is entered. If there is no override, it uses the generated amount.

![Production overrides](docs/images/production-overrides.png)

## PRODUCTION 7 - Save Overrides

1. After entering overrides, click **Save Overrides**.
2. Wait for the save to finish.
3. If the program shows a message, read it before continuing.

Do not print until overrides have been saved.

## PRODUCTION 8 - Preview A Prep Sheet

1. Find the prep sheet selector.
2. Choose the prep sheet you want:
   - Main Line
   - Pizza Salad
   - Any other configured prep sheet
3. Click **Preview Prep Sheet**.
4. Review the preview.

Check that:

1. The correct day is shown.
2. The correct prep sheet is shown.
3. Station headers are present.
4. Items are readable.
5. The right side of the sheet is not clipped.

![Prep sheet preview](docs/images/prep-sheet-preview.png)

## PRODUCTION 9 - Print Prep Sheets

1. Confirm the preview looks correct.
2. Click **Print Prep Sheet**.
3. Choose the printer.
4. Print the sheet.
5. Repeat for the other days or prep sheets needed.

Prep sheets include:

- Item
- Unit
- Shelf life
- Day
- Count
- To Do
- Initial

## PRODUCTION 10 - Use Freezer Pull

Freezer Pull is separate from Weekly Production.

1. From the dashboard, click **Production**.
2. Click **Freezer Pull**.
3. Enter quantities for Monday through Sunday.
4. Review the weekly total.
5. Save.
6. Print when ready.

![Freezer Pull](docs/images/freezer-pull.png)

Do not combine Freezer Pull with normal weekly production prep sheets.

---

# Backup And Cloud Mode

Most managers will not use this often, but they should understand the basics.

For normal daily use on the configured work PCs, the program should run in **Cloud Mode**. In Cloud Mode, normal saves go directly to the shared AWS RDS PostgreSQL database, so separate PCs use the same current data.

## Local Backup

1. Open **System**.
2. Enter the system password if asked.
3. Click **Backup**.
4. Choose where to save the backup.
5. Confirm the backup completed.

## Cloud Mode Reminder

Use **Cloud Mode** on every configured work PC when the cloud database has the correct current records.

1. Open **System**.
2. Click **Test Cloud Connection**.
3. Click **Use Cloud Mode**.
4. Restart the program.
5. Reopen **System** and confirm the current mode is Cloud PostgreSQL.

Users may need to reopen a screen to see changes saved from another PC. Avoid editing the same record on two PCs at the same time.

## Cloud Upload Reminder

Use this order only when intentionally moving local SQLite data to cloud:

1. Start in SQLite mode on the PC with the correct current data.
2. Open **System**.
3. Click **Test Cloud Connection**.
4. Click **Upload This PC to Cloud**.
5. Switch to **Cloud Mode**.
6. Restart the program.
7. Confirm the data is correct in cloud mode.

![System database sync](docs/images/system-database-sync.png)

Uploading replaces the cloud database with this PC's local SQLite data. Do not upload during normal daily cloud operation unless this PC is known to have the correct master data.

---

# Quick Troubleshooting

## A Product Is Missing From A Count

1. Check if the product exists.
2. Check if the product is active.
3. Check if the product is on the correct count template.
4. If the count was already started, delete the incomplete count and start it again after fixing the template.

## An Invoice Line Is Unknown

1. Search for the correct product.
2. Link the supplier SKU to that product.
3. Save the alias.
4. Confirm the line is now matched.

## A Cost Report Looks Wrong

1. Check opening count.
2. Check closing count.
3. Check count period dates.
4. Check sales period.
5. Check missing invoices.
6. Check duplicate invoices.
7. Check product reporting categories.

## The Wrong Database Mode Is Active

1. Open **System**.
2. Check database mode.
3. Choose SQLite or Cloud mode.
4. Restart the program.

Mode changes do not take effect until restart.

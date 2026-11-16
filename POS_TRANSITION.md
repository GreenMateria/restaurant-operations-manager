# POS Transition

_Updated November 15, 2026. The dev source imports legacy and new POS usage through one Weekly Production action. The manager reports that the implemented workflow has been tested and works properly. The desktop update is being held for a larger release._

## Context and current status

- StoreOps Manager is a multi-store application. Stores will move from the legacy POS to a new POS at different times, and a store may need reports from both systems during its changeover.
- Version 5.0.0 is the latest stable desktop release. The transition desktop changes are dev-only and have not been published to work PCs.
- The legacy POS exports numbered PLUs. The new POS does not use numbered PLUs, and a master menu-item list is not available to the manager.
- The manager can export a new-POS **Menu Item Quantity Sold by Weekday** workbook. Its item names may therefore need to supply the new-POS catalog incrementally.
- Weekly Production detects the new POS weekday header and routes that workbook to the new POS parser; other usage workbooks continue through the legacy PLU parser. The legacy POS Catalog importer still rejects new POS weekday workbooks, and the legacy production parser requires active PLU matches.
- The dev New POS Catalog can discover candidate names from a weekday report for manager review, store them separately from legacy PLUs, and assign existing production profiles. The profile picker supports typing to search. Candidate discovery uses an item row followed immediately by an exact day-part row, so names without breakdowns may need manual entry. The new catalog API routes were deployed October 29, and the manager confirmed catalog imports and profile assignments work through the dev app. Schema 25 is therefore operational, though the exact live `schema_version` value has not been queried independently.
- **Import Usage Report** now runs the formerly read-only new POS preview parser and production calculation, then saves the generated period. The separate preview button was removed. The parser reads weekday columns and `Selected Week` dates, selects item rows followed by exact day-part rows, collapses identical repeats, and excludes unmapped or invalid items. A conflict or daily/Week to Date mismatch on an active mapped item stops the import and names the item. No mapped production lines also stops the import. The report's actual seven consecutive dates define the production period, including rolling weeks that start on another weekday; desktop SQLite and API source use each date's weekday for day labels and quantities.
- The available `Menu Item Quantity Sold by Weekday (6).xlsx` covers October 27 to November 2, 2026. It has 542 candidate names and two conflicting repeated names, `No thank you` and `prepaid`. The manager reported that import succeeded after unmapped conflicts were allowed to remain excluded. This is a functional smoke check, not a reconciliation of saved prep quantities or printed sheets.

## Agreed end state and release path

- Keep one Weekly Production workflow. Automatic report-format detection is implemented. A review of source, dates, and mappings before save is still a possible future improvement.
- Keep legacy PLUs and new-POS text names as separate catalog identities, with both able to reference the same production profile. Use a compact source switch or filter in catalog maintenance rather than duplicating top-level menus.
- Parse each source with its own rules. New POS reports currently save their own seven-day range. For a transition period, combining legacy and new POS quantities by date with overlap protection and source provenance remains future work.
- Preserve the existing legacy sales, production, alcohol, and KDS rules. The dev build now supports text-name alcohol mappings and reads mapped quantities from the new POS weekday workbook for Alcohol Variance. New-POS sales-category parsing remains future work. Validate the alcohol amounts and report period against a real completed week before live use.
- The manager reports that the implemented import and production workflow has been tested and works properly. Mixed-source overlap handling is a separate future feature and will need validation when implemented.
- Keep the tested desktop changes in the dev build until the planned larger update is ready to publish to work PCs.

## Existing application touchpoints

| Workflow | Current implementation | Transition concern |
| --- | --- | --- |
| Sales report import | `PosSalesImportService` reads fixed Excel rows and gross/net columns into `ImportedSalesSummary`. | A new sales layout needs its own parser and category reconciliation. |
| Weekly Production | `ProductionUsageReportImportService` reads PLUs from column B and daily quantities from fixed columns. `WeeklyProductionView` generates and saves a week from one imported report. | The new report has names instead of a PLU column, different weekday order, and extra subtotal/breakdown rows. Reimporting a production week currently replaces its generated data. |
| Alcohol Variance | Legacy reports use active alcohol-mapping PLUs throughout the sheet; new POS weekday reports use separately keyed text-name alcohol mappings. | Reconcile new POS item-level quantities and count periods with a completed real report before live use. |
| POS Catalog / PLUs | `PosMenuItemImportService` imports name from column A and PLU from column B; `pos_menu_items` currently keys imported items by `pos_sku` within a store. | New-POS catalog discovery must work from usage-report item names and must keep source identities separate from legacy PLUs. |

The desktop parses files before sending normal business data through the store-scoped Cloud API. Preserve the existing store isolation and historical records when changing import or mapping storage.

## Observations from the supplied new-POS workbook

Sample: `Menu Item Quantity Sold by Weekday (4).xlsx`, supplied October 27, 2026. The sample remains in the user's Downloads folder; attach it again in a future session if it is unavailable. These are observations from this one export, not a guarantee that every export has the same layout.

- The table header is `Name`, `Wednesday`, `Thursday`, `Friday`, `Saturday`, `Sunday`, `Monday`, `Tuesday`, `Week to Date`. The weekday columns are B through H and the total is I in this sample. Find columns by header text rather than fixed positions.
- The footer's `Selected Week` Excel dates decode to **Wednesday, October 21 through Tuesday, October 27, 2026**. The `Business Dates` field near the top shows October 27. The visible quantities are concentrated on Monday and Tuesday in the inspected rows; do not infer a Monday-to-Sunday reporting period from the file name.
- A menu-item total row is followed by one or more day-part rows. Observed day-part labels are `Breakfast`, `Lunch`, `Dinner`, `Late Night`, and `Snack`. For example, `AYCE Bcn Caesar` totals 122 and its following day-part rows add to 122. Importing both would double-count it.
- Match day-part labels by the entire trimmed name, case-insensitively. Do not skip item names merely because they contain a day-part word; `Lunch Pizza` and `Italian Dinner for Two` appear as item names.
- Category/subtotal rows such as `Appetizers` and `Beer Dom` are present in the same numeric table and must not be treated as menu items or added to usage.
- The worksheet repeats many item rows with the **same current-week quantities**. The cause of this repetition is not established. An exploratory rule that considered a non-day-part row an item when the next row was a day part found 790 candidate rows but only 395 distinct candidate names; 393 candidate names appeared more than once. This rule needs validation on additional exports. Do not simply sum all candidate rows or assume the repetition is the previous-eight-weeks comparison.
- The workbook includes zero-quantity item rows, but it has not been proven to contain every menu item. Treat discovered names as a growing catalog, not a certified master list.
- The worksheet does not provide a separate numbered PLU column. Some item names contain numbers in parentheses, but their meaning and stability have not been confirmed; do not extract them as IDs without evidence.

### Additional partial export inspected October 29, 2026

`Menu Item Quantity Sold by Weekday (5).xlsx` shows `Business Dates` October 29 and a `Selected Week` footer of **Friday, October 23 through Thursday, October 29, 2026**. Its table header starts at Excel row 34 and orders the days **Friday through Thursday**. This confirms the weekday order changes between exports; match columns by header and dates from the footer. The header appears again at row 963, with a second section of item rows. The cause of repeated items still needs validation. The sample has negative quantities (for example, a `Dinner` breakdown of -2 for `Add Homeloaf`), so quantity validation must allow signed values where report semantics support them. This is also a partial export; Wednesday and Thursday contain quantities, while earlier weekdays are zero. No production week should be generated from it without confirming the intended calendar dates and duplicate-row rules.

## Earlier design proposals (partly superseded by the current implementation)

1. Keep a **per-store preferred POS format**. On each import, detect the likely format from report headers, show the detected format, and allow the manager to correct it. This permits legacy and new reports in the same app and during a changeover week.
2. Use a separate parser for each POS/report type. Convert each into the application's existing sales totals or daily item quantities before downstream calculations. Keep legacy rules, including its PLU and KDS rules, specific to the legacy parser.
3. For new-POS usage, locate the weekday headers, read the report's date range, select item totals, skip exact day-part breakdowns and category subtotals, and detect repeated item records. Validate daily sums against `Week to Date` where the report semantics allow it. Reject or flag unfamiliar layouts instead of silently importing zeros or doubling quantities.
4. Map quantities by **weekday name and actual report date**, then verify the chosen production/reporting period. The sample's Wednesday-to-Tuesday POS week does not align with the application's Monday-to-Sunday production week. A Monday-generated export may still use Wednesday-to-Tuesday headings; obtain one to verify. Do not silently assign data across period boundaries.
5. Let the new-POS usage import discover menu names for the store's POS catalog. Preview new names and unmatched/ambiguous names before creating records or generating production. Reuse confirmed production-profile and alcohol-product mappings on subsequent imports.
6. Keep new-POS identities distinct from legacy PLUs. Prefer a stable text ID if any export supplies one. If names are the only available key, normalize case and surrounding/repeated whitespace for matching, preserve the displayed name, and require review when a name changes or is ambiguous. Do not automatically merge names by fuzzy similarity.
7. During a store's transition week, combine validated quantities from the two POS systems for the intended calendar dates **before** saving Weekly Production. Check overlap so the same day is not counted twice. The current save path replaces an existing generated week on reimport.
8. Show an import preview with source POS, store, report dates, daily totals, item count, skipped day-part/subtotal/repeated rows, unmatched names, and mapping changes. Save provenance sufficient to diagnose which source report produced a period's results; exact schema and retention policy remain to be designed.

These are design proposals, not approved implementation details. In particular, the sample does not yet establish a reliable structural marker for every category row or repeated section.

## Reports and information to collect

### Highest priority

1. **New-POS weekly usage report for a completed week**, exported with its full date range visible. Include the export options or a screenshot of the report filters. This tests weekday mapping, section repetition, totals, and whether the format differs from the partial/current-week sample.
2. **The same usage report generated on a Monday**, even if it covers a partial week. This tests whether the weekday column order and selected-week dates change with the export day.
3. **New-POS sales report used for food/alcohol cost reporting**, showing gross and net sales by category, date range, discounts, refunds/voids, tax, and any service charges. A matching legacy report for the same date range would help reconcile meanings and totals.
4. **A new-POS item or menu export with any text-based item ID**, if the system offers one even though it has no numbered PLUs. A true master list is not required; an item-level sales detail or another export with a stable identifier would help protect mappings when display names change.
5. **An overlap period with both legacy and new POS data**, if one exists. Include the exact dates and which system was live for each day; this is needed to verify mixed-system weekly production and reporting without double counting.

### Also useful

- Another new-POS usage export from a different store, if its menu, categories, or report settings differ.
- An alcohol item-level sales/usage export, particularly for draught sizes, wine pours, bottles, modifiers, combos, and items with similar names.
- Examples of renamed items, duplicate display names in different categories, items with zero sales, and items sold through modifiers or bundles.
- An export showing negative quantities, refunds, voids, comps, or cancelled items, plus an explanation of how these should affect production and alcohol variance.
- The POS vendor's definitions of `Week to Date`, `Business Dates`, business-day cutoff, and the configured first day of its reporting week.
- A list of any rows or categories managers intentionally exclude from prep and alcohol variance, such as non-production modifiers, payment entries, tips, or gift cards. Do not infer exclusions solely from names.
- A legacy usage report and POS catalog export from the same store, to compare current PLU mappings with new text names.

Redacted exports are sufficient if they retain headers, representative row structure, dates, quantities, and example item names. Do not remove the rows that illustrate subtotals, day parts, repeated sections, or exceptions.

## Questions to settle before implementation

- Does the new POS provide a stable item identifier in any report or API, even if it does not call it a PLU? If not, can two distinct items share the same displayed name?
- Are the repeated current-week item rows intentional report groupings, and what distinguishes one grouping from another?
- Can the new POS export an explicit Monday-to-Sunday date range, and what determines the start day shown in the weekday columns? The two partial exports used Wednesday-to-Tuesday and Friday-to-Thursday respectively.
- Should a transition week combine sales and usage from both systems by actual business date, and which report is authoritative if dates overlap?
- Should zero-sale names discovered in a usage report enter the catalog automatically, or only after manager review?
- Which modifier, payment, and non-menu rows should be eligible for production or alcohol mappings?

## When work resumes

Read this file alongside `PROJECT_REFERENCE.md` and the latest `RELEASE_HISTORY.md`, then inspect the current import services, UI, API routes, and schema before changing them. Validate the proposed row rules against additional exports, design source-aware store mappings, and test legacy and new imports side by side. Update the project reference and user-facing guidance when behavior actually changes.

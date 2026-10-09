# StoreOps Admin Tools

For the current technical architecture, schema, API, and infrastructure/recovery reference, see [PROJECT_REFERENCE.md](../PROJECT_REFERENCE.md). This guide covers operating the launcher.

Double-click **Admin Tools.cmd** in the project folder to open the Windows launcher.
Desktop installer publishing remains in `Release.ps1`, unchanged.

Click **Guide: what to do and when** for a scrollable in-app checklist covering desktop releases, API deployment, database changes, new/existing stores, credentials, and finishing tasks. Its text is maintained in `docs/ADMIN_QUICK_GUIDE.txt`.

## Everyday tasks

- **Deploy API (usual update)** builds and tests the API, then deploys using the existing `api/samconfig.toml`. Review deployment prompts in the task window.
- **Refresh stores** loads stores from AWS. Select a store by its name and code; the launcher supplies its numeric ID automatically.
- **Add a new store** asks for its code, name, and login username. The task window asks for the RDS administrator password and the new store password. Existing codes cannot be overwritten through this action.
- **Edit selected store** opens a form populated with the current details.
- **Reset selected store password** uses the selected store's existing details and asks for passwords in the task window. This resets the store login password, not the System or Labour Setup protected password.
- **Copy setup to selected store** lets you choose a source store by name. The launcher requires an empty target store, preserving existing setup entries. The script still requires typing COPY before proceeding. Operational history is not copied.
- **AWS infrastructure status** shows managed stack status, termination protection, and operational alarm states.

Each action opens an interactive PowerShell task window. Read the result there and close it when finished. Only one task can run from the launcher at a time. Refresh stores after adding or editing a store.

## Settings and history

Settings include the stack, AWS region, administrator username, and AWS/PostgreSQL tool paths. They are saved in the ignored `.admin/settings.json` folder within this checkout. Previous settings files are retained when you save changes. Activity entries append to `.admin/activity.txt`; the launcher displays the latest 100 entries without deleting older ones.

Passwords are prompted in the task window and are not saved by the launcher. It uses your existing AWS CLI sign-in and SAM configuration. The deployment scripts still require their usual prerequisites: AWS CLI, SAM CLI, Maven/Java, and PostgreSQL command-line tools for store administration. Store administration currently uses the existing developer-machine RDS access path.

**API guided configuration** runs SAM's existing guided deployment workflow. Use it when deployment settings need changing; usual deployments reuse saved settings. Its stack/region settings come from SAM configuration, while the launcher Settings form controls store administration and infrastructure status.

## Advanced maintenance

For AWS resource changes, choose **Plan infrastructure changes**, run it, and review the proposed actions in the task window. Then choose **Apply reviewed infrastructure changes** and type APPLY. Plans and previous configurations are retained. The deployment guard refuses removals or replacements; the narrowly reviewed positive-to-positive RDS backup-retention change is allowed under a database stack policy that independently prohibits replacement/deletion. Infrastructure definitions and the account/region manifest live under `infra/`.

Normal API updates still use **Deploy API (usual update)**. Their saved SAM configuration references the managed API secret, so you do not re-enter the application database password or API key. RDS administrator and store login passwords are still prompted for store administration.

The named maintenance picker exposes the existing schema migrations, RDS application-user maintenance, and legacy local password tools. These tasks do not run automatically during deployment. The store login schema foundation action can reset the selected store's login credentials; use it only for deliberate initial setup/repair. Legacy local password tools do not reset cloud store protected passwords.

The original scripts remain available. Direct `Copy-LocationSetup.ps1` use retains its previous replacement behavior unless `-RequireEmptyTarget` is supplied. Direct `Set-LocationCredentials.ps1` retains its create-or-reset behavior unless `-CreateOnly` is supplied.

## Offline verification

Run `powershell.exe -NoProfile -STA -ExecutionPolicy Bypass -File scripts\Test-AdminLauncher.ps1` to check the launcher and script integration using local AWS/PostgreSQL test doubles. No deployment or live database changes occur.

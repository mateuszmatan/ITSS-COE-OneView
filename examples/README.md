# Examples

Jenkinsfiles, REST calls and a rendered configuration for ITSS COE OneView (short: OneView), and screenshots of
its GUI. The API scripts run against a OneView started as in [Running locally](../README.md#running-locally); it
starts empty, so `api/create-product.sh` first adds the product the other scripts read, or the scripts take the
products you added in OneView. Every pipeline key in these files is a placeholder: take the real one from the
product page, or from `api/pipeline-key.sh`.

| File | What it shows |
|------|---------------|
| [jenkins/full-pipeline.Jenkinsfile](jenkins/full-pipeline.Jenkinsfile) | a service's full pipeline: `devSecOpsPipeline` with one key |
| [jenkins/security-pipeline.Jenkinsfile](jenkins/security-pipeline.Jenkinsfile) | a security pipeline: `devSecOpsSecurityPipeline` |
| [jenkins/extended-pipeline.Jenkinsfile](jenkins/extended-pipeline.Jenkinsfile) | an extended pipeline: `devSecOpsExtendedPipeline` |
| [jenkins/sast-pipeline.Jenkinsfile](jenkins/sast-pipeline.Jenkinsfile) | a SAST scanning pipeline: `devSecOpsSASTScanningPipeline` |
| [jenkins/nexus-iq-pipeline.Jenkinsfile](jenkins/nexus-iq-pipeline.Jenkinsfile) | a Nexus IQ GoldenFix pipeline: `devSecOpsNexusIqGoldenFixPipeline` |
| [jenkins/multi-service/gateway.Jenkinsfile](jenkins/multi-service/gateway.Jenkinsfile) | the full pipeline of Payments Hub `gateway` |
| [jenkins/multi-service/ledger.Jenkinsfile](jenkins/multi-service/ledger.Jenkinsfile) | the full pipeline of Payments Hub `ledger` |
| [jenkins/multi-service/payhub-full.Jenkinsfile](jenkins/multi-service/payhub-full.Jenkinsfile) | one full run that builds every Payments Hub service, `gateway` first |
| [jenkins/multi-service/orders-full.Jenkinsfile](jenkins/multi-service/orders-full.Jenkinsfile) | one full run of the product `ORDERSGRADLE`: the Liquibase service with database changes `orders-db` first, so its changes reach RD before `orders-app` |
| [api/list-departments.sh](api/list-departments.sh) | the departments (Business Services in the GUI) with their product, service and pipeline counts |
| [api/list-products.sh](api/list-products.sh) | the products, optionally filtered by a search text |
| [api/create-product.sh](api/create-product.sh) with [api/new-product.json](api/new-product.json) | a new product with two services, as the Self-service wizard creates it, one of them with a Node.js frontend build |
| [api/create-database-product.sh](api/create-database-product.sh) | the two products below, created with a Full pipeline for each service; the Library defaults must first allow their databases and name the Nexus repositories of the database packages. A product OneView refuses does not stop the other one |
| [api/new-database-product.json](api/new-database-product.json) | the product `ORDERSDB` with one Liquibase service with database changes, `orders-db`: a Liquibase changelog, a test database with utPLSQL tests, RD and QC |
| [api/new-mixed-product.json](api/new-mixed-product.json) | the product `ORDERSGRADLE`, one repository with a Gradle build and its Liquibase module: `orders-db` first, then the Gradle service `orders-app` with its Node.js frontend, which leaves `orders-db` out of its AppScan scope |
| [api/product-pipelines.sh](api/product-pipelines.sh) | the services of a product with their pipelines and active keys |
| [api/pipeline-key.sh](api/pipeline-key.sh) | the active key of one pipeline, found by product code, service and type |
| [api/rotate-key.sh](api/rotate-key.sh) | a new key for a pipeline |
| [api/revoke-key.sh](api/revoke-key.sh) | invalidating a pipeline's key with a reason |
| [api/dso-config.sh](api/dso-config.sh) | the configuration the library reads for a key, as JSON and as YAML |
| [api/import-config.sh](api/import-config.sh) | a `devsecops_config.yaml`, a `config.yaml` or a rendered configuration read back into new service forms |
| [api/monitoring.sh](api/monitoring.sh) | the monitoring status, every product, all pipelines together, one product and one pipeline |
| [config/payhub-gateway-full.json](config/payhub-gateway-full.json), [.yaml](config/payhub-gateway-full.yaml) | the configuration of the full pipeline of the sample product Payments Hub, service `gateway` |
| [config/orders-db-full.json](config/orders-db-full.json), [.yaml](config/orders-db-full.yaml) | the configuration of the full pipeline of the Liquibase service with database changes `orders-db` of `ORDERSDB` |

## Jenkinsfiles

Each file is the whole Jenkinsfile of one Jenkins job, in the form the Self-service wizard and the product page
generate: the `@Library` line loads the shared library named in the Library defaults (`platform.jenkinsLibrary`,
`DevSecOpsJenkinsLibrary` by default), and the entry point of the pipeline type receives the pipeline key as a string.
Nothing else is in the file: no `devsecops_config.yaml`, no credentials ID and no tool server. The library reads
everything else from OneView by the key, at the address in the Jenkins variable `ONEVIEW_URL` (for example
`https://oneview.apps.bbh.com`), as described in
[How DSOEnhanced reads its configuration](../README.md#how-dsoenhanced-reads-its-configuration). A Jenkins that
still sets `DSO_PORTAL_URL`, the former name of the variable, runs these files unchanged. During the cutover,
pin the version of the library with the portal in the `@Library` line and in the Library defaults, for example
`DevSecOpsJenkinsLibrary@main`.

Save a file as `Jenkinsfile` in the top folder of the service's Bitbucket repository and point a Pipeline job (Pipeline
script from SCM) at it. A service with pipelines of several types has one job per type, and the Jenkinsfile of each
job passes the key of its own pipeline. The service template names these jobs `DevSecOps/<CODE>/<service>-<type>`,
for example `DevSecOps/PAYHUB/gateway-full`.

- `full-pipeline.Jenkinsfile` runs `devSecOpsPipeline`: build, scans, tests, deployment to RD and QC, and release.
- `security-pipeline.Jenkinsfile` runs `devSecOpsSecurityPipeline`: build and security scans; when the pipeline names
  an extended pipeline job in OneView, it starts that job.
- `extended-pipeline.Jenkinsfile` runs `devSecOpsExtendedPipeline`: the deployment and tests that the security
  pipeline starts. It reads the security run's state, so the first extended run after the cutover needs one security
  build made by the library with the portal.
- `sast-pipeline.Jenkinsfile` runs `devSecOpsSASTScanningPipeline`: an AppScan static scan of the sources only, with
  nothing built or deployed.
- `nexus-iq-pipeline.Jenkinsfile` runs `devSecOpsNexusIqGoldenFixPipeline`: it checks out the sources, builds each
  service so that the Nexus IQ scan patterns find the built artifacts, and scans the dependencies with Nexus IQ. On a
  policy violation GoldenFix raises the golden pull request with the upgrades in the service's Bitbucket repository.
  Nothing is deployed. The service template names these jobs `DevSecOps/<CODE>/<service>-nexusiq`, for example
  `DevSecOps/PAYHUB/gateway-nexusiq`.
- `multi-service/gateway.Jenkinsfile` and `multi-service/ledger.Jenkinsfile` are the full pipelines of two services of
  the sample product Payments Hub, each in its own repository with its own key. The key in `gateway.Jenkinsfile`
  (`00000000-0000-4000-8000-000000000000`) stands for the pipeline whose configuration `config/` shows.
- `multi-service/payhub-full.Jenkinsfile` is the Jenkinsfile the product page offers as "Jenkinsfile for several
  services": `pipelineKeys` lists the keys of the full pipelines of `gateway`, `ledger`, `notifications` and
  `mobile-app`, the primary service first. One run builds them all. Extended pipelines join such a run only when they
  name the same security pipeline.
- `multi-service/orders-full.Jenkinsfile` is the same form for the two services of `ORDERSGRADLE`, which share one
  repository and one Jenkins job: the product page shows it under "Show the Jenkinsfile", see
  `screenshots/v19/08-admin-product-shared-jenkinsfile.png`.

The keys stop working when they are invalidated or replaced: the next build of the job is refused with 403 and the
Jenkinsfile needs the pipeline's new key.

## API scripts

The scripts need bash, `curl` 7.76 or later and `jq`. Each one reads the address of OneView from `ONEVIEW_URL`
(default `http://localhost:8080`; for a deployed OneView for example `https://oneview.apps.bbh.com`) and prints the
answer through `jq` (YAML as it comes). `PORTAL_URL`, the former name of the variable, is read while `ONEVIEW_URL`
is not set. A slash at the end of the address is left out, as the library does with the Jenkins variable, so
`http://localhost:8080/` works too. When OneView refuses a request, the script prints the problem detail it sent and
exits with a non-zero status.

```bash
export ONEVIEW_URL=http://localhost:8080
examples/api/list-departments.sh
examples/api/create-product.sh
examples/api/list-products.sh TRADEARCHIVE
PRODUCT_ID=$(examples/api/list-products.sh TRADEARCHIVE | jq '.[0].id')
examples/api/product-pipelines.sh "$PRODUCT_ID"
KEY=$(examples/api/pipeline-key.sh TRADEARCHIVE archive-api FULL)
examples/api/dso-config.sh "$KEY"
```

- `list-departments.sh` calls `GET /api/departments`.
- `list-products.sh [SEARCH]` calls `GET /api/products?search=`; the search also matches the department name.
- `create-product.sh [DEPARTMENT]` looks up the department's ID by name (`Custody` by default), puts it into
  `new-product.json` and posts that to `POST /api/products?pipelineType=FULL`, as the Self-service wizard does after the
  choice of a full pipeline. The body is the product Trade Archive with `archive-api`, a Maven build on OpenShift with
  the RD and QC targets the wizard derives from the OpenShift project `cus-archive`, and `archive-gui`, a Gradle build
  that the UrbanCode Deploy application `TRADEARCHIVE-GUI` deploys to virtual machines. `archive-gui` builds its
  frontend in the folder `frontend` with Node.js before Gradle (`nodeBuild`, stage 0, with the default commands
  `npm ci` and `npm run build`) and lists its version catalog `gradle/libs.versions.toml` in `versionCatalogs`.
  GoldenFix finds that file by itself; listing it adds a run note when the file moves. The body carries the values the
  wizard fills in, including the Bitbucket repository, its credentials ID and the Nexus IQ application that the
  service template derives from the service name. The fields the wizard sends empty (`null`, `[]` or their defaults)
  are left out, which the API reads the same way. A service names its AppScan application (`appScan.applicationId`)
  and no AppScan API key, because the library signs in with the one key of the functional user. Each service gets a
  full pipeline with an active key that runs on the Jenkins agents named in `agentLabels`. A second run answers 409,
  because the product code is taken.
- `create-database-product.sh [DEPARTMENT]` posts `new-database-product.json` and `new-mixed-product.json` the same
  way. While the Library defaults allow no database, OneView answers 400 and names each JDBC URL; set the allowed
  databases and the Nexus repositories of the database packages under Admin, Library defaults, Liquibase database
  changes first. A refused product (400, or 409 when its code is taken) does not stop the other one, and the script
  then exits with status 1.
- `product-pipelines.sh PRODUCT_ID` calls `GET /api/products/{id}/pipelines` and shows each pipeline's ID, type,
  entry point, Jenkins job and active key.
- `pipeline-key.sh CODE SERVICE [TYPE]` finds the product by its code and prints the active key of the service's
  pipeline of that type (`FULL` by default, `NEXUS_IQ` for the Nexus IQ GoldenFix pipeline), for use in the other
  scripts. When it has no key to print, it prints nothing, says why on the error output and exits with a status of
  its own:
  - 1: the pipeline has no active key. The message ends with the call that issues one, for example
    `No active FULL pipeline key for archive-gui of TRADEARCHIVE: issue one with rotate-key.sh 2`.
  - 2: the product has no such service, or the service has no pipeline of that type:
    `No SAST pipeline for archive-api of TRADEARCHIVE`.
  - 3: no product has the code: `No product with the code NOSUCH`. The code is matched as it is written.
  - 4: OneView did not answer or refused the request; the error of `curl` and the problem detail are printed.
  - 64: an argument is missing or the type is not one of `FULL`, `SECURITY`, `EXTENDED`, `SAST` and `NEXUS_IQ`; the
    usage line is printed.
- `rotate-key.sh PIPELINE_ID` calls `POST /api/pipelines/{id}/keys`. An active key is invalidated with the reason
  "Replaced by a new key"; on a pipeline whose key was invalidated this regenerates it.
- `revoke-key.sh PIPELINE_ID REASON` calls `POST /api/pipelines/{id}/keys/revoke` with `{"reason": ...}`. The pipeline
  is disabled until a new key is issued; a pipeline without an active key answers 409.
- `dso-config.sh KEY` calls `GET /api/dso/config/{key}?format=json` and `GET /api/dso/config/{key}`, the requests the
  library sends, and records the key's last use. A revoked key answers 403, an unknown one 404.
- `import-config.sh CONFIG_FILE [SERVICE_NAME]` posts the file to `POST /api/service-configs/import`, which stores
  nothing, and shows the pipeline type and agents it read and each project with the notes on what OneView did not
  take over; `.services[].service` of the answer holds the service in the shape `POST /api/products` takes.
  `SERVICE_NAME` names the service of a file that holds one project without a `projects` map. Try it with
  `examples/api/import-config.sh examples/config/payhub-gateway-full.yaml`.
- `monitoring.sh PRODUCT_ID PIPELINE_ID [RANGE]` calls `GET /api/monitoring/status`, `/products`,
  `/activity?range=`, `/products/{id}` and `/pipelines/{id}?range=` (`30d` by default) and shortens each answer;
  without `INFLUX_URL` the status says that the metrics store is not configured and the runs stay empty.

`create-product.sh`, `create-database-product.sh`, `rotate-key.sh` and `revoke-key.sh` change the data of OneView.
Rotating or revoking a key of a pipeline no Jenkins job uses changes nothing in Jenkins.

## Configuration sample

`config/payhub-gateway-full.json` and `config/payhub-gateway-full.yaml` are what the library receives for the full
pipeline of the sample product Payments Hub, service `gateway`, generated with the two requests of `dso-config.sh`
from a OneView that held that product. The JSON is the same document as the YAML, pretty-printed; the key itself does not appear in either.
Both were rendered again by OneView on 2026-11-22 and are byte for byte what it answers: the project carries no
`asoc.keyId` and no `asoc.token`, because no service holds an AppScan API key, and `defaults.goldenFix.ecosystems`
names `nuget`. To get the same product, post `.services[0].service` of
`examples/api/import-config.sh examples/config/payhub-gateway-full.yaml` as the only service of a new product with
the code `PAYHUB` to `POST /api/products?pipelineType=FULL`:

```bash
ONEVIEW_URL="${ONEVIEW_URL:-${PORTAL_URL:-http://localhost:8080}}"
DEPARTMENT_ID=$(examples/api/list-departments.sh | jq '.[] | select(.name == "Fund Services") | .id')
examples/api/import-config.sh examples/config/payhub-gateway-full.yaml \
  | jq --argjson id "$DEPARTMENT_ID" '{code: "PAYHUB", name: "Payments Hub", departmentId: $id,
        ownerTeam: "Payments Platform", services: [.services[0].service]}' \
  | curl -sS --fail-with-body -X POST "${ONEVIEW_URL%/}/api/products?pipelineType=FULL" \
      -H 'Content-Type: application/json' --data-binary @- \
  | jq '{id, code, name, services: [.services[] | {id, name}]}'
```

The document has four parts: `pipeline` (type, entry point, product, the service in `projectNames` and the agent
labels), `platform` and `defaults` (from the Library defaults) and `projects.gateway` (the service's build, scans, tests,
OpenShift targets, Bitbucket repository and InfluxDB tags). It names Jenkins credentials IDs, never their secrets.
`defaults.build.node` holds the Node.js installation and npm registry of the Library defaults. `gateway` builds its
`console` frontend with Node.js first (`build.node`, rendered only while it is switched on), names its AppScan scan
(`sast.scanName`) and the credential of its SonarQube badges (`tools.sonar.badgeCredentialsId`), and lists the shared
BOM `bom/pom.xml` in `goldenFix.versionCatalogs`. Its build is delivered to Nexus, so the document has no
`deploy.openshift.rd.nexusDeliveryEnabled`; a service that is not delivered renders it as `false`.
`platform.jenkinsUrl` is the Jenkins URL of the Library defaults. Run `api/dso-config.sh` to see the configuration
of the OneView you run. The [user and administrator guide](../docs/documentation.md#22-field-reference-from-the-portal-to-devsecops_configyaml)
lists every key with the field that sets it.

`config/orders-db-full.json` and `config/orders-db-full.yaml` are the configuration of the full pipeline of the
Liquibase service with database changes `orders-db` of `new-database-product.json`, with Library defaults that allow
its databases and name the Nexus repositories of the database packages, and with the Jenkins URL and the Node.js
defaults of the Payments Hub sample. They are what OneView answers for the product `api/create-database-product.sh`
creates. `defaults.tools.liquibase`,
`defaults.tools.oracleJdbc`, `defaults.tools.utplsql` and `defaults.database` hold those Library defaults; they are
rendered only while they are set. The project has `buildTool: liquibase` and a `database` block after its build
settings: the changelog, a changelog parameter, the group ID of the package, and the three databases `test`, `rd` and
`qc`, each with its JDBC URL and the ID of the Jenkins credential of its database user. The test database runs the
utPLSQL tests, and QC sets its own value of the parameter. The project has no `build.liquibase` command, because the
database changes check and package the changelog themselves, and no `deployTarget`, `deploy` or `delivery`, because
the pipelines deploy the database changes themselves. Like every service it names the Nexus IQ server
(`tools.nexusIq.serverUrl`, `tools.nexusIq.credentialsId`), but no Nexus IQ application, so its Nexus IQ stage is not
required. It holds no user and no password. [Chapter 7 of the guide](../docs/documentation.md#7-sql-and-plsql-changes-with-liquibase)
describes Liquibase services with and without database changes.

## Screenshots

`screenshots/v1` to `screenshots/v22` hold screenshots of the GUI across versions. `v1` to `v18` show the portal under its working name, DevSecOps Management Portal; `v19` to `v22` show it as ITSS COE OneView.
- `v7` shows the departments.
- `v8` shows the demo integrations with the charts of Product Management and Pipeline Monitoring.
- `v10` shows the Self-service wizard and the Admin tabs of DevSecOps Management.
- `v11` shows the Nexus IQ GoldenFix pipeline: the Self-service wizard choosing it, the product and pipeline pages of DevSecOps Admin and Pipeline Monitoring, and two pages of the DSOEnhanced report of the new pipeline.
- `v15` shows the Pipelines tab of a department filtered and sorted in its headers, the page of a pipeline with its key, settings, Jenkinsfile and recent runs, a pipeline with an invalidated key, the Self-service wizard with the defaults of the library and the service template, Pipeline Monitoring, and the Admin tabs with the pipeline form filled from the template and the Service template page itself.
- `v16` shows step 2 of the Self-service wizard with the pipelines in their order and names: SAST (Static Application Security Tests) - HCL AppScan, OSA (Open Source Analysis) (NexusIQ with Golden Fix and Golden Pull Request), Security and Full.
- `v18` shows Pipeline Monitoring reading a real InfluxDB: the overview with the delivery performance, a product and a pipeline over 30 days, and the pipeline page with the dashboard cards of two Grafana instances.
- `v19` shows ITSS COE OneView under its new name, taken at 1440 by 900 from the application itself (the boot jar on a fresh H2 database) with the products the scripts of `api/` create: Trade Archive, Orders Database and Orders in Custody, and Payments Hub in Fund Services, read back from `config/payhub-gateway-full.yaml`. No InfluxDB was connected, so no pipeline has a run and the monitoring and pipeline pages say that run results are not available; `v18` shows them with InfluxDB. Each picture is the window as it is, so a page longer than the window ends at its lower edge (`05`, `07`, `09`), and the tables of `02` and `06` are wider than the page at every window size and scroll sideways.
  - `01-pipeline-monitoring-area-switch.png`: the header with the name BBH ITSS COE OneView, the area switch (DevSecOps, QA, Agile) and the sections of the area DevSecOps, on Pipeline Monitoring with the pipelines by business service and the four products.
  - `02-pipelines-of-the-business-service.png`: Pipelines, the five pipelines of Custody with their Jenkins jobs and key hints. The amber note above the table says that run results could not be loaded because InfluxDB is not configured: that is the state of this OneView, not a fault of the page. The column Jenkins job was widened by its header so that each job is on one line; the last column of the table, with the links "Configure service" and "Edit settings" of each row, is to the right of the picture and is reached by scrolling the table.
  - `03-self-service-product.png` and `04-self-service-pipeline.png`: steps 1 and 2 of the Self-service wizard for Trade Archive, a product in the portal, with the four pipeline types.
  - `05-owasp-samm.png`: OWASP SAMM, the maturity of BBH, the five business functions and the first business services; Fund Services goes on below the window.
  - `06-action-history.png`: Action history with the products created, the Library defaults saved and the keys issued and invalidated through the API scripts; the first entry is "Initialize portal" of ITSS COE OneView. The details of that entry and the column Status go on to the right of the picture.
  - `07-admin-products.png`: Admin, Products, by business service.
  - `08-admin-product-shared-jenkinsfile.png`: the product Orders with "Jenkinsfile for several services", the form of `jenkins/multi-service/orders-full.Jenkinsfile`.
  - `09-admin-library-defaults.png`: Admin, Library defaults, Tools and servers.
  - `10-qa-metrics.png`: the area QA with the page QA Metrics and what is planned.
  - `11-agile-maturity.png`: the area Agile with the page Agile Maturity and what is planned.
  The area switch in the header of `01` and the names QA and Agile of `10` and `11` were replaced in `v20`.
- `v20` shows the Main Landing Page and the menu on the left that replace the area switch, and the new names of the areas QM Metric Tool (was QA) and Agile Assessment & Adoption (was Agile), taken the same way as `v19` at 1440 by 900 with Trade Archive in Custody (`api/create-product.sh`) and Payments Hub in Fund Services (`config/payhub-gateway-full.yaml`). No InfluxDB was connected.
  - `01-main-landing-page.png`: the Main Landing Page at `/`, with one card for each area: DevSecOps with its six sections, QM Metric Tool and Agile Assessment & Adoption. The menu on the left marks Main Landing Page, and the header shows no sections.
  - `02-main-landing-page-card-hover.png`: the same page with the pointer on the DevSecOps card, which is raised and opens Pipeline Monitoring on a click.
  - `03-devsecops-pipeline-monitoring.png`: DevSecOps marked in the menu on the left, its six sections in the header and Pipeline Monitoring open.
  - `04-devsecops-pipelines.png`: Pipelines of the area DevSecOps with the same menu.
  - `05-qm-metric-tool.png` and `06-agile-assessment-and-adoption.png`: the pages of the two other areas under their new names, with what is planned.
  - `07-main-landing-page-narrow-window.png`: the Main Landing Page in a window 800 pixels wide, where the menu on the left becomes a row under the header and the cards wrap; nothing scrolls sideways.
  The sections Action history and Admin of DevSecOps in `01` and `03` moved to the menu on the left in `v21`.
- `v21` shows the bar that chooses a business service and an application, and the QM Metric Tool and Agile Assessment & Adoption with their Admin tabs, taken the same way as `v20` at 1440 by 900 from the boot jar on a fresh H2 database. The applications are Trade Archive (`api/create-product.sh`), Orders Database and Orders (`api/create-database-product.sh`, once the example databases and the Nexus repositories of the database packages were set in the Library defaults) in Custody, and Payments Hub in Fund Services (`config/payhub-gateway-full.yaml`). Trade Archive and Orders take part in the QM Metric Tool, Trade Archive with a code coverage target of its own; Payments Hub was set up and does not take part; Orders Database is not set up. The teams of Trade Archive, Orders and Payments Hub take part in Agile Assessment & Adoption, with four assessments, two of them of Trade Archive. The QA leads, agile coaches, Grafana addresses, levels and notes were set through the REST API of chapters 20 and 21 of the guide and are made up for the pictures. No InfluxDB was connected. `07` and `08` show the whole page, the others the window as it is.
  - `01-main-landing-page-all-of-bbh.png`: the Main Landing Page with All business services: the bar above the page, the note that the figures are for the whole of BBH, and the figures of the cards: 6 pipelines; 2 of 4 applications take part in the QM Metric Tool, 2 with a Grafana dashboard; 3 of 4 teams take part, 3 assessed, average level 2.9. The menu on the left has Action history and Admin below the areas.
  - `02-main-landing-page-business-service.png`: the same page with Custody chosen: Application offers All applications of Custody, the bar says "Showing Custody: 3 applications", and the cards count only its applications.
  - `03-main-landing-page-application.png`: Trade Archive chosen: the cards show its 2 pipelines, that it takes part in the QM Metric Tool with at least 85% code coverage, its own target, and at least 95% test pass rate, the BBH target, and its agile level 3.2, assessed on 13 Nov 2026.
  - `04-pipelines-of-the-business-service.png`: Pipelines with Custody chosen: the five pipelines of its three applications. The amber note says that run results could not be loaded because InfluxDB is not configured, as in `v19`.
  - `05-qm-metric-tool-all-of-bbh.png`: the QM Metric Tool with All business services: the tiles, the BBH targets and every application with its business service, whether it takes part, its QA lead, its targets and the link to its dashboard.
  - `06-qm-metric-tool-application.png`: Trade Archive alone: its QA lead, SonarQube project, defect project and quality dashboard, and the targets it works to, its own code coverage target next to two BBH targets.
  - `07-agile-assessment-and-adoption-all-of-bbh.png`: Agile Assessment & Adoption with All business services: the tiles, every team with its way of working, agile coach, latest assessment and level, the mean level of every dimension and of Custody and Fund Services, and what comes next. Long headers of the table take two lines, so the table fits the page.
  - `08-agile-assessment-and-adoption-application.png`: the team of Trade Archive: its latest assessment with the level of every dimension and how it changed since the assessment of August (for example "up 1 from 3"), its notes, and both assessments.
  - `09-admin-applications-and-targets.png`: OneView Admin with its six tabs in four groups, on Applications and targets: the BBH quality targets as OneView starts with them, and the applications with Edit or Set up.
  - `10-admin-teams-and-assessments.png`: Teams and assessments: every team with Edit or Set up and Record assessment, which stays disabled for Orders Database until its team is set up, and the four assessments with Delete.
  - `11-admin-record-an-assessment.png`: the window that records an assessment of Orders, with the day of today and the agile coach of the team filled in and a level to choose in every dimension. The browser shows the day in its own format.
  - `12-action-history-of-the-business-service.png`: OneView Action History with Custody chosen: the products created, two of them refused at first because the Library defaults allowed no database yet, the applications and teams set up, and the assessments recorded. The columns Details and Status go on to the right of the picture.
  - `13-main-landing-page-narrow-window.png`: the Main Landing Page with Custody chosen in a window 800 pixels wide: the menu on the left is a row under the header, the bar puts its summary under the two choices, and the cards wrap; nothing scrolls sideways.
- `v22` shows the guided set-up of a pipeline, Pipelines, Create a pipeline step by step, and Admin, Jenkins automation. The pictures were taken from OneView on a fresh H2 database with the product Trade Archive (`api/create-product.sh`), at a width of 1440 pixels and twice the resolution; each shows the whole page. Bitbucket, Jenkins and OIS are the stand-ins of `tools/wizard-stubs.py` on `localhost`, so the addresses, the repository, the job and its build 42 are made up; OneView fetched the secret of the generic user `svc-oneview` from the OIS stand-in over mutual TLS.
  - `01-pipelines-with-the-guided-set-up.png`: Pipelines with the button Create a pipeline step by step next to Set up pipelines in Self-service.
  - `02-guided-set-up-step-1-basics.png`: step 1 for the new product CertScanner of Corporate Technology, the service `certscanner-api` and the Security pipeline.
  - `03-guided-set-up-step-2-sources-read-from-bitbucket.png`: step 2 after Read the sources: every setting OneView found in the branch with where it found it, the settings of the Jenkinsfile among them, the list of what the sources cannot tell, and the fields filled in (Gradle, the agent of the Jenkinsfile, OpenShift).
  - `04-guided-set-up-step-3-existing-jenkins-build.png`: step 3 after Read the build: the configuration of the existing job and build 42, read only, with its three build parameters and the values the build ran with.
  - `05-guided-set-up-step-4-jenkins-job.png`: step 4 with OneView creates the Jenkins job: the folder and the job name OneView proposes, the credential taken from the existing job, Pipeline or Multibranch Pipeline, and what the new job takes over.
  - `06-guided-set-up-step-5-review.png`: the review before anything is saved.
  - `07-guided-set-up-pipeline-and-jenkins-job-created.png`: the result: the pipeline is saved, the Jenkins job is created with its trigger, and the Jenkinsfile with the key is ready to copy.
  - `08-admin-jenkins-automation.png`: Admin, Jenkins automation with the generic user, the OIS host, the stored OIS API key that is not shown, and a connection check that passed.
  - `09-guided-set-up-sources-entered-by-hand.png`: step 2 without reading the sources, for the new product Ledger Reports and a SAST pipeline, after the generic user was removed.
  - `10-guided-set-up-jenkins-job-left-to-the-user.png`: step 4 without the generic user: OneView says what an administrator has to set and leaves the job to the user.
  - `11-guided-set-up-pipeline-created-job-by-hand.png`: the result without a job: the Jenkinsfile and the settings of the Jenkins job to create by hand.

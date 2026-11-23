# Running ITSS COE OneView as a Container and on OpenShift

ITSS COE OneView ships as a single container image: its Spring Boot jar with the compiled Angular GUI inside, running on the Red Hat UBI 9 OpenJDK 21 runtime. The image runs as a non-root user (`185`) and supports the arbitrary UID (group `0`) assigned by OpenShift (`restricted-v2` SCC).

| Application | Dockerfile | Jar it copies | Port | Manifests |
|-------------|------------|---------------|------|-----------|
| ITSS COE OneView | `backend/Dockerfile` | `backend/build/libs/oneview-<version>.jar` | 8080 | `deploy/openshift/oneview` |

## Build the image

```bash
./gradlew :backend:bootJar
docker build -f backend/Dockerfile -t oneview:0.1.0-SNAPSHOT .
```

The build context is the repository root, whose `.dockerignore` lets only the boot jar `backend/build/libs/oneview-<version>.jar` through. To build on an internal mirror of the base image, pass `--build-arg BASE_IMAGE=<mirror>/ubi9/openjdk-21-runtime:<tag>`. Podman and Buildah accept the same arguments.

## Run the image locally

### With embedded H2 database (default `local` profile)

```bash
docker run --rm -p 8080:8080 -v oneview-data:/application/data oneview:0.1.0-SNAPSHOT
```

### With Oracle database (`rd`, `qc`, `prod` or `oracle` profile)

```bash
docker run --rm -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=rd \
  -e DB_URL='jdbc:oracle:thin:@//<host>:1521/<service>' \
  -e DB_USERNAME='<user>' \
  -e DB_PASSWORD='<password>' \
  oneview:0.1.0-SNAPSHOT
```

InfluxDB and Grafana are set as described in [Running locally](../../README.md#running-locally).

## Deploy to OpenShift (with Oracle database)

The team's applications share one OpenShift project, each in its own pod; the portal is the `oneview` Deployment
with one pod, its Service and its Route, so it sits next to the other applications without clashing. The manifests in `deploy/openshift/oneview` provide a Kustomize base (`Deployment`, `Service`, `Route`, and `ConfigMap`) with three environment overlays: `rd`, `qc`, and `prod`. Each overlay activates the matching Spring profile (`rd`, `qc`, `prod`), which belongs to the `oracle` profile group.

1. Push the image to a registry the cluster can pull from, then point the overlay at it:

   ```bash
   cd deploy/openshift/oneview/overlays/rd
   kustomize edit set image oneview=<registry>/<project>/oneview:0.1.0-SNAPSHOT
   ```

2. Create the Oracle database secret in the target OpenShift project:

   ```bash
   oc create secret generic oneview-db \
     --from-literal=DB_URL='jdbc:oracle:thin:@//<host>:1521/<service>' \
     --from-literal=DB_USERNAME='<user>' \
     --from-literal=DB_PASSWORD='<password>'
   ```

   Optionally create `oneview-influx` to give `INFLUX_TOKEN` as a Secret instead of saving it under Admin, Library defaults:

   ```bash
   oc create secret generic oneview-influx --from-literal=INFLUX_TOKEN='<token>'
   ```

3. Apply the overlay from its folder, where step 1 left you. `oc rollout restart` starts a new pod, which pulls an image
   rebuilt under the same tag and reads changed Secrets; `oc apply` alone rolls the pod only when the Deployment or the
   ConfigMap changed.

   ```bash
   oc apply -k .
   oc rollout restart deployment/oneview
   oc rollout status deployment/oneview
   oc get route oneview
   ```

Liquibase automatically migrates the Oracle schema when the pod starts, and Hibernate validates the schema (`ddl-auto: validate`). Because the database is external Oracle, the Deployment uses `RollingUpdate` for zero-downtime rollouts. The pod logs to its console, which OpenShift collects, and to `/application/logs` on an `emptyDir` of 3 GB that lives as long as the pod.

The pod reads the variables of [Running locally](../../README.md#running-locally) from:

| Variable | Source |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | ConfigMap `oneview-config`, set by the overlay |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Secret `oneview-db` |
| `DB_POOL_SIZE` | ConfigMap (optional) |
| `INFLUX_URL`, `INFLUX_ORG`, `INFLUX_BUCKET`, `GRAFANA_*` | ConfigMap |
| `INFLUX_TOKEN` | Secret `oneview-influx` (optional) |

Jenkins reaches OneView through its route: a Jenkins administrator sets the global environment variable `ONEVIEW_URL` to the route's address, for example `https://oneview.apps.bbh.com`, and DSOEnhanced fetches each pipeline's configuration from `/api/dso/config/<key>?format=json` and posts the report of each run to `/api/dso/reports`. The route must therefore be reachable from the agents that read OneView (the label in `ONEVIEW_AGENT`, `linux-agent` when it is not set) and from the agents that run the pipelines. A Jenkins that still sets `DSO_PORTAL_URL`, the former name of the variable, keeps working: the library reads it while `ONEVIEW_URL` is not set and names the new variable in the console of the build. When the route moves from a `dso-portal` host to the `oneview` host, change the value of the variable in the same step. A job pinned to a release of the library from before the new name reads only `DSO_PORTAL_URL`: keep both variables set to the same address until no job uses such a release.

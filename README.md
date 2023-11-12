# PerTaaS - Performance testing of the API

### Couchbase database setup
- Create a pvc with the name couchbase-pvc. The developer sandbox doesn't allow pvc to be created with ReadWriteMany, so we will be able to run only one pod for couchbase.
```bash
oc create -f - <<EOF
kind: PersistentVolumeClaim
apiVersion: v1
metadata:
  name: couchbase-pvc
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 2Gi
EOF
```
- Create the couchbase pod by deploying the couchbase image
```bash
oc new-app registry.connect.redhat.com/couchbase/server:7.2.2-1 --name couchbasedb1
oc set volume deployment/couchbasedb1 --add --overwrite --name=couchbasedb1-volume-1 --type=persistentVolumeClaim --claim-name=couchbase-pvc
```
- The couchbase database can also be created via the Couchbase Autonomous Operator
- Expose the route to access the couchbase UI
```bash
oc expose service couchbasedb1 --target-port=8091
```
- Setup the cluster by accessing the exposed route. Please note that this is a non ssl route, but openshift takes to ssl route when clicking on the link.
- Click on `Setup New Cluster`
- Give the cluster name as `pertaas-cluster`
- Provide a rememberable password for Administrator user
- Click on `Next: Accept Terms`
- Read the terms, select the checkboxes and click on `Configure Disk, Memory, Services`
- Unselect `Search, Analytics, Eventing, Backup`
- Click on `Save & Finish`
- Create a bucket with the name `pertaas` by clicking on Buckets -> Add Bucket (top right corner).
  <img width="531" alt="Add Bucket" src="https://github.com/himanshumps/pertaas/assets/22702284/09ddced5-fc0e-47ba-b1ad-329de96770ee">

- Create a user with username `pertaas_user` and password as `qHogxn5e` by clicking on Security -> Add User. Also in the right panel update the user access and give it Access under Bucket -> Bucket Admin -> pertaas, Bucket -> Manage Scopes -> pertaas, * and Bucket -> Application Access -> pertaas.
  <img width="1728" alt="pertaas_user security" src="https://github.com/himanshumps/pertaas/assets/22702284/b3471a9a-7b73-44db-b2b1-5662547aa8f9">

- Create two indexes by running these queries by selecting Query from left panel
```text
CREATE INDEX `key_tx_index` ON `pertaas`(`key_tx`);
CREATE INDEX `meta_id_index` ON `pertaas`((meta().`id`));
```
<img width="1723" alt="Indexes" src="https://github.com/himanshumps/pertaas/assets/22702284/ff44f2eb-bfdb-41e3-9537-bfb6afc154df">

### Roles and Role Bindings

#### Roles

We need to provide three roles to the default user for the pertaas-api application

```bash
oc create -f - <<EOF
kind: Role
apiVersion: rbac.authorization.k8s.io/v1
metadata:
  name: delete-job-role
rules:
  - verbs:
      - delete
    apiGroups:
      - batch
    resources:
      - jobs
EOF
```

```bash
oc create -f - <<EOF
kind: Role
apiVersion: rbac.authorization.k8s.io/v1
metadata:
  name: imagestream-list-role
rules:
  - verbs:
      - list
    apiGroups:
      - image.openshift.io
    resources:
      - imagestreams
EOF
```

```bash
oc create -f - <<EOF
kind: Role
apiVersion: rbac.authorization.k8s.io/v1
metadata:
  name: tekton-pipeline-run-role
  namespace: himanshumps-1-dev
rules:
  - verbs:
      - create
    apiGroups:
      - tekton.dev
    resources:
      - pipelineruns
EOF
```

#### Role Bindings

Bind the three roles to the default user

```bash
oc create -f - <<EOF
kind: RoleBinding
apiVersion: rbac.authorization.k8s.io/v1
metadata:
  name: delete-job-rolebinding
subjects:
  - kind: ServiceAccount
    name: default
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: Role
  name: delete-job-role
EOF
```

```bash
oc create -f - <<EOF
kind: RoleBinding
apiVersion: rbac.authorization.k8s.io/v1
metadata:
  name: imagestream-list-rolebinding
subjects:
  - kind: ServiceAccount
    name: default
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: Role
  name: imagestream-list-role
EOF
```

```bash
oc create -f - <<EOF
kind: RoleBinding
apiVersion: rbac.authorization.k8s.io/v1
metadata:
  name: tekton-pipeline-run-rolebinding
subjects:
  - kind: ServiceAccount
    name: default
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: Role
  name: tekton-pipeline-run-role
EOF
```
### Secret for couchbase

Create the couchbase secret. 

Get the svc details for the couchbasedb1 and provide it as part of connection string

```bash
oc get svc couchbasedb1 -o go-template --template='{{.metadata.name}}.{{.metadata.namespace}}.svc.cluster.local{{println}}'
```

Replace `<Internal service name>` with the service name received from above oc command
```bash
oc create -f - <<EOF
apiVersion: v1
metadata:
  name: couchbase-secret
data:
  COUCHBASE_CONNECTION_STRING: couchbase://<Internal service name>
  COUCHBASE_USER: pertaas_user
  COUCHBASE_PASSWORD: qHogxn5e
  COUCHBASE_BUCKET: pertaas
EOF
```

### PerTaaS API Application

The application can be deployed as knative application or as a deployment. The startup time for the application is around 3 seconds as the couchbase cluster and bucket initialization delays the startup.

```bash
# Deploy the app
oc new-app https://github.com/himanshumps/pertaas.git --context-dir=pertaas-api --strategy=docker --name="pertaas-api"
# Add the secret to the deployment
oc set env deployment/pertaas-api --from secret/couchbase-secret
# Expose the ssl route with edge termination
oc create route edge pertaas-api --service=pertaas-api --port=8080
```

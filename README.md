# PerTaaS - Performance Testing as a Service



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

### Tekton pipeline

We have created two tekton pipelines to create the job using helm and custom image creation for the pertaas-job application

```bash
oc create -f - <<EOF
apiVersion: tekton.dev/v1
kind: Task
metadata:
  name: deploy-job-using-helm
spec:
  description: These tasks will install Job using the helm chart
  params:
  - description: The Job ID
    name: jobId
    type: string
  - description: The helm chart to create the job
    name: helmChart
    type: string
  - description: The request json for the test
    name: requestJson
    type: string
  - description: The metrics scrape duration in seconds
    name: stepDuration
    type: string
  - description: The amount of CPU to be assigned for the pod running the test
    name: cpu
    type: string
  - description: The amount of RAM/Memory to be assigned for the pod running the test
    name: memory
    type: string
  - default: docker.io/dtzar/helm-kubectl:3.13.1
    description: Specify a specific helm image
    name: HELM_IMAGE
    type: string
  steps:
  - computeResources: {}
    image: $(params.HELM_IMAGE)
    name: helm-deploy
    script: |
      helm repo add pertaas-job-helm-repository https://himanshumps.github.io/pertaas-helm/


      helm install --set jobId="$(params.jobId)" --set-literal requestJson='$(params.requestJson)' --set-string stepDuration="$(params.stepDuration)" --set-string cpu="$(params.cpu)" --set-string memory="$(params.memory)" --debug $(params.jobId) pertaas-job-helm-repository/$(params.helmChart)
    workingDir: $(workspaces.source.path)
  workspaces:
  - name: source
---
apiVersion: tekton.dev/v1
kind: Task
metadata:
  name: download-go-binary
spec:
  steps:
  - args:
    - -c
    - |
      set -ex
      wget -O pertaas-couchbase-go-dev-linux-amd64 https://github.com/himanshumps/pertaas-couchbase-go/releases/download/dev/pertaas-couchbase-go-dev-linux-amd64
      chmod 777 pertaas-couchbase-go-dev-linux-amd64
    command:
    - /bin/sh
    computeResources: {}
    image: alpine
    name: download-go-binary
    workingDir: $(workspaces.source.path)
  workspaces:
  - description: A workspace that needs to be dumped.
    name: source
---
apiVersion: tekton.dev/v1
kind: Task
metadata:
  name: update-couchbase
spec:
  params:
  - name: jobId
    type: string
  - name: message
    type: string
  steps:
  - computeResources: {}
    envFrom:
    - secretRef:
        name: couchbase-secret
    image: registry.access.redhat.com/ubi9/ubi-minimal:9.2-750.1697625013
    name: update-couchbase
    script: |
      #!/bin/sh
      echo "start execution"
      /workspace/source/pertaas-couchbase-go-dev-linux-amd64 -setJobId "$(params.jobId)" -setMessage "$(params.message)"
      echo "execution completed"
    workingDir: $(workspaces.source.path)
  workspaces:
  - description: A workspace that needs to be dumped.
    name: source
---
apiVersion: tekton.dev/v1
kind: Pipeline
metadata:
  name: create-job-via-helm-pipeline
spec:
  params:
  - name: jobId
    type: string
  - name: helmChart
    type: string
  - name: requestJson
    type: string
  - name: stepDuration
    type: string
  - default: "1000"
    name: cpu
    type: string
  - default: "512"
    name: memory
    type: string
  tasks:
  - name: deploy-job-using-helm
    params:
    - name: jobId
      value: $(params.jobId)
    - name: helmChart
      value: $(params.helmChart)
    - name: requestJson
      value: $(params.requestJson)
    - name: stepDuration
      value: $(params.stepDuration)
    - name: cpu
      value: $(params.cpu)
    - name: memory
      value: $(params.memory)
    - name: HELM_IMAGE
      value: docker.io/dtzar/helm-kubectl:3.13.1
    taskRef:
      kind: Task
      name: deploy-job-using-helm
    workspaces:
    - name: source
      workspace: source
  workspaces:
  - name: source
---
apiVersion: tekton.dev/v1
kind: Pipeline
metadata:
  name: custom-image-pipeline
spec:
  params:
  - description: The custom image name
    name: imageName
    type: string
  - description: The image description to be added as label
    name: image_description
    type: string
  - default: https://github.com/himanshumps/pertaas-job.git
    description: The repo where the source code is located for the performance test
    name: githubUrl
    type: string
  - default: main
    description: The branch or tag revision of the source code for the performance
      test
    name: gitRevision
    type: string
  tasks:
  - name: download-go-binary
    taskRef:
      kind: Task
      name: download-go-binary
    workspaces:
    - name: source
      workspace: emptydir
  - name: update-couchbase-start-pipeline
    params:
    - name: jobId
      value: $(params.imageName)
    - name: message
      value: Started the pipeline
    runAfter:
    - download-go-binary
    taskRef:
      kind: Task
      name: update-couchbase
    workspaces:
    - name: source
      workspace: emptydir
  - name: git-clone
    params:
    - name: url
      value: $(params.githubUrl)
    - name: revision
      value: $(params.gitRevision)
    - name: refspec
      value: ""
    - name: submodules
      value: "true"
    - name: depth
      value: "1"
    - name: sslVerify
      value: "false"
    - name: crtFileName
      value: ca-bundle.crt
    - name: subdirectory
      value: $(params.imageName)
    - name: sparseCheckoutDirectories
      value: ""
    - name: deleteExisting
      value: "true"
    - name: httpProxy
      value: ""
    - name: httpsProxy
      value: ""
    - name: noProxy
      value: ""
    - name: verbose
      value: "true"
    - name: gitInitImage
      value: registry.redhat.io/openshift-pipelines/pipelines-git-init-rhel8@sha256:1a50511583fc02a27012d17d942e247813404104ddd282d7e26f99765174392c
    runAfter:
    - update-couchbase-start-pipeline
    taskRef:
      kind: ClusterTask
      name: git-clone
    workspaces:
    - name: output
      workspace: emptydir
  - name: update-couchbase-maven-build
    params:
    - name: jobId
      value: $(params.imageName)
    - name: message
      value: Started the maven build
    runAfter:
    - git-clone
    taskRef:
      kind: Task
      name: update-couchbase
    workspaces:
    - name: source
      workspace: emptydir
  - name: maven
    params:
    - name: MAVEN_IMAGE
      value: maven:3.9.5-eclipse-temurin-21
    - name: GOALS
      value:
      - install
      - -f
      - $(params.imageName)/pom.xml
      - -B
      - -Dmaven.repo.local=.m2/repository/
    - name: MAVEN_MIRROR_URL
      value: ""
    - name: SERVER_USER
      value: ""
    - name: SERVER_PASSWORD
      value: ""
    - name: PROXY_USER
      value: ""
    - name: PROXY_PASSWORD
      value: ""
    - name: PROXY_PORT
      value: ""
    - name: PROXY_HOST
      value: ""
    - name: PROXY_NON_PROXY_HOSTS
      value: ""
    - name: PROXY_PROTOCOL
      value: http
    - name: CONTEXT_DIR
      value: .
    runAfter:
    - update-couchbase-maven-build
    taskRef:
      kind: ClusterTask
      name: maven
    workspaces:
    - name: source
      workspace: emptydir
    - name: maven-settings
      workspace: emptydir
  - name: update-couchbase-build-completed
    params:
    - name: jobId
      value: $(params.imageName)
    - name: message
      value: Maven build completed
    runAfter:
    - maven
    taskRef:
      kind: Task
      name: update-couchbase
    workspaces:
    - name: source
      workspace: emptydir
  - name: buildah
    params:
    - name: IMAGE
      value: image-registry.openshift-image-registry.svc:5000/$(context.pipelineRun.namespace)/$(params.imageName)
    - name: BUILDER_IMAGE
      value: registry.redhat.io/rhel8/buildah@sha256:00795fafdab9bbaa22cd29d1faa1a01e604e4884a2c935c1bf8e3d1f0ad1c084
    - name: STORAGE_DRIVER
      value: vfs
    - name: DOCKERFILE
      value: $(params.imageName)/Dockerfile
    - name: CONTEXT
      value: $(params.imageName)/
    - name: TLSVERIFY
      value: "true"
    - name: FORMAT
      value: oci
    - name: PUSH_EXTRA_ARGS
      value: ""
    - name: SKIP_PUSH
      value: "false"
    - name: BUILD_EXTRA_ARGS
      value: --label pertaas-image-description="$(params.image_description)"
    runAfter:
    - update-couchbase-build-completed
    taskRef:
      kind: ClusterTask
      name: buildah
    workspaces:
    - name: source
      workspace: emptydir
  - name: update-couchbase-buildah-completed
    params:
    - name: jobId
      value: $(params.imageName)
    - name: message
      value: Image created and pushed to openshift imagestream
    runAfter:
    - openshift-client
    taskRef:
      kind: Task
      name: update-couchbase
    workspaces:
    - name: source
      workspace: emptydir
  - name: openshift-client
    params:
    - name: SCRIPT
      value: oc annotate is/$(params.imageName) "pertaas-image-description"="$(params.image_description)"
    - name: VERSION
      value: latest
    runAfter:
    - buildah
    taskRef:
      kind: ClusterTask
      name: openshift-client
  workspaces:
  - name: emptydir
  - name: configmap_settings
EOF
```bash


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


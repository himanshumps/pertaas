## PerTaaS API service

### Build strategy
It uses Dockerfile to build the final image.

The builder image used is maven:3.9.5-eclipse-temurin-21-alpine and the runtime image used is JSK 21 from eclipse temurin running on ubi 9 (eclipse-temurin:21.0.1_12-jdk-ubi9-minimal)

### API endpoints

- `POST /customImage/create`: Runs a teckton pipeline (custom-image-pipeline) for creating the image, send status to couchbase (could be replaced with cloudevents or k8s events) and pushing it to openshift internal registry
- `GET /getDocument/{documentId}`: Gets the content of the given document ID from couchbase by using the blocking call. In case the document is not retrieved, it returns "No data found"
- `GET /healthz /healthz/1 /healthz/2` and `POST /healthz`: Generic endpoints to demonstrate performance test
- `GET /helm/charts`: Gets the helm chart information from https://himanshumps.github.io/pertaas-helm/index.yaml and returns the json with the latest version of the chart.
- `POST /helm/create`: Runs the tekton pipeline (create-job-via-helm-pipeline) which imports the helm chart repository and passes the passed parameters to the helm chart to create a new job.
- `GET /job/generateID`: Generates a new job id which can be used in CI/CD pipeline or helm chart.
- `GET /job/stop/{jobId}`: Deletes the job and corresponding resources for the given job ID.
- `GET /metrics/{registryType}/{jobId}`: Gets the metrics from couchbase for the given registry type (Step meter (scraping every step duration or simple meter (final scrape after test completion)) for the given job ID
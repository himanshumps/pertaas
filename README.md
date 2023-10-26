# pertaas
Performance testing of the API

Couchbase Database:
* ARM 64 version: `oc new-app --name=db --docker-image=registry.connect.redhat.com/couchbase/server:7.2.2-1-arm64`
* Created a PV with the couchbase-pv and attached it to the deployment (mount path: /opt/couchbase/var)
* Created a non-ssl route to port 8091
* Created a couchbase cluster with the name pertaas-cluster
* Create a pertaas bucket
* Created a user pertaas_user and gave the bucket admin access to pertaas bucket

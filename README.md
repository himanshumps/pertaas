# pertaas
Performance testing of the API

Installed couchbase using the operator. Documentation available at https://docs.couchbase.com/operator/current/install-openshift.html

* Login as kubedmin: `/Users/himanshu/Downloads/oc login -u kubedmin https://api.crc.testing:6443`
* Install CRD: `couchbase-autonomous-operator_2.5.0-180-kubernetes-macos-arm64 % /Users/himanshu/Downloads/oc create -f crd.yaml`
* Create secret: `/Users/himanshu/Downloads/oc create secret docker-registry rh-catalog --docker-server=registry.connect.redhat.com --docker-username=himanshu.mps@gmail.com --docker-password=******* --docker-email=himanshu.mps@gmail.com`
* DAC: `couchbase-autonomous-operator_2.5.0-180-kubernetes-macos-arm64 % bin/cao create admission --image-pull-secret rh-catalog`
* Operator: `couchbase-autonomous-operator_2.5.0-180-kubernetes-macos-arm64 %bin/cao create operator --image-pull-secret rh-catalog`

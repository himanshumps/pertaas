package org.redhat.hackathon.job;

import io.fabric8.kubernetes.api.model.EmptyDirVolumeSource;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.tekton.client.TektonClient;
import io.fabric8.tekton.pipeline.v1.*;
import io.fabric8.tekton.pipeline.v1alpha1.ArrayOrString;
import io.fabric8.tekton.triggers.v1alpha1.TriggerTemplateBuilder;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;

import java.util.Collections;
import java.util.List;

@Path("/job")
public class JobController {

  @Inject
  TektonClient tektonClient;

  @GET
  @Path("/create")
  public String createJobViaTektonPipeline() {
    /*JsonObject jsonObject = new JsonObject(jsonString);
    int stepDuration = jsonObject.getInteger("stepDuration");
    String gitUrl = jsonObject.getString("gitUrl");
    String revision = jsonObject.getString("revision");
    JsonObject requestJson = jsonObject.getJsonObject("requestJson");*/

    PipelineRun pipelineRunBuilderSpecNested = new PipelineRunBuilder()
        .withNewMetadata().withName("test-pipeline-run")
        .endMetadata()
        .withNewSpec()
        .withNewPipelineRef()
        .withName("job-pipeline")
        .endPipelineRef()

        .addNewParam()
        .withName("jobId")
        .withValue(new ParamValue("job-17"))
        .endParam()
        .addNewParam()
        .withName("requestJson")
        .withValue(new ParamValue("{\"hostname\":\"pertaas-api-himanshumps-1-dev.apps.sandbox-m3.1530.p1.openshiftapps.com\",\"port\":443,\"ssl\":true,\"maxConnections\":10,\"runDurationInSeconds\":180,\"requestPerSecond\":10000000,\"httpRequests\":[{\"method\":\"GET\",\"uri\":\"/healthz\",\"headers\":null,\"queryParams\":null,\"pathParams\":null,\"body\":null}]}"))
        .endParam()
        .addNewParam()
        .withName("gitRepoUrl")
        .withValue(new ParamValue("https://github.com/himanshumps/pertaas-job.git"))
        .endParam()
        .addNewParam()
        .withName("gitRevision")
        .withValue(new ParamValue("main"))
        .endParam()
        .withWorkspaces(new WorkspaceBindingBuilder().withName("emptydir").withNewPersistentVolumeClaim("tekton1", false).build(),
            new WorkspaceBindingBuilder().withName("configmap_settings").withEmptyDir(new EmptyDirVolumeSource()).build())
        .endSpec()
        .build();
    tektonClient.v1().pipelineRuns().create(pipelineRunBuilderSpecNested);

    return "The job has been triggered";
  }
}

package org.redhat.hackathon.job;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.kv.CounterResult;
import com.couchbase.client.java.kv.IncrementOptions;
import io.fabric8.kubernetes.api.model.EmptyDirVolumeSource;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.openshift.client.OpenShiftConfigBuilder;
import io.fabric8.tekton.client.TektonClient;
import io.fabric8.tekton.pipeline.v1.ParamValue;
import io.fabric8.tekton.pipeline.v1.PipelineRun;
import io.fabric8.tekton.pipeline.v1.PipelineRunBuilder;
import io.fabric8.tekton.pipeline.v1.WorkspaceBindingBuilder;
import io.quarkus.logging.Log;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

@Path("/job")
public class JobController {


    @Inject
    TektonClient tektonClient;

    @Inject
    OpenShiftClient openShiftClient;

    @Inject
    Bucket bucket;

    @GET
    @Path("/generateId")
    public String generateJobId() {
        CounterResult counterResult = bucket.defaultCollection().binary().increment("JOB_COUNTER", IncrementOptions.incrementOptions().initial(1));
        return "job-" + Long.valueOf(counterResult.content()).intValue();
    }

    @POST
    @Path("/create")
    @RunOnVirtualThread
    public String createJobViaTektonPipeline(String jsonString) {
        Log.info("Received: " + jsonString);
        CounterResult counterResult = bucket.defaultCollection().binary().increment("JOB_COUNTER", IncrementOptions.incrementOptions().initial(1));
        String jobId = "job-" + Long.valueOf(counterResult.content()).intValue();
        JsonObject jsonObject = new JsonObject(jsonString);
        String stepDuration = jsonObject.getString("stepDuration", "10");
        String github_url = jsonObject.getString("github_url");
        String revision = jsonObject.getString("revision");
        Object requestJson = jsonObject.getValue("request_json");
        String cpu = jsonObject.getString("cpu", "1000");
        String ram = jsonObject.getString("ram", "1024");
        PipelineRun pipelineRunBuilderSpecNested = new PipelineRunBuilder()
                .withNewMetadata().withName(jobId + "-run")
                .endMetadata()
                .withNewSpec()
                .withNewPipelineRef()
                .withName("job-pipeline")
                .endPipelineRef()
                .addNewParam()
                .withName("jobId")
                .withValue(new ParamValue(jobId))
                .endParam()
                .addNewParam()
                .withName("requestJson")
                .withValue(new ParamValue(requestJson.toString()))
                .endParam()
                .addNewParam()
                .withName("gitRepoUrl")
                .withValue(new ParamValue(github_url))
                .endParam()
                .addNewParam()
                .withName("gitRevision")
                .withValue(new ParamValue(revision))
                .endParam()
                .addNewParam()
                .withName("stepDuration")
                .withValue(new ParamValue(stepDuration))
                .endParam()
                .addNewParam()
                .withName("cpu")
                .withValue(new ParamValue(cpu))
                .endParam()
                .addNewParam()
                .withName("ram")
                .withValue(new ParamValue(ram))
                .endParam()
                .withWorkspaces(new WorkspaceBindingBuilder().withName("emptydir").withNewPersistentVolumeClaim("tekton-pvc", false).build(),
                        new WorkspaceBindingBuilder().withName("configmap_settings").withEmptyDir(new EmptyDirVolumeSource()).build())
                .endSpec()
                .build();
        try {
            tektonClient.v1().pipelineRuns().resource(pipelineRunBuilderSpecNested).create();
            return "The job with the ID: \"" + jobId + "\" has been triggered. Please keep a note of this job id which you can use to monitor the job.";
        } catch (Exception e) {
            Log.error("Issue while running the pipeline", e);
            return "There was some issue while running the pipeline.";
        }


    }

    @GET
    @Path("/stop/{jobId}")
    @RunOnVirtualThread
    public String stopJob(String jobId) {
        try {
            openShiftClient.batch().v1().jobs().withName(jobId).delete();
            return "The job with the job ID: " + jobId + " has been sent for deletion.";
        } catch (Exception e) {
            Log.error("Job deletion error", e);
            return "There was some problem with the job deletion";
        }
    }
}

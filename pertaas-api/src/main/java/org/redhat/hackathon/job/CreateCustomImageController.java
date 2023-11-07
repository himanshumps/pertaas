package org.redhat.hackathon.job;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.kv.CounterResult;
import com.couchbase.client.java.kv.IncrementOptions;
import io.fabric8.kubernetes.api.model.EmptyDirVolumeSource;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.tekton.client.TektonClient;
import io.fabric8.tekton.pipeline.v1.ParamValue;
import io.fabric8.tekton.pipeline.v1.PipelineRun;
import io.fabric8.tekton.pipeline.v1.PipelineRunBuilder;
import io.fabric8.tekton.pipeline.v1.WorkspaceBindingBuilder;
import io.quarkus.logging.Log;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

@Path("/customImage")
public class CreateCustomImageController {

  @Inject
  TektonClient tektonClient;

  @Inject
  OpenShiftClient openShiftClient;
  //@Inject
  Bucket bucket;

  @POST
  @Path("/create")
  public String createCustomImage(String jsonString) {
    JsonObject jsonObject = new JsonObject(jsonString);
    String imageName = jsonObject.getString("image_name");
    String githubUrl = jsonObject.getString("github_url");
    String githubRevision = jsonObject.getString("github_revision");
    Long count = openShiftClient.imageStreams().list().getItems()
        .stream()
        .filter(imageStream -> {
          Log.info("The image name is: " + imageStream);
          return imageStream.getMetadata().getName().equalsIgnoreCase(imageName);
        })
        .count();
    if(count.intValue() > 0) {
      return "The image with the name already exists. Please select another name for the image.";
    }
    CounterResult counterResult = bucket.defaultCollection().binary().increment("CUSTOM_IMAGE_COUNTER", IncrementOptions.incrementOptions().initial(1));
    String pipelineRunId = "pertaas-custom-image-" +  Long.valueOf(counterResult.content()).intValue() + "-run";
    PipelineRun pipelineRunBuilderSpecNested = new PipelineRunBuilder()
        .withNewMetadata().withName(pipelineRunId)
        .endMetadata()
        .withNewSpec()
        .withNewPipelineRef()
        .withName("custom-image-pipeline")
        .endPipelineRef()
        .addNewParam()
        .withName("imageName")
        .withValue(new ParamValue(imageName))
        .endParam()
        .addNewParam()
        .withName("githubUrl")
        .withValue(new ParamValue(githubUrl))
        .endParam()
        .addNewParam()
        .withName("gitRevision")
        .withValue(new ParamValue(githubRevision))
        .endParam()
        .withWorkspaces(new WorkspaceBindingBuilder().withName("emptydir").withNewPersistentVolumeClaim("tekton1", false).build(),
            new WorkspaceBindingBuilder().withName("configmap_settings").withEmptyDir(new EmptyDirVolumeSource()).build())
        .endSpec()
        .build();
    tektonClient.v1().pipelineRuns().create(pipelineRunBuilderSpecNested);


    return "The request for custom image creation has been accepted. The new image would be available after 10 minutes in case there are no failures. Please share the ID: \"" + pipelineRunId + "\" with openshift administrator in case of any issues.";
  }
}

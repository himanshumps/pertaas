package org.redhat.hackathon.controller;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.kv.CounterResult;
import com.couchbase.client.java.kv.IncrementOptions;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.fabric8.kubernetes.api.model.EmptyDirVolumeSource;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.tekton.client.TektonClient;
import io.fabric8.tekton.pipeline.v1.ParamValue;
import io.fabric8.tekton.pipeline.v1.PipelineRun;
import io.fabric8.tekton.pipeline.v1.PipelineRunBuilder;
import io.fabric8.tekton.pipeline.v1.WorkspaceBindingBuilder;
import io.quarkus.logging.Log;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Path("/helm")
public class HelmChartController {

  @Inject
  TektonClient tektonClient;

  @Inject
  OpenShiftClient openShiftClient;

  @Inject
  Bucket bucket;

  /**
   * Fetch the helm chart by reading the index.yaml from the helm repository,
   * convert the yaml to json,
   * read the latest version of all helm charts and send it back in json formt
   *
   * @return
   * @throws MalformedURLException
   * @throws URISyntaxException
   */
  @GET
  @Path("/charts")
  @RunOnVirtualThread
  public String getHelmCharts() throws MalformedURLException, URISyntaxException {
    // TODO: Create helm repo if it does not exists. This doesn't work on openshift sandbox as we do not have role to do so
        /*
        AtomicReference<Boolean> createRepo = new AtomicReference<>(Boolean.TRUE);
        openShiftClient.helmChartRepositories().list().getItems().forEach(helmChartRepository -> {
            System.out.println(helmChartRepository.getMetadata().getName());
            if (helmChartRepository.getMetadata().getName().equalsIgnoreCase("pertaas-job-helm-repository")) {
                createRepo.set(Boolean.FALSE);
            }
        });
        if (createRepo.get()) {
            // Create the helm repo if it does not exist in the current namespace.
            HelmChartRepository helmChartRepository = new HelmChartRepositoryBuilder().withMetadata(new ObjectMetaBuilder().withName("pertaas-job-helm-repository").build()).build();
            HelmChartRepositorySpec helmChartRepositorySpec = new HelmChartRepositorySpec();
            helmChartRepositorySpec.setName("PerTaaS Job Helm Repository");
            helmChartRepositorySpec.setConnectionConfig(new ConnectionConfigBuilder().withUrl("https://himanshumps.github.io/pertaas-helm/").build());
            openShiftClient.resource(helmChartRepository).create();
        }*/

    // I am reading the Helm chart details from my github repo.
    // It should be read using openshift client or k8s client, but I did not find any API to get the chart info to achieve the same.
    // I am not using webclient here as the test uses webclient and if we create an instance here, it will add to the existing micrometer metrics
    URL u = new URI("https://himanshumps.github.io/pertaas-helm/index.yaml").toURL();
    List<ImageNameAndDetails> imageNameAndDetailsList = new ArrayList<>();
    try (InputStream in = u.openStream()) {
      String yaml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
      Object obj = yamlReader.readValue(yaml, Object.class);
      Map<String, String> helmChartNameAndDescription = new LinkedHashMap<>();
      JsonObject jsonObject = new JsonObject(Json.encode(obj));
      JsonObject entries = jsonObject.getJsonObject("entries");
      entries.forEach(entry -> {
        //Log.info("entry.getKey(): " + entry.getKey());
        if (helmChartNameAndDescription.get(entry.getKey()) == null) {
          //Log.info("entries.getJsonArray(entry.getKey()): " + entries.getJsonArray(entry.getKey()));
          imageNameAndDetailsList.add(new ImageNameAndDetails(
              entry.getKey(), // Always use the latest version. If other version needs to be supported, add `" --version " + entries.getJsonArray(entry.getKey()).getJsonObject(0).getString("version")`
              entry.getKey() + " - " + entries.getJsonArray(entry.getKey()).getJsonObject(0).getString("description")
          ));
          helmChartNameAndDescription.put(entry.getKey(), "");
        }
      });
    } catch (IOException e) {
      Log.error("There was an exception reading the index file from github repo", e);
    }
    return Json.encode(imageNameAndDetailsList);
  }

  /**
   * Create the job using the tekton pipeline. It passes the values received in UI to the tekton job which in turn
   * passes it to the helm chart which creates the job.
   *
   * @param jsonString
   * @return
   */
  @POST
  @Path("/create")
  @RunOnVirtualThread
  public String createHelmJobViaTektonPipeline(String jsonString) {
    Log.info("Received: " + jsonString);
    CounterResult counterResult = bucket.defaultCollection().binary().increment("JOB_COUNTER", IncrementOptions.incrementOptions().initial(1));
    String jobId = "job-" + Long.valueOf(counterResult.content()).intValue();
    JsonObject jsonObject = new JsonObject(jsonString);
    String helmChart = jsonObject.getString("helm_chart");
    Object requestJson = jsonObject.getValue("request_json");
    String stepDuration = jsonObject.getString("stepDuration", "10");
    String cpu = jsonObject.getString("cpu", "1000");
    String memory = jsonObject.getString("memory", "1024");
    PipelineRun pipelineRunBuilderSpecNested = new PipelineRunBuilder()
        .withNewMetadata().withName(jobId + "-run")
        .endMetadata()
        .withNewSpec()
        .withNewPipelineRef()
        .withName("create-job-via-helm-pipeline")
        .endPipelineRef()
        .addNewParam()
        .withName("jobId")
        .withValue(new ParamValue(jobId))
        .endParam()
        .addNewParam()
        .withName("helmChart")
        .withValue(new ParamValue(helmChart.toString()))
        .endParam()
        .addNewParam()
        .withName("requestJson")
        .withValue(new ParamValue(requestJson.toString()))
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
        .withName("memory")
        .withValue(new ParamValue(memory))
        .endParam()
        .withWorkspaces(new WorkspaceBindingBuilder().withName("source").withEmptyDir(new EmptyDirVolumeSource()).build())
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

  record ImageNameAndDetails(String name, String description) {
  }


}

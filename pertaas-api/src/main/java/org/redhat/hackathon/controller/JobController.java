package org.redhat.hackathon.controller;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.kv.CounterResult;
import com.couchbase.client.java.kv.IncrementOptions;
import io.fabric8.openshift.client.OpenShiftClient;
import io.quarkus.logging.Log;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@Path("/job")
public class JobController {

  @Inject
  OpenShiftClient openShiftClient;

  @Inject
  Bucket bucket;

  @GET
  @Path("/generateId")
  @RunOnVirtualThread
  public String generateJobId() {
    CounterResult counterResult = bucket.defaultCollection().binary().increment("JOB_COUNTER", IncrementOptions.incrementOptions().initial(1));
    return "job-" + Long.valueOf(counterResult.content()).intValue();
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

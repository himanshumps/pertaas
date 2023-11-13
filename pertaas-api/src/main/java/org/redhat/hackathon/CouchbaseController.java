package org.redhat.hackathon;

import com.couchbase.client.java.Bucket;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Path("/couchbase")
public class CouchbaseController {

  @Inject
  Bucket bucket;

  @GET
  @Path("/{identifier}")
  public CompletionStage<byte[]> getCouchbaseByIdentifier(@PathParam("identifier") String identifier) {
    return CompletableFuture.completedFuture(bucket.defaultCollection().get(identifier).contentAsBytes());
  }
}

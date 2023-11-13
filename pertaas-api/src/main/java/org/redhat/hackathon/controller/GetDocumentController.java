package org.redhat.hackathon.controller;

import com.couchbase.client.java.Bucket;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import java.nio.charset.StandardCharsets;

@Path("/getDocument")
public class GetDocumentController {

  @Inject
  Bucket bucket;

  @GET
  @Path("/{documentId}")
  @RunOnVirtualThread
  public byte[] getDocumentFromCouchbase(String documentId) {
    try {
      return bucket.defaultCollection().get(documentId).contentAsBytes();
    } catch (Exception e) {
      return "No data found".getBytes(StandardCharsets.UTF_8);
    }
  }

}

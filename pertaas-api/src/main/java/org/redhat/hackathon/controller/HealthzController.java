package org.redhat.hackathon.controller;

import io.smallrye.common.annotation.RunOnVirtualThread;
import io.vertx.core.json.JsonObject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Path("/healthz")
public class HealthzController {
    @GET
    public CompletionStage<String> getHealthz() {
        return CompletableFuture.completedFuture(JsonObject.of("success", true).encode());
    }

    @GET
    @Path("/1")
    public CompletionStage<String> getHealthz1() {
        return CompletableFuture.completedFuture(JsonObject.of("success", true, "endpoint", "Healthz 1 endpoint").encode());
    }

    @GET
    @Path("/2")
    public CompletionStage<String> getHealthz2() {
        return CompletableFuture.completedFuture(JsonObject.of("success", true, "endpoint", "Healthz 2 endpoint").encode());
    }

    @POST
    public CompletionStage<String> postHealthz(String body) {
        return CompletableFuture.completedFuture(JsonObject.of("success", true, "length", body != null ? body.length() : 0).encode());
    }

}

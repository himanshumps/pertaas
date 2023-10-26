package org.redhat.hackathon;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.event.Observes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Path("/healthz")
public class HealthzController {
    @GET
    public CompletionStage<String> getHealthz() {
        return CompletableFuture.completedFuture(JsonObject.of("success", true).encode());
    }


}

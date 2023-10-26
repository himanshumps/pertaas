package org.redhat.hackathon.couchbase;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Default;
import jakarta.inject.Inject;
import jakarta.ws.rs.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;

@ApplicationScoped
public class CouchbaseBucketBean {

    @Inject
    Cluster cluster;
    @ConfigProperty(defaultValue = "pertaas")
    String couchbaseBucket;

    @Produces
    @Default
    Bucket bucket() {
        Bucket bucket = cluster.bucket(couchbaseBucket);
        bucket.waitUntilReady(Duration.ofMinutes(1));
        return bucket;
    }




}

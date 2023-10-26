package org.redhat.hackathon.couchbase;

import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.ClusterOptions;
import com.couchbase.client.java.env.ClusterEnvironment;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;
import jakarta.inject.Inject;
import jakarta.ws.rs.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;

@ApplicationScoped
public class CouchbaseClusterBean {
    @Inject
    ClusterEnvironment clusterEnvironment;

    @ConfigProperty
    String couchbaseConnectionString;

    @ConfigProperty(defaultValue = "pertaas_user")
    String couchbaseUsername;

    @ConfigProperty
    String couchbasePassword;

    /**
     * Creates the couchbase cluster object and waits for a minute to initialize
     * @return The couchbase cluster object
     */
    @Produces
    @Default
    Cluster cluster() {
        Cluster cluster = Cluster.connect(couchbaseConnectionString,
                ClusterOptions.clusterOptions(couchbaseUsername, couchbasePassword)
                        .environment(clusterEnvironment));
        cluster.waitUntilReady(Duration.ofMinutes(1));
        return cluster;
    }
}

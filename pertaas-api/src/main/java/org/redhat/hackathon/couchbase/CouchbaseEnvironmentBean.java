package org.redhat.hackathon.couchbase;

import com.couchbase.client.core.env.IoConfig;
import com.couchbase.client.java.env.ClusterEnvironment;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;
import jakarta.ws.rs.Produces;

@ApplicationScoped
public class CouchbaseEnvironmentBean {

  @Produces
  @Default
  ClusterEnvironment clusterEnvironment() {
    return ClusterEnvironment.builder().ioConfig(IoConfig.enableDnsSrv(false)).build();
  }
}

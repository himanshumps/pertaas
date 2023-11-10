package org.redhat.hackathon.job;

import io.fabric8.openshift.client.OpenShiftClient;
import io.vertx.core.json.Json;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import java.util.ArrayList;
import java.util.List;

@Path("/images")
public class ImagestreamController {
    @Inject
    OpenShiftClient openShiftClient;

    @GET
    public String getImages() {
        List<ImageNameAndDetails> imageNameAndDetailsList = new ArrayList<>();
        openShiftClient.imageStreams().list().getItems().forEach(imageStream -> {
            if(imageStream.getMetadata().getName().startsWith("pertaas-j-")) {
                imageNameAndDetailsList.add(new ImageNameAndDetails(imageStream.getMetadata().getName(), imageStream.getMetadata().getAnnotations().get("pertaas-image-description")));
            }
        });
        return Json.encode(imageNameAndDetailsList);
    }

    record ImageNameAndDetails(String name, String description) {};

}

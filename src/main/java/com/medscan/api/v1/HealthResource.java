package com.medscan.api.v1;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/v1/health")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
public class HealthResource {

    @GET
    public HealthResponse getHealth() {
        return new HealthResponse("UP");
    }
}

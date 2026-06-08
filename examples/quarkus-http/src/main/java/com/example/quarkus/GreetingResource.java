package com.example.quarkus;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Path("/config-greeting")
public final class GreetingResource {
    @ConfigProperty(name = "zolt.greeting")
    String greeting;

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String greeting() {
        return greeting;
    }
}

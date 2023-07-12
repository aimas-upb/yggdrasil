package org.hyperagents.yggdrasil.auth.http;

import org.apache.http.HttpStatus;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;

public class WACHandler {
  private static final Logger LOGGER = LoggerFactory.getLogger(WACHandler.class.getName());
  private Vertx vertx;
  
  public WACHandler(Vertx vertx) {
    this.vertx = vertx;
  }

  public void handleWACRepresentation(RoutingContext context) {
    LOGGER.info("Handling WAC Representation retrieval action...");
    
    String entityRepresentation = context.getBodyAsString();
    String envName = context.pathParam("envid");
    String wkspName = context.pathParam("wkspid");
    String artifactName = context.pathParam("artid");

    HttpServerRequest request = context.request();
    String agentId = request.getHeader("X-Agent-WebID");

    if (agentId == null) {
      context.response().setStatusCode(HttpStatus.SC_UNAUTHORIZED).end();
    }
  }
}

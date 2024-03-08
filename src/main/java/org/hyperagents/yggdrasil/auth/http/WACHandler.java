package org.hyperagents.yggdrasil.auth.http;

import java.util.Optional;

import org.apache.http.HttpStatus;
import org.hyperagents.yggdrasil.auth.AuthorizationRegistry;
import org.hyperagents.yggdrasil.auth.model.AuthorizationAccessType;
import org.hyperagents.yggdrasil.http.HttpEntityHandler;
import org.hyperagents.yggdrasil.http.HttpInterfaceConfig;
import org.hyperagents.yggdrasil.store.RdfStore;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;

public class WACHandler {
 
  private static final Logger LOGGER = LoggerFactory.getLogger(WACHandler.class.getName());
  private Vertx vertx;
  
  public WACHandler(Vertx vertx) {
    this.vertx = vertx;
  }

  /**
   * This method is invoked by the Yggdrasil HTTP server to handle a request to retrieve the WAC representation of an entity.
   * @param context
   */
  public void handleWACRepresentation(RoutingContext context) {
    LOGGER.info("Handling WAC Representation retrieval action...");
    
    // obtain the entity IRI by concatenating the base URI with the request path up to the second to last path segment, which will contain the entity ID
    HttpInterfaceConfig httpConfig = new HttpInterfaceConfig(Vertx.currentContext().config());
    String entityPath = context.request().path();
    String entityIRI = httpConfig.getBaseUri() + entityPath.substring(0, entityPath.lastIndexOf("/"));
    
    AuthorizationRegistry authRegistry = AuthorizationRegistry.getInstance();

    // get the WAC document URI from the authorization registry for the entity
    Optional<String> wacDocumentURI = authRegistry.getAuthorisationDocumentURI(entityIRI);

    // if there is no WAC document URI for the entity, return a 404 Not Found response
    if (!wacDocumentURI.isPresent()) {
      LOGGER.info("No WAC document URI found for entity with URI: " + entityIRI);
      context.response().setStatusCode(HttpStatus.SC_NOT_FOUND).end();
      return;
    }
    else {
      // otherwise, send a request to the RdfStoreVerticle event bus to retrieve the WAC document
      LOGGER.info("Sending request to retrieve WAC document with URI: " + wacDocumentURI.get() + " ...");
      DeliveryOptions options = new DeliveryOptions();
      options.addHeader(HttpEntityHandler.REQUEST_METHOD, RdfStore.GET_ENTITY);
      options.addHeader(HttpEntityHandler.REQUEST_URI, wacDocumentURI.get());

      vertx.eventBus().request(RdfStore.BUS_ADDRESS, null, options, reply -> {
        if (reply.succeeded()) {
          // if the reply is successful, return a 200 OK response with the WAC document
          LOGGER.info("WAC document with URI: " + wacDocumentURI.get() + " successfully retrieved.");
          context.response().setStatusCode(HttpStatus.SC_OK).end(reply.result().body().toString());
        }
        else {
          // if reply is a 404 Not Found, return a 404 Not Found response
          // check if the reply is a ReplyException
          if (reply.cause() instanceof ReplyException) {
            if (((ReplyException)reply.cause()).failureCode() == HttpStatus.SC_NOT_FOUND) {
              LOGGER.info("WAC document with URI: " + wacDocumentURI.get() + " not found. Sending 404 Not Found response.");
              context.response().setStatusCode(HttpStatus.SC_NOT_FOUND).end();
            }
            else {
              // otherwise return a 500 Internal Server Error response
              LOGGER.info("ReplyException retrieving WAC document with URI: " + wacDocumentURI.get() + ". Sending 500 Internal Server Error response.");
              context.response().setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR).end();
            }
          }
          else {
            // otherwise return a 500 Internal Server Error response
            LOGGER.info("Error retrieving WAC document with URI: " + wacDocumentURI.get() + ". Reason: " + reply.cause().getMessage());
            context.response().setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR).end();
          }
        }
      });

    }

    // String entityRepresentation = context.getBodyAsString();
    // String envName = context.pathParam("envid");
    // String wkspName = context.pathParam("wkspid");
    // String artifactName = context.pathParam("artid");
  }


  /**
   * This method is invoked by the Yggdrasil HTTP server to filter access to the requested artifact resource
   * @param context the routing context
   */
  public void filterAccess(RoutingContext context) {
    // We need to check whether the entity is protected by a shared context web access control list
    // If so, we need to validate the access request by sending a request to the WAC Handler event bus with a request to validate the access
    HttpInterfaceConfig httpConfig = new HttpInterfaceConfig(Vertx.currentContext().config());
    
    // obtain the entity IRI by concatenating the base URI with the request path up to the second to last path segment, which will contain the artifact id
    String requestPath = context.request().path();
    String artifactIRI = httpConfig.getBaseUri() + requestPath.substring(0, requestPath.lastIndexOf("/"));
    
    // obtain the agent's web id from the request header
    String agentWebId = context.request().getHeader("X-Agent-WebID");

    LOGGER.info("Handling Authorization validation for resource with URI: " + artifactIRI 
      + " invoked by agent with WebID: " + agentWebId);
    
    AuthorizationRegistry authRegistry = AuthorizationRegistry.getInstance();

    // For now we only handle the case where the agent invokes the artifact action using a POST method, so we need to 
    // check whether the artifact is write protected by a Shared Context Access Authorization
    if (authRegistry.isWriteProtected(artifactIRI)) {
      // In this case we need to validate the access request, by sending a request to the WAC Handler event bus
      // with a request to validate the access. We do this because the check will involve a federated SPAQRL query and
      // we want to avoid blocking the main event loop
      DeliveryOptions options = new DeliveryOptions();
      options.addHeader(WACVerticle.WAC_METHOD, WACVerticle.VALIDATE_AUTHORIZATION);
      options.addHeader(WACVerticle.ACCESSED_RESOURCE_URI, artifactIRI);
      options.addHeader(WACVerticle.ACCESS_TYPE, AuthorizationAccessType.WRITE.toString());
      options.addHeader(WACVerticle.AGENT_WEBID, agentWebId);

      LOGGER.info("Sending request to validate access to resource with URI: " + artifactIRI + " ...");
      LOGGER.info("Delivery options: " + options.toString());

      vertx.eventBus().request(WACVerticle.BUS_ADDRESS, null, options, reply -> {
        if (reply.succeeded()) {
          // if the access is granted, we let the request go through
          LOGGER.info("Access to resource with URI: " + artifactIRI + " granted.");
          context.next();
        }
        else {
          // otherwise we return an error
          LOGGER.info("Access to resource with URI: " + artifactIRI + " denied.");
          context.response().setStatusCode(HttpStatus.SC_UNAUTHORIZED).end();
        }
      });
    }
    else {
      // otherwise we just let the request go through
      LOGGER.info("Resource with URI: " + artifactIRI + " is not write protected. Letting request go through.");
      context.next();
    }
  }
}

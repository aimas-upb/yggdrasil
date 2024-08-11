package org.hyperagents.yggdrasil.store;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.apache.commons.rdf.api.Graph;
import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.RDFSyntax;
import org.apache.commons.rdf.api.Triple;
import org.apache.commons.rdf.rdf4j.RDF4J;
import org.apache.http.HttpStatus;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.util.ModelBuilder;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.hyperagents.yggdrasil.auth.AuthorizationRegistry;
import org.hyperagents.yggdrasil.auth.model.ContextBasedAuthorization;
import org.hyperagents.yggdrasil.context.http.Utils.Tuple;
import org.hyperagents.yggdrasil.http.HttpEntityHandler;
import org.hyperagents.yggdrasil.store.impl.RdfStoreFactory;
import org.hyperagents.yggdrasil.websub.HttpNotificationVerticle;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.client.WebClient;

/*
 * Stores the RDF graphs representing the instantiated artifacts
 *
 */
public class RdfStoreVerticle extends AbstractVerticle {
  private final static Logger LOGGER = LoggerFactory.getLogger(RdfStoreVerticle.class.getName());

  private RdfStore store;
  private final RDF4J rdf = new RDF4J();
  private WebClient client;

  @Override
  public void start() {
    store = RdfStoreFactory.createStore(config().getJsonObject("rdf-store", null));
    client = WebClient.create(vertx);

    EventBus eventBus = vertx.eventBus();
    eventBus.consumer(RdfStore.BUS_ADDRESS, this::handleEntityRequest);
  }

  private void handleEntityRequest(Message<String> message) {
    try {
      String requestIRIString = message.headers().get(HttpEntityHandler.REQUEST_URI);
      IRI requestIRI = store.createIRI(requestIRIString);

      String requestMethod = message.headers().get(HttpEntityHandler.REQUEST_METHOD);
      switch (requestMethod) {
        case RdfStore.GET_ENTITY:
          handleGetEntity(requestIRI, message);
          break;
        case RdfStore.CREATE_ENTITY:
          handleCreateEntity(requestIRI, message);
          break;
        case RdfStore.PATCH_ENTITY:
          handlePatchEntity(requestIRI, message);
          break;
        case RdfStore.UPDATE_ENTITY:
          handleUpdateEntity(requestIRI, message);
          break;
        case RdfStore.DELETE_ENTITY:
          handleDeleteEntity(requestIRI, message);
          break;
        default:
        	break;
      }
    }
    catch (IOException | IllegalArgumentException e) {
      LOGGER.error(e.getMessage());
      replyFailed(message);
    }
  }

  private void handleGetEntity(IRI requestIRI, Message<String> message)
      throws IllegalArgumentException, IOException {
    Optional<Graph> result = store.getEntityGraph(requestIRI);

    if (result.isPresent() && result.get().size() > 0) {
      replyWithPayload(message, store.graphToString(result.get(), RDFSyntax.TURTLE));
    } else {
      replyEntityNotFound(message);
    }
  }

  /**
   * Creates an entity and adds it to the store
   * @param requestIRI	IRI where the request originated from
   * @param message Request
   * @throws IllegalArgumentException
   * @throws IOException
   */
  private void handleCreateEntity(IRI requestIRI, Message<String> message)
      throws IllegalArgumentException, IOException {
	  // Create IRI for new entity
    String slug = message.headers().get(HttpEntityHandler.ENTITY_URI_HINT);
    // String contentType = message.headers().get(HttpEntityHandler.CONTENT_TYPE);
    String entityIRIString = generateEntityIRI(requestIRI.getIRIString(), slug);

    IRI entityIRI = store.createIRI(entityIRIString);

    if (message.body() == null || message.body().isEmpty()) {
      replyFailed(message);
    } else {
      // Replace all null relative IRIs with the IRI generated for this entity
      String entityGraphStr = message.body();

//      if (contentType != null && contentType.equals("application/ld+json")) {
//        entityGraph = store.stringToGraph(entityGraphStr, entityIRI, RDFSyntax.JSONLD);
//      } else {
        entityGraphStr = entityGraphStr.replaceAll("<>", "<" + entityIRIString + ">");
        Tuple<Graph, Model> entityReprTuple = store.stringToGraph(entityGraphStr, entityIRI, RDFSyntax.TURTLE);
//      }

      Graph entityGraph = entityReprTuple.getFirst();
      Model entityModel = entityReprTuple.getSecond();

      // check to see if we can extract a Shared Context Access or Control Authorization from the entity graph
      // First create a eclipse.rdf4j.model from the org.apache.commons.rdf.api.Graph entityGraph
      // ModelBuilder builder = new ModelBuilder();
      // entityGraph.stream().forEach(triple -> builder.add(triple.getSubject().ntriplesString(),
      //     triple.getPredicate().ntriplesString(), triple.getObject().ntriplesString()));
      // Model entityModel = builder.build();

      AuthorizationRegistry authRegistry = AuthorizationRegistry.getInstance();
      
      // extract the access authorizations from the entity Model, then register them with the AuthorizationRegistry
      List<ContextBasedAuthorization> accessAuthorizations = ContextBasedAuthorization.fromModel(entityModel);
      for (ContextBasedAuthorization auth : accessAuthorizations) {
        authRegistry.addContextAuthorisation(entityIRI.getIRIString(), auth);
      }

      // create an all authorizations list by combining the access and control authorizations and use the list to create the document graph
      List<ContextBasedAuthorization> allAuthorizations = new ArrayList<>();
      allAuthorizations.addAll(accessAuthorizations);

      if (!allAuthorizations.isEmpty()) {
        LOGGER.info("Found Shared Context Authorizations in entity graph");
        
        // create the document IRI by appending the path /wac to the entity IRI
        IRI documentIRI = store.createIRI(entityIRIString + "/wac");

        // create a new acl document graph by adding the authorizations to the entity graph. The graph name is the document IRI
        Graph aclGraph = rdf.createGraph();
        for (ContextBasedAuthorization authorization : allAuthorizations) {
          // obtain the Model from the authorization
          Model authModel = authorization.toModel().values().iterator().next();
          
          // convert the Model to a Graph
          Graph authGraph = rdf.asGraph(authModel);

          // add the triples from the authorization graph to the acl graph
          authGraph.stream().forEach(triple -> aclGraph.add(triple.getSubject(), triple.getPredicate(), triple.getObject()));
        }

        // store the acl graph
        store.createEntityGraph(documentIRI, aclGraph);
      }

      // TODO: seems like legacy integration from Simon Bienz, to be reviewed
      IRI subscribesIri = rdf.createIRI("http://w3id.org/eve#subscribes");
      if (entityGraph.contains(null, subscribesIri, null)) {
        LOGGER.info("Crawler subscription link found!");
        subscribeCrawler(entityGraph);
      }

      entityGraph = addContainmentTriples(entityIRI, entityGraph);

      store.createEntityGraph(entityIRI, entityGraph);
      replyWithPayload(message, entityGraphStr);

//      DeliveryOptions options = new DeliveryOptions()
//          .addHeader(HttpEntityHandler.REQUEST_METHOD, HttpNotificationVerticle
//              .ENTITY_CREATED)
//          .addHeader(HttpEntityHandler.REQUEST_URI, entityIRIString);
//
//      vertx.eventBus().send(HttpNotificationVerticle.BUS_ADDRESS, entityGraphStr, options);

      pushNotification(HttpNotificationVerticle.ENTITY_CREATED, requestIRI, entityGraphStr);
    }
  }

  private Graph addContainmentTriples(IRI entityIRI, Graph entityGraph)
      throws IllegalArgumentException, IOException {
    LOGGER.info("Looking for containment triples for: " + entityIRI.getIRIString());

    if (entityGraph.contains(entityIRI, store.createIRI(RDF.TYPE.stringValue()),
        store.createIRI(("http://w3id.org/eve#Artifact")))) {
      String artifactIRI = entityIRI.getIRIString();
      IRI workspaceIRI = store.createIRI(artifactIRI.substring(0, artifactIRI.indexOf("/artifacts")));

      LOGGER.info("Found workspace IRI: " + workspaceIRI);

      Optional<Graph> workspaceGraph = store.getEntityGraph(workspaceIRI);
      if (workspaceGraph.isPresent()) {
        Graph wkspGraph = workspaceGraph.get();
        LOGGER.info("Found workspace graph: " + wkspGraph);
        wkspGraph.add(workspaceIRI, store.createIRI("http://w3id.org/eve#contains"), entityIRI);
        // TODO: updateEntityGraph would yield 404, to be investigated
        store.createEntityGraph(workspaceIRI, wkspGraph);

        String entityGraphStr = store.graphToString(wkspGraph, RDFSyntax.TURTLE);
        pushNotification(HttpNotificationVerticle.ENTITY_CHANGED, workspaceIRI, entityGraphStr);
      }
    } else if (entityGraph.contains(entityIRI, store.createIRI(RDF.TYPE.stringValue()),
        store.createIRI(("http://w3id.org/eve#WorkspaceArtifact")))) {
      String workspaceIRI = entityIRI.getIRIString();
      IRI envIRI = store.createIRI(workspaceIRI.substring(0, workspaceIRI.indexOf("/workspaces")));

      LOGGER.info("Found env IRI: " + workspaceIRI);

      Optional<Graph> envGraph = store.getEntityGraph(envIRI);
      if (envGraph.isPresent()) {
        Graph graph = envGraph.get();
        LOGGER.info("Found env graph: " + graph);
        graph.add(envIRI, store.createIRI("http://w3id.org/eve#contains"), entityIRI);
        // TODO: updateEntityGraph would yield 404, to be investigated
        store.createEntityGraph(envIRI, graph);

        String entityGraphStr = store.graphToString(graph, RDFSyntax.TURTLE);
        pushNotification(HttpNotificationVerticle.ENTITY_CHANGED, envIRI, entityGraphStr);
      }
    }

    return entityGraph;
  }

  private void handlePatchEntity(IRI requestIRI, Message<String> message)
      throws IllegalArgumentException, IOException {
    // TODO
  }

  private void handleUpdateEntity(IRI requestIRI, Message<String> message)
      throws IllegalArgumentException, IOException {
    
    // Check to see if requestIRI contains a port number; if it does, remove it from the IRI
    // To do so, split the request IRI into its domain and path parts; then, remove the port number from the domain part
    String requestIRIString = requestIRI.getIRIString();
    String domain = requestIRIString.substring(0, requestIRIString.indexOf("/", requestIRIString.indexOf("//") + 2));
    String path = requestIRIString.substring(requestIRIString.indexOf("/", requestIRIString.indexOf("//") + 2));
    // Use a regex to remove the port number from the domain part
    domain = domain.replaceAll(":[0-9]+", "");
    requestIRIString = domain + path;
    requestIRI = store.createIRI(requestIRIString);
    // TODO: fix this hack with the port number in the IRI
    
    if (store.containsEntityGraph(requestIRI)) {
      if (message.body() == null || message.body().isEmpty()) {
        replyFailed(message);
      } else {
        Tuple<Graph, Model> entityReprTuple = store.stringToGraph(message.body(), requestIRI, RDFSyntax.TURTLE);
        Graph entityGraph = entityReprTuple.getFirst();
        store.updateEntityGraph(requestIRI, entityGraph);

        Optional<Graph> result = store.getEntityGraph(requestIRI);

        if (result.isPresent() && result.get().size() > 0) {
          String entityGraphStr = store.graphToString(result.get(), RDFSyntax.TURTLE);
          replyWithPayload(message, entityGraphStr);

          LOGGER.info("Sending update notification for " + requestIRI.getIRIString());

//          DeliveryOptions options = new DeliveryOptions()
//              .addHeader(HttpEntityHandler.REQUEST_METHOD, HttpNotificationVerticle
//                  .ENTITY_CHANGED)
//              .addHeader(HttpEntityHandler.REQUEST_URI, requestIRI.getIRIString());
//
//          vertx.eventBus().send(HttpNotificationVerticle.BUS_ADDRESS, entityGraphStr,
//              options);

          pushNotification(HttpNotificationVerticle.ENTITY_CHANGED, requestIRI, entityGraphStr);
        } else {
          replyFailed(message);
        }
      }
    } else {
      replyEntityNotFound(message);
    }
  }

  private void handleDeleteEntity(IRI requestIRI, Message<String> message)
      throws IllegalArgumentException, IOException {
    Optional<Graph> result = store.getEntityGraph(requestIRI);

    if (result.isPresent() && result.get().size() > 0) {
      String entityGraphStr = store.graphToString(result.get(), RDFSyntax.TURTLE);
      store.deleteEntityGraph(requestIRI);
      replyWithPayload(message, entityGraphStr);

//      DeliveryOptions options = new DeliveryOptions()
//          .addHeader(HttpEntityHandler.REQUEST_METHOD, HttpNotificationVerticle
//              .ENTITY_DELETED)
//          .addHeader(HttpEntityHandler.REQUEST_URI, requestIRI.getIRIString());
//
//      vertx.eventBus().send(HttpNotificationVerticle.BUS_ADDRESS, entityGraphStr, options);

      pushNotification(HttpNotificationVerticle.ENTITY_DELETED, requestIRI, entityGraphStr);
    } else {
      replyEntityNotFound(message);
    }
  }

  private void pushNotification(String notificationType, IRI requestIRI, String entityGraph) {
    DeliveryOptions options = new DeliveryOptions()
        .addHeader(HttpEntityHandler.REQUEST_METHOD, notificationType)
        .addHeader(HttpEntityHandler.REQUEST_URI, requestIRI.getIRIString());

    vertx.eventBus().send(HttpNotificationVerticle.BUS_ADDRESS, entityGraph, options);
  }

  private void replyWithPayload(Message<String> message, String payload) {
    message.reply(payload);
  }

  private void replyFailed(Message<String> message) {
    message.fail(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Store request failed.");
  }

  private void replyEntityNotFound(Message<String> message) {
    message.fail(HttpStatus.SC_NOT_FOUND, "Entity not found.");
  }

  private String generateEntityIRI(String requestIRI, String hint) {
    if (!requestIRI.endsWith("/")) {
      requestIRI = requestIRI.concat("/");
    }

    String candidateIRI;

    // Try to generate an IRI using the hint provided in the initial request
    if (hint != null && !hint.isEmpty()) {
      candidateIRI = requestIRI.concat(hint);
      if (!store.containsEntityGraph(store.createIRI(candidateIRI))) {
        return candidateIRI;
      }
    }

    // Generate a new IRI
    do {
      candidateIRI = requestIRI.concat(UUID.randomUUID().toString());
    } while (store.containsEntityGraph(store.createIRI(candidateIRI)));

    return candidateIRI;
  }

  private void subscribeCrawler(Graph entityGraph) {
    IRI subscribesIri = rdf.createIRI("http://w3id.org/eve#subscribes");
    for (Triple t : entityGraph.iterate(null, subscribesIri, null)) {
      String crawlerUrl = t.getObject().toString();
      LOGGER.info(crawlerUrl);

      String id = t.getSubject().toString();
      client.postAbs(crawlerUrl).sendBuffer(Buffer.buffer(id), response -> {
        LOGGER.info("Registered at crawler: " + crawlerUrl);
      });
    }
  }
}

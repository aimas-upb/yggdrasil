package org.hyperagents.yggdrasil.http;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.apache.http.HttpStatus;
import org.hyperagents.yggdrasil.auth.http.WACHandler;
import org.hyperagents.yggdrasil.context.http.ContextMgmtHandler;

/**
 * This verticle exposes an HTTP/1.1 interface for Yggdrasil. All requests are forwarded to a
 * corresponding handler.
 */
public class HttpServerVerticle extends AbstractVerticle {

  @Override
  public void start() {
    HttpServer server = vertx.createHttpServer();

    Router router = createRouter();
    HttpInterfaceConfig httpConfig = new HttpInterfaceConfig(config());
    server.requestHandler(router).listen(httpConfig.getPort(), httpConfig.getHost());
  }

  /**
   * The HTTP API is defined here when creating the router.
   */
  private Router createRouter() {
    Router router = Router.router(vertx);

    router.route().handler(BodyHandler.create());

    router.get("/").handler((routingContext) -> routingContext.response()
      .setStatusCode(HttpStatus.SC_OK)
      .end("Yggdrasil v0.0"));

    HttpEntityHandler handler = new HttpEntityHandler(vertx);
    WACHandler wacHandler = new WACHandler(vertx);
    ContextMgmtHandler ctxHandler = new ContextMgmtHandler(vertx);

    router.get("/environments/:envid/").handler(handler::handleRedirectWithoutSlash);
    router.get("/environments/:envid").handler(handler::handleGetEntity);
//    router.post("/environments/").handler(handler::handleCreateEntity);
    router.post("/environments/").handler(handler::handleCreateEnvironment);
    router.put("/environments/:envid").handler(handler::handleUpdateEntity);
    router.delete("/environments/:envid").handler(handler::handleDeleteEntity);

    router.get("/environments/:envid/workspaces/:wkspid/").handler(handler::handleRedirectWithoutSlash);
    router.get("/environments/:envid/workspaces/:wkspid").handler(handler::handleGetEntity);
    router.post("/environments/:envid/workspaces/").consumes("text/turtle")
        .handler(handler::handleCreateEntity);
    router.post("/environments/:envid/workspaces/").handler(handler::handleCreateWorkspace);
    router.put("/environments/:envid/workspaces/:wkspid").handler(handler::handleUpdateEntity);
    router.delete("/environments/:envid/workspaces/:wkspid").handler(handler::handleDeleteEntity);

    router.get("/environments/:envid/workspaces/:wkspid/artifacts/:artid/").handler(handler::handleRedirectWithoutSlash);
    router.get("/environments/:envid/workspaces/:wkspid/artifacts/:artid").handler(handler::handleGetEntity);
    router.post("/environments/:envid/workspaces/:wkspid/artifacts/").consumes("text/turtle")
        .handler(handler::handleCreateEntity);
    router.post("/environments/:envid/workspaces/:wkspid/artifacts/").consumes("application/json")
        .handler(handler::handleCreateArtifact);
    router.put("/environments/:envid/workspaces/:wkspid/artifacts/:artid").handler(handler::handleUpdateEntity);
    router.delete("/environments/:envid/workspaces/:wkspid/artifacts/:artid").handler(handler::handleDeleteEntity);
    
    // Route all paths that require representation of the access control authorizations to the WAC handler
    // For now, we are only adding the routes for Artifact web access control and we are only serving the GET and HEAD methods
    // TODO: add routes for environment and workspace web access control
    // TODO: add routes for POST, PUT, DELETE methods
    router.route("/environments/:envid/workspaces/:wkspid/artifacts/:artid/wac").handler(wacHandler::handleWACRepresentation);
    
    // route all artifact action first through a WAC filter to check if the agent is authorized to perform the action
    Router wacRouter = Router.router(vertx);
    wacRouter.route("/*").handler(wacHandler::filterAccess);

    Router artifactActionRouter = Router.router(vertx);
    artifactActionRouter.route("/*").handler(handler::handleAction);

    // make the action router a subrouter of the WAC router to handle the artifact actions after the WAC filter
    wacRouter.mountSubRouter("/", artifactActionRouter);

    //router.route("/environments/:envid/workspaces/:wkspid/artifacts/:artid/*").handler(handler::handleAction);

    // add the wacRouter as a subrouter for artifact affordances of the main router
    router.mountSubRouter("/environments/:envid/workspaces/:wkspid/artifacts/:artid", wacRouter);

    // route artifact manual requests
    // TODO: this feature was implemented for the WWW2020 demo, a manual is any RDF graph
    router.get("/manuals/:wkspid").handler(handler::handleGetEntity);
    router.post("/manuals/").handler(handler::handleCreateEntity);
    router.put("/manuals/:wkspid").handler(handler::handleUpdateEntity);
    router.delete("/manuals/:wkspid").handler(handler::handleDeleteEntity);

    router.post("/hub/").handler(handler::handleEntitySubscription);

    return router;
  }
}

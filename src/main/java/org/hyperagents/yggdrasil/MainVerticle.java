package org.hyperagents.yggdrasil;

import org.hyperagents.yggdrasil.auth.http.WACVerticle;
import org.hyperagents.yggdrasil.cartago.CartagoVerticle;
import org.hyperagents.yggdrasil.context.http.ContextMgmtVerticle;
import org.hyperagents.yggdrasil.http.HttpInterfaceConfig;
import org.hyperagents.yggdrasil.http.HttpServerVerticle;
import org.hyperagents.yggdrasil.store.RdfStoreVerticle;
import org.hyperagents.yggdrasil.websub.HttpNotificationVerticle;
import java.io.File;
import java.util.concurrent.TimeUnit;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.json.JsonObject;

public class MainVerticle extends AbstractVerticle {

  @Override
  public void start() {
    HttpInterfaceConfig serverConfig = new HttpInterfaceConfig(config());
    int port = serverConfig.getPort();
    String host = serverConfig.getHost();
    String baseUri = serverConfig.getBaseUri();
    // Deploy the main HTTP server verticle for this Yggdrasil instance
    vertx.deployVerticle(new HttpServerVerticle(),
        new DeploymentOptions().setConfig(config())
        .setMaxWorkerExecuteTime(600000).setMaxWorkerExecuteTimeUnit(TimeUnit.MILLISECONDS)
      );

    // Deploy the RDF store verticle for this Yggdrasil instance
    vertx.deployVerticle(new RdfStoreVerticle(),
        new DeploymentOptions().setWorker(true).setConfig(config())
        .setMaxWorkerExecuteTime(600000).setMaxWorkerExecuteTimeUnit(TimeUnit.MILLISECONDS)
      );

    // Deploy the HTTP notification verticle that allows for modifications 
    // to the internal state of an artifact to reflect in its RDF representation and vice versa
    vertx.deployVerticle(new HttpNotificationVerticle(),
        new DeploymentOptions().setWorker(true).setConfig(config())
        .setMaxWorkerExecuteTime(600000).setMaxWorkerExecuteTimeUnit(TimeUnit.MILLISECONDS)
      );

    JsonObject knownArtifacts = new JsonObject()
        // .put("https://ci.mines-stetienne.fr/kg/ontology#PhantomX_3D",
        //     "org.hyperagents.yggdrasil.cartago.artifacts.PhantomX3D")
        // .put("http://example.org/Counter", "org.hyperagents.yggdrasil.cartago.artifacts.Counter")
        // .put("http://example.org/SpatialCalculator2D", "org.hyperagents.yggdrasil.cartago"
        //     + ".SpatialCalculator2D")
        .put(baseUri + "/HueLamp", "org.hyperagents.yggdrasil.auth.artifacts.AuthHue");

    JsonObject cartagoConfig = config();
    cartagoConfig.put("known-artifacts", knownArtifacts);

    // Deploy the Cartago verticle
    vertx.deployVerticle(new CartagoVerticle(),
        new DeploymentOptions().setWorker(true).setConfig(cartagoConfig)
        .setMaxWorkerExecuteTime(600000).setMaxWorkerExecuteTimeUnit(TimeUnit.MILLISECONDS)
      );
    
    // Deploy a verticle to handle context management.
    // This includes: managing the RDF graphs for static and profiled context received from deployed artifacts, 
    // managing the RDF streams with dynamic context data, 
    // managing the group membership RDF graph containing information about agent membership in ContextDomainGroups,
    // dispatching context-based authorization validation requests to the WAC verticle

    // set up a default configuration for the context management verticle
    JsonObject contextMgmtConfig = config();
    
    // add the URI of the service to the configuration
    contextMgmtConfig.put("service-uri", baseUri + "/environments/upb_hmas/ctxmgmt");

    String testResourcesRelativePath = "src/test/resources";
    File testResourcesDir = new File(testResourcesRelativePath);
    String baseResourcesFilePath = testResourcesDir.getAbsolutePath();

    // for it to work on windows

    if (System.getProperty("os.name").toLowerCase().contains("windows")) {
      baseResourcesFilePath = baseResourcesFilePath.replace("\\", "/");
      // and add a leading slash
      baseResourcesFilePath = "/" + baseResourcesFilePath;
    }

    // The default configuration includes for this service contains local file URLs for the static context and profiled context graphs
    // deployed at the level of the Yggdrasil instance. The configuration can be modified to include other sources of context graphs.
    contextMgmtConfig.put("static-context", "file://" + baseResourcesFilePath + "/upb-hmas-static-context.ttl");
    contextMgmtConfig.put("profiled-context", "file://" + baseResourcesFilePath + "/upb-hmas-profiled-context.ttl");

    JsonObject dynamicContextConfig = new JsonObject()
        .put(baseUri + "/LocatedAt", baseUri + "/environments/upb_hmas/ctxmgmt/streams/LocatedAt");
    contextMgmtConfig.put("dynamic-context", dynamicContextConfig);

    // The configuration also includes a map of context domains, 
    // each domain being identified by the URI of the ContextEntity playing the object role in the ContextAssertion
    JsonObject contextDomainConfig = new JsonObject()
      .put(baseUri + "/environments/upb_hmas/ctxmgmt/domains/lab308Domain", new JsonObject()
        .put("assertion", baseUri + "/LocatedAt")
        .put("entity", baseUri + "/lab308")
        .put("stream", baseUri + "/environments/upb_hmas/ctxmgmt/streams/LocatedAt")
        .put("generatorClass", "org.hyperagents.yggdrasil.context.LocatedAtContextStream")
        .put("rule", "file://" + baseResourcesFilePath + "/lab308membership.rspql")
        .put("engine-config", "file://" + baseResourcesFilePath + "/lab308membership-csparql-engine-config.properties")
      );
    contextMgmtConfig.put("context-domains", contextDomainConfig);
    
    // The configuration also includes a mapping of artifact URIs to the URI of access control policies (given as SHACL shapes) 
    // that govern access to the artifact
    JsonObject artifactPolicyConfig = new JsonObject()
      .put(baseUri + "/environments/upb_hmas/workspaces/precis/artifacts/light308", "file://" + baseResourcesFilePath + "/upb-hmas-context-access-condition-shapes.ttl");
    contextMgmtConfig.put("artifact-policies", artifactPolicyConfig);
    
    vertx.deployVerticle(new ContextMgmtVerticle(),
        new DeploymentOptions().setWorker(true).setConfig(contextMgmtConfig)
        .setMaxWorkerExecuteTime(600000).setMaxWorkerExecuteTimeUnit(TimeUnit.MILLISECONDS)
      );

    // Deploy the WAC verticle
    vertx.deployVerticle(new WACVerticle(),
        new DeploymentOptions().setWorker(true).setConfig(config())
        .setMaxWorkerExecuteTime(600000).setMaxWorkerExecuteTimeUnit(TimeUnit.MILLISECONDS)
      );
  }
}

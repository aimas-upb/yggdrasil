package org.hyperagents.yggdrasil.auth.artifacts;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.util.ModelBuilder;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.hyperagents.yggdrasil.auth.AuthorizationRegistry;
import org.hyperagents.yggdrasil.auth.model.AuthorizationAccessType;
import org.hyperagents.yggdrasil.auth.model.AuthorizedEntityType;
import org.hyperagents.yggdrasil.auth.model.CASHMERE;
import org.hyperagents.yggdrasil.auth.model.ContextBasedAuthorization;

import cartago.OPERATION;
import ch.unisg.ics.interactions.wot.td.schemas.ArraySchema;
import ch.unisg.ics.interactions.wot.td.schemas.StringSchema;
import ch.unisg.ics.interactions.wot.td.security.NoSecurityScheme;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;


public class AuthHue extends ContextAuthHypermediaArtifact {

  private static final String EXAMPLE_PREFIX = "http://example.org/";
  private static final String LIGHT308_PREFIX = "http://example.org/environments/upb_hmas/workspaces/precis/artifacts/light308/";
  private static final Logger LOGGER = LoggerFactory.getLogger(AuthHue.class.getName());

  private enum LightState {
    ON("on"),
    OFF("off");

    private final String state;

    LightState(String state) {
      this.state = state;
    }

    public String getState() {
      return state;
    }

    public static LightState fromString(String state) {
      for (LightState lightState : LightState.values()) {
        if (lightState.getState().equals(state)) {
          return lightState;
        }
      }
      return null;
    }
  }

  private String hueURIBase = LIGHT308_PREFIX;
  private String port = "8080";
  private LightState lightState = LightState.OFF;
  private String color = "green";

  public void init(String hueURIBase, String port, String lightState, String color) {
    this.hueURIBase = hueURIBase;
    this.port = port;
    this.lightState = LightState.fromString(lightState);
    this.color = color;

    // define the observable properties of the artifact
    defineObsProperty("lightState", this.lightState.getState());
    defineObsProperty("color", this.color);
  }

  @OPERATION
  public void state(String state) {
    LightState newState = LightState.fromString(state);

    if (newState == null) {
      failed("Invalid state: " + state);
    }

    if (newState == LightState.ON) {
      turnOn();
    } else {
      turnOff();
    }
  }

  private void turnOn() {
    this.lightState = LightState.ON;
    updateRepresentation("state", this.lightState.getState());
    // updateObsProperty("lightState", this.lightState.getState());
  }

  private void turnOff() {
    this.lightState = LightState.OFF;
    updateRepresentation("state", this.lightState.getState());
    // updateObsProperty("lightState", this.lightState.getState());
  }

  @OPERATION
  public void color(String color) {
    this.color = color;
    updateRepresentation("color", this.color);
  }

  private void updateRepresentation(String propertyLocalName, String value) {
    String agentId = this.getCurrentOpAgentId().getGlobalId();
    String uri = getArtifactUri();

    // we need to set the port in the URI which already contains the URI of the action
    // get the domain part of the URI
    String domain = uri.substring(0, uri.indexOf("/", uri.indexOf("//") + 2));
    // get the path part of the URI
    String path = uri.substring(uri.indexOf("/", uri.indexOf("//") + 2));
    // set the port in the domain part of the URI
    uri = domain + ":" + port + path;

    LOGGER.info("Updating the " + propertyLocalName + " representation of the artifact with a PUT request at address: " + uri);

    // The property we need to change in the metadata is obtained using the EXAMPLE_PREFIX + "state" string.
    // The value we want to set is the state of the light.
    ValueFactory rdfVals = SimpleValueFactory.getInstance();
    IRI property = rdfVals.createIRI(EXAMPLE_PREFIX + propertyLocalName);

    // Remove the old value of the property from the metadata
    metadata.remove(rdfVals.createIRI(getArtifactUri()), property, null, (Resource)null);

    // Add the new value of the property to the metadata
    metadata.add(rdfVals.createIRI(getArtifactUri()), property, rdfVals.createLiteral(value));

    // Retrieve the representation of the artifact
    String representation = getHypermediaDescription();

    // make the PUT HTTP request
    HttpClient client = HttpClients.createDefault();
    ClassicHttpRequest request = new BasicClassicHttpRequest("PUT", uri);

    // set the X-Agent-WebID header in the request
    request.setHeader("X-Agent-WebID", agentId);
    // set the Content-Type header in the request
    request.setHeader("Content-Type", "text/turtle");
    // set the body of the request as the representation of the artifact
    request.setEntity(new StringEntity(representation, ContentType.create("text/turtle")));

    try {
      client.execute(request);
    } catch (IOException e) {
      failed(e.getMessage());
    }
  }

  private void invokeAction(String action, String value) {
    String agentId = this.getCurrentOpAgentId().getGlobalId();
    String uri = hueURIBase + action;

    // we need to set the port in the URI which already contains the URI of the action
    // get the domain part of the URI
    String domain = uri.substring(0, uri.indexOf("/", uri.indexOf("//") + 2));
    // get the path part of the URI
    String path = uri.substring(uri.indexOf("/", uri.indexOf("//") + 2));
    // set the port in the domain part of the URI
    uri = domain + ":" + port + path;

    LOGGER.info("Updating the representation of the artifact at address: " + uri);
    
    HttpClient client = HttpClients.createDefault();
    ClassicHttpRequest request = new BasicClassicHttpRequest("POST", uri);

    // set the X-Agent-WebID header in the request
    request.setHeader("X-Agent-WebID", agentId);
    // set the Content-Type header in the request
    request.setHeader("Content-Type", "application/json");

    request.setEntity(new StringEntity('[' + '\"' + value + '\"' + ']', ContentType.create("application/json")));

    try {
      client.execute(request);
    } catch (IOException e) {
      failed(e.getMessage());
    }
  }

  @Override
  protected void registerInteractionAffordances() {

    registerActionAffordance(EXAMPLE_PREFIX + "LampState", "state", "/state",
        new ArraySchema.Builder().addSemanticType(EXAMPLE_PREFIX + "LampState")
            .addItem(new StringSchema.Builder().addEnum(new HashSet<String>(Arrays.asList("on", "off"))).build())
            .addMinItems(1)
            .addMaxItems(1)
            .build());
    

    registerActionAffordance(EXAMPLE_PREFIX + "LampColor", "color", "/color",
      new ArraySchema.Builder().addSemanticType(EXAMPLE_PREFIX + "LampColor")
          .addItem(new StringSchema.Builder().addEnum(new HashSet<String>(Arrays.asList("red", "green", "blue"))).build())
          .addMinItems(1)
          .addMaxItems(1)
          .build());
      
    // Add initial coordinates, these are currently hard-coded
    ModelBuilder builder = new ModelBuilder();
    ValueFactory rdf = SimpleValueFactory.getInstance();

    builder.add(getArtifactUri(), RDF.TYPE, rdf.createIRI(EXAMPLE_PREFIX + "HueLamp"));
    builder.add(getArtifactUri(), rdf.createIRI(EXAMPLE_PREFIX + "state"), rdf.createLiteral("off"));
    builder.add(getArtifactUri(), rdf.createIRI(EXAMPLE_PREFIX + "color"), rdf.createLiteral("green"));
    
    addMetadata(builder.build());

    setSecurityScheme(new NoSecurityScheme());
  }
  
  @Override
  protected void registerSharedContextAutorizations() {
    // add the Lab308ContextDomainGroup as a shared context requirement in the AuthorisationRegistry
    // First, create the read and write SharedContextAccessAuthorisation object

    // The URI for the ContextDomainGroup 
    ContextBasedAuthorization accessAuth = new ContextBasedAuthorization(getArtifactUri(), 
            Arrays.asList(AuthorizationAccessType.READ, AuthorizationAccessType.WRITE), AuthorizedEntityType.AGENT,
            CASHMERE.accessRequester.stringValue(),
            EXAMPLE_PREFIX + "light308AccessCondition");
    
    // register the authorization object for the artifact
    registerAuthorization(accessAuth);
    
    // add the read and write SharedContextAccessAuthorisation object to the AuthorisationRegistry
    AuthorizationRegistry authRegistry = AuthorizationRegistry.getInstance();
    authRegistry.addContextAuthorisation(getArtifactUri(), accessAuth);
  }
}

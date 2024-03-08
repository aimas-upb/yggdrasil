package org.hyperagents.yggdrasil.cartago.artifacts;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.util.ModelBuilder;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.hyperagents.yggdrasil.auth.AuthorizationRegistry;
import org.hyperagents.yggdrasil.auth.model.AuthorizationAccessType;
import org.hyperagents.yggdrasil.auth.model.AuthorizedEntityType;
import org.hyperagents.yggdrasil.auth.model.CASHMERE;
import org.hyperagents.yggdrasil.auth.model.ContextBasedAuthorization;
import org.hyperagents.yggdrasil.cartago.ContextAuthHypermediaArtifact;

import cartago.OPERATION;
import ch.unisg.ics.interactions.wot.td.schemas.StringSchema;
import ch.unisg.ics.interactions.wot.td.security.NoSecurityScheme;

public class AuthHue extends ContextAuthHypermediaArtifact {

  private static final String EXAMPLE_PREFIX = "http://example.org/";
  private static final String LIGHT308_PREFIX = "http://example.org/upb_hmas/workspaces/precis/artifacts/light308/";

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

  private String hueURIBase;
  private LightState lightState;
  private String color;

  public void init(String hueURIBase, String lightState, String color) {
    this.hueURIBase = hueURIBase;
    this.lightState = LightState.fromString(lightState);
    this.color = color;

    // define the observable properties of the artifact
    defineObsProperty("lightState", this.lightState.getState());
    defineObsProperty("color", this.color);
  }

  @OPERATION
  public void toggle() {
    if (lightState == LightState.ON) {
      turnOff();
    } else {
      turnOn();
    }
  }

  private void turnOn() {
    invokeAction("/state", "on");
    this.lightState = LightState.ON;
    // updateObsProperty("lightState", this.lightState.getState());
  }

  private void turnOff() {
    invokeAction("/state", "off");
    this.lightState = LightState.OFF;
    // updateObsProperty("lightState", this.lightState.getState());
  }

  @OPERATION
  public void setColor(String color) {
    this.color = color;

    invokeAction("/color", color);
  }

  private void invokeAction(String action, String value) {
    String uri = hueURIBase + action;
    System.out.println("Invoking action: " + uri);
    
    HttpClient client = HttpClients.createDefault();
    ClassicHttpRequest request = new BasicClassicHttpRequest("PUT", uri);

    request.setEntity(new StringEntity("{\"value\" : " + value + "}",
        ContentType.create("application/json")));

    try {
      client.execute(request);
    } catch (IOException e) {
      failed(e.getMessage());
    }
  }

  @Override
  protected void registerInteractionAffordances() {

    registerActionAffordance(EXAMPLE_PREFIX + "LampState", "state", "/state",
        new StringSchema.Builder().addEnum(new HashSet<String>(Arrays.asList("on", "off"))).build());

    registerActionAffordance(EXAMPLE_PREFIX + "LampColor", "color", "/color",
        new StringSchema.Builder().addEnum(new HashSet<String>(Arrays.asList("red", "green", "blue"))).build());
          
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
    
    // add the read and write SharedContextAccessAuthorisation object to the AuthorisationRegistry
    AuthorizationRegistry authRegistry = AuthorizationRegistry.getInstance();
    authRegistry.addContextAuthorisation(getArtifactUri(), accessAuth);
  }
}

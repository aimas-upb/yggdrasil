package org.hyperagents.yggdrasil.auth.artifacts;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.util.ModelBuilder;
import org.hyperagents.yggdrasil.auth.model.AuthorizationAccessType;
import org.hyperagents.yggdrasil.auth.model.AuthorizedEntityType;
import org.hyperagents.yggdrasil.auth.model.CASHMERE;
import org.hyperagents.yggdrasil.auth.model.ContextBasedAuthorization;
import org.hyperagents.yggdrasil.cartago.HypermediaArtifact;
import org.hyperagents.yggdrasil.cartago.HypermediaArtifactRegistry;

import cartago.CartagoException;
import ch.unisg.ics.interactions.wot.td.ThingDescription;
import ch.unisg.ics.interactions.wot.td.affordances.ActionAffordance;
import ch.unisg.ics.interactions.wot.td.io.TDGraphWriter;


public abstract class ContextAuthHypermediaArtifact extends HypermediaArtifact {
  
    // The list of Authorization objects for this ContextAuthenticated Hypermedia Artifact
    protected List<ContextBasedAuthorization> authorizations = new ArrayList<ContextBasedAuthorization>();

    protected abstract void registerSharedContextAutorizations();

    // method to get the list of authorizations
    public List<ContextBasedAuthorization> getAuthorizations() {
        return authorizations;
    }

    // method to register an authorization object
    public void registerAuthorization(ContextBasedAuthorization auth) {
        authorizations.add(auth);
    }

    // method to remove an authorization object
    public void removeAuthorization(ContextBasedAuthorization auth) {
        authorizations.remove(auth);
    }

    // method to remove all authorizations to a resource identified by its URI
    public void removeAuthorizations(String resourceUri) {
        authorizations.removeIf(auth -> auth.getResourceURI().equals(resourceUri));
    }

    // method to remove all agit uthorizations granted to an entity identified by its URI
    public void removeAuthorizationsToEntity(String requesterIdentifierURI) {
        authorizations.removeIf(auth -> auth.getAuthorizedEntityURI().equals(requesterIdentifierURI));
    }

    // method to register an authorization by its components
    public void registerAuthorization(String resourceName, String resourceUri,  
                                      AuthorizationAccessType accessType, AuthorizedEntityType requesterType, 
                                      String requesterName, String requesterIdentifierUri, String accessConditionsShapeUri) {
        ContextBasedAuthorization auth = new ContextBasedAuthorization(resourceName, resourceUri, Arrays.asList(accessType), 
              requesterName, requesterType, requesterIdentifierUri, accessConditionsShapeUri);
        authorizations.add(auth);
    }

    // Override the setupOperations method to add the authorizations.
    @Override
    protected void setupOperations() throws CartagoException {
      super.setupOperations();

      registerInteractionAffordances();
      registerSharedContextAutorizations();
      HypermediaArtifactRegistry.getInstance().register(this);
    }

    // Override the hypermedia description method to add the authorizations. 
    @Override
    public String getHypermediaDescription() {
        ThingDescription.Builder tdBuilder = new ThingDescription.Builder(getArtifactName())
            .addSecurityScheme(securityScheme)
            .addSemanticType("http://w3id.org/eve#Artifact")
            .addSemanticType(getSemanticType())
            .addThingURI(getArtifactUri())
            .addGraph(metadata);

        // Before adding the action affordances, let us add the authorizations. We loop through the list of authorizations
        // and add the corresponding triples to the metadata graph. 
        // TODO: Redefine the Authorization class to use the CASHMERE ontology
        ModelBuilder authorisationsModel = new ModelBuilder();
        for (ContextBasedAuthorization auth : getAuthorizations()) {
          Map<IRI, Model> authTriples = auth.toModel();
          if (auth.getAccessTypes().contains(AuthorizationAccessType.CONTROL)){
            // If the access type is control, we add the hasControlAuthorization property. We use authTriples.keySet().iterator().next() because
            // we know that the authTriples map contains only one entry
            authorisationsModel.add(getArtifactUri(), CASHMERE.hasControlAuthorization, authTriples.keySet().iterator().next());  
          }
          else {
            // Otherwise, we add the hasAccessAuthorization property
            authorisationsModel.add(getArtifactUri(), CASHMERE.hasAccessAuthorization, authTriples.keySet().iterator().next());
          }
          // We can write this line because we know that the authTriples map contains only one entry
          tdBuilder.addGraph(authTriples.values().iterator().next());
        }
        tdBuilder.addGraph(authorisationsModel.build());
        
        // Now we can add the action affordances
        for (String actionName : actionAffordances.keySet()) {
          for (ActionAffordance action : actionAffordances.get(actionName)) {
            tdBuilder.addAction(action);
          }
        }

        return new TDGraphWriter(tdBuilder.build())
            .setNamespace("td", "https://www.w3.org/2019/wot/td#")
            .setNamespace("htv", "http://www.w3.org/2011/http#")
            .setNamespace("hctl", "https://www.w3.org/2019/wot/hypermedia#")
            .setNamespace("wotsec", "https://www.w3.org/2019/wot/security#")
            .setNamespace("dct", "http://purl.org/dc/terms/")
            .setNamespace("js", "https://www.w3.org/2019/wot/json-schema#")
            .setNamespace("eve", "http://w3id.org/eve#")
            .write();
  }
}

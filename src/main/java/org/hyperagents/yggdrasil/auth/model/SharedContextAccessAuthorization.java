package org.hyperagents.yggdrasil.auth.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.vocabulary.RDF;

public class SharedContextAccessAuthorization extends Authorization {

  public SharedContextAccessAuthorization(String resourceUri, AuthorizationAccessType type, String entityUri) {
    super(resourceUri, type, AuthorizedEntityType.AGENT_GROUP, entityUri);
  }

  public SharedContextAccessAuthorization(String resourceName, String resourceUri,  
                              AuthorizationAccessType accessType,  
                              String entityName, String entityUri) {
    super(resourceName, resourceUri, accessType, AuthorizedEntityType.AGENT_GROUP, entityName, entityUri);
  }


  // Static method that parses a CASHMERE ontology based model of a shared context access authorizations 
  // and returns a list of SharedContextAccessAuthorization objects
  public static List<Authorization> fromModel(Model sharedContextAuthModel) {
    List<Authorization> sharedCtxAuths = new ArrayList<Authorization>();
    
    // identify the subject of the authorization 
    Set<Resource> sharedCtxAuthorizations = sharedContextAuthModel.filter(null, RDF.TYPE, CASHMERE.SharedContextAccessAuthorization).subjects();
    
    // for each authorization, get the resource to which access is being given, the access type, and the ContextDomain Group URI  receiving access
    for (Resource sharedCtxAuth : sharedCtxAuthorizations) {
      try {
        String accessedResourceUri = sharedContextAuthModel.filter(sharedCtxAuth, CASHMERE.accessTo, null).iterator().next().getObject().stringValue();
        String accessTypeUri = sharedContextAuthModel.filter(sharedCtxAuth, CASHMERE.enablesAccessType, null).iterator().next().getObject().stringValue();
        AuthorizationAccessType accessType = AuthorizationAccessType.fromUri(accessTypeUri).get();
        
        Set<Value> entities = sharedContextAuthModel.filter(sharedCtxAuth, CASHMERE.requiresMembershipIn, null).objects();
        for (Value entity : entities) {
          String entityUri = entity.stringValue();
          
          // create the authorization object
          SharedContextAccessAuthorization sharedCtxAccessAuth = new SharedContextAccessAuthorization(accessedResourceUri, accessType, entityUri);
          
          // add the authorization to the list
          sharedCtxAuths.add(sharedCtxAccessAuth);
        }
      }
      catch (Exception e) {
        System.out.println("Error parsing shared context access authorization with URI " + sharedCtxAuth.stringValue() + 
            " in model \n" + sharedContextAuthModel.toString());
        System.out.println("Reason: " + e.getMessage());
        System.out.println();
      }
    }

    return sharedCtxAuths;
  }
}

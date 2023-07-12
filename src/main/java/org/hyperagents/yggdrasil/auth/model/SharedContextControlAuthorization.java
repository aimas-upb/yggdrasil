package org.hyperagents.yggdrasil.auth.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.hyperagents.yggdrasil.auth.CASHMERE;

public class SharedContextControlAuthorization extends Authorization {

  public SharedContextControlAuthorization(String resourceUri, AuthorizedEntityType authorizedEntityType, String entityUri) {
    super(resourceUri, AuthorizationAccessType.CONTROL, authorizedEntityType, entityUri);
  }

  public SharedContextControlAuthorization(String resourceName, String resourceUri,  
                              AuthorizedEntityType authorizedEntityType, String entityName, String entityUri) {
    super(resourceName, resourceUri, AuthorizationAccessType.CONTROL, authorizedEntityType, entityName, entityUri);
  }


  // Static method that parses a CASHMERE ontology based model of a shared context access authorizations 
  // and returns a list of SharedContextAccessAuthorization objects
  public static List<Authorization> fromModel(Model sharedContextAuthModel) {
    List<Authorization> sharedCtxCtrlAuths = new ArrayList<Authorization>();
    
    // identify the subject of the authorization 
    Set<Resource> sharedCtxCtrlAuthorizations = sharedContextAuthModel.filter(null, RDF.TYPE, CASHMERE.SharedContextControlAuthorization).subjects();
    
    // for each authorization, get the resource to which access is being given, the access type, and the ContextDomain Group URI  receiving access
    for (Resource sharedCtxAuth : sharedCtxCtrlAuthorizations) {
      try {
        String accessedResourceUri = sharedContextAuthModel.filter(sharedCtxAuth, CASHMERE.accessTo, null).iterator().next().getObject().stringValue();
        Set<Value> agentEntities = sharedContextAuthModel.filter(sharedCtxAuth, CASHMERE.requiresAgent, null).objects();
        Set<Value> agentClassEntities = sharedContextAuthModel.filter(sharedCtxAuth, CASHMERE.requiresAgentClass, null).objects();
        
        for (Value entity : agentEntities) {
          String entityUri = entity.stringValue();
          
          // create the authorization object
          SharedContextControlAuthorization sharedCtxCtrlAuth = new SharedContextControlAuthorization(accessedResourceUri, AuthorizedEntityType.AGENT, entityUri);
          
          // add the authorization to the list
          sharedCtxCtrlAuths.add(sharedCtxCtrlAuth);
        }
        
        for (Value entity : agentClassEntities) {
          String entityUri = entity.stringValue();
          
          // create the authorization object
          SharedContextControlAuthorization sharedCtxCtrlAuth = new SharedContextControlAuthorization(accessedResourceUri, AuthorizedEntityType.AGENT_CLASS, entityUri);
          
          // add the authorization to the list
          sharedCtxCtrlAuths.add(sharedCtxCtrlAuth);
        }
      }
      catch (Exception e) {
        System.out.println("Error parsing shared context access authorization with URI " + sharedCtxAuth.stringValue() + 
            " in model \n" + sharedContextAuthModel.toString());
        System.out.println("Reason: " + e.getMessage());
        System.out.println();
      }
    }

    return sharedCtxCtrlAuths;
  }
}

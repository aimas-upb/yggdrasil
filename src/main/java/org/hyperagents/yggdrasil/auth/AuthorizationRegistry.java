package org.hyperagents.yggdrasil.auth;

import java.util.Collections;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.hyperagents.yggdrasil.auth.model.AuthorizationAccessType;
import org.hyperagents.yggdrasil.auth.model.SharedContextAccessAuthorization;
import org.hyperagents.yggdrasil.auth.model.SharedContextControlAuthorization;

public class AuthorizationRegistry {
  // A singleton class used to manage authorisations. 
  // Methods provided by this class are used to keep mappings between an artifact instance (denoted by its URI) and (i) the list of 
  // shared context access authorisations for that artifact, (ii) the list of shared context control authorisations for that artifact. 
  // It also provides methods to add and remove authorisations for a given artifact.
  
  private static AuthorizationRegistry registry;
  private Map<String, List<SharedContextAccessAuthorization>> sharedContextAccessAuthorisationMap;
  private Map<String, List<SharedContextControlAuthorization>> sharedContextControlAuthorisationMap;

  private AuthorizationRegistry() {
    sharedContextAccessAuthorisationMap = new Hashtable<>();
    sharedContextControlAuthorisationMap = new Hashtable<>();
  }

  public static synchronized AuthorizationRegistry getInstance() {
    if (registry == null) {
        registry = new AuthorizationRegistry();
    }

    return registry;
  }

  public List<SharedContextAccessAuthorization> getSharedContextAccessAuthorisations(String artifactIRI) {
    return sharedContextAccessAuthorisationMap.getOrDefault(artifactIRI, Collections.<SharedContextAccessAuthorization>emptyList());
  }

  public List<SharedContextControlAuthorization> getSharedContextControlAuthorisations(String artifactIRI) {
    return sharedContextControlAuthorisationMap.getOrDefault(artifactIRI, Collections.<SharedContextControlAuthorization>emptyList());
  }

  public void addSharedContextAccessAuthorisation(String artifactIRI, SharedContextAccessAuthorization accessAuthorization) {
    List<SharedContextAccessAuthorization> accessAuthorisations = registry.getSharedContextAccessAuthorisations(artifactIRI);
    accessAuthorisations.add(accessAuthorization);

    sharedContextAccessAuthorisationMap.put(artifactIRI, accessAuthorisations);
  }

  public void addSharedContextControlAuthorisation(String artifactIRI, SharedContextControlAuthorization controlAuthorization) {
    List<SharedContextControlAuthorization> controlAuthorisations = registry.getSharedContextControlAuthorisations(artifactIRI);
    controlAuthorisations.add(controlAuthorization);

    sharedContextControlAuthorisationMap.put(artifactIRI, controlAuthorisations);
  }

  public void removeSharedContextAccessAuthorisation(String artifactIRI, SharedContextAccessAuthorization accessAuthorization) {
    List<SharedContextAccessAuthorization> accessAuthorisations = registry.getSharedContextAccessAuthorisations(artifactIRI);
    accessAuthorisations.remove(accessAuthorization);

    if (accessAuthorisations.isEmpty()) {
      sharedContextAccessAuthorisationMap.remove(artifactIRI);
    } else {
      sharedContextAccessAuthorisationMap.put(artifactIRI, accessAuthorisations);
    }
  }

  public void removeSharedContextControlAuthorisation(String artifactIRI, SharedContextControlAuthorization controlAuthorization) {
    List<SharedContextControlAuthorization> controlAuthorisations = registry.getSharedContextControlAuthorisations(artifactIRI);
    controlAuthorisations.remove(controlAuthorization);

    if (controlAuthorisations.isEmpty()) {
      sharedContextControlAuthorisationMap.remove(artifactIRI);
    } else {
      sharedContextControlAuthorisationMap.put(artifactIRI, controlAuthorisations);
    }
  }

  public boolean hasAccessAuthorization(String artifactIRI, AuthorizationAccessType accessType) {
    List<SharedContextAccessAuthorization> accessAuthorisations = registry.getSharedContextAccessAuthorisations(artifactIRI);

    for (SharedContextAccessAuthorization accessAuthorization : accessAuthorisations) {
      if (accessAuthorization.getAccessType() == accessType) {
        return true;
      }
    }

    return false;
  }

  public boolean isReadProtected(String artifactIRI) {
    // We consider that a user has read access to an artifact if he has either a read or a write access to it.
    return hasAccessAuthorization(artifactIRI, AuthorizationAccessType.READ) || hasAccessAuthorization(artifactIRI, AuthorizationAccessType.WRITE);
  }

  public boolean isWriteProtected(String artifactIRI) {
    return hasAccessAuthorization(artifactIRI, AuthorizationAccessType.WRITE);
  }

  public boolean isControlProtected(String artifactIRI) {
    List<SharedContextControlAuthorization> controlAuthorisations = registry.getSharedContextControlAuthorisations(artifactIRI);

    return !controlAuthorisations.isEmpty();
  }

  // Method to get the URI of the RDF document that contains the authorisations for a given artifact
  // This is built by appending the artifact URI with the string "/wac".
  // Returns an Optional<String> object containing the URI of the RDF document if any authorisation is found for the given artifact, 
  // or an empty Optional<String> object otherwise.
  public Optional<String> getAuthorisationDocumentURI(String artifactIRI) {
    if (sharedContextAccessAuthorisationMap.containsKey(artifactIRI) || sharedContextControlAuthorisationMap.containsKey(artifactIRI)) {
      return Optional.of(artifactIRI + "/wac");
    }
    
    return Optional.empty();
  }
}

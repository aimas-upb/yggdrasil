package org.hyperagents.yggdrasil.auth.http.org.hyperagents.yggdrasil.auth;

import java.util.Collections;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import org.hyperagents.yggdrasil.auth.model.SharedContextAccessAuthorization;
import org.hyperagents.yggdrasil.auth.model.SharedContextControlAuthorization;

public class AuthorisationRegistry {
  // A singleton class used to manage authorisations. 
  // Methods provided by this class are used to keep mappings between an artifact instance (denoted by its URI) and (i) the list of 
  // shared context access authorisations for that artifact, (ii) the list of shared context control authorisations for that artifact. 
  // It also provides methods to add and remove authorisations for a given artifact.
  
  private static AuthorisationRegistry registry;
  private Map<String, List<SharedContextAccessAuthorization>> sharedContextAccessAuthorisationMap;
  private Map<String, List<SharedContextControlAuthorization>> sharedContextControlAuthorisationMap;

  private AuthorisationRegistry() {
    sharedContextAccessAuthorisationMap = new Hashtable<>();
    sharedContextControlAuthorisationMap = new Hashtable<>();
  }

  public static synchronized AuthorisationRegistry getInstance() {
    if (registry == null) {
        registry = new AuthorisationRegistry();
    }

    return registry;
  }

  public List<SharedContextAccessAuthorization> getSharedContextAccessAuthorisations(String artifactInstance) {
    return sharedContextAccessAuthorisationMap.getOrDefault(artifactInstance, Collections.<SharedContextAccessAuthorization>emptyList());
  }

  public List<SharedContextControlAuthorization> getSharedContextControlAuthorisations(String artifactInstance) {
    return sharedContextControlAuthorisationMap.getOrDefault(artifactInstance, Collections.<SharedContextControlAuthorization>emptyList());
  }

  public void addSharedContextAccessAuthorisation(String artifactInstance, SharedContextAccessAuthorization accessAuthorization) {
    List<SharedContextAccessAuthorization> accessAuthorisations = registry.getSharedContextAccessAuthorisations(artifactInstance);
    accessAuthorisations.add(accessAuthorization);

    sharedContextAccessAuthorisationMap.put(artifactInstance, accessAuthorisations);
  }

  public void addSharedContextControlAuthorisation(String artifactInstance, SharedContextControlAuthorization controlAuthorization) {
    List<SharedContextControlAuthorization> controlAuthorisations = registry.getSharedContextControlAuthorisations(artifactInstance);
    controlAuthorisations.add(controlAuthorization);

    sharedContextControlAuthorisationMap.put(artifactInstance, controlAuthorisations);
  }

  public void removeSharedContextAccessAuthorisation(String artifactInstance, SharedContextAccessAuthorization accessAuthorization) {
    List<SharedContextAccessAuthorization> accessAuthorisations = registry.getSharedContextAccessAuthorisations(artifactInstance);
    accessAuthorisations.remove(accessAuthorization);

    if (accessAuthorisations.isEmpty()) {
      sharedContextAccessAuthorisationMap.remove(artifactInstance);
    } else {
      sharedContextAccessAuthorisationMap.put(artifactInstance, accessAuthorisations);
    }
  }

  public void removeSharedContextControlAuthorisation(String artifactInstance, SharedContextControlAuthorization controlAuthorization) {
    List<SharedContextControlAuthorization> controlAuthorisations = registry.getSharedContextControlAuthorisations(artifactInstance);
    controlAuthorisations.remove(controlAuthorization);

    if (controlAuthorisations.isEmpty()) {
      sharedContextControlAuthorisationMap.remove(artifactInstance);
    } else {
      sharedContextControlAuthorisationMap.put(artifactInstance, controlAuthorisations);
    }
  }
}

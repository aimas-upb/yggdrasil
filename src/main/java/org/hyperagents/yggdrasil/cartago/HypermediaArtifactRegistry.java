package org.hyperagents.yggdrasil.cartago;

import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import ch.unisg.ics.interactions.wot.td.affordances.ActionAffordance;
import ch.unisg.ics.interactions.wot.td.affordances.Form;
import io.vertx.core.json.JsonObject;

/**
 * A singleton used to manage CArtAgO artifacts. An equivalent implementation can be obtained with
 * local maps in Vert.x. Can be refactored using async shared maps to run over a cluster. 
 * 
 * @author Andrei Ciortea
 *
 */
public class HypermediaArtifactRegistry {
  private static HypermediaArtifactRegistry registry;
  
  private String httpPrefix = "http://localhost:8080";
  
  private final Map<String, String> workspaceEnvironmentMap;
  private final Map<String, String> artifactSemanticTypes;
  private final Map<String, String> artifactTemplateDescriptions;
  private final Map<String, String> artifactActionRouter;
  
  private final Map<String, String> artifactAPIKeys;
  
  private HypermediaArtifactRegistry() {
    workspaceEnvironmentMap = new Hashtable<String, String>();
    artifactSemanticTypes = new Hashtable<String, String>();
    artifactTemplateDescriptions = new Hashtable<String, String>();
    artifactActionRouter = new Hashtable<String, String>();
    artifactAPIKeys = new Hashtable<String, String>();
  }
  
  public static synchronized HypermediaArtifactRegistry getInstance() {
    if (registry == null) {
        registry = new HypermediaArtifactRegistry();
    }
    
    return registry;
  }
  
  public void register(HypermediaArtifact artifact) {
    String artifactTemplate = artifact.getArtifactId().getName();
    artifactTemplateDescriptions.put(artifactTemplate, artifact.getHypermediaDescription());
    
    Map<String, List<ActionAffordance>> actions = artifact.getActionAffordances();
    
    for (String actionName : actions.keySet()) {
      for (ActionAffordance action : actions.get(actionName)) {
        Optional<Form> form = action.getFirstForm();
        
        if (form.isPresent()) {
          artifactActionRouter.put(form.get().getMethodName().get() + form.get().getTarget(), 
              actionName);
        }
      }
    }
  }
  
  public void addWorkspace(String envName, String wkspName) {
    workspaceEnvironmentMap.put(wkspName, envName);
  }
  
  public Optional<String> getEnvironmentForWorkspace(String wkspName) {
    String envName = workspaceEnvironmentMap.get(wkspName);
    System.out.println("env: " + envName);
    return envName == null ? Optional.empty() : Optional.of(envName);
  }
  
  public void addArtifactTemplates(JsonObject artifactTemplates) {
    if (artifactTemplates != null) {
      artifactTemplates.forEach(entry -> 
          artifactSemanticTypes.put(entry.getKey(), (String) entry.getValue()));
    }
  }
  
  public Set<String> getArtifactTemplates() {
    return artifactSemanticTypes.keySet();
  }
  
  public Optional<String> getArtifactSemanticType(String artifactTemplate) {
    for (String artifactType : artifactSemanticTypes.keySet()) {
      if (artifactSemanticTypes.get(artifactType).compareTo(artifactTemplate) == 0) {
        return Optional.of(artifactType);
      }
    }
    
    return Optional.empty();
  }
  
  public Optional<String> getArtifactTemplate(String artifactClass) {
    String artifactTemplate = artifactSemanticTypes.get(artifactClass);
    return artifactTemplate == null ? Optional.empty() : Optional.of(artifactTemplate);
  }
  
  public String getArtifactDescription(String artifactName) {
    return artifactTemplateDescriptions.get(artifactName);
  }
  
  public String getActionName(String method, String requestURI) {
    return artifactActionRouter.get(method + requestURI);
  }
  
  public String setAPIKeyForArtifact(String artifactId, String apiKey) {
    return artifactAPIKeys.put(artifactId, apiKey);
  }
  
  public String getAPIKeyForArtifact(String artifactId) {
    return artifactAPIKeys.get(artifactId);
  }
  
  public void setHttpPrefix(String prefix) {
    this.httpPrefix = prefix;
  }
  
  public String getHttpPrefix() {
    return this.httpPrefix;
  }
  
  public String getHttpEnvironmentsPrefix() {
    return getHttpPrefix() + "/environments/";
  }
  
  public String getHttpWorkspacesPrefix(String envId) {
    return getHttpEnvironmentsPrefix() + envId + "/workspaces/";
  }
  
  public String getHttpArtifactsPrefix(String wkspName) {
    Optional<String> envId = this.getEnvironmentForWorkspace(wkspName);
    
    if (envId.isPresent()) {
      return getHttpWorkspacesPrefix(envId.get()) + wkspName + "/artifacts/";
    }
    
    System.out.println("workspace not found!");
    
    throw new IllegalArgumentException("Workspace " + wkspName + " not found in any environment.");
  }
}
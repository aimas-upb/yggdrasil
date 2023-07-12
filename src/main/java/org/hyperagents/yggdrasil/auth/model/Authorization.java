package org.hyperagents.yggdrasil.auth.model;

import java.util.Optional;

import org.eclipse.rdf4j.model.BNode;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.util.ModelBuilder;
import org.eclipse.rdf4j.model.vocabulary.RDF;

import javafx.util.Pair;

public class Authorization {
  private Optional<String> resourceName = Optional.empty();
  private String resourceUri;

  private AuthorizationAccessType accessType;
  private AuthorizedEntityType entityType;
  
  private Optional<String> entityName = Optional.empty();
  private String entityUri;

  // constructor
  public Authorization(String resourceUri, AuthorizationAccessType type, AuthorizedEntityType entityType, String entityUri) {
    this.resourceUri = resourceUri;
    this.accessType = type;
    this.entityType = entityType;
    this.entityUri = entityUri;
  }

  // constructor with resource and entity name
  public Authorization(String resourceName, String resourceUri,  
                      AuthorizationAccessType accessType, AuthorizedEntityType entityType, 
                      String entityName, String entityUri) {
    this.resourceName = Optional.of(resourceName);
    this.resourceUri = resourceUri;
    this.accessType = accessType;
    this.entityType = entityType;
    this.entityName = Optional.of(entityName);
    this.entityUri = entityUri;
  }

  // getters
  public Optional<String> getResourceName() {
    return resourceName;
  }

  public String getResourceUri() {
    return resourceUri;
  }

  public AuthorizationAccessType getAccessType() {
    return accessType;
  }

  public AuthorizedEntityType getEntityType() {
    return entityType;
  }

  public Optional<String> getEntityName() {
    return entityName;
  }

  public String getEntityUri() {
    return entityUri;
  }


  // create equals and hashcode
  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (!(obj instanceof Authorization)) {
      return false;
    }
    Authorization auth = (Authorization) obj;
    return auth.resourceUri.equals(resourceUri) &&
           auth.accessType.equals(accessType) &&
           auth.entityType.equals(entityType) &&
           auth.entityUri.equals(entityUri);
  }

  @Override
  public int hashCode() {
    int result = 17;
    result = 31 * result + resourceUri.hashCode();
    result = 31 * result + accessType.hashCode();
    result = 31 * result + entityType.hashCode();
    result = 31 * result + entityUri.hashCode();
    return result;
  }

  // Generate an RDF graph Model of this Authorization instance, using the org.eclipse.rdf4j.model.util.ModelBuilder 
  // and the corresponding ACL ontology vocabulary. Return a tuple of the authorization instance URI and the Model.
  public Pair<IRI, Model> toModel() {
    ModelBuilder builder = new ModelBuilder();
    builder.setNamespace("acl", ACL.NS);
    builder.setNamespace("rdf", RDF.NAMESPACE);

    ValueFactory rdfVals = SimpleValueFactory.getInstance();
    
    // create a blank node for the authorization instance
    BNode authInstance = rdfVals.createBNode();

    // describe the authorization instance
    builder.add(authInstance, RDF.TYPE, ACL.Authorization);
    builder.add(authInstance, ACL.accessTo, rdfVals.createIRI(resourceUri));
    builder.add(authInstance, ACL.mode, rdfVals.createIRI(accessType.getUri()));

    // set the property of the entity being authorized by looking up the property from the entity type
    builder.add(authInstance, rdfVals.createIRI(entityType.getProperty()), rdfVals.createIRI(entityUri));
    
    // return a tuple of the authorization instance URI and the Model
    return new Pair<>(
      rdfVals.createIRI(authInstance.stringValue()), 
      builder.build());
  }
    
}

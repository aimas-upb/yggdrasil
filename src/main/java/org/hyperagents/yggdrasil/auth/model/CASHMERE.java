package org.hyperagents.yggdrasil.auth.model;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;

public class CASHMERE {
    // Namespaces
    private static final String CASHMERE = "https://aimas.cs.pub.ro/ont/cashmere";
    private static final String ACL = "http://www.w3.org/ns/auth/acl#";
    private static final String CONSERT_CORE = "http://pervasive.semanticweb.org/ont/2017/07/consert/core#";

    // Classes
    public static final IRI Access = iri(ACL, "Access");
    public static final IRI Append = iri(ACL, "Append");
    public static final IRI AuthenticatedAgent = iri(ACL, "AuthenticatedAgent");
    public static final IRI Authorization = iri(ACL, "Authorization");
    public static final IRI Control = iri(ACL, "Control");
    public static final IRI Origin = iri(ACL, "Origin");
    public static final IRI Read = iri(ACL, "Read");
    public static final IRI Write = iri(ACL, "Write");
    public static final IRI ContextAuthorizedResource = iri(CASHMERE, "ContextAuthorizedResource");
    public static final IRI ContextBasedAccessAuthorization = iri(CASHMERE, "ContextBasedAccessAuthorization");
    public static final IRI ContextBasedAccessCondition = iri(CASHMERE, "ContextBasedAccessCondition");
    public static final IRI ContextBasedAuthorization = iri(CASHMERE, "ContextBasedAuthorization");
    public static final IRI ContextBasedControlAuthorization = iri(CASHMERE, "ContextBasedControlAuthorization");
    public static final IRI ContextDomain = iri(CASHMERE, "ContextDomain");
    public static final IRI ContextDomainCondition = iri(CASHMERE, "ContextDomainCondition");
    public static final IRI ContextDomainGroup = iri(CASHMERE, "ContextDomainGroup");
    public static final IRI ContextManagementService = iri(CASHMERE, "ContextManagementService");
    public static final IRI ContextStream = iri(CASHMERE, "ContextStream");
    public static final IRI ProfiledContextCondition = iri(CASHMERE, "ProfiledContextCondition");
    public static final IRI StaticContextCondition = iri(CASHMERE, "StaticContextCondition");

    // Object Properties
    public static final IRI accessTo = iri(ACL, "accessTo");
    public static final IRI accessToClass = iri(ACL, "accessToClass");
    public static final IRI agent = iri(ACL, "agent");
    public static final IRI agentClass = iri(ACL, "agentClass");
    public static final IRI agentGroup = iri(ACL, "agentGroup");
    public static final IRI delegates = iri(ACL, "delegates");
    public static final IRI definesGroup = iri(CASHMERE, "definesGroup");
    public static final IRI groupFor = iri(CASHMERE, "groupFor");
    public static final IRI hasAccessAuthorization = iri(CASHMERE, "hasAccessAuthorization");
    public static final IRI hasAccessCondition = iri(CASHMERE, "hasAccessCondition");
    public static final IRI hasContextDimension = iri(CASHMERE, "hasContextDimension");
    public static final IRI hasControlAuthorization = iri(CASHMERE, "hasControlAuthorization");
    public static final IRI managesDomain = iri(CASHMERE, "managesDomain");
    public static final IRI managesStream = iri(CASHMERE, "managesStream");
    public static final IRI memberIn = iri(CASHMERE, "memberIn");
    public static final IRI streams = iri(CASHMERE, "streams");

    // Individual
    public static final IRI accessRequester = iri(CASHMERE, "accessRequester");

    // Annotation Properties
    public static final IRI describes = iri("http://purl.org/dc/elements/1.1/describes");
    public static final IRI title = iri("http://purl.org/dc/elements/1.1/title");
    
    // Helper method to create IRI from direct string
    private static IRI iri(String iri) {
        return SimpleValueFactory.getInstance().createIRI(iri);
    }

    // Helper method to create IRI
    private static IRI iri(String namespace, String localName) {
        return SimpleValueFactory.getInstance().createIRI(namespace, localName);
    }

}


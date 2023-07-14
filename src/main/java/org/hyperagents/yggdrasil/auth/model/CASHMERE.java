package org.hyperagents.yggdrasil.auth.model;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;

public class CASHMERE {
    // Vocabulary terms
    public static final String NS = "https://aimas.cs.pub.ro/ont/cashmere#";
    private static final SimpleValueFactory VALUE_FACTORY = SimpleValueFactory.getInstance();

    // Properties
    public static final IRI accessTo = createIRI("accessTo");
    public static final IRI enablesAccessType = createIRI("enablesAccessType");
    public static final IRI hasAccessAuthorization = createIRI("hasAccessAuthorization");
    public static final IRI hasControlAuthorization = createIRI("hasControlAuthorization");
    public static final IRI requiresAgent = createIRI("requiresAgent");
    public static final IRI requiresAgentClass = createIRI("requiresAgentClass");
    public static final IRI requiresMembershipIn = createIRI("requiresMembershipIn");
    
    // Classes
    public static final IRI ContextAuthorizedResource = createIRI("ContextAuthorizedResource");
    public static final IRI SharedContextAuthorization = createIRI("SharedContextAuthorization");
    public static final IRI SharedContextAccessAuthorization = createIRI("SharedContextAccessAuthorization");
    public static final IRI SharedContextControlAuthorization = createIRI("SharedContextControlAuthorization");

    private static IRI createIRI(String localName) {
        return VALUE_FACTORY.createIRI(NS + localName);
    }
}


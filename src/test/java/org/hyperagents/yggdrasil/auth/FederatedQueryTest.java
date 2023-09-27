package org.hyperagents.yggdrasil.auth;

import org.eclipse.rdf4j.query.BooleanQuery;
import org.eclipse.rdf4j.query.QueryLanguage;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryResolver;
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository;


public class FederatedQueryTest {
	
	public static void main(String[] args) {
		String graph1URI = "http://example.org/Lab308ContextDomainGroup/memberships";
		String graph2URI = "http://example.org/Lab303ContextDomainGroup/memberships";
		
	    String sparqlEndpoint1URI = "http://localhost:8080/Lab308ContextDomainGroup/sparql";
	    String sparqlEndpoint2URI = "http://localhost:8081/Lab303ContextDomainGroup/sparql";
	    
	    String ctxDomain1URI = "http://example.org/Lab308ContextDomainGroup";
	    String ctxDomain2URI = "http://example.org/Lab303ContextDomainGroup";
	    
	    String federatedAskQuery = 
	    		"PREFIX vcard: <http://www.w3.org/2006/vcard/ns#> "
	    		+ "PREFIX profiles: <http://example.org/profiles#> "
	    		+ "PREFIX example: <http://example.org/> "
	    		+ "ASK {"
	    		+ "  VALUES (?endpointURI ?graphURI ?ctxDomainURI) {"
	    		+ "    (<"+sparqlEndpoint1URI+"> <"+graph1URI+"> <"+ctxDomain1URI+">)"
	    		+ "    (<"+sparqlEndpoint2URI+"> <"+graph2URI+"> <"+ctxDomain2URI+">)"
	    		+ "  }"
	    		+ "  "
	    		+ "  SERVICE ?endpointURI {"
	    		+ "    GRAPH ?graphURI {"
	    		+ "      ?ctxDomainURI vcard:member profiles:alex ."
	    		+ "    }"
	    		+ "  }"
	    		+ "}";
	    
	 // Create RDF4J HTTPRepositories for the remote SPARQL endpoints
        Repository endpoint1Repository = new SPARQLRepository(sparqlEndpoint1URI);
        Repository endpoint2Repository = new SPARQLRepository(sparqlEndpoint2URI);

        // Create a federated repository resolver
        RepositoryResolver resolver = (locationURI) -> {
            if (sparqlEndpoint1URI.equals(locationURI)) {
                return endpoint1Repository;
            } else if (sparqlEndpoint2URI.equals(locationURI)) {
                return endpoint2Repository;
            } else {
                throw new IllegalArgumentException("Unknown repository ID: " + locationURI);
            }
        };

	    
	    try {
	    	// initialize both endpoint repos
	    	endpoint1Repository.init();
	    	endpoint2Repository.init();
	    	
	    	RepositoryConnection conn = endpoint1Repository.getConnection();
	    	BooleanQuery query = conn.prepareBooleanQuery(QueryLanguage.SPARQL, federatedAskQuery);
	    	
	    	boolean result = query.evaluate();
	    	
	    	System.out.println("Alex found as member in at least one ctx domain group: " + result);
	    }
	    catch (Exception e) {
			// TODO: handle exception
	    	e.printStackTrace();
		}
	    finally {
			endpoint1Repository.shutDown();
			endpoint2Repository.shutDown();
		}
	}
}

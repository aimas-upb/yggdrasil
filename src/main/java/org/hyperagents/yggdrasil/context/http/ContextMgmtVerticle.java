package org.hyperagents.yggdrasil.context.http;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.rdf4j.common.iteration.Iterations;
import org.eclipse.rdf4j.exceptions.ValidationException;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDF4J;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFParseException;
import org.eclipse.rdf4j.sail.memory.MemoryStore;
import org.eclipse.rdf4j.sail.shacl.ShaclSail;
import org.hyperagents.yggdrasil.auth.model.CASHMERE;
import org.hyperagents.yggdrasil.context.ContextDomain;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;



public class ContextMgmtVerticle extends AbstractVerticle {
    public static final String BUS_ADDRESS = "org.hyperagents.yggdrasil.eventbus.context";
    
    // Context services
    public static final String VALIDATE_CONTEXT_BASED_ACCESS = "org.hyperagents.yggdrasil.eventbus.headers.services" + ".validateContextBasedAccess";
    
    // keys for the headers of the event bus messages
    public static final String ACCESS_REQUESTER_URI = "org.hyperagents.yggdrasil.eventbus.headers.accessRequesterURI";
    public static final String ACCESSED_RESOURCE_URI = "org.hyperagents.yggdrasil.eventbus.headers.accessedResourceURI";
    public static final String CONTEXT_SERVICE = "org.hyperagents.yggdrasil.eventbus.headers.contextService";

    // Logger
    private static final Logger LOGGER = LoggerFactory.getLogger(ContextMgmtVerticle.class.getName());

    // The URI of the context management service
    private String serviceURI;

    // The RDF store for the static context information
    private SailRepository staticContextRepo;

    // The RDF store for the profiled context information
    private SailRepository profiledContextRepo;

    // A Map linking the URI of the ContextDomain to the ContextDomain object
    private Map<String, ContextDomain> contextDomains;
    
    // A Map linking dynamic ContextAssertions to the RDF stream URI on which their updates are published
    private Map<String, String> dynamicContextAssertions;

    // A SailRepository object containing Named Graphs with SHACL shapes that define the context access conditions required for 
    // access to a particular Artifact.
    private SailRepository contextAccessConditionsRepo;
    private Map<String, String> artifactPolicies;

    @Override
    public void start() {
        //register the event bus handlers
        EventBus eventBus = vertx.eventBus();
        eventBus.consumer(BUS_ADDRESS, this::handleContextRequest);

        // initialize the map of context domains, the map of dynamic context assertions and the map of artifact policies
        contextDomains = new HashMap<>();
        dynamicContextAssertions = new HashMap<>();
        artifactPolicies = new HashMap<>();
        
        JsonObject ctxMgmtServiceConfig = config();

        // get the service URI from the configuration
        this.serviceURI = ctxMgmtServiceConfig.getString("service-uri");

        setupStaticContextRepo(config());
        setupProfiledContextRepo(config());
        setupCDGMembershipRepo(config());
        setupAssertionStreams(config());

        // Set up the context access conditions repository
        setupContextAccessConditionsRepo(config());
    }

    private void setupStaticContextRepo(JsonObject config) {
        // First, set up the static context repository. We set it up as a SailRepository over an in-memory store.
        staticContextRepo = new SailRepository(new MemoryStore());
        
        try {
            URL staticContextURL = new URL(config.getString("static-context"));
            
            // open the URL stream and load the contents of the RDF file (in turtle format) into the static context repository
            staticContextRepo.getConnection().add(staticContextURL, "http://example.org/", RDFFormat.TURTLE);

        } catch (MalformedURLException e) {
            LOGGER.error("Malformed URL for source of default static context information: " + config.getString("static-context") + ". Reason: " + e.getMessage());
        } catch (RDFParseException e) {
            LOGGER.error("Error parsing the RDF content of the default static context information: " + config.getString("static-context") + ". Reason: " + e.getMessage());
        } catch (RepositoryException e) {
            LOGGER.error("Error adding the RDF content of the default static context information to the static repository: " + ". Reason: " + e.getMessage());
        } catch (IOException e) {
            LOGGER.error("Error reading the RDF content of the default static context information from the source: " + config.getString("static-context") + ". Reason: " + e.getMessage());
        } 
    }

    private void setupProfiledContextRepo(JsonObject config) {
        // Set up the profiled context repository. We set it up as a SailRepository over an in-memory store.
        profiledContextRepo = new SailRepository(new MemoryStore());
        
        try {
            URL profiledContextURL = new URL(config.getString("profiled-context"));
            
            // Open the URL stream and load the contents of the RDF file (in turtle format) into the profiled context repository
            profiledContextRepo.getConnection().add(profiledContextURL, "http://example.org/", RDFFormat.TURTLE);

        } catch (MalformedURLException e) {
            LOGGER.error("Malformed URL for source of profiled context information: " + config.getString("profiled-context") + ". Reason: " + e.getMessage());
        } catch (RDFParseException e) {
            LOGGER.error("Error parsing the RDF content of the profiled context information: " + config.getString("profiled-context") + ". Reason: " + e.getMessage());
        } catch (RepositoryException e) {
            LOGGER.error("Error adding the RDF content of the profiled context information to the profiled repository: " + ". Reason: " + e.getMessage());
        } catch (IOException e) {
            LOGGER.error("Error reading the RDF content of the profiled context information from the source: " + config.getString("profiled-context") + ". Reason: " + e.getMessage());
        } 
    }

    private void setupCDGMembershipRepo(JsonObject config) {
        // Set up the ContextDomains. 
        // The first level of keys in the context-domains JSON object are the URIs of the ContextDomains.
        // Iterate over the keys and set up the ContextDomain for each key.
        for (String contextDomainURI : config.getJsonObject("context-domains").fieldNames()) {
            JsonObject contextDomainConfig = config.getJsonObject("context-domains").getJsonObject(contextDomainURI);
            String contextAssertionURI = contextDomainConfig.getString("assertion");
            String contextEntityURI = contextDomainConfig.getString("entity");
            String engineConfigURI = contextDomainConfig.getString("engine-config");

            // if a stream URI is provided, set it
            Optional<String> assertionStreamURI = Optional.ofNullable(contextDomainConfig.getString("stream")); 
            String ruleURI = contextDomainConfig.getString("rule");

            // if a local stream generator class is provided, set it
            Optional<String> streamGeneratorClass = Optional.ofNullable(contextDomainConfig.getString("generatorClass"));
            

            // create the ContextDomain object and add it to the contextDomains map
            ContextDomain contextDomain = new ContextDomain(serviceURI, contextAssertionURI, contextEntityURI, 
                                                            assertionStreamURI, streamGeneratorClass,
                                                            ruleURI, engineConfigURI);
            contextDomains.put(contextDomainURI, contextDomain);
        }
    }

    private void setupAssertionStreams(JsonObject config) {
        // Set up the dynamic context assertion streams.
        // The keys in the dynamic-context-assertions JSON object are the URIs of the dynamic ContextAssertions.
        // Iterate over the keys and set up the assertion stream for each key.
        JsonObject dynamicContextAssertionsConfig = config.getJsonObject("dynamic-context");
        for (String assertionURI : dynamicContextAssertionsConfig.fieldNames()) {
            String streamURI = dynamicContextAssertionsConfig.getString(assertionURI);
            dynamicContextAssertions.put(assertionURI, streamURI);
        }
    }

    private void setupContextAccessConditionsRepo(JsonObject config) {
        ShaclSail shaclSail = new ShaclSail(new MemoryStore());
        contextAccessConditionsRepo = new SailRepository(shaclSail);

        // read the artifact-policies JSON object from the configuration
        JsonObject artifactPolicies = config.getJsonObject("artifact-policies");
        for (String artifactURI : artifactPolicies.fieldNames()) {
            // add the entry to the artifactPolicies map
            String policyURI = artifactPolicies.getString(artifactURI);
            this.artifactPolicies.put(artifactURI, policyURI);

            // Dereference the policy URI as a file and add the contents to the contextAccessConditionsRepo.
            // They are added in a named graph with the artifact URI as the graph name.
            try {
                IRI contextIRI = SimpleValueFactory.getInstance().createIRI(artifactURI);
                URL policyURL = new URL(policyURI);
                contextAccessConditionsRepo.getConnection().add(policyURL, null, RDFFormat.TURTLE, contextIRI);
            } catch (MalformedURLException e) {
                LOGGER.error("Malformed URL for source of context access conditions for artifact " + artifactURI + ": " + policyURI + ". Reason: " + e.getMessage());
            } catch (RDFParseException e) {
                LOGGER.error("Error parsing the RDF content of the context access conditions for artifact " + artifactURI + ": " + policyURI + ". Reason: " + e.getMessage());
            } catch (RepositoryException e) {
                LOGGER.error("Error adding the RDF content of the context access conditions for artifact " + artifactURI + " to the context access conditions repository: " + ". Reason: " + e.getMessage());
            } catch (IOException e) {
                LOGGER.error("Error reading the RDF content of the context access conditions for artifact " + artifactURI + " from the source: " + policyURI + ". Reason: " + e.getMessage());
            }
        }
    }

    private void handleContextRequest(Message<String> message) {
        LOGGER.info("Handling Context Request...");
        String contextService = message.headers().get(ContextMgmtVerticle.CONTEXT_SERVICE);
         
        switch (contextService) {
            case ContextMgmtVerticle.VALIDATE_CONTEXT_BASED_ACCESS:
                LOGGER.info("Handling Context-based access validation action...");
                validateContextBasedAccess(message);
                break;
            default:
                LOGGER.info("Context service " + contextService + " not supported yet");
        }
    }

    // ============================================================================
    // =================== Methods for context validation =========================
    // ============================================================================

    private boolean isAccessProtected(String artifactURI) {
        return artifactPolicies.containsKey(artifactURI);
    }


    private Optional<List<Statement>> getAccessConditions(String artifactURI) {
        // get the URI of the named graph containing the access conditions for the artifact
        String accessConditionsGraphURI = artifactPolicies.get(artifactURI);

        if (accessConditionsGraphURI == null) {
            return Optional.empty();
        }

        // get the statements in the named graph
        try (RepositoryConnection conn = contextAccessConditionsRepo.getConnection()) {
            return Optional.of(Iterations.asList(conn.getStatements(null, null, null, false, conn.getValueFactory().createIRI(artifactURI))));
        } catch (RepositoryException e) {
            LOGGER.error("Error accessing the context access conditions repository: " + e.getMessage());
            return Optional.empty();
        }
    }


    private List<Statement> customizeAccessConditions(List<Statement> accessConditions, String accessRequesterURI, String accessedArtifactURI) {
        // Iterate through all the statements in the accessConditions list and replace the `cashmere:accessRequester` object placeholder 
        // with the actual accessRequesterURI
        accessConditions.replaceAll(stmt -> {
            if (stmt.getObject().stringValue().equals(CASHMERE.accessRequester.stringValue())) {
                return SimpleValueFactory.getInstance().createStatement(stmt.getSubject(), stmt.getPredicate(), SimpleValueFactory.getInstance().createIRI(accessRequesterURI));
            } else {
                return stmt;
            }
        });
        
        // return the customized access conditions
        return accessConditions;
    }


    private void validateContextBasedAccess(Message<String> message) {
        // Extract from the message the URI of the agent requesting access and the URI of the artifact being accessed
        String accessRequesterURI = message.headers().get(ContextMgmtVerticle.ACCESS_REQUESTER_URI);
        String accessedArtifactURI = message.headers().get(ContextMgmtVerticle.ACCESSED_RESOURCE_URI);

        // Validate the access request against the static, profiled and dynamic context information
        boolean staticContextValidation = validateStaticContext(accessRequesterURI, accessedArtifactURI);
        if (!staticContextValidation) {
            LOGGER.info("Access to artifact " + accessedArtifactURI + " denied for access requester: " 
                + accessRequesterURI + ". Reason: Static Context validation failed.");
            message.fail(403, "Access denied. Reason: Static Context validation failed.");
            return;
        }

        boolean profiledContextValidation = validateProfiledContext(accessRequesterURI, accessedArtifactURI);
        if (!profiledContextValidation) {
            LOGGER.info("Access to artifact " + accessedArtifactURI + " denied for access requester: " 
                + accessRequesterURI + ". Reason: Profiled Context validation failed.");
            message.fail(403, "Access denied. Reason: Profiled Context validation failed.");
            return;
        }

        boolean dynamicContextValidation = validateDynamicContext(accessRequesterURI, accessedArtifactURI);
        if (!dynamicContextValidation) {
            LOGGER.info("Access to artifact " + accessedArtifactURI + " denied for access requester: " 
                + accessRequesterURI + ". Reason: Dynamic Context validation failed.");
            message.fail(403, "Access denied. Reason: Dynamic Context validation failed.");
            return;
        }

        // If all validations are successful, allow access
        message.reply(true);
    }


    private boolean validateStaticContext(String accessRequesterURI, String accessedArtifactURI) {
        // TODO: this remains to be implemented properly. For now, we just log the request and return a success message.
        LOGGER.info("Validating Static Context...");
        
        // by default, we allow access
        return true;
    }

    // ============================================================================
    // ======================== Profiled Context Validation =======================
    // ============================================================================

    private boolean validateProfiledContext(String accessRequesterURI, String accessedArtifactURI) {
        
        // check if the artifact is protected by access conditions    
        if (isAccessProtected(accessedArtifactURI)) {
            // create a new ShaclSail object and set it up with the contextAccessConditionsRepo
            ShaclSail shaclSail = new ShaclSail(new MemoryStore());
            SailRepository validationRepo = new SailRepository(shaclSail);
            validationRepo.init();
            
            Optional<List<Statement>> accessConditions = getAccessConditions(accessedArtifactURI);
            if (accessConditions.isPresent()) {
                
                List<Statement> customAccessConditions = customizeAccessConditions(accessConditions.get(), accessRequesterURI, accessedArtifactURI);

                try (RepositoryConnection conn = validationRepo.getConnection()) {
                    // load the access conditions into the profiledContextRepo, under the RDF4J.SHACL_SHAPE_GRAPH context
                    conn.begin();
                    conn.clear(RDF4J.SHACL_SHAPE_GRAPH);
                    conn.add(customAccessConditions, RDF4J.SHACL_SHAPE_GRAPH);

                    // load the contents of the profiledContextRepo into the validationRepo
                    conn.add(profiledContextRepo.getConnection().getStatements(null, null, null, false));
                    
                    // validate the access conditions against the profiled context repository
                    conn.commit();

                    // if the validation is successful, allow access
                    return true;
                } catch (RepositoryException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof ValidationException) {
                        LOGGER.info("Access to artifact " + accessedArtifactURI + " denied for access requester: " 
                            + accessRequesterURI + ". Reason:  " + cause.getMessage()); 
                    } else {
                        LOGGER.error("Error validating the access conditions of artifact: " + accessedArtifactURI 
                            + " for requester: " + accessRequesterURI +  " against the profiled context repository: " + e.getMessage());
                    }

                    // in case of error, deny access
                    return false;
                }
                
            } else {
                // allow access by default
                return true;
            }
        } 
        else {
            // allow access by default
            return true;
        }
    
    }

    // ============================================================================
    // ======================== Dynamic Context Validation ========================
    // ============================================================================

    private Optional<List<String>> getContextDomainGroupURIs(String accessedArtifactURI, String accessRequesterURI) {
        // open a connection to the contextAccessConditionsRepo
        try (RepositoryConnection conn = contextAccessConditionsRepo.getConnection()) {
            // prepare the query to retrieve the ContextDomainGroup URIs, running it on the named graph of the artifact
            String query = 
                "PREFIX cashmere: <" + CASHMERE.CASHMERE_NS + "> " +
                "PREFIX sh: <http://www.w3.org/ns/shacl#> " +
                "SELECT ?domainCond ?ctxGroupURI "
                + "WHERE {"
                +   "?domainCond a cashmere:ContextDomainCondition ."
                +   "?domainCond sh:property [sh:hasValue ?ctxGroupURI] ."
                + "}";

            // execute the query
            return Optional.of(Iterations.asList(conn.prepareTupleQuery(query).evaluate()).stream()
                .map(binding -> binding.getValue("ctxGroupURI").stringValue())
                .toList());
        } catch (RepositoryException e) {
            LOGGER.error("Error accessing the context access conditions repository of the artifact " + accessedArtifactURI 
                    + " by access requester " + accessRequesterURI + ": " + e.getMessage());
        }
        
        return Optional.empty();
    }

    private boolean validateDynamicContext(String accessRequesterURI, String accessedArtifactURI) {
        // This validation requires to first identify if the artifact is protected by ContextDomainConditions.
        // This must be checked within the named graph of the contextAccessConditionsRepo that contains the access conditions for the artifact.
        // Specifically, we must retrieve all the instances of `cashmere:ContextDomainCondition` and retrieve for them the ContextDomainGroup they reference 
        // through the RDF property path `sh:property / sh:hasValue`.
        
        // check if the artifact is protected by access conditions
        if (isAccessProtected(accessedArtifactURI)) {
            Optional<List<String>> contextDomainGroupURIs = getContextDomainGroupURIs(accessedArtifactURI, accessRequesterURI);
            if (contextDomainGroupURIs.isPresent()) {
                // iterate over the contextDomainGroupURIs 
                for (String ctxGroupURI : contextDomainGroupURIs.get()) {
                    // get the ContextDomain URI from the contextDomainGroupURI
                    String ctxDomainURI = ContextDomain.getDomainFromGroup(ctxGroupURI);

                    // get the ContextDomain object from the contextDomains map
                    ContextDomain ctxDomain = contextDomains.get(ctxDomainURI);
                    if (ctxDomain != null) {
                        // check if the accessRequesterURI is a member of the group
                        if (!ctxDomain.verifyMembership(accessRequesterURI)) {
                            // if for at least one ContextDomain the accessRequesterURI is not a member of the group, deny access
                            return false;
                        }
                    }
                }

                // if the accessRequesterURI is a member of all the ContextDomainGroups, allow access
                return true;
            } else {
                // If there are no ContextDomainConditions for the artifact, allow access by default
                return true;
            }
        }
        else {
            // allow access by default
            return true;
        }
    }
}

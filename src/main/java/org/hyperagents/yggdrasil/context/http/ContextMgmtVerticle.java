package org.hyperagents.yggdrasil.context.http;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFParseException;
import org.eclipse.rdf4j.sail.memory.MemoryStore;
import org.eclipse.rdf4j.sail.shacl.ShaclSail;
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
    public static final String VALIDATE_STATIC_CONTEXT_CONDITION = "org.hyperagents.yggdrasil.eventbus.headers.services" + ".validateStaticContext";
    public static final String VALIDATE_PROFILED_CONTEXT_CONDITION = "org.hyperagents.yggdrasil.eventbus.headers.services" + ".validateProfiledContext";
    public static final String VALIDATE_DYNAMIC_CONTEXT_CONDITION = "org.hyperagents.yggdrasil.eventbus.headers.services" + ".validateDynamicContext";

    // keys for the headers of the event bus messages
    public static final String CONTEXT_SERVICE = "org.hyperagents.yggdrasil.eventbus.headers.contextService";

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
            case ContextMgmtVerticle.VALIDATE_STATIC_CONTEXT_CONDITION:
                LOGGER.info("Handling Static Context validation action...");
                validateStaticContext(message);
                break;
            case ContextMgmtVerticle.VALIDATE_PROFILED_CONTEXT_CONDITION:
                LOGGER.info("Handling Profiled Context validation action...");
                validateProfiledContext(message);
                break;
            case ContextMgmtVerticle.VALIDATE_DYNAMIC_CONTEXT_CONDITION:
                LOGGER.info("Handling Dynamic Context validation action...");
                validateDynamicContext(message);
                break;
            default:
                LOGGER.info("Context service " + contextService + " not supported yet");
        }
    }


    private void validateStaticContext(Message<String> message) {
        // TODO: Implement this method
    }

    private void validateProfiledContext(Message<String> message) {
        // TODO: Implement this method
    }

    private void validateDynamicContext(Message<String> message) {
        // TODO: Implement this method
    }
}

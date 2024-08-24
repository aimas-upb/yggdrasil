package org.hyperagents.yggdrasil.store.impl;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.rdf.api.Dataset;
import org.apache.commons.rdf.api.Graph;
import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.RDFSyntax;
import org.apache.commons.rdf.rdf4j.RDF4J;
import org.apache.commons.rdf.rdf4j.RDF4JGraph;
import org.apache.commons.rdf.rdf4j.RDF4JTriple;
import org.eclipse.rdf4j.RDF4JException;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFHandlerException;
import org.eclipse.rdf4j.rio.RDFParseException;
import org.eclipse.rdf4j.rio.RDFParser;
import org.eclipse.rdf4j.rio.RDFWriter;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.rio.UnsupportedRDFormatException;
import org.eclipse.rdf4j.rio.helpers.BasicWriterSettings;
import org.eclipse.rdf4j.rio.helpers.JSONLDMode;
import org.eclipse.rdf4j.rio.helpers.JSONLDSettings;
import org.eclipse.rdf4j.rio.helpers.StatementCollector;
import org.eclipse.rdf4j.sail.memory.MemoryStore;
import org.eclipse.rdf4j.sail.nativerdf.NativeStore;
import org.hyperagents.yggdrasil.cartago.CartagoVerticle;
import org.hyperagents.yggdrasil.cartago.HypermediaArtifactRegistry;
import org.hyperagents.yggdrasil.context.http.Utils;
import org.hyperagents.yggdrasil.http.HttpEntityHandler;
import org.hyperagents.yggdrasil.store.RdfStore;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;


public class Rdf4jStore implements RdfStore {
  private final static Logger LOGGER = LoggerFactory.getLogger(RdfStore.class.getName());

  private final RDF4J rdfImpl;
  private final Dataset dataset;
  private Vertx vertx;

  public Rdf4jStore(JsonObject config, Vertx vertx) {
    this.vertx = vertx;
    Repository repository;
    JsonObject rdfStoreConfig = config.getJsonObject("rdf-store", null);
    JsonObject httpConfig = config.getJsonObject("http-config", null);
    try {
      if (rdfStoreConfig != null && !rdfStoreConfig.getBoolean("in-memory", false)) {
        String storePath = rdfStoreConfig.getString("store-path", "data/");
        File dataDir = new File(storePath);
        repository = new SailRepository(new NativeStore(dataDir));

        // TODO - check if any Authorized artifacts are already stored in the repository and populate the AuthorizationRegistry
      } else {
        repository = new SailRepository(new MemoryStore());
      }
    } catch (ClassCastException e) {
      LOGGER.error("Exception raised while reading rdf-store config properties: " + e.getMessage());
      repository = new SailRepository(new MemoryStore());
    }

    rdfImpl = new RDF4J();
    dataset = rdfImpl.asDataset(repository, RDF4J.Option.handleInitAndShutdown);
    //Check if the dataset is empty
    if (dataset.size() == 0) {
      LOGGER.info("Empty RDF dataset created");
    } else {
      // save all the artifacts in the dataset in a list of Strings, extracting only the triples that contain the word "artifact"
      //and save them subject-predicate-object format
      
      List<String> artifacts = dataset.stream().filter(triple -> triple.getSubject().toString().contains("artifact"))
                                .map(triple -> "Subject:" + triple.getSubject().toString() + " "
                                 + "Predicate:" + triple.getPredicate().toString() + " " 
                                 + "Object:" + triple.getObject().toString()).collect(Collectors.toList());
      
      

      // From the list of strings, extract only the unique subjects 
      Set<String> uniqueSubjects = new HashSet<>(artifacts.stream()
          .map(artifact -> artifact.substring(artifact.indexOf("Subject:") + 8, artifact.indexOf(" Predicate:")))
          .collect(Collectors.toSet()));

      //now for every unigue subject

      for (String subject : uniqueSubjects) {
        //take the object of the triple where the subject is the current subject and the predicate is "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"
        //and print it in the file
        String artefactType = "";
        String artefactName = "";
        final String workspaceName = subject.substring(subject.indexOf("workspaces/") + 11, subject.indexOf("/artifacts"));
        final String envName = subject.substring(subject.indexOf("environments/") + 13, subject.indexOf("/workspaces"));;
        String agent = httpConfig.getString("base-uri") + "alexAgent";
        for (String artifact : artifacts) {
          if (artifact.contains("Subject:" + subject + " "
            + "Predicate:http://www.w3.org/1999/02/22-rdf-syntax-ns#type"
            + " " + "Object:" + httpConfig.getString("base-uri"))) {
            artefactType = artifact.substring(artifact.indexOf("Object:") + 7);
          }
          if (artifact.contains("Subject:" + subject + " "
            + "Predicate:https://www.w3.org/2019/wot/td#title")) {
            artefactName = artifact.substring(artifact.indexOf("Object:") + 7);
            //remove the quotes from the name
            artefactName = artefactName.substring(1, artefactName.length() - 1);
          }
        }
        //extract the workspace name of the current subject
        //he is beetween workspaces/ and /artifacts
       
        //Create a JsonObject named JsonRepresentation that contain artefactType, artefactName

        JsonObject JsonRepresentation = new JsonObject().put("artifactName", artefactName).put("artifactClass", artefactType);

        //now make a string out of representation
        String representation = JsonRepresentation.toString();

        LOGGER.info("Recreate the workspace " + workspaceName);

        DeliveryOptions optionsWsp = new DeliveryOptions()
          .addHeader(CartagoVerticle.AGENT_ID, agent)
          .addHeader(CartagoVerticle.WORKSPACE_NAME, workspaceName)
          .addHeader(HttpEntityHandler.REQUEST_METHOD, CartagoVerticle.CREATE_WORKSPACE)
          .addHeader(CartagoVerticle.ENV_NAME, envName);

        vertx.eventBus().request(CartagoVerticle.BUS_ADDRESS, representation, optionsWsp,
          response -> {
            if (response.succeeded()) {
              HypermediaArtifactRegistry.getInstance().addWorkspace(envName, workspaceName);
              LOGGER.info("CArtAgO workspace recreated");
              //print the response
              LOGGER.info(response.result().body());
            } else {
              LOGGER.error("CArtAgO workspace not recreated" + response.cause());
            }
          });
          
        LOGGER.info("Recreate the artifact " + subject);

        DeliveryOptions options = new DeliveryOptions()
          .addHeader(CartagoVerticle.AGENT_ID, agent)
          .addHeader(CartagoVerticle.WORKSPACE_NAME, workspaceName)
          .addHeader(HttpEntityHandler.REQUEST_METHOD, CartagoVerticle.CREATE_ARTIFACT)
          .addHeader(CartagoVerticle.ARTIFACT_NAME, artefactName);

          vertx.eventBus().request(CartagoVerticle.BUS_ADDRESS, representation, options,
            response -> {
              if (response.succeeded()) {
                
                LOGGER.info("CArtAgO artifact recreated");
              } else {
                LOGGER.error("CArtAgO artifact not recreated" + response.cause());
              }
            });
      }
    }
  }

  @Override
  public boolean containsEntityGraph(IRI entityIri) {
    return dataset.contains(Optional.of(entityIri), null, null, null);
  }

  @Override
  public Optional<Graph> getEntityGraph(IRI entityIri) {
    return dataset.getGraph(entityIri);
  }

  @Override
  public void createEntityGraph(IRI entityIri, Graph entityGraph) {
    if (entityGraph instanceof RDF4JGraph) {
      addEntityGraph(entityIri, entityGraph);
    } else {
      throw new IllegalArgumentException("Unsupported RDF graph implementation");
    }
  }

  @Override
  public void updateEntityGraph(IRI entityIri, Graph entityGraph) {
    if (entityGraph instanceof RDF4JGraph) {
      deleteEntityGraph(entityIri);
      addEntityGraph(entityIri, entityGraph);
    } else {
      throw new IllegalArgumentException("Unsupported RDF graph implementation");
    }
  }

  @Override
  public void deleteEntityGraph(IRI entityIri) {
    dataset.remove(Optional.of(entityIri), null, null, null);
  }

  @Override
  public IRI createIRI(String iriString) throws IllegalArgumentException {
    return rdfImpl.createIRI(iriString);
  }

  @Override
  public String graphToString(Graph graph, RDFSyntax syntax) throws IllegalArgumentException, IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();

    RDFWriter writer;

    if (syntax.equals(RDFSyntax.TURTLE)) {
      writer = Rio.createWriter(RDFFormat.TURTLE, out);
    } else if (syntax.equals(RDFSyntax.JSONLD)) {
      writer = Rio.createWriter(RDFFormat.JSONLD, out);
      writer.getWriterConfig().set(JSONLDSettings.JSONLD_MODE, JSONLDMode.FLATTEN);
      writer.getWriterConfig().set(JSONLDSettings.USE_NATIVE_TYPES, true);
      writer.getWriterConfig().set(JSONLDSettings.OPTIMIZE, true);
    } else {
      throw new IllegalArgumentException("Unsupported RDF serialization format.");
    }

    writer.getWriterConfig()
      .set(BasicWriterSettings.PRETTY_PRINT, true)
      .set(BasicWriterSettings.RDF_LANGSTRING_TO_LANG_LITERAL, true)
      .set(BasicWriterSettings.XSD_STRING_TO_PLAIN_LITERAL, true)
      .set(BasicWriterSettings.INLINE_BLANK_NODES, true);
      
    if (graph instanceof RDF4JGraph) {
      try {
        writer.startRDF();

        writer.handleNamespace("eve", "http://w3id.org/eve#");
        writer.handleNamespace("td", "https://www.w3.org/2019/wot/td#");
        writer.handleNamespace("htv", "http://www.w3.org/2011/http#");
        writer.handleNamespace("hctl", "https://www.w3.org/2019/wot/hypermedia#");
        writer.handleNamespace("wotsec", "https://www.w3.org/2019/wot/security#");
        writer.handleNamespace("dct", "http://purl.org/dc/terms/");
        writer.handleNamespace("js", "https://www.w3.org/2019/wot/json-schema#");
        writer.handleNamespace("saref", "https://w3id.org/saref#");

        try (Stream<RDF4JTriple> stream = ((RDF4JGraph) graph).stream()) {
          stream.forEach(triple -> writer.handleStatement(triple.asStatement()));
        }
        writer.endRDF();
      }
      catch (RDF4JException e) {
        throw new IOException("RDF handler exception: " + e.getMessage());
      }
      catch (UnsupportedRDFormatException e) {
        throw new IllegalArgumentException("Unsupported RDF syntax: " + e.getMessage());
      }
      finally {
        out.close();
      }
    } else {
      throw new IllegalArgumentException("Unsupported RDF graph implementation");
    }
    return out.toString();
  }

  @Override
  public Utils.Tuple<Graph, Model> stringToGraph(String graphString, IRI baseIRI, RDFSyntax syntax) throws IllegalArgumentException, IOException {
    StringReader stringReader = new StringReader(graphString);

    RDFFormat format = RDFFormat.JSONLD;
    if (syntax.equals(RDFSyntax.TURTLE)) {
      format = RDFFormat.TURTLE;
    }

    RDFParser rdfParser = Rio.createParser(format);
    Model model = new LinkedHashModel();
    rdfParser.setRDFHandler(new StatementCollector(model));

    try {
      rdfParser.parse(stringReader, baseIRI.getIRIString());
    }
    catch (RDFParseException e) {
      throw new IllegalArgumentException("RDF parse error: " + e.getMessage());
    }
    catch (RDFHandlerException e) {
      throw new IOException("RDF handler exception: " + e.getMessage());
    }
    finally {
      stringReader.close();
    }
    return new Utils.Tuple<Graph,Model>(rdfImpl.asGraph(model), model);
  }

  public void addEntityGraph(IRI entityIri, Graph entityGraph) {
    try(Stream<RDF4JTriple> stream = ((RDF4JGraph) entityGraph).stream()) {
      stream.forEach(triple -> dataset.add(entityIri, triple.getSubject(), triple.getPredicate(), triple.getObject()));
    }
  }
}

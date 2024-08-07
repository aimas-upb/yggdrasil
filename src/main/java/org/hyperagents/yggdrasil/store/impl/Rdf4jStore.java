package org.hyperagents.yggdrasil.store.impl;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.Optional;
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
import org.hyperagents.yggdrasil.context.http.Utils;
import org.hyperagents.yggdrasil.store.RdfStore;

import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;


public class Rdf4jStore implements RdfStore {
  private final static Logger LOGGER = LoggerFactory.getLogger(RdfStore.class.getName());

  private final RDF4J rdfImpl;
  private final Dataset dataset;

  public Rdf4jStore(JsonObject config) {
    Repository repository;

    try {
      if (config != null && !config.getBoolean("in-memory", false)) {
        String storePath = config.getString("store-path", "data/");
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

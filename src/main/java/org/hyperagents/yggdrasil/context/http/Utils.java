package org.hyperagents.yggdrasil.context.http;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;

import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryResult;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class Utils {
  
  // Logger
    private static final Logger LOGGER = LoggerFactory.getLogger(ContextMgmtVerticle.class.getName());


  public static class Tuple<X, Y> {
    private final X x;
    private final Y y;

    public Tuple(X x, Y y) {
      this.x = x;
      this.y = y;
    }

    public X getFirst() {
      return x;
    }

    public Y getSecond() {
      return y;
    }
  }
  
  public static String getConceptLocalName(String uri) {
    // get the local name of the concept denoted by the uri, checking whether it is the last part of the URI or a fragment
    String[] uriParts = uri.split("/");
    String localName = uriParts[uriParts.length - 1];
    if (localName.contains("#")) {
      localName = localName.split("#")[1];
    }
    return localName;
  }

  public static String parseRSPQLQuery(String queryFileURI) {
    // parse the RSPQL query from the provided file URI, by opening it as a stream and reading its content
    try {
      URI queryUri = new URI(queryFileURI);
      FileReader fileReader = new FileReader(new File(queryUri));

      StringBuilder queryBuilder = new StringBuilder();
      int character;
      while ((character = fileReader.read()) != -1) {
        queryBuilder.append((char) character);
      }
      fileReader.close();
      String qString = queryBuilder.toString();
      
      return qString;

    } catch (URISyntaxException e) {
      System.err.println("Error while parsing the RSPQL query from the file URI " + queryFileURI + ": " + e.getMessage());
      return null;
    }
    catch (IOException e) {
      System.err.println("Error while parsing the RSPQL query from the file URI " + queryFileURI + ": " + e.getMessage());
      return null;
    }
  }

  public static void serializeSailRepository(SailRepository repo, File repositoryFile) {
    // serialize the provided SailRepository to the provided file
    try (RepositoryConnection conn = repo.getConnection()) {
            OutputStream  out = new FileOutputStream(repositoryFile);
            Model model = new LinkedHashModel();

            // Retrieve the content of the repository
            RepositoryResult<Statement> statements = conn.getStatements(null, null, null);

            // Iterate over the statements and add them to the Model
            while (statements.hasNext()) {
                Statement st = statements.next();
                model.add(st);
            }

            // Use the Rio writer to serialize the model to Turtle format
            Rio.write(model, out, RDFFormat.TURTLE);
        } catch (Exception e) {
            LOGGER.error("Error exporting the contents of the validation data repository: " + e.getMessage());
        }
  }

  public static void serializeRepoConnection(RepositoryConnection conn, File repositoryFile, Resource... contexts) {
    // serialize the provided RepositoryConnection to the provided file
    try {
            OutputStream  out = new FileOutputStream(repositoryFile);
            Model model = new LinkedHashModel();

            // Retrieve the content of the repository
            RepositoryResult<Statement> statements = conn.getStatements(null, null, null, contexts);

            // Iterate over the statements and add them to the Model
            while (statements.hasNext()) {
                Statement st = statements.next();
                model.add(st);
            }

            // Use the Rio writer to serialize the model to Turtle format
            Rio.write(model, out, RDFFormat.TURTLE);
        } catch (Exception e) {
            LOGGER.error("Error exporting the contents of the validation data repository: " + e.getMessage());
        }
  }

}

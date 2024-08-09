package org.hyperagents.yggdrasil.context.http;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

public class Utils {
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
}

package org.folio.rest.support;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;

public class Response {
  protected final String body;
  private final int statusCode;

  public Response(int statusCode) {
    this.body = null;
    this.statusCode = statusCode;
  }

  public Response(int statusCode, String body) {
    this.body = body;
    this.statusCode = statusCode;
  }

  public static Response from(HttpResponse<Buffer> response) {
    return new Response(response.statusCode(),
      response.bodyAsString());
  }

  public int getStatusCode() {
    return statusCode;
  }

  public String getBody() {
    return body;
  }

  public boolean hasBody() {
    return getBody() != null && getBody().trim() != "";
  }
  
  public JsonObject getJson() {
    if(hasBody()) {
      return new JsonObject(getBody());
    }
    else {
      return new JsonObject();
    }
  }
}

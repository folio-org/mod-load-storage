package org.folio.rest.api;

import static org.folio.rest.support.builders.LoanPolicyRequestBuilder.defaultRollingPolicy;
import static org.folio.rest.support.builders.LoanPolicyRequestBuilder.emptyPolicy;
import static org.folio.rest.support.matchers.periodJsonObjectMatcher.matchesPeriod;
import static org.folio.rest.support.matchers.OkapiResponseStatusCodeMatchers.isNoContent;
import static org.folio.rest.support.matchers.OkapiResponseStatusCodeMatchers.matchesBadRequest;
import static org.folio.rest.support.matchers.OkapiResponseStatusCodeMatchers.matchesCreated;
import static org.folio.rest.support.matchers.OkapiResponseStatusCodeMatchers.matchesOk;
import static org.folio.rest.support.matchers.OkapiResponseStatusCodeMatchers.matchesNoContent;
import static org.folio.rest.support.matchers.OkapiResponseStatusCodeMatchers.matchesNotFound;
import static org.folio.rest.support.matchers.OkapiResponseStatusCodeMatchers.matchesUnprocessableEntity;
import static org.folio.rest.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static org.folio.rest.support.matchers.ValidationErrorMatchers.hasMessageContaining;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.folio.rest.jaxrs.model.Period;
import org.folio.rest.support.ApiTests;
import org.folio.rest.support.IndividualResource;
import org.folio.rest.support.JsonArrayHelper;
import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.folio.rest.support.TextResponse;
import org.folio.rest.support.builders.LoanPolicyRequestBuilder;
import org.hamcrest.junit.MatcherAssert;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class LoanPoliciesApiTest extends ApiTests {
  @Before
  public void beforeEach()
    throws MalformedURLException {

    System.out.println("attempting full delete LoanPoliciesApiTest.....");
    StorageTestSuite.deleteAll(loanPolicyStorageUrl());
  }

  @After
  public void checkIdsAfterEach() {
    StorageTestSuite.checkForMismatchedIDs("loan_policy");
  }

  @Test
  public void loanPolicyWithFixedDate() throws
    InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID fddId = UUID.randomUUID();

    ///////////// create fixed due date to use as foreign key
    JsonObject fdd = FixedDueDateApiTest.createFixedDueDate(fddId.toString(), "semester_test", "desc");
    JsonObject fddSchedule = FixedDueDateApiTest.createSchedule("2017-01-01T10:00:00.000+0000",
      "2017-03-03T10:00:00.000+0000", "2017-04-04T10:00:00.000+0000");
    fdd.put(FixedDueDateApiTest.SCHEDULE_SECTION, new JsonArray().add(fddSchedule));

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();
    client.post(FixedDueDateApiTest.dueDateURL(), fdd, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));
    JsonResponse response = createCompleted.get(5, TimeUnit.SECONDS);
    assertThat(String.format("Failed to create due date: %s", response.getBody()),
      response, matchesCreated());
    JsonObject representation = response.getJson();
    assertThat(representation.getString("id"), is(fddId.toString()));
    ////////////////////////////////////////////////////////////

    UUID id1 = UUID.randomUUID();

    //////////// create loan policy with foreign key to fdd
    JsonObject loanPolicyRequest = defaultRollingPolicy()
      .withId(id1)
      .withName("Example Loan Policy")
      .withDescription("An example loan policy")
      .create();
    loanPolicyRequest.getJsonObject("loansPolicy").put("fixedDueDateScheduleId", fddId.toString());
    CompletableFuture<JsonResponse> createLPCompleted = new CompletableFuture<>();
    client.post(loanPolicyStorageUrl(),
      loanPolicyRequest, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createLPCompleted));
    JsonResponse lpResponse = createLPCompleted.get(5, TimeUnit.SECONDS);
    assertThat(String.format("Failed to create loan policy: %s", lpResponse.getBody()),
      lpResponse, matchesCreated());
    ////////////////////////////////////////////////////////

    ///////////non-existent foreign key
    UUID id2 = UUID.randomUUID();
    JsonObject badLoanPolicyRequest = defaultRollingPolicy()
        .withId(id2)
        .withName("Example Loan Policy")
        .withDescription("An example loan policy")
        .create();
    badLoanPolicyRequest.getJsonObject("loansPolicy")
      .put("fixedDueDateScheduleId", "cb201cd6-296c-4457-9ac4-617d9584e27b");
    CompletableFuture<JsonResponse> createBadLPCompleted = new CompletableFuture<>();
    client.post(loanPolicyStorageUrl(),
      badLoanPolicyRequest, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createBadLPCompleted));
    JsonResponse badlpResponse = createBadLPCompleted.get(5, TimeUnit.SECONDS);
    assertThat(String.format("Non-existent foreign key: %s", badlpResponse.getBody()),
      badlpResponse, matchesUnprocessableEntity());
    //////////////////////////////////////////

    ///////////bad foreign key
    id2 = UUID.randomUUID();
    JsonObject bad2LoanPolicyRequest = defaultRollingPolicy()
        .withId(id2)
        .withName("Example Loan Policy")
        .withDescription("An example loan policy")
        .create();
    bad2LoanPolicyRequest.getJsonObject("loansPolicy")
      .put("fixedDueDateScheduleId", UUID.randomUUID().toString());
    CompletableFuture<JsonResponse> createBadLP2Completed = new CompletableFuture<>();
    client.post(loanPolicyStorageUrl(),
      bad2LoanPolicyRequest, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createBadLP2Completed));
    JsonResponse badlpResponse2 = createBadLP2Completed.get(5, TimeUnit.SECONDS);
    assertThat("Bad foreign key", badlpResponse2, matchesUnprocessableEntity());
    //////////////////////////////////////////

    id2 = UUID.randomUUID();

    //////////// create loan policy with fk to jsonb->'renewalsPolicy'->>'alternateFixedDueDateScheduleId'
    JsonObject loanPolicyRequest4 = defaultRollingPolicy()
      .withId(id2)
      .withName("Example Loan Policy")
      .withDescription("An example loan policy")
      .create();
    JsonObject renewalsPolicy = loanPolicyRequest4.getJsonObject("renewalsPolicy");
    renewalsPolicy.put("alternateFixedDueDateScheduleId", fddId.toString());
    CompletableFuture<JsonResponse> createLPHeirarchyCompleted = new CompletableFuture<>();
    client.post(loanPolicyStorageUrl(),
      loanPolicyRequest4, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createLPHeirarchyCompleted));
    JsonResponse lpHierarchyResponse = createLPHeirarchyCompleted.get(5, TimeUnit.SECONDS);
    assertThat(String.format("Failed to create loan policy: %s", lpHierarchyResponse.getBody()),
      lpHierarchyResponse, matchesCreated());

    ///validation, fk to fixedDueDateScheduleId required once profileid = fixed
    CompletableFuture<JsonResponse> createpdateV2Completed = new CompletableFuture<>();
    JsonObject loanPolicyRequest8 = defaultRollingPolicy()
        .withId(id2)
        .withName("Example Loan Policy")
        .withDescription("An example loan policy")
        .create();
    loanPolicyRequest8.getJsonObject("loansPolicy").put("profileId", "Fixed");
    loanPolicyRequest8.put("renewable", false);
    client.put(loanPolicyStorageUrl("/"+id2), loanPolicyRequest8, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createpdateV2Completed));
    JsonResponse updateV2response = createpdateV2Completed.get(5, TimeUnit.SECONDS);
    assertThat(String.format("Failed to create due date: %s", updateV2response.getBody()),
      updateV2response, matchesUnprocessableEntity());

    //update alternateFixedDueDateScheduleId with a bad (non existent) id
    CompletableFuture<TextResponse> updateCompleted = new CompletableFuture<>();
    renewalsPolicy.put("alternateFixedDueDateScheduleId", "ab201cd6-296c-4457-9ac4-617d9584e27b");
    client.put(loanPolicyStorageUrl("/"+id2),
      loanPolicyRequest4, StorageTestSuite.TENANT_ID,
      ResponseHandler.text(updateCompleted));
    TextResponse updateResponse = updateCompleted.get(5, TimeUnit.SECONDS);
    assertThat("update loanPolicy: set alternateFixedDueDateScheduleId = non existent id",
        updateResponse, matchesBadRequest());

    //delete loan policy //////////////////
    System.out.println("Running: DELETE " + loanPolicyStorageUrl("/"+id2));
    CompletableFuture<Response> delCompleted2 = new CompletableFuture<>();
    client.delete(loanPolicyStorageUrl("/"+id2), StorageTestSuite.TENANT_ID,
      ResponseHandler.empty(delCompleted2));
    Response delCompleted4Response = delCompleted2.get(5, TimeUnit.SECONDS);
    assertThat(String.format("Failed to delete due date: %s", loanPolicyStorageUrl("/"+id2)),
      delCompleted4Response.getStatusCode(), isNoContent());
    ///////////////////////////////////////

    //delete loan policy //////////////////
    System.out.println("Running: DELETE " + loanPolicyStorageUrl("/"+id1));
    CompletableFuture<Response> delCompleted9 = new CompletableFuture<>();
    client.delete(loanPolicyStorageUrl("/"+id1), StorageTestSuite.TENANT_ID,
      ResponseHandler.empty(delCompleted9));
    Response delCompleted5Response = delCompleted9.get(5, TimeUnit.SECONDS);
    assertThat(String.format("Failed to delete due date: %s", loanPolicyStorageUrl("/"+id1)),
      delCompleted5Response.getStatusCode(), isNoContent());
    ///////////////////////////////////////

    //// try to delete the fdd - not allowed since referenced by loan policy ///////////////////////
    System.out.println("Running: DELETE " + FixedDueDateApiTest.dueDateURL("/"+fddId.toString()));
    CompletableFuture<Response> delCompleted3 = new CompletableFuture<>();
    client.delete(FixedDueDateApiTest.dueDateURL("/"+fddId.toString()), StorageTestSuite.TENANT_ID,
      ResponseHandler.empty(delCompleted3));
    Response delCompleted6Response = delCompleted3.get(5, TimeUnit.SECONDS);
    assertThat(String.format("Failed to delete due date: %s", FixedDueDateApiTest.dueDateURL("/"+fddId.toString())),
      delCompleted6Response.getStatusCode(), isNoContent());
    //////////////////////////////////////////////////////////////////////////////////////////

    //// try to delete all fdds (uses cascade so will succeed) ///////////////////////
    System.out.println("Running: DELETE " + FixedDueDateApiTest.dueDateURL());
    CompletableFuture<Response> delAllCompleted = new CompletableFuture<>();
    client.delete(FixedDueDateApiTest.dueDateURL(), StorageTestSuite.TENANT_ID,
      ResponseHandler.empty(delAllCompleted));
    Response delAllCompleted4Response = delAllCompleted.get(5, TimeUnit.SECONDS);
    assertThat(String.format("Failed to delete due date: %s", FixedDueDateApiTest.dueDateURL()),
      delAllCompleted4Response.getStatusCode(), isNoContent());
    //////////////////////////////////////////////////////////////////////////////////////////

  }

  @Test
  public void cannotUpdateWhenFixedIsRenewable() throws Exception {
    UUID id = UUID.randomUUID();
    CompletableFuture<JsonResponse> completed = new CompletableFuture<>();
    JsonObject loanPolicy = defaultRollingPolicy().withId(id).create();
    createLoanPolicy(loanPolicy);
    loanPolicy.getJsonObject("loansPolicy").put("profileId", "Fixed");
    client.put(loanPolicyStorageUrl("/" + id), loanPolicy, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(completed));
    JsonResponse response = completed.get(5, TimeUnit.SECONDS);
    String message = String.format("update when fixed is renewable, PUT request=%s, response=%s",
        loanPolicy.encodePrettily(), response.getJson().encodePrettily());
    assertThat(message, response, matchesUnprocessableEntity());
    assertThat(message, response.getJson(),
        hasErrorWith(hasMessageContaining("profile is Fixed")));
  }

  @Test
  public void cannotCreateLoanPolicyWithLoanableFalseAndLoansPolicy() {
    JsonObject loanPolicyRequest = new JsonObject()
        .put("name", "9").put("loanable", false).put("renewable", false)
        .put("loansPolicy", new JsonObject());
    assertThat(postLoanPolicyWith422(loanPolicyRequest),
        hasErrorWith(hasMessageContaining("Fixed due date")));
  }

  @Test
  public void canCreateALoanPolicy()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    UUID id = UUID.randomUUID();

    JsonObject loanPolicyRequest = defaultRollingPolicy()
      .withId(id)
      .withName("Example Loan Policy")
      .withDescription("An example loan policy")
      .create();

    client.post(loanPolicyStorageUrl(),
      loanPolicyRequest, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    JsonResponse response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to create loan policy: %s", response.getBody()),
      response, matchesCreated());

    JsonObject representation = response.getJson();

    assertThat(representation.getString("id"), is(id.toString()));
    assertThat(representation.getString("name"), is("Example Loan Policy"));
    assertThat(representation.getString("description"), is("An example loan policy"));
    assertThat(representation.getBoolean("loanable"), is(true));
    assertThat(representation.getBoolean("renewable"), is(true));

    assertThat(representation.containsKey("loansPolicy"), is(true));
    assertThat(representation.containsKey("metadata"), is(true));

    JsonObject loansPolicy = representation.getJsonObject("loansPolicy");

    assertThat(loansPolicy.getString("profileId"), is("Rolling"));
    assertThat(loansPolicy.getJsonObject("period"), matchesPeriod(1, "Months"));
    assertThat(loansPolicy.getString("closedLibraryDueDateManagementId"), is("CURRENT_DUE_DATE"));
    assertThat(loansPolicy.getJsonObject("gracePeriod"), matchesPeriod(7, "Days"));

    checkRequestManagementSection(representation);

    assertThat(representation.containsKey("renewalsPolicy"), is(true));

    JsonObject renewalsPolicy = representation.getJsonObject("renewalsPolicy");

    assertThat(renewalsPolicy.getBoolean("unlimited"), is(true));
    assertThat(renewalsPolicy.getString("renewFromId"), is("CURRENT_DUE_DATE"));
    assertThat(renewalsPolicy.getBoolean("differentPeriod"), is(true));
    assertThat(renewalsPolicy.getJsonObject("period"), matchesPeriod(30, "Days"));
  }

  @Test
  public void canCreateARollingLoanPolicyLimitedByFixedDueDateSchedule()
    throws Exception {

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    UUID id = UUID.randomUUID();

    DateTime from = DateTime.now().minusMonths(3);
    DateTime to = DateTime.now().plusMonths(3);
    DateTime dueDate = to.plusDays(15);

    IndividualResource fixedDueDateSchedule =
      createFixedDueDateSchedule("example limiting schedule", from, to, dueDate);

    JsonObject loanPolicyRequest = LoanPolicyRequestBuilder.emptyPolicy()
      .withId(id)
      .withName("Example Loan Policy")
      .withDescription("An example loan policy")
      .rolling(2, "Days", fixedDueDateSchedule.getId())
      .create();

    client.post(loanPolicyStorageUrl(),
      loanPolicyRequest, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    JsonResponse response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to create loan policy: %s", response.getBody()),
      response, matchesCreated());

    JsonObject representation = response.getJson();

    assertThat(representation.getString("id"), is(id.toString()));
    assertThat(representation.getString("name"), is("Example Loan Policy"));
    assertThat(representation.getString("description"), is("An example loan policy"));
    assertThat(representation.getBoolean("loanable"), is(true));
    assertThat(representation.getBoolean("renewable"), is(false));

    assertThat(representation.containsKey("loansPolicy"), is(true));
    assertThat(representation.containsKey("metadata"), is(true));

    JsonObject loansPolicy = representation.getJsonObject("loansPolicy");

    assertThat(loansPolicy.getString("profileId"), is("Rolling"));
    assertThat(loansPolicy.getJsonObject("period"), matchesPeriod(2, "Days"));
    assertThat(loansPolicy.getString("fixedDueDateScheduleId"), is(fixedDueDateSchedule.getId()));
  }

  @Test
  public void cannotCreateALoanPolicyWithAdditionalPropertiesInLoanPolicy()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    UUID id = UUID.randomUUID();

    JsonObject loanPolicyRequest = defaultRollingPolicy()
      .withId(id)
      .withName("Example Loan Policy")
      .withDescription("An example loan policy")
      .create();

    loanPolicyRequest.getJsonObject("loansPolicy").put("anAdditionalProperty", "blah");

    client.post(loanPolicyStorageUrl(),
      loanPolicyRequest, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    JsonResponse response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Should fail to create loan policy: %s", response.getBody()),
      response, matchesUnprocessableEntity());
  }

  @Test
  public void canCreateLoanPolicyWithItemLimitWithinBounds()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    UUID id = UUID.randomUUID();

    JsonObject loanPolicyRequest = defaultRollingPolicy()
      .withId(id)
      .withName("Example Loan Policy")
      .withDescription("An example loan policy")
      .create();

    loanPolicyRequest.getJsonObject("loansPolicy").put("itemLimit", 1000);

    client.post(loanPolicyStorageUrl(),
      loanPolicyRequest, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    JsonResponse response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to create loan policy: %s", response.getBody()),
      response, matchesCreated());
    assertThat(response.getJson().getJsonObject("loansPolicy").getInteger("itemLimit"), is(1000));
  }

  @Test
  public void cannotCreateLoanPolicyWithItemLimitBelowMinimum()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    UUID id = UUID.randomUUID();

    JsonObject loanPolicyRequest = defaultRollingPolicy()
      .withId(id)
      .withName("Example Loan Policy")
      .withDescription("An example loan policy")
      .create();

    loanPolicyRequest.getJsonObject("loansPolicy").put("itemLimit", 0);

    client.post(loanPolicyStorageUrl(),
      loanPolicyRequest, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    JsonResponse response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Should fail to create loan policy: %s", response.getBody()),
      response, matchesUnprocessableEntity());

    JsonObject error = getErrorFromResponse(response);
    JsonObject parameters = error.getJsonArray("parameters").getJsonObject(0);

    assertThat(error.getString("message"), is("must be greater than or equal to 1"));
    assertThat(parameters.getString("key"), is("loansPolicy.itemLimit"));
    assertThat(parameters.getString("value"), is("0"));
  }

  @Test
  public void cannotCreateLoanPolicyWithItemLimitAboveMaximum()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    UUID id = UUID.randomUUID();

    JsonObject loanPolicyRequest = defaultRollingPolicy()
      .withId(id)
      .withName("Example Loan Policy")
      .withDescription("An example loan policy")
      .create();

    loanPolicyRequest.getJsonObject("loansPolicy").put("itemLimit", 10_000);

    client.post(loanPolicyStorageUrl(),
      loanPolicyRequest, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    JsonResponse response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Should fail to create loan policy: %s", response.getBody()),
      response, matchesUnprocessableEntity());

    JsonObject error = getErrorFromResponse(response);
    JsonObject parameters = error.getJsonArray("parameters").getJsonObject(0);

    assertThat(error.getString("message"), is("must be less than or equal to 9999"));
    assertThat(parameters.getString("key"), is("loansPolicy.itemLimit"));
    assertThat(parameters.getString("value"), is("10000"));
  }

  @Test
  public void cannotCreateALoanPolicyWithInvalidPeriodInterval()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    UUID id = UUID.randomUUID();

    JsonObject loanPolicyRequest = defaultRollingPolicy()
      .withId(id)
      .withName("Example Loan Policy")
      .withDescription("An example loan policy")
      .create();

    JsonObject period = new JsonObject();

    period.put("duration", 1);
    period.put("intervalId", "Foo");

    loanPolicyRequest.getJsonObject("loansPolicy").put("period", period);

    client.post(loanPolicyStorageUrl(),
      loanPolicyRequest, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    JsonResponse response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Should fail to create loan policy: %s", response.getBody()),
      response, matchesBadRequest());
  }

  @Test
  public void canCreateALoanPolicyWithoutAnId()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    JsonObject loanPolicyRequest = defaultRollingPolicy()
      .withNoId()
      .create();

    client.post(loanPolicyStorageUrl(),
      loanPolicyRequest, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    JsonResponse response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to create loan policy: %s", response.getBody()),
      response, matchesCreated());

    JsonObject representation = response.getJson();

    assertThat(representation.getString("id"), is(notNullValue()));
  }

  @Test
  public void canCreateALoanPolicyAtASpecificLocation()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    UUID id = UUID.randomUUID();

    JsonObject loanPolicyRequest = defaultRollingPolicy()
      .withId(id)
      .create();

    client.put(loanPolicyStorageUrl(String.format("/%s", id.toString())),
      loanPolicyRequest, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    JsonResponse createResponse = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to create loan policy: %s", createResponse.getBody()),
      createResponse, matchesNoContent());

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();

    client.get(loanPolicyStorageUrl(String.format("/%s", id.toString())),
      StorageTestSuite.TENANT_ID,
      ResponseHandler.json(getCompleted));

    JsonResponse getResponse = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to get updated loan policy: %s", getResponse.getBody()),
      getResponse, matchesOk());

    JsonObject representation = getResponse.getJson();

    assertThat(representation.getString("id"), is(id.toString()));
    assertThat(representation.getString("description"), is("An example loan policy"));
    assertThat(representation.getString("name"), is("Example Loan Policy"));
    assertThat(representation.getBoolean("loanable"), is(true));
    assertThat(representation.getBoolean("renewable"), is(true));

    assertThat(representation.containsKey("loansPolicy"), is(true));

    JsonObject loansPolicy = representation.getJsonObject("loansPolicy");

    assertThat(loansPolicy.getString("profileId"), is("Rolling"));
    assertThat(loansPolicy.getJsonObject("period"), matchesPeriod(1, "Months"));
    assertThat(loansPolicy.getString("closedLibraryDueDateManagementId"), is("CURRENT_DUE_DATE"));
    assertThat(loansPolicy.getJsonObject("gracePeriod"), matchesPeriod(7, "Days"));

    checkRequestManagementSection(representation);

    assertThat(representation.containsKey("renewalsPolicy"), is(true));

    JsonObject renewalsPolicy = representation.getJsonObject("renewalsPolicy");

    assertThat(renewalsPolicy.getBoolean("unlimited"), is(true));
    assertThat(renewalsPolicy.getString("renewFromId"), is("CURRENT_DUE_DATE"));
    assertThat(renewalsPolicy.getBoolean("differentPeriod"), is(true));
    assertThat(renewalsPolicy.getJsonObject("period"), matchesPeriod(30, "Days"));
  }

  @Test
  public void canGetALoanPolicyById()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID id = UUID.randomUUID();

    createLoanPolicy(defaultRollingPolicy()
      .withId(id)
      .create()
    );

    JsonResponse getResponse = getById(id);

    assertThat(String.format("Failed to get loan policy: %s", getResponse.getBody()),
      getResponse, matchesOk());

    JsonObject representation = getResponse.getJson();

    assertThat(representation.getString("id"), is(notNullValue()));
    assertThat(representation.getString("description"), is("An example loan policy"));
    assertThat(representation.getString("name"), is("Example Loan Policy"));
    assertThat(representation.getBoolean("loanable"), is(true));
    assertThat(representation.getBoolean("renewable"), is(true));

    assertThat(representation.containsKey("loansPolicy"), is(true));

    JsonObject loansPolicy = representation.getJsonObject("loansPolicy");

    assertThat(loansPolicy.getString("profileId"), is("Rolling"));
    assertThat(loansPolicy.getJsonObject("period"), matchesPeriod(1, "Months"));
    assertThat(loansPolicy.getString("closedLibraryDueDateManagementId"), is("CURRENT_DUE_DATE"));
    assertThat(loansPolicy.getJsonObject("gracePeriod"), matchesPeriod(7, "Days"));
    assertThat(loansPolicy.getJsonObject("openingTimeOffset"), matchesPeriod(3, "Hours"));

    checkRequestManagementSection(representation);

    assertThat(representation.containsKey("renewalsPolicy"), is(true));

    JsonObject renewalsPolicy = representation.getJsonObject("renewalsPolicy");

    assertThat(renewalsPolicy.getBoolean("unlimited"), is(true));
    assertThat(renewalsPolicy.getString("renewFromId"), is("CURRENT_DUE_DATE"));
    assertThat(renewalsPolicy.getBoolean("differentPeriod"), is(true));
    assertThat(renewalsPolicy.getJsonObject("period"), matchesPeriod(30, "Days"));
  }

  @Test
  public void cannotGetLoanPolicyForUnknownId()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    JsonResponse getResponse = getById(UUID.randomUUID());

    assertThat(getResponse, matchesNotFound());
  }

  @Test
  public void canGetAllPolicies()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    String firstPolicyId = createLoanPolicy(defaultRollingPolicy().create()).getId();
    String secondPolicyId = createLoanPolicy(defaultRollingPolicy().create()).getId();
    String thirdPolicyId = createLoanPolicy(defaultRollingPolicy().create()).getId();
    String fourthPolicyId = createLoanPolicy(defaultRollingPolicy().create()).getId();

    CompletableFuture<JsonResponse> getAllCompleted = new CompletableFuture<>();

    client.get(loanPolicyStorageUrl(), StorageTestSuite.TENANT_ID,
      ResponseHandler.json(getAllCompleted));

    JsonResponse getAllResponse = getAllCompleted.get(5, TimeUnit.SECONDS);

    MatcherAssert.assertThat(String.format("Failed to get policies: %s",
      getAllResponse.getBody()),
      getAllResponse.getStatusCode(), is(200));

    JsonObject firstPage = getAllResponse.getJson();

    List<JsonObject> firstPolicyResults = JsonArrayHelper.toList(
      firstPage.getJsonArray("loanPolicies"));

    assertThat(firstPolicyResults.size(), is(4));
    assertThat(firstPage.getInteger("totalRecords"), is(4));

    assertThat(hasRecordWithId(firstPolicyResults, firstPolicyId), is(true));
    assertThat(hasRecordWithId(firstPolicyResults, secondPolicyId), is(true));
    assertThat(hasRecordWithId(firstPolicyResults, thirdPolicyId), is(true));
    assertThat(hasRecordWithId(firstPolicyResults, fourthPolicyId), is(true));
  }

  @Test
  public void canPageLoanPolicies()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    createLoanPolicy(defaultRollingPolicy().create());
    createLoanPolicy(defaultRollingPolicy().create());
    createLoanPolicy(defaultRollingPolicy().create());
    createLoanPolicy(defaultRollingPolicy().create());
    createLoanPolicy(defaultRollingPolicy().create());
    createLoanPolicy(defaultRollingPolicy().create());
    createLoanPolicy(defaultRollingPolicy().create());

    CompletableFuture<JsonResponse> firstPageCompleted = new CompletableFuture<>();
    CompletableFuture<JsonResponse> secondPageCompleted = new CompletableFuture<>();

    client.get(loanPolicyStorageUrl() + "?limit=4", StorageTestSuite.TENANT_ID,
      ResponseHandler.json(firstPageCompleted));

    client.get(loanPolicyStorageUrl() + "?limit=4&offset=4", StorageTestSuite.TENANT_ID,
      ResponseHandler.json(secondPageCompleted));

    JsonResponse firstPageResponse = firstPageCompleted.get(5, TimeUnit.SECONDS);
    JsonResponse secondPageResponse = secondPageCompleted.get(5, TimeUnit.SECONDS);

    MatcherAssert.assertThat(String.format("Failed to get first page of loan policies: %s",
      firstPageResponse.getBody()),
      firstPageResponse.getStatusCode(), is(200));

    MatcherAssert.assertThat(String.format("Failed to get second page of loans policies: %s",
      secondPageResponse.getBody()),
      secondPageResponse.getStatusCode(), is(200));

    JsonObject firstPage = firstPageResponse.getJson();
    JsonObject secondPage = secondPageResponse.getJson();

    JsonArray firstPageLoans = firstPage.getJsonArray("loanPolicies");
    JsonArray secondPageLoans = secondPage.getJsonArray("loanPolicies");

    MatcherAssert.assertThat(firstPageLoans.size(), is(4));
    MatcherAssert.assertThat(firstPage.getInteger("totalRecords"), is(7));

    MatcherAssert.assertThat(secondPageLoans.size(), is(3));
    MatcherAssert.assertThat(secondPage.getInteger("totalRecords"), is(7));
  }

  @Test
  public void canSearchForLoanPolicyById()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    String firstPolicyId = createLoanPolicy(defaultRollingPolicy().create()).getId();
    String secondPolicyId = createLoanPolicy(defaultRollingPolicy().create()).getId();

    String queryTemplate = loanPolicyStorageUrl() + "?query=id=\"%s\"";

    CompletableFuture<JsonResponse> searchForFirstPolicyCompleted = new CompletableFuture<>();
    CompletableFuture<JsonResponse> searchForSecondPolicyCompleted = new CompletableFuture<>();

    client.get(String.format(queryTemplate, firstPolicyId),
      StorageTestSuite.TENANT_ID, ResponseHandler.json(searchForFirstPolicyCompleted));

    client.get(String.format(queryTemplate, secondPolicyId),
      StorageTestSuite.TENANT_ID, ResponseHandler.json(searchForSecondPolicyCompleted));

    JsonResponse firstPolicySearchResponse = searchForFirstPolicyCompleted.get(5, TimeUnit.SECONDS);
    JsonResponse secondPolicySearchResponse = searchForSecondPolicyCompleted.get(5, TimeUnit.SECONDS);

    MatcherAssert.assertThat(String.format("Failed to get policy by id: %s",
      firstPolicySearchResponse.getBody()),
      firstPolicySearchResponse.getStatusCode(), is(200));

    MatcherAssert.assertThat(String.format("Failed to get policy by id: %s",
      secondPolicySearchResponse.getBody()),
      secondPolicySearchResponse.getStatusCode(), is(200));

    JsonObject firstPage = firstPolicySearchResponse.getJson();
    JsonObject secondPage = secondPolicySearchResponse.getJson();

    List<JsonObject> firstPolicyResults = JsonArrayHelper.toList(
      firstPage.getJsonArray("loanPolicies"));

    List<JsonObject> secondPolicyResults = JsonArrayHelper.toList(
      secondPage.getJsonArray("loanPolicies"));

    assertThat(firstPolicyResults.size(), is(1));
    assertThat(firstPage.getInteger("totalRecords"), is(1));

    assertThat(hasRecordWithId(firstPolicyResults, firstPolicyId), is(true));

    assertThat(secondPolicyResults.size(), is(1));
    assertThat(secondPage.getInteger("totalRecords"), is(1));

    assertThat(hasRecordWithId(firstPolicyResults, firstPolicyId), is(true));
  }

  @Test
  public void canUpdateAnExistingLoanPolicyByReplacingItsRepresentation()
  throws InterruptedException,
  MalformedURLException,
  TimeoutException,
  ExecutionException {

    UUID id = UUID.randomUUID();

    createLoanPolicy(defaultRollingPolicy().withId(id).create());

    CompletableFuture<JsonResponse> updateCompleted = new CompletableFuture<>();

    JsonObject loanPolicyRequest = defaultRollingPolicy()
      .withId(id)
      .withName("A Different Name")
      .withDescription("A different description")
      .create();

    client.put(loanPolicyStorageUrl(String.format("/%s", id.toString())),
      loanPolicyRequest, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(updateCompleted));

    JsonResponse updateResponse = updateCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to update loan policy: %s", updateResponse.getBody()),
      updateResponse, matchesNoContent());

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();

    client.get(loanPolicyStorageUrl(String.format("/%s", id.toString())),
      StorageTestSuite.TENANT_ID,
      ResponseHandler.json(getCompleted));

    JsonResponse getResponse = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to get updated loan policy: %s", getResponse.getBody()),
      getResponse, matchesOk());

    JsonObject representation = getResponse.getJson();

    assertThat(representation.getString("id"), is(id.toString()));
    assertThat(representation.getString("name"), is("A Different Name"));
    assertThat(representation.getString("description"), is("A different description"));
    assertThat(representation.getBoolean("loanable"), is(true));
    assertThat(representation.getBoolean("renewable"), is(true));

    assertThat(representation.containsKey("loansPolicy"), is(true));

    JsonObject loansPolicy = representation.getJsonObject("loansPolicy");

    assertThat(loansPolicy.getString("profileId"), is("Rolling"));
    assertThat(loansPolicy.getJsonObject("period"), matchesPeriod(1, "Months"));
    assertThat(loansPolicy.getString("closedLibraryDueDateManagementId"), is("CURRENT_DUE_DATE"));
    assertThat(loansPolicy.getJsonObject("gracePeriod"), matchesPeriod(7, "Days"));
    assertThat(loansPolicy.getJsonObject("openingTimeOffset"), matchesPeriod(3, "Hours"));

    checkRequestManagementSection(representation);

    assertThat(representation.containsKey("renewalsPolicy"), is(true));

    JsonObject renewalsPolicy = representation.getJsonObject("renewalsPolicy");

    assertThat(renewalsPolicy.getBoolean("unlimited"), is(true));
    assertThat(renewalsPolicy.getString("renewFromId"), is("CURRENT_DUE_DATE"));
    assertThat(renewalsPolicy.getBoolean("differentPeriod"), is(true));
    assertThat(renewalsPolicy.getJsonObject("period"), matchesPeriod(30, "Days"));
  }

  @Test
  public void canDeleteALoanPolicy()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    CompletableFuture<JsonResponse> deleteCompleted = new CompletableFuture<>();

    UUID id = UUID.randomUUID();

    createLoanPolicy(defaultRollingPolicy().withId(id).create());

    client.delete(loanPolicyStorageUrl(String.format("/%s", id.toString())),
      StorageTestSuite.TENANT_ID,
      ResponseHandler.json(deleteCompleted));

    JsonResponse createResponse = deleteCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to delete loan policy: %s", createResponse.getBody()),
      createResponse, matchesNoContent());

    JsonResponse getResponse = getById(id);

    assertThat(String.format("Found a deleted loan policy: %s", getResponse.getBody()),
      getResponse, matchesNotFound());
  }

  @Test
  public void cannotUseHoldAlternateRenewalLoanPeriodForFixedProfile() throws Exception {
    DateTime from = DateTime.now().minusMonths(3);
    DateTime to = DateTime.now().plusMonths(3);
    DateTime dueDate = to.plusDays(15);

    IndividualResource fixedDueDateSchedule =
      createFixedDueDateSchedule("semester_for_fixed_policy", from, to, dueDate);

    LoanPolicyRequestBuilder loanPolicy = emptyPolicy()
      .fixed(fixedDueDateSchedule.getId())
      .withAlternateFixedDueDateScheduleId(fixedDueDateSchedule.getId())
      .withHoldsRenewalLoanPeriod(new Period()
        .withDuration(1)
        .withIntervalId(Period.IntervalId.DAYS)
      );

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    client.post(loanPolicyStorageUrl(), loanPolicy.create(),
      StorageTestSuite.TENANT_ID, ResponseHandler.json(createCompleted));

    JsonResponse postResponse = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(postResponse.getStatusCode(), is(422));
    assertThat(postResponse.getJson().getJsonArray("errors")
        .getJsonObject(0).getString("message"),
      is("Alternate Renewal Loan Period for Holds is not allowed for policies with Fixed profile"));
  }

  @Test
  public void cannotUseRenewalsPeriodForFixedProfile() throws Exception {
    DateTime from = DateTime.now().minusMonths(3);
    DateTime to = DateTime.now().plusMonths(3);
    DateTime dueDate = to.plusDays(15);

    IndividualResource fixedDueDateSchedule = createFixedDueDateSchedule(
      "semester fixed policy test", from, to, dueDate);
    LoanPolicyRequestBuilder loanPolicy = emptyPolicy()
      .fixed(fixedDueDateSchedule.getId())
      .withAlternateFixedDueDateScheduleId(fixedDueDateSchedule.getId())
      .withName("test")
      .withRenewalPeriod(new Period()
        .withDuration(1)
        .withIntervalId(Period.IntervalId.DAYS)
      );

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    client.post(loanPolicyStorageUrl(), loanPolicy.create(),
      StorageTestSuite.TENANT_ID, ResponseHandler.json(createCompleted));

    JsonResponse postResponse = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(postResponse.getStatusCode(), is(422));
    assertThat(postResponse.getJson().getJsonArray("errors")
      .getJsonObject(0).getString("message"),
      is("Period in RenewalsPolicy is not allowed for policies with Fixed profile"));
  }

  static URL loanPolicyStorageUrl() throws MalformedURLException {
    return loanPolicyStorageUrl("");
  }

  private static URL loanPolicyStorageUrl(String subPath)
    throws MalformedURLException {

    return StorageTestSuite.storageUrl("/loan-policy-storage/loan-policies" + subPath);
  }

  private IndividualResource createLoanPolicy(JsonObject loanPolicyRequest)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    client.post(loanPolicyStorageUrl(),
      loanPolicyRequest, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    JsonResponse postResponse = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to create loan policy: %s", postResponse.getBody()),
      postResponse, matchesCreated());

    return new IndividualResource(postResponse);
  }

  /**
   * Assert that a POST of the loanPolicyRequest returns a 422 HTTP status code.
   * @return the body with the validation failure message
   */
  private JsonObject postLoanPolicyWith422(JsonObject loanPolicyRequest) {
    try {
      CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

      client.post(loanPolicyStorageUrl(),
          loanPolicyRequest, StorageTestSuite.TENANT_ID,
          ResponseHandler.json(createCompleted));
      JsonResponse postResponse = createCompleted.get(5, TimeUnit.SECONDS);

      assertThat(String.format("Expected validation failure when creating loan policy: request=%s, response=%s",
          loanPolicyRequest.encodePrettily(), postResponse.getBody()),
          postResponse, matchesUnprocessableEntity());

      return postResponse.getJson();
    } catch (MalformedURLException | InterruptedException | ExecutionException | TimeoutException e) {
      throw new RuntimeException(e.getMessage() + ": " + loanPolicyRequest.encodePrettily(), e);
    }
  }

  private JsonResponse getById(UUID id)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    URL getInstanceUrl = loanPolicyStorageUrl(String.format("/%s", id));

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();

    client.get(getInstanceUrl, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(getCompleted));

    return getCompleted.get(5, TimeUnit.SECONDS);
  }

  private boolean hasRecordWithId(List<JsonObject> records, String firstPolicyId) {
    return records.stream().anyMatch(item -> item.getString("id").equals(firstPolicyId));
  }

  private void checkRequestManagementSection(JsonObject representation) {
    assertThat(representation.containsKey("requestManagement"), is(true));
    JsonObject requestManagement = representation.getJsonObject("requestManagement");
    assertThat(requestManagement.containsKey("recalls"), is(true));
    assertThat(requestManagement.containsKey("holds"), is(true));
    assertThat(requestManagement.containsKey("pages"), is(true));
    JsonObject recalls = requestManagement.getJsonObject("recalls");
    assertThat(recalls.getJsonObject("alternateGracePeriod"), matchesPeriod(1, "Months"));
    assertThat(recalls.getJsonObject("minimumGuaranteedLoanPeriod"), matchesPeriod(1, "Weeks"));
    assertThat(recalls.getJsonObject("recallReturnInterval"), matchesPeriod(1, "Days"));
    JsonObject holds = requestManagement.getJsonObject("holds");
    assertThat(holds.getJsonObject("alternateCheckoutLoanPeriod"), matchesPeriod(2, "Months"));
    assertThat(holds.getBoolean("renewItemsWithRequest"), is(true));
    assertThat(holds.getJsonObject("alternateRenewalLoanPeriod"), matchesPeriod(2, "Days"));
    JsonObject pages = requestManagement.getJsonObject("pages");
    assertThat(pages.getJsonObject("alternateCheckoutLoanPeriod"), matchesPeriod(3, "Months"));
    assertThat(pages.getBoolean("renewItemsWithRequest"), is(true));
    assertThat(pages.getJsonObject("alternateRenewalLoanPeriod"), matchesPeriod(3, "Days"));
  }

  private IndividualResource createFixedDueDateSchedule(String name,
    DateTime from, DateTime to, DateTime dueDate) throws Exception {

    UUID fddId = UUID.randomUUID();
    JsonObject fdd = FixedDueDateApiTest
      .createFixedDueDate(fddId.toString(), name, "desc");

    JsonObject fddSchedule = FixedDueDateApiTest
      .createSchedule(from.toString(), to.toString(), dueDate.toString());

    fdd.put(FixedDueDateApiTest.SCHEDULE_SECTION, new JsonArray().add(fddSchedule));

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();
    client.post(FixedDueDateApiTest.dueDateURL(), fdd, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    JsonResponse response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to create due date: %s", response.getBody()),
      response, matchesCreated());

    return new IndividualResource(response);
  }

  private JsonObject getErrorFromResponse(JsonResponse response) {
    return response.getJson().getJsonArray("errors").getJsonObject(0);
  }
}

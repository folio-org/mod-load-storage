package org.folio.rest.support.builders;

import java.util.UUID;

import org.folio.rest.jaxrs.model.Period;

import io.vertx.core.json.JsonObject;

public class LoanPolicyRequestBuilder {
  private final UUID id;
  private final String name;
  private final String description;
  private final String profile;
  private final String fixedDueDateScheduleId;
  private final String alternateFixedDueDateScheduleId;
  private final Period holdsRenewalLoanPeriod;
  private final Period loanPeriod;
  private final Period renewalPeriod;

  private LoanPolicyRequestBuilder(
    UUID id, String name, String description, String profile,
    String fixedDueDateScheduleId,
    String alternateFixedDueDateScheduleId,
    Period holdsRenewalLoanPeriod,
    Period loanPeriod,
    Period renewalPeriod
  ) {
    this.id = id;
    this.name = name;
    this.description = description;
    this.profile = profile;
    this.fixedDueDateScheduleId = fixedDueDateScheduleId;
    this.alternateFixedDueDateScheduleId = alternateFixedDueDateScheduleId;
    this.holdsRenewalLoanPeriod = holdsRenewalLoanPeriod;
    this.loanPeriod = loanPeriod;
    this.renewalPeriod = renewalPeriod;
  }

  public JsonObject create() {
    JsonObject request = new JsonObject();

    if (id != null) {
      request.put("id", id.toString());
    }

    request.put("name", this.name);
    request.put("description", this.description);
    request.put("loanable", true);

    JsonObject loansPolicy = new JsonObject();

    loansPolicy.put("profileId", profile);
    if (loanPeriod != null) {
      loansPolicy.put("period", JsonObject.mapFrom(loanPeriod));
    }

    loansPolicy.put("closedLibraryDueDateManagementId", "CURRENT_DUE_DATE");
    loansPolicy.put("gracePeriod", createPeriod(7, "Days"));
    loansPolicy.put("openingTimeOffset", createPeriod(3, "Hours"));
    put(loansPolicy, "fixedDueDateScheduleId", fixedDueDateScheduleId);

    JsonObject requestManagement = new JsonObject();
    JsonObject recalls = new JsonObject();
    recalls.put("alternateGracePeriod", createPeriod(1, "Months"));
    recalls.put("minimumGuaranteedLoanPeriod", createPeriod(1, "Weeks"));
    recalls.put("recallReturnInterval", createPeriod(1, "Days"));
    if (holdsRenewalLoanPeriod != null) {
      JsonObject holds = new JsonObject();
      holds.put("alternateCheckoutLoanPeriod", createPeriod(2, "Months"));
      holds.put("renewItemsWithRequest", true);
      holds.put("alternateRenewalLoanPeriod", JsonObject.mapFrom(holdsRenewalLoanPeriod));

      requestManagement.put("holds", holds);
    }

    JsonObject pages = new JsonObject();
    pages.put("alternateCheckoutLoanPeriod", createPeriod(3, "Months"));
    pages.put("renewItemsWithRequest", true);
    pages.put("alternateRenewalLoanPeriod", createPeriod(3, "Days"));
    requestManagement.put("recalls", recalls);
    requestManagement.put("pages", pages);

    request.put("loansPolicy", loansPolicy);
    request.put("requestManagement", requestManagement);
    request.put("renewable",
      renewalPeriod != null || alternateFixedDueDateScheduleId != null);

    JsonObject renewalsPolicy = new JsonObject();

    renewalsPolicy.put("unlimited", true);
    renewalsPolicy.put("renewFromId", "CURRENT_DUE_DATE");
    renewalsPolicy.put("differentPeriod", true);
    put(renewalsPolicy, "alternateFixedDueDateScheduleId", alternateFixedDueDateScheduleId);

    if (renewalPeriod != null) {
        renewalsPolicy.put("period", JsonObject.mapFrom(renewalPeriod));
    }
    request.put("renewalsPolicy", renewalsPolicy);

    return request;
  }

  public LoanPolicyRequestBuilder withId(UUID id) {
    return new LoanPolicyRequestBuilder(id, this.name, this.description,
      this.profile, this.fixedDueDateScheduleId,
      this.alternateFixedDueDateScheduleId,
      this.holdsRenewalLoanPeriod,
      this.loanPeriod,
      this.renewalPeriod
    );
  }

  public LoanPolicyRequestBuilder withNoId() {
    return new LoanPolicyRequestBuilder(null, this.name, this.description,
      this.profile, this.fixedDueDateScheduleId,
      this.alternateFixedDueDateScheduleId,
      this.holdsRenewalLoanPeriod,
      this.loanPeriod,
      this.renewalPeriod
    );
  }

  public LoanPolicyRequestBuilder withName(String name) {
    return new LoanPolicyRequestBuilder(this.id, name, this.description,
      this.profile, this.fixedDueDateScheduleId,
      this.alternateFixedDueDateScheduleId,
      this.holdsRenewalLoanPeriod,
      this.loanPeriod,
      this.renewalPeriod
    );
  }

  public LoanPolicyRequestBuilder withDescription(String description) {
    return new LoanPolicyRequestBuilder(this.id, name, description,
      this.profile, this.fixedDueDateScheduleId,
      this.alternateFixedDueDateScheduleId,
      this.holdsRenewalLoanPeriod,
      this.loanPeriod,
      this.renewalPeriod
    );
  }

  public LoanPolicyRequestBuilder rolling(int duration, String intervalId,
    String fixedDueDateScheduleId) {

    return new LoanPolicyRequestBuilder(this.id, this.name, this.description,
      "Rolling", fixedDueDateScheduleId, this.alternateFixedDueDateScheduleId,
      this.holdsRenewalLoanPeriod,
      createPeriod(duration, intervalId).mapTo(Period.class), renewalPeriod);
  }

  public LoanPolicyRequestBuilder fixed(String fixedDueDateScheduleId) {
    return new LoanPolicyRequestBuilder(this.id, this.name, description,
      "Fixed", fixedDueDateScheduleId,
      this.alternateFixedDueDateScheduleId,
      this.holdsRenewalLoanPeriod,
      this.loanPeriod,
      this.renewalPeriod
    );
  }

  public LoanPolicyRequestBuilder withAlternateFixedDueDateScheduleId(
    String alternateFixedDueDateScheduleId) {
    return new LoanPolicyRequestBuilder(this.id, name, description,
      this.profile, this.fixedDueDateScheduleId,
      alternateFixedDueDateScheduleId,
      this.holdsRenewalLoanPeriod,
      this.loanPeriod,
      this.renewalPeriod
    );
  }

  public LoanPolicyRequestBuilder withHoldsRenewalLoanPeriod(Period holdsRenewalLoanPeriod) {
    return new LoanPolicyRequestBuilder(this.id, name, description,
      this.profile, this.fixedDueDateScheduleId,
      this.alternateFixedDueDateScheduleId,
      holdsRenewalLoanPeriod,
      this.loanPeriod,
      this.renewalPeriod
    );
  }

  public LoanPolicyRequestBuilder withLoanPeriod(Period loanPeriod) {
    return new LoanPolicyRequestBuilder(this.id, name, description,
      this.profile, this.fixedDueDateScheduleId,
      this.alternateFixedDueDateScheduleId,
      this.holdsRenewalLoanPeriod,
      loanPeriod,
      this.renewalPeriod
    );
  }

  public LoanPolicyRequestBuilder withRenewalPeriod(Period renewalPeriod) {
    return new LoanPolicyRequestBuilder(this.id, name, description,
      this.profile, this.fixedDueDateScheduleId,
      this.alternateFixedDueDateScheduleId,
      this.holdsRenewalLoanPeriod,
      this.loanPeriod,
      renewalPeriod
    );
  }

  public static LoanPolicyRequestBuilder defaultRollingPolicy() {
    return new LoanPolicyRequestBuilder(UUID.randomUUID(),
      "Example Loan Policy",
      "An example loan policy",
      "Rolling",
      null,
      null,
      createPeriod(2, "Days").mapTo(Period.class),
      createPeriod(1, "Months").mapTo(Period.class),
      createPeriod(30, "Days").mapTo(Period.class)
    );
  }

  public static LoanPolicyRequestBuilder emptyPolicy() {
    return new LoanPolicyRequestBuilder(UUID.randomUUID(),
      "Example Loan Policy",
      "An example loan policy",
      null,
      null,
      null,
      null,
      null,
      null
    );
  }

  private static JsonObject createPeriod(Integer duration, String intervalId) {
    JsonObject period = new JsonObject();

    period.put("duration", duration);
    period.put("intervalId", intervalId);

    return period;
  }
  private <T> void put(JsonObject object, String key, T value) {
    if (object != null && value != null) {
      object.put(key, value);
    }
  }
}

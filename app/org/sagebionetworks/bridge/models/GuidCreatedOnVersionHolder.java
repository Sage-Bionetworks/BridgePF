package org.sagebionetworks.bridge.models;

import static com.google.common.base.Preconditions.checkNotNull;

import org.sagebionetworks.bridge.json.DateTimeJsonSerializer;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.validators.Validate;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

public class GuidCreatedOnVersionHolder {

    private final String guid;
    private final long createdOn;
    private final Long version;
    
    public GuidCreatedOnVersionHolder(Survey survey) {
        checkNotNull(survey, Validate.CANNOT_BE_NULL, "survey");
        this.guid = survey.getGuid();
        this.createdOn = survey.getCreatedOn();
        this.version = survey.getVersion();
    }
    
    public String getGuid() {
        return guid;
    }

    @JsonSerialize(using = DateTimeJsonSerializer.class)
    public long getCreatedOn() {
        return createdOn;
    }
    
    public Long getVersion() {
        return version;
    }
    
}

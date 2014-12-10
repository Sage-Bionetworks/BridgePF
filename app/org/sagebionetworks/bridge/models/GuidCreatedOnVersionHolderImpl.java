package org.sagebionetworks.bridge.models;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import org.sagebionetworks.bridge.json.DateTimeJsonSerializer;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.validators.Validate;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

public class GuidCreatedOnVersionHolderImpl implements GuidCreatedOnVersionHolder {

    private final String guid;
    private final long createdOn;
    private final Long version;
    
    public GuidCreatedOnVersionHolderImpl(Survey survey) {
        checkNotNull(survey, Validate.CANNOT_BE_NULL, "survey");
        this.guid = survey.getGuid();
        this.createdOn = survey.getCreatedOn();
        this.version = survey.getVersion();
    }
    
    public GuidCreatedOnVersionHolderImpl(String guid, long createdOn) {
        checkArgument(isNotBlank(guid), Validate.CANNOT_BE_BLANK, "guid");
        checkArgument(createdOn != 0, "createdOn cannot be zero");
        this.guid = guid;
        this.createdOn = createdOn;
        this.version = null;
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

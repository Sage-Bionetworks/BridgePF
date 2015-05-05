package org.sagebionetworks.bridge.models;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.json.DateTimeJsonDeserializer;
import org.sagebionetworks.bridge.json.DateTimeJsonSerializer;
import org.sagebionetworks.bridge.models.schedules.SurveyReference;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.SurveyResponse;
import org.sagebionetworks.bridge.validators.Validate;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@BridgeTypeName("GuidCreatedOnVersionHolder")
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
    
    public GuidCreatedOnVersionHolderImpl(SurveyResponse response) {
        checkNotNull(response);
        this.guid = response.getSurveyGuid();
        this.createdOn = response.getSurveyCreatedOn();
        this.version = null;
    }

    public GuidCreatedOnVersionHolderImpl(SurveyReference reference) {
        checkNotNull(reference);
        this.guid = reference.getGuid();
        this.createdOn = (reference.getCreatedOn() == null) ? 0L : reference.getCreatedOn().getMillis();
        this.version = null;
    }
    
    @JsonCreator
    public GuidCreatedOnVersionHolderImpl(@JsonProperty("guid") String guid,
            @JsonProperty("createdOn") @JsonDeserialize(using = DateTimeJsonDeserializer.class) long createdOn) {
        checkArgument(isNotBlank(guid), Validate.CANNOT_BE_BLANK, "guid");
        checkArgument(createdOn != 0, "createdOn cannot be zero");
        this.guid = guid;
        this.createdOn = createdOn;
        this.version = null;
    }
    
    @Override
    public String getGuid() {
        return guid;
    }

    @Override
    @JsonSerialize(using = DateTimeJsonSerializer.class)
    public long getCreatedOn() {
        return createdOn;
    }
    
    @Override
    public Long getVersion() {
        return version;
    }

    @Override
    public boolean keysEqual(GuidCreatedOnVersionHolder keys) {
        return (keys != null && keys.getGuid().equals(guid) && keys.getCreatedOn() == createdOn);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (int) (createdOn ^ (createdOn >>> 32));
        result = prime * result + ((guid == null) ? 0 : guid.hashCode());
        result = prime * result + ((version == null) ? 0 : version.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        GuidCreatedOnVersionHolderImpl other = (GuidCreatedOnVersionHolderImpl) obj;
        if (createdOn != other.createdOn)
            return false;
        if (guid == null) {
            if (other.guid != null)
                return false;
        } else if (!guid.equals(other.guid))
            return false;
        if (version == null) {
            if (other.version != null)
                return false;
        } else if (!version.equals(other.version))
            return false;
        return true;
    }
    
}

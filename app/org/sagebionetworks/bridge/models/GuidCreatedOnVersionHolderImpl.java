package org.sagebionetworks.bridge.models;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.Objects;

import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.json.DateTimeToLongDeserializer;
import org.sagebionetworks.bridge.json.DateTimeToLongSerializer;
import org.sagebionetworks.bridge.models.schedules.SurveyReference;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.validators.Validate;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@BridgeTypeName("GuidCreatedOnVersionHolder")
public final class GuidCreatedOnVersionHolderImpl implements GuidCreatedOnVersionHolder {

    private final String guid;
    private final long createdOn;
    private final Long version;
    
    public GuidCreatedOnVersionHolderImpl(Survey survey) {
        checkNotNull(survey, Validate.CANNOT_BE_NULL, "survey");
        this.guid = survey.getGuid();
        this.createdOn = survey.getCreatedOn();
        this.version = survey.getVersion();
    }

    public GuidCreatedOnVersionHolderImpl(SurveyReference reference) {
        checkNotNull(reference);
        this.guid = reference.getGuid();
        this.createdOn = (reference.getCreatedOn() == null) ? 0L : reference.getCreatedOn().getMillis();
        this.version = null;
    }
    
    @JsonCreator
    public GuidCreatedOnVersionHolderImpl(@JsonProperty("guid") String guid,
            @JsonProperty("createdOn") @JsonDeserialize(using = DateTimeToLongDeserializer.class) long createdOn) {
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
    @JsonSerialize(using = DateTimeToLongSerializer.class)
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
        result = prime * result + Objects.hashCode(createdOn);
        result = prime * result + Objects.hashCode(guid);
        result = prime * result + Objects.hashCode(version);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        GuidCreatedOnVersionHolderImpl other = (GuidCreatedOnVersionHolderImpl) obj;
        return (Objects.equals(createdOn, other.createdOn) && 
                Objects.equals(guid, other.guid) && 
                Objects.equals(version, other.version));
    }
    
    @Override
    public String toString() {
        return String.format("GuidCreatedOnVersionHolderImpl [guid=%s, createdOn=%d, version=%d]", 
            guid, createdOn, version);
    }
}

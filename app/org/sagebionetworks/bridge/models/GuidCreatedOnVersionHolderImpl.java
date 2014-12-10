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

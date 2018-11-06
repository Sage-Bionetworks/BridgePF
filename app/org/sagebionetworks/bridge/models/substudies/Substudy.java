package org.sagebionetworks.bridge.models.substudies;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.hibernate.HibernateSubstudy;
import org.sagebionetworks.bridge.models.BridgeEntity;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(as=HibernateSubstudy.class)
public interface Substudy extends BridgeEntity {
    
    public static Substudy create() {
        return new HibernateSubstudy();
    }

    String getId();
    void setId(String id);
    
    String getStudyId();
    void setStudyId(String studyId);
    
    String getName();
    void setName(String name);
    
    boolean isDeleted();
    void setDeleted(boolean deleted);
    
    DateTime getCreatedOn();
    void setCreatedOn(DateTime createdOn);
    
    DateTime getModifiedOn();
    void setModifiedOn(DateTime modifiedOn);
    
    Long getVersion();
    void setVersion(Long version);
    
}

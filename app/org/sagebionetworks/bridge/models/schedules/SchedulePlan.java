package org.sagebionetworks.bridge.models.schedules;

import org.sagebionetworks.bridge.dynamodb.DynamoSchedulePlan;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@BridgeTypeName("SchedulePlan")
@JsonDeserialize(as = DynamoSchedulePlan.class)
public interface SchedulePlan extends BridgeEntity {
    /** Convenience method for creating an instance of SchedulePlan using a concrete implementation. */
    static SchedulePlan create() {
        return new DynamoSchedulePlan();
    }

    String getGuid();
    void setGuid(String guid);
    
    String getLabel();
    void setLabel(String label);
    
    String getStudyKey();
    void setStudyKey(String studyKey);
    
    long getModifiedOn();
    void setModifiedOn(long modifiedOn);
    
    boolean isDeleted();
    void setDeleted(boolean deleted);
    
    Long getVersion();
    void setVersion(Long version);
    
    ScheduleStrategy getStrategy();
    void setStrategy(ScheduleStrategy strategy);
}

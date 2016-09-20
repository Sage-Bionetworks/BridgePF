package org.sagebionetworks.bridge.models.activities;

import org.sagebionetworks.bridge.models.BridgeEntity;

public interface ActivityEvent extends BridgeEntity {

    String getHealthCode();
    void setHealthCode(String healthCode);
    
    String getEventId();
    void setEventId(String eventId);

    String getAnswerValue();
    void setAnswerValue(String answerValue);
    
    Long getTimestamp();
    void setTimestamp(Long timestamp);
    
}

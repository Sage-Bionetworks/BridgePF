package org.sagebionetworks.bridge.models.reports;

import org.sagebionetworks.bridge.dynamodb.DynamoReportIndex;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * <p>An index entry of an identifier used for a report in a study. It's possible to have the same identifier 
 * used for a study and a participant report, so the ReportIndex tables support this by including the type 
 * as well as the study in the record's hash key. </p>
 *
 * <p>Study index records can be deleted when study reports are deleted, but we can't do the same for 
 * participants because we don't know (without scanning) when the last participant is removed from a report 
 * under a specific identifier.</p>
 */
@BridgeTypeName("ReportIndex")
@JsonDeserialize(as=DynamoReportIndex.class)
public interface ReportIndex extends BridgeEntity {

    static ReportIndex create() {
        return new DynamoReportIndex();
    }
    
    @JsonIgnore
    String getKey();
    void setKey(String key);
    
    String getIdentifier();
    void setIdentifier(String identifier);
    
    boolean isPublic();
    void setPublic(boolean isPublic);
}

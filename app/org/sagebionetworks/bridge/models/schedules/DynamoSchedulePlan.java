package org.sagebionetworks.bridge.models.schedules;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * You will want to create subclasses out of the rows in dynamo, that will 
 * interpret the data (a JSON blob) differently and implement an algorythm
 * for making schedules.
 */
public abstract class DynamoSchedulePlan implements SchedulePlan {

    protected String studyKey;
    protected String type;
    protected long modifiedOn;
    protected ObjectNode data;
    
    public DynamoSchedulePlan() {
        this.data = JsonNodeFactory.instance.objectNode();
    }
    
    @Override
    public String getStudyKey() {
        return studyKey;
    }
    @Override
    public void setStudyKey(String studyKey) {
        this.studyKey = studyKey;
    }
    @Override
    public String getType() {
        return type;
    }
    @Override
    public void setType(String type) {
        this.type = type;
    }
    @Override
    public long getModifiedOn() {
        return modifiedOn;
    }
    @Override
    public void setModifiedOn(long modifiedOn) {
        this.modifiedOn = modifiedOn;
    }
    public ObjectNode getData() {
        return data;
    }
    public void setData(ObjectNode data) {
        this.data = data;
    }

}

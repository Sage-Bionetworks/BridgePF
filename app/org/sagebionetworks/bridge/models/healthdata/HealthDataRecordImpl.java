package org.sagebionetworks.bridge.models.healthdata;

import org.sagebionetworks.bridge.context.BridgeContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

public class HealthDataRecordImpl implements HealthDataRecord {

    private static final String RECORD_ID = "recordId";
    private static final String START_DATE = "startDate";
    private static final String END_DATE = "endDate";
    private static final String DATA = "data";

    private static final Logger logger = LoggerFactory.getLogger(BridgeContext.class);    

    protected String recordId;
    protected long startDate;
    protected long endDate;
    protected JsonNode data;
    
    public HealthDataRecordImpl() {
    }
    
    public HealthDataRecordImpl(String recordId, long date, JsonNode data) {
        this.recordId = recordId;
        this.startDate = date;
        this.endDate = date;
        this.data = data;
    }
    
    public HealthDataRecordImpl(String recordId, long startDate, long endDate, JsonNode data) {
        this.recordId = recordId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.data = data;
    }
    
    public static final HealthDataRecordImpl fromJson(JsonNode node) {
        String recordId = null;
        long startDate = 0L;
        long endDate = 0L;
        JsonNode data = null;
        
        if (node != null) {
            if (node.get(RECORD_ID) != null) {
                recordId = node.get(RECORD_ID).asText();
            }
            if (node.get(START_DATE) != null) {
                startDate = node.get(START_DATE).asLong();
            }
            if (node.get(END_DATE) != null) {
                endDate = node.get(END_DATE).asLong();
            }
            if (node.get(END_DATE) != null) {
                endDate = node.get(END_DATE).asLong();
            }
            if (node.get(DATA) != null) {
                data = node.get(DATA);
            }
        }
        return new HealthDataRecordImpl(recordId, startDate, endDate, data);
    }
    
    
    @Override
    public String getRecordId() { return recordId; }
    @Override
    public void setRecordId(String recordId) { this.recordId = recordId;}
    
    @Override
    public long getStartDate() { return startDate; }
    @Override
    public void setStartDate(long startDate) { this.startDate = startDate; }
    
    @Override
    public long getEndDate() { return endDate; }
    @Override
    public void setEndDate(long endDate) { this.endDate = endDate; }

    @Override
    public JsonNode getData() { return data; }
    @Override
    public void setData(JsonNode data) { this.data = data; }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((recordId == null) ? 0 : recordId.hashCode());
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
        HealthDataRecordImpl other = (HealthDataRecordImpl) obj;
        if (recordId == null) {
            if (other.recordId != null)
                return false;
        } else if (!recordId.equals(other.recordId))
            return false;
        return true;
    }
}

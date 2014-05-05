package org.sagebionetworks.bridge.healthdata;

import java.io.IOException;

import org.sagebionetworks.bridge.context.BridgeContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class HealthDataEntryImpl implements HealthDataEntry {

    private static final String ID = "id";
    private static final String START_DATE = "startDate";
    private static final String END_DATE = "endDate";
    private static final String PAYLOAD = "payload";

    private static final Logger logger = LoggerFactory.getLogger(BridgeContext.class);    

    protected String id;
    protected long startDate;
    protected long endDate;
    protected JsonNode payload;
    
    public HealthDataEntryImpl() {
    }
    
    public HealthDataEntryImpl(String id, long date, JsonNode payload) {
        this.id = id;
        this.startDate = date;
        this.endDate = date;
        this.payload = payload;
    }
    
    public HealthDataEntryImpl(String id, long startDate, long endDate, JsonNode payload) {
        this.id = id;
        this.startDate = startDate;
        this.endDate = endDate;
        this.payload = payload;
    }
    
    public static final HealthDataEntryImpl fromJson(JsonNode node) {
        String id = null;
        long startDate = 0L;
        long endDate = 0L;
        JsonNode payload = null;
        
        if (node != null) {
            if (node.get(ID) != null) {
                id = node.get(ID).asText();
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
            if (node.get(PAYLOAD) != null) {
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    payload = mapper.readTree(node.get(PAYLOAD).asText());
                } catch (JsonProcessingException e) {
                    logger.error("Error retrieving JSON from DynamoDB (corrupt?)", e);
                } catch (IOException e) {
                    logger.error("Error retrieving JSON from DynamoDB", e);
                }
            }
        }
        return new HealthDataEntryImpl(id, startDate, endDate, payload);
    }
    
    
    @Override
    public String getId() { return id; }
    @Override
    public void setId(String id) { this.id = id;}
    
    @Override
    public long getStartDate() { return startDate; }
    @Override
    public void setStartDate(long startDate) { this.startDate = startDate; }
    
    @Override
    public long getEndDate() { return endDate; }
    @Override
    public void setEndDate(long endDate) { this.endDate = endDate; }

    @Override
    public JsonNode getPayload() { return payload; }
    @Override
    public void setPayload(JsonNode payload) { this.payload = payload; }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (int) (endDate ^ (endDate >>> 32));
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        result = prime * result + (int) (startDate ^ (startDate >>> 32));
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
        HealthDataEntryImpl other = (HealthDataEntryImpl) obj;
        if (endDate != other.endDate)
            return false;
        if (id == null) {
            if (other.id != null)
                return false;
        } else if (!id.equals(other.id))
            return false;
        if (startDate != other.startDate)
            return false;
        return true;
    }
    
}

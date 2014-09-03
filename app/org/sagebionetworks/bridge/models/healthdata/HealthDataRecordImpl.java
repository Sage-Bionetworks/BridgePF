package org.sagebionetworks.bridge.models.healthdata;

import org.sagebionetworks.bridge.json.DateTimeJsonDeserializer;
import org.sagebionetworks.bridge.json.DateTimeJsonSerializer;
import org.sagebionetworks.bridge.models.DateConverter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

public class HealthDataRecordImpl implements HealthDataRecord {

    private static final String RECORD_ID = "recordId";
    private static final String START_DATE = "startDate";
    private static final String END_DATE = "endDate";
    private static final String VERSION = "version";
    private static final String DATA = "data";

    protected String recordId;
    protected long startDate;
    protected long endDate;
    protected Long version;
    protected JsonNode data;
    
    public HealthDataRecordImpl() {
    }

    public HealthDataRecordImpl(String recordId, long startDate, long endDate, Long version, JsonNode data) {
        this.recordId = recordId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.version = version;
        this.data = data;
    }

    public static final HealthDataRecordImpl fromJson(JsonNode node) {
        String recordId = null;
        long startDate = 0L;
        long endDate = 0L;
        long version = 0L;
        JsonNode data = null;

        if (node != null) {
            if (node.get(RECORD_ID) != null) {
                recordId = node.get(RECORD_ID).asText();
            }
            if (node.get(START_DATE) != null) {
                startDate = DateConverter.convertMillisFromEpoch(node.get(START_DATE).asText());
            }
            if (node.get(END_DATE) != null) {
                endDate = DateConverter.convertMillisFromEpoch(node.get(END_DATE).asText());
            }
            if (node.get(VERSION) != null) {
                version = node.get(VERSION).asLong();
            }
            if (node.get(DATA) != null) {
                data = node.get(DATA);
            }
        }
        return new HealthDataRecordImpl(recordId, startDate, endDate, version, data);
    }
    
    @Override
    public String getRecordId() { return recordId; }
    @Override
    public void setRecordId(String recordId) { this.recordId = recordId;}
    
    @Override
    @JsonSerialize(using = DateTimeJsonSerializer.class)
    public long getStartDate() { return startDate; }
    @Override
    @JsonDeserialize(using = DateTimeJsonDeserializer.class)
    public void setStartDate(long startDate) { this.startDate = startDate; }
    
    @Override
    @JsonSerialize(using = DateTimeJsonSerializer.class)
    public long getEndDate() { return endDate; }
    @Override
    @JsonDeserialize(using = DateTimeJsonDeserializer.class)
    public void setEndDate(long endDate) { this.endDate = endDate; }

    @Override
    public JsonNode getData() { return data; }
    @Override
    public void setData(JsonNode data) { this.data = data; }

    @Override
    public Long getVersion() { return version; }
    @Override
    public void setVersion(Long version) { this.version = version; }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((recordId == null) ? 0 : recordId.hashCode());
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
        HealthDataRecordImpl other = (HealthDataRecordImpl) obj;
        if (recordId == null) {
            if (other.recordId != null)
                return false;
        } else if (!recordId.equals(other.recordId))
            return false;
        if (version == null) {
            if (other.version != null)
                return false;
        } else if (!version.equals(other.version))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "HealthDataRecordImpl [recordId=" + recordId + ", startDate=" + startDate + ", endDate=" + endDate
                + ", version=" + version + ", data=" + data + "]";
    }
    
}

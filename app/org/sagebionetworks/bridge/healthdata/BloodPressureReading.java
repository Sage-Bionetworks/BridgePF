package org.sagebionetworks.bridge.healthdata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class BloodPressureReading extends HealthDataEntryImpl {

    public BloodPressureReading() {
        payload = new ObjectMapper().createObjectNode();
    }

    public BloodPressureReading(HealthDataEntry entry) {
        super(entry.getId(), entry.getStartDate(), entry.getPayload());
    }
    
    public int getSystolic() {
        JsonNode node = payload.get("systolic");
        return (node == null) ? 0 : node.asInt();
    }
    public void setSystolic(int systolic) {
        ((ObjectNode)payload).put("systolic", systolic);
    }
    public int getDiastolic() {
        JsonNode node = payload.get("diastolic");
        return (node == null) ? 0 : node.asInt();
    }
    public void setDiastolic(int diastolic) {
        ((ObjectNode)payload).put("diastolic", diastolic);
    }

    @Override
    public String toString() {
        return "BloodPressureReading [getSystolic()=" + getSystolic() + ", getDiastolic()=" + getDiastolic()
                + ", getId()=" + getId() + ", getStartDate()=" + getStartDate() + ", getEndDate()=" + getEndDate()
                + "]";
    }
    
}

package org.sagebionetworks.bridge.healthdata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class BloodPressureReading extends HealthDataEntryImpl {

    public BloodPressureReading() {
        data = new ObjectMapper().createObjectNode();
    }

    public BloodPressureReading(HealthDataEntry entry) {
        super(entry.getId(), entry.getStartDate(), entry.getData());
    }
    
    public int getSystolic() {
        JsonNode node = data.get("systolic");
        return (node == null) ? 0 : node.asInt();
    }
    public void setSystolic(int systolic) {
        ((ObjectNode)data).put("systolic", systolic);
    }
    public int getDiastolic() {
        JsonNode node = data.get("diastolic");
        return (node == null) ? 0 : node.asInt();
    }
    public void setDiastolic(int diastolic) {
        ((ObjectNode)data).put("diastolic", diastolic);
    }

    @Override
    public String toString() {
        return "BloodPressureReading [getSystolic()=" + getSystolic() + ", getDiastolic()=" + getDiastolic()
                + ", getId()=" + getId() + ", getStartDate()=" + getStartDate() + ", getEndDate()=" + getEndDate()
                + "]";
    }
    
}

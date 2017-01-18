package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;

import org.joda.time.DateTime;
import org.junit.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.OperatingSystem;
import org.sagebionetworks.bridge.models.notifications.NotificationRegistration;

import com.fasterxml.jackson.databind.JsonNode;

public class DynamoNotificationRegistrationTest {
    
    private static final String DEVICE_ID = "aDeviceId";
    private static final String ENDPOINT_ARN = "anEndpointARN";
    private static final String GUID = "ABC";
    private static final String HEALTH_CODE = "healthCode";
    private static final String CREATED_ON_STRING = "2017-01-10T20:29:14.319Z";
    private static final String MODIFIED_ON_STRING = "2017-01-11T20:29:14.319Z";
    private static final long CREATED_ON = DateTime.parse(CREATED_ON_STRING).getMillis();
    private static final long MODIFIED_ON = DateTime.parse(MODIFIED_ON_STRING).getMillis();
    
    @Test
    public void canSerialize() throws Exception {
        NotificationRegistration reg = NotificationRegistration.create();
        reg.setHealthCode(HEALTH_CODE);
        reg.setGuid(GUID);
        reg.setEndpointARN(ENDPOINT_ARN);
        reg.setDeviceId(DEVICE_ID);
        reg.setOsName(OperatingSystem.ANDROID);
        reg.setCreatedOn(CREATED_ON);
        reg.setModifiedOn(MODIFIED_ON);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(reg);
        assertEquals(GUID, node.get("guid").asText());
        assertEquals(DEVICE_ID, node.get("deviceId").asText());
        assertEquals(OperatingSystem.ANDROID, node.get("osName").asText());
        assertEquals(CREATED_ON_STRING, node.get("createdOn").asText());
        assertEquals(MODIFIED_ON_STRING, node.get("modifiedOn").asText());
        assertEquals("NotificationRegistration", node.get("type").asText());
        assertEquals(6, node.size()); // and no other fields like healthCode;
        
        // In creating a registration, the deviceId, osName and sometimes the  guid, so these must 
        // deserialize correctly. Other fields will be set on the server.
        String json = TestUtils.createJson("{'guid':'ABC','deviceId':'aDeviceId','osName':'iPhone OS'}");
        
        NotificationRegistration deser = BridgeObjectMapper.get().readValue(json, NotificationRegistration.class);
        assertEquals(GUID, deser.getGuid());
        assertEquals(DEVICE_ID, deser.getDeviceId());
        assertEquals(OperatingSystem.IOS, deser.getOsName());
    }
    
}

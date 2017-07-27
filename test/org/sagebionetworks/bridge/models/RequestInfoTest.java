package org.sagebionetworks.bridge.models;

import static org.junit.Assert.assertEquals;

import java.util.LinkedHashSet;
import java.util.Set;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Sets;

import nl.jqno.equalsverifier.EqualsVerifier;

public class RequestInfoTest {

    private static final StudyIdentifier STUDY_ID = new StudyIdentifierImpl("test-study");
    private static final String USER_ID = "userId";
    private static final ClientInfo CLIENT_INFO = ClientInfo.parseUserAgentString("app/20");
    private static final String USER_AGENT_STRING = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/53.0.2785.116 Safari/537.36";
    private static final LinkedHashSet<String> LANGUAGES = TestUtils.newLinkedHashSet("en", "fr");
    private static final DateTimeZone PST = DateTimeZone.forOffsetHours(-7);
    private static final DateTimeZone MST = DateTimeZone.forOffsetHours(3);
    private static final DateTime ACTIVITIES_REQUESTED_ON = DateUtils.getCurrentDateTime().withZone(PST);
    private static final DateTime SIGNED_IN_ON = ACTIVITIES_REQUESTED_ON.minusHours(4).withZone(PST);
    
    @Test
    public void hashCodeEquals() {
        EqualsVerifier.forClass(RequestInfo.class).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void canSerialize() throws Exception {
        RequestInfo requestInfo = new RequestInfo.Builder()
                .withStudyIdentifier(STUDY_ID)
                .withClientInfo(CLIENT_INFO)
                .withUserAgent(USER_AGENT_STRING)
                .withUserDataGroups(TestConstants.USER_DATA_GROUPS)
                .withLanguages(LANGUAGES)
                .withUserId(USER_ID)
                .withTimeZone(MST)
                .withActivitiesAccessedOn(ACTIVITIES_REQUESTED_ON)
                .withSignedInOn(SIGNED_IN_ON).build();
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(requestInfo);

        assertEquals("userId", node.get("userId").asText());
        assertEquals(ACTIVITIES_REQUESTED_ON.withZone(MST).toString(), node.get("activitiesAccessedOn").asText());
        assertEquals("en", node.get("languages").get(0).asText());
        assertEquals("fr", node.get("languages").get(1).asText());
        Set<String> groups = Sets.newHashSet(
            node.get("userDataGroups").get(0).asText(),
            node.get("userDataGroups").get(1).asText()
        );
        assertEquals(TestConstants.USER_DATA_GROUPS, groups);
        assertEquals(SIGNED_IN_ON.withZone(MST).toString(), node.get("signedInOn").asText());
        assertEquals("+03:00", node.get("timeZone").asText());
        assertEquals("RequestInfo", node.get("type").asText());
        assertEquals(USER_AGENT_STRING, node.get("userAgent").asText());
        assertEquals(10, node.size());
        
        JsonNode studyIdNode = node.get("studyIdentifier");
        assertEquals("test-study", studyIdNode.get("identifier").asText());
        assertEquals("StudyIdentifier", studyIdNode.get("type").asText());
        assertEquals(2, studyIdNode.size());
        
        JsonNode clientInfoNode = node.get("clientInfo");
        assertEquals("app", clientInfoNode.get("appName").asText());
        assertEquals(20, clientInfoNode.get("appVersion").asInt());
        
        RequestInfo deserClientInfo = BridgeObjectMapper.get().readValue(node.toString(), RequestInfo.class);
        assertEquals(requestInfo, deserClientInfo);
    }
    
    @Test
    public void ifNoTimeZoneUseUTC() {
        RequestInfo requestInfo = new RequestInfo.Builder()
                .withActivitiesAccessedOn(ACTIVITIES_REQUESTED_ON)
                .withSignedInOn(SIGNED_IN_ON)
                .withTimeZone(null) // this won't reset time zone, still going to use UTC
                .build();
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(requestInfo);
        
        assertEquals(ACTIVITIES_REQUESTED_ON.withZone(DateTimeZone.UTC).toString(),
                node.get("activitiesAccessedOn").asText());
        assertEquals(SIGNED_IN_ON.withZone(DateTimeZone.UTC).toString(), 
                node.get("signedInOn").asText());
    }
}

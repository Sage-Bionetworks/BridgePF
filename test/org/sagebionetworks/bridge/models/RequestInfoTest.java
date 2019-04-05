package org.sagebionetworks.bridge.models;

import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.Set;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.time.DateUtils;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;

import nl.jqno.equalsverifier.EqualsVerifier;

public class RequestInfoTest {

    private static final StudyIdentifier STUDY_ID = new StudyIdentifierImpl("test-study");
    private static final String USER_ID = "userId";
    private static final ClientInfo CLIENT_INFO = ClientInfo.parseUserAgentString("app/20");
    private static final String USER_AGENT_STRING = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/53.0.2785.116 Safari/537.36";
    private static final List<String> LANGUAGES = ImmutableList.of("en", "fr");
    private static final DateTimeZone PST = DateTimeZone.forOffsetHours(-7);
    private static final DateTimeZone MST = DateTimeZone.forOffsetHours(3);
    private static final DateTime ACTIVITIES_REQUESTED_ON = DateUtils.getCurrentDateTime().withZone(PST);
    private static final DateTime SIGNED_IN_ON = ACTIVITIES_REQUESTED_ON.minusHours(4).withZone(PST);
    private static final DateTime UPLOADED_ON = ACTIVITIES_REQUESTED_ON.minusHours(3).withZone(PST);
    
    @Test
    public void hashCodeEquals() {
        EqualsVerifier.forClass(RequestInfo.class).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void canSerialize() throws Exception {
        RequestInfo requestInfo = createRequestInfo();
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(requestInfo);

        assertEquals("userId", node.get("userId").textValue());
        assertEquals(ACTIVITIES_REQUESTED_ON.withZone(MST).toString(), node.get("activitiesAccessedOn").textValue());
        assertEquals(UPLOADED_ON.withZone(MST).toString(), node.get("uploadedOn").textValue());
        assertEquals("en", node.get("languages").get(0).textValue());
        assertEquals("fr", node.get("languages").get(1).textValue());
        Set<String> groups = Sets.newHashSet(
            node.get("userDataGroups").get(0).textValue(),
            node.get("userDataGroups").get(1).textValue()
        );
        assertEquals(TestConstants.USER_DATA_GROUPS, groups);
        
        Set<String> substudyIds = Sets.newHashSet(
            node.get("userSubstudyIds").get(0).textValue(),
            node.get("userSubstudyIds").get(1).textValue()
        );
        assertEquals(TestConstants.USER_SUBSTUDY_IDS, substudyIds);
        assertEquals(SIGNED_IN_ON.withZone(MST).toString(), node.get("signedInOn").textValue());
        assertEquals("+03:00", node.get("timeZone").textValue());
        assertEquals("RequestInfo", node.get("type").textValue());
        assertEquals(USER_AGENT_STRING, node.get("userAgent").textValue());
        assertEquals(12, node.size());
        
        JsonNode studyIdNode = node.get("studyIdentifier");
        assertEquals("test-study", studyIdNode.get("identifier").textValue());
        assertEquals("StudyIdentifier", studyIdNode.get("type").textValue());
        assertEquals(2, studyIdNode.size());
        
        JsonNode clientInfoNode = node.get("clientInfo");
        assertEquals("app", clientInfoNode.get("appName").textValue());
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
                node.get("activitiesAccessedOn").textValue());
        assertEquals(SIGNED_IN_ON.withZone(DateTimeZone.UTC).toString(), 
                node.get("signedInOn").textValue());
    }
    
    @Test
    public void copyOf() {
        RequestInfo requestInfo = createRequestInfo();
        
        RequestInfo copy = new RequestInfo.Builder().copyOf(requestInfo).build();
        assertEquals(STUDY_ID, copy.getStudyIdentifier());
        assertEquals(CLIENT_INFO, copy.getClientInfo());
        assertEquals(USER_AGENT_STRING, copy.getUserAgent());
        assertEquals(TestConstants.USER_DATA_GROUPS, copy.getUserDataGroups());
        assertEquals(TestConstants.USER_SUBSTUDY_IDS, copy.getUserSubstudyIds());
        assertEquals(LANGUAGES, copy.getLanguages());
        assertEquals(USER_ID, copy.getUserId());
        assertEquals(MST, copy.getTimeZone());
        assertEquals(ACTIVITIES_REQUESTED_ON.withZone(copy.getTimeZone()), copy.getActivitiesAccessedOn());
        assertEquals(UPLOADED_ON.withZone(copy.getTimeZone()), copy.getUploadedOn());
        assertEquals(SIGNED_IN_ON.withZone(copy.getTimeZone()), copy.getSignedInOn());
    }

    private RequestInfo createRequestInfo() {
        RequestInfo requestInfo = new RequestInfo.Builder()
                .withStudyIdentifier(STUDY_ID)
                .withClientInfo(CLIENT_INFO)
                .withUserAgent(USER_AGENT_STRING)
                .withUserDataGroups(TestConstants.USER_DATA_GROUPS)
                .withUserSubstudyIds(TestConstants.USER_SUBSTUDY_IDS)
                .withLanguages(LANGUAGES)
                .withUserId(USER_ID)
                .withTimeZone(MST)
                .withActivitiesAccessedOn(ACTIVITIES_REQUESTED_ON)
                .withUploadedOn(UPLOADED_ON)
                .withSignedInOn(SIGNED_IN_ON).build();
        return requestInfo;
    }
    
}

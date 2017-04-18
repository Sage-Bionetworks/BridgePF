package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;

import com.fasterxml.jackson.databind.JsonNode;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

import org.joda.time.DateTime;
import org.junit.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.surveys.DataType;
import org.sagebionetworks.bridge.models.surveys.DateConstraints;
import org.sagebionetworks.bridge.models.surveys.DateTimeConstraints;
import org.sagebionetworks.bridge.models.surveys.Image;
import org.sagebionetworks.bridge.models.surveys.IntegerConstraints;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.SurveyElement;
import org.sagebionetworks.bridge.models.surveys.SurveyRule;
import org.sagebionetworks.bridge.models.surveys.TestSurvey;

@SuppressWarnings("unchecked")
public class DynamoSurveyTest {
    private static final String MODULE_ID = "test-survey-module";
    private static final int MODULE_VERSION = 3;
    private static final long TEST_CREATED_ON_MILLIS = DateTime.parse("2015-05-22T18:34-0700").getMillis();
    private static final long TEST_MODIFIED_ON_MILLIS = DateTime.parse("2015-05-22T18:57-0700").getMillis();

    /**
     * Tests of the serialization and deserialization of all the data
     * to/from JSON. This is complicated for surveys as we change their
     * representation from the public API to the way they are stored in
     * Dynamo.
     */
    @Test
    public void jsonSerialization() throws Exception {
        Survey survey = makeTestSurvey();

        // add an info screen for completeness
        DynamoSurveyInfoScreen screen = new DynamoSurveyInfoScreen();
        screen.setGuid("test-info-screen-guid");
        screen.setIdentifier("screenA");
        screen.setTitle("The title of the screen");
        screen.setPrompt("This is the prompt");
        screen.setPromptDetail("This is further explanation of the prompt.");
        screen.setImage(new Image("http://foo.bar", 100, 100));
        survey.getElements().add(screen);

        // Convert to JSON.
        JsonNode jsonNode = BridgeObjectMapper.get().convertValue(survey, JsonNode.class);

        // Convert JSON to map to validate JSON. Note that study ID is intentionally omitted, but type is added.
        assertEquals(13, jsonNode.size());
        assertEquals("test-survey-guid", jsonNode.get("guid").textValue());
        assertEquals(2, jsonNode.get("version").intValue());
        assertEquals(MODULE_ID, jsonNode.get("moduleId").textValue());
        assertEquals(MODULE_VERSION, jsonNode.get("moduleVersion").intValue());
        assertEquals(survey.getName(), jsonNode.get("name").textValue());
        assertEquals(survey.getIdentifier(), jsonNode.get("identifier").textValue());
        assertTrue(jsonNode.get("published").booleanValue());
        assertTrue(jsonNode.get("deleted").booleanValue());
        assertEquals(42, jsonNode.get("schemaRevision").intValue());
        assertEquals("Survey", jsonNode.get("type").textValue());
        

        // Timestamps are stored as long, but serialized as ISO timestamps. Convert them back to long millis so we
        // don't have to deal with timezones and formatting issues.
        assertEquals(TEST_CREATED_ON_MILLIS, DateTime.parse(jsonNode.get("createdOn").textValue()).getMillis());
        assertEquals(TEST_MODIFIED_ON_MILLIS, DateTime.parse(jsonNode.get("modifiedOn").textValue()).getMillis());

        // Just test that we have the right number of elements. In-depth serialization testing is done by
        // SurveyElementTest
        JsonNode jsonElementList = jsonNode.get("elements");
        assertEquals(10, jsonElementList.size());

        // Convert back to POJO and validate. Note that study ID is still missing, since it was removed from the JSON.
        Survey convertedSurvey = BridgeObjectMapper.get().convertValue(jsonNode, Survey.class);
        assertNull(convertedSurvey.getStudyIdentifier());
        assertEquals("test-survey-guid", convertedSurvey.getGuid());
        assertEquals(TEST_CREATED_ON_MILLIS, convertedSurvey.getCreatedOn());
        assertEquals(TEST_MODIFIED_ON_MILLIS, convertedSurvey.getModifiedOn());
        assertEquals(MODULE_ID, convertedSurvey.getModuleId());
        assertEquals(MODULE_VERSION, convertedSurvey.getModuleVersion().intValue());
        assertEquals(2, convertedSurvey.getVersion().longValue());
        assertEquals(survey.getName(), convertedSurvey.getName());
        assertEquals(survey.getIdentifier(), convertedSurvey.getIdentifier());
        assertTrue(convertedSurvey.isPublished());
        assertEquals(42, convertedSurvey.getSchemaRevision().longValue());
        assertEquals(10, convertedSurvey.getElements().size());
        for (int i = 0; i < 10; i++) {
            assertEqualsSurveyElement(survey.getElements().get(i), convertedSurvey.getElements().get(i));
        }

        // There are 10 survey elements, but only the first 9 are questions.
        assertEquals(9, convertedSurvey.getUnmodifiableQuestionList().size());
        for (int i = 0; i < 9; i++) {
            assertEqualsSurveyElement(convertedSurvey.getElements().get(i),
                    convertedSurvey.getUnmodifiableQuestionList().get(i));
        }

        // validate that date constraints are persisted
        DateConstraints dc = (DateConstraints)TestSurvey.selectBy(convertedSurvey, DataType.DATE).getConstraints();
        assertNotNull("Earliest date exists", dc.getEarliestValue());
        assertNotNull("Latest date exists", dc.getLatestValue());

        DateTimeConstraints dtc = (DateTimeConstraints) TestSurvey.selectBy(convertedSurvey, DataType.DATETIME).getConstraints();
        assertNotNull("Earliest date exists", dtc.getEarliestValue());
        assertNotNull("Latest date exists", dtc.getLatestValue());
        
        IntegerConstraints ic = (IntegerConstraints) TestSurvey.selectBy(convertedSurvey, DataType.INTEGER).getConstraints();
        assertEquals(SurveyRule.Operator.LE, ic.getRules().get(0).getOperator());
        assertEquals(2, ic.getRules().get(0).getValue());
        assertEquals("name", ic.getRules().get(0).getSkipToTarget());
        
        assertEquals(SurveyRule.Operator.DE, ic.getRules().get(1).getOperator());
        assertEquals("name", ic.getRules().get(1).getSkipToTarget());
    }

    @Test
    public void copyConstructor() {
        // copy
        DynamoSurvey survey = makeTestSurvey();
        Survey copy = new DynamoSurvey(survey);

        // validate
        assertEquals(TEST_STUDY_IDENTIFIER, copy.getStudyIdentifier());
        assertEquals("test-survey-guid", copy.getGuid());
        assertEquals(TEST_CREATED_ON_MILLIS, copy.getCreatedOn());
        assertEquals(TEST_MODIFIED_ON_MILLIS, copy.getModifiedOn());
        assertEquals(MODULE_ID, copy.getModuleId());
        assertEquals(MODULE_VERSION, copy.getModuleVersion().intValue());
        assertEquals(2, copy.getVersion().longValue());
        assertEquals(survey.getName(), copy.getName());
        assertEquals(survey.getIdentifier(), copy.getIdentifier());
        assertTrue(copy.isPublished());
        assertEquals(42, copy.getSchemaRevision().longValue());
        assertEquals(9, copy.getElements().size());
        for (int i = 0; i < 9; i++) {
            assertEqualsSurveyElement(survey.getElements().get(i), copy.getElements().get(i));
        }
    }
    
    @Test
    public void equalsVerifier() {
        EqualsVerifier.forClass(DynamoSurvey.class).suppress(Warning.NONFINAL_FIELDS).allFieldsShouldBeUsed().verify();
    }

    @Test
    public void testToString() {
        Survey survey = makeTestSurvey();
        String surveyString = survey.toString();
        assertTrue(surveyString.contains("createdOn=" + TEST_CREATED_ON_MILLIS));
        assertTrue(surveyString.contains("guid=test-survey-guid"));
        assertTrue(surveyString.contains("moduleId=" + MODULE_ID));
        assertTrue(surveyString.contains("moduleVersion=" + MODULE_VERSION));
    }

    private static void assertEqualsSurveyElement(SurveyElement expected, SurveyElement actual) {
        // Test Survey has anonymous subclasses, so we can't use .equals(). SurveyElementTest already tests survey
        // elements, so here, just make sure they both derive from the same class (SurveyInfoScreen vs SurveyQuestion)
        // and they have the same ID.
        assertTrue((expected instanceof DynamoSurveyQuestion && actual instanceof DynamoSurveyQuestion)
                || (expected instanceof DynamoSurveyInfoScreen && actual instanceof DynamoSurveyInfoScreen));
        assertEquals(expected.getIdentifier(), actual.getIdentifier());
    }

    private static DynamoSurvey makeTestSurvey() {
        // Make survey. Modify a few fields to make testing easier.
        DynamoSurvey survey = new TestSurvey(DynamoSurveyTest.class, false);
        survey.setGuid("test-survey-guid");
        survey.setCreatedOn(TEST_CREATED_ON_MILLIS);
        survey.setModifiedOn(TEST_MODIFIED_ON_MILLIS);
        survey.setModuleId(MODULE_ID);
        survey.setModuleVersion(MODULE_VERSION);
        survey.setPublished(true);
        survey.setDeleted(true);
        return survey;
    }
}

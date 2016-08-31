package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.junit.BeforeClass;
import org.junit.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dao.UploadSchemaDao;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolderImpl;
import org.sagebionetworks.bridge.models.surveys.Survey;

public class DynamoSurveyDaoMockTest {
    private static final DateTime MOCK_NOW = DateTime.parse("2016-08-24T15:23:57.123-0700");
    private static final long MOCK_NOW_MILLIS = MOCK_NOW.getMillis();

    @BeforeClass
    public static void mockNow() {
        DateTimeUtils.setCurrentMillisFixed(MOCK_NOW_MILLIS);
    }

    @Test
    public void publishSurvey() {
        // test inputs and outputs
        GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl("test-guid", 1337);
        Survey survey = new DynamoSurvey();

        DynamoUploadSchema schema = new DynamoUploadSchema();
        schema.setRevision(42);

        // mock survey mapper
        DynamoDBMapper mockSurveyMapper = mock(DynamoDBMapper.class);

        // mock schema dao
        UploadSchemaDao mockSchemaDao = mock(UploadSchemaDao.class);
        when(mockSchemaDao.createUploadSchemaFromSurvey(TestConstants.TEST_STUDY, survey, true)).thenReturn(schema);

        // Set up DAO for test.
        DynamoSurveyDao surveyDao = spy(new DynamoSurveyDao());
        surveyDao.setSurveyMapper(mockSurveyMapper);
        surveyDao.setUploadSchemaDao(mockSchemaDao);

        // spy getSurvey() - There's a lot of complex logic in that query builder that's irrelevant to what we're
        // trying to test. Rather than over-specify our test and make our tests overly complicated, we'll just spy out
        // getSurvey().
        doReturn(survey).when(surveyDao).getSurvey(keys);

        // execute and validate
        Survey retval = surveyDao.publishSurvey(TestConstants.TEST_STUDY, keys, true);
        assertTrue(retval.isPublished());
        assertEquals(MOCK_NOW_MILLIS, retval.getModifiedOn());
        assertEquals(42, retval.getSchemaRevision().intValue());

        verify(mockSurveyMapper).save(same(retval));
    }
}

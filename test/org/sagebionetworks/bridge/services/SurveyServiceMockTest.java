package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dao.SurveyDao;
import org.sagebionetworks.bridge.dynamodb.DynamoSurvey;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolderImpl;
import org.sagebionetworks.bridge.models.surveys.Survey;

public class SurveyServiceMockTest {
    @Test
    public void publishSurvey() {
        // test inputs and outputs
        GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl("test-guid", 1337);
        Survey survey = new DynamoSurvey();

        // mock DAO
        SurveyDao mockDao = mock(SurveyDao.class);
        when(mockDao.publishSurvey(TestConstants.TEST_STUDY, keys, true)).thenReturn(survey);

        // set up test
        SurveyService svc = new SurveyService();
        svc.setSurveyDao(mockDao);

        // execute and validate
        Survey retval = svc.publishSurvey(TestConstants.TEST_STUDY, keys, true);
        assertSame(survey, retval);
    }
}

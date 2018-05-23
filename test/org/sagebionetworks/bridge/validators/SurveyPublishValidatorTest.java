package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;

import java.util.List;

import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Test;

import org.sagebionetworks.bridge.models.surveys.MultiValueConstraints;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestion;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestionOption;
import org.sagebionetworks.bridge.models.surveys.TestSurvey;

/**
 * Created by jyliu on 5/19/2017.
 */
public class SurveyPublishValidatorTest {

    private Survey survey;

    private SurveyPublishValidator validator;

    @Before
    public void before() {
        survey = new TestSurvey(SurveySaveValidatorTest.class, true);
        // because this is set by the service before validation
        survey.setGuid("AAA");
        validator = new SurveyPublishValidator();
    }

    @Test
    public void supports() throws Exception {
        validator.supports(survey.getClass());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void multiValueWithNoEnumeration() {
        List<SurveyQuestionOption>[] testCases = new List[] { null, ImmutableList.of() };

        for (List<SurveyQuestionOption> oneTestCase : testCases) {
            SurveyQuestion question = ((TestSurvey) survey).getMultiValueQuestion();
            ((MultiValueConstraints) question.getConstraints()).setEnumeration(oneTestCase);

            assertValidatorMessage(validator, survey, "elements[7].constraints.enumeration",
                    "must have non-null, non-empty choices list");
        }
    }
    
    @Test
    public void validateGuidsNotDuplicates() {
        survey = new TestSurvey(SurveySaveValidatorTest.class, false);
        survey.getElements().get(0).setGuid(survey.getElements().get(1).getGuid());
        
        assertValidatorMessage(validator, survey, "elements[1].guid", "exists in an earlier survey element");
    }
    
    @Test
    public void validateIdentifiersNotDuplicates() {
        survey = new TestSurvey(SurveySaveValidatorTest.class, false);
        survey.getElements().get(0).setIdentifier(survey.getElements().get(1).getIdentifier());
        
        assertValidatorMessage(validator, survey, "elements[1].identifier", "exists in an earlier survey element");
    }

}
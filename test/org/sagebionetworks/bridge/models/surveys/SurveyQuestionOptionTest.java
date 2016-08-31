package org.sagebionetworks.bridge.models.surveys;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

public class SurveyQuestionOptionTest {
    private static final Image DUMMY_IMAGE = new Image("dummy-source", 42, 42);

    @Test
    public void allValues() {
        SurveyQuestionOption option = new SurveyQuestionOption("test-label", "test-detail", "test-value", DUMMY_IMAGE);
        assertEquals("test-label", option.getLabel());
        assertEquals("test-detail", option.getDetail());
        assertEquals("test-value", option.getValue());
        assertEquals(DUMMY_IMAGE, option.getImage());

        String optionString = option.toString();
        assertTrue(optionString.contains(option.getLabel()));
        assertTrue(optionString.contains(option.getDetail()));
        assertTrue(optionString.contains(option.getValue()));
        assertTrue(optionString.contains(option.getImage().toString()));
    }

    @Test
    public void blankValue() {
        String[] testCaseArr = { null, "", "   " };
        for (String oneTestCase : testCaseArr) {
            SurveyQuestionOption option = new SurveyQuestionOption("test-label", null, oneTestCase, null);
            assertEquals("test-label", option.getValue());
        }
    }

    @Test
    public void equalsVerifier() {
        EqualsVerifier.forClass(SurveyQuestionOption.class).allFieldsShouldBeUsed().verify();
    }

    @Test
    public void toStringAllNulls() {
        // Make sure toString() doesn't throw if all fields are null.
        SurveyQuestionOption option = new SurveyQuestionOption(null, null, null, null);
        assertNotNull(option.toString());
    }
}

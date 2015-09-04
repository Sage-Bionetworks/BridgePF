package org.sagebionetworks.bridge.validators;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.dynamodb.DynamoSurveyInfoScreen;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.surveys.Image;
import org.sagebionetworks.bridge.models.surveys.MultiValueConstraints;
import org.sagebionetworks.bridge.models.surveys.StringConstraints;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.SurveyElement;
import org.sagebionetworks.bridge.models.surveys.SurveyInfoScreen;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestion;
import org.sagebionetworks.bridge.models.surveys.TestSurvey;
import org.sagebionetworks.bridge.models.surveys.UIHint;

public class SurveyValidatorTest {

    private Survey survey;

    private SurveyValidator validator;

    @Before
    public void before() {
        survey = new TestSurvey(true);
        // because this is set by the service before validation
        survey.setGuid("AAA");
        validator = new SurveyValidator();
    }

    private String errorFor(InvalidEntityException e, String field) {
        List<String> errors = e.getErrors().get(field);
        assertNotNull(errors);
        assertEquals(1, errors.size());
        return errors.get(0);
    }

    private SurveyInfoScreen createSurveyInfoScreen() {
        SurveyInfoScreen screen = new DynamoSurveyInfoScreen();
        screen.setTitle("title");
        screen.setPrompt("prompt");
        screen.setPromptDetail("prompt detail");
        screen.setIdentifier("screen-identifier");
        return screen;
    }

    private SurveyElement last(Survey survey) {
        return survey.getElements().get(survey.getElements().size() - 1);
    }

    @Test
    public void newTestSurveyIsValid() {
        Validate.entityThrowingException(validator, survey);
    }

    @Test
    public void nameRequired() {
        try {
            survey.setName("");
            Validate.entityThrowingException(validator, survey);
            fail("Should have thrown exception");
        } catch (InvalidEntityException e) {
            assertEquals("name is required", errorFor(e, "name"));
        }
    }

    @Test
    public void identifierRequired() {
        try {
            survey.setIdentifier(" ");
            Validate.entityThrowingException(validator, survey);
            fail("Should have thrown exception");
        } catch (InvalidEntityException e) {
            assertEquals("identifier is required", errorFor(e, "identifier"));
        }
    }

    @Test
    public void studyIdentifierRequired() {
        try {
            survey.setStudyIdentifier("");
            Validate.entityThrowingException(validator, survey);
            fail("Should have thrown exception");
        } catch (InvalidEntityException e) {
            assertEquals("studyIdentifier is required", errorFor(e, "studyIdentifier"));
        }
    }

    @Test
    public void guidRequired() {
        try {
            survey.setGuid(null);
            Validate.entityThrowingException(validator, survey);
            fail("Should have thrown exception");
        } catch (InvalidEntityException e) {
            assertEquals("guid is required", errorFor(e, "guid"));
        }
    }

    @Test
    public void preventsDuplicateElementIdentfiers() {
        try {
            survey = new TestSurvey(true);
            String identifier = survey.getElements().get(0).getIdentifier();
            survey.getElements().get(1).setIdentifier(identifier);
            Validate.entityThrowingException(validator, survey);
            fail("Should have thrown exception");
        } catch (InvalidEntityException e) {
            assertEquals("element1.identifier exists in an earlier survey element", errorFor(e, "element1.identifier"));
        }
    }

    @Test
    public void infoScreenIdentifierRequired() {
        try {
            survey = new TestSurvey(true);
            survey.getElements().add(createSurveyInfoScreen());
            SurveyInfoScreen screen = (SurveyInfoScreen) last(survey);
            screen.setIdentifier("");

            Validate.entityThrowingException(validator, survey);
            fail("Should have thrown exception");
        } catch (InvalidEntityException e) {
            assertEquals("element9.identifier is required", errorFor(e, "element9.identifier"));
        }
    }

    @Test
    public void infoScreenTitleRequired() {
        try {
            survey = new TestSurvey(true);
            survey.getElements().add(createSurveyInfoScreen());
            SurveyInfoScreen screen = (SurveyInfoScreen) last(survey);
            screen.setTitle("");

            Validate.entityThrowingException(validator, survey);
            fail("Should have thrown exception");
        } catch (InvalidEntityException e) {
            assertEquals("element9.title is required", errorFor(e, "element9.title"));
        }
    }

    @Test
    public void infoScreenPromptRequired() {
        try {
            survey = new TestSurvey(true);
            survey.getElements().add(createSurveyInfoScreen());
            SurveyInfoScreen screen = (SurveyInfoScreen) last(survey);
            screen.setPrompt("");

            Validate.entityThrowingException(validator, survey);
            fail("Should have thrown exception");
        } catch (InvalidEntityException e) {
            assertEquals("element9.prompt is required", errorFor(e, "element9.prompt"));
        }
    }

    @Test
    public void ifPresentAllFieldsOfSurveyScreenImageRequired() {
        try {
            survey = new TestSurvey(false);
            survey.getElements().add(createSurveyInfoScreen());
            SurveyInfoScreen screen = (SurveyInfoScreen) last(survey);
            screen.setImage(new Image("", 0, 0));

            Validate.entityThrowingException(validator, survey);
            fail("Should have thrown exception");
        } catch (InvalidEntityException e) {
            assertEquals("element9.image.width is required", errorFor(e, "element9.image.width"));
            assertEquals("element9.image.height is required", errorFor(e, "element9.image.height"));
            assertEquals("element9.image.source is required", errorFor(e, "element9.image.source"));
        }
    }

    @Test
    public void questionIdentifierRequired() {
        try {
            survey = new TestSurvey(false);
            survey.getElements().get(0).setIdentifier("");

            Validate.entityThrowingException(validator, survey);
            fail("Should have thrown exception");
        } catch (InvalidEntityException e) {
            assertEquals("element0.identifier is required", errorFor(e, "element0.identifier"));
        }
    }

    @Test
    public void questionUiHintRequired() {
        try {
            survey = new TestSurvey(false);
            survey.getUnmodifiableQuestionList().get(0).setUiHint(null);

            Validate.entityThrowingException(validator, survey);
            fail("Should have thrown exception");
        } catch (InvalidEntityException e) {
            assertEquals("element0.uiHint is required", errorFor(e, "element0.uiHint"));
        }
    }

    @Test
    public void questionPromptRequired() {
        try {
            survey = new TestSurvey(false);
            survey.getUnmodifiableQuestionList().get(0).setPrompt("");

            Validate.entityThrowingException(validator, survey);
            fail("Should have thrown exception");
        } catch (InvalidEntityException e) {
            assertEquals("element0.prompt is required", errorFor(e, "element0.prompt"));
        }
    }

    @Test
    public void questionConstraintsRequired() {
        try {
            survey = new TestSurvey(false);
            survey.getUnmodifiableQuestionList().get(0).setConstraints(null);

            Validate.entityThrowingException(validator, survey);
            fail("Should have thrown exception");
        } catch (InvalidEntityException e) {
            assertEquals("element0.constraints is required", errorFor(e, "element0.constraints"));
        }
    }

    // Constraints are largely optional, but each constraint must have a data type.
    // In addition, there are some known combinations of UI hints and constraints that make
    // no sense, e.g. allowMultiple with radio buttons.

    @Test
    public void constraintDataTypeRequired() {
        try {
            survey = new TestSurvey(false);
            survey.getUnmodifiableQuestionList().get(0).getConstraints().setDataType(null);

            Validate.entityThrowingException(validator, survey);
            fail("Should have thrown exception");
        } catch (InvalidEntityException e) {
            assertEquals("element0.constraints.dataType is required", errorFor(e, "element0.constraints.dataType"));
        }
    }

    @Test
    public void uiHintMustBeSupportedByConstraintsType() {
        try {
            survey = new TestSurvey(false);
            // Boolean constraints do not jive with lists (which are normally for select multiple)
            survey.getUnmodifiableQuestionList().get(0).setUiHint(UIHint.LIST);

            Validate.entityThrowingException(validator, survey);
            fail("Should have thrown exception");
        } catch (InvalidEntityException e) {
            assertEquals("data type 'boolean' doesn't match the UI hint of 'list'",
                            errorFor(e, "element0.constraints.dataType"));
        }
    }

    @Test
    public void multiValueWithComboboxLimitsConstraints() {
        try {
            survey = new TestSurvey(false);
            SurveyQuestion question = ((TestSurvey) survey).getMultiValueQuestion();
            question.setUiHint(UIHint.COMBOBOX);
            ((MultiValueConstraints) question.getConstraints()).setAllowMultiple(true);

            Validate.entityThrowingException(validator, survey);
            fail("Should have thrown exception");
        } catch (InvalidEntityException e) {
            assertEquals("'combobox' is only valid when multiple = false and other = true",
                            errorFor(e, "element7.constraints.uiHint"));
        }
        try {
            survey = new TestSurvey(false);
            SurveyQuestion question = ((TestSurvey) survey).getMultiValueQuestion();
            question.setUiHint(UIHint.COMBOBOX);
            ((MultiValueConstraints) question.getConstraints()).setAllowOther(false);

            Validate.entityThrowingException(validator, survey);
            fail("Should have thrown exception");
        } catch (InvalidEntityException e) {
            assertEquals("'combobox' is only valid when multiple = false and other = true",
                            errorFor(e, "element7.constraints.uiHint"));
        }
    }

    @Test
    public void multiValueWithMultipleAnswersLimitsConstraints() {
        try {
            survey = new TestSurvey(false);
            SurveyQuestion question = ((TestSurvey) survey).getMultiValueQuestion();
            question.setUiHint(UIHint.SLIDER);
            ((MultiValueConstraints) question.getConstraints()).setAllowMultiple(true);

            Validate.entityThrowingException(validator, survey);
            fail("Should have thrown exception");
        } catch (InvalidEntityException e) {
            assertEquals("allows multiples but the 'slider' UI hint doesn't gather more than one answer",
                            errorFor(e, "element7.constraints.uiHint"));
        }
    }

    @Test
    public void multiValueWithOneAnswerLimitsConstraints() {
        try {
            survey = new TestSurvey(false);
            SurveyQuestion question = ((TestSurvey) survey).getMultiValueQuestion();
            question.setUiHint(UIHint.CHECKBOX);
            ((MultiValueConstraints) question.getConstraints()).setAllowMultiple(false);

            Validate.entityThrowingException(validator, survey);
            fail("Should have thrown exception");
        } catch (InvalidEntityException e) {
            assertEquals("doesn't allow multiples but the 'checkbox' UI hint gathers more than one answer",
                            errorFor(e, "element7.constraints.uiHint"));
        }
    }

    @Test
    public void stringConstraintsMustHaveValidRegularExpressionPattern() {
        try {
            survey = new TestSurvey(false);
            SurveyQuestion question = ((TestSurvey) survey).getStringQuestion();
            ((StringConstraints) question.getConstraints()).setPattern("?");

            Validate.entityThrowingException(validator, survey);
            fail("Should have thrown exception");
        } catch (InvalidEntityException e) {
            assertEquals("pattern is not a valid regular expression: ?", errorFor(e, "element8.constraints.pattern"));
        }
    }
    
}

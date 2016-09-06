package org.sagebionetworks.bridge.validators;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.util.List;

import com.google.common.collect.ImmutableList;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.junit.Before;
import org.junit.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoSurvey;
import org.sagebionetworks.bridge.dynamodb.DynamoSurveyInfoScreen;
import org.sagebionetworks.bridge.dynamodb.DynamoSurveyQuestion;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.surveys.DateConstraints;
import org.sagebionetworks.bridge.models.surveys.DateTimeConstraints;
import org.sagebionetworks.bridge.models.surveys.DecimalConstraints;
import org.sagebionetworks.bridge.models.surveys.DurationConstraints;
import org.sagebionetworks.bridge.models.surveys.Image;
import org.sagebionetworks.bridge.models.surveys.IntegerConstraints;
import org.sagebionetworks.bridge.models.surveys.MultiValueConstraints;
import org.sagebionetworks.bridge.models.surveys.StringConstraints;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.SurveyElement;
import org.sagebionetworks.bridge.models.surveys.SurveyInfoScreen;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestion;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestionOption;
import org.sagebionetworks.bridge.models.surveys.SurveyRule;
import org.sagebionetworks.bridge.models.surveys.SurveyRule.Operator;
import org.sagebionetworks.bridge.models.surveys.TestSurvey;
import org.sagebionetworks.bridge.models.surveys.UIHint;
import org.sagebionetworks.bridge.upload.UploadUtil;

import com.google.common.collect.Lists;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class SurveyValidatorTest {

    private Survey survey;

    private SurveyValidator validator;

    @Before
    public void before() {
        survey = new TestSurvey(SurveyValidatorTest.class, true);
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
            survey = new TestSurvey(SurveyValidatorTest.class, true);
            String identifier = survey.getElements().get(0).getIdentifier();
            survey.getElements().get(1).setIdentifier(identifier);
            Validate.entityThrowingException(validator, survey);
            fail("Should have thrown exception");
        } catch (InvalidEntityException e) {
            assertEquals("elements[1].identifier exists in an earlier survey element", errorFor(e, "elements[1].identifier"));
        }
    }

    @Test
    public void infoScreenIdentifierRequired() {
        try {
            survey = new TestSurvey(SurveyValidatorTest.class, true);
            survey.getElements().add(createSurveyInfoScreen());
            SurveyInfoScreen screen = (SurveyInfoScreen) last(survey);
            screen.setIdentifier("");

            Validate.entityThrowingException(validator, survey);
            fail("Should have thrown exception");
        } catch (InvalidEntityException e) {
            assertEquals("elements[9].identifier is required", errorFor(e, "elements[9].identifier"));
        }
    }

    @Test
    public void infoScreenTitleRequired() {
        try {
            survey = new TestSurvey(SurveyValidatorTest.class, true);
            survey.getElements().add(createSurveyInfoScreen());
            SurveyInfoScreen screen = (SurveyInfoScreen) last(survey);
            screen.setTitle("");

            Validate.entityThrowingException(validator, survey);
            fail("Should have thrown exception");
        } catch (InvalidEntityException e) {
            assertEquals("elements[9].title is required", errorFor(e, "elements[9].title"));
        }
    }

    @Test
    public void infoScreenPromptRequired() {
        try {
            survey = new TestSurvey(SurveyValidatorTest.class, true);
            survey.getElements().add(createSurveyInfoScreen());
            SurveyInfoScreen screen = (SurveyInfoScreen) last(survey);
            screen.setPrompt("");

            Validate.entityThrowingException(validator, survey);
            fail("Should have thrown exception");
        } catch (InvalidEntityException e) {
            assertEquals("elements[9].prompt is required", errorFor(e, "elements[9].prompt"));
        }
    }

    @Test
    public void ifPresentAllFieldsOfSurveyScreenImageRequired() {
        try {
            survey = new TestSurvey(SurveyValidatorTest.class, false);
            survey.getElements().add(createSurveyInfoScreen());
            SurveyInfoScreen screen = (SurveyInfoScreen) last(survey);
            screen.setImage(new Image("", 0, 0));

            Validate.entityThrowingException(validator, survey);
            fail("Should have thrown exception");
        } catch (InvalidEntityException e) {
            assertEquals("elements[9].image.width is required", errorFor(e, "elements[9].image.width"));
            assertEquals("elements[9].image.height is required", errorFor(e, "elements[9].image.height"));
            assertEquals("elements[9].image.source is required", errorFor(e, "elements[9].image.source"));
        }
    }

    @Test
    public void questionIdentifierRequired() {
        try {
            survey = new TestSurvey(SurveyValidatorTest.class, false);
            survey.getElements().get(0).setIdentifier("");

            Validate.entityThrowingException(validator, survey);
            fail("Should have thrown exception");
        } catch (InvalidEntityException e) {
            assertEquals("elements[0].identifier is required", errorFor(e, "elements[0].identifier"));
        }
    }

    @Test
    public void questionIdentifierInvalid() {
        try {
            survey = new TestSurvey(SurveyValidatorTest.class, false);
            survey.getElements().get(0).setIdentifier("**invalid!q##");

            Validate.entityThrowingException(validator, survey);
            fail("Should have thrown exception");
        } catch (InvalidEntityException e) {
            assertEquals("elements[0].identifier " + UploadUtil.INVALID_FIELD_NAME_ERROR_MESSAGE,
                    errorFor(e, "elements[0].identifier"));
        }
    }

    @Test
    public void questionUiHintRequired() {
        try {
            survey = new TestSurvey(SurveyValidatorTest.class, false);
            survey.getUnmodifiableQuestionList().get(0).setUiHint(null);

            Validate.entityThrowingException(validator, survey);
            fail("Should have thrown exception");
        } catch (InvalidEntityException e) {
            assertEquals("elements[0].uiHint is required", errorFor(e, "elements[0].uiHint"));
        }
    }

    @Test
    public void questionPromptRequired() {
        try {
            survey = new TestSurvey(SurveyValidatorTest.class, false);
            survey.getUnmodifiableQuestionList().get(0).setPrompt("");

            Validate.entityThrowingException(validator, survey);
            fail("Should have thrown exception");
        } catch (InvalidEntityException e) {
            assertEquals("elements[0].prompt is required", errorFor(e, "elements[0].prompt"));
        }
    }

    @Test
    public void questionConstraintsRequired() {
        try {
            survey = new TestSurvey(SurveyValidatorTest.class, false);
            survey.getUnmodifiableQuestionList().get(0).setConstraints(null);

            Validate.entityThrowingException(validator, survey);
            fail("Should have thrown exception");
        } catch (InvalidEntityException e) {
            assertEquals("elements[0].constraints is required", errorFor(e, "elements[0].constraints"));
        }
    }

    // Constraints are largely optional, but each constraint must have a data type.
    // In addition, there are some known combinations of UI hints and constraints that make
    // no sense, e.g. allowMultiple with radio buttons.

    @Test
    public void constraintDataTypeRequired() {
        try {
            survey = new TestSurvey(SurveyValidatorTest.class, false);
            survey.getUnmodifiableQuestionList().get(0).getConstraints().setDataType(null);

            Validate.entityThrowingException(validator, survey);
            fail("Should have thrown exception");
        } catch (InvalidEntityException e) {
            assertEquals("elements[0].constraints.dataType is required", errorFor(e, "elements[0].constraints.dataType"));
        }
    }

    @Test
    public void uiHintMustBeSupportedByConstraintsType() {
        try {
            survey = new TestSurvey(SurveyValidatorTest.class, false);
            // Boolean constraints do not jive with lists (which are normally for select multiple)
            survey.getUnmodifiableQuestionList().get(0).setUiHint(UIHint.LIST);

            Validate.entityThrowingException(validator, survey);
            fail("Should have thrown exception");
        } catch (InvalidEntityException e) {
            assertEquals("data type 'boolean' doesn't match the UI hint of 'list'",
                            errorFor(e, "elements[0].constraints.dataType"));
        }
    }

    @Test
    public void multiValueWithComboboxLimitsConstraints() {
        try {
            survey = new TestSurvey(SurveyValidatorTest.class, false);
            SurveyQuestion question = ((TestSurvey) survey).getMultiValueQuestion();
            question.setUiHint(UIHint.COMBOBOX);
            ((MultiValueConstraints) question.getConstraints()).setAllowMultiple(true);

            Validate.entityThrowingException(validator, survey);
            fail("Should have thrown exception");
        } catch (InvalidEntityException e) {
            assertEquals("'combobox' is only valid when multiple = false and other = true",
                            errorFor(e, "elements[7].constraints.uiHint"));
        }
        try {
            survey = new TestSurvey(SurveyValidatorTest.class, false);
            SurveyQuestion question = ((TestSurvey) survey).getMultiValueQuestion();
            question.setUiHint(UIHint.COMBOBOX);
            ((MultiValueConstraints) question.getConstraints()).setAllowOther(false);

            Validate.entityThrowingException(validator, survey);
            fail("Should have thrown exception");
        } catch (InvalidEntityException e) {
            assertEquals("'combobox' is only valid when multiple = false and other = true",
                            errorFor(e, "elements[7].constraints.uiHint"));
        }
    }

    @Test
    public void multiValueWithMultipleAnswersLimitsConstraints() {
        try {
            survey = new TestSurvey(SurveyValidatorTest.class, false);
            SurveyQuestion question = ((TestSurvey) survey).getMultiValueQuestion();
            question.setUiHint(UIHint.SLIDER);
            ((MultiValueConstraints) question.getConstraints()).setAllowMultiple(true);

            Validate.entityThrowingException(validator, survey);
            fail("Should have thrown exception");
        } catch (InvalidEntityException e) {
            assertEquals("allows multiples but the 'slider' UI hint doesn't gather more than one answer",
                            errorFor(e, "elements[7].constraints.uiHint"));
        }
    }

    @Test
    public void multiValueWithOneAnswerLimitsConstraints() {
        try {
            survey = new TestSurvey(SurveyValidatorTest.class, false);
            SurveyQuestion question = ((TestSurvey) survey).getMultiValueQuestion();
            question.setUiHint(UIHint.CHECKBOX);
            ((MultiValueConstraints) question.getConstraints()).setAllowMultiple(false);

            Validate.entityThrowingException(validator, survey);
            fail("Should have thrown exception");
        } catch (InvalidEntityException e) {
            assertEquals("doesn't allow multiples but the 'checkbox' UI hint gathers more than one answer",
                            errorFor(e, "elements[7].constraints.uiHint"));
        }
    }

    @Test
    public void multiValueWithNoEnumeration() {
        List<SurveyQuestionOption>[] testCases = new List[] { null, ImmutableList.of() };

        for (List<SurveyQuestionOption> oneTestCase : testCases) {
            try {
                SurveyQuestion question = ((TestSurvey) survey).getMultiValueQuestion();
                ((MultiValueConstraints) question.getConstraints()).setEnumeration(oneTestCase);

                Validate.entityThrowingException(validator, survey);
                fail("Should have thrown exception");
            } catch (InvalidEntityException e) {
                assertEquals("enumeration must have non-null, non-empty choices list",
                        errorFor(e, "elements[7].constraints.enumeration"));
            }
        }
    }

    @Test
    public void multiValueWithOptionWithNoLabel() {
        String[] testCases = { null, "", "   " };

        for (String oneTestCase : testCases) {
            try {
                List<SurveyQuestionOption> optionList = ImmutableList.of(new SurveyQuestionOption(oneTestCase));

                SurveyQuestion question = ((TestSurvey) survey).getMultiValueQuestion();
                ((MultiValueConstraints) question.getConstraints()).setEnumeration(optionList);

                Validate.entityThrowingException(validator, survey);
                fail("Should have thrown exception");
            } catch (InvalidEntityException e) {
                assertEquals("label must be specified", errorFor(e, "elements[7].constraints.enumeration[0].label"));
            }
        }
    }

    @Test
    public void multiValueWithOptionWithInvalidValue() {
        try {
            List<SurveyQuestionOption> optionList = ImmutableList.of(new SurveyQuestionOption("My Question", null,
                    "@invalid#answer$", null));

            SurveyQuestion question = ((TestSurvey) survey).getMultiValueQuestion();
            ((MultiValueConstraints) question.getConstraints()).setEnumeration(optionList);

            Validate.entityThrowingException(validator, survey);
            fail("Should have thrown exception");
        } catch (InvalidEntityException e) {
            assertEquals("value " + UploadUtil.INVALID_FIELD_NAME_ERROR_MESSAGE,
                    errorFor(e, "elements[7].constraints.enumeration[0].value"));
        }
    }

    @Test
    public void multiValueWithDupeOptions() {
        try {
            List<SurveyQuestionOption> optionList = ImmutableList.of(
                    new SurveyQuestionOption("a", null, "a", null),
                    new SurveyQuestionOption("b1", null, "b", null),
                    new SurveyQuestionOption("b2", null, "b", null),
                    new SurveyQuestionOption("c", null, "c", null),
                    new SurveyQuestionOption("d1", null, "d", null),
                    new SurveyQuestionOption("d2", null, "d", null),
                    new SurveyQuestionOption("e", null, "e", null));

            SurveyQuestion question = ((TestSurvey) survey).getMultiValueQuestion();
            ((MultiValueConstraints) question.getConstraints()).setEnumeration(optionList);

            Validate.entityThrowingException(validator, survey);
            fail("Should have thrown exception");
        } catch (InvalidEntityException e) {
            assertEquals("enumeration must have unique values", errorFor(e, "elements[7].constraints.enumeration"));
        }
    }

    @Test
    public void stringConstraintsMustHaveValidRegularExpressionPattern() {
        try {
            survey = new TestSurvey(SurveyValidatorTest.class, false);
            SurveyQuestion question = ((TestSurvey) survey).getStringQuestion();
            ((StringConstraints) question.getConstraints()).setPattern("?");

            Validate.entityThrowingException(validator, survey);
            fail("Should have thrown exception");
        } catch (InvalidEntityException e) {
            assertEquals("pattern is not a valid regular expression: ?", errorFor(e, "elements[8].constraints.pattern"));
        }
    }
    
    @Test
    public void willValidateRuleReferencesToNonQuestions() {
        // ie this is valid because it is looking at identifiers in survey info screens.
        Survey survey = new DynamoSurvey();
        survey.setName("Name");
        survey.setIdentifier("Identifier");
        survey.setStudyIdentifier("study-key");
        survey.setGuid("guid");
        
        StringConstraints constraints = new StringConstraints();
        
        constraints.getRules().add(
                new SurveyRule.Builder().withOperator(Operator.EQ).withValue("No").withSkipToTarget("theend").build());
        
        SurveyQuestion question = new DynamoSurveyQuestion();
        question.setIdentifier("start-q");
        question.setUiHint(UIHint.TEXTFIELD);
        question.setPrompt("Prompt");
        question.setConstraints(constraints);
        
        SurveyInfoScreen info = new DynamoSurveyInfoScreen();
        info.setTitle("Title");
        info.setPrompt("Prompt");
        info.setIdentifier("theend");
        
        survey.getElements().add(question);
        survey.getElements().add(info);
        
        // Anticlimactic, but this used to throw an exception, and it should not have,
        // because the second element is a SurveyInfoScreen
        Validate.entityThrowingException(validator, survey);
    }
    
    @Test
    public void willVerifyRuleDoesntHaveSkipToAndEndSurvey() throws Exception {
        Survey survey = new DynamoSurvey();
        survey.setName("Name");
        survey.setIdentifier("Identifier");
        survey.setStudyIdentifier("study-key");
        survey.setGuid("guid");
        
        StringConstraints constraints = new StringConstraints();
        
        // This is actually the only way to create an invalid rule (deserializing JSON).
        String json = TestUtils.createJson("{'operator':'eq','value':'No',"+
                "'skipTo':'theend','endSurvey':true}");
        SurveyRule rule = BridgeObjectMapper.get().readValue(json, SurveyRule.class);
        constraints.getRules().add(rule);
        
        SurveyQuestion question = new DynamoSurveyQuestion();
        question.setIdentifier("start-q");
        question.setUiHint(UIHint.TEXTFIELD);
        question.setPrompt("Prompt");
        question.setConstraints(constraints);
        survey.getElements().add(question);
        
        SurveyInfoScreen info = new DynamoSurveyInfoScreen();
        info.setTitle("Title");
        info.setPrompt("Prompt");
        info.setIdentifier("theend");
        survey.getElements().add(info);

        try {
            Validate.entityThrowingException(validator, survey);
            fail("Should have thrown exception");
        } catch (InvalidEntityException e) {
            assertEquals("rule cannot have a skipTo target and an endSurvey property", errorFor(e, "elements[0].rule"));
        }
    }
    
    @Test
    public void willVerifyRuleHasEitherSkipToTargetOrEndSurvey() throws Exception {
        Survey survey = new DynamoSurvey();
        survey.setName("Name");
        survey.setIdentifier("Identifier");
        survey.setStudyIdentifier("study-key");
        survey.setGuid("guid");
        
        StringConstraints constraints = new StringConstraints();
        
        // This is actually the only way to create an invalid rule (deserializing JSON).
        String json = TestUtils.createJson("{'operator':'eq','value':'No'}");
        SurveyRule rule = BridgeObjectMapper.get().readValue(json, SurveyRule.class);
        constraints.getRules().add(rule);
        
        SurveyQuestion question = new DynamoSurveyQuestion();
        question.setIdentifier("start-q");
        question.setUiHint(UIHint.TEXTFIELD);
        question.setPrompt("Prompt");
        question.setConstraints(constraints);
        survey.getElements().add(question);

        try {
            Validate.entityThrowingException(validator, survey);
            fail("Should have thrown exception");
        } catch (InvalidEntityException e) {
            assertEquals("rule must have a skipTo target or an endSurvey property", errorFor(e, "elements[0].rule"));
        }
    }
    
    @Test
    public void willValidateStringMaxLengthNotLowerThanMinLength() {
        try {
            survey = new TestSurvey(SurveyValidatorTest.class, false);
            SurveyQuestion question = ((TestSurvey) survey).getStringQuestion();
            StringConstraints constraints = (StringConstraints)question.getConstraints();
            constraints.setMaxLength(2);
            constraints.setMinLength(3);

            Validate.entityThrowingException(validator, survey);
            fail("Should have thrown exception");
        } catch (InvalidEntityException e) {
            assertEquals("minLength is longer than the maxLength", errorFor(e, "elements[8].constraints.minLength"));
        }
    }
    
    @Test
    public void willValidateMaxValueNotLowerThanMinValueForInteger() {
        try {
            survey = new TestSurvey(SurveyValidatorTest.class, false);
            SurveyQuestion question = ((TestSurvey) survey).getIntegerQuestion();
            IntegerConstraints constraints = (IntegerConstraints)question.getConstraints();
            constraints.setMaxValue(2d);
            constraints.setMinValue(3d);

            Validate.entityThrowingException(validator, survey);
            fail("Should have thrown exception");
        } catch (InvalidEntityException e) {
            assertEquals("minValue is greater than the maxValue", errorFor(e, "elements[4].constraints.minValue"));
        }
    }
    
    @Test
    public void willValidateMaxValueNotLowerThanMinValueForDecimal() {
        try {
            survey = new TestSurvey(SurveyValidatorTest.class, false);
            SurveyQuestion question = ((TestSurvey) survey).getDecimalQuestion();
            DecimalConstraints constraints = (DecimalConstraints)question.getConstraints();
            constraints.setMaxValue(2d);
            constraints.setMinValue(3d);

            Validate.entityThrowingException(validator, survey);
            fail("Should have thrown exception");
        } catch (InvalidEntityException e) {
            assertEquals("minValue is greater than the maxValue", errorFor(e, "elements[3].constraints.minValue"));
        }
    }
    
    @Test
    public void willValidateMaxValueNotLowerThanMinValueForDuration() {
        try {
            survey = new TestSurvey(SurveyValidatorTest.class, false);
            SurveyQuestion question = ((TestSurvey) survey).getDurationQuestion();
            DurationConstraints constraints = (DurationConstraints)question.getConstraints();
            constraints.setMaxValue(2d);
            constraints.setMinValue(3d);

            Validate.entityThrowingException(validator, survey);
            fail("Should have thrown exception");
        } catch (InvalidEntityException e) {
            assertEquals("minValue is greater than the maxValue", errorFor(e, "elements[5].constraints.minValue"));
        }
    }
    
    @Test
    public void willValidateStepValueNotHigherThanRangeOfInteger() {
        try {
            survey = new TestSurvey(SurveyValidatorTest.class, false);
            SurveyQuestion question = ((TestSurvey) survey).getIntegerQuestion();
            IntegerConstraints constraints = (IntegerConstraints)question.getConstraints();
            constraints.setMinValue(2d);
            constraints.setMaxValue(4d);
            constraints.setStep(3d);

            Validate.entityThrowingException(validator, survey);
            fail("Should have thrown exception");
        } catch (InvalidEntityException e) {
            assertEquals("step is larger than the range of allowable values", errorFor(e, "elements[4].constraints.step"));
        }
    }
    
    @Test
    public void willValidateStepValueNotHigherThanRangeOfDecimal() {
        try {
            survey = new TestSurvey(SurveyValidatorTest.class, false);
            SurveyQuestion question = ((TestSurvey) survey).getDecimalQuestion();
            DecimalConstraints constraints = (DecimalConstraints)question.getConstraints();
            constraints.setMinValue(2d);
            constraints.setMaxValue(4d);
            constraints.setStep(3d);

            Validate.entityThrowingException(validator, survey);
            fail("Should have thrown exception");
        } catch (InvalidEntityException e) {
            assertEquals("step is larger than the range of allowable values", errorFor(e, "elements[3].constraints.step"));
        }
    }
    
    @Test
    public void willValidateStepValueNotHigherThanRangeOfDuration() {
        try {
            survey = new TestSurvey(SurveyValidatorTest.class, false);
            SurveyQuestion question = ((TestSurvey) survey).getDurationQuestion();
            DurationConstraints constraints = (DurationConstraints)question.getConstraints();
            constraints.setMinValue(2d);
            constraints.setMaxValue(2d);
            constraints.setStep(3d);

            Validate.entityThrowingException(validator, survey);
            fail("Should have thrown exception");
        } catch (InvalidEntityException e) {
            assertEquals("step is larger than the range of allowable values", errorFor(e, "elements[5].constraints.step"));
        }
    }
    
    @Test
    public void willValidateEarliestLocalDateIsNotAfterLatestLocalDate() {
        try {
            survey = new TestSurvey(SurveyValidatorTest.class, false);
            SurveyQuestion question = ((TestSurvey) survey).getDateTimeQuestion();
            DateTimeConstraints constraints = (DateTimeConstraints)question.getConstraints();
            constraints.setEarliestValue(DateTime.parse("2010-10-10T10:10:00.000Z"));
            constraints.setLatestValue(DateTime.parse("2010-10-10T10:09:00.000Z"));

            Validate.entityThrowingException(validator, survey);
            fail("Should have thrown exception");
        } catch (InvalidEntityException e) {
            assertEquals("earliestValue is after the latest value", errorFor(e, "elements[2].constraints.earliestValue"));
        }
    }

    @Test
    public void willValidateEarliestDateTimeIsNotAfterLatestDateTime() {
        try {
            survey = new TestSurvey(SurveyValidatorTest.class, false);
            SurveyQuestion question = ((TestSurvey) survey).getDateQuestion();
            DateConstraints constraints = (DateConstraints)question.getConstraints();
            constraints.setEarliestValue(LocalDate.parse("2010-10-11"));
            constraints.setLatestValue(LocalDate.parse("2010-10-10"));

            Validate.entityThrowingException(validator, survey);
            fail("Should have thrown exception");
        } catch (InvalidEntityException e) {
            assertEquals("earliestValue is after the latest value", errorFor(e, "elements[1].constraints.earliestValue"));
        }
    }
    
    @Test
    public void backreferenceSkipToTargetInvalid() throws Exception {
        try {
            survey = new TestSurvey(SurveyValidatorTest.class, false);
            
            // The integer question is after the high_bp question. Create a rule that would backgtrack, verify it doesn't validate.
            SurveyQuestion question = ((TestSurvey) survey).getIntegerQuestion();

            SurveyRule rule = new SurveyRule.Builder().withOperator(SurveyRule.Operator.EQ).withValue(1).withSkipToTarget("high_bp").build();
            question.getConstraints().setRules(Lists.newArrayList(rule));
            
            Validate.entityThrowingException(validator, survey);
            fail("Should have thrown exception");
        } catch (InvalidEntityException e) {
            assertEquals("back references question high_bp", errorFor(e, "elements[4].rule"));
        }
    }
    
    @Test
    public void endSurveyRuleValid() {
        survey = new TestSurvey(SurveyValidatorTest.class, false);
        
        // This rule is a valid "end the survey" rule, and passes validation.
        SurveyQuestion question = ((TestSurvey) survey).getIntegerQuestion();
        SurveyRule rule = new SurveyRule.Builder().withOperator(SurveyRule.Operator.EQ).withValue(1).withEndSurvey(Boolean.TRUE).build();
        question.getConstraints().setRules(Lists.newArrayList(rule));
        
        Validate.entityThrowingException(validator, survey);
    }
    
    @Test
    public void noSkipToTargetInvalid() {
        try {
            survey = new TestSurvey(SurveyValidatorTest.class, false);
            
            // The integer question is after the high_bp question. Create a rule that would backgtrack, verify it doesn't validate.
            SurveyQuestion question = ((TestSurvey) survey).getIntegerQuestion();

            SurveyRule rule = new SurveyRule.Builder().withOperator(SurveyRule.Operator.EQ).withValue(1)
                    .withSkipToTarget("this_does_not_exist").build();
            question.getConstraints().setRules(Lists.newArrayList(rule));
            
            Validate.entityThrowingException(validator, survey);
            fail("Should have thrown exception");
        } catch (InvalidEntityException e) {
            assertEquals("has a skipTo identifier that doesn't exist: this_does_not_exist", errorFor(e, "elements[4].rule"));
        }
    }
}

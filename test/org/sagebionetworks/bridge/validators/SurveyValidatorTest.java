package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;

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
        survey.setName("");
        assertValidatorMessage(validator, survey, "name", "is required");
    }

    @Test
    public void identifierRequired() {
        survey.setIdentifier(" ");
        assertValidatorMessage(validator, survey, "identifier", "is required");
    }

    @Test
    public void studyIdentifierRequired() {
        survey.setStudyIdentifier("");
        assertValidatorMessage(validator, survey, "studyIdentifier", "is required");
    }

    @Test
    public void guidRequired() {
        survey.setGuid(null);
        assertValidatorMessage(validator, survey, "guid", "is required");
    }

    @Test
    public void preventsDuplicateElementIdentfiers() {
        survey = new TestSurvey(SurveyValidatorTest.class, true);
        String identifier = survey.getElements().get(0).getIdentifier();
        survey.getElements().get(1).setIdentifier(identifier);

        survey.setGuid(null);
        assertValidatorMessage(validator, survey, "elements[1].identifier", "exists in an earlier survey element");
    }

    @Test
    public void infoScreenIdentifierRequired() {
        survey = new TestSurvey(SurveyValidatorTest.class, true);
        survey.getElements().add(createSurveyInfoScreen());
        SurveyInfoScreen screen = (SurveyInfoScreen) last(survey);
        screen.setIdentifier("");

        assertValidatorMessage(validator, survey, "elements[9].identifier", "is required");
    }

    @Test
    public void infoScreenTitleRequired() {
        survey = new TestSurvey(SurveyValidatorTest.class, true);
        survey.getElements().add(createSurveyInfoScreen());
        SurveyInfoScreen screen = (SurveyInfoScreen) last(survey);
        screen.setTitle("");

        assertValidatorMessage(validator, survey, "elements[9].title", "is required");
    }

    @Test
    public void infoScreenPromptRequired() {
        survey = new TestSurvey(SurveyValidatorTest.class, true);
        survey.getElements().add(createSurveyInfoScreen());
        SurveyInfoScreen screen = (SurveyInfoScreen) last(survey);
        screen.setPrompt("");

        assertValidatorMessage(validator, survey, "elements[9].prompt", "is required");
    }

    @Test
    public void ifPresentAllFieldsOfSurveyScreenImageRequired() {
        survey = new TestSurvey(SurveyValidatorTest.class, false);
        survey.getElements().add(createSurveyInfoScreen());
        SurveyInfoScreen screen = (SurveyInfoScreen) last(survey);
        screen.setImage(new Image("", 0, 0));

        assertValidatorMessage(validator, survey, "elements[9].image.width", "is required");
        assertValidatorMessage(validator, survey, "elements[9].image.height", "is required");
        assertValidatorMessage(validator, survey, "elements[9].image.source", "is required");
    }

    @Test
    public void questionIdentifierRequired() {
        survey = new TestSurvey(SurveyValidatorTest.class, false);
        survey.getElements().get(0).setIdentifier("");

        assertValidatorMessage(validator, survey, "elements[0].identifier", "is required");
    }

    @Test
    public void questionIdentifierInvalid() {
        String fieldName = "**invalid!q##";
        survey = new TestSurvey(SurveyValidatorTest.class, false);
        survey.getElements().get(0).setIdentifier(fieldName);

        assertValidatorMessage(validator, survey, "elements[0].identifier",
                String.format(UploadUtil.INVALID_FIELD_NAME_ERROR_MESSAGE, fieldName));
    }

    @Test
    public void questionUiHintRequired() {
        survey = new TestSurvey(SurveyValidatorTest.class, false);
        survey.getUnmodifiableQuestionList().get(0).setUiHint(null);

        assertValidatorMessage(validator, survey, "elements[0].uiHint", "is required");
    }

    @Test
    public void questionPromptRequired() {
        survey = new TestSurvey(SurveyValidatorTest.class, false);
        survey.getUnmodifiableQuestionList().get(0).setPrompt("");

        assertValidatorMessage(validator, survey, "elements[0].prompt", "is required");
    }

    @Test
    public void questionConstraintsRequired() {
        survey = new TestSurvey(SurveyValidatorTest.class, false);
        survey.getUnmodifiableQuestionList().get(0).setConstraints(null);

        assertValidatorMessage(validator, survey, "elements[0].constraints", "is required");
    }

    // Constraints are largely optional, but each constraint must have a data type.
    // In addition, there are some known combinations of UI hints and constraints that make
    // no sense, e.g. allowMultiple with radio buttons.

    @Test
    public void constraintDataTypeRequired() {
        survey = new TestSurvey(SurveyValidatorTest.class, false);
        survey.getUnmodifiableQuestionList().get(0).getConstraints().setDataType(null);

        assertValidatorMessage(validator, survey, "elements[0].constraints.dataType", "is required");
    }

    @Test
    public void uiHintMustBeSupportedByConstraintsType() {
        survey = new TestSurvey(SurveyValidatorTest.class, false);
        // Boolean constraints do not jive with lists (which are normally for select multiple)
        survey.getUnmodifiableQuestionList().get(0).setUiHint(UIHint.LIST);

        assertValidatorMessage(validator, survey, "elements[0].constraints.dataType",
                "'boolean' doesn't match the UI hint of 'list'");
    }

    @Test
    public void multiValueWithComboboxLimitsConstraints() {
        survey = new TestSurvey(SurveyValidatorTest.class, false);
        SurveyQuestion question = ((TestSurvey) survey).getMultiValueQuestion();
        question.setUiHint(UIHint.COMBOBOX);
        ((MultiValueConstraints) question.getConstraints()).setAllowMultiple(true);

        assertValidatorMessage(validator, survey, "elements[7].constraints.uiHint",
                "'combobox' is only valid when multiple = false and other = true");

        survey = new TestSurvey(SurveyValidatorTest.class, false);
        question = ((TestSurvey) survey).getMultiValueQuestion();
        question.setUiHint(UIHint.COMBOBOX);
        ((MultiValueConstraints) question.getConstraints()).setAllowOther(false);

        assertValidatorMessage(validator, survey, "elements[7].constraints.uiHint",
                "'combobox' is only valid when multiple = false and other = true");
    }

    @Test
    public void multiValueWithMultipleAnswersLimitsConstraints() {
        survey = new TestSurvey(SurveyValidatorTest.class, false);
        SurveyQuestion question = ((TestSurvey) survey).getMultiValueQuestion();
        question.setUiHint(UIHint.SLIDER);
        ((MultiValueConstraints) question.getConstraints()).setAllowMultiple(true);

        assertValidatorMessage(validator, survey, "elements[7].constraints.uiHint",
                "allows multiples but the 'slider' UI hint doesn't gather more than one answer");
    }

    @Test
    public void multiValueWithOneAnswerLimitsConstraints() {
        survey = new TestSurvey(SurveyValidatorTest.class, false);
        SurveyQuestion question = ((TestSurvey) survey).getMultiValueQuestion();
        question.setUiHint(UIHint.CHECKBOX);
        ((MultiValueConstraints) question.getConstraints()).setAllowMultiple(false);

        assertValidatorMessage(validator, survey, "elements[7].constraints.uiHint",
                "doesn't allow multiples but the 'checkbox' UI hint gathers more than one answer");
    }

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
    public void multiValueWithOptionWithNoLabel() {
        String[] testCases = { null, "", "   " };

        for (String oneTestCase : testCases) {
            List<SurveyQuestionOption> optionList = ImmutableList.of(new SurveyQuestionOption(oneTestCase));

            SurveyQuestion question = ((TestSurvey) survey).getMultiValueQuestion();
            ((MultiValueConstraints) question.getConstraints()).setEnumeration(optionList);

            assertValidatorMessage(validator, survey, "elements[7].constraints.enumeration[0].label", "is required");
        }
    }

    @Test
    public void keywordsAreValidChoiceValues() {
        List<SurveyQuestionOption> optionList = ImmutableList.of(new SurveyQuestionOption("true"),
                new SurveyQuestionOption("false"), new SurveyQuestionOption("select"),
                new SurveyQuestionOption("where"));

        SurveyQuestion question = ((TestSurvey) survey).getMultiValueQuestion();
        ((MultiValueConstraints) question.getConstraints()).setEnumeration(optionList);

        Validate.entityThrowingException(validator, survey);
    }

    @Test
    public void multiValueWithOptionWithInvalidValue() {
        String answerChoice = "@invalid#answer$";
        List<SurveyQuestionOption> optionList = ImmutableList
                .of(new SurveyQuestionOption("My Question", null, answerChoice, null));

        SurveyQuestion question = ((TestSurvey) survey).getMultiValueQuestion();
        ((MultiValueConstraints) question.getConstraints()).setEnumeration(optionList);

        assertValidatorMessage(validator, survey, "elements[7].constraints.enumeration[0].value",
                String.format(UploadUtil.INVALID_ANSWER_CHOICE_ERROR_MESSAGE, answerChoice));
    }

    @Test
    public void multiValueWithDupeOptions() {
        List<SurveyQuestionOption> optionList = ImmutableList.of(new SurveyQuestionOption("a", null, "a", null),
                new SurveyQuestionOption("b1", null, "b", null), new SurveyQuestionOption("b2", null, "b", null),
                new SurveyQuestionOption("c", null, "c", null), new SurveyQuestionOption("d1", null, "d", null),
                new SurveyQuestionOption("d2", null, "d", null), new SurveyQuestionOption("e", null, "e", null));

        SurveyQuestion question = ((TestSurvey) survey).getMultiValueQuestion();
        ((MultiValueConstraints) question.getConstraints()).setEnumeration(optionList);

        assertValidatorMessage(validator, survey, "elements[7].constraints.enumeration", "must have unique values");
    }

    @Test
    public void stringConstraintsMustHaveValidRegularExpressionPattern() {
        survey = new TestSurvey(SurveyValidatorTest.class, false);
        SurveyQuestion question = ((TestSurvey) survey).getStringQuestion();
        ((StringConstraints) question.getConstraints()).setPattern("?");

        assertValidatorMessage(validator, survey, "elements[8].constraints.pattern",
                "is not a valid regular expression: ?");
    }
    
    @Test
    public void stringConstraintsCanHaveValidRegularExpressionPattern() {
        survey = new TestSurvey(SurveyValidatorTest.class, false);
        SurveyQuestion question = ((TestSurvey) survey).getStringQuestion();
        ((StringConstraints) question.getConstraints()).setPattern("{1,3}\\d-{1,3}\\d-");

        Validate.entityThrowingException(validator, survey);
    }
    
    @Test
    public void whenPatternIsSetPatternErrorMessageMustBeSet() {
        survey = new TestSurvey(SurveyValidatorTest.class, false);
        SurveyQuestion question = ((TestSurvey) survey).getStringQuestion();
        ((StringConstraints) question.getConstraints()).setPatternErrorMessage(null);

        assertValidatorMessage(validator, survey, "elements[8].constraints.patternErrorMessage",
                "is required if pattern is defined");
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
        String json = TestUtils.createJson("{'operator':'eq','value':'No'," + "'skipTo':'theend','endSurvey':true}");
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

        assertValidatorMessage(validator, survey, "elements[0].rule",
                "cannot have a skipTo target and an endSurvey property");
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

        assertValidatorMessage(validator, survey, "elements[0].rule",
                "must have a skipTo target or an endSurvey property");
    }

    @Test
    public void willValidateStringMaxLengthNotLowerThanMinLength() {
        survey = new TestSurvey(SurveyValidatorTest.class, false);
        SurveyQuestion question = ((TestSurvey) survey).getStringQuestion();
        StringConstraints constraints = (StringConstraints) question.getConstraints();
        constraints.setMaxLength(2);
        constraints.setMinLength(3);

        assertValidatorMessage(validator, survey, "elements[8].constraints.minLength", "is longer than the maxLength");
    }

    @Test
    public void willValidateMaxValueNotLowerThanMinValueForInteger() {
        survey = new TestSurvey(SurveyValidatorTest.class, false);
        SurveyQuestion question = ((TestSurvey) survey).getIntegerQuestion();
        IntegerConstraints constraints = (IntegerConstraints) question.getConstraints();
        constraints.setMaxValue(2d);
        constraints.setMinValue(3d);

        assertValidatorMessage(validator, survey, "elements[4].constraints.minValue", "is greater than the maxValue");
    }

    @Test
    public void willValidateMaxValueNotLowerThanMinValueForDecimal() {
        survey = new TestSurvey(SurveyValidatorTest.class, false);
        SurveyQuestion question = ((TestSurvey) survey).getDecimalQuestion();
        DecimalConstraints constraints = (DecimalConstraints) question.getConstraints();
        constraints.setMaxValue(2d);
        constraints.setMinValue(3d);

        assertValidatorMessage(validator, survey, "elements[3].constraints.minValue", "is greater than the maxValue");
    }

    @Test
    public void willValidateMaxValueNotLowerThanMinValueForDuration() {
        survey = new TestSurvey(SurveyValidatorTest.class, false);
        SurveyQuestion question = ((TestSurvey) survey).getDurationQuestion();
        DurationConstraints constraints = (DurationConstraints) question.getConstraints();
        constraints.setMaxValue(2d);
        constraints.setMinValue(3d);

        assertValidatorMessage(validator, survey, "elements[5].constraints.minValue", "is greater than the maxValue");
    }

    @Test
    public void willValidateStepValueNotHigherThanRangeOfInteger() {
        survey = new TestSurvey(SurveyValidatorTest.class, false);
        SurveyQuestion question = ((TestSurvey) survey).getIntegerQuestion();
        IntegerConstraints constraints = (IntegerConstraints) question.getConstraints();
        constraints.setMinValue(2d);
        constraints.setMaxValue(4d);
        constraints.setStep(3d);

        assertValidatorMessage(validator, survey, "elements[4].constraints.step",
                "is larger than the range of allowable values");
    }

    @Test
    public void willValidateStepValueNotHigherThanRangeOfDecimal() {
        survey = new TestSurvey(SurveyValidatorTest.class, false);
        SurveyQuestion question = ((TestSurvey) survey).getDecimalQuestion();
        DecimalConstraints constraints = (DecimalConstraints) question.getConstraints();
        constraints.setMinValue(2d);
        constraints.setMaxValue(4d);
        constraints.setStep(3d);

        assertValidatorMessage(validator, survey, "elements[3].constraints.step",
                "is larger than the range of allowable values");
    }

    @Test
    public void willValidateStepValueNotHigherThanRangeOfDuration() {
        survey = new TestSurvey(SurveyValidatorTest.class, false);
        SurveyQuestion question = ((TestSurvey) survey).getDurationQuestion();
        DurationConstraints constraints = (DurationConstraints) question.getConstraints();
        constraints.setMinValue(2d);
        constraints.setMaxValue(2d);
        constraints.setStep(3d);

        assertValidatorMessage(validator, survey, "elements[5].constraints.step",
                "is larger than the range of allowable values");
    }

    @Test
    public void willValidateEarliestLocalDateIsNotAfterLatestLocalDate() {
        survey = new TestSurvey(SurveyValidatorTest.class, false);
        SurveyQuestion question = ((TestSurvey) survey).getDateTimeQuestion();
        DateTimeConstraints constraints = (DateTimeConstraints) question.getConstraints();
        constraints.setEarliestValue(DateTime.parse("2010-10-10T10:10:00.000Z"));
        constraints.setLatestValue(DateTime.parse("2010-10-10T10:09:00.000Z"));

        assertValidatorMessage(validator, survey, "elements[2].constraints.earliestValue", "is after the latest value");
    }

    @Test
    public void willValidateEarliestDateTimeIsNotAfterLatestDateTime() {
        survey = new TestSurvey(SurveyValidatorTest.class, false);
        SurveyQuestion question = ((TestSurvey) survey).getDateQuestion();
        DateConstraints constraints = (DateConstraints) question.getConstraints();
        constraints.setEarliestValue(LocalDate.parse("2010-10-11"));
        constraints.setLatestValue(LocalDate.parse("2010-10-10"));

        assertValidatorMessage(validator, survey, "elements[1].constraints.earliestValue", "is after the latest value");
    }

    @Test
    public void backreferenceSkipToTargetInvalid() throws Exception {
        survey = new TestSurvey(SurveyValidatorTest.class, false);

        // The integer question is after the high_bp question. Create a rule that would backgtrack, verify it
        // doesn't validate.
        SurveyQuestion question = ((TestSurvey) survey).getIntegerQuestion();

        SurveyRule rule = new SurveyRule.Builder().withOperator(SurveyRule.Operator.EQ).withValue(1)
                .withSkipToTarget("high_bp").build();
        question.getConstraints().setRules(Lists.newArrayList(rule));

        assertValidatorMessage(validator, survey, "elements[4].rule", "back references question high_bp");
    }

    @Test
    public void endSurveyRuleValid() {
        survey = new TestSurvey(SurveyValidatorTest.class, false);

        // This rule is a valid "end the survey" rule, and passes validation.
        SurveyQuestion question = ((TestSurvey) survey).getIntegerQuestion();
        SurveyRule rule = new SurveyRule.Builder().withOperator(SurveyRule.Operator.EQ).withValue(1)
                .withEndSurvey(Boolean.TRUE).build();
        question.getConstraints().setRules(Lists.newArrayList(rule));

        Validate.entityThrowingException(validator, survey);
    }

    @Test
    public void noSkipToTargetInvalid() {
        survey = new TestSurvey(SurveyValidatorTest.class, false);

        // The integer question is after the high_bp question. Create a rule that would backgtrack, verify it
        // doesn't validate.
        SurveyQuestion question = ((TestSurvey) survey).getIntegerQuestion();

        SurveyRule rule = new SurveyRule.Builder().withOperator(SurveyRule.Operator.EQ).withValue(1)
                .withSkipToTarget("this_does_not_exist").build();
        question.getConstraints().setRules(Lists.newArrayList(rule));

        assertValidatorMessage(validator, survey, "elements[4].rule",
                "has a skipTo identifier that doesn't exist: this_does_not_exist");
    }
}

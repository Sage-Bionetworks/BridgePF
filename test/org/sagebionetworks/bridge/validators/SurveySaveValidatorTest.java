package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.google.common.collect.ImmutableList;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.YearMonth;
import org.junit.Before;
import org.junit.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoSurvey;
import org.sagebionetworks.bridge.dynamodb.DynamoSurveyInfoScreen;
import org.sagebionetworks.bridge.dynamodb.DynamoSurveyQuestion;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.surveys.DataType;
import org.sagebionetworks.bridge.models.surveys.DateConstraints;
import org.sagebionetworks.bridge.models.surveys.DateTimeConstraints;
import org.sagebionetworks.bridge.models.surveys.DecimalConstraints;
import org.sagebionetworks.bridge.models.surveys.DurationConstraints;
import org.sagebionetworks.bridge.models.surveys.Image;
import org.sagebionetworks.bridge.models.surveys.IntegerConstraints;
import org.sagebionetworks.bridge.models.surveys.MultiValueConstraints;
import org.sagebionetworks.bridge.models.surveys.PostalCodeConstraints;
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
import org.sagebionetworks.bridge.models.surveys.YearMonthConstraints;
import org.sagebionetworks.bridge.upload.UploadUtil;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class SurveySaveValidatorTest {
    
    private static final Set<String> STUDY_DATA_GROUPS = Sets.newHashSet("foo", "baz");

    private Survey survey;

    private SurveySaveValidator validator;

    @Before
    public void before() {
        
        survey = new TestSurvey(SurveySaveValidatorTest.class, true);
        // because this is set by the service before validation
        survey.setGuid("AAA");
        validator = new SurveySaveValidator(STUDY_DATA_GROUPS);
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
        survey = new TestSurvey(SurveySaveValidatorTest.class, true);
        String identifier = survey.getElements().get(0).getIdentifier();
        survey.getElements().get(1).setIdentifier(identifier);

        survey.setGuid(null);
        assertValidatorMessage(validator, survey, "elements[1].identifier", "exists in an earlier survey element");
    }

    @Test
    public void infoScreenIdentifierRequired() {
        survey = new TestSurvey(SurveySaveValidatorTest.class, true);
        survey.getElements().add(createSurveyInfoScreen());
        SurveyInfoScreen screen = (SurveyInfoScreen) last(survey);
        screen.setIdentifier("");

        assertValidatorMessage(validator, survey, "elements[11].identifier", "is required");
    }

    @Test
    public void infoScreenTitleRequired() {
        survey = new TestSurvey(SurveySaveValidatorTest.class, true);
        survey.getElements().add(createSurveyInfoScreen());
        SurveyInfoScreen screen = (SurveyInfoScreen) last(survey);
        screen.setTitle("");

        assertValidatorMessage(validator, survey, "elements[11].title", "is required");
    }

    @Test
    public void infoScreenPromptRequired() {
        survey = new TestSurvey(SurveySaveValidatorTest.class, true);
        survey.getElements().add(createSurveyInfoScreen());
        SurveyInfoScreen screen = (SurveyInfoScreen) last(survey);
        screen.setPrompt("");

        assertValidatorMessage(validator, survey, "elements[11].prompt", "is required");
    }

    @Test
    public void ifPresentAllFieldsOfSurveyScreenImageRequired() {
        survey = new TestSurvey(SurveySaveValidatorTest.class, false);
        survey.getElements().add(createSurveyInfoScreen());
        SurveyInfoScreen screen = (SurveyInfoScreen) last(survey);
        screen.setImage(new Image("", 0, 0));

        assertValidatorMessage(validator, survey, "elements[11].image.width", "is required");
        assertValidatorMessage(validator, survey, "elements[11].image.height", "is required");
        assertValidatorMessage(validator, survey, "elements[11].image.source", "is required");
    }

    @Test
    public void questionIdentifierRequired() {
        survey = new TestSurvey(SurveySaveValidatorTest.class, false);
        survey.getElements().get(0).setIdentifier("");

        assertValidatorMessage(validator, survey, "elements[0].identifier", "is required");
    }

    @Test
    public void questionIdentifierInvalid() {
        String fieldName = "**invalid!q##";
        survey = new TestSurvey(SurveySaveValidatorTest.class, false);
        survey.getElements().get(0).setIdentifier(fieldName);

        assertValidatorMessage(validator, survey, "elements[0].identifier",
                String.format(UploadUtil.INVALID_FIELD_NAME_ERROR_MESSAGE, fieldName));
    }

    @Test
    public void questionUiHintRequired() {
        survey = new TestSurvey(SurveySaveValidatorTest.class, false);
        survey.getUnmodifiableQuestionList().get(0).setUiHint(null);

        assertValidatorMessage(validator, survey, "elements[0].uiHint", "is required");
    }

    @Test
    public void questionPromptRequired() {
        survey = new TestSurvey(SurveySaveValidatorTest.class, false);
        survey.getUnmodifiableQuestionList().get(0).setPrompt("");

        assertValidatorMessage(validator, survey, "elements[0].prompt", "is required");
    }

    @Test
    public void questionConstraintsRequired() {
        survey = new TestSurvey(SurveySaveValidatorTest.class, false);
        survey.getUnmodifiableQuestionList().get(0).setConstraints(null);

        assertValidatorMessage(validator, survey, "elements[0].constraints", "is required");
    }

    // Constraints are largely optional, but each constraint must have a data type.
    // In addition, there are some known combinations of UI hints and constraints that make
    // no sense, e.g. allowMultiple with radio buttons.

    @Test
    public void constraintDataTypeRequired() {
        survey = new TestSurvey(SurveySaveValidatorTest.class, false);
        survey.getUnmodifiableQuestionList().get(0).getConstraints().setDataType(null);

        assertValidatorMessage(validator, survey, "elements[0].constraints.dataType", "is required");
    }

    @Test
    public void uiHintMustBeSupportedByConstraintsType() {
        survey = new TestSurvey(SurveySaveValidatorTest.class, false);
        // Boolean constraints do not jive with lists (which are normally for select multiple)
        survey.getUnmodifiableQuestionList().get(0).setUiHint(UIHint.LIST);

        assertValidatorMessage(validator, survey, "elements[0].constraints.dataType",
                "'boolean' doesn't match the UI hint of 'list'");
    }

    @Test
    public void multiValueWithComboboxLimitsConstraints() {
        survey = new TestSurvey(SurveySaveValidatorTest.class, false);
        SurveyQuestion question = ((TestSurvey) survey).getMultiValueQuestion();
        question.setUiHint(UIHint.COMBOBOX);
        ((MultiValueConstraints) question.getConstraints()).setAllowMultiple(true);

        assertValidatorMessage(validator, survey, "elements[7].constraints.uiHint",
                "'combobox' is only valid when multiple = false and other = true");

        survey = new TestSurvey(SurveySaveValidatorTest.class, false);
        question = ((TestSurvey) survey).getMultiValueQuestion();
        question.setUiHint(UIHint.COMBOBOX);
        ((MultiValueConstraints) question.getConstraints()).setAllowOther(false);

        assertValidatorMessage(validator, survey, "elements[7].constraints.uiHint",
                "'combobox' is only valid when multiple = false and other = true");
    }

    @Test
    public void multiValueWithMultipleAnswersLimitsConstraints() {
        survey = new TestSurvey(SurveySaveValidatorTest.class, false);
        SurveyQuestion question = ((TestSurvey) survey).getMultiValueQuestion();
        question.setUiHint(UIHint.SLIDER);
        ((MultiValueConstraints) question.getConstraints()).setAllowMultiple(true);

        assertValidatorMessage(validator, survey, "elements[7].constraints.uiHint",
                "allows multiples but the 'slider' UI hint doesn't gather more than one answer");
    }

    @Test
    public void multiValueWithOneAnswerLimitsConstraints() {
        survey = new TestSurvey(SurveySaveValidatorTest.class, false);
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

            Validate.entityThrowingException(validator, survey);
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
        survey = new TestSurvey(SurveySaveValidatorTest.class, false);
        SurveyQuestion question = ((TestSurvey) survey).getStringQuestion();
        ((StringConstraints) question.getConstraints()).setPattern("?");

        assertValidatorMessage(validator, survey, "elements[8].constraints.pattern",
                "is not a valid regular expression: ?");
    }
    
    @Test
    public void stringConstraintsCanHaveValidRegularExpressionPattern() {
        survey = new TestSurvey(SurveySaveValidatorTest.class, false);
        SurveyQuestion question = ((TestSurvey) survey).getStringQuestion();
        ((StringConstraints) question.getConstraints()).setPattern("{1,3}\\d-{1,3}\\d-");

        Validate.entityThrowingException(validator, survey);
    }
    
    @Test
    public void whenPatternIsSetPatternErrorMessageMustBeSet() {
        survey = new TestSurvey(SurveySaveValidatorTest.class, false);
        SurveyQuestion question = ((TestSurvey) survey).getStringQuestion();
        ((StringConstraints) question.getConstraints()).setPatternErrorMessage(null);

        assertValidatorMessage(validator, survey, "elements[8].constraints.patternErrorMessage",
                "is required if pattern is defined");
    }

    @Test
    public void willValidateRuleReferencesToNonQuestionsInConstraints() {
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
    public void willVerifyRuleDoesntHaveSkipToAndEndSurveyInConstraints() throws Exception {
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

        assertValidatorMessage(validator, survey, "elements[0].constraints.rules[0]",
                "must have one and only one action");
    }

    @Test
    public void willVerifyRuleHasEitherSkipToTargetOrEndSurveyInConstraints() throws Exception {
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

        assertValidatorMessage(validator, survey, "elements[0].constraints.rules[0]",
                "must have one and only one action");
    }

    @Test
    public void willValidateStringMaxLengthNotLowerThanMinLength() {
        survey = new TestSurvey(SurveySaveValidatorTest.class, false);
        SurveyQuestion question = ((TestSurvey) survey).getStringQuestion();
        StringConstraints constraints = (StringConstraints) question.getConstraints();
        constraints.setMaxLength(2);
        constraints.setMinLength(3);

        assertValidatorMessage(validator, survey, "elements[8].constraints.minLength", "is longer than the maxLength");
    }
    
    @Test
    public void willValidateRuleReferencesToNonQuestionsInElement() {
        // ie this is valid because it is looking at identifiers in survey info screens.
        Survey survey = new DynamoSurvey();
        survey.setName("Name");
        survey.setIdentifier("Identifier");
        survey.setStudyIdentifier("study-key");
        survey.setGuid("guid");

        StringConstraints constraints = new StringConstraints();

        SurveyQuestion question = new DynamoSurveyQuestion();
        question.setIdentifier("start-q");
        question.setUiHint(UIHint.TEXTFIELD);
        question.setPrompt("Prompt");
        question.setConstraints(constraints);
        question.setAfterRules(Lists.newArrayList(
                new SurveyRule.Builder().withOperator(Operator.EQ).withValue("No").withSkipToTarget("theend").build()));

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
    public void willVerifyRuleDoesntHaveSkipToAndEndSurveyInElement() throws Exception {
        Survey survey = new DynamoSurvey();
        survey.setName("Name");
        survey.setIdentifier("Identifier");
        survey.setStudyIdentifier("study-key");
        survey.setGuid("guid");

        StringConstraints constraints = new StringConstraints();

        // This is actually the only way to create an invalid rule (deserializing JSON).
        String json = TestUtils.createJson("{'operator':'eq','value':'No'," + "'skipTo':'theend','endSurvey':true}");
        SurveyRule rule = BridgeObjectMapper.get().readValue(json, SurveyRule.class);

        SurveyQuestion question = new DynamoSurveyQuestion();
        question.setIdentifier("start-q");
        question.setUiHint(UIHint.TEXTFIELD);
        question.setPrompt("Prompt");
        question.setConstraints(constraints);
        question.setAfterRules(Lists.newArrayList(rule));
        
        survey.getElements().add(question);

        SurveyInfoScreen info = new DynamoSurveyInfoScreen();
        info.setTitle("Title");
        info.setPrompt("Prompt");
        info.setIdentifier("theend");
        survey.getElements().add(info);

        assertValidatorMessage(validator, survey, "elements[0].afterRules[0]",
                "must have one and only one action");
    }

    @Test
    public void willVerifyRuleHasEitherSkipToTargetOrEndSurveyInElement() throws Exception {
        Survey survey = new DynamoSurvey();
        survey.setName("Name");
        survey.setIdentifier("Identifier");
        survey.setStudyIdentifier("study-key");
        survey.setGuid("guid");

        StringConstraints constraints = new StringConstraints();

        // This is actually the only way to create an invalid rule (deserializing JSON).
        String json = TestUtils.createJson("{'operator':'eq','value':'No'}");
        SurveyRule rule = BridgeObjectMapper.get().readValue(json, SurveyRule.class);

        SurveyQuestion question = new DynamoSurveyQuestion();
        question.setIdentifier("start-q");
        question.setUiHint(UIHint.TEXTFIELD);
        question.setPrompt("Prompt");
        question.setConstraints(constraints);
        question.setAfterRules(Lists.newArrayList(rule));
        
        survey.getElements().add(question);

        assertValidatorMessage(validator, survey, "elements[0].afterRules[0]",
                "must have one and only one action");
    }

    @Test
    public void willValidateMaxValueNotLowerThanMinValueForInteger() {
        survey = new TestSurvey(SurveySaveValidatorTest.class, false);
        SurveyQuestion question = ((TestSurvey) survey).getIntegerQuestion();
        IntegerConstraints constraints = (IntegerConstraints) question.getConstraints();
        constraints.setMaxValue(2d);
        constraints.setMinValue(3d);

        assertValidatorMessage(validator, survey, "elements[4].constraints.minValue", "is greater than the maxValue");
    }

    @Test
    public void willValidateIntegerSelectHasMinValue() {
        survey = new TestSurvey(SurveySaveValidatorTest.class, false);
        SurveyQuestion question = ((TestSurvey) survey).getIntegerQuestion();
        question.setUiHint(UIHint.SELECT);

        IntegerConstraints constraints = (IntegerConstraints) question.getConstraints();
        constraints.setMinValue(null);

        assertValidatorMessage(validator, survey, "elements[4].constraints.minValue", "is required for select");
    }

    @Test
    public void willValidateIntegerSelectHasMaxValue() {
        survey = new TestSurvey(SurveySaveValidatorTest.class, false);
        SurveyQuestion question = ((TestSurvey) survey).getIntegerQuestion();
        question.setUiHint(UIHint.SELECT);

        IntegerConstraints constraints = (IntegerConstraints) question.getConstraints();
        constraints.setMaxValue(null);

        assertValidatorMessage(validator, survey, "elements[4].constraints.maxValue", "is required for select");
    }


    @Test
    public void willValidateIntegerSliderHasMinValue() {
        survey = new TestSurvey(SurveySaveValidatorTest.class, false);
        SurveyQuestion question = ((TestSurvey) survey).getIntegerQuestion();
        question.setUiHint(UIHint.SLIDER);

        IntegerConstraints constraints = (IntegerConstraints) question.getConstraints();
        constraints.setMinValue(null);

        assertValidatorMessage(validator, survey, "elements[4].constraints.minValue", "is required for slider");
    }

    @Test
    public void willValidateIntegerSliderHasMaxValue() {
        survey = new TestSurvey(SurveySaveValidatorTest.class, false);
        SurveyQuestion question = ((TestSurvey) survey).getIntegerQuestion();
        question.setUiHint(UIHint.SLIDER);

        IntegerConstraints constraints = (IntegerConstraints) question.getConstraints();
        constraints.setMaxValue(null);

        assertValidatorMessage(validator, survey, "elements[4].constraints.maxValue", "is required for slider");
    }

    @Test
    public void willValidateMaxValueNotLowerThanMinValueForDecimal() {
        survey = new TestSurvey(SurveySaveValidatorTest.class, false);
        SurveyQuestion question = ((TestSurvey) survey).getDecimalQuestion();
        DecimalConstraints constraints = (DecimalConstraints) question.getConstraints();
        constraints.setMaxValue(2d);
        constraints.setMinValue(3d);

        assertValidatorMessage(validator, survey, "elements[3].constraints.minValue", "is greater than the maxValue");
    }

    @Test
    public void willValidateDecimalSelectHasMinValue() {
        survey = new TestSurvey(SurveySaveValidatorTest.class, false);
        SurveyQuestion question = ((TestSurvey) survey).getDecimalQuestion();
        question.setUiHint(UIHint.SELECT);

        DecimalConstraints constraints = (DecimalConstraints) question.getConstraints();
        constraints.setMinValue(null);

        assertValidatorMessage(validator, survey, "elements[3].constraints.minValue", "is required for select");
    }

    @Test
    public void willValidateDecimalSelectHasMaxValue() {
        survey = new TestSurvey(SurveySaveValidatorTest.class, false);
        SurveyQuestion question = ((TestSurvey) survey).getDecimalQuestion();
        question.setUiHint(UIHint.SELECT);

        DecimalConstraints constraints = (DecimalConstraints) question.getConstraints();
        constraints.setMaxValue(null);

        assertValidatorMessage(validator, survey, "elements[3].constraints.maxValue", "is required for select");
    }


    @Test
    public void willValidateDecimalSliderHasMinValue() {
        survey = new TestSurvey(SurveySaveValidatorTest.class, false);
        SurveyQuestion question = ((TestSurvey) survey).getDecimalQuestion();
        question.setUiHint(UIHint.SLIDER);

        DecimalConstraints constraints = (DecimalConstraints) question.getConstraints();
        constraints.setMinValue(null);

        assertValidatorMessage(validator, survey, "elements[3].constraints.minValue", "is required for slider");
    }

    @Test
    public void willValidateDecimalSliderHasMaxValue() {
        survey = new TestSurvey(SurveySaveValidatorTest.class, false);
        SurveyQuestion question = ((TestSurvey) survey).getDecimalQuestion();
        question.setUiHint(UIHint.SLIDER);

        DecimalConstraints constraints = (DecimalConstraints) question.getConstraints();
        constraints.setMaxValue(null);

        assertValidatorMessage(validator, survey, "elements[3].constraints.maxValue", "is required for slider");
    }

    @Test
    public void willValidateMaxValueNotLowerThanMinValueForDuration() {
        survey = new TestSurvey(SurveySaveValidatorTest.class, false);
        SurveyQuestion question = ((TestSurvey) survey).getDurationQuestion();
        DurationConstraints constraints = (DurationConstraints) question.getConstraints();
        constraints.setMaxValue(2d);
        constraints.setMinValue(3d);

        assertValidatorMessage(validator, survey, "elements[5].constraints.minValue", "is greater than the maxValue");
    }

    @Test
    public void willValidateStepValueNotHigherThanRangeOfInteger() {
        survey = new TestSurvey(SurveySaveValidatorTest.class, false);
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
        survey = new TestSurvey(SurveySaveValidatorTest.class, false);
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
        survey = new TestSurvey(SurveySaveValidatorTest.class, false);
        SurveyQuestion question = ((TestSurvey) survey).getDurationQuestion();
        DurationConstraints constraints = (DurationConstraints) question.getConstraints();
        constraints.setMinValue(2d);
        constraints.setMaxValue(2d);
        constraints.setStep(3d);

        assertValidatorMessage(validator, survey, "elements[5].constraints.step",
                "is larger than the range of allowable values");
    }

    @Test
    public void willValidateDurationSelectHasMinValue() {
        survey = new TestSurvey(SurveySaveValidatorTest.class, false);
        SurveyQuestion question = ((TestSurvey) survey).getDurationQuestion();
        question.setUiHint(UIHint.SELECT);

        DurationConstraints constraints = (DurationConstraints) question.getConstraints();
        constraints.setMinValue(null);

        assertValidatorMessage(validator, survey, "elements[5].constraints.minValue", "is required for select");
    }

    @Test
    public void willValidateDurationSelectHasMaxValue() {
        survey = new TestSurvey(SurveySaveValidatorTest.class, false);
        SurveyQuestion question = ((TestSurvey) survey).getDurationQuestion();
        question.setUiHint(UIHint.SELECT);

        DurationConstraints constraints = (DurationConstraints) question.getConstraints();
        constraints.setMaxValue(null);

        assertValidatorMessage(validator, survey, "elements[5].constraints.maxValue", "is required for select");
    }


    @Test
    public void willValidateDurationSliderHasMinValue() {
        survey = new TestSurvey(SurveySaveValidatorTest.class, false);
        SurveyQuestion question = ((TestSurvey) survey).getDurationQuestion();
        question.setUiHint(UIHint.SLIDER);

        DurationConstraints constraints = (DurationConstraints) question.getConstraints();
        constraints.setMinValue(null);

        assertValidatorMessage(validator, survey, "elements[5].constraints.minValue", "is required for slider");
    }

    @Test
    public void willValidateDurationSliderHasMaxValue() {
        survey = new TestSurvey(SurveySaveValidatorTest.class, false);
        SurveyQuestion question = ((TestSurvey) survey).getDurationQuestion();
        question.setUiHint(UIHint.SLIDER);

        DurationConstraints constraints = (DurationConstraints) question.getConstraints();
        constraints.setMaxValue(null);

        assertValidatorMessage(validator, survey, "elements[5].constraints.maxValue", "is required for slider");
    }

    @Test
    public void willValidateEarliestLocalDateIsNotAfterLatestLocalDate() {
        survey = new TestSurvey(SurveySaveValidatorTest.class, false);
        SurveyQuestion question = ((TestSurvey) survey).getDateTimeQuestion();
        DateTimeConstraints constraints = (DateTimeConstraints) question.getConstraints();
        constraints.setEarliestValue(DateTime.parse("2010-10-10T10:10:00.000Z"));
        constraints.setLatestValue(DateTime.parse("2010-10-10T10:09:00.000Z"));

        assertValidatorMessage(validator, survey, "elements[2].constraints.earliestValue", "is after the latest value");
    }

    @Test
    public void willValidateEarliestDateTimeIsNotAfterLatestDateTime() {
        survey = new TestSurvey(SurveySaveValidatorTest.class, false);
        SurveyQuestion question = ((TestSurvey) survey).getDateQuestion();
        DateConstraints constraints = (DateConstraints) question.getConstraints();
        constraints.setEarliestValue(LocalDate.parse("2010-10-11"));
        constraints.setLatestValue(LocalDate.parse("2010-10-10"));

        assertValidatorMessage(validator, survey, "elements[1].constraints.earliestValue", "is after the latest value");
    }

    @Test
    public void backreferenceSkipToTargetInvalidInConstraints() throws Exception {
        SurveyRule rule = new SurveyRule.Builder().withOperator(SurveyRule.Operator.EQ).withValue(1)
                .withSkipToTarget("foo").build();
        updateSurveyWithRulesInConstraints(rule);
        
        // need a backreference to trigger failure 
        Survey tempSurvey = new TestSurvey(SurveySaveValidatorTest.class, false);
        SurveyQuestion question = ((TestSurvey)tempSurvey).getStringQuestion();
        question.setIdentifier("foo");
        survey.getElements().add(0, question);

        assertValidatorMessage(validator, survey, "elements[1].constraints.rules[0].skipTo", "back references question foo");
    }

    @Test
    public void endSurveyRuleValidInConstraints() {
        SurveyRule rule = new SurveyRule.Builder().withOperator(SurveyRule.Operator.EQ).withValue(1)
                .withEndSurvey(Boolean.TRUE).build();
        updateSurveyWithRulesInConstraints(rule);

        Validate.entityThrowingException(validator, survey);
    }

    @Test
    public void noSkipToTargetInvalidInConstraints() throws Exception {
        SurveyRule rule = new SurveyRule.Builder().withOperator(SurveyRule.Operator.EQ).withValue(1)
                .withSkipToTarget("this_does_not_exist").build();
        updateSurveyWithRulesInConstraints(rule);

        assertValidatorMessage(validator, survey, "elements[0].constraints.rules[0].skipTo",
                "identifier doesn't exist: this_does_not_exist");
    }

    @Test
    public void endSurveyRuleValidInElement() {
        // This rule is a valid "end the survey" rule, and passes validation.
        SurveyRule rule = new SurveyRule.Builder().withOperator(SurveyRule.Operator.EQ).withValue(1)
                .withEndSurvey(Boolean.TRUE).build();
        updateSurveyWithAfterRulesInOneQuestion(rule);

        Validate.entityThrowingException(validator, survey);
    }

    @Test
    public void backreferenceSkipToTargetInvalidInElement() throws Exception {
        SurveyRule rule = new SurveyRule.Builder().withOperator(SurveyRule.Operator.EQ).withValue(1)
                .withSkipToTarget("foo").build();
        updateSurveyWithAfterRulesInOneQuestion(rule);
        
        // need a backreference to trigger failure 
        Survey tempSurvey = new TestSurvey(SurveySaveValidatorTest.class, false);
        SurveyQuestion question = ((TestSurvey)tempSurvey).getStringQuestion();
        question.setIdentifier("foo");
        survey.getElements().add(0, question);

        assertValidatorMessage(validator, survey, "elements[1].afterRules[0].skipTo", "back references question foo");
    }

    @Test
    public void noSkipToTargetInvalidInElement() throws Exception {
        SurveyRule rule = new SurveyRule.Builder().withOperator(SurveyRule.Operator.EQ).withValue(1)
                .withSkipToTarget("this_does_not_exist").build();
        updateSurveyWithAfterRulesInOneQuestion(rule);

        assertValidatorMessage(validator, survey, "elements[0].afterRules[0].skipTo",
                "identifier doesn't exist: this_does_not_exist");
    }
    
    @Test
    public void operatorsInInfoScreenRulesAreValidated() {
        Survey survey = new TestSurvey(SurveySaveValidatorTest.class, true);
        survey.setGuid("guid");
        SurveyInfoScreen info = SurveyInfoScreen.create();
        info.setTitle("title");
        info.setPrompt("prompt");
        info.setPromptDetail("prompt detail");
        info.setIdentifier("identifier");
        info.setGuid("guid");
        info.setBeforeRules(Lists.newArrayList(
                new SurveyRule.Builder().withValue("foo").withOperator(Operator.EQ).withEndSurvey(true).build()));
        info.setAfterRules(Lists.newArrayList(
                new SurveyRule.Builder().withValue("foo").withOperator(Operator.EQ).withEndSurvey(true).build()));
        survey.setElements(Lists.newArrayList(info));
        
        assertValidatorMessage(validator, survey, "elements[0].beforeRules[0].operator",
                "only 'any', 'all', and 'always' operators are valid for info screen rules");
        assertValidatorMessage(validator, survey, "elements[0].afterRules[0].operator",
                "only 'any', 'all', and 'always' operators are valid for info screen rules");
    }
    
    @Test
    public void anyOperatorsAllowedInInfoScreenRules() {
        Survey survey = new TestSurvey(SurveySaveValidatorTest.class, true);
        survey.setGuid("guid");
        SurveyInfoScreen info = SurveyInfoScreen.create();
        info.setTitle("title");
        info.setPrompt("prompt");
        info.setPromptDetail("prompt detail");
        info.setIdentifier("identifier");
        info.setGuid("guid");
        SurveyRule rule = new SurveyRule.Builder().withOperator(Operator.ANY).withDataGroups(Sets.newHashSet("foo"))
                .withEndSurvey(true).build();
        info.setBeforeRules(Lists.newArrayList(rule));
        info.setAfterRules(Lists.newArrayList(rule));
        survey.setElements(Lists.newArrayList(info));
        
        Validate.entityThrowingException(validator, survey);
    }
    
    @Test
    public void allOperatorsAllowedInInfoScreenRules() {
        Survey survey = new TestSurvey(SurveySaveValidatorTest.class, true);
        survey.setGuid("guid");
        SurveyInfoScreen info = SurveyInfoScreen.create();
        info.setTitle("title");
        info.setPrompt("prompt");
        info.setPromptDetail("prompt detail");
        info.setIdentifier("identifier");
        info.setGuid("guid");
        SurveyRule rule = new SurveyRule.Builder().withOperator(Operator.ALL).withDataGroups(Sets.newHashSet("foo"))
                .withEndSurvey(true).build();
        info.setBeforeRules(Lists.newArrayList(rule));
        info.setAfterRules(Lists.newArrayList(rule));
        survey.setElements(Lists.newArrayList(info));
        
        Validate.entityThrowingException(validator, survey);
    }
    
    @Test
    public void alwaysOperatorsAllowedInInfoScreenRules() {
        Survey survey = new TestSurvey(SurveySaveValidatorTest.class, true);
        survey.setGuid("guid");
        SurveyInfoScreen info = SurveyInfoScreen.create();
        info.setTitle("title");
        info.setPrompt("prompt");
        info.setPromptDetail("prompt detail");
        info.setIdentifier("identifier");
        info.setGuid("guid");
        SurveyRule rule = new SurveyRule.Builder().withOperator(Operator.ALWAYS).withEndSurvey(true).build();
        info.setBeforeRules(Lists.newArrayList(rule));
        info.setAfterRules(Lists.newArrayList(rule));
        survey.setElements(Lists.newArrayList(info));
        
        Validate.entityThrowingException(validator, survey);
    }
    
    @Test
    public void validatesAssignDataGroupMustAppearAlone() {
        String targetId = ((TestSurvey) survey).getStringQuestion().getIdentifier();

        SurveyRule rule = new SurveyRule.Builder().withOperator(SurveyRule.Operator.EQ).withValue(1)
                .withSkipToTarget(targetId).withAssignDataGroup("baz").build();
        updateSurveyWithAfterRulesInOneQuestion(rule);

        assertValidatorMessage(validator, survey, "elements[0].afterRules[0]",
                "must have one and only one action");
    }
    
    @Test
    public void validateAssignDataGroupAssignsRealDataGroup() {
        SurveyRule rule = new SurveyRule.Builder().withOperator(SurveyRule.Operator.EQ).withValue(1)
                .withAssignDataGroup("bar").build();
        updateSurveyWithAfterRulesInOneQuestion(rule);

        assertValidatorMessage(validator, survey, "elements[0].afterRules[0].assignDataGroup",
                "has a data group 'bar' that is not a valid data group: baz, foo");
    }
    
    @Test
    public void validatesEmptyDataGroupsForAnyOperator() {
        SurveyRule rule = new SurveyRule.Builder().withOperator(SurveyRule.Operator.ANY)
                .withDataGroups(Sets.newHashSet()).withEndSurvey(true).build();
        updateSurveyWithAfterRulesInOneQuestion(rule);

        assertValidatorMessage(validator, survey, "elements[0].afterRules[0].dataGroups",
                "should define one or more data groups");
    }
    
    @Test
    public void validatesInvalidDataGroupsForAnyOperator() {
        SurveyRule rule = new SurveyRule.Builder().withOperator(SurveyRule.Operator.ANY)
                .withDataGroups(Sets.newHashSet("notInStudy")).withEndSurvey(true).build();
        updateSurveyWithAfterRulesInOneQuestion(rule);

        assertValidatorMessage(validator, survey, "elements[0].afterRules[0].dataGroups",
                "contains data groups 'notInStudy' that are not valid data groups: baz, foo");
    }
    
    @Test
    public void validatesMixedValidityDataGroupsForAllOperator() {
        TreeSet<String> dataGroups = new TreeSet();
        dataGroups.add("foo");
        dataGroups.add("notInStudy");
        
        SurveyRule rule = new SurveyRule.Builder().withOperator(SurveyRule.Operator.ALL)
                .withDataGroups(dataGroups).withEndSurvey(true).build();

        updateSurveyWithAfterRulesInOneQuestion(rule);

        assertValidatorMessage(validator, survey, "elements[0].afterRules[0].dataGroups",
                "contains data groups 'foo, notInStudy' that are not valid data groups: baz, foo");
    }
    
    @Test
    public void beforeRulesAreValidated() {
        SurveyRule invalidSkipTo = new SurveyRule.Builder().withOperator(SurveyRule.Operator.EQ).withValue(1).withSkipToTarget("some-nonsense").build();
        SurveyRule tooManyActions = new SurveyRule.Builder().withOperator(SurveyRule.Operator.LE).withValue(1).withSkipToTarget("nonsense").withEndSurvey(true).build();

        updateSurveyWithBeforeRulesInOneQuestion(invalidSkipTo, tooManyActions);
        
        assertValidatorMessage(validator, survey, "elements[0].beforeRules[0].skipTo",
                "identifier doesn't exist: some-nonsense");
        assertValidatorMessage(validator, survey, "elements[0].beforeRules[1]",
                "must have one and only one action");
    }
    
    @Test
    public void cannotSetDisplayRuleAfterScreenDisplayed() {
        // Most rules work in most places, but these specifically refer to the current screen and 
        // must be evaluated before the screen is displayed in "after rules" is not useful.
        SurveyRule displayIf = new SurveyRule.Builder().withOperator(SurveyRule.Operator.EQ).withValue(1).withDisplayIf(true).build();
        SurveyRule displayUnless = new SurveyRule.Builder().withOperator(SurveyRule.Operator.LE).withValue(1).withDisplayUnless(true).build();
        
        updateSurveyWithAfterRulesInOneQuestion(displayIf, displayUnless);
        
        assertValidatorMessage(validator, survey, "elements[0].afterRules[0].displayIf",
                "specifies display after screen has been shown");
        assertValidatorMessage(validator, survey, "elements[0].afterRules[1].displayUnless",
                "specifies display after screen has been shown");
    }
    
    @Test
    public void validatesValueSetForRulesRequiringIt() {
        SurveyRule displayIf = new SurveyRule.Builder().withOperator(SurveyRule.Operator.EQ).withDisplayIf(true).build();
        
        updateSurveyWithBeforeRulesInOneQuestion(displayIf);
        
        assertValidatorMessage(validator, survey, "elements[0].beforeRules[0].value", "is required");
    }
    
    @Test
    public void valueMissingForNullValueOperatorsIsValid() {
        SurveyRule displayIf = new SurveyRule.Builder().withOperator(SurveyRule.Operator.DE).withDisplayIf(true).build();
        
        updateSurveyWithBeforeRulesInOneQuestion(displayIf);
        
        Validate.entityThrowingException(validator, survey);
    }
    
    @Test
    public void actionSkipToTargetValid() {
        SurveyRule rule = new SurveyRule.Builder().withSkipToTarget("afterTarget").withOperator(Operator.ALWAYS).build();
        
        updateSurveyWithAfterRulesInOneQuestion(rule);
        
        // need afterTarget to pass validation
        Survey tempSurvey = new TestSurvey(SurveySaveValidatorTest.class, false);
        SurveyQuestion question = ((TestSurvey)tempSurvey).getStringQuestion();
        question.setIdentifier("afterTarget");
        survey.getElements().add(question);
        
        Validate.entityThrowingException(validator, survey);
    }
    
    @Test
    public void actionEndSurveyValid() {
        SurveyRule rule = new SurveyRule.Builder().withEndSurvey(true).withOperator(Operator.ALWAYS).build();
        
        updateSurveyWithAfterRulesInOneQuestion(rule);
        
        Validate.entityThrowingException(validator, survey);
    }
    
    @Test
    public void actionAssignDataGroupValid() {
        SurveyRule rule = new SurveyRule.Builder().withAssignDataGroup("foo").withOperator(Operator.ALWAYS).build();
        
        updateSurveyWithAfterRulesInOneQuestion(rule);
        
        Validate.entityThrowingException(validator, survey);
    }
    
    @Test
    public void actionDisplayIfValid() {
        SurveyRule rule = new SurveyRule.Builder().withDisplayIf(true).withOperator(Operator.ALWAYS).build();
        
        updateSurveyWithBeforeRulesInOneQuestion(rule);
        
        Validate.entityThrowingException(validator, survey);
    }
    
    @Test
    public void actionDisplayUnlessValid() {
        SurveyRule rule = new SurveyRule.Builder().withDisplayUnless(true).withOperator(Operator.ALWAYS).build();
        
        updateSurveyWithBeforeRulesInOneQuestion(rule);
        
        Validate.entityThrowingException(validator, survey);
    }
    
    @Test
    public void validateNoAction() {
        SurveyRule rule = new SurveyRule.Builder().withOperator(Operator.ALWAYS).build();
        
        updateSurveyWithBeforeRulesInOneQuestion(rule);
        
        assertValidatorMessage(validator, survey, "elements[0].beforeRules[0]", "must have one and only one action");
    }
    
    @Test
    public void validateMultipleActions() {
        SurveyRule rule = new SurveyRule.Builder().withEndSurvey(true).withAssignDataGroup("foo")
                .withOperator(Operator.ALWAYS).build();
        
        updateSurveyWithBeforeRulesInOneQuestion(rule);
        
        assertValidatorMessage(validator, survey, "elements[0].beforeRules[0]", "must have one and only one action");
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
    
    @Test
    public void validatesYearMonthEarliestValueBeforeLatest() {
        survey = new TestSurvey(SurveySaveValidatorTest.class, false);
        
        YearMonthConstraints con = (YearMonthConstraints)TestSurvey.selectBy(survey, DataType.YEARMONTH).getConstraints(); 
        con.setEarliestValue(YearMonth.parse("2012-05"));
        con.setLatestValue(YearMonth.parse("2012-01"));
        
        assertValidatorMessage(validator, survey, "elements[9].constraints.earliestValue", "is after the latest value");
    }

    @Test
    public void validatesPostalCodeCountryCodeRequired() {
        survey = new TestSurvey(SurveySaveValidatorTest.class, false);

        PostalCodeConstraints pcc = (PostalCodeConstraints)TestSurvey.selectBy(survey, DataType.POSTALCODE).getConstraints();
        pcc.setCountryCode(null);
        
        assertValidatorMessage(validator, survey, "elements[10].constraints.postalCode", "is required");
    }
    
    private Survey updateSurveyWithBeforeRulesInOneQuestion(SurveyRule... rules) {
        survey = new TestSurvey(SurveySaveValidatorTest.class, false);
        
        SurveyQuestion question = ((TestSurvey) survey).getStringQuestion();
        question.setBeforeRules(Lists.newArrayList(rules));
        
        survey.setElements(Lists.newArrayList(question));
        return survey;
    }
    
    private Survey updateSurveyWithAfterRulesInOneQuestion(SurveyRule... rules) {
        survey = new TestSurvey(SurveySaveValidatorTest.class, false);
        
        SurveyQuestion question = ((TestSurvey) survey).getStringQuestion();
        question.setAfterRules(Lists.newArrayList(rules));
        
        survey.setElements(Lists.newArrayList(question));
        return survey;
    }
    
    private Survey updateSurveyWithRulesInConstraints(SurveyRule... rules) {
        survey = new TestSurvey(SurveySaveValidatorTest.class, false);
        
        SurveyQuestion question = ((TestSurvey) survey).getStringQuestion();
        question.getConstraints().getRules().addAll(Lists.newArrayList(rules));
        
        survey.setElements(Lists.newArrayList(question));
        return survey;
    }    
}

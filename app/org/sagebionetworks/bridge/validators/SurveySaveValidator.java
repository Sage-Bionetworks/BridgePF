package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.BridgeUtils.isEmpty;
import static org.sagebionetworks.bridge.BridgeUtils.COMMA_SPACE_JOINER;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.sagebionetworks.bridge.models.surveys.SurveyElementConstants.SURVEY_INFO_SCREEN_TYPE;
import static org.sagebionetworks.bridge.models.surveys.SurveyElementConstants.SURVEY_QUESTION_TYPE;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.google.common.collect.Sets;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.YearMonth;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.models.surveys.Constraints;
import org.sagebionetworks.bridge.models.surveys.DateConstraints;
import org.sagebionetworks.bridge.models.surveys.DateTimeConstraints;
import org.sagebionetworks.bridge.models.surveys.Image;
import org.sagebionetworks.bridge.models.surveys.MultiValueConstraints;
import org.sagebionetworks.bridge.models.surveys.NumericalConstraints;
import org.sagebionetworks.bridge.models.surveys.PostalCodeConstraints;
import org.sagebionetworks.bridge.models.surveys.StringConstraints;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.SurveyElement;
import org.sagebionetworks.bridge.models.surveys.SurveyInfoScreen;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestion;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestionOption;
import org.sagebionetworks.bridge.models.surveys.SurveyRule;
import org.sagebionetworks.bridge.models.surveys.UIHint;
import org.sagebionetworks.bridge.models.surveys.YearMonthConstraints;
import org.sagebionetworks.bridge.upload.UploadUtil;

@Component
public class SurveySaveValidator implements Validator {
    
    private final Set<String> dataGroups;
    
    public SurveySaveValidator(Set<String> dataGroups) {
        this.dataGroups = dataGroups;
    }
    
    @Override
    public boolean supports(Class<?> clazz) {
        return Survey.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object object, Errors errors) {
        Survey survey = (Survey)object;
        if (StringUtils.isBlank(survey.getName())) {
            errors.rejectValue("name", "is required");
        }
        if (StringUtils.isBlank(survey.getIdentifier())) {
            errors.rejectValue("identifier", "is required");
        }
        if (StringUtils.isBlank(survey.getStudyIdentifier())) {
            errors.rejectValue("studyIdentifier", "is required");
        }
        if (StringUtils.isBlank(survey.getGuid())) {
            errors.rejectValue("guid", "is required");
        }

        // Validate that no identifier has been duplicated.
        Set<String> foundIdentifiers = Sets.newHashSet();
        Set<String> foundGuids = Sets.newHashSet();
        for (int i=0; i < survey.getElements().size(); i++) {
            SurveyElement element = survey.getElements().get(i);
            errors.pushNestedPath("elements["+i+"]");
            if (SURVEY_QUESTION_TYPE.equals(element.getType())) {
                doValidateQuestion((SurveyQuestion)element, errors);
            } else if (SURVEY_INFO_SCREEN_TYPE.equals(element.getType())) {
                doValidateInfoScreen((SurveyInfoScreen)element, errors);
            }
            if (foundIdentifiers.contains(element.getIdentifier())) {
                errors.rejectValue("identifier", "exists in an earlier survey element");
            }
            if (element.getGuid() != null && foundGuids.contains(element.getGuid())) {
                errors.rejectValue("guid", "exists in an earlier survey element");
            }
            foundIdentifiers.add(element.getIdentifier());
            if (element.getGuid() != null) {
                foundGuids.add(element.getGuid());    
            }
            errors.popNestedPath();
        }
        // You can get all sorts of NPEs if survey is not valid and you look at the rules.
        // So don't.
        if (!errors.hasErrors()) {
            validateRules(errors, survey.getElements());
        }
    }
    private void doValidateQuestion(SurveyQuestion question, Errors errors) {
        String questionId = question.getIdentifier();
        if (isBlank(questionId)) {
            errors.rejectValue("identifier", "is required");
        } else if (!UploadUtil.isValidSchemaFieldName(questionId)) {
            errors.rejectValue("identifier", String.format(UploadUtil.INVALID_FIELD_NAME_ERROR_MESSAGE, questionId));
        }

        if (question.getUiHint() == null) {
            errors.rejectValue("uiHint", "is required");
        }
        if (isBlank(question.getPrompt())) {
            errors.rejectValue("prompt", "is required");
        }
        if (question.getConstraints() == null) {
            errors.rejectValue("constraints", "is required");
        } else {
            errors.pushNestedPath("constraints");
            doValidateConstraints(question, question.getConstraints(), errors);
            errors.popNestedPath();
        }
    }
    private void doValidateInfoScreen(SurveyInfoScreen screen, Errors errors) {
        if (isBlank(screen.getIdentifier())) {
            errors.rejectValue("identifier", "is required");
        }
        if (isBlank(screen.getTitle())) {
            errors.rejectValue("title", "is required");
        }
        if (isBlank(screen.getPrompt())) {
            errors.rejectValue("prompt", "is required");
        }
        if (screen.getImage() != null) {
            errors.pushNestedPath("image");
            Image image = screen.getImage();
            if (isBlank(image.getSource())) {
                errors.rejectValue("source", "is required");
            } else if (!image.getSource().startsWith("http://") && !image.getSource().startsWith("https://")) {
                errors.rejectValue("source", "must be a valid URL to an image");
            }
            if (image.getWidth() == 0) {
                errors.rejectValue("width", "is required");
            }
            if (image.getHeight() == 0) {
                errors.rejectValue("height", "is required");
            }
            errors.popNestedPath();
        }
    }

    private void validateRules(Errors errors, List<SurveyElement> elements) {
        Set<String> alreadySeenIdentifiers = Sets.newHashSet();
        
        for (int i=0; i < elements.size(); i++) {
            SurveyElement element = elements.get(i);
            String propertyPath = "elements["+i+"]";
            
            validateOneRuleSet(errors, element.getBeforeRules(), alreadySeenIdentifiers, propertyPath, "beforeRules");
            validateOneRuleSet(errors, element.getAfterRules(), alreadySeenIdentifiers, propertyPath, "afterRules");
            
            if (element instanceof SurveyQuestion) {
                SurveyQuestion question = (SurveyQuestion)element;
            
                validateOneRuleSet(errors, question.getConstraints().getRules(), alreadySeenIdentifiers,
                        propertyPath+".constraints", "rules");
            } else if (element instanceof SurveyInfoScreen) {
                // There are some additional constraints for information screens.
                validateOneRuleSetInInfoScreen(errors, element.getBeforeRules(), propertyPath, "beforeRules");
                validateOneRuleSetInInfoScreen(errors, element.getAfterRules(), propertyPath, "afterRules");
            }
            alreadySeenIdentifiers.add(element.getIdentifier());
        }        
        
        // Now verify that all skipToTarget identifiers actually exist
        for (int i=0; i < elements.size(); i++) {
            SurveyElement element = elements.get(i);
            String propertyPath = "elements["+i+"]";
            
            validateSkipToTargetExists(errors, element.getBeforeRules(), alreadySeenIdentifiers, propertyPath,
                    "beforeRules");
            validateSkipToTargetExists(errors, element.getAfterRules(), alreadySeenIdentifiers, propertyPath,
                    "afterRules");
            
            if (element instanceof SurveyQuestion) {
                SurveyQuestion question = (SurveyQuestion)element;
                
                validateSkipToTargetExists(errors, question.getConstraints().getRules(), alreadySeenIdentifiers,
                        propertyPath + ".constraints", "rules");
            }
        }
    }

    private void validateOneRuleSetInInfoScreen(Errors errors, List<SurveyRule> rules, String propertyPath, String fieldName) {
        if (rules != null) {
            for (int j=0; j < rules.size(); j++) {
                SurveyRule rule = rules.get(j);
                if (!SurveyRule.INFO_SCREEN_OPERATORS.contains(rule.getOperator())) {
                    errors.pushNestedPath(propertyPath);
                    errors.rejectValue(fieldName+"["+j+"].operator", "only 'any', 'all', and 'always' operators are valid for info screen rules");
                    errors.popNestedPath();
                }
            }
        }        
    }
    
    private void validateOneRuleSet(Errors errors, List<SurveyRule> rules, Set<String> alreadySeenIdentifiers,
            String propertyPath, String fieldName) {
        if (rules != null) {
            for (int j=0; j < rules.size(); j++) {
                SurveyRule rule = rules.get(j);
                String fieldPath = fieldName+"["+j+"]";
                
                errors.pushNestedPath(propertyPath);
                
                if (!hasOneAction(rule)) {
                    errors.rejectValue(fieldPath, "must have one and only one action");
                }
                if (assignedDataGroupDoesNotExist(rule)) {
                    errors.rejectValue(fieldPath + ".assignDataGroup", "has a data group '" + rule.getAssignDataGroup()
                            + "' that is not a valid data group: " + COMMA_SPACE_JOINER.join(dataGroups));            
                }
                if (afterRuleControlsDisplay(fieldPath, rule)) {
                    if (rule.getDisplayIf() == Boolean.TRUE) {
                        errors.rejectValue(fieldPath + ".displayIf", "specifies display after screen has been shown");    
                    } else if (rule.getDisplayUnless() == Boolean.TRUE) {
                        errors.rejectValue(fieldPath + ".displayUnless", "specifies display after screen has been shown");
                    }
                }
                if (skipToBackReferencesQuestion(alreadySeenIdentifiers, rule)) {
                    errors.rejectValue(fieldPath + ".skipTo", "back references question " + rule.getSkipToTarget());
                }
                // Split rules by their operator, the operators determines what data is being tested. 
                // Only validate fields that are relevant for the operator, ignore the other.
                if (SurveyRule.SET_OPERATORS.contains(rule.getOperator())) {
                    // tests against data groups
                    if (isEmpty(rule.getDataGroups())) {
                        errors.rejectValue(fieldPath + ".dataGroups", "should define one or more data groups");
                    } else if (!dataGroups.containsAll(rule.getDataGroups())) {
                        errors.rejectValue(fieldPath + ".dataGroups",
                            "contains data groups '" + COMMA_SPACE_JOINER.join(rule.getDataGroups())
                                    + "' that are not valid data groups: " + COMMA_SPACE_JOINER.join(dataGroups));
                    }
                } else if (valueMissingForOperator(rule)) {
                    errors.rejectValue(fieldPath + ".value", "is required");
                }
                errors.popNestedPath();
            }
        }
    }

    private boolean afterRuleControlsDisplay(String propertyPath, SurveyRule rule) {
        return propertyPath.startsWith("afterRules") && 
            (rule.getDisplayIf() == Boolean.TRUE || rule.getDisplayUnless() == Boolean.TRUE);
    }
    
    private boolean valueMissingForOperator(SurveyRule rule) {
        return !SurveyRule.NULL_VALUE_OPERATORS.contains(rule.getOperator()) && rule.getValue() == null;
    }

    private boolean skipToBackReferencesQuestion(Set<String> alreadySeenIdentifiers, SurveyRule rule) {
        return rule.getSkipToTarget() != null && alreadySeenIdentifiers.contains(rule.getSkipToTarget());
    }

    private boolean assignedDataGroupDoesNotExist(SurveyRule rule) {
        return rule.getAssignDataGroup() != null && !dataGroups.contains(rule.getAssignDataGroup());
    }

    private boolean hasOneAction(SurveyRule rule) {
        int actionCount = 0;
        if (rule.getSkipToTarget() != null) {
            actionCount++;
        }
        if (rule.getEndSurvey() != null) {
            actionCount++;
        }
        if (rule.getAssignDataGroup() != null) {
            actionCount++;
        }
        if (rule.getDisplayIf() != null) {
            actionCount++;
        }
        if (rule.getDisplayUnless() != null) {
            actionCount++;
        }
        return actionCount == 1;
    }
    
    private void validateSkipToTargetExists(Errors errors, List<SurveyRule> rules, Set<String> alreadySeenIdentifiers,
            String propertyPath, String fieldName) {
        if (rules != null) {
            for (int j=0; j < rules.size(); j++) {
                SurveyRule rule = rules.get(j);
                
                if (rule.getSkipToTarget() != null) {
                    if (!skipToBackReferencesQuestion(alreadySeenIdentifiers, rule)) {
                        errors.pushNestedPath(propertyPath);
                        errors.rejectValue(fieldName+"["+j+"].skipTo", "identifier doesn't exist: " + rule.getSkipToTarget());
                        errors.popNestedPath();
                    }
                }
            }
        }
    }
    
    private void doValidateConstraints(SurveyQuestion question, Constraints con, Errors errors) {
        if (con.getDataType() == null) {
            errors.rejectValue("dataType", "is required");
            return;
        }
        UIHint hint = question.getUiHint();
        if (hint == null) {
            return; // will have been validated above, skip this
        }
        if (!con.getSupportedHints().contains(hint)) {
            errors.rejectValue("dataType", String.format("'%s' doesn't match the UI hint of '%s'",
                    con.getDataType().name().toLowerCase(), hint.name().toLowerCase()));
        } else if (con instanceof MultiValueConstraints) {
            doValidateConstraintsType(errors, hint, (MultiValueConstraints)con);
        } else if (con instanceof StringConstraints) {
            doValidateConstraintsType(errors, hint, (StringConstraints)con);
        } else if (con instanceof DateConstraints) {
            doValidateConstraintsType(errors, hint, (DateConstraints)con);
        } else if (con instanceof DateTimeConstraints) {
            doValidateConstraintsType(errors, hint, (DateTimeConstraints)con);
        } else if (con instanceof YearMonthConstraints) {
            doValidateConstraintsType(errors, hint, (YearMonthConstraints)con);
        } else if (con instanceof PostalCodeConstraints) {
            doValidateConstraintsType(errors, hint, (PostalCodeConstraints)con);
        } else if (con instanceof NumericalConstraints) {
            doValidateConstraintsType(errors, hint, (NumericalConstraints)con);
        }
    }

    private void doValidateConstraintsType(Errors errors, UIHint hint, MultiValueConstraints mcon) {
        String hintName = hint.name().toLowerCase();

        if ((mcon.getAllowMultiple() || !mcon.getAllowOther()) && MultiValueConstraints.OTHER_ALWAYS_ALLOWED.contains(hint)) {
            errors.rejectValue("uiHint", "'"+hintName+"' is only valid when multiple = false and other = true");
        } else if (mcon.getAllowMultiple() && MultiValueConstraints.ONE_ONLY.contains(hint)) {
            errors.rejectValue("uiHint", "allows multiples but the '"+hintName+"' UI hint doesn't gather more than one answer");
        } else if (!mcon.getAllowMultiple() && MultiValueConstraints.MANY_ONLY.contains(hint)) {
            errors.rejectValue("uiHint", "doesn't allow multiples but the '"+hintName+"' UI hint gathers more than one answer");
        }

        // must have an enumeration (list of question options)
        List<SurveyQuestionOption> optionList = mcon.getEnumeration();
        if (optionList != null && !optionList.isEmpty()) {
            // validate each option
            int numOptions = optionList.size();
            Set<String> valueSet = new HashSet<>();
            Set<String> dupeSet = new TreeSet<>();
            for (int i = 0; i < numOptions; i++) {
                errors.pushNestedPath("enumeration[" + i + "]");

                // must have a label
                SurveyQuestionOption oneOption = optionList.get(i);
                if (StringUtils.isBlank(oneOption.getLabel())) {
                    errors.rejectValue("label", "is required");
                }

                String optionValue = oneOption.getValue();
                if (!UploadUtil.isValidAnswerChoice(optionValue)) {
                    errors.rejectValue("value",
                            String.format(UploadUtil.INVALID_ANSWER_CHOICE_ERROR_MESSAGE, optionValue));
                }

                // record values seen so far
                if (!valueSet.contains(optionValue)) {
                    valueSet.add(optionValue);
                } else {
                    dupeSet.add(optionValue);
                }

                errors.popNestedPath();
            }

            // values must be unique
            if (!dupeSet.isEmpty()) {
                errors.rejectValue("enumeration", "must have unique values");
            }
        }
    }

    private void doValidateConstraintsType(Errors errors, UIHint hint, StringConstraints con) {
        if (StringUtils.isNotBlank(con.getPattern())) {
            try {
                Pattern.compile(con.getPattern());
            } catch (PatternSyntaxException exception) {
                errors.rejectValue("pattern", "is not a valid regular expression: "+con.getPattern());
            }
            if (StringUtils.isBlank(con.getPatternErrorMessage())) {
                errors.rejectValue("patternErrorMessage", "is required if pattern is defined");
            }
        }
        // It's okay to provide the error message without a pattern... it's not useful, but it's allowed
        Integer min = con.getMinLength();
        Integer max = con.getMaxLength();
        if (min != null && max != null) {
            if (min > max) {
                errors.rejectValue("minLength", "is longer than the maxLength");
            }
        }
    }

    private void doValidateConstraintsType(Errors errors, UIHint hint, DateConstraints con) {
        LocalDate earliestDate = con.getEarliestValue();
        LocalDate latestDate = con.getLatestValue();
        if (earliestDate != null && latestDate != null) {
            if (latestDate.isBefore(earliestDate)) {
                errors.rejectValue("earliestValue", "is after the latest value");
            }
        }
    }

    private void doValidateConstraintsType(Errors errors, UIHint hint, DateTimeConstraints con) {
        DateTime earliestDate = con.getEarliestValue();
        DateTime latestDate = con.getLatestValue();
        if (earliestDate != null && latestDate != null) {
            if (latestDate.isBefore(earliestDate)) {
                errors.rejectValue("earliestValue", "is after the latest value");
            }
        }
    }
    
    private void doValidateConstraintsType(Errors errors, UIHint hint, YearMonthConstraints con) {
        YearMonth earliestDate = con.getEarliestValue();
        YearMonth latestDate = con.getLatestValue();
        if (earliestDate != null && latestDate != null) {
            if (latestDate.isBefore(earliestDate)) {
                errors.rejectValue("earliestValue", "is after the latest value");
            }
        }
    }
    
    private void doValidateConstraintsType(Errors errors, UIHint hint, PostalCodeConstraints con) {
        if (con.getCountryCode() == null) {
            errors.rejectValue("postalCode", "is required");
        }
    }

    private void doValidateConstraintsType(Errors errors, UIHint hint, NumericalConstraints con) {
        Double min = con.getMinValue();
        Double max = con.getMaxValue();
        if (min != null && max != null) {
            if (max < min) {
                errors.rejectValue("minValue", "is greater than the maxValue");
            }
            double diff = max-min;
            if (con.getStep() != null && con.getStep() > diff) {
                errors.rejectValue("step", "is larger than the range of allowable values");
            }
        } else if ( UIHint.SELECT == hint || UIHint.SLIDER == hint) {
            if (min == null) {
                errors.rejectValue("minValue", "is required for " + hint.name().toLowerCase());
            }
            if (max == null) {
                errors.rejectValue("maxValue", "is required for " + hint.name().toLowerCase());
            }
        }
    }
}

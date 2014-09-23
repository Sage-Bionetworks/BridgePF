package org.sagebionetworks.bridge.surveys;

import java.util.List;

import org.sagebionetworks.bridge.dynamodb.DynamoSurvey;
import org.sagebionetworks.bridge.dynamodb.DynamoSurveyQuestion;
import org.sagebionetworks.bridge.models.surveys.DataType;
import org.sagebionetworks.bridge.models.surveys.MultiValueConstraints;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestion;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestionOption;
import org.sagebionetworks.bridge.models.surveys.UIHint;

import com.google.common.collect.Lists;

/**
 * Issues:
 *  - none of the text formatting is preserved
 *  - there's a long pre-amble which is duplicated for each question
 *  - actually the answers are duplicated as well, but I think that's okay to start
 */
public class PDQ8Survey extends DynamoSurvey {

    private List<SurveyQuestion> questions = Lists.newArrayList();
    
    public PDQ8Survey() {
        setName("Parkinson’s Disease Quality of Life Questionnaire");
        setIdentifier("PDQ8");
        setStudyKey("neurod");
        questions.add(question1);
        questions.add(question2);
        questions.add(question3);
        questions.add(question4);
        questions.add(question5);
        questions.add(question6);
        questions.add(question7);
        questions.add(question8);
        setQuestions(questions);
    }
    
    private static final String PREAMBLE = "Due to having Parkinson’s disease, how often during the last month have you ";
    private static final List<SurveyQuestionOption> options = Lists.newArrayList(
        new SurveyQuestionOption("Never", 0),
        new SurveyQuestionOption("Occassionally", 1),
        new SurveyQuestionOption("Sometimes", 2),
        new SurveyQuestionOption("Often", 3),
        new SurveyQuestionOption("Always (or cannot do at all)", 4)
    );
    private static final MultiValueConstraints constraints = new MultiValueConstraints() {
        {
            setDataType(DataType.INTEGER);
            setEnumeration(options);
        }
    };
    private SurveyQuestion question1 = new DynamoSurveyQuestion() {
        {
            setPrompt(PREAMBLE + "had difficulty getting around in public?");
            setIdentifier("mobility");
            setConstraints(constraints);
            setUiHint(UIHint.RADIOBUTTON);
        }
    };
    private SurveyQuestion question2 = new DynamoSurveyQuestion() {
        {
            setPrompt(PREAMBLE + "had difficulty dressing yourself?");
            setIdentifier("dressing");
            setConstraints(constraints);
            setUiHint(UIHint.RADIOBUTTON);
        }
    };
    private SurveyQuestion question3 = new DynamoSurveyQuestion() {
        {
            setPrompt(PREAMBLE + "felt depressed?");
            setIdentifier("depression");
            setConstraints(constraints);
            setUiHint(UIHint.RADIOBUTTON);
        }
    };
    private SurveyQuestion question4 = new DynamoSurveyQuestion() {
        {
            setPrompt(PREAMBLE + "had problems with your close personal relationships?");
            setIdentifier("relationships");
            setConstraints(constraints);
            setUiHint(UIHint.RADIOBUTTON);
        }
    };
    private SurveyQuestion question5 = new DynamoSurveyQuestion() {
        {
            setPrompt(PREAMBLE + "had problems with your concentration, e.g. when reading or watching TV?");
            setIdentifier("concentration");
            setConstraints(constraints);
            setUiHint(UIHint.RADIOBUTTON);
        }
    };
    private SurveyQuestion question6 = new DynamoSurveyQuestion() {
        {
            setPrompt(PREAMBLE + "felt unable to communicate with people properly?");
            setIdentifier("communication");
            setConstraints(constraints);
            setUiHint(UIHint.RADIOBUTTON);
        }
    };
    private SurveyQuestion question7 = new DynamoSurveyQuestion() {
        {
            setPrompt(PREAMBLE + "had painful muscle cramps or spasms?");
            setIdentifier("pain");
            setConstraints(constraints);
            setUiHint(UIHint.RADIOBUTTON);
        }
    };
    private SurveyQuestion question8 = new DynamoSurveyQuestion() {
        {
            setPrompt(PREAMBLE + "felt embarrassed in public due to having Parkinson’s disease?");
            setIdentifier("embarassment");
            setConstraints(constraints);
            setUiHint(UIHint.RADIOBUTTON);
        }
    };
}

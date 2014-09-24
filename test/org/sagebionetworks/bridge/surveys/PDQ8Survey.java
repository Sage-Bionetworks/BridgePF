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
        setQuestions(questions);
        makeQuestion("had difficulty getting around in public?", "mobility");
        makeQuestion("had difficulty dressing yourself?","dressing");
        makeQuestion("felt depressed?", "depression");
        makeQuestion("had problems with your close personal relationships?", "relationships");
        makeQuestion("had problems with your concentration, e.g. when reading or watching TV?", "concentration");
        makeQuestion("felt unable to communicate with people properly?", "communication");
        makeQuestion("had painful muscle cramps or spasms?", "pain");
        makeQuestion("felt embarrassed in public due to having Parkinson’s disease?", "embarassment");
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
    private void makeQuestion(final String prompt, final String id) {
        questions.add(new DynamoSurveyQuestion() {{
            setPrompt(PREAMBLE + prompt);
            setIdentifier(id);
            setConstraints(constraints);
            setUiHint(UIHint.SLIDER);
        }});
    }
}

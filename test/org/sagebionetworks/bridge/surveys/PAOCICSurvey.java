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

public class PAOCICSurvey extends DynamoSurvey {

    private List<SurveyQuestion> questions = Lists.newArrayList();
    
    public PAOCICSurvey() {
        setName("Patient Assessment of Chronic Illness Care Survey");
        setIdentifier("PAOCICS");
        setStudyKey("neurod");
        setQuestions(questions);
        
        makeQuestion("Asked for my ideas when we made a treatment plan.", "asked_for_ideas");
        makeQuestion("Given choices about treatment to think about.", "given_choices");
        makeQuestion("Asked to talk about any problems with my medicines or their effects.", "medicine_problems");
        makeQuestion("Given a written list of things I should do to improve my health.", "written_instructions");
        makeQuestion("Shown how what I did to take care of my illness influenced my condition.", "self_care_instruction");
        makeQuestion("Asked to talk about my goals in caring for my illness.", "self_care");
        makeQuestion("Helped to set specific goals to improve my eating or exercise.", "setting_goals");
        makeQuestion("Given a copy of my treatment plan.", "treatment_plan_copy");
        makeQuestion("Encouraged to go to a specific group or class to help me cope with my chronic illness.", "group_support");
        makeQuestion("Asked questions, either directly or on a survey, about my health habits.", "asked_questions");
        makeQuestion("Sure that my doctor or nurse thought about my values and my traditions when they recommended treatments to me.", "culturally_sensitive");
        makeQuestion("Helped to make a treatment plan that I could do in my daily life.", "treatment_plan_help");
        makeQuestion("Helped to plan ahead so I could take care of my illness even in hard times.", "contingency_planning");
        makeQuestion("Contacted after a visit to see how things were going.", "follow_up_visit");
        makeQuestion("Encouraged to attend programs in the community that could help me.", "program_support");
        makeQuestion("Referred to a dietitian, health educator, or counselor.", "referrals");
        makeQuestion("Told how my visits with other types of doctors, like the eye doctor or surgeon, helped my treatment.", "other_treatment_assessment");
        makeQuestion("Asked how my visits with other doctors were going.", "doctor_visit_assessment");
    }
    
    private static final String PREAMBLE = "Over the past 6 months, when receiving medical care for my chronic illness, I was: ";
    private static final List<SurveyQuestionOption> options = Lists.newArrayList(
        new SurveyQuestionOption("Almost Never", 1),
        new SurveyQuestionOption("Generally Not", 2),
        new SurveyQuestionOption("Sometimes", 3),
        new SurveyQuestionOption("Most of the Time", 4),
        new SurveyQuestionOption("Almost Always", 5)
    );
    private static final MultiValueConstraints constraints = new MultiValueConstraints() {{
        setDataType(DataType.INTEGER);
        setEnumeration(options);
    }};
    private void makeQuestion(final String prompt, final String id) {
        questions.add(new DynamoSurveyQuestion() {{
            setPrompt(PREAMBLE + prompt);
            setIdentifier(id);
            setConstraints(constraints);
            setUiHint(UIHint.SLIDER);
        }});
    }
    
}

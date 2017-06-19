package org.sagebionetworks.bridge.dynamodb;

import java.util.List;

import org.sagebionetworks.bridge.models.surveys.SurveyRule;

import com.fasterxml.jackson.core.type.TypeReference;

public class SurveyRuleListMarshaller extends ListMarshaller<SurveyRule> {

    private static final TypeReference<List<SurveyRule>> STRING_LIST_TYPE =
            new TypeReference<List<SurveyRule>>() {};

    /** {@inheritDoc} */
    @Override
    public TypeReference<List<SurveyRule>> getTypeReference() {
        return STRING_LIST_TYPE;
    }

}

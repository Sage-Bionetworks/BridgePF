package org.sagebionetworks.bridge.dynamodb;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Resource;

import org.sagebionetworks.bridge.dao.StudyConsentDao;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsent;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

import com.amazonaws.services.dynamodbv2.datamodeling.QueryResultPage;
import org.springframework.stereotype.Component;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;

@Component
public class DynamoStudyConsentDao implements StudyConsentDao {

    private DynamoDBMapper mapper;

    @Resource(name = "studyConsentDdbMapper")
    final void setDdbMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }
    
    @Override
    public StudyConsent addConsent(SubpopulationGuid subpopGuid, String storagePath, long createdOn) {
        DynamoStudyConsent1 consent = new DynamoStudyConsent1();
        consent.setSubpopulationGuid(subpopGuid.getGuid());
        consent.setCreatedOn(createdOn);
        consent.setStoragePath(storagePath);
        mapper.save(consent);
        return consent;
    }

    @Override
    public StudyConsent getMostRecentConsent(SubpopulationGuid subpopGuid) {
        DynamoStudyConsent1 hashKey = new DynamoStudyConsent1();
        hashKey.setSubpopulationGuid(subpopGuid.getGuid());
        DynamoDBQueryExpression<DynamoStudyConsent1> queryExpression = 
                new DynamoDBQueryExpression<DynamoStudyConsent1>()
                .withHashKeyValues(hashKey)
                .withScanIndexForward(false)
                .withLimit(1);
        QueryResultPage<DynamoStudyConsent1> resultPage = mapper.queryPage(DynamoStudyConsent1.class, queryExpression);
        List<DynamoStudyConsent1> page = resultPage.getResults();
        if (page.isEmpty()) {
            return null;
        }
        return page.iterator().next();
    }
    
    @Override
    public StudyConsent getConsent(SubpopulationGuid subpopGuid, long consentCreatedOn) {
        DynamoStudyConsent1 consent = new DynamoStudyConsent1();
        consent.setSubpopulationGuid(subpopGuid.getGuid());
        consent.setCreatedOn(consentCreatedOn);
        return mapper.load(consent);
    }

    @Override
    public List<StudyConsent> getConsents(SubpopulationGuid subpopGuid) {
        DynamoStudyConsent1 hashKey = new DynamoStudyConsent1();
        hashKey.setSubpopulationGuid(subpopGuid.getGuid());
        DynamoDBQueryExpression<DynamoStudyConsent1> queryExpression = 
                new DynamoDBQueryExpression<DynamoStudyConsent1>()
                .withHashKeyValues(hashKey)
                .withScanIndexForward(false);
        PaginatedQueryList<DynamoStudyConsent1> consents = mapper.query(DynamoStudyConsent1.class, queryExpression);
        List<StudyConsent> results = new ArrayList<StudyConsent>();
        for (DynamoStudyConsent1 consent : consents) {
            results.add(consent);
        }
        return results;
    }

    /** {@inheritDoc} */
    @Override
    public void deleteConsentPermanently(StudyConsent consent) {
        mapper.delete(consent);
    }
}

package org.sagebionetworks.bridge.dynamodb;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import org.sagebionetworks.bridge.dao.AppConfigElementDao;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.models.VersionHolder;
import org.sagebionetworks.bridge.models.appconfig.AppConfigElement;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.springframework.stereotype.Component;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

@Component
public class DynamoAppConfigElementDao implements AppConfigElementDao {
    static final String STUDY_ID_INDEX_NAME = "studyId-index";
    
    private DynamoDBMapper mapper;
    
    @Resource(name = "appConfigElementDdbMapper")
    final void setMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }
    
    @Override
    public List<AppConfigElement> getMostRecentElements(StudyIdentifier studyId, boolean includeDeleted) {
        DynamoAppConfigElement key = new DynamoAppConfigElement();
        key.setStudyId(studyId.getIdentifier());

        DynamoDBQueryExpression<DynamoAppConfigElement> query = new DynamoDBQueryExpression<DynamoAppConfigElement>()
                .withIndexName(STUDY_ID_INDEX_NAME)
                .withHashKeyValues(key)
                .withConsistentRead(false)
                .withScanIndexForward(false);
        
        List<DynamoAppConfigElement> elementIndices = mapper.query(DynamoAppConfigElement.class, query);
        
        Map<String,AppConfigElement> versionMap = Maps.newHashMap();
        
        Map<String, List<Object>> resultMap = mapper.batchLoad(elementIndices);
        for (List<Object> resultList : resultMap.values()) {
            for (Object oneResult : resultList) {
                if (!(oneResult instanceof DynamoAppConfigElement)) {
                    // This should never happen, but just in case.
                    throw new BridgeServiceException("DynamoDB returned objects of type " +
                            oneResult.getClass().getName() + " instead of DynamoAppConfigElement");
                }
                
                DynamoAppConfigElement oneElement = (DynamoAppConfigElement)oneResult;
                if (!includeDeleted && oneElement.isDeleted()) {
                    continue;
                }
                AppConfigElement existingElement = versionMap.get(oneElement.getId());
                Long existingRevision = (existingElement == null) ? null : existingElement.getRevision();
                if (existingRevision == null || oneElement.getRevision() > existingRevision) {
                    versionMap.put(oneElement.getId(), oneElement);
                }
            }
        }
        List<AppConfigElement> elements = Lists.newArrayList(versionMap.values());
        Collections.sort(elements, Comparator.comparing(AppConfigElement::getId));
        return elements; 
    }

    @Override
    public AppConfigElement getMostRecentElement(StudyIdentifier studyId, String id) {
        DynamoAppConfigElement key = new DynamoAppConfigElement();
        key.setStudyId(studyId.getIdentifier());
        key.setId(id);        

        DynamoDBQueryExpression<DynamoAppConfigElement> query = new DynamoDBQueryExpression<DynamoAppConfigElement>()
                .withHashKeyValues(key);
        excludeDeleted(query);
        query.setScanIndexForward(false);
        query.setLimit(1);
        
        PaginatedQueryList<DynamoAppConfigElement> results = mapper.query(DynamoAppConfigElement.class, query);
        
        return (results.isEmpty()) ? null : results.get(0);
    }

    @Override
    public List<AppConfigElement> getElementRevisions(StudyIdentifier studyId, String id, boolean includeDeleted) {
        DynamoAppConfigElement key = new DynamoAppConfigElement();
        key.setStudyId(studyId.getIdentifier());
        key.setId(id);        

        DynamoDBQueryExpression<DynamoAppConfigElement> query = new DynamoDBQueryExpression<DynamoAppConfigElement>()
                .withHashKeyValues(key)
                .withScanIndexForward(false);
        if (!includeDeleted) {
            excludeDeleted(query);
        }
        PaginatedQueryList<DynamoAppConfigElement> results = mapper.query(DynamoAppConfigElement.class, query);
        
        return results.stream().collect(Collectors.toList());
    }
    
    private void excludeDeleted(DynamoDBQueryExpression<DynamoAppConfigElement> query) {
        query.withQueryFilterEntry("deleted", new Condition()
                .withComparisonOperator(ComparisonOperator.EQ)
                .withAttributeValueList(new AttributeValue().withBOOL(Boolean.FALSE)));
    }

    @Override
    public AppConfigElement getElementRevision(StudyIdentifier studyId, String id, long revision) {
        AppConfigElement key = new DynamoAppConfigElement();
        key.setStudyId(studyId.getIdentifier());
        key.setId(id);
        key.setRevision(revision);
        
        return mapper.load(key);
    }

    @Override
    public VersionHolder saveElementRevision(AppConfigElement element) {
        try {
            mapper.save(element);
            return new VersionHolder(element.getVersion());
        } catch(ConditionalCheckFailedException e) {
            throw new ConcurrentModificationException(element);
        }
    }

    @Override
    public void deleteElementRevisionPermanently(StudyIdentifier studyId, String id, long revision) {
        AppConfigElement element = getElementRevision(studyId, id, revision);
        if (element != null) {
            try {
                mapper.delete(element);
            } catch(ConditionalCheckFailedException e) {
                throw new ConcurrentModificationException(element);
            }
        }
    }

}

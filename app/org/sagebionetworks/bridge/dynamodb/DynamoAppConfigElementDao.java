package org.sagebionetworks.bridge.dynamodb;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import org.sagebionetworks.bridge.dao.AppConfigElementDao;
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
import com.google.common.collect.Maps;
import com.newrelic.agent.deps.com.google.common.collect.Lists;

@Component
public class DynamoAppConfigElementDao implements AppConfigElementDao {
    private DynamoDBMapper mapper;
    private DynamoIndexHelper indexHelper;
    
    @Resource(name = "appConfigElementDdbMapper")
    final void setMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }
    
    @Resource(name = "appConfigElementStudyIndex")
    final void setIndexHelper(DynamoIndexHelper indexHelper) {
        this.indexHelper = indexHelper;
    }
    
    @Override
    public List<AppConfigElement> getMostRecentElements(StudyIdentifier studyId, boolean includeDeleted) {
        DynamoAppConfigElement key = new DynamoAppConfigElement();
        key.setStudyId(studyId.getIdentifier());
        
        // This only retrieves the keys of these elements and the deleted flag, which is included in the GSI
        List<DynamoAppConfigElement> elements = indexHelper.queryKeys(DynamoAppConfigElement.class, "studyId",
                studyId.getIdentifier(), null);
        
        // Looking for the highest revision of each ID
        Map<String,Long> versionMap = Maps.newHashMap();
        for (DynamoAppConfigElement oneElement : elements) {
            if (includeDeleted || !oneElement.isDeleted()) {
                Long existingRevision = versionMap.get(oneElement.getId());
                if (existingRevision == null || oneElement.getRevision() > existingRevision) {
                    versionMap.put(oneElement.getId(), oneElement.getRevision());
                }
            }
        }
        
        List<AppConfigElement> mostRecentElements = Lists.newArrayListWithCapacity(versionMap.size());
        for (Map.Entry<String,Long> entry : versionMap.entrySet()) {
            DynamoAppConfigElement oneKey = new DynamoAppConfigElement();
            oneKey.setKey(studyId.getIdentifier() + ":" + entry.getKey());
            oneKey.setRevision(entry.getValue());
            
            DynamoAppConfigElement appConfigElement = mapper.load(oneKey);
            mostRecentElements.add(appConfigElement);
        }
        return mostRecentElements;
    }

    @Override
    public AppConfigElement getMostRecentlyPublishedElement(StudyIdentifier studyId, String id) {
        DynamoAppConfigElement key = new DynamoAppConfigElement();
        key.setKey(studyId.getIdentifier() + ":" + id);

        DynamoDBQueryExpression<DynamoAppConfigElement> query = new DynamoDBQueryExpression<DynamoAppConfigElement>()
                .withHashKeyValues(key);
        query.withQueryFilterEntry("deleted", new Condition()
            .withComparisonOperator(ComparisonOperator.NE)
            .withAttributeValueList(new AttributeValue().withN("1")));
        query.withQueryFilterEntry("published", new Condition()
                .withComparisonOperator(ComparisonOperator.NE)
                .withAttributeValueList(new AttributeValue().withN("0")));
        query.setScanIndexForward(false);
        
        PaginatedQueryList<DynamoAppConfigElement> results = mapper.query(DynamoAppConfigElement.class, query);
        
        return (results.isEmpty()) ? null : results.iterator().next();
    }

    @Override
    public List<AppConfigElement> getElementRevisions(StudyIdentifier studyId, String id, boolean includeDeleted) {
        DynamoAppConfigElement key = new DynamoAppConfigElement();
        key.setKey(studyId.getIdentifier() + ":" + id);

        DynamoDBQueryExpression<DynamoAppConfigElement> query = new DynamoDBQueryExpression<DynamoAppConfigElement>()
                .withHashKeyValues(key);
        if (!includeDeleted) {
            query.withQueryFilterEntry("deleted", new Condition()
                .withComparisonOperator(ComparisonOperator.NE)
                .withAttributeValueList(new AttributeValue().withN("1")));
        }
        PaginatedQueryList<DynamoAppConfigElement> results = mapper.query(DynamoAppConfigElement.class, query);
        
        return results.stream().collect(Collectors.toList());
    }

    @Override
    public AppConfigElement getElementRevision(StudyIdentifier studyId, String id, long revision) {
        AppConfigElement key = new DynamoAppConfigElement();
        key.setKey(studyId.getIdentifier() + ":" + id);
        key.setRevision(revision);
        
        return mapper.load(key);
    }

    @Override
    public VersionHolder saveElementRevision(AppConfigElement element) {
        mapper.save(element);
        return new VersionHolder(element.getVersion());
    }

    @Override
    public void deleteElementRevisionPermanently(StudyIdentifier studyId, String id, long revision) {
        AppConfigElement key = new DynamoAppConfigElement();
        key.setKey(studyId.getIdentifier() + ":" + id);
        key.setRevision(revision);
        
        mapper.delete(key);
    }

}

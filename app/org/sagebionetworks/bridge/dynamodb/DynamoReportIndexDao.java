package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;

import javax.annotation.Resource;

import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.ReportIndexDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.ReportTypeResourceList;
import org.sagebionetworks.bridge.models.reports.ReportDataKey;
import org.sagebionetworks.bridge.models.reports.ReportIndex;
import org.sagebionetworks.bridge.models.reports.ReportType;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBSaveExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.amazonaws.services.dynamodbv2.model.ExpectedAttributeValue;
import com.google.common.collect.ImmutableMap;

@Component
public class DynamoReportIndexDao implements ReportIndexDao {

    private static final DynamoDBSaveExpression DOES_NOT_EXIST_EXPRESSION = new DynamoDBSaveExpression()
            .withExpected(new ImmutableMap.Builder<String,ExpectedAttributeValue>()
                    .put("key", new ExpectedAttributeValue(false))
                    .put("identifier", new ExpectedAttributeValue(false)).build());
    
    private DynamoDBMapper mapper;

    @Resource(name = "reportIndexMapper")
    final void setReportIndexMapper(DynamoDBMapper reportIndexMapper) {
        this.mapper = reportIndexMapper;
    }
    
    @Override
    public ReportIndex getIndex(ReportDataKey key) {
        checkNotNull(key);
        
        DynamoReportIndex hashKey = new DynamoReportIndex();
        hashKey.setKey(key.getIndexKeyString());
        hashKey.setIdentifier(key.getIdentifier());
        
        return mapper.load(hashKey);
    }

    @Override
    public void addIndex(ReportDataKey key) {
        checkNotNull(key);
        
        DynamoReportIndex index = new DynamoReportIndex();
        index.setKey(key.getIndexKeyString());
        index.setIdentifier(key.getIdentifier());

        try {
            mapper.save(index, DOES_NOT_EXIST_EXPRESSION);    
        } catch(ConditionalCheckFailedException e) {
            // Do not throw an exception if the index already exists. This is called as a side
            // effect of saving a report, and can be called multiple times for a report.
        }
    }

    @Override
    public void removeIndex(ReportDataKey key) {
        checkNotNull(key);
        
        DynamoReportIndex hashKey = new DynamoReportIndex();
        hashKey.setKey(key.getIndexKeyString());
        hashKey.setIdentifier(key.getIdentifier());
        
        DynamoReportIndex index = mapper.load(hashKey);
        if (index != null) {
            mapper.delete(index);
        }
    }

    @Override
    public void updateIndex(ReportIndex index) {
        checkNotNull(index);
        
        DynamoReportIndex hashKey = new DynamoReportIndex();
        hashKey.setKey(index.getKey());
        hashKey.setIdentifier(index.getIdentifier());
        
        Map<String,ExpectedAttributeValue> map = new ImmutableMap.Builder<String,ExpectedAttributeValue>()
                .put("key", new ExpectedAttributeValue(new AttributeValue(index.getKey())))
                .put("identifier", new ExpectedAttributeValue(new AttributeValue(index.getIdentifier()))).build();
        
        DynamoDBSaveExpression doesExistExpression = new DynamoDBSaveExpression().withExpected(map);
        
        try {
            mapper.save(index, doesExistExpression);
        } catch(ConditionalCheckFailedException e) {
            throw new EntityNotFoundException(ReportIndex.class);
        }
    }
    
    @Override
    public ReportTypeResourceList<? extends ReportIndex> getIndices(StudyIdentifier studyId, ReportType reportType) {
        checkNotNull(studyId);
        checkNotNull(reportType);
        
        // Don't use ReportDataKey because it enforces presence of healthCode for ReportType.PARTICIPANT.
        String key = String.format("%s:%s", studyId.getIdentifier(), reportType.name());
        
        DynamoReportIndex hashKey = new DynamoReportIndex();
        hashKey.setKey(key);
        
        DynamoDBQueryExpression<DynamoReportIndex> query =
                new DynamoDBQueryExpression<DynamoReportIndex>().withHashKeyValues(hashKey);

        return new ReportTypeResourceList<>(mapper.query(DynamoReportIndex.class, query), reportType);
    }

}

package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;

import javax.annotation.Resource;

import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.ReportIndexDao;
import org.sagebionetworks.bridge.models.reports.ReportDataKey;
import org.sagebionetworks.bridge.models.reports.ReportIndex;
import org.sagebionetworks.bridge.models.reports.ReportType;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper.FailedBatch;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;

@Component
public class DynamoReportIndexDao implements ReportIndexDao {
    
    private DynamoDBMapper mapper;

    @Resource(name = "reportIndexMapper")
    final void setReportIndexMapper(DynamoDBMapper reportIndexMapper) {
        this.mapper = reportIndexMapper;
    }
    
    @Override
    public void addIndex(ReportDataKey key) {
        checkNotNull(key);
        
        DynamoReportIndex index = new DynamoReportIndex();
        index.setKey(key.getIndexKeyString());
        index.setIdentifier(key.getIdentifier());
        
        mapper.save(index);
    }

    @Override
    public void removeIndex(ReportDataKey key) {
        checkNotNull(key);
        checkArgument(key.getReportType() != ReportType.PARTICIPANT, "cannot remove participant report indices");
        
        DynamoReportIndex hashKey = new DynamoReportIndex();
        hashKey.setKey(key.getIndexKeyString());
        
        Condition idCondition = new Condition().withComparisonOperator(ComparisonOperator.EQ)
                .withAttributeValueList(new AttributeValue().withS(key.getIdentifier()));
        
        DynamoDBQueryExpression<DynamoReportIndex> query =
                new DynamoDBQueryExpression<DynamoReportIndex>().withHashKeyValues(hashKey)
                        .withRangeKeyCondition("identifier", idCondition);
        List<DynamoReportIndex> objectsToDelete = mapper.query(DynamoReportIndex.class, query);
        
        if (!objectsToDelete.isEmpty()) {
            List<FailedBatch> failures = mapper.batchDelete(objectsToDelete);
            BridgeUtils.ifFailuresThrowException(failures);
        }
    }

    @Override
    public List<? extends ReportIndex> getIndices(StudyIdentifier studyId, ReportType reportType) {
        checkNotNull(studyId);
        checkNotNull(reportType);
        
        String key = String.format("%s:%s", studyId.getIdentifier(), reportType.name());
        
        DynamoReportIndex hashKey = new DynamoReportIndex();
        hashKey.setKey(key);
        
        DynamoDBQueryExpression<DynamoReportIndex> query =
                new DynamoDBQueryExpression<DynamoReportIndex>().withHashKeyValues(hashKey);

        return mapper.query(DynamoReportIndex.class, query);
    }

}

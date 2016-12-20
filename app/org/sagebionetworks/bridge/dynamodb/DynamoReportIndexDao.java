package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkNotNull;

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

        // Don't recreate if it exists. There isn't a non-key attribute to use for a 
        // conditional save operation, so load, test, then save.
        DynamoReportIndex dbIndex = mapper.load(index);
        if (dbIndex == null) {
            mapper.save(index);
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
        
        DynamoReportIndex dbIndex = mapper.load(hashKey);
        if (dbIndex == null) {
            throw new EntityNotFoundException(ReportIndex.class);
        }
        mapper.save(index);
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

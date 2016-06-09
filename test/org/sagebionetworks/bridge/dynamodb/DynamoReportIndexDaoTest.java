package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;

import java.util.List;

import javax.annotation.Resource;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.models.reports.ReportDataKey;
import org.sagebionetworks.bridge.models.reports.ReportIndex;
import org.sagebionetworks.bridge.models.reports.ReportType;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper.FailedBatch;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoReportIndexDaoTest {

    @Autowired
    DynamoReportIndexDao dao;
    
    private ReportDataKey studyReportKey1;
    
    private ReportDataKey studyReportKey2;
    
    private ReportDataKey participantReportKey1;
    
    private ReportDataKey participantReportKey2;

    @Resource(name = "reportIndexMapper")
    private DynamoDBMapper mapper;

    @Before
    public void before() {
        String reportName1 = TestUtils.randomName(DynamoReportIndexDaoTest.class);
        String reportName2 = TestUtils.randomName(DynamoReportIndexDaoTest.class);
        
        ReportDataKey.Builder builder = new ReportDataKey.Builder().withStudyIdentifier(TEST_STUDY);
        builder.withReportType(ReportType.STUDY);
        studyReportKey1 = builder.withIdentifier(reportName1).build();
        studyReportKey2 = builder.withIdentifier(reportName2).build();
        
        builder.withReportType(ReportType.PARTICIPANT);
        participantReportKey1 = builder.withIdentifier(reportName1)
                .withHealthCode(BridgeUtils.generateGuid()).build();
        participantReportKey2 = builder.withIdentifier(reportName2)
                .withHealthCode(BridgeUtils.generateGuid()).build();
    }
    
    @Test
    public void canCRUDReportIndex() {
        int count = dao.getIndices(TEST_STUDY, ReportType.STUDY).size();
        // adding twice is fine
        dao.addIndex(studyReportKey1);
        dao.addIndex(studyReportKey1);
        dao.addIndex(studyReportKey2);

        List<? extends ReportIndex> indices = dao.getIndices(TEST_STUDY, ReportType.STUDY);
        assertEquals(count+2, indices.size());
        
        // Wrong type returns zero records
        indices = dao.getIndices(TEST_STUDY, ReportType.PARTICIPANT);
        assertEquals(0, indices.size());
        
        dao.removeIndex(studyReportKey1);
        indices = dao.getIndices(TEST_STUDY, ReportType.STUDY);
        assertEquals(count+1, indices.size());
        
        dao.removeIndex(studyReportKey2);
        indices = dao.getIndices(TEST_STUDY, ReportType.STUDY);
        assertEquals(count, indices.size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void cannotCreateIndexForParticipantReport() {
        dao.removeIndex(participantReportKey1);
    }
    
    @Test
    public void canCreateAndReadParticipantIndex() {
        int count = dao.getIndices(TEST_STUDY, ReportType.PARTICIPANT).size();
        
        // adding twice is fine
        dao.addIndex(participantReportKey1);
        dao.addIndex(participantReportKey1);
        dao.addIndex(participantReportKey2);
        
        List<? extends ReportIndex> indices = dao.getIndices(TEST_STUDY, ReportType.PARTICIPANT);
        assertEquals(count+2, indices.size());
        assertEquals(participantReportKey1.getIdentifier(), indices.get(0).getIdentifier());
        assertEquals(participantReportKey2.getIdentifier(), indices.get(1).getIdentifier());
        
        // wrong type returns no records
        indices = dao.getIndices(TEST_STUDY, ReportType.STUDY);
        assertEquals(0, indices.size());
        
        // Use mapper directly to clean up, as we don't allow deletion of participant reports.
        deleteParticipantReport(participantReportKey1);
        deleteParticipantReport(participantReportKey2);
        
        indices = dao.getIndices(TEST_STUDY, ReportType.PARTICIPANT);
        assertEquals(count, indices.size());
    }
    
    private void deleteParticipantReport(ReportDataKey key) {
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
}

package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.amazonaws.services.dynamodbv2.model.ComparisonOperator.BETWEEN;
import static org.sagebionetworks.bridge.models.ResourceList.PAGE_SIZE;
import static org.sagebionetworks.bridge.models.ResourceList.OFFSET_KEY;
import static org.sagebionetworks.bridge.models.ResourceList.START_DATE;
import static org.sagebionetworks.bridge.models.ResourceList.END_DATE;
import static org.sagebionetworks.bridge.models.ResourceList.START_TIME;
import static org.sagebionetworks.bridge.models.ResourceList.END_TIME;

import java.util.List;

import javax.annotation.Resource;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.ReportDataDao;
import org.sagebionetworks.bridge.models.DateRangeResourceList;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.reports.ReportData;
import org.sagebionetworks.bridge.models.reports.ReportDataKey;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.QueryResultPage;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper.FailedBatch;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

@Component
public class DynamoReportDataDao implements ReportDataDao {

    private DynamoDBMapper mapper;

    @Resource(name = "reportDataMapper")
    final void setReportDataMapper(DynamoDBMapper reportDataMapper) {
        this.mapper = reportDataMapper;
    }
    
    @Override
    public DateRangeResourceList<? extends ReportData> getReportData(ReportDataKey key, LocalDate startDate, LocalDate endDate) {
        checkNotNull(key);
        checkNotNull(startDate);
        checkNotNull(endDate);
        
        DynamoReportData hashKey = new DynamoReportData();
        hashKey.setKey(key.getKeyString());
        
        // range key is between start date and end date
        Condition dateCondition = new Condition().withComparisonOperator(BETWEEN)
                .withAttributeValueList(new AttributeValue().withS(startDate.toString()),
                        new AttributeValue().withS(endDate.toString()));

        DynamoDBQueryExpression<DynamoReportData> query =
                new DynamoDBQueryExpression<DynamoReportData>().withHashKeyValues(hashKey)
                        .withRangeKeyCondition("date", dateCondition);
        List<DynamoReportData> results = mapper.query(DynamoReportData.class, query);

        return new DateRangeResourceList<DynamoReportData>(results)
                .withRequestParam(START_DATE, startDate)
                .withRequestParam(END_DATE, endDate);
    }
    
    /**
     * Query for report records within a range of DateTime values, using the indicated page size and offset key. 
     * The report's date field will be returned using the timezone provided in the startTime/endTime parameters, 
     * but all searches are done against the values in UTC time so they remain accurate when a user switches 
     * time zones.
     */
    @Override
    public ForwardCursorPagedResourceList<ReportData> getReportDataV4(final ReportDataKey key, final DateTime startTime,
            final DateTime endTime, final String offsetKey, final int pageSize) {
        checkNotNull(key);
        checkNotNull(startTime);
        checkNotNull(endTime);
        
        int pageSizeWithIndicatorRecord = pageSize+1;
        DynamoReportData hashKey = new DynamoReportData();
        hashKey.setKey(key.getKeyString());
        
        AttributeValue start = new AttributeValue().withS(startTime.withZone(DateTimeZone.UTC).toString());
        AttributeValue end = new AttributeValue().withS(endTime.withZone(DateTimeZone.UTC).toString());
        // The offsetKey should be in UTC
        if (offsetKey != null) {
            start = new AttributeValue().withS(offsetKey);
        }
        
        Condition dateCondition = new Condition().withComparisonOperator(BETWEEN)
                .withAttributeValueList(start, end);

        DynamoDBQueryExpression<DynamoReportData> query = new DynamoDBQueryExpression<DynamoReportData>()
                .withHashKeyValues(hashKey)
                .withRangeKeyCondition("date", dateCondition)
                .withLimit(pageSizeWithIndicatorRecord);
        
        QueryResultPage<DynamoReportData> page = mapper.queryPage(DynamoReportData.class, query);
        
        List<ReportData> list = Lists.newArrayListWithCapacity(pageSizeWithIndicatorRecord);
        for (int i = 0, len = page.getResults().size(); i < len; i++) {
            ReportData oneReport = page.getResults().get(i);
            DateTime dateTime = oneReport.getDateTime();
            if (dateTime != null) {
                oneReport.setDateTime(dateTime.withZone(startTime.getZone()));
            }
            list.add(i, oneReport);
        }
        
        String nextPageOffsetKey = null;
        if (list.size() == pageSizeWithIndicatorRecord) {
            nextPageOffsetKey = Iterables.getLast(list).getDate();
        }

        int limit = Math.min(list.size(), pageSize);
        return new ForwardCursorPagedResourceList<ReportData>(list.subList(0, limit), nextPageOffsetKey)
                .withRequestParam(PAGE_SIZE, pageSize)
                .withRequestParam(OFFSET_KEY, offsetKey)
                .withRequestParam(START_TIME, startTime)
                .withRequestParam(END_TIME, endTime);
    }

    @Override
    public void saveReportData(ReportData reportData) {
        checkNotNull(reportData);
        
        DateTime dateTime = reportData.getDateTime();
        if (dateTime != null) {
            reportData.setDateTime(dateTime.withZone(DateTimeZone.UTC));
        }
        mapper.save(reportData);
    }

    @Override
    public void deleteReportData(ReportDataKey key) {
        checkNotNull(key);

        DynamoReportData hashKey = new DynamoReportData();
        hashKey.setKey(key.getKeyString());

        DynamoDBQueryExpression<DynamoReportData> query =
                new DynamoDBQueryExpression<DynamoReportData>().withHashKeyValues(hashKey);
        List<DynamoReportData> objectsToDelete = mapper.query(DynamoReportData.class, query);
        
        if (!objectsToDelete.isEmpty()) {
            List<FailedBatch> failures = mapper.batchDelete(objectsToDelete);
            BridgeUtils.ifFailuresThrowException(failures);
        }
    }
    
    @Override
    public void deleteReportDataRecord(ReportDataKey key, String date) {
        DynamoReportData hashKey = new DynamoReportData();
        hashKey.setKey(key.getKeyString());
        hashKey.setDate(date);
        
        DynamoReportData reportDataRecord = mapper.load(hashKey);
        if (reportDataRecord != null) {
            mapper.delete(reportDataRecord);
        }
    }
}

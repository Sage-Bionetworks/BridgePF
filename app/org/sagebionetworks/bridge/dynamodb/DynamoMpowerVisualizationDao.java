package org.sagebionetworks.bridge.dynamodb;

import java.util.List;
import javax.annotation.Resource;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.joda.time.LocalDate;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.MpowerVisualizationDao;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

/** DDB implementation of mPower visualization. */
@Component
public class DynamoMpowerVisualizationDao implements MpowerVisualizationDao {
    private DynamoDBMapper mapper;

    /** DDB mapper for the mPower visualization table. */
    @Resource(name = "mpowerVisualizationDdbMapper")
    final void setDdbMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }

    /** {@inheritDoc} */
    @Override
    public JsonNode getVisualization(String healthCode, LocalDate startDate, LocalDate endDate) {
        // hash key comes from study and health code
        DynamoMpowerVisualization hashKey = new DynamoMpowerVisualization();
        hashKey.setHealthCode(healthCode);

        // range key is between start date and end date
        Condition dateCondition = new Condition().withComparisonOperator(ComparisonOperator.BETWEEN)
                .withAttributeValueList(new AttributeValue().withS(startDate.toString()),
                        new AttributeValue().withS(endDate.toString()));

        // query
        DynamoDBQueryExpression<DynamoMpowerVisualization> query =
                new DynamoDBQueryExpression<DynamoMpowerVisualization>().withHashKeyValues(hashKey)
                        .withRangeKeyCondition("date", dateCondition);
        List<DynamoMpowerVisualization> vizList = mapper.query(DynamoMpowerVisualization.class, query);

        // convert to JSON object
        ObjectNode vizColNode = BridgeObjectMapper.get().createObjectNode();
        for (DynamoMpowerVisualization oneViz : vizList) {
            vizColNode.set(oneViz.getDate().toString(), oneViz.getVisualization());
        }
        return vizColNode;
    }
}

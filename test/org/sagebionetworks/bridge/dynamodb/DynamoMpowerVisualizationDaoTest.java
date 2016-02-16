package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Resource;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.joda.time.LocalDate;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoMpowerVisualizationDaoTest {
    @Autowired
    @SuppressWarnings("unused")
    private DynamoMpowerVisualizationDao dao;

    @Resource(name = "mpowerVisualizationDdbMapper")
    @SuppressWarnings("unused")
    private DynamoDBMapper mapper;

    private List<DynamoMpowerVisualization> vizList;

    @Before
    public void setup() {
        // Create test data for health code AAA from Feb 1 to Feb 4, test data for health code BBB for Feb 4. This
        // gives us a nice range of data to play with.
        // Test data will be strings, even though in production they'll be complex objects.
        vizList = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            String dateStr = "2016-02-0" + i;

            DynamoMpowerVisualization viz = new DynamoMpowerVisualization();
            viz.setDate(LocalDate.parse(dateStr));
            viz.setHealthCode("AAA");
            viz.setVisualization(new TextNode("data AAA-" + dateStr));

            vizList.add(viz);
        }

        {
            DynamoMpowerVisualization viz = new DynamoMpowerVisualization();
            viz.setDate(LocalDate.parse("2016-02-04"));
            viz.setHealthCode("BBB");
            viz.setVisualization(new TextNode("data BBB-2016-02-04"));

            vizList.add(viz);
        }
    }

    @After
    public void cleanup() {
        mapper.batchDelete(vizList);
    }

    @Test
    public void test() {
        // write statuses
        for (DynamoMpowerVisualization oneViz : vizList) {
            dao.writeVisualization(oneViz);
        }

        // Read statuses back. Read only a subset to make sure query logic works.
        JsonNode vizColNode = dao.getVisualization("AAA", LocalDate.parse("2016-02-02"),
                LocalDate.parse("2016-02-04"));
        assertEquals(3, vizColNode.size());
        assertEquals("data AAA-2016-02-02", vizColNode.get("2016-02-02").textValue());
        assertEquals("data AAA-2016-02-03", vizColNode.get("2016-02-03").textValue());
        assertEquals("data AAA-2016-02-04", vizColNode.get("2016-02-04").textValue());
    }
}

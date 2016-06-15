package org.sagebionetworks.bridge.dynamodb;

import static org.mockito.Mockito.mock;

import java.util.List;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.model.TableDescription;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.BridgeTestSpringConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration(classes={BridgeTestSpringConfig.class})
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoInitializerIntegrationTest {

    private DynamoInitializer initializer;
    @Autowired
    private AmazonDynamoDBClient dynamoDBClient;
    private AmazonDynamoDBClient dynamoDBClientSpy;

    @Autowired
    private BridgeConfig bridgeConfig;

    @Autowired
    private AnnotationBasedTableCreator annotationBasedTableCreator;

    @Autowired
    private DynamoNamingHelper dynamoNamingHelper;

    @Autowired
    private DynamoUtils dynamoUtils;

    private DynamoBackupHandler dynamoBackupHandler;

    @Before
    public void init() {
        dynamoDBClientSpy = Mockito.spy(dynamoDBClient);

        dynamoBackupHandler = mock(DynamoBackupHandler.class);

        initializer = new DynamoInitializer(bridgeConfig, dynamoDBClientSpy, dynamoBackupHandler, dynamoUtils, dynamoNamingHelper);
    }

    @Test
    public void testInitCreatesObjectsForTables() {
        List<TableDescription> tables = annotationBasedTableCreator.getTables(DynamoSurvey.class, DynamoExternalIdentifier.class);

        initializer.init(tables);

        Mockito.verify(dynamoBackupHandler).backupPipelineForTables(tables);
    }
}

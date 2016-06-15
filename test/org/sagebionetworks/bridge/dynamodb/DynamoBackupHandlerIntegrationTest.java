package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.amazonaws.services.datapipeline.DataPipelineClient;
import com.amazonaws.services.datapipeline.model.CreatePipelineRequest;
import com.amazonaws.services.datapipeline.model.CreatePipelineResult;
import com.amazonaws.services.datapipeline.model.GetPipelineDefinitionRequest;
import com.amazonaws.services.datapipeline.model.GetPipelineDefinitionResult;
import com.amazonaws.services.datapipeline.model.PipelineObject;
import com.amazonaws.services.datapipeline.model.PutPipelineDefinitionRequest;
import com.amazonaws.services.dynamodbv2.model.TableDescription;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.BridgeTestSpringConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration(classes = {BridgeTestSpringConfig.class})
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoBackupHandlerIntegrationTest {

    @Autowired
    private DataPipelineClient dataPipelineClient;
    private DataPipelineClient dataPipelineClientSpy;

    @Autowired
    private BridgeConfig bridgeConfig;

    @Autowired
    private AnnotationBasedTableCreator annotationBasedTableCreator;

    @Autowired
    private DynamoNamingHelper dynamoNamingHelper;

    private DynamoBackupHandler dynamoBackupHandler;

    private static class TestDynamoBackupHandler extends DynamoBackupHandler {

        private final String rand;
        public TestDynamoBackupHandler(BridgeConfig bridgeConfig, DataPipelineClient dataPipelineClient) {
            super(bridgeConfig, dataPipelineClient);
            rand = RandomStringUtils.randomAlphabetic(5);
        }

        @Override
        public String getPipelineUniqueId() {
            String id = super.getPipelineUniqueId() + "-TEST-" + rand;
            return id;
        }

        @Override
        protected boolean shouldActivatePipeline() {
            return true;
        }
    }

    @Before
    public void init() {
        dataPipelineClientSpy = Mockito.spy(dataPipelineClient);

        dynamoBackupHandler = new TestDynamoBackupHandler(bridgeConfig, dataPipelineClientSpy);
    }

    @After
    public void cleanup() {
        dynamoBackupHandler.deletePipeline();
    }

    @Test
    public void testInitCreatesObjectsForTables() {
        List<TableDescription> tables = annotationBasedTableCreator.getTables(DynamoSurvey.class, DynamoExternalIdentifier.class);

        assertTrue(dynamoBackupHandler.backupPipelineForTables(tables));

        String table1Name = dynamoNamingHelper.getFullyQualifiedTableName(DynamoSurvey.class);
        String table2Name = dynamoNamingHelper.getFullyQualifiedTableName(DynamoExternalIdentifier.class);

        ArgumentCaptor<PutPipelineDefinitionRequest> putPipelineDefinitionRequestArgumentCaptor =
                ArgumentCaptor.forClass(PutPipelineDefinitionRequest.class);

        Mockito.verify(dataPipelineClientSpy).putPipelineDefinition(putPipelineDefinitionRequestArgumentCaptor.capture());

        PutPipelineDefinitionRequest putPipelineDefinitionRequest = putPipelineDefinitionRequestArgumentCaptor.getValue();

        Map<String, List<PipelineObject>> typeToObject = putPipelineDefinitionRequest.getPipelineObjects().stream()
                .collect(Collectors.groupingBy(o -> DynamoDataPipelineHelper.getStringValue(o, "type").orElse("")));

        // find dynamo table data sources
        Map<String, PipelineObject> tableNameToPipelineObject =
                typeToObject.get(DynamoDataPipelineHelper.PipelineObjectType.DYNAMO_DATA_NODE).stream().collect(Collectors
                        .toMap(o -> DynamoDataPipelineHelper.getStringValue(o, "tableName").orElse(""), Function.identity()));

        PipelineObject table1Source = tableNameToPipelineObject.get(table1Name);
        PipelineObject table2Source = tableNameToPipelineObject.get(table2Name);
        assertNotNull(table1Source);
        assertNotNull(table2Source);

        // find activities using our data sources as inputs
        Map<String, PipelineObject> tableSourceIdToActivity =
                typeToObject.get(DynamoDataPipelineHelper.PipelineObjectType.EMR_ACTIVITY).stream()
                        .collect(Collectors.toMap(o -> DynamoDataPipelineHelper.getRefValue(o, "input").orElse(""), Function.identity()));

        PipelineObject table1BackupActivity = tableSourceIdToActivity.get(table1Source.getId());
        PipelineObject table2BackupActivity = tableSourceIdToActivity.get(table2Source.getId());

        assertNotNull(table1BackupActivity);
        assertNotNull(table2BackupActivity);

        // find our s3 outputs
        String table1OutputObjectId = DynamoDataPipelineHelper.getRefValue(table1BackupActivity, "output").orElse("");
        String table2OutputObjectId = DynamoDataPipelineHelper.getRefValue(table2BackupActivity, "output").orElse("");

        Map<String, PipelineObject> idToS3DataNode = typeToObject.get(DynamoDataPipelineHelper.PipelineObjectType.S3_DATA_NODE).stream()
                .collect(Collectors.toMap(PipelineObject::getId, Function.identity()));

        PipelineObject table1S3Output = idToS3DataNode.get(table1OutputObjectId);
        PipelineObject table2S3Output = idToS3DataNode.get(table2OutputObjectId);

        assertNotNull(table1S3Output);
        assertNotNull(table2S3Output);

        String table1OutputPath = DynamoDataPipelineHelper.getStringValue(table1S3Output, "directoryPath").orElse("");
        String table2OutputPath = DynamoDataPipelineHelper.getStringValue(table2S3Output, "directoryPath").orElse("");

        assertTrue(table1OutputPath.contains(table1Name));
        assertTrue(table2OutputPath.contains(table2Name));

        assertTrue(table1OutputPath.contains(DynamoBackupHandler.DYNAMO_REGION.getName()));
        assertTrue(table2OutputPath.contains(DynamoBackupHandler.DYNAMO_REGION.getName()));
    }

    @Test
    public void testAdditionOfTable() {
        List<String> tables = Arrays.asList("TableA", "TableB");
        assertTrue(dynamoBackupHandler.backupPipelineForTableNames(tables));
        assertEquals(tables, getTables());

        tables = Arrays.asList("TableA", "TableB", "TableC");
        assertTrue(dynamoBackupHandler.backupPipelineForTableNames(tables));
        assertEquals(tables, getTables());
    }

    @Test
    public void testRemovalOfExistingTable() {
        List<String> tables = Arrays.asList("TableA", "TableB", "TableC");
        assertTrue(dynamoBackupHandler.backupPipelineForTableNames(tables));
        assertEquals(tables, getTables());

        tables = Arrays.asList("TableA", "TableC");
        assertTrue(dynamoBackupHandler.backupPipelineForTableNames(tables));
        assertEquals(tables, getTables());
    }

    private List<String> getTables() {
        CreatePipelineResult createPipelineResult = dataPipelineClient
                .createPipeline(new CreatePipelineRequest().withUniqueId(dynamoBackupHandler.getPipelineUniqueId())
                        .withName(dynamoBackupHandler.getPipelineName()));

        GetPipelineDefinitionResult result = dataPipelineClient
                .getPipelineDefinition(new GetPipelineDefinitionRequest().withPipelineId(createPipelineResult.getPipelineId()));

        return result.getPipelineObjects().stream().filter(o -> DynamoDataPipelineHelper.PipelineObjectType.DYNAMO_DATA_NODE
                .equals(DynamoDataPipelineHelper.getStringValue(o, "type").orElse("")))
                .map(o -> DynamoDataPipelineHelper.getStringValue(o, "tableName").orElse("")).collect(Collectors.toList());
    }
}

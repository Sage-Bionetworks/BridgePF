package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.amazonaws.services.datapipeline.DataPipelineClient;
import com.amazonaws.services.datapipeline.model.Field;
import com.amazonaws.services.datapipeline.model.PipelineObject;
import com.amazonaws.services.datapipeline.model.PutPipelineDefinitionRequest;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.model.TableDescription;
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

@ContextConfiguration(classes={BridgeTestSpringConfig.class})
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoInitializerIntegrationTest {

    private DynamoInitializer initializer;
    @Autowired
    private AmazonDynamoDBClient dynamoDBClient;
    private AmazonDynamoDBClient dynamoDBClientSpy;
    @Autowired
    private DataPipelineClient dataPipelineClient;
    private DataPipelineClient dataPipelineClientSpy;

    @Autowired
    BridgeConfig bridgeConfig;

    @Autowired
    AnnotationBasedTableCreator annotationBasedTableCreator;

    @Before
    public void init() {
        dynamoDBClientSpy = Mockito.spy(dynamoDBClient);
        dataPipelineClientSpy = Mockito.spy(dataPipelineClient);

        initializer = new DynamoInitializer(bridgeConfig, dynamoDBClientSpy, dataPipelineClientSpy);
    }

    @Test
    public void testInitCreatesObjectsForTables() {
        List<TableDescription> tables =
                annotationBasedTableCreator.getTables(DynamoSurvey.class, DynamoSurveyResponse.class);

        initializer.init(tables);

        String table1Name = DynamoUtils.getFullyQualifiedTableName(DynamoSurvey.class, bridgeConfig);
        String table2Name = DynamoUtils.getFullyQualifiedTableName(DynamoSurveyResponse.class, bridgeConfig);

        ArgumentCaptor<PutPipelineDefinitionRequest> putPipelineDefinitionRequestArgumentCaptor =
                ArgumentCaptor.forClass(PutPipelineDefinitionRequest.class);

        Mockito
                .verify(dataPipelineClientSpy)
                .putPipelineDefinition(putPipelineDefinitionRequestArgumentCaptor.capture());

        PutPipelineDefinitionRequest putPipelineDefinitionRequest =
                putPipelineDefinitionRequestArgumentCaptor.getValue();


        Map<String, List<PipelineObject>> typeToObject = putPipelineDefinitionRequest
                .getPipelineObjects()
                .stream()
                .collect(Collectors.groupingBy(o -> getStringValue(o, "type").orElse("")));

        // find dynamo table data sources
        Map<String, PipelineObject> tableNameToPipelineObject = typeToObject
                .get("DynamoDBDataNode")
                .stream()
                .collect(Collectors.toMap(o -> getStringValue(o, "tableName").orElse(""), Function.identity()));

        PipelineObject table1Source = tableNameToPipelineObject.get(table1Name);
        PipelineObject table2Source = tableNameToPipelineObject.get(table2Name);
        assertNotNull(table1Source);
        assertNotNull(table2Source);

        // find activities using our data sources as inputs
        Map<String, PipelineObject> tableSourceIdToActivity = typeToObject
                .get("EmrActivity")
                .stream()
                .collect(Collectors.toMap(o -> getRefValue(o, "input").orElse(""), Function.identity()));

        PipelineObject table1BackupActivity = tableSourceIdToActivity.get(table1Source.getId());
        PipelineObject table2BackupActivity = tableSourceIdToActivity.get(table2Source.getId());

        assertNotNull(table1BackupActivity);
        assertNotNull(table2BackupActivity);

        // find our s3 outputs
        String table1OutputObjectId = getRefValue(table1BackupActivity, "output").orElse("");
        String table2OutputObjectId = getRefValue(table2BackupActivity, "output").orElse("");

        Map<String, PipelineObject> idToS3DataNode = typeToObject
                .get("S3DataNode")
                .stream()
                .collect(Collectors.toMap(PipelineObject::getId, Function.identity()));

        PipelineObject table1S3Output = idToS3DataNode.get(table1OutputObjectId);
        PipelineObject table2S3Output = idToS3DataNode.get(table2OutputObjectId);

        assertNotNull(table1S3Output);
        assertNotNull(table2S3Output);

        String table1OutputPath = getStringValue(table1S3Output, "directoryPath").orElse("");
        String table2OutputPath = getStringValue(table2S3Output, "directoryPath").orElse("");

        assertTrue(table1OutputPath.contains(table1Name));
        assertTrue(table2OutputPath.contains(table2Name));

        assertTrue(table1OutputPath.contains(DynamoInitializer.DYNAMO_REGION.getName()));
        assertTrue(table2OutputPath.contains(DynamoInitializer.DYNAMO_REGION.getName()));
    }

    private Optional<String> getStringValue(PipelineObject o, String key) {
        return o
                .getFields()
                .stream()
                .filter(f -> key.equals(f.getKey()))
                .findFirst()
                .map(Field::getStringValue);
    }

    private Optional<String> getRefValue(PipelineObject o, String key) {
        return o
                .getFields()
                .stream()
                .filter(f -> key.equals(f.getKey()))
                .findFirst()
                .map(Field::getRefValue);
    }

}

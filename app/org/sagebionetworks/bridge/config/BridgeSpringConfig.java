package org.sagebionetworks.bridge.config;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.Resource;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.PredefinedClientConfigurations;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.datapipeline.DataPipelineClient;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClient;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.stormpath.sdk.api.ApiKey;
import com.stormpath.sdk.api.ApiKeys;
import com.stormpath.sdk.application.Application;
import com.stormpath.sdk.client.Client;
import com.stormpath.sdk.client.ClientBuilder;
import com.stormpath.sdk.client.Clients;
import com.stormpath.sdk.impl.client.DefaultClientBuilder;

import org.sagebionetworks.bridge.dynamodb.AnnotationBasedTableCreator;
import org.sagebionetworks.bridge.dynamodb.DynamoNamingHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import org.springframework.context.annotation.FilterType;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.crypto.AesGcmEncryptor;
import org.sagebionetworks.bridge.crypto.BridgeEncryptor;
import org.sagebionetworks.bridge.crypto.CmsEncryptor;
import org.sagebionetworks.bridge.crypto.CmsEncryptorCacheLoader;
import org.sagebionetworks.bridge.dynamodb.DynamoHealthDataAttachment;
import org.sagebionetworks.bridge.dynamodb.DynamoHealthDataRecord;
import org.sagebionetworks.bridge.dynamodb.DynamoIndexHelper;
import org.sagebionetworks.bridge.dynamodb.DynamoParticipantOptions;
import org.sagebionetworks.bridge.dynamodb.DynamoReportData;
import org.sagebionetworks.bridge.dynamodb.DynamoReportIndex;
import org.sagebionetworks.bridge.dynamodb.DynamoSchedulePlan;
import org.sagebionetworks.bridge.dynamodb.DynamoStudyConsent1;
import org.sagebionetworks.bridge.dynamodb.DynamoSurvey;
import org.sagebionetworks.bridge.dynamodb.DynamoSurveyElement;
import org.sagebionetworks.bridge.dynamodb.DynamoScheduledActivity;
import org.sagebionetworks.bridge.dynamodb.DynamoSubpopulation;
import org.sagebionetworks.bridge.dynamodb.DynamoActivityEvent;
import org.sagebionetworks.bridge.dynamodb.DynamoCriteria;
import org.sagebionetworks.bridge.dynamodb.DynamoExternalIdentifier;
import org.sagebionetworks.bridge.dynamodb.DynamoFPHSExternalIdentifier;
import org.sagebionetworks.bridge.dynamodb.DynamoUpload2;
import org.sagebionetworks.bridge.dynamodb.DynamoUploadDedupe;
import org.sagebionetworks.bridge.dynamodb.DynamoUploadSchema;
import org.sagebionetworks.bridge.dynamodb.DynamoUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.s3.S3Helper;
import org.sagebionetworks.bridge.upload.DecryptHandler;
import org.sagebionetworks.bridge.upload.IosSchemaValidationHandler2;
import org.sagebionetworks.bridge.upload.ParseJsonHandler;
import org.sagebionetworks.bridge.upload.S3DownloadHandler;
import org.sagebionetworks.bridge.upload.StrictValidationHandler;
import org.sagebionetworks.bridge.upload.TranscribeConsentHandler;
import org.sagebionetworks.bridge.upload.UnzipHandler;
import org.sagebionetworks.bridge.upload.UploadArtifactsHandler;
import org.sagebionetworks.bridge.upload.UploadValidationHandler;

/**
 * Annotation-based Spring config. This class is shared between both production and (Spring-based) test configs. For
 * production-only Spring configs, see {@link BridgeProductionSpringConfig}. For test-only Spring configs, see
 * BridgeTestSpringConfig in tests.
 */
@ComponentScan(basePackages = "org.sagebionetworks.bridge", excludeFilters = @ComponentScan.Filter(
        type = FilterType.ANNOTATION, value = Configuration.class))
@Configuration
public class BridgeSpringConfig {
    @Bean(name = "redisProviders")
    public List<String> redisProviders() {
        return Lists.newArrayList("REDISCLOUD_URL", "REDISTOGO_URL");
    }
    
    @Bean(name = "bridgeObjectMapper")
    public BridgeObjectMapper bridgeObjectMapper() {
        return BridgeObjectMapper.get();
    }

    @Bean(name = "bridgeConfig")
    public BridgeConfig bridgeConfig() {
        return BridgeConfigFactory.getConfig();
    }

    @Bean(name = "annotationBasedTableCreator")
    public AnnotationBasedTableCreator annotationBasedTableCreator(DynamoNamingHelper dynamoNamingHelper) {
        return new AnnotationBasedTableCreator(dynamoNamingHelper);
    }

    @Bean(name = "healthCodeEncryptor")
    @Resource(name = "bridgeConfig")
    public BridgeEncryptor healthCodeEncryptor(BridgeConfig bridgeConfig) {
        return new BridgeEncryptor(new AesGcmEncryptor(bridgeConfig.getHealthCodeKey()));
    }

    @Bean(name = "awsCredentials")
    public BasicAWSCredentials awsCredentials() {
        BridgeConfig bridgeConfig = bridgeConfig();
        return new BasicAWSCredentials(bridgeConfig.getProperty("aws.key"),
                bridgeConfig.getProperty("aws.secret.key"));
    }

    @Bean(name = "s3UploadCredentials")
    @Resource(name = "bridgeConfig")
    public BasicAWSCredentials s3UploadCredentials(BridgeConfig bridgeConfig) {
        return new BasicAWSCredentials(bridgeConfig.getProperty("aws.key.upload"),
                bridgeConfig.getProperty("aws.secret.key.upload"));
    }

    @Bean(name = "s3CmsCredentials")
    @Resource(name = "bridgeConfig")
    public BasicAWSCredentials s3CmsCredentials(BridgeConfig bridgeConfig) {
        return new BasicAWSCredentials(bridgeConfig.getProperty("aws.key.upload.cms"),
                bridgeConfig.getProperty("aws.secret.key.upload.cms"));
    }

    @Bean(name = "dynamoDbClient")
    @Resource(name = "awsCredentials")
    public AmazonDynamoDBClient dynamoDbClient() {
        int maxRetries = bridgeConfig().getPropertyAsInt("ddb.max.retries");
        ClientConfiguration awsClientConfig = PredefinedClientConfigurations.dynamoDefault()
                .withMaxErrorRetry(maxRetries);
        return new AmazonDynamoDBClient(awsCredentials(), awsClientConfig);
    }

    @Bean(name = "dataPipelineClient")
    @Resource(name = "awsCredentials")
    public DataPipelineClient dataPipelineClient(BasicAWSCredentials awsCredentials) {
        return new DataPipelineClient(awsCredentials);
    }

    @Bean(name = "s3Client")
    @Resource(name = "awsCredentials")
    public AmazonS3Client s3Client(BasicAWSCredentials awsCredentials) {
        return new AmazonS3Client(awsCredentials);
    }

    @Bean(name = "s3UploadClient")
    @Resource(name = "s3UploadCredentials")
    public AmazonS3Client s3UploadClient(BasicAWSCredentials s3UploadCredentials) {
        return new AmazonS3Client(s3UploadCredentials);
    }

    @Bean(name = "s3CmsClient")
    @Resource(name = "s3CmsCredentials")
    public AmazonS3Client s3CmsClient(BasicAWSCredentials s3CmsCredentials) {
        return new AmazonS3Client(s3CmsCredentials);
    }

    @Bean(name ="uploadTokenServiceClient")
    @Resource(name = "s3UploadCredentials")
    public AWSSecurityTokenServiceClient uploadTokenServiceClient(BasicAWSCredentials s3UploadCredentials) {
        return new AWSSecurityTokenServiceClient(s3UploadCredentials);
    }

    @Bean(name = "s3CmsHelper")
    @Resource(name = "s3CmsClient")
    public S3Helper s3CmsHelper(AmazonS3Client s3CmsClient) {
        S3Helper s3CmsHelper = new S3Helper();
        s3CmsHelper.setS3Client(s3CmsClient);
        return s3CmsHelper;
    }

    @Bean(name = "s3Helper")
    @Resource(name = "s3Client")
    public S3Helper s3Helper(AmazonS3Client s3Client) {
        S3Helper s3Helper = new S3Helper();
        s3Helper.setS3Client(s3Client);
        return s3Helper;
    }

    @Bean(name = "s3ConsentsCredentials")
    @Resource(name = "bridgeConfig")
    public BasicAWSCredentials s3ConsentsCredentials(BridgeConfig bridgeConfig) {
        return new BasicAWSCredentials(bridgeConfig.getProperty("aws.key.consents"),
                bridgeConfig.getProperty("aws.secret.key.consents"));
    }

    @Bean(name = "s3ConsentsClient")
    @Resource(name = "s3ConsentsCredentials")
    public AmazonS3Client s3ConsentsClient(BasicAWSCredentials awsCredentials) {
        return new AmazonS3Client(awsCredentials);
    }

    @Bean(name = "s3ConsentsHelper")
    @Resource(name = "s3ConsentsClient")
    public S3Helper s3ConsentsHelper(AmazonS3Client s3Client) {
        S3Helper s3Helper = new S3Helper();
        s3Helper.setS3Client(s3Client);
        return s3Helper;
    }

    @Bean(name = "sesClient")
    @Resource(name="awsCredentials")
    public AmazonSimpleEmailServiceClient sesClient(BasicAWSCredentials awsCredentials) {
        return new AmazonSimpleEmailServiceClient(awsCredentials);
    }

    @Bean(name = "sqsClient")
    @Resource(name = "awsCredentials")
    public AmazonSQSClient sqsClient(BasicAWSCredentials awsCredentials) {
        return new AmazonSQSClient(awsCredentials);
    }

    @Bean(name = "asyncExecutorService")
    @Resource(name = "bridgeConfig")
    public ExecutorService asyncExecutorService(BridgeConfig bridgeConfig) {
        return Executors.newFixedThreadPool(bridgeConfig.getPropertyAsInt("async.worker.thread.count"));
    }

    @Bean(name = "supportEmail")
    @Resource(name = "bridgeConfig")
    public String supportEmail(BridgeConfig bridgeConfig) {
        return bridgeConfig.getProperty("support.email");
    }

    @Bean(name = "cmsEncryptorCache")
    @Autowired
    public LoadingCache<String, CmsEncryptor> cmsEncryptorCache(CmsEncryptorCacheLoader cacheLoader) {
        return CacheBuilder.newBuilder().build(cacheLoader);
    }

    @Bean(name = "dynamoUtils")
    @Autowired
    public DynamoUtils dynamoUtils(DynamoNamingHelper dynamoNamingHelper, AmazonDynamoDB dynamoDB) {
        return new DynamoUtils(dynamoNamingHelper, dynamoDB);
    }

    @Bean(name = "dynamoNamingHelper")
    @Autowired
    public DynamoNamingHelper dynamoNamingHelper(BridgeConfig bridgeConfig) {
        return new DynamoNamingHelper(bridgeConfig);
    }

    @Bean(name = "healthDataAttachmentDdbMapper")
    @Autowired
    public DynamoDBMapper healthDataAttachmentDdbMapper(DynamoUtils dynamoUtils) {
        return dynamoUtils.getMapper(DynamoHealthDataAttachment.class);
    }

    @Bean(name = "reportDataMapper")
    @Autowired
    public DynamoDBMapper reportDataMapper(DynamoUtils dynamoUtils) {
        return dynamoUtils.getMapper(DynamoReportData.class);
    }
    
    @Bean(name = "reportIndexMapper")
    @Autowired
    public DynamoDBMapper reportIndexMapper(DynamoUtils dynamoUtils) {
        return dynamoUtils.getMapper(DynamoReportIndex.class);
    }
    
    @Bean(name = "healthDataDdbMapper")
    @Autowired
    public DynamoDBMapper healthDataDdbMapper(DynamoUtils dynamoUtils) {
        return dynamoUtils.getMapper(DynamoHealthDataRecord.class);
    }

    @Bean(name = "activityEventDdbMapper")
    @Autowired
    public DynamoDBMapper activityEventDdbMapper(DynamoUtils dynamoUtils) {
        return dynamoUtils.getMapper(DynamoActivityEvent.class);
    }

    @Bean(name = "studyConsentDdbMapper")
    @Autowired
    public DynamoDBMapper studyConsentDdbMapper(DynamoUtils dynamoUtils) {
        return dynamoUtils.getMapper(DynamoStudyConsent1.class);
    }

    @Bean(name = "subpopulationDdbMapper")
    @Autowired
    public DynamoDBMapper subpopulationDdbMapper(DynamoUtils dynamoUtils) {
        return dynamoUtils.getMapper(DynamoSubpopulation.class);
    }
    
    @Bean(name = "surveyMapper")
    @Autowired
    public DynamoDBMapper surveyDdbMapper(DynamoUtils dynamoUtils) {
        return dynamoUtils.getMapper(DynamoSurvey.class);
    }

    @Bean(name = "surveyElementMapper")
    @Autowired
    public DynamoDBMapper surveyElementDdbMapper(DynamoUtils dynamoUtils) {
        return dynamoUtils.getMapper(DynamoSurveyElement.class);
    }

    @Bean(name = "criteriaMapper")
    @Autowired
    public DynamoDBMapper criteriaMapper(DynamoUtils dynamoUtils) {
        return dynamoUtils.getMapper(DynamoCriteria.class);
    }
    
    @Bean(name = "schedulePlanMapper")
    @Autowired
    public DynamoDBMapper schedulePlanMapper(DynamoUtils dynamoUtils) {
        return dynamoUtils.getMapper(DynamoSchedulePlan.class);
    }
    
    @Bean(name = "uploadHealthCodeRequestedOnIndex")
    @Autowired
    public DynamoIndexHelper uploadHealthCodeRequestedOnIndex(AmazonDynamoDBClient dynamoDBClient, DynamoUtils dynamoUtils,
            DynamoNamingHelper dynamoNamingHelper) {
        return DynamoIndexHelper.create(DynamoUpload2.class, "healthCode-requestedOn-index", dynamoDBClient, dynamoNamingHelper, dynamoUtils);
    }
    
    @Bean(name = "healthDataHealthCodeIndex")
    @Autowired
    public DynamoIndexHelper healthDataHealthCodeIndex(AmazonDynamoDBClient dynamoDBClient,
                                                       DynamoUtils dynamoUtils,
                                                       DynamoNamingHelper dynamoNamingHelper) {
        return DynamoIndexHelper.create(DynamoHealthDataRecord.class, "healthCode-index", dynamoDBClient, dynamoNamingHelper, dynamoUtils);
    }

    @Bean(name = "healthDataUploadDateIndex")
    @Autowired
    public DynamoIndexHelper healthDataUploadDateIndexDynamoUtils(AmazonDynamoDBClient dynamoDBClient,
                                                                  DynamoUtils dynamoUtils,
                                                                  DynamoNamingHelper dynamoNamingHelper) {
        return DynamoIndexHelper.create(DynamoHealthDataRecord.class, "uploadDate-index", dynamoDBClient, dynamoNamingHelper, dynamoUtils);
    }

    @Bean(name = "activitySchedulePlanGuidIndex")
    @Autowired
    public DynamoIndexHelper activitySchedulePlanGuidIndex(AmazonDynamoDBClient dynamoDBClient,
                                                           DynamoUtils dynamoUtils,
                                                           DynamoNamingHelper dynamoNamingHelper) {
        return DynamoIndexHelper
                .create(DynamoScheduledActivity.class, "schedulePlanGuid-index", dynamoDBClient, dynamoNamingHelper, dynamoUtils);
    }

    @Bean(name = "uploadSchemaStudyIdIndex")
    @Autowired
    public DynamoIndexHelper uploadSchemaStudyIdIndex(AmazonDynamoDBClient dynamoDBClient,
                                                      DynamoUtils dynamoUtils,
                                                      DynamoNamingHelper dynamoNamingHelper) {
        return DynamoIndexHelper.create(DynamoUploadSchema.class, "studyId-index", dynamoDBClient, dynamoNamingHelper, dynamoUtils);
    }

    @Bean(name = "uploadDdbMapper")
    @Autowired
    public DynamoDBMapper uploadDdbMapper(DynamoUtils dynamoUtils) {
        return dynamoUtils.getMapper(DynamoUpload2.class);
    }

    @Bean(name = "uploadDedupeDdbMapper")
    public DynamoDBMapper uploadDedupeDdbMapper(DynamoUtils dynamoUtils) {
        return dynamoUtils.getMapper(DynamoUploadDedupe.class);
    }
    
    @Bean(name = "fphsExternalIdDdbMapper")
    @Autowired
    public DynamoDBMapper fphsExternalIdDdbMapper(DynamoUtils dynamoUtils) {
        return dynamoUtils.getMapper(DynamoFPHSExternalIdentifier.class);
    }
    
    @Bean(name = "externalIdDdbMapper")
    @Autowired
    public DynamoDBMapper externalIdDdbMapper(DynamoUtils dynamoUtils) {
        return dynamoUtils.getMapper(DynamoExternalIdentifier.class);
    }
    
    @Bean(name = "participantOptionsDbMapper")
    @Autowired
    public DynamoDBMapper participantOptionsDbMapper(DynamoUtils dynamoUtils) {
        return dynamoUtils.getMapper(DynamoParticipantOptions.class);
    }

    @Bean(name = "uploadValidationHandlerList")
    @Autowired
    public List<UploadValidationHandler> uploadValidationHandlerList(S3DownloadHandler s3DownloadHandler,
            DecryptHandler decryptHandler, UnzipHandler unzipHandler, ParseJsonHandler parseJsonHandler,
            IosSchemaValidationHandler2 iosSchemaValidationHandler2,
            StrictValidationHandler strictValidationHandler, TranscribeConsentHandler transcribeConsentHandler,
            UploadArtifactsHandler uploadArtifactsHandler) {
        return ImmutableList.of(s3DownloadHandler, decryptHandler, unzipHandler, parseJsonHandler,
                iosSchemaValidationHandler2, strictValidationHandler, transcribeConsentHandler,
                uploadArtifactsHandler);
    }

    @Bean(name = "uploadSchemaDdbMapper")
    @Autowired
    public DynamoDBMapper uploadSchemaDdbMapper(DynamoUtils dynamoUtils) {
        return dynamoUtils.getMapper(DynamoUploadSchema.class);
    }

    @Bean(name = "activityDdbMapper")
    @Autowired
    public DynamoDBMapper activityDdbMapper(DynamoUtils dynamoUtils) {
        return dynamoUtils.getMapper(DynamoScheduledActivity.class);
    }

    // Do NOT reference this bean outside of StormpathAccountDao. Injected for testing purposes.
    @Bean(name = "stormpathClient")
    @Autowired
    public Client getStormpathClient(BridgeConfig bridgeConfig) {
        ApiKey apiKey = ApiKeys.builder()
            .setId(bridgeConfig.getStormpathId())
            .setSecret(bridgeConfig.getStormpathSecret()).build();
        ClientBuilder clientBuilder = Clients.builder().setApiKey(apiKey);
        ((DefaultClientBuilder)clientBuilder).setBaseUrl("https://enterprise.stormpath.io/v1");
        return clientBuilder.build();        
    }

    // Do NOT reference this bean outside of StormpathAccountDao. Injected for testing purposes.
    @Bean(name = "stormpathApplication")
    @Autowired
    public Application getStormpathApplication(BridgeConfig bridgeConfig, Client stormpathClient) {
        return stormpathClient.getResource(bridgeConfig.getStormpathApplicationHref(), Application.class);
    }
    
    @Bean(name = "sessionExpireInSeconds")
    public int getSessionExpireInSeconds() {
        return BridgeConstants.BRIDGE_SESSION_EXPIRE_IN_SECONDS;
    }

}

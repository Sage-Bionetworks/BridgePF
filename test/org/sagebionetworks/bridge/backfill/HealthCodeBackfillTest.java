package org.sagebionetworks.bridge.backfill;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.dynamodb.DynamoHealthCode;
import org.sagebionetworks.bridge.dynamodb.DynamoHealthId;
import org.sagebionetworks.bridge.dynamodb.DynamoTestUtil;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.stormpath.StormpathFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.account.AccountList;
import com.stormpath.sdk.application.Application;
import com.stormpath.sdk.client.Client;
import com.stormpath.sdk.directory.CustomData;

import controllers.StudyControllerService;

@ContextConfiguration("file:conf/application-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class HealthCodeBackfillTest {

    @Resource
    private Client stormpathClient;

    @Resource
    private StudyControllerService studyControllerService;

    @Resource
    private HealthCodeBackfill healthCodeBackfill;

    @Before
    public void before() {
        clearDynamo();
    }

    @After
    public void after() {
        clearDynamo();
    }

    @Test
    public void test() {
        healthCodeBackfill.regerateHealthId();
        verify();
    }

    private void verify() {
        Iterable<Study> studies = studyControllerService.getStudies();
        Application application = StormpathFactory.createStormpathApplication(stormpathClient);
        AccountList accounts = application.getAccounts();
        for (Account account : accounts) {
            CustomData customData = account.getCustomData();
            // After the backfill, there should be no more old version
            Object version = customData.get(BridgeConstants.CUSTOM_DATA_VERSION);
            for (Study study : studies) {
                String key = study.getKey() + BridgeConstants.CUSTOM_DATA_HEALTH_CODE_SUFFIX;
                Object healthCode = customData.get(key);
                if (version == null && healthCode != null) {
                    Assert.fail("backfill has failed.");
                }
            }
        }
    }

    private void clearDynamo() {
        DynamoTestUtil.clearTable(DynamoHealthCode.class, "version");
        DynamoTestUtil.clearTable(DynamoHealthId.class, "code", "version");
    }
}

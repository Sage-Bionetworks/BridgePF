package org.sagebionetworks.bridge.dynamodb;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("file:conf/application-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoUserConsentDaoTest {

    @Resource
    private DynamoUserConsentDao userConsentDao;

    @Before
    public void before() {
        DynamoTestUtil.clearTable(DynamoUserConsent.class, "give", "withdraw", "version");
    }

    @After
    public void after() {
        DynamoTestUtil.clearTable(DynamoUserConsent.class, "give", "withdraw", "version");
    }

    @Test
    public void test() {
        DynamoStudyConsent consent = new DynamoStudyConsent();
        consent.setStudyKey("study123");
        consent.setTimestamp(123L);
        userConsentDao.giveConsent("hc123", consent);
    }
}

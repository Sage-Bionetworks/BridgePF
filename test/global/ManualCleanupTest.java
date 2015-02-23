package global;

import javax.annotation.Resource;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.services.UserAdminService;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class ManualCleanupTest {

    @Resource
    UserAdminService userAdminService;

    @Test
    public void deleteAllRemainingTestUsers() {
        userAdminService.deleteAllUsers(BridgeConstants.TEST_USERS_GROUP);
    }
}

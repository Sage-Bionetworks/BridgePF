package org.sagebionetworks.bridge.services;

import javax.annotation.Resource;


import org.junit.*;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.TestUserAdminHelper;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserProfile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.junit.Assert.*;

@ContextConfiguration("file:conf/application-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class UserProfileServiceImplTest {
    
    @Resource
    UserProfileServiceImpl service;
    
    @Resource
    TestUserAdminHelper helper;
    
    @Before
    public void before() {
        helper.createOneUser();
    }
    
    @After
    public void after() {
        helper.deleteOneUser();
    }
    
    @Test
    public void canUpdateUserProfile() {
        UserProfile userProfile = helper.getUserProfile();
        userProfile.setFirstName("Test");
        userProfile.setLastName("Powers");
        
        User updatedUser = service.updateProfile(helper.getUser(), userProfile);
        
        assertTrue(helper.getUser().equals(updatedUser));
    }

}

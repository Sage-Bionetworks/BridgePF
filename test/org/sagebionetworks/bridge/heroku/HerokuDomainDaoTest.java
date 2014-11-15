package org.sagebionetworks.bridge.heroku;

import javax.annotation.Resource;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class HerokuDomainDaoTest {

    @Resource
    HerokuDomainDao domainDao;
    
    @Test
    public void registerDomains() {
        domainDao.addDomain("belgium");
        domainDao.removeDomain("belgium");
    }
    
}

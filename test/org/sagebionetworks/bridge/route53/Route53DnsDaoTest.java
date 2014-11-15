package org.sagebionetworks.bridge.route53;

import javax.annotation.Resource;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class Route53DnsDaoTest {

    @Resource
    private Route53DnsDao dnsDao;
    
    @Test
    public void addAndRemover() {
        dnsDao.addCnameRecordsForStudy("dns-test");
        dnsDao.removeCnameRecordsForStudy("dns-test");
    }
    
}

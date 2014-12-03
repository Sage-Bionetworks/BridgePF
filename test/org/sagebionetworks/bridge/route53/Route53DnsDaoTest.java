package org.sagebionetworks.bridge.route53;

import static org.junit.Assert.*;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class Route53DnsDaoTest {

    @Resource
    private Route53DnsDao dnsDao;
    
    private String identifier;
    
    @After
    public void after() {
        if (identifier != null) {
            dnsDao.deleteDnsRecordForStudy(identifier);
        }
    }
    
    @Test
    public void crudDnsRecord() {
        identifier = TestUtils.randomName();
        
        String dnsRecord = dnsDao.createDnsRecordForStudy(identifier);
        assertEquals("Correct hostname", BridgeConfigFactory.getConfig().getStudyHostname(identifier), dnsRecord);
        
        dnsDao.deleteDnsRecordForStudy(identifier);
        
        dnsRecord = dnsDao.getDnsRecordForStudy(identifier);
        assertNull("Hostname deleted", dnsRecord);
        
        identifier = null;
    }
    
}

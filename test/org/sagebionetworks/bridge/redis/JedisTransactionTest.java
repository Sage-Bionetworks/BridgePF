package org.sagebionetworks.bridge.redis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.List;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class JedisTransactionTest {

    private final String key1 = JedisTransactionTest.class.getSimpleName() + "Key1";
    private final String key2 = JedisTransactionTest.class.getSimpleName() + "Key2";
    private final String val1 = JedisTransactionTest.class.getSimpleName() + "Val1";
    private final String val2 = JedisTransactionTest.class.getSimpleName() + "Val2";

    @Resource
    private JedisOps jedisOps;

    @After
    public void after() {
        jedisOps.del(key1, key2);
    }

    @Test
    public void testExec() throws Exception {
        final List<Object> results = jedisOps.getTransaction()
                .setex(key1, 10, val1)
                .setex(key2, 10, val2)
                .exec();
        assertNotNull(results);
        assertEquals(2, results.size());
        assertEquals("OK", results.get(0));
        assertEquals("OK", results.get(1));
        assertEquals(val1, jedisOps.get(key1));
        assertEquals(val2, jedisOps.get(key2));
    }

    @Test
    public void testWatch() throws Exception {
        JedisTransaction transaction = jedisOps.getTransaction(key1)
                .setex(key1, 10, val1)
                .setex(key2, 10, val2);
        jedisOps.setex(key1, 10, val2);
        List<Object> results = transaction.exec();
        assertNull("Transaction should have been aborted.", results);
        assertEquals(val2, jedisOps.get(key1));
        assertNull("Transaction should have been aborted.", jedisOps.get(key2));
    }

    @Test
    public void testDiscard() throws Exception {
        final String results = jedisOps.getTransaction()
                .setex(key1, 10, val1)
                .setex(key2, 10, val2)
                .discard();
        assertEquals("OK", results);
        assertNull(jedisOps.get(key1));
        assertNull(jedisOps.get(key2));
    }

    @Test
    public void testFinalize() throws Exception {
        JedisTransaction transaction = jedisOps.getTransaction();
        transaction.exec();
        transaction.finalize();
        transaction = jedisOps.getTransaction();
        transaction.setex(key1, 10, val1).discard();
        transaction.finalize();
    }
}

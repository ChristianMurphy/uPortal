/**
 * Licensed to Apereo under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright ownership. Apereo
 * licenses this file to you under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the License at the
 * following location:
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apereo.portal.session.redis;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import org.junit.Before;
import org.junit.Test;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisNode;
import org.springframework.data.redis.connection.RedisSentinelConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.test.util.ReflectionTestUtils;

/** The connection factory carries the org.apereo.portal.session.redis.* settings (MOD-D22). */
public class RedisSessionConfigTest {

    private RedisSessionConfig config;

    @Before
    public void setUp() {
        this.config = new RedisSessionConfig();
    }

    private void set(String field, Object value) {
        ReflectionTestUtils.setField(this.config, field, value);
    }

    private LettuceConnectionFactory factory() {
        return (LettuceConnectionFactory) this.config.redisConnectionFactory();
    }

    @Test
    public void standaloneUsesHostPortDatabasePasswordAndTimeout() {
        set("redisMode", "standalone");
        set("redisHost", "redis.example.edu");
        set("redisPort", 6380);
        set("redisDatabase", 3);
        set("redisPassword", "secret");
        set("redisTimeout", 1500);

        final LettuceConnectionFactory factory = factory();

        assertEquals("redis.example.edu", factory.getHostName());
        assertEquals(6380, factory.getPort());
        assertEquals(3, factory.getDatabase());
        assertEquals("secret", factory.getPassword());
        assertEquals(Duration.ofMillis(1500), factory.getClientConfiguration().getCommandTimeout());
        assertNull(factory.getSentinelConfiguration());
        assertNull(factory.getClusterConfiguration());
    }

    @Test
    public void standaloneDefaultsToLocalhostWithoutPassword() {
        set("redisMode", "standalone");

        final LettuceConnectionFactory factory = factory();

        assertEquals("localhost", factory.getHostName());
        assertEquals(6379, factory.getPort());
        assertEquals(0, factory.getDatabase());
        assertNull(factory.getPassword());
    }

    @Test
    public void sentinelUsesMasterNodesDatabaseAndPassword() {
        set("redisMode", "SENTINEL");
        set("redisSentinelMaster", "portal");
        set("redisSentinelNodes", "s1.example.edu:26379,s2.example.edu:26380");
        set("redisDatabase", 2);
        set("redisPassword", "secret");

        final LettuceConnectionFactory factory = factory();

        final RedisSentinelConfiguration sentinel = factory.getSentinelConfiguration();
        assertEquals("portal", sentinel.getMaster().getName());
        assertTrue(sentinel.getSentinels().contains(new RedisNode("s1.example.edu", 26379)));
        assertTrue(sentinel.getSentinels().contains(new RedisNode("s2.example.edu", 26380)));
        assertEquals(2, sentinel.getDatabase());
        assertArrayEquals("secret".toCharArray(), sentinel.getPassword().get());
    }

    @Test
    public void clusterUsesNodesMaxRedirectsAndPassword() {
        set("redisMode", "cluster");
        set("redisClusterNodes", "c1.example.edu:7000,c2.example.edu:7001");
        set("redisClusterMaxRedirects", 4);
        set("redisPassword", "secret");

        final LettuceConnectionFactory factory = factory();

        final RedisClusterConfiguration cluster = factory.getClusterConfiguration();
        assertEquals(2, cluster.getClusterNodes().size());
        assertTrue(cluster.getClusterNodes().contains(new RedisNode("c1.example.edu", 7000)));
        assertEquals(Integer.valueOf(4), cluster.getMaxRedirects());
        assertArrayEquals("secret".toCharArray(), cluster.getPassword().get());
    }

    @Test(expected = IllegalArgumentException.class)
    public void unknownModeIsRejected() {
        set("redisMode", "shards");
        this.config.redisConnectionFactory();
    }
}

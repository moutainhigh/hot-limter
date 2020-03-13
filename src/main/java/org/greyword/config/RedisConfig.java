package org.greyword.config;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CachingConfigurerSupport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPool;

@Configuration
public class RedisConfig extends CachingConfigurerSupport {

    @Bean
    public JedisPool getJedisPool(@Value("${spring.redis.host}") String host,
                                  @Value("${spring.redis.port}") int port,
                                  @Value("${spring.redis.timeout}") int timeout,
                                  @Value("${spring.redis.database}") int database
                                    ){
        GenericObjectPoolConfig poolConfig = new GenericObjectPoolConfig();
        return new JedisPool(poolConfig,host,port,timeout,null,database);
    }
}

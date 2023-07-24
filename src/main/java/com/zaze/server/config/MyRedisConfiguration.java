package com.zaze.server.config;

import io.lettuce.core.ReadFrom;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

@Configuration(proxyBeanMethods = false)
class MyRedisConfiguration {
    /**
     * redisTemplate 配置
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);
//        RedisSerializer<String> redisSerializer = new StringRedisSerializer();
//        Jackson2JsonRedisSerializer<CoffeeVo> jackson2JsonRedisSerializer = new Jackson2JsonRedisSerializer<>(CoffeeVo.class);
//        template.setKeySerializer(redisSerializer);
//        template.setValueSerializer(jackson2JsonRedisSerializer);
//        template.setHashKeySerializer(redisSerializer);
//        template.setHashValueSerializer(jackson2JsonRedisSerializer);
        return template;
    }

    @Bean
    public LettuceClientConfigurationBuilderCustomizer customizer() {
        return builder -> builder.readFrom(ReadFrom.MASTER_PREFERRED);
    }

//    @Bean
//    @ConfigurationProperties("redis")
//    public JedisPoolConfig jedisPoolConfig() {
//        return new JedisPoolConfig();
//    }
//
//    @Bean(destroyMethod = "close")
//    public JedisPool jedisPool(@Value("${redis.host}") String host) {
//        return new JedisPool(jedisPoolConfig(), host);
//    }
}

package com.example.dbms_homework_1.config;

import com.example.dbms_homework_1.stream.NotificationConsumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Duration;

@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class RedisConfig {

    @Bean
    public DefaultRedisScript<Long> incrementLoginsScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("scripts/increment_logins.lua"));
        script.setResultType(Long.class);
        return script;
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> streamContainer(
            RedisConnectionFactory connectionFactory,
            NotificationConsumer consumer) {
        StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
                        .pollTimeout(Duration.ofMillis(100))
                        .build();

        StreamMessageListenerContainer<String, MapRecord<String, String, String>> container =
                StreamMessageListenerContainer.create(connectionFactory, options);

        try {
            new StringRedisTemplate(connectionFactory).execute((RedisCallback<Void>) connection -> {
                try {
                    connection.streamCommands().xGroupCreate("notifications".getBytes(), "notifications-group", ReadOffset.latest(), true);
                    log.info("Created notifications stream group");
                } catch (Exception e) {
                    if (containsBusyGroup(e)) {
                        log.info("Notifications stream group already exists");
                    } else {
                        log.warn("Failed to create stream group: {}", e.getMessage());
                    }
                }
                return null;
            });
        } catch (Exception e) {
            log.warn("Stream group init error: {}", e.getMessage());
        }

        container.receiveAutoAck(Consumer.from("notifications-group", "consumer-1"),
                StreamOffset.create("notifications", ReadOffset.lastConsumed()),
                consumer);

        return container;
    }

    private static boolean containsBusyGroup(Throwable e) {
        Throwable current = e;
        while (current != null) {
            String msg = current.getMessage();
            if (msg != null && msg.contains("BUSYGROUP")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    @Bean
    public RedisHealthCheck redisHealthCheck(RedisConnectionFactory connectionFactory) {
        return new RedisHealthCheck(connectionFactory);
    }

    public static class RedisHealthCheck {
        public RedisHealthCheck(RedisConnectionFactory connectionFactory) {
            try (var connection = connectionFactory.getConnection()) {
                String pong = connection.ping();
                log.info("Redis PING: {}", pong);
            } catch (Exception e) {
                log.error("Redis connection check failed: {}", e.getMessage());
            }
        }
    }
}

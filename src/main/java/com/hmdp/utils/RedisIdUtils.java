package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;

@Component
public class RedisIdUtils {
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    //起始时间戳
    private final Long start = LocalDateTime.of(2025, 10, 1, 0, 0).toEpochSecond(ZoneOffset.UTC);

    /**
     * 生成唯一id
     * @param keyPrefix
     * @return
     */
    public Long nextId(String keyPrefix){
        //当前时间戳
        Long now = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        //时间戳之差
        Long epoch = now - start;
        //序列号：精确到每天，自增序列
        String day = LocalDate.now().toString();
        String key = "icr:" + day + ":" + keyPrefix;
        Long count = redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, 1, TimeUnit.DAYS);
        return (epoch << 32) | count;
    }

}

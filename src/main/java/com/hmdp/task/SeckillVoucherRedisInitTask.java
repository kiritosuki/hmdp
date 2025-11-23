package com.hmdp.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
public class SeckillVoucherRedisInitTask {
    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private VoucherOrderMapper voucherOrderMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final String LOCK_KEY = "seckill:redis:init:lock";

    // 每隔7分钟检查一次秒杀券是否需要初始化
    @Scheduled(fixedDelay = 7 * 60 * 1000)
    public void initRedisSeckillVoucher() {
        // 1. 获取距离开始时间 <= 10分钟且未初始化的秒杀券
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime tenMinutesLater = now.plusMinutes(10);

        List<SeckillVoucher> vouchers = seckillVoucherService
                .query()
                .lt("begin_time", tenMinutesLater)   // 秒杀开始时间 <= 现在+10分钟
                .gt("begin_time", now)               // 秒杀开始时间 >= 现在
                .list();

        if (vouchers.isEmpty()) {
            return;
        }

        // 2. 获取分布式锁，保证多实例只执行一次
        RLock lock = redissonClient.getLock(LOCK_KEY);
        boolean isLock = false;
        try {
            isLock = lock.tryLock(0, 10, TimeUnit.SECONDS); // 最多持锁10秒
            if (!isLock) {
                return;
            }

            // 3. 初始化 Redis 数据
            for (SeckillVoucher voucher : vouchers) {
                String stockKey = "seckill:stock:" + voucher.getVoucherId();
                stringRedisTemplate.opsForValue().set(stockKey, String.valueOf(voucher.getStock()));

                String orderKey = "seckill:order:" + voucher.getVoucherId();
                // 从数据库查询已经购买过该秒杀券的用户
                List<Long> userIds = voucherOrderMapper.selectList(
                                new LambdaQueryWrapper<VoucherOrder>()
                                        .eq(VoucherOrder::getVoucherId, voucher.getVoucherId())
                        )
                        .stream().map(VoucherOrder::getUserId).collect(Collectors.toList());

                if (!userIds.isEmpty()) {
                    for(Long userId : userIds){
                        stringRedisTemplate.opsForSet().add(orderKey, String.valueOf(userId));
                    }
                }
            }

        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            if (isLock) {
                lock.unlock();
            }
        }
    }
}

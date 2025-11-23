package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.dto.SeckillMessage;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIdUtils;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;
    @Autowired
    private RedisIdUtils redisIdUtils;
    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final DefaultRedisScript<Long> CHECK_SECKILL_SCRIPT;

    static {
        CHECK_SECKILL_SCRIPT = new DefaultRedisScript<>();
        CHECK_SECKILL_SCRIPT.setLocation(new ClassPathResource("scripts/check_seckill.lua"));
        CHECK_SECKILL_SCRIPT.setResultType(Long.class);
    }

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 限时优惠卷秒杀下单
     * @param voucherId
     * @return
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        /*
        //查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //判断秒杀是否开始
        LocalDateTime beginTime = voucher.getBeginTime();
        LocalDateTime endTime = voucher.getEndTime();
        if(LocalDateTime.now().isAfter(endTime)){
            return Result.fail("秒杀已结束！");
        }
        if(LocalDateTime.now().isBefore(beginTime)){
            return Result.fail("秒杀尚未开始！");
        }
        //判断库存
        if (voucher.getStock() < 1) {
            return Result.fail("库存不足！");
        }
        //判断一人一单
        Long userId = UserHolder.getUser().getId();
        Integer count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if(count > 0){
            return Result.fail("该用户已经购买过了！");
        }
        RLock lock = redissonClient.getLock(RedisConstants.LOCK_ORDER_KEY + userId);
        boolean isLock = lock.tryLock();
        if(!isLock){
            return Result.fail("不允许重复下单！");
        }
        try {
            //扣减库存
            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock - 1")
                    .eq("voucher_id", voucherId)
                    .gt("stock", 0) // 关键条件
                    .update();
            if(!success){
                return Result.fail("库存不足！");
            }
            //创建订单
            VoucherOrder voucherOrder = new VoucherOrder();
            Long voucherOrderId = redisIdUtils.nextId("voucherOrder");
            voucherOrder.setId(voucherOrderId);
            voucherOrder.setUserId(UserHolder.getUser().getId());
            voucherOrder.setVoucherId(voucherId);
            save(voucherOrder);
            return Result.ok(voucherOrderId);
        } finally {
            lock.unlock();
        }
*/

        //查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //判断秒杀是否开始
        LocalDateTime beginTime = voucher.getBeginTime();
        LocalDateTime endTime = voucher.getEndTime();
        if(LocalDateTime.now().isAfter(endTime)){
            return Result.fail("秒杀已结束！");
        }
        if(LocalDateTime.now().isBefore(beginTime)){
            return Result.fail("秒杀尚未开始！");
        }

        // 当前用户 id
        Long userId = UserHolder.getUser().getId();
        // 生成订单号（后面 MQ 消费者落库要用）
        Long orderId = redisIdUtils.nextId("voucherOrder");
        // 执行 Lua 脚本（原子：资格判断 + 扣库存）
        Long result = stringRedisTemplate.execute(
                CHECK_SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString(),
                orderId.toString()
        );
        if (result == null) {
            return Result.fail("服务器异常，请重试！");
        }
        int r = result.intValue();
        if (r == 1) {
            return Result.fail("库存不足！");
        }
        if (r == 2) {
            return Result.fail("不能重复下单！");
        }

        // Lua 执行成功 异步下单
        SeckillMessage message = new SeckillMessage(userId, voucherId, orderId);

        rabbitTemplate.convertAndSend(
                "voucherOrder.exchange",
                "voucherOrder.create",
                message
        );

        return Result.ok(orderId);
    }
}

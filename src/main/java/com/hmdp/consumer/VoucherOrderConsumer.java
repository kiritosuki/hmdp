package com.hmdp.consumer;

import com.hmdp.dto.SeckillMessage;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
@Component
@Slf4j
public class VoucherOrderConsumer {
    @Autowired
    private VoucherOrderMapper voucherOrderMapper;
    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @RabbitListener(queues = "voucherOrder.seckill.queue")
    public void createOrder(SeckillMessage message) {
        // 扣减 MySQL 库存，乐观锁
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", message.getVoucherId())
                .gt("stock", 0)
                .update();
        // 扣减失败
        if (!success){
            log.error("MySQL库存扣减失败, orderId={}", message.getOrderId());
        }

        // 创建订单
        VoucherOrder order = new VoucherOrder();
        order.setId(message.getOrderId());
        order.setUserId(message.getUserId());
        order.setVoucherId(message.getVoucherId());
        voucherOrderMapper.insert(order);
    }
}

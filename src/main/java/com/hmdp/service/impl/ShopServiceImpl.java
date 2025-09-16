package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.MessageConstants;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private ThreadPoolTaskExecutor cacheRebuildExecutor;
    @Autowired
    private RedissonClient redissonClient;

    /**
     * 根据商铺id查询商铺详细信息
     * @param id
     * @return
     */
    @Override
    @Transactional
    public Result queryById(Long id) {
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        //查询redis中的缓存信息
        Object cacheShop = redisTemplate.opsForValue().get(key);
        //若缓存存在
        if(cacheShop != null){
            //如果缓存了空值
            if(cacheShop.toString().equals("null")){
                return Result.fail("商铺不存在！");
            }
            //如果缓存了商铺信息
            RedisData redisData = (RedisData) cacheShop;
            //判断是否逻辑过期
            //如果未过期 直接返回
            if(redisData.getExpireTime().isAfter(LocalDateTime.now())){
                return Result.ok(redisData.getData());
            }
            //如果已经过期 获取分布式锁 异步更新缓存 返回旧值
            cacheRebuildExecutor.submit(() -> {
                RLock lock = redissonClient.getLock(RedisConstants.LOCK_SHOP_KEY + id);
                boolean isLock = lock.tryLock();
                if(isLock){
                    try {
                        RedisData redisData2 = new RedisData();
                        //查询数据库 设置逻辑过期时间
                        redisData2.setData(getById(id));
                        redisData2.setExpireTime(LocalDateTime.now().plusMinutes(RedisConstants.CACHE_LOGIC_SHOP_TTL));
                        //更新缓存
                        Long ttl = RedisConstants.CACHE_SHOP_TTL + ThreadLocalRandom.current().nextLong(-1, 2);
                        redisTemplate.opsForValue().set(key, redisData2, ttl, TimeUnit.DAYS);
                    } catch (Exception e) {
                        log.error(MessageConstants.CACHE_REBUILD_ERROR);
                    } finally {
                        lock.unlock();
                    }
                }
            });
            return Result.ok(redisData.getData());
        }
        //若缓存不存在
        Shop shop = getById(id);
        if(shop == null){
            //数据库中也没有 商铺不存在 缓存短期空值 防止穿透
            redisTemplate.opsForValue().set(key, "null", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return Result.fail("商铺不存在！");
        }
        //数据库中能查到 保存到redis缓存
        //设置30min逻辑过期时间
        //设置较长时间的实际过期时间 上下浮动防止雪崩
        RedisData redisData3 = new RedisData();
        redisData3.setData(shop);
        redisData3.setExpireTime(LocalDateTime.now().plusMinutes(RedisConstants.CACHE_LOGIC_SHOP_TTL));
        Long ttl = RedisConstants.CACHE_SHOP_TTL + ThreadLocalRandom.current().nextLong(-1, 2);
        redisTemplate.opsForValue().set(key, redisData3, ttl, TimeUnit.DAYS);
        return Result.ok(shop);
    }

    /**
     * 更新商铺信息
     * @param shop
     * @return
     */
    @Override
    public Result updateShopById(Shop shop) {
        Long id = shop.getId();
        if(id == null){
            return Result.fail("商铺id不能为空！");
        }
        //更新数据库
        updateById(shop);
        //删除缓存
        redisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + id);
        return Result.ok();
    }
}

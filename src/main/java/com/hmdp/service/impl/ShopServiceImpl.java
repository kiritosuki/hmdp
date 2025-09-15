package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Random;
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

    /**
     * 根据商铺id查询商铺详细信息
     * @param id
     * @return
     */
    @Override
    public Result queryById(Long id) {
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        //查询redis缓存中商铺信息
        Object cacheShop = redisTemplate.opsForValue().get(key);
        Shop shop = new Shop();
        //缓存存在
        if(cacheShop != null){
            //如果是缓存空值
            if(cacheShop.toString().equals("null")){
                return Result.fail("商铺不存在！");
            }
            //如果是缓存商铺信息
            shop = (Shop) cacheShop;
            return Result.ok(shop);
        }
        //缓存不存在
        //查询数据库
        shop = getById(id);
        //商铺不存在
        if(shop == null){
            redisTemplate.opsForValue().set(key, "null", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return Result.fail("商铺不存在！");
        }
        //商铺存在
        //保存到redis缓存
        //给key的ttl设置为随机值防止雪崩
        Long ttl = RedisConstants.CACHE_SHOP_TTL + ThreadLocalRandom.current().nextLong(-7, 8);
        redisTemplate.opsForValue().set(key, shop, ttl, TimeUnit.MINUTES);
        //返回
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

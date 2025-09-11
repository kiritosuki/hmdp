package com.hmdp.service.impl;

import cn.hutool.Hutool;
import cn.hutool.captcha.generator.RandomGenerator;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.MessageConstants;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 发送验证码
     * @param phone
     * @param session
     * @return
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //检查手机号码格式
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail(MessageConstants.INVALID_PHONE);
        }
        //手机号格式正确 发送验证码
        String code = RandomUtil.randomNumbers(6);
        log.info("发送验证码成功：{}", code);
        //把验证码缓存到redis中
        redisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        return Result.ok();
    }

    /**
     * 用户登录
     * @param loginForm
     * @param session
     * @return
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //校验手机号格式
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail(MessageConstants.INVALID_PHONE);
        }
        //校验验证码
        String code = loginForm.getCode();
        String cacheCode = (String) redisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        if(cacheCode == null || !cacheCode.equals(code)){
            return Result.fail(MessageConstants.CODE_ERROR);
        }
        //查询用户是否存在
        User user = query().eq("phone", phone).one();
        if(user == null){
            user = createUser(loginForm);
        }
        //生成token 缓存userDTO
        String token = UUID.randomUUID().toString();
        UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(user, userDTO);
        redisTemplate.opsForValue().set(LOGIN_USER_KEY + token, userDTO);
        //设置缓存有效期
        redisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.MINUTES);
        return Result.ok(token);
    }

    /**
     * 新建用户 插入数据库
     * @param loginForm
     * @return
     */
    private User createUser(LoginFormDTO loginForm) {
        User user = new User();
        user.setPhone(loginForm.getPhone());
        user.setPassword(loginForm.getPassword());
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        save(user);
        return user;
    }

}

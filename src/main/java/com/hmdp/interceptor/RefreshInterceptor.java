package com.hmdp.interceptor;

import com.hmdp.dto.UserDTO;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.concurrent.TimeUnit;

/**
 * 拦截所有请求 用于刷新redis缓存时效
 */
@Component
public class RefreshInterceptor implements HandlerInterceptor {
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = request.getHeader(SystemConstants.TOKEN_NAME);
        if(token == null || token.isEmpty()){
            return true;
        }
        UserDTO userDTO = (UserDTO) redisTemplate.opsForValue().get(RedisConstants.LOGIN_USER_KEY + token);
        if(userDTO == null){
            return true;
        }
        //把用户信息放到threadlocal
        UserHolder.saveUser(userDTO);
        //刷新redis缓存
        redisTemplate.expire(RedisConstants.LOGIN_USER_KEY + token, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}

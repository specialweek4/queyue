package com.specialweek.limiter.aop;


import com.specialweek.limiter.annotation.RateLimiter;
import com.specialweek.limiter.exception.RateLimitException;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Collections;

@Aspect
@Component
public class RateLimiterAspect {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final DefaultRedisScript<Long> SLIDING_WINDOW_SCRIPT;

    static {
        SLIDING_WINDOW_SCRIPT = new DefaultRedisScript<>();
        SLIDING_WINDOW_SCRIPT.setLocation(new ClassPathResource("lua/limiter.lua"));
        SLIDING_WINDOW_SCRIPT.setResultType(Long.class);
    }

    //前置拦截
    @Before("@annotation(rateLimiter)")
    public void doBefore(JoinPoint point, RateLimiter rateLimiter){
        System.out.println("进入切面逻辑！！！");

        String key = rateLimiter.key();
        long window = rateLimiter.window();
        long limit = rateLimiter.limit();

        String fullkey = buildRateLimitKey(point, rateLimiter, key);
        Long result = executeSlidingWindowScript(fullkey, window, limit);

        // 如果返回0表示被限流
        if (result != null && result == 0) {
            throw new RateLimitException(rateLimiter.message());
        }
    }

    /**
     * 执行滑动窗口限流脚本
     *
     * @param key    限流key
     * @param window 时间窗口（秒）
     * @param limit  限制请求数量
     * @return 当前窗口内请求数量计数（如果被限流返回0）
     */
    public Long executeSlidingWindowScript(String key, Long window, Long limit) {
        long now = System.currentTimeMillis();
        System.out.printf("key:%s, window:%d, limit:%d\n", key, window, limit);
        return stringRedisTemplate.execute(
                SLIDING_WINDOW_SCRIPT,
                Collections.singletonList(key),
                window.toString(), limit.toString(), Long.toString(now)
        );
    }


    /**
     *构建限流key
     */
    private String buildRateLimitKey(JoinPoint point, RateLimiter rateLimiter, String baseKey) {
        StringBuilder keyBuilder = new StringBuilder(baseKey);

        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();

        // 添加类名和方法名
        keyBuilder.append(method.getDeclaringClass().getName())
                .append(":")
                .append(method.getName());

        // 根据限流类型添加额外维度
        switch (rateLimiter.type()) {
            case IP:
                keyBuilder.append(":ip:").append(getClientIp());
                break;
            case USER:
                keyBuilder.append(":user:").append(getCurrentUserId());
                break;
            case METHOD:
            default:
                // 方法级限流使用默认key
                break;
        }

        return keyBuilder.toString();
    }

    //获取客户端IP
    private String getClientIp() {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

    //获取用户id
    private String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Jwt jwt = (Jwt) authentication.getPrincipal();
        return jwt.getSubject();
    }

}

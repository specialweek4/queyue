package com.specialweek.shop.util;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.specialweek.common.util.RedisData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.specialweek.common.util.RedisConstants.CACHE_NULL_TTL;
import static com.specialweek.common.util.RedisConstants.LOCK_SHOP_KEY;

@Component
@Slf4j
public class shopCacheClient {

    private StringRedisTemplate stringRedisTemplate;

    public shopCacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit unit){
        //注意stringRedisTemplate只能操作字符串类型的，别忘了序列化。
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit){
        //设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        //写入Redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <R, ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit){
        String key = keyPrefix + id;
        //1.从Redis中查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if(StrUtil.isNotBlank(json)){
            //3.存在则返回
            return JSONUtil.toBean(json, type);
        }
        //判断命中是否是空值
        if(json != null){
            return null;
        }
        //4.不存在,通过传过来的数据库逻辑函数，来根据id查询数据库
        R r =  dbFallback.apply(id);
        //5.不存在，返回错误
        if(r == null){
            //将空值写入Redis
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
            //返回错误信息。
            return null;
        }
        //6.存在，写入Redis
        this.set(key, r, time, unit);
        //7.返回
        return r;
    }

    //创建线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public <R, ID> R queryWithLogicalExpire(
            String keyPrefix,ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit){
        String key = keyPrefix + id;
        //1.从Redis中查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if(StrUtil.isBlank(json)){
            //3.不存在则返回null
            return null;
        }
        //4.命中需要把JSON反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        R r = JSONUtil.toBean(data, type);
        LocalDateTime expireTime = redisData.getExpireTime();
        //5.判断是否过期
        if(expireTime.isAfter(LocalDateTime.now())){
            //5.1 未过期返回店铺信息
            return r;
        }
        //5.2 已经过期需要缓存重建
        //6 缓存重建
        //6.1 获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        //6.2 判断是否获取锁成功。
        if(isLock){
            //6.3 获取成功。开启独立线程，实现缓存重建。
            CACHE_REBUILD_EXECUTOR.submit(() ->{
                try {
                    //重建缓存
                    //查询数据库
                    R r1 = dbFallback.apply(id);
                    //写入Redis
                    this.setWithLogicalExpire(key, r1, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    //释放锁
                    unLock(lockKey);
                }
            });
        }
        //6.4 返回过期的商铺信息
        return r;
    }
    public <R, ID> R queryWithMutex(
            String keyPrefix,ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit){
        String key = keyPrefix + id;
        //1.从Redis中查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if(StrUtil.isNotBlank(json)){
            //3.存在则返回
            return JSONUtil.toBean(json, type);
        }
        //判断命中是否是空值
        if(json != null){
            return null;
        }
        //未命中数据，也没命中空值，就是未命中。
        //开始获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        R r = null;
        try {
            boolean isLock = tryLock(lockKey);
            //判断是否获取成功。
            if(!isLock){
                //失败，则休眠并重试。
                Thread.sleep(50);
                //重试重新查询，就是做递归
                return queryWithMutex(keyPrefix, id, type, dbFallback, time, unit);
            }

            //成功根据id查询数据库
            r = dbFallback.apply(id);
            //5.不存在，返回错误
            if(r == null){
                //将空值写入Redis
                stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
                //返回错误信息。
                return null;
            }
            //6.存在，写入Redis
            this.set(key,r,time, unit);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            //释放互斥锁，finally一定会执行，保证了锁的释放。
            unLock(lockKey);
        }
        //7.返回
        return r;
    }

    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        //hutool包中的可以帮我们判断，也可以帮我们拆箱。
        return BooleanUtil.isTrue(flag);
    }

    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }

}

package org.greyword.hot.limter;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPubSub;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class HotLimter {
    private Map<Long, ProtectThing> map = new HashMap<>();
    private Map<Long, AtomicInteger> countMap = new HashMap<>();
    private Function<Long,ProtectThing> whenNoContain; //不是保护商品的查询策略
    private Function<Long,Integer> buyGood;  //不是保护商品的购买策略
    private Function<Long,Integer> buySuccess;  //保护商品购买成功的执行策略
    private JedisPool pool;
    private Jedis jedis;
    private ThreadPoolExecutor executor;
    private String prefix = "org:greyword:hot:limter:";

    //获取商品信息的方法，非保护商品会返回默认方法
    public ProtectThing get(Long id){
        if(map.containsKey(id))
            return map.get(id);
        return whenNoContain.apply(id);
    }
    //购买商品的方法，只能购买一个
    public int buy(Long id){
        if(map.containsKey(id)) {
            ProtectThing thing = map.get(id);
            if(thing.getCount()==0){
                //System.out.println("缓存不足而拒绝");
                return 0;
            }
            int count = countMap.get(id).incrementAndGet();
            //count=jedis.incr(prefix+"count:"+thing.getId());

            if((count & count + 1) == 0){
                Jedis jedis = pool.getResource();
                Long decr = jedis.decr(prefix+"total:"+id);
                if(decr<=0){
                    jedis.publish(prefix,Long.toString(id));
                    jedis.del(prefix+"total:"+thing.getId());
                    //jedis.del(prefix+"count:"+thing.getId());
                }
                jedis.close();
                if(decr>=0){
                    return this.buySuccess.apply(id);
                }
                System.out.println("尝试购买但失败");

                return 0;
            }

            System.out.println("流量被过滤");
            return 0;
        }
        return buyGood.apply(id);
    }
    //添加保护商品的方法
    public void add(ProtectThing thing){
        map.put(thing.getId(),thing);
        countMap.put(thing.getId(),new AtomicInteger(0));
        Jedis jedis = pool.getResource();
        jedis.set(prefix+"total:"+thing.getId(),Integer.toString(thing.getCount()));
        //jedis.set(prefix+"count:"+thing.getId(),Integer.toString(0));
        jedis.close();
    }
    //解除保护商品的方法
    public void del(Long id){
        if(map.containsKey(id)){
            map.remove(id);
            countMap.remove(id);
        }
    }
    public void setWhenNoContain(Function<Long, ProtectThing> whenNoContain) {
        this.whenNoContain = whenNoContain;
    }

    public void setBuyGood(Function<Long, Integer> buyGood) {
        this.buyGood=buyGood;
    }

    public void setBuySuccess(Function<Long, Integer> buySuccess) {
        this.buySuccess = buySuccess;
    }

    public void setPool(JedisPool pool) {
        if(jedis==null){
            this.pool = pool;
            this.jedis = pool.getResource();
            executor = new ThreadPoolExecutor(1, 1, 60L, TimeUnit.SECONDS, new ArrayBlockingQueue(32), new ThreadPoolExecutor.AbortPolicy());
            executor.execute(()-> {
                jedis.subscribe(new JedisPubSub() {
                    @Override
                    public void onMessage(String channel, String message) {
                        Long id = Long.parseLong(message);
                        if(map.containsKey(id))
                            map.get(id).setCount(0);
                    }
                },prefix);
            });
        }

    }
}

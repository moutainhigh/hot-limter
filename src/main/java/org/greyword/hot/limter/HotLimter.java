package org.greyword.hot.limter;

import org.greyword.entity.ProtectThing;
import org.springframework.beans.factory.annotation.Autowired;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPubSub;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class HotLimter {
    private Map<Long, ProtectThing> map = new HashMap<Long, ProtectThing>();
    private Function<Long,ProtectThing> whenNoContain;
    private Function<Long,Integer> buyGood;
    private JedisPool pool;
    private Jedis jedis;
    ThreadPoolExecutor executor;
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
                System.out.println("缓存不足而拒绝");
                return 0;
            }
            Jedis jedis = pool.getResource();
            Long count = jedis.incr(prefix+"count:"+id);
            if((count & count + 1) == 0){
                System.out.println("尝试购买");
                Long decr = jedis.decr(prefix+"total:"+id);
                if(decr<=0)jedis.publish(prefix,Long.toString(id));
                return decr>=0?1:0;
            }
            jedis.close();
            System.out.println("流量被过滤");
            return 0;
        }
        return buyGood.apply(id);
    }
    //添加保护商品的方法
    public void add(ProtectThing thing){
        map.put(thing.getId(),thing);
        Jedis jedis = pool.getResource();
        jedis.set(prefix+"total:"+thing.getId(),Integer.toString(thing.getCount()));
        jedis.set(prefix+"count:"+thing.getId(),Integer.toString(0));
        jedis.close();
    }
    public void setWhenNoContain(Function<Long, ProtectThing> whenNoContain) {
        this.whenNoContain = whenNoContain;
    }

    public void setBuyGood(Function<Long, Integer> buyGood) {
        this.buyGood=buyGood;
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

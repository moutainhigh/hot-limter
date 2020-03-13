package org.greyword.controller;

import org.greyword.entity.GoodThing;
import org.greyword.entity.ProtectThing;
import org.greyword.hot.limter.HotLimter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import redis.clients.jedis.JedisPool;

@RestController
public class TestController {

    JedisPool pool;
    HotLimter limit;
    @RequestMapping("/get")
    public ProtectThing get(Long id){
        return limit.get(id);
    }
    @RequestMapping("/buy")
    public String buy(Long id){
        if(limit.buy(id)>0)
            return "购买成功";
        else
            return "商品售空";
    }
    public TestController(JedisPool pool){
        this.pool=pool;
        limit = new HotLimter();
        limit.setWhenNoContain((id)->{
            GoodThing good = new GoodThing();
            good.setId(id);
            good.setCount(10);
            good.setInfo(id+"from Creater");
            return good;
        });
        limit.setBuyGood((id)->{
            System.out.println("不是保护商品");
            return Math.random()>0.5?1:0;
        });
        limit.setBuySuccess((id)->{
            System.out.println("保护商品购买成功");
            return 1;
        });
        limit.setPool(pool);
        this.create(1L,"asd",3);
    }
    public void create(Long id,String info,Integer count){
        GoodThing good = new GoodThing();
        good.setId(id);
        good.setCount(count);
        good.setInfo(info);
        limit.add(good);
    }
}

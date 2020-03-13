# hot-limter
秒杀限流工具0.01

核心思想是在预设热点商品的情况下，将获取商品信息的流量导向内存，将购买商品的流量按一定规则进行抛弃

目前的规则是只有2的n次方次请求才能购买商品，所以在请求流量不够大或商品数量过多的情况下会出现明明有库存却无法购买的情况
<br/>另一项规则是默认一个人一次只能购买一件商品，不存在买多个的情况

未预设热点的商品会按默认规则，不限流不处理。
后期版本可能会增加自动发现热点商品并限流（可能）

采用redis频道订阅的方式，成功购买商品的服务器负责检查余量，当为0时推送商品售空信息，其他服务器会把商品库存置零。
后期版本可能会更改为推送商品现有库存，但理论上秒杀商品的库存只有售空和未售空两种可能，所以大概率不会改

内部对是否热点的查询使用的hashmap,如果高并发的设置保护商品可能会出问题，但设置保护商品本来也不应该出现高并发的情况
<br/>或许当加入自动保护策略时会受影响，但目前没有这种问题

因为售空信息是一次性推送的，所以如果发生退货服务器信息不会自动更新

使用方式为新建HotLimter对象，为对象设置whenNoContain(不是保护商品的查询策略),buyGood(不是保护商品的购买策略),JedisPool(redis连接池，用于数据更新和推送订阅)，buySuccess(保护商品购买成功的执行策略)

保护商品的设置为HotLimter的add方法，保护商品要继承ProtectThing接口

最终前端的查询和购买方法直接调用HotLimter的get和buy即可

在自行测试中，500并发的商品购买请求，触发了27次流量过滤，发送了7次购买请求，卖出5件商品，其余请求均是内存访问


最后想说的是这个东西不一定有多好，但是我想找人讨论一下这个东西有没有什么实际价值或致命缺陷

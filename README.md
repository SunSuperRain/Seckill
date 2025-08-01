

### 接口说明
在该项目中核心就是秒杀的实现：不能超卖、不能重复抢_

- 不能超卖在doSeckill1中通过update的排他性实现(乐观锁)。而在doSeckill2中通过redis预减库存(redis的原子性实现)
- 不能重复抢通过唯一索引实现，默认建表时没有添加，压测可以把用户加少点商品多一点就可以复现重复购买

2.优化不过就是把数据库的重复访问，能放到redis就放到redis；而如果访问redis太多了就再加一层内存标记

3.redis和mysql要么都在远程，要么都在本地，否则可能会出现redis缓存优化了但QPS没提升
### 页面说明

### 环境搭建

需要安装配置Mysql、Redis、RabbitMQ

**Mysql**: 建表语句 `sqldoc/创建t_user.sql`

**Redis**：本地安装，或者远程linux服务器直接docker装，不装项目起不来。

**RabbitMQ**：推荐RabbitMQ直接docker安装，两行直接搞定，不行再看看防火墙、安全组。（不安装也能学到P44）

```bash
docker pull rabbitmq

docker run \
 -e RABBITMQ_DEFAULT_USER=itcast \
 -e RABBITMQ_DEFAULT_PASS=123123 \
 --name mq \
 --hostname mq1 \
 -p 15672:15672 \
 -p 5672:5672 \
 -d \
 rabbitmq:3-management
```
访问http://localhost:8080/login/toLogin， 账号密码 1000 123456


### 注意事项
秒杀接口的结果：
![这是一个示例图片](/assets/result.png)
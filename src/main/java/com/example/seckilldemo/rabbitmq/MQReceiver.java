package com.example.seckilldemo.rabbitmq;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.seckilldemo.entity.TSeckillOrder;
import com.example.seckilldemo.entity.TUser;
import com.example.seckilldemo.service.ITGoodsService;
import com.example.seckilldemo.service.ITOrderService;
import com.example.seckilldemo.service.ITSeckillOrderService;
import com.example.seckilldemo.utils.JsonUtil;
import com.example.seckilldemo.vo.GoodsVo;
import com.example.seckilldemo.vo.RespBean;
import com.example.seckilldemo.vo.RespBeanEnum;
import com.example.seckilldemo.vo.SeckillMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 消息消费者
 *
 * @author: LC
 * @date 2022/3/7 7:44 下午
 * @ClassName: MQReceiver
 */
@Service
@Slf4j
public class MQReceiver {

    @Autowired
    private ITGoodsService itGoodsServicel;
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private ITOrderService itOrderService;


    /**
     * 下单操作
     * 消费者 消费逻辑 拿到 消息 去对 数据库进行操作
     **/
    @RabbitListener(queues = "seckillQueue")
    public void receive(String message) {
        log.info("接收消息：" + message);
        // 反序列化 JSON --> Object
        SeckillMessage seckillMessage = JSON.parseObject(message, SeckillMessage.class);
        Long goodsId = seckillMessage.getGoodsId();
        TUser user = seckillMessage.getTUser();
        // 数据库 查询当前库存 Redis 和 MySQL 数据不一致
        GoodsVo goodsVo = itGoodsServicel.findGoodsVobyGoodsId(goodsId);
        if (goodsVo.getStockCount() < 1) {
            // 库存不够 直接返回
            return;
        }
        // 数据库 层面 查看是否重复抢购
        TSeckillOrder tSeckillOrder = (TSeckillOrder) redisTemplate.opsForValue().get("order:" + user.getId() + ":" + goodsId);
        if (tSeckillOrder != null) {
            // 重复抢购 直接返回
            return;
        }
        // 数据库执行下单操作
        itOrderService.secKill(user, goodsVo);
    }
}

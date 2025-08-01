package com.example.seckilldemo.rabbitmq;

import com.example.seckilldemo.config.RabbitMQTopicConfig;
import com.example.seckilldemo.vo.SeckillMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 消息发送者
 *
 * @author: LC
 * @date 2022/3/7 7:42 下午
 * @ClassName: MQSender
 */
@Service
@Slf4j
public class MQSender {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    /*
    * 秒杀信息 入 消息队列
    * 发送秒杀信息
    * */
    public void SendSeckillMessage(String seckillMessage) {
        log.info("发送消息"+seckillMessage);
        rabbitTemplate.convertAndSend("seckillExchange", "seckill.message", seckillMessage);
    }

    public void sendSeckillMessage(String seckillMessage) {
        log.info("发送消息"+seckillMessage);
        rabbitTemplate.convertAndSend("seckillExchange", "seckill.message", seckillMessage);
    }

}

package com.example.seckilldemo.controller;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.seckilldemo.config.AccessLimit;
import com.example.seckilldemo.entity.TOrder;
import com.example.seckilldemo.entity.TSeckillOrder;
import com.example.seckilldemo.entity.TUser;
import com.example.seckilldemo.exception.GlobalException;
import com.example.seckilldemo.rabbitmq.MQSender;
import com.example.seckilldemo.service.ITGoodsService;
import com.example.seckilldemo.service.ITOrderService;
import com.example.seckilldemo.service.ITSeckillOrderService;
import com.example.seckilldemo.utils.JsonUtil;
import com.example.seckilldemo.vo.GoodsVo;
import com.example.seckilldemo.vo.RespBean;
import com.example.seckilldemo.vo.RespBeanEnum;
import com.example.seckilldemo.vo.SeckillMessage;
import com.wf.captcha.ArithmeticCaptcha;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.models.auth.In;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 秒杀
 *
 * @author: LC
 * @date 2022/3/4 11:34 上午
 * @ClassName: SeKillController
 */
@Slf4j
@Controller
@RequestMapping("/seckill")
@Api(value = "秒杀", tags = "秒杀")
public class SeKillController implements InitializingBean {

    @Autowired
    private ITGoodsService itGoodsService;
    @Autowired
    private ITSeckillOrderService itSeckillOrderService;
    @Autowired
    private ITOrderService orderService;
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private MQSender mqSender;
    @Autowired
    private RedisScript<Long> redisScript;

    private Map<Long, Boolean> EmptyStockMap = new HashMap<>();     // 内存 二级缓存

    /*
    * 自定义秒杀功能
    * */
    @RequestMapping(value = "/myDoSeckill", method = RequestMethod.POST)
    @ResponseBody
    public RespBean myDoSeckill(TUser user, Long goodsId) {
        if (user == null) {
            // 当前用户未通过认证
            return RespBean.error(RespBeanEnum.SESSION_ERROR);
        }
        // 二级缓存 判断库存是否为空
        if (EmptyStockMap.get(goodsId)) {
            // 当前库存为空 直接返回
            return RespBean.error(RespBeanEnum.EMPTY_STOCK);
        }
        // 基于 Redis 操作
        ValueOperations valueOperations = redisTemplate.opsForValue();
        // 1、判断用户是否重复抢购 Redis 中进行判断
        TSeckillOrder seckillOrder = (TSeckillOrder) valueOperations.get("order:" + user.getId() + ":" + goodsId);
        if (seckillOrder != null) {
            // 抢购记录 存在 重复抢购
            return RespBean.error(RespBeanEnum.REPEATE_ERROR);
        }
        // 2、Redis 库存 递减    预减库存操作
        Long stock = valueOperations.decrement("seckillGoods:" + goodsId);  // 原子操作
        if (stock < 0) {
            // 重置 二级缓存 商品库存
            EmptyStockMap.put(goodsId, true);
            // 当前 库存 不足
            valueOperations.increment("seckillGoods:" + goodsId);   // 原子操作
            return RespBean.error(RespBeanEnum.EMPTY_STOCK);
        }
        // 3、当前库存充足 下单操作    下单请求 送到 RabbitMQ
        SeckillMessage seckillMessage = new SeckillMessage(user, goodsId);  // 构造消息 提交到 消息队列中
        mqSender.SendSeckillMessage(JsonUtil.object2JsonStr(seckillMessage));

        // 当前请求排队中 客户端轮询结果 是否抢购成功
        return RespBean.success(0);
    }



    @ApiOperation("获取验证码")
    @GetMapping(value = "/captcha")
    public void verifyCode(TUser tUser, Long goodsId, HttpServletResponse response) {
        if (tUser == null || goodsId < 0) {
            throw new GlobalException(RespBeanEnum.REQUEST_ILLEGAL);
        }
        //设置请求头为输出图片的类型
        response.setContentType("image/jpg");
        response.setHeader("Pargam", "No-cache");
        response.setHeader("Cache-Control", "no-cache");
        response.setDateHeader("Expires", 0);
        //生成验证码
        ArithmeticCaptcha captcha = new ArithmeticCaptcha(130, 32, 3);
        redisTemplate.opsForValue().set("captcha:" + tUser.getId() + ":" + goodsId, captcha.text(), 300, TimeUnit.SECONDS);
        try {
            captcha.out(response.getOutputStream());
        } catch (IOException e) {
            log.error("验证码生成失败", e.getMessage());
        }
    }

    /**
     * 获取秒杀地址
     *
     * @param tuser
     * @param goodsId
     * @param captcha
     * @return com.example.seckilldemo.vo.RespBean
     * @author LiChao
     * @operation add
     * @date 4:04 下午 2022/3/9
     **/
    @ApiOperation("获取秒杀地址")
    @AccessLimit(second = 5, maxCount = 5, needLogin = true)
    @GetMapping(value = "/path")
    @ResponseBody
    public RespBean getPath(TUser tuser, Long goodsId, String captcha, HttpServletRequest request) {
        if (tuser == null) {
            return RespBean.error(RespBeanEnum.SESSION_ERROR);
        }

        boolean check = orderService.checkCaptcha(tuser, goodsId, captcha);
        if (!check) {
            return RespBean.error(RespBeanEnum.ERROR_CAPTCHA);
        }
        String str = orderService.createPath(tuser, goodsId);
        return RespBean.success(str);
    }


    /**
     * 获取秒杀结果
     *
     * @param tUser
     * @param goodsId
     * @return orderId 成功 ；-1 秒杀失败 ；0 排队中
     * @author LiChao
     * @operation add
     * @date 7:04 下午 2022/3/8
     **/
    @ApiOperation("获取秒杀结果")
    @GetMapping("getResult")
    @ResponseBody
    public RespBean getResult(TUser tUser, Long goodsId) {
        if (tUser == null) {
            return RespBean.error(RespBeanEnum.SESSION_ERROR);
        }
        Long orderId = itSeckillOrderService.getResult(tUser, goodsId);
        return RespBean.success(orderId);
    }

    /**
     * 最终秒杀功能
     *
     * @param user
     * @param goodsId
     * @return java.lang.String
     * @author LC
     * @operation add
     * @date 11:36 上午 2022/3/4
     **/
    @ApiOperation("秒杀功能")
    @RequestMapping(value = "/{path}/doSeckill", method = RequestMethod.POST)
    @ResponseBody
    public RespBean doSecKill(@PathVariable String path, TUser user, Long goodsId) {
        if (user == null) {
            return RespBean.error(RespBeanEnum.SESSION_ERROR);
        }
        // 优化后代码
        ValueOperations valueOperations = redisTemplate.opsForValue();
        boolean check = orderService.checkPath(user, goodsId, path);
        if (!check) {
            return RespBean.error(RespBeanEnum.REQUEST_ILLEGAL);
        }

        //判断是否重复抢购
        TSeckillOrder tSeckillOrder = (TSeckillOrder) redisTemplate.opsForValue().get("order:" + user.getId() + ":" + goodsId);
        if (tSeckillOrder != null) {
            return RespBean.error(RespBeanEnum.REPEATE_ERROR);
        }
        // 内存标记，减少Redis的访问  EmptyStockMap 理解成二级缓存 当前库存为0 减少对 Redis 的访问
        if (EmptyStockMap.get(goodsId)) {
            return RespBean.error(RespBeanEnum.EMPTY_STOCK);
        }
        //预减库存
//        Long stock = valueOperations.decrement("seckillGoods:" + goodsId);
        Long stock = (Long) redisTemplate.execute(redisScript, Collections.singletonList("seckillGoods:" + goodsId), Collections.EMPTY_LIST);
        if (stock < 0) {
            EmptyStockMap.put(goodsId, true);
            valueOperations.increment("seckillGoods:" + goodsId);
            return RespBean.error(RespBeanEnum.EMPTY_STOCK);
        }
        SeckillMessage seckillMessag = new SeckillMessage(user, goodsId);
        mqSender.sendSeckillMessage(JsonUtil.object2JsonStr(seckillMessag));
        return RespBean.success(0);
    }

    /**
     * 秒杀功能-1
     * update排他+唯一索引实现秒杀(没有做order页面静态化)
     * @param model
     * @param user
     * @param goodsId
     * @return java.lang.String
     * @author LC
     * @operation add
     * @date 11:36 上午 2022/3/4
     **/
    @ApiOperation("秒杀功能-最基础的秒杀功能")
    @RequestMapping(value = "/doSeckill1", method = RequestMethod.POST)
    public String doSecKill1(Model model, TUser user, Long goodsId) {
//        System.out.println("in doSeckill1");
        model.addAttribute("user", user);
        GoodsVo goodsVo = itGoodsService.findGoodsVobyGoodsId(goodsId);
        if (goodsVo.getStockCount() < 1) {
            model.addAttribute("errmsg", RespBeanEnum.EMPTY_STOCK.getMessage());
            return "secKillFail";
        }

        // 方法二： 使用redis判断是否重复抢购
        TSeckillOrder tSeckillOrder = (TSeckillOrder) redisTemplate.opsForValue().get("order:" + user.getId() + ":" + goodsId);
        if (tSeckillOrder != null) {
            model.addAttribute("errmsg", RespBeanEnum.REPEATE_ERROR.getMessage());
            return "secKillFail";
        }
        TOrder tOrder = orderService.secKill(user, goodsVo);
        model.addAttribute("order", tOrder);
        model.addAttribute("goods", goodsVo);
        return "orderDetail";
    }


    /**
     * 秒杀功能 2
     * order界面静态化 +  redis预减库存 + 内存标记 + MQ
     * @param model
     * @param user
     * @param goodsId
     * @return java.lang.String
     * @author LC
     * @operation add
     * @date 11:36 上午 2022/3/4
     **/
    @ApiOperation("秒杀功能2-在1基础上加上 静态化  MQ  redis")  // p54
    @RequestMapping(value = "/doSeckill2", method = RequestMethod.POST)
    @ResponseBody
    public RespBean doSecKill2(Model model, TUser user, Long goodsId) {
//        System.out.println("in doSeckill2");

        Long v = redisTemplate.opsForValue().decrement("seckillGoods:" + goodsId);
        if (v < 0){
            redisTemplate.opsForValue().increment("seckillGoods:" + goodsId);
            return RespBean.error(RespBeanEnum.EMPTY_STOCK);
        }

        // 方法二： 使用redis判断是否重复抢购
        TSeckillOrder tSeckillOrder = (TSeckillOrder) redisTemplate.opsForValue().get("order:" + user.getId() + ":" + goodsId);
        if (tSeckillOrder != null) {
            return RespBean.error(RespBeanEnum.REPEATE_ERROR);
        }

        SeckillMessage seckillMessage = new SeckillMessage(user, goodsId);

        mqSender.sendSeckillMessage(JSON.toJSONString(seckillMessage));

        return RespBean.success();
    }

    /**
     * 系统初始化，把商品库存数量加载到 Redis
     *
     * @param
     * @return void
     * @author LiChao
     * @operation add
     * @date 6:29 下午 2022/3/8
     **/
    @Override
    public void afterPropertiesSet() throws Exception {
        List<GoodsVo> list = itGoodsService.findGoodsVo();
        if (CollectionUtils.isEmpty(list)) {
            return;
        }
        list.forEach(goodsVo -> {
            // 商品 id -> 商品 库存 加载到 Redis 中
            redisTemplate.opsForValue().set("seckillGoods:" + goodsVo.getId(), goodsVo.getStockCount());
            EmptyStockMap.put(goodsVo.getId(), false);  // 初始化 内存 二级缓存 当前获取均充足
            // false:有库存 true:没有库存
        });
    }
}

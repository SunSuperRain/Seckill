package com.example.seckilldemo.utils;

import com.example.seckilldemo.entity.TUser;
import com.example.seckilldemo.service.ITUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 生成用户工具类
 *
 * @author: LC
 * @date 2022/3/4 3:29 下午
 * @ClassName: UserUtil
 */
@Component
public class UserUtil {

    @Autowired
    ITUserService tUserService;

  public void createUser(int n) throws IOException {
        List<TUser> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            TUser tUser = new TUser();
            tUser.setId(1233L + i);
            tUser.setNickname("user" + i);
            tUser.setSalt("1a2b3c");
            tUser.setPassword("6e0a7fe692684372437c9e508508990d");
            list.add(tUser);
        }
        tUserService.saveBatch(list);
        System.out.println("✅ 成功创建 " + n + " 个用户");
    }

    public static void main(String[] args) {}
}

package com.vincent.demo.service.impl;

import com.vincent.demo.service.IDemoService;
import com.vincent.mvcframework.annotation.VincentService;

/**
 * 核心业务逻辑
 */
@VincentService
public class DemoService implements IDemoService {

    @Override
    public String get(String name) {
        return "My name is " + name + " from service.";
    }
}

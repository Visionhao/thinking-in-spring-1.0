package com.vincent.demo.mvc.action;


import com.vincent.demo.service.IDemoService;
import com.vincent.mvcframework.annotation.VincentAutowired;
import com.vincent.mvcframework.annotation.VincentController;
import com.vincent.mvcframework.annotation.VincentRequestMapping;
import com.vincent.mvcframework.annotation.VincentRequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@VincentController
@VincentRequestMapping("/demo")
public class DemoAction {

    @VincentAutowired
    private IDemoService demoService;

    /**
     * 查询
     * @param request
     * @param response
     * @param name
     */
    @VincentRequestMapping("/query")
    public void query(HttpServletRequest request, HttpServletResponse response, @VincentRequestParam("name") String name){
        String result = demoService.get(name);
        try {
            response.getWriter().write(result);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 数字相加
     * @param request
     * @param response
     * @param a
     * @param b
     */
    /*@VincentRequestMapping("/add")
    public void add(HttpServletRequest request,HttpServletResponse response,
                    @VincentRequestParam("a") Integer a, @VincentRequestParam("b") Integer b){
        try {
            response.getWriter().write(a + "+" + b + "=" +(a + b));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @VincentRequestMapping("/sub")
    public void sub(HttpServletRequest request,HttpServletResponse response,
                    @VincentRequestParam("a") Integer a, @VincentRequestParam("b") Integer b){
        try {
            response.getWriter().write(a + "-" + b + "=" +(a - b));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @VincentRequestMapping("/remove")
    public String remove(@VincentRequestParam("id") Integer id){
        return "" + id;
    }*/

}

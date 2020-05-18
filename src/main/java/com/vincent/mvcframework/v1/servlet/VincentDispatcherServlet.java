package com.vincent.mvcframework.v1.servlet;

import com.vincent.mvcframework.annotation.VincentAutowired;
import com.vincent.mvcframework.annotation.VincentController;
import com.vincent.mvcframework.annotation.VincentRequestMapping;
import com.vincent.mvcframework.annotation.VincentService;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * 把请求交给dispatcherServlet去进行转发
 */
public class VincentDispatcherServlet extends HttpServlet {

    private Map<String,Object> mapping = new HashMap<String,Object>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            doDispatch(req, resp);
        }catch (Exception e){
            resp.getWriter().write("500 Exception " + Arrays.toString(e.getStackTrace()));
        }
    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        //获取请求的uri
        String url = req.getRequestURI();
        //获取请求的内容路径
        String contentPath = req.getContextPath();
        //替换为 /
        url = url.replace(contentPath,"").replaceAll("/+","/");
        //如果请求的url在map中不存在，则报错
        if(!this.mapping.containsKey(url)){
            resp.getWriter().write("404 NOT Found!!!");
            return;
        }
        //把 url 强转为 Method
        Method method = (Method) this.mapping.get(url);
        //获取请求的参数map
        Map<String,String[]> params = req.getParameterMap();
        // this.mapping.get(method.getDeclaringClass().getName()) 获取当前反射等到的name作为参数
        // new Object[]{req,resp,params.get("name")[0]} 只获取当前的第一个参数
        method.invoke(this.mapping.get(method.getDeclaringClass().getName()),new Object[]{req,resp,params.get("name")[0]});
    }

    //init方法肯定干得的初始化的工作
    //inti首先我得初始化所有的相关的类，IOC容器、servletBean
    @Override
    public void init(ServletConfig config) throws ServletException {
        //输出流
        InputStream is = null;
        try{
            //jdk配置类
            Properties properties = new Properties();
            //通过流读取配置文件中的location
            is = this.getClass().getClassLoader().getResourceAsStream(config.getInitParameter("contextConfigLocation"));
            //加载
            properties.load(is);
            //根据key获取路径
            String scanPackage = properties.getProperty("scanPackage");
            //把所有编译过的类的名字放进map
            doScanner(scanPackage);
            //遍历map
            for(String className : mapping.keySet()){
                //如果className不存在. 则跳出
                if(!className.contains(".")){
                    continue;
                }
                //通过className 反射获取 信息
                Class<?> clazz = Class.forName(className);
                //如果当前是controller注解
                if(clazz.isAnnotationPresent(VincentController.class)){
                    //把当前类存入到map中，
                    mapping.put(className,clazz.newInstance());
                    String baseUrl = "";
                    //如果当前是 requestMapping 注解
                    if(clazz.isAnnotationPresent(VincentRequestMapping.class)){
                        //获取注解
                        VincentRequestMapping requestMapping = clazz.getAnnotation(VincentRequestMapping.class);
                        //获取在注解上面的value
                        baseUrl = requestMapping.value();
                    }
                    //获取所有的方法列表
                    Method[] methods = clazz.getMethods();
                    //遍历方法列表
                    for(Method method : methods){
                        //方法上如果没有这个注解，则跳出
                        if(!method.isAnnotationPresent(VincentRequestMapping.class)){
                            continue;
                        }
                        VincentRequestMapping requestMapping = clazz.getAnnotation(VincentRequestMapping.class);
                        //获取完整的 url 请求
                        String url = ("/" + baseUrl + "/" + requestMapping.value()).replaceAll("/+","/");
                        //把 url 放进 map 中
                        mapping.put(url,method);
                        System.out.println(" Mapped " + url + "," + method);
                    }
                }else if(clazz.isAnnotationPresent(VincentService.class)){
                    //获取 service 注解
                    VincentService service = clazz.getAnnotation(VincentService.class);
                    //获取注解方法的 value
                    String beanName = service.value();
                    //如果 beanName 不存在的话，则拿当前的 clazz.getName() 为beanName
                    if("".equals(beanName)){
                        beanName = clazz.getName();
                    }
                    //实例化
                    Object instance = clazz.newInstance();
                    //把 beanName 放到map中
                    mapping.put(beanName,instance);
                    //遍历接口放到map中
                    for(Class<?> i : clazz.getInterfaces()){
                        mapping.put(i.getName(),instance);
                    }

                }else{
                    continue;
                }
            }
            //遍历map
            for(Object object : mapping.values()){
                if(object == null){
                    continue;
                }
                Class clazz = object.getClass();
                if(clazz.isAnnotationPresent(VincentController.class)){
                    //获取类中的所有信息，这包括公共字段、受保护字段、默认(包)访问和私有字段，但不包括继承字段
                    Field[] fields = clazz.getDeclaredFields();
                    for(Field field : fields){
                        //判断是否存在 autowired 注解 ，不存在则跳出不处理
                        if(!field.isAnnotationPresent(VincentAutowired.class)){
                            continue;
                        }
                        //获取 autowired 注解
                        VincentAutowired autowired = field.getAnnotation(VincentAutowired.class);
                        //获取value
                        String beanName = autowired.value();
                        if("".equals(beanName)){
                            beanName = field.getType().getName();
                        }
                        //强制访问
                        field.setAccessible(true);
                        field.set(mapping.get(clazz.getName()),mapping.get(beanName));
                    }
                }

            }
            
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            if(is != null){
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        System.out.println(" Vincent MVC Framework is init ");
    }

    //扫描当前的包路径，需要进行转义
    private void doScanner(String scanPackage) {
        //把 . 全部替换成 /
        URL url = this.getClass().getClassLoader().getResource("/" + scanPackage.replaceAll("\\.","/"));
        //获取编译过之后的文件的目录
        File classDir = new File(url.getFile());
        //循环遍历文件
        for(File file : classDir.listFiles()){
            //如果当前是目录的话，递归操作
            if(file.isDirectory()){
                doScanner(scanPackage + "." +file.getName());
            }else{
                //如果不是以.class结尾的，跳出
                if(!file.getName().endsWith(".class")){
                    continue;
                }
                //获取以class结尾的文件，并把 .class 替换为空
                String clazzName = (scanPackage + "." + file.getName().replace(".class",""));
                //把 clazzName 丢进 map中
                mapping.put(clazzName,null);
            }
        }
    }
}

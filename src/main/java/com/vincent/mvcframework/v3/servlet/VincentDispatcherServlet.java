package com.vincent.mvcframework.v3.servlet;

import com.vincent.mvcframework.annotation.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VincentDispatcherServlet extends HttpServlet {

    //保存 application.properties 配置文件中的内容
    private Properties contextConfig = new Properties();

    //保存扫描的所有的类名
    private List<String> classNames = new ArrayList<String>();

    //IoC容器
    private Map<String,Object> ioc = new HashMap<String,Object>();

    private List<Handler> handlerMapping = new ArrayList<Handler>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            doDispatch(req, resp);
        } catch (Exception e) {
            e.printStackTrace();
            resp.getWriter().write("500 Exection,Detail : " + Arrays.toString(e.getStackTrace()));
        }
    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        Handler handler = getHandler(req);
        if(handler == null){
            resp.getWriter().write("404 Not Found!!!");
            return;
        }
        //获取方法的形参列表
        Class<?>[] paramTypes = handler.getParamTyeps();
        Object[] paramValues = new Object[paramTypes.length];
        Map<String,String[]> params = req.getParameterMap();
        for(Map.Entry<String,String[]> parm : params.entrySet()){
            String value = Arrays.toString(parm.getValue()).replaceAll("\\[|\\]","").replaceAll("\\s",",");
            if(!handler.paramIndexMapping.containsKey(parm.getKey())){
                continue;
            }
            int index = handler.paramIndexMapping.get(parm.getKey());
            paramValues[index] = convert(paramTypes[index],value);
        }

        if(handler.paramIndexMapping.containsKey(HttpServletRequest.class.getName())){
            int reqIndex = handler.paramIndexMapping.get(HttpServletRequest.class.getName());
            paramValues[reqIndex] = req;
        }

        if(handler.paramIndexMapping.containsKey(HttpServletResponse.class.getName())){
            int respIndex = handler.paramIndexMapping.get(HttpServletResponse.class.getName());
            paramValues[respIndex] = resp;
        }

        Object returnValue = handler.method.invoke(handler.controller,paramValues);
        if(returnValue == null || returnValue instanceof Void){
            return;
        }
        resp.getWriter().write(returnValue.toString());
    }

    /**
     * url 传过来的参数都是Sting 类型的，HTTP是基于字符串协议
     * 只需要把String 转换为任意类型就好
     * @param type
     * @param value
     * @return
     */
    private Object convert(Class<?> type,String value){
        //如果是int,转为String
        if(Integer.class == type){
            return Integer.valueOf(value);
        }else if(Double.class == type){
            return Double.valueOf(value);
        }
        return value;
    }

    private Handler getHandler(HttpServletRequest req) {
        if(handlerMapping.isEmpty()) {
            return null;
        }
        //获取绝对路径
        String url = req.getRequestURI();
        //处理成相对路径
        String contextPath = req.getContextPath();
        url = url.replaceAll(contextPath,"").replaceAll("/+","/");

        for(Handler handler : this.handlerMapping){
            Matcher matcher = handler.getPattern().matcher(url);
            if(!matcher.matches()){
                continue;
            }
            return handler;
        }
        return null;
    }


    //初始化
    @Override
    public void init(ServletConfig config) throws ServletException {
        //1、加载配置文件
        doLoadConfig(config.getInitParameter("contextConfigLocation"));

        //2、扫描相关的类
        doScanner(contextConfig.getProperty("scanPackage"));

        //3、初始化扫描到的类，并且将它们放入到IoC容器之中
        doInstance();

        //4、完成依赖注入
        doAutowired();

        //5、初始化HandlerMapping
        initHandlerMapping();

        System.out.println("GP Spring framework is init.");
    }

    //初始化url 和Method 的一对一对应关系
    private void initHandlerMapping() {
        if(ioc.isEmpty()){
            return;
        }
        for(Map.Entry<String,Object> entry : ioc.entrySet()){
            Class<?> clazz = entry.getValue().getClass();

            if(!clazz.isAnnotationPresent(VincentController.class)){
                continue;
            }

            //保存写在类上面的@VincentRequestMapping("/demo")
            String baseUrl = "";
            if(clazz.isAnnotationPresent(VincentRequestMapping.class)){
                VincentRequestMapping requestMapping = clazz.getAnnotation(VincentRequestMapping.class);
                baseUrl = requestMapping.value();
            }

            // 默认获取所有的public 方法
            for(Method method : clazz.getMethods()){
                if(!method.isAnnotationPresent(VincentRequestMapping.class)){
                    continue;
                }

                VincentRequestMapping requestMapping = method.getAnnotation(VincentRequestMapping.class);
                String regex = ("/" + baseUrl + "/" + requestMapping.value()).replaceAll("/+","/");
                Pattern pattern = Pattern.compile(regex);
                this.handlerMapping.add(new Handler(pattern,entry.getValue(),method));
                System.out.println("Mapped :" + pattern + "," + method);
            }

        }


    }

    //自动依赖注入
    private void doAutowired() {
        if(ioc.isEmpty()){
            return;
        }
        for(Map.Entry<String,Object> entry : ioc.entrySet()){
            //Declared 所有的，特定的字段，包括private/protected/default
            //正常来说，普通的OOP编程只能拿到public 的属性
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for(Field field : fields){
                if(!field.isAnnotationPresent(VincentAutowired.class)){
                    continue;
                }
                VincentAutowired autowired = field.getAnnotation(VincentAutowired.class);

                //
                String beanName = autowired.value().trim();
                if("".equals(beanName)){
                    //获得接口的类型，作为key 待会拿到这个key到ioc容器中去取值
                    beanName = field.getType().getName();
                }

                //如果是public 以外的修饰符，只要加了@Autowired注解，都要强制赋值
                field.setAccessible(true);

                try {
                    //用反射机制，动态给字段赋值
                    field.set(entry.getValue(),ioc.get(beanName));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }

        }


    }

    private void doInstance() {
        // 初始化，为DI做准备
        if(classNames.isEmpty()){
            return;
        }
        try {
            for(String className : classNames){
                Class<?> clazz = Class.forName(className);
                if(clazz.isAnnotationPresent(VincentController.class)){
                    Object instance = clazz.newInstance();
                    //Spring 默认类名首字母小写
                    String beanName = toLowerFirstCase(clazz.getSimpleName());
                    ioc.put(beanName,instance);
                }else if(clazz.isAnnotationPresent(VincentService.class)){
                    //1、自定义的beanName
                    VincentService service = clazz.getAnnotation(VincentService.class);
                    String beanName = service.value();
                    //2、默认类名首字母小写
                    if("".equals(beanName.trim())){
                        beanName = toLowerFirstCase(clazz.getSimpleName());
                    }
                    Object instance = clazz.newInstance();
                    ioc.put(beanName,instance);

                    //3、根据类型自动赋值，投机取巧的方式
                    for(Class<?> i : clazz.getInterfaces()){
                        if(ioc.containsKey(i.getName())){
                            throw new Exception("The “" + i.getName() + "” is exists!!");
                        }
                        //把接口的类型直接当成key了
                        ioc.put(i.getName(),instance);
                    }
                }else{
                    continue;
                }
            }

        }catch (Exception e){
            e.printStackTrace();
        }

    }

    private String toLowerFirstCase(String simpleName) {
        char[] chars = simpleName.toCharArray();
        // 之所以加，是因为大小写字母的ASCII码相差32，而且大写字母的ASCII码要小于小写字母的ASCII码
        // 在Java中，对char做算学运算，实际上就是对ASCII码做算学运算
        chars[0] += 32;
        return String.valueOf(chars);
    }

    //扫描出相关的类
    private void doScanner(String scanPackage) {
        // scanPackage = com.vincent.demo 存储的是包路径
        // 转换为文件路径，实际上就是把 . 替换为 / 就OK了
        // classpath
        URL url = this.getClass().getClassLoader().getResource("/" + scanPackage.replaceAll("\\.","/"));
        File classPath = new File(url.getFile());
        for(File file : classPath.listFiles()){
            if(file.isDirectory()){
                doScanner(scanPackage + "." + file.getName());
            }else{
                if(!file.getName().endsWith(".class")){
                    continue;
                }
                String className = (scanPackage + "." +file.getName().replace(".class",""));
                classNames.add(className);
            }

        }

    }

    //加载配置文件
    private void doLoadConfig(String contextConfigLocation) {
        // 直接从类路径下找到Spring主配置文件所在的路径
        // 并且将其读取出来放到Properties对象中
        // 相当于scanPackage=com.vincent.demo 从文件中保存到了内存中
        InputStream fis = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);
        try {
            contextConfig.load(fis);
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            if(null != fis){
                try {
                    fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    //保存一个url 和 一个Method的关系
    public class Handler {
        //必须把url放到HandleMapping才好理解
        private Pattern pattern;
        private Method method;
        private Object controller;
        private Class<?> [] paramTyeps;

        public Pattern getPattern() {
            return pattern;
        }

        public Method getMethod() {
            return method;
        }

        public Object getController() {
            return controller;
        }

        public Class<?>[] getParamTyeps() {
            return paramTyeps;
        }

        //形参列表
        //参数的名字作为key,参数的顺序，位置作为值
        private Map<String,Integer> paramIndexMapping;

        public Handler(Pattern pattern, Object controller,Method method) {
            this.pattern = pattern;
            this.method = method;
            this.controller = controller;
            this.paramTyeps = method.getParameterTypes();
            this.paramIndexMapping = new HashMap<String, Integer>();
            putParamIndexMapping(method);
        }

        private void putParamIndexMapping(Method method){
            /**
             * 提取方法中加了注解的参数
             * 把方法上的注释拿到，得到的是一个二维数组
             * 因为一个参数可以有多个注解，而一个方法又有多个参数
             */
            Annotation [] [] pa = method.getParameterAnnotations();
            for (int i = 0; i < pa.length; i++) {
                for (Annotation a : pa[i]){
                    if(a instanceof VincentRequestParam){
                        String paramName = ((VincentRequestParam) a ).value();
                        if(!"".equals(paramName.trim())){
                            paramIndexMapping.put(paramName,i);
                        }
                    }
                }
            }

            //提取方法中的request 和response 参数
            Class<?>[] parmasTypes = method.getParameterTypes();
            for (int i = 0; i < parmasTypes.length; i++) {
                Class<?> type = parmasTypes[i];
                if(type == HttpServletRequest.class || type == HttpServletResponse.class){
                    paramIndexMapping.put(type.getName(),i);
                }
            }
        }
    }
}

package com.vincent.mvcframework.annotation;

import java.lang.annotation.*;

/**
 * 自定义注解 autowired
 */

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface VincentAutowired {

    String value() default "";
}

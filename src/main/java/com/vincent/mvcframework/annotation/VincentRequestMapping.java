package com.vincent.mvcframework.annotation;

import java.lang.annotation.*;
/**
 * 自定义注解 requestMapping
 */
@Target({ElementType.TYPE,ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface VincentRequestMapping {

    String value() default "";
}

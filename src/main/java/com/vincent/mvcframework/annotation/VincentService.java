package com.vincent.mvcframework.annotation;

import java.lang.annotation.*;
/**
 * 自定义注解 service
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface VincentService {

    String value() default "";
}

package com.vincent.mvcframework.annotation;

import java.lang.annotation.*;
/**
 * 自定义注解requestParam
 */
@Target({ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface VincentRequestParam {

    String value() default "";
}

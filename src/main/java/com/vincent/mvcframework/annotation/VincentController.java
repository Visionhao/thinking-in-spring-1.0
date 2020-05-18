package com.vincent.mvcframework.annotation;

import java.lang.annotation.*;
/**
 *  自定义注解 controller
 */

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface VincentController {

    String value() default "";

}

package com.digitalsanctuary.spring.user.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation is used to indicate that the current user should be excluded from the model for the request. This annotation can be applied to a
 * method or a class. It should be used when the user in model mode is set to opt-out.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface ExcludeUserFromModel {
}

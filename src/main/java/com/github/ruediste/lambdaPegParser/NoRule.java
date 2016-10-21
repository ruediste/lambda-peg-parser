package com.github.ruediste.lambdaPegParser;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * When present on a method of a {@link Parser}, no rule weaving/enhancing will
 * be performed on the method
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface NoRule {

}

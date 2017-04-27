package com.example.meitu.layouttest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/*********************************************
 * Author:  2017/2/24
 * ********************************************
 * Version: 版本
 * Author:
 * Changes: 更新点
 * ********************************************
 */
@Target(ElementType.TYPE)
public @interface Table {
    String tableName() default "className";
}

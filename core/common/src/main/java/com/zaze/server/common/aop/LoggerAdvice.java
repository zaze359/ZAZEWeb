package com.zaze.server.common.aop;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

@Aspect
@Component
@Slf4j
public class LoggerAdvice {

    @Before("within(com.zaze..*) && @annotation(loggerManage)")
    public void addBeforeLogger(JoinPoint joinPoint, LoggerManage loggerManage) {
        log.info("开始执行【{}】", loggerManage.description());
        log.info(joinPoint.getSignature().toString());
        log.info(parseParams(joinPoint.getArgs()));
    }

    @AfterReturning(value = "within(com.zaze..*) && @annotation(loggerManage)", returning = "result")
    public void addAfterReturningLogger(JoinPoint joinPoint, LoggerManage loggerManage, Object result) {
        log.info("执行【{}】; 结果: {}", loggerManage.description(), result);
        log.info("结束执行【{}】", loggerManage.description());
    }

    @AfterThrowing(pointcut = "within(com.zaze..*) && @annotation(loggerManage)", throwing = "ex")
    public void addAfterThrowingLogger(JoinPoint joinPoint, LoggerManage loggerManage, Exception ex) {
        log.error("执行【{}】异常", loggerManage.description(), ex);
    }


    private String parseParams(Object[] params) {
        if (null == params || params.length <= 0 || params.length > 1024) {
            return "";
        }
        StringBuilder param = new StringBuilder("传入参数[{}] ");
        for (Object obj : params) {
            param.append(obj.toString()).append("  ");
        }
        return param.toString();
    }

}

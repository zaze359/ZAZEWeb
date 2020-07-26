package com.zaze.server.comm.aop;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Aspect
@Service
public class LoggerAdvice {

	protected Logger logger =  LoggerFactory.getLogger(this.getClass());

	@Before("within(com.zaze..*) && @annotation(loggerManage)")
	public void addBeforeLogger(JoinPoint joinPoint, LoggerManage loggerManage) {
		logger.info("执行 " + loggerManage.description() + " 开始");
		logger.info(joinPoint.getSignature().toString());
		logger.info(parseParams(joinPoint.getArgs()));
	}
	
	@AfterReturning("within(com.zaze..*) && @annotation(loggerManage)")
	public void addAfterReturningLogger(JoinPoint joinPoint, LoggerManage loggerManage) {
		logger.info("执行 " + loggerManage.description() + " 结束");
	}
	
	@AfterThrowing(pointcut = "within(com.zaze..*) && @annotation(loggerManage)", throwing = "ex")
	public void addAfterThrowingLogger(JoinPoint joinPoint, LoggerManage loggerManage, Exception ex) {
		logger.error("执行 " + loggerManage.description() + " 异常", ex);
	}

	private String parseParams(Object[] params) {
		if (null == params || params.length <= 0 || params.length >1024) {
			return "";
		}
		StringBuilder param = new StringBuilder("传入参数[{}] ");
		for (Object obj : params) {
			param.append(obj.toString()).append("  ");
		}
		return param.toString();
	}
	
}

package com.zaze.server.common.log;


import com.zaze.server.common.utils.StackTraceHelper;
import com.zaze.server.common.utils.TraceHelper;
import lombok.extern.slf4j.Slf4j;

/**
 * Desc :
 *
 * @author zaze
 * @version 2017/3/23 - 下午11:52 1.0
 */
@Slf4j
public class ZLog {
    private static ZLogFace logFace;
    private static boolean alwaysPrint = false;
    private static boolean needStack = false;
    // --------------------------------------------------
    private static boolean E = true;
    private static boolean W = true;
    private static boolean I = true;
    private static boolean D = true;

    static {
        registerLogCaller(ZLog.class.getName());
//        registerLogCaller(Log.class.getName());
        registerLogCaller(TraceHelper.class.getName());
    }

    // ----------- D -----------
    public static void d(String tag, String message) {
        if (D) {
            if (alwaysPrint || logFace == null) {
                log.debug(tag + "{}", getStackTrace(message));
            }
            if (logFace != null) {
                logFace.d(tag + "{}", getStackTrace(message));
            }
        }
    }

    // ----------- I -----------
    public static void i(String tag, String message) {
        if (I) {
            if (alwaysPrint || logFace == null) {
                log.info(tag + "{}", getStackTrace(message));
            }
            if (logFace != null) {
                logFace.i(tag + "{}", getStackTrace(message));
            }
        }

    }

    // ----------- W -----------
    public static void w(String tag, String message) {
        if (W) {
            if (alwaysPrint || logFace == null) {
                log.warn(tag + "{}", getStackTrace(message));
            }
            if (logFace != null) {
                logFace.w(tag + "{}", getStackTrace(message));
            }
        }
    }

    public static void w(String tag, String message, Throwable e) {
        if (W) {
            if (alwaysPrint || logFace == null) {
                log.warn(tag + "{}", getStackTrace(message), e);
            }
            if (logFace != null) {
                logFace.w(tag + "{}", getStackTrace(message), e);
            }
        }
    }

    // ----------- E -----------
    public static void e(String tag, String message) {
        if (E) {
            if (alwaysPrint || logFace == null) {
                log.error(tag + "{}", getStackTrace(message));
            }
            if (logFace != null) {
                logFace.e(tag + "{}", getStackTrace(message));
            }
        }
    }

    public static void e(String tag, String message, Throwable e) {
        if (E) {
            if (alwaysPrint || logFace == null) {
                log.error(tag + "{}", getStackTrace(message), e);
            }
            if (logFace != null) {
                logFace.e(tag + "{}", getStackTrace(message), e);
            }
        }
    }

    // ---------------------------------------------------

    public static void setLogLevel(@ZLogLevel int level) {
        E = false;
        W = false;
        I = false;
        D = false;

        if (level >= ZLogLevel.ERROR) {
            E = true;
        }
        if (level >= ZLogLevel.WARN) {
            W = true;
        }
        if (level >= ZLogLevel.INFO) {
            I = true;
        }
        if (level >= ZLogLevel.DEBUG) {
            D = true;
        }
    }

    // ---------------------------------------------------
    // ---------------------------------------------------


    public static void registerLogCaller(String logCaller) {
        StackTraceHelper.registerStackCaller(logCaller);
    }

    public static void setNeedStack(boolean needStack) {
        ZLog.needStack = needStack;
    }

    public static boolean isNeedStack() {
        return ZLog.needStack;
    }

    private static String getTag(StackTraceElement ste) {
        return String.format("(%s:%d)[%s]", ste.getFileName(), ste.getLineNumber(), ste.getMethodName());
    }

    private static String getStackTrace(String message) {
        if (needStack) {
            return getTag(StackTraceHelper.callerStackTraceElement()) + ": " + message;
        } else {
            return message;
        }
    }

    public static void setLogFace(ZLogFace face) {
        logFace = face;
    }
    public static ZLogFace getLogFace() {
        return logFace;
    }

    public static void closeAllLog() {
        E = false;
        W = false;
        I = false;
        D = false;
    }

    public static void openAllLog() {
        E = true;
        W = true;
        I = true;
        D = true;
    }

    public static void openAlwaysPrint() {
        alwaysPrint = true;
    }

    public static void closeAlwaysPrint() {
        alwaysPrint = true;
    }
}

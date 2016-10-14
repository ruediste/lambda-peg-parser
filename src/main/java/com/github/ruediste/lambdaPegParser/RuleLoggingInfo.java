package com.github.ruediste.lambdaPegParser;

/**
 * Contains all information reported when logging rule invocations
 */
public class RuleLoggingInfo {
    public Class<?> parserClass;
    public String methodName;
    public Object[] arguments;
    public Class<?>[] argumentTypes;
    public Object result;
    public int index;

    public RuleLoggingInfo(RuleLoggingInfo existing) {
        parserClass = existing.parserClass;
        methodName = existing.methodName;
        arguments = existing.arguments;
        argumentTypes = existing.argumentTypes;
        result = existing.result;
        index = existing.index;
    }

    public RuleLoggingInfo() {
    }
}

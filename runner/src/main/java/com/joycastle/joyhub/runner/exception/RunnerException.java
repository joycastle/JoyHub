package com.joycastle.joyhub.runner.exception;

public class RunnerException extends RuntimeException {
    private final String code;

    public RunnerException(String code, String message) {
        super(message);
        this.code = code;
    }

    public RunnerException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}

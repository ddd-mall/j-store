package com.jstore.order.expired;

import lombok.extern.slf4j.Slf4j;

import java.util.function.Consumer;

@Slf4j
public class RetryTimes {

    public static RetryBuilder of(int maxRetryTimes) {
        return new RetryBuilder(maxRetryTimes);
    }

    public static class RetryBuilder {
        private final int maxRetryTimes;

        private RetryBuilder(int maxRetryTimes) {
            this.maxRetryTimes = maxRetryTimes;
        }

        public RetryExecution doWithRetry(ThrowableRunnable task) {
            return new RetryExecution(maxRetryTimes, task);
        }
    }

    public static class RetryExecution {
        private final int maxRetryTimes;
        private final ThrowableRunnable task;
        private Consumer<Throwable> exceptionHandler;

        private RetryExecution(int maxRetryTimes, ThrowableRunnable task) {
            this.maxRetryTimes = maxRetryTimes;
            this.task = task;
        }

        public RetryExecution whenException(Consumer<Throwable> exceptionHandler) {
            this.exceptionHandler = exceptionHandler;
            return this;
        }

        public boolean execute() {
            boolean success = false;
            int attemptCount = 0;
            while (attemptCount <= maxRetryTimes) {
                try {
                    task.run();
                    success = true;
                    break;
                } catch (Throwable e) {
                    attemptCount++;

                    // 如果设置了异常处理器，则调用它
                    if (exceptionHandler != null) {
                        try {
                            exceptionHandler.accept(e);
                        } catch (Exception handlerException) {
                            // 异常处理器本身抛出异常时，记录但不中断重试流程
                            log.error("Exception handler failed: {}", handlerException.getMessage());
                        }
                    }
                }
            }
            return success;
        }

        public void onFailure(Runnable onFailureCallback) {
            boolean success = execute();
            // 如果最终失败，执行失败回调
            if (!success && onFailureCallback != null) {
                onFailureCallback.run();
            }
        }
    }

    @FunctionalInterface
    public interface ThrowableRunnable {
        void run() throws Throwable;
    }

}

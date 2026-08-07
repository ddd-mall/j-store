/*
 * SPDX-FileCopyrightText: 2024-2026 潘少峰 (Peter Pan)
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jstore.order.expired;

import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

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
                            log.error(
                                    "Exception handler failed: {}", handlerException.getMessage());
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

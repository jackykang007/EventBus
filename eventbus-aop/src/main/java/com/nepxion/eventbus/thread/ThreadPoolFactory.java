package com.nepxion.eventbus.thread;

/**
 * <p>Title: Nepxion EventBus</p>
 * <p>Description: Nepxion EventBus AOP</p>
 * <p>Copyright: Copyright (c) 2017</p>
 * <p>Company: Nepxion</p>
 * @author Haojun Ren
 * @email 1394997@qq.com
 * @version 1.0
 */

import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component("threadPoolFactory")
public class ThreadPoolFactory {
    @Value("${" + ThreadConstant.THREAD_POOL_MULTI_MODE + "}")
    private boolean threadPoolMultiMode;

    @Value("${" + ThreadConstant.THREAD_POOL_NAME_CUSTOMIZED + "}")
    private boolean threadPoolNameCustomized;

    @Value("${" + ThreadConstant.THREAD_POOL_CORE_POOL_SIZE + "}")
    private int threadPoolCorePoolSize;

    @Value("${" + ThreadConstant.THREAD_POOL_MAXIMUM_POOL_SIZE + "}")
    private int threadPoolMaximumPoolSize;

    @Value("${" + ThreadConstant.THREAD_POOL_KEEP_ALIVE_TIME + "}")
    private long threadPoolKeepAliveTime;

    @Value("${" + ThreadConstant.THREAD_POOL_ALLOW_CORE_THREAD_TIMEOUT + "}")
    private boolean threadPoolAllowCoreThreadTimeout;

    @Value("${" + ThreadConstant.THREAD_POOL_QUEUE + "}")
    private String threadPoolQueue;

    @Value("${" + ThreadConstant.THREAD_POOL_QUEUE_CAPACITY + "}")
    private int threadPoolQueueCapacity;

    @Value("${" + ThreadConstant.THREAD_POOL_REJECTED_POLICY + "}")
    private String threadPoolRejectedPolicy;

    private volatile Map<String, ThreadPoolExecutor> threadPoolExecutorMap = new ConcurrentHashMap<String, ThreadPoolExecutor>();

    private ThreadPoolExecutor threadPoolExecutor;

    public ThreadPoolExecutor getThreadPoolExecutor(String threadPoolName) {
        if (threadPoolMultiMode) {
            ThreadPoolExecutor threadPoolExecutor = threadPoolExecutorMap.get(threadPoolName);
            if (threadPoolExecutor == null) {
                ThreadPoolExecutor newThreadPoolExecutor = createThreadPoolExecutor(threadPoolName);
                threadPoolExecutor = threadPoolExecutorMap.putIfAbsent(threadPoolName, newThreadPoolExecutor);
                if (threadPoolExecutor == null) {
                    threadPoolExecutor = newThreadPoolExecutor;
                }
            }

            return threadPoolExecutor;
        } else {
            return createSingletonThreadPoolExecutor(threadPoolName);
        }
    }

    private ThreadPoolExecutor createSingletonThreadPoolExecutor(String threadPoolName) {
        if (threadPoolExecutor == null) {
            synchronized (ThreadPoolFactory.class) {
                if (threadPoolExecutor == null) {
                    threadPoolExecutor = createThreadPoolExecutor(threadPoolName);
                }
            }
        }

        return threadPoolExecutor;
    }

    private ThreadPoolExecutor createThreadPoolExecutor(String threadPoolName) {
        return threadPoolNameCustomized ?
                createThreadPoolExecutor(threadPoolName, threadPoolCorePoolSize, threadPoolMaximumPoolSize, threadPoolKeepAliveTime, threadPoolAllowCoreThreadTimeout, threadPoolQueue, threadPoolQueueCapacity, threadPoolRejectedPolicy) :
                createThreadPoolExecutor(threadPoolCorePoolSize, threadPoolMaximumPoolSize, threadPoolKeepAliveTime, threadPoolAllowCoreThreadTimeout, threadPoolQueue, threadPoolQueueCapacity, threadPoolRejectedPolicy);
    }

    public static ThreadPoolExecutor createThreadPoolExecutor(String threadPoolName, int corePoolSize, int maximumPoolSize, long keepAliveTime, boolean allowCoreThreadTimeout, String queue, int queueCapacity, String rejectedPolicy) {
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(corePoolSize,
                maximumPoolSize,
                keepAliveTime,
                TimeUnit.MILLISECONDS,
                createBlockingQueue(queue, queueCapacity),
                new ThreadFactory() {
                    private AtomicInteger number = new AtomicInteger(0);

                    @Override
                    public Thread newThread(Runnable runnable) {
                        return new Thread(runnable, threadPoolName + "-" + number.getAndIncrement());
                    }
                },
                createRejectedPolicy(rejectedPolicy));
        threadPoolExecutor.allowCoreThreadTimeOut(allowCoreThreadTimeout);

        return threadPoolExecutor;
    }

    public static ThreadPoolExecutor createThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, boolean allowCoreThreadTimeout, String queue, int queueCapacity, String rejectedPolicy) {
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(corePoolSize,
                maximumPoolSize,
                keepAliveTime,
                TimeUnit.MILLISECONDS,
                createBlockingQueue(queue, queueCapacity),
                createRejectedPolicy(rejectedPolicy));
        threadPoolExecutor.allowCoreThreadTimeOut(allowCoreThreadTimeout);

        return threadPoolExecutor;
    }

    private static BlockingQueue<Runnable> createBlockingQueue(String queue, int queueCapacity) {
        ThreadQueueType queueType = ThreadQueueType.fromString(queue);

        int capacity = ThreadConstant.CPUS * queueCapacity;

        switch (queueType) {
            case LINKED_BLOCKING_QUEUE:
                return new LinkedBlockingQueue<Runnable>(capacity);
            case ARRAY_BLOCKING_QUEUE:
                return new ArrayBlockingQueue<Runnable>(capacity);
            case SYNCHRONOUS_QUEUE:
                return new SynchronousQueue<Runnable>();
        }

        return null;
    }

    private static RejectedExecutionHandler createRejectedPolicy(String rejectedPolicy) {
        ThreadRejectedPolicyType rejectedPolicyType = ThreadRejectedPolicyType.fromString(rejectedPolicy);

        switch (rejectedPolicyType) {
            case BLOCKING_POLICY_WITH_REPORT:
                return new BlockingPolicyWithReport();
            case CALLER_RUNS_POLICY_WITH_REPORT:
                return new CallerRunsPolicyWithReport();
            case ABORT_POLICY_WITH_REPORT:
                return new AbortPolicyWithReport();
            case REJECTED_POLICY_WITH_REPORT:
                return new RejectedPolicyWithReport();
            case DISCARDED_POLICY_WITH_REPORT:
                return new DiscardedPolicyWithReport();
        }

        return null;
    }
}
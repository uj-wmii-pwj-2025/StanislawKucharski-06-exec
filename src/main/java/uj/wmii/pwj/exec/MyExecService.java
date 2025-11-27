package uj.wmii.pwj.exec;

import java.util.*;
import java.util.concurrent.*;

public class MyExecService implements ExecutorService {
    private final Thread worker;
    private final BlockingQueue<TaskUnit<?>> queue;
    public volatile boolean acceptsJobs = true;

    public MyExecService() {
        queue = new LinkedBlockingQueue<>();
        worker = new Thread(new Student(queue, this));
        worker.start();
    }

    static MyExecService newInstance() {
        return new MyExecService();
    }

    @Override
    public void shutdown() {
        acceptsJobs = false;
    }

    @Override
    public List<Runnable> shutdownNow() {
        acceptsJobs = false;
        worker.interrupt();
        List<TaskUnit<?>> drained = new ArrayList<>();
        queue.drainTo(drained);

        List<Runnable> remaining = new ArrayList<>();
        for (TaskUnit<?> taskUnit : drained) {
            remaining.add(()-> {
                try {
                    taskUnit.callable.call();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
        return remaining;
    }

    @Override
    public boolean isShutdown() {
        return !acceptsJobs;
    }

    @Override
    public boolean isTerminated() {
        return !acceptsJobs && !worker.isAlive();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        worker.join(unit.toMillis(timeout));
        return !worker.isAlive();
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        if(!acceptsJobs)throw new RejectedExecutionException();
        CompletableFuture<T> future = new CompletableFuture<>();
        queue.add(new TaskUnit<>(task, future));
        return future;
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        if(!acceptsJobs)throw new RejectedExecutionException();
        Callable<T> wrapper = () -> { task.run(); return result; };
        return submit(wrapper);
    }

    @Override
    public Future<?> submit(Runnable task) {
        if(!acceptsJobs)throw new RejectedExecutionException();
        Callable<Void> wrapper = () -> { task.run(); return null; };
        return submit(wrapper);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        if(!acceptsJobs)throw new RejectedExecutionException();
        List<Future<T>> futures = tasks.stream().map(this::submit).toList();

        for (Future<T> f : futures) {
            try { f.get(); } catch (Exception ignored) {}
        }
        return futures;
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
        if(!acceptsJobs)throw new RejectedExecutionException();
        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
        List<Future<T>> futures = tasks.stream().map(this::submit  ).toList();

        for (Future<T> f : futures) {
            long timeLeft = deadline - System.currentTimeMillis();
            if (timeLeft <= 0) break;
            try { f.get(timeLeft, TimeUnit.MILLISECONDS); } catch (Exception ignored) {}
        }
        return futures;
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        if(!acceptsJobs)throw new RejectedExecutionException();
        List<Future<T>> futures = tasks.stream().map(this::submit).toList();

        while (true) {
            for (Future<T> f : futures) {
                if (f.isDone()) {
                    try {
                        return f.get();
                    } catch (ExecutionException e) {
                        throw e;
                    }
                }
            }
            Thread.sleep(1);
        }
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        if(!acceptsJobs)throw new RejectedExecutionException();
        List<Future<T>> futures = tasks.stream().map(this::submit).toList();
        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);

        while (true) {
            for (Future<T> f : futures) {
                if (f.isDone()) {
                    try {
                        return f.get();
                    } catch (ExecutionException e) {
                        throw e;
                    }
                }
            }
            if (System.currentTimeMillis() > deadline) {
                throw new TimeoutException();
            }
            Thread.sleep(1);
        }
    }

    @Override
    public void execute(Runnable command) {
        if(!acceptsJobs)throw new RejectedExecutionException();
        submit(command);
    }
}

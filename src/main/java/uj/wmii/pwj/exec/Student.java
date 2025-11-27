package uj.wmii.pwj.exec;

import java.util.concurrent.BlockingQueue;

public class Student implements Runnable {
    private final BlockingQueue<TaskUnit<?>> queue;
    private MyExecService overlord;

    public Student(BlockingQueue<TaskUnit<?>> queue, MyExecService service) {
        this.queue = queue;
        overlord = service;
    }


    @Override
    public void run() {
        while (overlord.acceptsJobs || !queue.isEmpty()) {
            try {
                TaskUnit task = queue.take();

                try {
                    Object result = task.callable.call();
                    task.future.complete(result);
                } catch (Throwable ex) {
                    task.future.completeExceptionally(ex);
                }
            } catch (InterruptedException e) {
                break;
            }
        }
    }

}

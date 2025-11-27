package uj.wmii.pwj.exec;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

class TaskUnit<T> {
    final Callable<T> callable;
    final CompletableFuture<T> future;

    TaskUnit(Callable<T> callable, Future<T> future) {
        this.callable = callable;
        this.future = (CompletableFuture<T>) future;
    }
}

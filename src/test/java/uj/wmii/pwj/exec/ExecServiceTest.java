package uj.wmii.pwj.exec;

import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

public class ExecServiceTest {

    static void doSleep(int ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    @Test
    void testExecuteRunnable() {
        MyExecService s = MyExecService.newInstance();
        TestRunnable r = new TestRunnable();
        s.execute(r);
        doSleep(20);
        assertTrue(r.wasRun);
    }

    @Test
    void testSubmitRunnable() throws Exception {
        MyExecService s = MyExecService.newInstance();
        TestRunnable r = new TestRunnable();
        Future<?> f = s.submit(r);
        doSleep(20);
        assertTrue(r.wasRun);
        assertTrue(f.isDone());
        assertNull(f.get());
    }

    @Test
    void testSubmitRunnableWithResult() throws Exception {
        MyExecService s = MyExecService.newInstance();
        TestRunnable r = new TestRunnable();
        String expected = "result";
        Future<String> f = s.submit(r, expected);
        doSleep(20);
        assertTrue(r.wasRun);
        assertTrue(f.isDone());
        assertEquals(expected, f.get());
    }

    @Test
    void testSubmitCallable() throws Exception {
        MyExecService s = MyExecService.newInstance();
        Callable<String> c = () -> "OK";
        Future<String> f = s.submit(c);
        doSleep(20);
        assertTrue(f.isDone());
        assertEquals("OK", f.get());
    }

    @Test
    void testSubmitCallableException() {
        MyExecService s = MyExecService.newInstance();
        Callable<String> c = () -> { throw new RuntimeException("fail"); };
        Future<String> f = s.submit(c);
        doSleep(20);
        assertTrue(f.isDone());
        assertThrows(ExecutionException.class, f::get);
    }

    // ========================= Shutdown =========================
    @Test
    void testShutdownRejectsNewTasks() {
        MyExecService s = MyExecService.newInstance();
        s.shutdown();
        assertThrows(RejectedExecutionException.class, () -> s.submit(() -> "X"));
    }

    @Test
    void testShutdownNowReturnsRemaining() {
        MyExecService s = MyExecService.newInstance();
        Future<String> f1 = s.submit(() -> { doSleep(500000); return "A"; });
        Future<String> f2 = s.submit(() -> { doSleep(500000); return "B"; });
        doSleep(50);
        List<Runnable> remaining = s.shutdownNow();
        assertFalse(s.acceptsJobs);
        assertTrue(s.isShutdown());
        assertEquals(1, remaining.size());
    }

    // ========================= invokeAll =========================
    @Test
    void testInvokeAll() throws Exception {
        MyExecService s = MyExecService.newInstance();
        List<Callable<String>> tasks = Arrays.asList(
                () -> { doSleep(10); return "A"; },
                () -> { doSleep(20); return "B"; }
        );
        List<Future<String>> futures = s.invokeAll(tasks);
        assertEquals(2, futures.size());
        assertEquals("A", futures.get(0).get());
        assertEquals("B", futures.get(1).get());
    }

    @Test
    void testInvokeAllWithTimeout() throws Exception {
        MyExecService s = MyExecService.newInstance();
        List<Callable<String>> tasks = Arrays.asList(
                () -> { doSleep(50); return "A"; },
                () -> { doSleep(50); return "B"; }
        );
        List<Future<String>> futures = s.invokeAll(tasks, 20, TimeUnit.MILLISECONDS);
        assertEquals(2, futures.size());
        for (Future<String> f : futures) {
            assertNotNull(f);
        }
    }

    // ========================= invokeAny =========================
    @Test
    void testInvokeAnySingleThread() throws Exception {
        MyExecService s = MyExecService.newInstance();
        List<Callable<String>> tasks = Arrays.asList(
                () -> { doSleep(10); return "A"; },
                () -> { doSleep(20); return "B"; }
        );
        String result = s.invokeAny(tasks);
        assertEquals("A", result);
    }

    @Test
    void testInvokeAnyTimeout() {
        MyExecService s = MyExecService.newInstance();
        List<Callable<String>> tasks = Arrays.asList(
                () -> { doSleep(50); return "A"; },
                () -> { doSleep(50); return "B"; }
        );
        assertThrows(TimeoutException.class, () -> s.invokeAny(tasks, 20, TimeUnit.MILLISECONDS));
    }

    // ========================= extras =========================

    @Test
    void testMultipleStringCallables() throws Exception {
        MyExecService s = MyExecService.newInstance();
        StringCallable c1 = new StringCallable("A", 10);
        StringCallable c2 = new StringCallable("B", 20);
        Future<String> f1 = s.submit(c1);
        Future<String> f2 = s.submit(c2);
        doSleep(50);
        assertTrue(f1.isDone() && f2.isDone());
        assertEquals("A", f1.get());
        assertEquals("B", f2.get());
    }

    @Test
    void testStringCallableWithException() {
        MyExecService s = MyExecService.newInstance();
        StringCallable c = new StringCallable(null, 0) {
            @Override
            public String call() throws Exception {
                throw new RuntimeException("fail");
            }
        };
        Future<String> f = s.submit(c);
        doSleep(10);
        assertTrue(f.isDone());
        assertThrows(ExecutionException.class, f::get);
    }

    @Test
    void testInvokeAllWithStringCallables() throws Exception {
        MyExecService s = MyExecService.newInstance();
        List<StringCallable> tasks = Arrays.asList(
                new StringCallable("X", 10),
                new StringCallable("Y", 20)
        );
        List<Future<String>> futures = s.invokeAll(tasks);
        assertEquals(2, futures.size());
        assertEquals("X", futures.get(0).get());
        assertEquals("Y", futures.get(1).get());
    }

    @Test
    void testInvokeAnyWithStringCallables() throws Exception {
        MyExecService s = MyExecService.newInstance();
        List<StringCallable> tasks = Arrays.asList(
                new StringCallable("X", 20),
                new StringCallable("Y", 10)
        );
        String result = s.invokeAny(tasks);
        assertEquals("X", result);
    }

}

// ========================= Helpers =========================
class TestRunnable implements Runnable {
    boolean wasRun;
    @Override
    public void run() {
        wasRun = true;
    }

}

class StringCallable implements Callable<String> {
    private final String result;
    private final int milis;
    StringCallable(String result, int milis) {
        this.result = result;
        this.milis = milis;
    }
    @Override
    public String call() throws Exception {
        ExecServiceTest.doSleep(milis);
        return result;
    }
}

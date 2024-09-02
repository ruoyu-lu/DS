import java.util.concurrent.*;
import java.util.List;
import java.util.ArrayList;

/**
 * A simple thread pool class that manages a pool of threads.
 */
public class ThreadPool {
    private final ExecutorService executor;
    private final List<Thread> managedThreads;

    public ThreadPool(int nThreads) {
        this.executor = Executors.newFixedThreadPool(nThreads);
        this.managedThreads = new ArrayList<>();
    }

    public void execute(Runnable task) {
        executor.execute(task);
    }

    public void shutdown() {
        for (Thread thread : managedThreads) {
            thread.interrupt();
        }

        executor.shutdown();
        try {
            if (!executor.awaitTermination(Constant.THREAD_TIMEOUT, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        for (Thread thread : managedThreads) {
            try {
                thread.join(Constant.THREAD_JOIN); // Wait for 1 second for each thread to finish
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
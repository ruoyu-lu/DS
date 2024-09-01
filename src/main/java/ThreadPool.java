/**
 * ThreadPool:
 * Manages the execution of multiple threads. This class typically includes a task queue and a set of worker
 * threads. The ThreadPool is responsible for allocating tasks to threads, monitoring their status, and optimizing
 * resource utilization and task execution efficiency.
 */
import java.util.concurrent.*;
import java.util.List;
import java.util.ArrayList;

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
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        for (Thread thread : managedThreads) {
            try {
                thread.join(1000); // Wait for 1 second for each thread to finish
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
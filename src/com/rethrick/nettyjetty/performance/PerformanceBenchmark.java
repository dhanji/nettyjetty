package com.rethrick.nettyjetty.performance;

import com.google.common.base.Stopwatch;
import com.squareup.okhttp.ConnectionPool;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import humanize.Humanize;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
public class PerformanceBenchmark {
  private static final int REQUESTS = 1 * 1000 * 1000;
  private static final int POOL_SIZE = 1000;

  private final OkHttpClient client = new OkHttpClient();
  private final ExecutorService pool = Executors.newFixedThreadPool(POOL_SIZE);

  private final Callable<RequestStats> task = new Callable<RequestStats>() {
    @Override public RequestStats call() {
      Stopwatch stopwatch = Stopwatch.createStarted();
      try {
        return new RequestStats(stopwatch.elapsed(TimeUnit.MILLISECONDS), client.newCall(new Request.Builder()
            .url("http://localhost:8080/")
            .build())
            .execute()
            .code(), false);
      } catch (IOException e) {
        return new RequestStats(stopwatch.elapsed(TimeUnit.MILLISECONDS), 0, true);
      }
    }
  };

  public static void main(String[] args) throws ExecutionException, InterruptedException {
    PerformanceBenchmark performanceBenchmark = new PerformanceBenchmark();
    performanceBenchmark.client.setConnectionPool(new ConnectionPool(100, TimeUnit.SECONDS.toMillis(5)));

    System.out.println("Warm up run...");
    performanceBenchmark.testPerformance(10 * 1000);

    System.out.println();
    System.out.println("Actual Test run...");
    System.out.println();
    performanceBenchmark.testPerformance(REQUESTS);

    System.exit(0);
  }

  public void testPerformance(int runs) throws ExecutionException, InterruptedException {
    // Hammer Jetty as fast as we can.
    List<Future<RequestStats>> futures = new ArrayList<Future<RequestStats>>(runs);
    for (int i = 0; i < runs; i++) {
      futures.add(pool.submit(task));
    }

    List<RequestStats> allStats = new ArrayList<RequestStats>();
    long sumTimeTaken = 0, ioErrors = 0, serverErrors = 0;
    for (Future<RequestStats> future : futures) {
      RequestStats stats = future.get();

      sumTimeTaken += stats.timeTakenMillis;
      ioErrors += stats.ioerror ? 1 : 0;
      serverErrors += stats.status > 399 ? 1 : 0;
      allStats.add(stats);
    }
    futures = null; // Attempt to flush from memory fwiw.
    System.gc();

    System.out.println("Total requests attempted : " + runs);
    System.out.println("Total requests performed : " + allStats.size());
    System.out.println("Total requests time      : " + (sumTimeTaken / 1000) + "s");
    System.out.println("Total server errors      : " + serverErrors);
    System.out.println("Total io errors          : " + ioErrors);

    System.out.println("Server error rate      : " + Humanize.formatPercent(serverErrors / runs));
    System.out.println("IO error rate          : " + Humanize.formatPercent(ioErrors / runs));

    System.out.println();
    System.out.println("Avg request time         : " + (sumTimeTaken / 1000 / allStats.size()) + "s");
    System.out.println("Avg requests/sec         : " + ((allStats.size() - ioErrors - serverErrors) / sumTimeTaken / 1000));
    System.out.println("Avg errors/sec           : " + ((ioErrors + serverErrors) / sumTimeTaken / 1000));

    Collections.sort(allStats);
    RequestStats median = allStats.get(allStats.size() / 2);

    System.out.println();
    System.out.println("Median request time      : " + Humanize.duration(median.timeTakenMillis / 1000));
  }

  public static class RequestStats implements Comparable<RequestStats> {
    private final long timeTakenMillis;
    private final int status;
    private final boolean ioerror;

    public RequestStats(long timeTakenMillis, int status, boolean ioerror) {
      this.timeTakenMillis = timeTakenMillis;
      this.status = status;
      this.ioerror = ioerror;
    }

    @Override public int compareTo(RequestStats that) {
      return Long.compare(that.timeTakenMillis, this.timeTakenMillis);
    }
  }
}

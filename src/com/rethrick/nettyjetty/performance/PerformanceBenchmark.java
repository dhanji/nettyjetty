package com.rethrick.nettyjetty.performance;

import com.google.common.base.Preconditions;
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

  public PerformanceBenchmark(String url) {
    this.url = url;
  }

  private final String url;
  private final Callable<RequestStats> task = new Callable<RequestStats>() {
    @Override public RequestStats call() {
      long start = System.currentTimeMillis();
      try {
        return new RequestStats(System.currentTimeMillis() - start, client.newCall(new Request.Builder()
            .url(url)
            .build())
            .execute()
            .code(), false);
      } catch (IOException e) {
        return new RequestStats(System.currentTimeMillis() - start, 0, true);
      }
    }
  };

  public static void main(String[] args) throws ExecutionException, InterruptedException {
    Preconditions.checkArgument(args.length > 0, "Expected URL as first argument");
    PerformanceBenchmark performanceBenchmark = new PerformanceBenchmark(args[0]);
    performanceBenchmark.client.setConnectionPool(new ConnectionPool(100, TimeUnit.SECONDS.toMillis(5)));

    System.out.println("Warm up run...");
    performanceBenchmark.testPerformance(100 * 1000);

    System.out.println();
    System.out.println("Actual Test run...");
    System.out.println();
    performanceBenchmark.testPerformance(REQUESTS);

    System.exit(0);
  }

  public void testPerformance(int runs) throws ExecutionException, InterruptedException {
    // Hammer Jetty as fast as we can.
    List<Future<RequestStats>> futures = new ArrayList<Future<RequestStats>>(runs);
    long wallClockSec = System.currentTimeMillis();
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
    wallClockSec = System.currentTimeMillis() - wallClockSec;

    futures = null; // Attempt to flush from memory fwiw.
    System.gc();

    long durationSec = TimeUnit.MILLISECONDS.toSeconds(sumTimeTaken);
    int totalRequests = allStats.size();
    System.out.println("Total requests attempted : " + runs);
    System.out.println("Total requests performed : " + totalRequests);
    System.out.println("Total wall time          : " + wallClockSec + "s");
    System.out.println("Total requests time      : " + durationSec + "s");
    System.out.println("Total server errors      : " + serverErrors);
    System.out.println("Total io errors          : " + ioErrors);

    System.out.println("Server error rate      : " + Humanize.formatPercent(serverErrors / runs * 1.0));
    System.out.println("IO error rate          : " + Humanize.formatPercent(ioErrors / runs * 1.0));

    System.out.println();
    System.out.println("Avg request time         : " + (durationSec / totalRequests) + "s");
    System.out.println("Avg request wall time    : " + (wallClockSec / totalRequests) + "s");
    System.out.println("Avg requests/sec (wall)  : " + ((totalRequests - ioErrors - serverErrors * 1.0) / wallClockSec));
    System.out.println("Avg errors/sec   (wall)  : " + ((ioErrors + serverErrors * 1.0) / wallClockSec));

    Collections.sort(allStats);
    RequestStats median = allStats.get(totalRequests / 2);

    System.out.println();
    System.out.println("Median request time      : " + (median.timeTakenMillis / 1000));
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

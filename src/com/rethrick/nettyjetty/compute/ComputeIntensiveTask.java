package com.rethrick.nettyjetty.compute;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

/**
 * A nominally compute intensive task that should take a
 * fixed amount of CPU time on every invocation.
 *
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
public class ComputeIntensiveTask {
  public byte[] compute() {
    byte[] bytes = new byte[8 * 1024];  // 8k
    new Random().nextBytes(bytes);
    return hash(bytes);
  }

  private static byte[] hash(byte[] input) {
    try {
      return MessageDigest.getInstance("SHA-1").digest(input);
    } catch(NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }
}

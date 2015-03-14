package com.rethrick.nettyjetty;

import com.rethrick.nettyjetty.compute.ComputeIntensiveTask;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
public class Jetty {
  public static void main(String[] args) throws Exception {
    Server server = new Server(8080);
    ServletContextHandler context = new ServletContextHandler();
    server.setHandler(context);

    context.addServlet(new ServletHolder(new HttpServlet() {
      @Override
      protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.getOutputStream().write(new ComputeIntensiveTask().compute());
      }
    }), "/");

    server.start();
    server.join();
  }
}

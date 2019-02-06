/*
 * Copyright (c) 2012-2018 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.api.deploy;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.slf4j.LoggerFactory.getLogger;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.websocket.server.ServerEndpoint;
import org.eclipse.che.api.core.jsonrpc.commons.RequestProcessorConfigurator;
import org.eclipse.che.api.core.websocket.commons.WebSocketMessageReceiver;
import org.eclipse.che.api.core.websocket.impl.BasicWebSocketEndpoint;
import org.eclipse.che.api.core.websocket.impl.GuiceInjectorEndpointConfigurator;
import org.eclipse.che.api.core.websocket.impl.MessagesReSender;
import org.eclipse.che.api.core.websocket.impl.WebSocketSessionRegistry;
import org.eclipse.che.api.core.websocket.impl.WebsocketIdService;
import org.eclipse.che.commons.lang.concurrent.LoggingUncaughtExceptionHandler;
import org.slf4j.Logger;

/**
 * Implementation of {@link BasicWebSocketEndpoint} for Che packaging. Add only mapping
 * "/websocket-minor".
 */
@ServerEndpoint(value = "/websocket-minor", configurator = GuiceInjectorEndpointConfigurator.class)
public class CheMinorWebSocketEndpoint extends BasicWebSocketEndpoint {

  private static final Logger LOG = getLogger(CheMinorWebSocketEndpoint.class);

  public static final String ENDPOINT_ID = "master-websocket-minor-endpoint";

  @Inject
  public CheMinorWebSocketEndpoint(
      WebSocketSessionRegistry registry,
      MessagesReSender reSender,
      WebSocketMessageReceiver receiver,
      WebsocketIdService websocketIdService) {
    super(registry, reSender, receiver, websocketIdService);
  }

  @Override
  protected String getEndpointId() {
    return ENDPOINT_ID;
  }

  public static class CheMinorWebSocketEndpointConfiguration
      implements RequestProcessorConfigurator.Configuration {

    private final ExecutorService executor;

    @Inject
    public CheMinorWebSocketEndpointConfiguration(
        @Named("che.core.jsonrpc.minor_executor") ExecutorService executor) {
      this.executor = executor;
    }

    @Override
    public String getEndpointId() {
      return ENDPOINT_ID;
    }

    @Override
    public ExecutorService getExecutionService() {
      return executor;
    }
  }

  @Singleton
  public static class CheMinorWebSocketEndpointExecutorServiceProvider
      implements Provider<ExecutorService> {

    private final ThreadPoolExecutor executor;

    @Inject
    public CheMinorWebSocketEndpointExecutorServiceProvider(
        @Named("che.core.jsonrpc.processor_max_pool_size") int maxPoolSize) {
      ThreadFactory factory =
          new ThreadFactoryBuilder()
              .setUncaughtExceptionHandler(LoggingUncaughtExceptionHandler.getInstance())
              .setNameFormat(CheMajorWebSocketEndpoint.class.getSimpleName() + "-%d")
              .setDaemon(true)
              .build();

      executor =
          new ThreadPoolExecutor(0, maxPoolSize, 60L, SECONDS, new SynchronousQueue<>(), factory);
      executor.setRejectedExecutionHandler(
          (r, __) -> LOG.error("Message {} rejected for execution", r));
    }

    @Override
    public ExecutorService get() {
      return executor;
    }
  }
}

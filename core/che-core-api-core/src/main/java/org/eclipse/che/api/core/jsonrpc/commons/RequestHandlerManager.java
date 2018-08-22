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
package org.eclipse.che.api.core.jsonrpc.commons;

import static org.slf4j.LoggerFactory.getLogger;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import org.eclipse.che.api.core.websocket.commons.WebSocketMessageTransmitter;
import org.eclipse.che.commons.annotation.Nullable;
import org.slf4j.Logger;

/**
 * Manages request handlers. There are nine types of such handlers that differs by the type and
 * number of incoming parameters and outgoing results:
 *
 * <ul>
 *   <li>{@link NoneToNoneHandler} - to receive a notification w/o parameters
 *   <li>{@link NoneToOneHandler} - to receive a request w/o parameters and a single result
 *   <li>{@link NoneToManyHandler} - to receive a request w/o parameters and multiple results
 *   <li>{@link OneToNoneHandler} - to receive a notification with a single parameter
 *   <li>{@link OneToOneHandler} - to receive a request with a single parameter and a single result
 *   <li>{@link OneToManyHandler}- to receive a request with a single parameter and multiple results
 *   <li>{@link ManyToNoneHandler} - to receive a notification with multiple parameters
 *   <li>{@link ManyToOneHandler} - to receive request with multiple parameters and a single result
 *   <li>{@link ManyToManyHandler} - to receive request with multiple parameters and multiple
 *       results
 * </ul>
 */
@Singleton
public class RequestHandlerManager {
  private static final Logger LOGGER = getLogger(RequestHandlerManager.class);

  private final Multimap<String, JsonRpcMethodInvokerFilter> filters = ArrayListMultimap.create();

  private final Map<String, Handler> handlers = new ConcurrentHashMap<>();

  private final WebSocketMessageTransmitter transmitter;
  private final JsonRpcComposer dtoComposer;
  private final JsonRpcMarshaller marshaller;

  @Inject
  public RequestHandlerManager(
      WebSocketMessageTransmitter transmitter,
      JsonRpcComposer dtoComposer,
      JsonRpcMarshaller marshaller) {
    this.transmitter = transmitter;
    this.dtoComposer = dtoComposer;
    this.marshaller = marshaller;
  }

  public synchronized <P, R> void registerOneToOne(
      String method, Class<P> pClass, Class<R> ignored, BiFunction<String, P, R> biFunction) {
    mustNotBeRegistered(method);

    handlers.put(method, new OneToOneHandler<>(pClass, biFunction));
  }

  public synchronized void registerMethodInvokerFilter(
      JsonRpcMethodInvokerFilter filter, String... methods) {
    for (String method : methods) {
      filters.put(method, filter);
    }
  }

  public synchronized <P, R> void registerOneToPromiseOne(
      String method,
      Class<P> pClass,
      Class<R> rClass,
      BiFunction<String, P, JsonRpcPromise<R>> function) {
    mustNotBeRegistered(method);
    handlers.put(method, new OneToPromiseOneHandler<>(pClass, function));
  }

  public synchronized <P, R> void registerOneToMany(
      String method, Class<P> pClass, Class<R> rClass, BiFunction<String, P, List<R>> biFunction) {
    mustNotBeRegistered(method);

    handlers.put(method, new OneToManyHandler<>(pClass, biFunction));
  }

  public synchronized <P> void registerOneToNone(
      String method, Class<P> pClass, BiConsumer<String, P> biConsumer) {
    mustNotBeRegistered(method);

    handlers.put(method, new OneToNoneHandler<>(pClass, biConsumer));
  }

  public synchronized <P, R> void registerManyToOne(
      String method, Class<P> pClass, Class<R> rClass, BiFunction<String, List<P>, R> biFunction) {
    mustNotBeRegistered(method);

    handlers.put(method, new ManyToOneHandler<>(pClass, biFunction));
  }

  public synchronized <P, R> void registerManyToMany(
      String method,
      Class<P> pClass,
      Class<R> rClass,
      BiFunction<String, List<P>, List<R>> function) {
    mustNotBeRegistered(method);

    handlers.put(method, new ManyToManyHandler<>(pClass, function));
  }

  public synchronized <P> void registerManyToNone(
      String method, Class<P> pClass, BiConsumer<String, List<P>> biConsumer) {
    mustNotBeRegistered(method);

    handlers.put(method, new ManyToNoneHandler<>(pClass, biConsumer));
  }

  public synchronized <R> void registerNoneToOne(
      String method, Class<R> rClass, Function<String, R> function) {
    mustNotBeRegistered(method);

    handlers.put(method, new NoneToOneHandler<>(function));
  }

  public synchronized <R> void registerNoneToMany(
      String method, Class<R> rClass, Function<String, List<R>> function) {
    mustNotBeRegistered(method);

    handlers.put(method, new NoneToManyHandler<>(function));
  }

  public synchronized void registerNoneToNone(String method, Consumer<String> consumer) {
    mustNotBeRegistered(method);

    handlers.put(method, new NoneToNoneHandler(consumer));
  }

  public boolean isRegistered(String method) {
    return handlers.containsKey(method);
  }

  public synchronized boolean deregister(String method) {
    return handlers.remove(method) == null;
  }

  public void handle(
      String endpointId, String requestId, String method, JsonRpcParams params) {
    mustBeRegistered(method);

    Handler handler = handlers.get(method);
    if (handler == null) {
      LOGGER.error("Something went wrong trying to find out handler category");
      return;
    }

    handler.handle(endpointId, requestId, method, params);
  }

  public void handle(String endpointId, String method, JsonRpcParams params) {
    mustBeRegistered(method);

    Handler handler = handlers.get(method);
    if (handler == null) {
      LOGGER.error("Something went wrong trying to find out handler category");
      return;
    }

    handler.handle(endpointId, null, method, params);
  }

  private void mustBeRegistered(String method) {
    if (!isRegistered(method)) {
      String message = "Method '" + method + "' is not registered";
      LOGGER.error(message);
      throw new IllegalStateException(message);
    }
  }

  private void mustNotBeRegistered(String method) {
    if (isRegistered(method)) {
      String message = "Method '" + method + "' is already registered";
      LOGGER.error(message);
      throw new IllegalStateException(message);
    }
  }

  private <P> List<P> composeMany(JsonRpcParams params, Class<P> pClass) {
    return dtoComposer.composeMany(params, pClass);
  }

  private <P> P composeOne(JsonRpcParams params, Class<P> pClass) {
    return dtoComposer.composeOne(params, pClass);
  }

  private void filter(String method, Object... param) {
    for (JsonRpcMethodInvokerFilter filter : filters.get(method)) {
      filter.accept(method, param);
    }
  }

  private <R> void transmitOne(String endpointId, String id, R result) {
    JsonRpcResult jsonRpcResult = new JsonRpcResult(result);
    JsonRpcResponse jsonRpcResponse = new JsonRpcResponse(id, jsonRpcResult, null);
    String message = marshaller.marshall(jsonRpcResponse);
    transmitter.transmit(endpointId, message);
  }

  private void transmitMany(String endpointId, String id, List<?> result) {
    JsonRpcResult jsonRpcResult = new JsonRpcResult(result);
    JsonRpcResponse jsonRpcResponse = new JsonRpcResponse(id, jsonRpcResult, null);
    String message = marshaller.marshall(jsonRpcResponse);
    transmitter.transmit(endpointId, message);
  }

  private <R> void transmitPromiseOne(
      String endpointId, String requestId, JsonRpcPromise<R> promise) {
    promise.onSuccess(result -> transmitOne(endpointId, requestId, result));
    promise.onFailure(
        jsonRpcError -> {
          JsonRpcResponse jsonRpcResponse = new JsonRpcResponse(requestId, null, jsonRpcError);
          String message = marshaller.marshall(jsonRpcResponse);
          transmitter.transmit(endpointId, message);
        });
  }

  private interface Handler {
    void handle(String endpointId, @Nullable String requestId, String method, JsonRpcParams params);
  }

  private class OneToOneHandler<P, R> implements Handler {
    private final Class<P> pClass;
    private final BiFunction<String, P, R> biFunction;

    private OneToOneHandler(Class<P> pClass, BiFunction<String, P, R> biFunction) {
      this.pClass = pClass;
      this.biFunction = biFunction;
    }

    @Override
    public void handle(String endpointId, String requestId, String method, JsonRpcParams params) {
      P dto = composeOne(params, pClass);
      filter(method, dto);
      transmitOne(endpointId, requestId, biFunction.apply(endpointId, dto));
    }
  }

  private class OneToPromiseOneHandler<P, R> implements Handler {
    private final Class<P> pClass;
    private BiFunction<String, P, JsonRpcPromise<R>> function;

    private OneToPromiseOneHandler(
        Class<P> pClass, BiFunction<String, P, JsonRpcPromise<R>> function) {
      this.pClass = pClass;
      this.function = function;
    }

    @Override
    public void handle(String endpointId, String requestId, String method, JsonRpcParams params) {
      P dto = composeOne(params, pClass);
      filter(method, dto);
      transmitOne(endpointId, requestId, function.apply(endpointId, dto));
    }
  }

  private class OneToManyHandler<P, R> implements Handler {
    private final Class<P> pClass;
    private final BiFunction<String, P, List<R>> biFunction;

    private OneToManyHandler(
        Class<P> pClass, BiFunction<String, P, List<R>> biFunction) {
      this.pClass = pClass;
      this.biFunction = biFunction;
    }

    @Override
    public void handle(String endpointId, String requestId, String method, JsonRpcParams params) {
      P dto = dtoComposer.composeOne(params, pClass);
      filter(method, dto);
      transmitMany(endpointId, requestId, biFunction.apply(endpointId, dto));
    }
  }

  private class OneToNoneHandler<P> implements Handler {
    private final Class<P> pClass;
    private final BiConsumer<String, P> biConsumer;

    private OneToNoneHandler(Class<P> pClass, BiConsumer<String, P> biConsumer) {
      this.pClass = pClass;
      this.biConsumer = biConsumer;
    }

    @Override
    public void handle(String endpointId, String requestId, String method, JsonRpcParams params) {
      P param = composeOne(params, pClass);
      filter(method, param);
      biConsumer.accept(endpointId, param);
    }
  }

  private class ManyToOneHandler<P, R> implements Handler {
    private final Class<P> pClass;
    private final BiFunction<String, List<P>, R> biFunction;

    private ManyToOneHandler(
        Class<P> pClass, BiFunction<String, List<P>, R> biFunction) {
      this.pClass = pClass;
      this.biFunction = biFunction;
    }

    @Override
    public void handle(String endpointId, String requestId, String method, JsonRpcParams params) {
      List<P> dto = dtoComposer.composeMany(params, pClass);
      filter(method, dto);
      transmitOne(endpointId, requestId, biFunction.apply(endpointId, dto));
    }
  }

  private class ManyToManyHandler<P, R> implements Handler {
    private final Class<P> pClass;
    private final BiFunction<String, List<P>, List<R>> biFunction;

    private ManyToManyHandler(
        Class<P> pClass, BiFunction<String, List<P>, List<R>> biFunction) {
      this.pClass = pClass;
      this.biFunction = biFunction;
    }

    @Override
    public void handle(String endpointId, String requestId, String method, JsonRpcParams params) {
      List<P> dto = dtoComposer.composeMany(params, pClass);
      filter(method, dto);
      transmitMany(endpointId, requestId, biFunction.apply(endpointId, dto));
    }
  }

  private class ManyToNoneHandler<P> implements Handler {
    private final Class<P> pClass;
    private final BiConsumer<String, List<P>> biConsumer;

    private ManyToNoneHandler(Class<P> pClass, BiConsumer<String, List<P>> biConsumer) {
      this.pClass = pClass;
      this.biConsumer = biConsumer;
    }

    @Override
    public void handle(String endpointId, String requestId, String method, JsonRpcParams params) {
      List<P> listDto = composeMany(params, pClass);
      filter(method, listDto);
      biConsumer.accept(endpointId, listDto);
    }
  }

  private class NoneToOneHandler<R> implements Handler {
    private final Function<String, R> function;

    private NoneToOneHandler(Function<String, R> function) {
      this.function = function;
    }

    @Override
    public void handle(String endpointId, String requestId, String method, JsonRpcParams params) {
      filter(method);
      transmitOne(endpointId, requestId, function.apply(endpointId));
    }
  }

  private class NoneToManyHandler<R> implements Handler {
    private final Function<String, List<R>> function;

    private NoneToManyHandler(Function<String, List<R>> function) {
      this.function = function;
    }

    @Override
    public void handle(String endpointId, String requestId, String method, JsonRpcParams params) {
      filter(method);
      transmitMany(endpointId, requestId, function.apply(endpointId));
    }
  }

  private class NoneToNoneHandler implements Handler {
    private final Consumer<String> consumer;

    private NoneToNoneHandler(Consumer<String> consumer) {
      this.consumer = consumer;
    }

    @Override
    public void handle(String endpointId, String requestId, String method, JsonRpcParams params) {
      filter(method);
      consumer.accept(endpointId);
    }
  }
}

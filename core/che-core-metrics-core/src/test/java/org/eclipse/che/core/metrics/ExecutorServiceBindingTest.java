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
package org.eclipse.che.core.metrics;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.inject.Inject;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

public class ExecutorServiceBindingTest {

  Injector injector;

  @BeforeTest
  public void init() {
    injector = Guice.createInjector(new MetricsModule(), new DefaultConfigurationModule());
  }

  @Test
  public void test() {
    TestComponent component = injector.getInstance(TestComponent.class);
    component.getExecutorService();
  }

  public static class DefaultConfigurationModule extends AbstractModule {
    @Override
    protected void configure() {
      bindConstant().annotatedWith(Names.named("che.metrics.port")).to(10);
      bind(MeterRegistry.class).to(SimpleMeterRegistry.class);
      bind(ExecutorService.class)
          .annotatedWith(Names.named("test.executor"))
          .toProvider(TestExecutorProvider.class);
    }
  }

  public static class TestComponent {
    private final ExecutorService executorService;

    @Inject
    public TestComponent(@Named("test.executor") ExecutorService executorService) {
      this.executorService = executorService;
    }

    public ExecutorService getExecutorService() {
      return executorService;
    }
  }

  public static class TestExecutorProvider implements Provider<ExecutorService> {
    @Override
    public ExecutorService get() {
      return Executors.newSingleThreadExecutor();
    }
  }
}

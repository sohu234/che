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

import com.google.common.annotations.Beta;
import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.matcher.Matchers;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Named;
import com.google.inject.spi.ProvisionListener;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.logging.LogbackMetrics;
import io.micrometer.core.instrument.binder.system.FileDescriptorMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.binder.system.UptimeMetrics;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.prometheus.client.CollectorRegistry;
import java.lang.annotation.Annotation;
import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Beta
public class MetricsModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(MetricsServer.class).asEagerSingleton();
    bind(MetricsBinder.class).asEagerSingleton();
    bind(CollectorRegistry.class).toInstance(CollectorRegistry.defaultRegistry);
    bind(PrometheusMeterRegistry.class)
        .toProvider(PrometheusMeterRegistryProvider.class)
        .asEagerSingleton();

    Multibinder<MeterBinder> meterMultibinder =
        Multibinder.newSetBinder(binder(), MeterBinder.class);
    meterMultibinder.addBinding().to(ClassLoaderMetrics.class);
    meterMultibinder.addBinding().to(JvmMemoryMetrics.class);
    meterMultibinder.addBinding().to(JvmGcMetrics.class);
    meterMultibinder.addBinding().to(JvmThreadMetrics.class);
    meterMultibinder.addBinding().to(LogbackMetrics.class);
    meterMultibinder.addBinding().to(FileDescriptorMetrics.class);
    meterMultibinder.addBinding().to(ProcessorMetrics.class);
    meterMultibinder.addBinding().to(UptimeMetrics.class);
    meterMultibinder.addBinding().to(FileStoresMeterBinder.class);
    meterMultibinder.addBinding().to(ApiResponseCounter.class);

    bindListener(
        Matchers.any(), new MyProvisionListener(getProvider(PrometheusMeterRegistry.class)));
  }

  private static class MyProvisionListener implements ProvisionListener {

    private final Logger LOG = LoggerFactory.getLogger(ProvisionListener.class);
    Provider<PrometheusMeterRegistry> meterRegistryProvider;

    public MyProvisionListener(Provider<PrometheusMeterRegistry> meterRegistryProvider) {
      this.meterRegistryProvider = meterRegistryProvider;
    }

    @Override
    public <T> void onProvision(ProvisionInvocation<T> provision) {
      T obj = provision.provision();
      //
      if (obj != null && ExecutorService.class.isAssignableFrom(obj.getClass())) {
        Key<T> key = provision.getBinding().getKey();
        Annotation an = key.getAnnotation();
        if (an != null) {
          String name = null;
          if (Named.class.isAssignableFrom(an.annotationType())) {
            name = ((Named) an).value();
          } else if (javax.inject.Named.class.isAssignableFrom(an.annotationType())) {
            name = ((javax.inject.Named) an).value();
          }
          LOG.info("Binding metrics for {} with name {} ", obj, name);
          ExecutorServiceMetrics.monitor(
              meterRegistryProvider.get(), (ExecutorService) obj, name, Tags.empty());
        }
      }
    }
  }
}

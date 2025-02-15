/**
 * Copyright (C) 2015 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.fabric8.kubernetes.client.vertx;

import io.fabric8.kubernetes.client.http.HttpClient;
import io.fabric8.kubernetes.client.http.StandardHttpClientBuilder;
import io.fabric8.kubernetes.client.http.TlsVersion;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.IdentityCipherSuiteFilter;
import io.netty.handler.ssl.JdkSslContext;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.net.JdkSSLEngineOptions;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.ProxyType;
import io.vertx.core.spi.tls.SslContextFactory;
import io.vertx.ext.web.client.WebClientOptions;

import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class VertxHttpClientBuilder<F extends HttpClient.Factory>
    extends StandardHttpClientBuilder<VertxHttpClient<F>, F, VertxHttpClientBuilder<F>> {

  private static final int MAX_CONNECTIONS = 8192;

  final Vertx vertx;

  public VertxHttpClientBuilder(F clientFactory, Vertx vertx) {
    super(clientFactory);
    this.vertx = vertx;
  }

  @Override
  public VertxHttpClient<F> build() {
    WebClientOptions options = new WebClientOptions();

    options.setMaxPoolSize(MAX_CONNECTIONS);
    options.setMaxWebSockets(MAX_CONNECTIONS);
    options.setIdleTimeoutUnit(TimeUnit.SECONDS);

    if (this.connectTimeout != null) {
      options.setConnectTimeout((int) this.connectTimeout.toMillis());
    }

    if (this.writeTimeout != null) {
      options.setWriteIdleTimeout((int) this.writeTimeout.getSeconds());
    }

    if (this.followRedirects) {
      options.setFollowRedirects(followRedirects);
    }

    if (this.proxyAddress != null) {
      ProxyOptions proxyOptions = new ProxyOptions()
          .setHost(this.proxyAddress.getHostName())
          .setPort(this.proxyAddress.getPort())
          .setType(ProxyType.HTTP);
      options.setProxyOptions(proxyOptions);
    }

    if (tlsVersions != null && tlsVersions.length > 0) {
      Stream.of(tlsVersions).map(TlsVersion::javaName).forEach(options::addEnabledSecureTransportProtocol);
    }

    if (this.preferHttp11) {
      options.setProtocolVersion(HttpVersion.HTTP_1_1);
    }

    if (this.sslContext != null) {
      options.setSsl(true);
      options.setSslEngineOptions(new JdkSSLEngineOptions() {
        @Override
        public JdkSSLEngineOptions copy() {
          return this;
        }

        @Override
        public SslContextFactory sslContextFactory() {
          return () -> new JdkSslContext(
              sslContext,
              true,
              null,
              IdentityCipherSuiteFilter.INSTANCE,
              ApplicationProtocolConfig.DISABLED,
              io.netty.handler.ssl.ClientAuth.NONE,
              null,
              false);
        }
      });
    }

    // track derived clients to clean up properly
    VertxHttpClient<F> result = new VertxHttpClient<>(this, options, proxyAddress != null ? proxyAuthorization : null);
    if (this.client != null) {
      this.client.addDerivedClient(result);
    }
    return result;
  }

  @Override
  protected VertxHttpClientBuilder<F> newInstance(F clientFactory) {
    return new VertxHttpClientBuilder<>(clientFactory, vertx);
  }

}

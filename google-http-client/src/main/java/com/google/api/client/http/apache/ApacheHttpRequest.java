/*
 * Copyright (c) 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.api.client.http.apache;

import com.google.api.client.http.DnsResolver;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.util.Preconditions;
import com.google.common.base.Optional;
import java.io.IOException;
import java.net.InetAddress;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpHost;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpRequestBase;

/** @author Yaniv Inbar */
final class ApacheHttpRequest extends LowLevelHttpRequest {
  private final HttpClient httpClient;

  private final HttpRequestBase request;
  private final DnsResolver dnsResolver;

  private RequestConfig.Builder requestConfig;

  ApacheHttpRequest(HttpClient httpClient, HttpRequestBase request) {
    this(httpClient, request, /*dnsResolver=*/ null);
  }

  ApacheHttpRequest(HttpClient httpClient, HttpRequestBase request, DnsResolver dnsResolver) {
    this.httpClient = httpClient;
    this.request = request;
    this.requestConfig = RequestConfig.custom().setRedirectsEnabled(false);
    this.dnsResolver = dnsResolver;
  }

  @Override
  public void addHeader(String name, String value) {
    request.addHeader(name, value);
  }

  @Override
  public void setTimeout(int connectTimeout, int readTimeout) throws IOException {
    requestConfig.setConnectionRequestTimeout(connectTimeout).setSocketTimeout(readTimeout);
  }

  @Override
  public LowLevelHttpResponse execute() throws IOException {
    if (getStreamingContent() != null) {
      Preconditions.checkArgument(
          request instanceof HttpEntityEnclosingRequest,
          "Apache HTTP client does not support %s requests with content.",
          request.getRequestLine().getMethod());
      ContentEntity entity = new ContentEntity(getContentLength(), getStreamingContent());
      entity.setContentEncoding(getContentEncoding());
      entity.setContentType(getContentType());
      ((HttpEntityEnclosingRequest) request).setEntity(entity);
    }
    request.setConfig(requestConfig.build());
    if (dnsResolver == null) {
      // Use the default OS DNS resolution.
      return new ApacheHttpResponse(request, httpClient.execute(request));
    }

    // Use the IP provided by our specialized DNS resolver.
    InetAddress ip = dnsResolver.resolve(request.getURI().getHost());
    int port =
        request.getURI().getPort() != -1
            ? request.getURI().getPort()
            : "https".equals(request.getURI().getScheme()) ? 443 : 80;
    return new ApacheHttpResponse(
        request,
        httpClient.execute(
            new HttpHost(ip, request.getURI().getHost(), port, request.getURI().getScheme()),
            request));
  }
}

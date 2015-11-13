/*
 * Copyright 2014 NAVER Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.navercorp.pinpoint.plugin.okhttp.interceptor;

import com.navercorp.pinpoint.bootstrap.context.*;
import com.navercorp.pinpoint.bootstrap.interceptor.AroundInterceptor;
import com.navercorp.pinpoint.bootstrap.interceptor.annotation.Group;
import com.navercorp.pinpoint.bootstrap.interceptor.group.ExecutionPolicy;
import com.navercorp.pinpoint.bootstrap.interceptor.group.InterceptorGroup;
import com.navercorp.pinpoint.bootstrap.interceptor.group.InterceptorGroupInvocation;
import com.navercorp.pinpoint.bootstrap.logging.PLogger;
import com.navercorp.pinpoint.bootstrap.logging.PLoggerFactory;
import com.navercorp.pinpoint.bootstrap.sampler.SamplingFlagUtils;
import com.navercorp.pinpoint.plugin.okhttp.OkHttpConstants;
import com.navercorp.pinpoint.plugin.okhttp.UrlGetter;
import com.squareup.okhttp.Request;

import java.net.URL;

/**
 * @author jaehong.kim
 */
@Group(value = OkHttpConstants.SEND_REQUEST_SCOPE, executionPolicy = ExecutionPolicy.INTERNAL)
public class RequestBuilderBuildMethodBackwardCompatibilityInterceptor implements AroundInterceptor {
    private final PLogger logger = PLoggerFactory.getLogger(this.getClass());
    private final boolean isDebug = logger.isDebugEnabled();

    private TraceContext traceContext;
    private MethodDescriptor methodDescriptor;
    private InterceptorGroup interceptorGroup;

    public RequestBuilderBuildMethodBackwardCompatibilityInterceptor(TraceContext traceContext, MethodDescriptor methodDescriptor, InterceptorGroup interceptorGroup) {
        this.traceContext = traceContext;
        this.methodDescriptor = methodDescriptor;
        this.interceptorGroup = interceptorGroup;
    }

    @Override
    public void before(Object target, Object[] args) {
        if (isDebug) {
            logger.beforeInterceptor(target, args);
        }

        final Trace trace = traceContext.currentRawTraceObject();
        if (trace == null) {
            return;
        }

        try {
            final Request.Builder builder = ((Request.Builder) target);
            if (!trace.canSampled()) {
                if (isDebug) {
                    logger.debug("set Sampling flag=false");
                }
                ((Request.Builder) target).header(Header.HTTP_SAMPLED.toString(), SamplingFlagUtils.SAMPLING_RATE_FALSE);
                return;
            }

            final InterceptorGroupInvocation invocation = interceptorGroup.getCurrentInvocation();
            if (invocation == null || invocation.getAttachment() == null || !(invocation.getAttachment() instanceof TraceId)) {
                logger.debug("Invalid interceptor group invocation. {}", invocation);
                return;
            }

            final TraceId nextId = (TraceId) invocation.getAttachment();
            builder.header(Header.HTTP_TRACE_ID.toString(), nextId.getTransactionId());
            builder.header(Header.HTTP_SPAN_ID.toString(), String.valueOf(nextId.getSpanId()));

            builder.header(Header.HTTP_PARENT_SPAN_ID.toString(), String.valueOf(nextId.getParentSpanId()));

            builder.header(Header.HTTP_FLAGS.toString(), String.valueOf(nextId.getFlags()));
            builder.header(Header.HTTP_PARENT_APPLICATION_NAME.toString(), traceContext.getApplicationName());
            builder.header(Header.HTTP_PARENT_APPLICATION_TYPE.toString(), Short.toString(traceContext.getServerTypeCode()));

            if (target instanceof UrlGetter) {
                final URL url = ((UrlGetter) target)._$PINPOINT$_getUrl();
                if (url != null) {
                    final String endpoint = getDestinationId(url);
                    logger.debug("Set HTTP_HOST {}", endpoint);
                    builder.header(Header.HTTP_HOST.toString(), endpoint);
                }
            }
        } catch (Throwable t) {
            logger.warn("Failed to BEFORE process. {}", t.getMessage(), t);
        }
    }

    private String getDestinationId(URL httpUrl) {
        if (httpUrl == null || httpUrl.getHost() == null) {
            return "UnknownHttpClient";
        }
        if (httpUrl.getPort() <= 0 || httpUrl.getPort() == httpUrl.getDefaultPort()) {
            return httpUrl.getHost();
        }
        final StringBuilder sb = new StringBuilder();
        sb.append(httpUrl.getHost());
        sb.append(':');
        sb.append(httpUrl.getPort());
        return sb.toString();
    }

    @Override
    public void after(Object target, Object[] args, Object result, Throwable throwable) {
        if (isDebug) {
            logger.afterInterceptor(target, args);
        }
    }
}
/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.netty.handler.timeout;

import java.util.concurrent.TimeUnit;

import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelDownstreamHandler;
import org.jboss.netty.util.ExternalResourceReleasable;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 */
@ChannelPipelineCoverage("one")
public class WriteTimeoutHandler extends SimpleChannelDownstreamHandler implements ExternalResourceReleasable {

    static final ChannelWriteTimeoutException EXCEPTION = new ChannelWriteTimeoutException();

    private final Timer timer;
    private final long timeoutNanos;

    public WriteTimeoutHandler(
            Timer timer, long timeout, TimeUnit unit) {
        if (timer == null) {
            throw new NullPointerException("timer");
        }
        if (unit == null) {
            throw new NullPointerException("unit");
        }
        if (timeout <= 0) {
            throw new IllegalArgumentException(
                    "timeout must be greater than 0: " + timeout);
        }

        this.timer = timer;
        timeoutNanos = unit.toNanos(timeout);
    }

    public void releaseExternalResources() {
        timer.stop();
    }

    protected long getTimeoutNanos(@SuppressWarnings("unused") MessageEvent e) {
        return timeoutNanos;
    }

    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception {

        long timeoutNanos = getTimeoutNanos(e);
        if (timeoutNanos > 0) {
            // Set timeout only when getTimeoutNanos() returns a positive value.
            ChannelFuture future = e.getFuture();
            final Timeout timeout = timer.newTimeout(
                    new WriteTimeoutTask(ctx, future),
                    timeoutNanos, TimeUnit.NANOSECONDS);

            future.addListener(new ChannelFutureListener() {
                public void operationComplete(ChannelFuture future) throws Exception {
                    timeout.cancel();
                }
            });
        }

        super.writeRequested(ctx, e);
    }

    private class WriteTimeoutTask implements TimerTask {

        private final ChannelHandlerContext ctx;
        private final ChannelFuture future;

        WriteTimeoutTask(ChannelHandlerContext ctx, ChannelFuture future) {
            this.ctx = ctx;
            this.future = future;
        }

        public void run(Timeout timeout) throws Exception {
            if (timeout.isCancelled()) {
                return;
            }

            // Mark the future as failure
            if (future.setFailure(EXCEPTION)) {
                // If succeeded to mark as failure, notify the pipeline, too.
                Channels.fireExceptionCaught(ctx, EXCEPTION);
            }
        }
    }
}

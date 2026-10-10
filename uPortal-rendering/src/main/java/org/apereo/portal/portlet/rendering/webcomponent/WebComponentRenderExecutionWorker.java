/**
 * Licensed to Apereo under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright ownership. Apereo
 * licenses this file to you under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the License at the
 * following location:
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apereo.portal.portlet.rendering.webcomponent;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apereo.portal.portlet.om.IPortletWindow;
import org.apereo.portal.portlet.om.IPortletWindowId;
import org.apereo.portal.portlet.rendering.IPortletRenderer;
import org.apereo.portal.portlet.rendering.PortletRenderResult;
import org.apereo.portal.portlet.rendering.worker.IPortletRenderExecutionWorker;
import org.jspecify.annotations.Nullable;

/**
 * Renders a web-component channel for the portlet execution manager, without the portlet container
 * and without a worker thread.
 *
 * <p>The execution manager starts a worker for every channel while the page streams, then asks each
 * worker for its title and output as the channel's placeholders are reached. This worker does
 * nothing when submitted and renders on the first of those questions, on the request thread. That
 * keeps the rendering in page order, which {@link WebComponentChannelRenderer} relies on to emit
 * each module script only for the first channel that needs it.
 *
 * <p>The channel's title is its definition's title, and it reports no new items and no external
 * link. In the exclusive window state the portal writes no page around the channel, so the output
 * goes straight to the response, as the portlet container does for portlets.
 */
public class WebComponentRenderExecutionWorker implements IPortletRenderExecutionWorker {

    private final WebComponentChannelRenderer renderer;
    private final HttpServletRequest request;
    private final HttpServletResponse response;
    private final IPortletWindow portletWindow;
    private final Map<String, Object> executionAttributes = new ConcurrentHashMap<>();

    private volatile long submitted;
    private volatile long started;
    private volatile long complete;
    private volatile boolean retrieved;
    // Set once, by render(), under the lock
    private @Nullable Rendered rendered;
    private @Nullable RuntimeException failure;

    public WebComponentRenderExecutionWorker(
            WebComponentChannelRenderer renderer,
            HttpServletRequest request,
            HttpServletResponse response,
            IPortletWindow portletWindow) {
        this.renderer = renderer;
        this.request = request;
        this.response = response;
        this.portletWindow = portletWindow;
    }

    @Override
    public void submit() {
        if (submitted > 0) {
            throw new IllegalStateException(this + " has already been submitted");
        }
        submitted = System.currentTimeMillis();
    }

    @Override
    public long waitForStart(long timeout) {
        return started;
    }

    @Override
    public synchronized PortletRenderResult get(long timeout) throws Exception {
        retrieved = true;
        return render().result;
    }

    @Override
    public synchronized String getOutput(long timeout) throws Exception {
        retrieved = true;
        return render().output;
    }

    /** Renders the channel the first time it is asked for; later calls repeat the outcome. */
    private synchronized Rendered render() throws IOException {
        final RuntimeException failed = failure;
        if (failed != null) {
            throw failed;
        }
        final Rendered done = rendered;
        if (done != null) {
            return done;
        }
        if (submitted == 0) {
            throw new IllegalStateException(this + " has not been submitted");
        }
        started = System.currentTimeMillis();
        final String output;
        try {
            output = renderer.render(request, portletWindow);
        } catch (RuntimeException e) {
            failure = e;
            throw e;
        } finally {
            complete = System.currentTimeMillis();
        }
        final Rendered result =
                new Rendered(output, new PortletRenderResult(null, null, 0, complete - started));
        rendered = result;
        if (IPortletRenderer.EXCLUSIVE.equals(portletWindow.getWindowState())) {
            writeExclusiveOutput(output);
        }
        return result;
    }

    private void writeExclusiveOutput(String output) throws IOException {
        if (response.getContentType() == null) {
            response.setContentType("text/html;charset=UTF-8");
        }
        final PrintWriter writer = response.getWriter();
        writer.write(output);
        writer.flush();
    }

    /** Nothing runs in the background, so there is nothing to cancel. */
    @Override
    public void cancel() {}

    @Override
    public int getCancelCount() {
        return 0;
    }

    @Override
    public boolean isRetrieved() {
        return retrieved;
    }

    @Override
    public ExecutionType getExecutionType() {
        return ExecutionType.RENDER;
    }

    @Override
    public @Nullable Object setExecutionAttribute(String name, @Nullable Object value) {
        return value == null
                ? executionAttributes.remove(name)
                : executionAttributes.put(name, value);
    }

    @Override
    public @Nullable Object getExecutionAttribute(String name) {
        return executionAttributes.get(name);
    }

    @Override
    public IPortletWindowId getPortletWindowId() {
        return portletWindow.getPortletWindowId();
    }

    @Override
    public String getPortletFname() {
        return portletWindow.getPortletEntity().getPortletDefinition().getFName();
    }

    /** The worker never waits on anything, so no timeout applies. */
    @Override
    public long getApplicableTimeout() {
        return -1;
    }

    @Override
    public boolean isSubmitted() {
        return submitted > 0;
    }

    @Override
    public boolean isStarted() {
        return started > 0;
    }

    @Override
    public boolean isComplete() {
        return complete > 0;
    }

    @Override
    public long getSubmittedTime() {
        return submitted;
    }

    @Override
    public long getStartedTime() {
        return started;
    }

    @Override
    public long getCompleteTime() {
        return complete;
    }

    @Override
    public long getWait() {
        return started > 0 ? started - submitted : 0;
    }

    @Override
    public long getDuration() {
        return complete > 0 ? complete - started : 0;
    }

    @Override
    public String toString() {
        return "WebComponentRenderExecutionWorker [portletWindowId="
                + portletWindow.getPortletWindowId()
                + "]";
    }

    /** The markup a successful render produced, and its result. */
    private static final class Rendered {
        private final String output;
        private final PortletRenderResult result;

        private Rendered(String output, PortletRenderResult result) {
            this.output = output;
            this.result = result;
        }
    }
}

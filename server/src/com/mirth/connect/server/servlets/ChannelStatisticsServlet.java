/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.server.servlets;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.log4j.Logger;
import org.eclipse.jetty.io.RuntimeIOException;

import com.mirth.connect.client.core.Operation;
import com.mirth.connect.client.core.Operations;
import com.mirth.connect.donkey.model.message.Status;
import com.mirth.connect.model.ChannelStatistics;
import com.mirth.connect.model.converters.ObjectXMLSerializer;
import com.mirth.connect.server.controllers.ChannelController;
import com.mirth.connect.server.controllers.ConfigurationController;

public class ChannelStatisticsServlet extends MirthServlet {
    private Logger logger = Logger.getLogger(this.getClass());
    private ChannelController channelController = ChannelController.getInstance();

    public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // MIRTH-1745
        response.setCharacterEncoding("UTF-8");

        if (!isUserLoggedIn(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
        } else {
            try {
                ObjectXMLSerializer serializer = ObjectXMLSerializer.getInstance();
                PrintWriter out = response.getWriter();
                Operation operation = Operations.getOperation(request.getParameter("op"));
                Map<String, Object> parameterMap = new HashMap<String, Object>();

                if (operation.equals(Operations.CHANNEL_STATS_GET)) {
                    String channelId = request.getParameter("id");
                    parameterMap.put("channelId", channelId);

                    if (!isUserAuthorized(request, parameterMap)) {
                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                    } else {
                        response.setContentType(APPLICATION_XML);
                        Map<Status, Long> map = channelController.getStatistics().getConnectorStats(channelId, null);

                        ChannelStatistics channelStatistics = new ChannelStatistics();
                        channelStatistics.setChannelId(channelId);
                        channelStatistics.setServerId(ConfigurationController.getInstance().getServerId());
                        channelStatistics.setError(map.get(Status.ERROR));
                        channelStatistics.setFiltered(map.get(Status.FILTERED));
                        channelStatistics.setReceived(map.get(Status.RECEIVED));
                        channelStatistics.setSent(map.get(Status.SENT));

                        serializer.serialize(channelStatistics, out);
                    }
                } else if (operation.equals(Operations.CHANNEL_STATS_CLEAR)) {
                    @SuppressWarnings("unchecked")
                    Map<String, List<Integer>> channelConnectorMap = serializer.deserialize(request.getParameter("channelConnectorMap"), Map.class);
                    parameterMap.put("channelConnectorMap", channelConnectorMap);

                    if (!isUserAuthorized(request, parameterMap)) {
                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                    } else {
                        boolean deleteReceived = Boolean.valueOf(request.getParameter("deleteReceived"));
                        boolean deleteFiltered = Boolean.valueOf(request.getParameter("deleteFiltered"));
                        boolean deleteSent = Boolean.valueOf(request.getParameter("deleteSent"));
                        boolean deleteErrored = Boolean.valueOf(request.getParameter("deleteErrored"));

                        Set<Status> statusesToClear = new HashSet<Status>();

                        if (deleteReceived) {
                            statusesToClear.add(Status.RECEIVED);
                        }
                        if (deleteFiltered) {
                            statusesToClear.add(Status.FILTERED);
                        }
                        if (deleteSent) {
                            statusesToClear.add(Status.SENT);
                        }
                        if (deleteErrored) {
                            statusesToClear.add(Status.ERROR);
                        }

                        channelController.resetStatistics(channelConnectorMap, statusesToClear);
                    }
                } else if (operation.equals(Operations.CHANNEL_STATS_CLEAR_ALL)) {
                    if (!isUserAuthorized(request, parameterMap)) {
                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                    } else {
                        channelController.resetAllStatistics();
                    }
                }
            } catch (RuntimeIOException rio) {
                logger.debug(rio);
            } catch (Throwable t) {
                logger.error(ExceptionUtils.getStackTrace(t));
                throw new ServletException(t);
            }
        }
    }
}

/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.donkey.model.channel;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.builder.EqualsBuilder;

import com.mirth.connect.donkey.util.DonkeyElement;
import com.mirth.connect.donkey.util.migration.Migratable;
import com.mirth.connect.donkey.util.purge.Purgable;

public class DestinationConnectorProperties implements Serializable, Migratable, Purgable {
    private boolean queueEnabled;
    private boolean sendFirst;
    private int retryIntervalMillis;
    private boolean regenerateTemplate;
    private int retryCount;
    private boolean rotate;
    private int threadCount;
    private boolean validateResponse;

    public DestinationConnectorProperties() {
        this(false);
    }

    public DestinationConnectorProperties(boolean validateResponse) {
        queueEnabled = false;
        sendFirst = false;
        retryIntervalMillis = 10000;
        regenerateTemplate = false;
        retryCount = 0;
        rotate = false;
        threadCount = 1;
        this.validateResponse = validateResponse;
    }

    public DestinationConnectorProperties(DestinationConnectorProperties props) {
        queueEnabled = props.isQueueEnabled();
        sendFirst = props.isSendFirst();
        retryIntervalMillis = props.getRetryIntervalMillis();
        regenerateTemplate = props.isRegenerateTemplate();
        retryCount = props.getRetryCount();
        rotate = props.isRotate();
        threadCount = props.getThreadCount();
        validateResponse = props.isValidateResponse();
    }

    public boolean isQueueEnabled() {
        return queueEnabled;
    }

    public void setQueueEnabled(boolean enabled) {
        this.queueEnabled = enabled;
    }

    public boolean isSendFirst() {
        return sendFirst;
    }

    public void setSendFirst(boolean sendFirst) {
        this.sendFirst = sendFirst;
    }

    public int getRetryIntervalMillis() {
        return retryIntervalMillis;
    }

    public void setRetryIntervalMillis(int retryIntervalMillis) {
        this.retryIntervalMillis = retryIntervalMillis;
    }

    public boolean isRegenerateTemplate() {
        return regenerateTemplate;
    }

    public void setRegenerateTemplate(boolean regenerateTemplate) {
        this.regenerateTemplate = regenerateTemplate;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public boolean isRotate() {
        return rotate;
    }

    public void setRotate(boolean rotate) {
        this.rotate = rotate;
    }

    public int getThreadCount() {
        return threadCount;
    }

    public void setThreadCount(int threadCount) {
        this.threadCount = threadCount;
    }

    public boolean isValidateResponse() {
        return validateResponse;
    }

    public void setValidateResponse(boolean validateResponse) {
        this.validateResponse = validateResponse;
    }

    @Override
    public boolean equals(Object obj) {
        return EqualsBuilder.reflectionEquals(this, obj);
    }

    @Override
    public void migrate3_0_1(DonkeyElement element) {}

    @Override
    public void migrate3_0_2(DonkeyElement element) {}

    @Override
    public void migrate3_1_0(DonkeyElement element) {
        element.addChildElementIfNotExists("validateResponse", "false");
    }

    @Override
    public Map<String, Object> getPurgedProperties() {
        Map<String, Object> purgedProperties = new HashMap<String, Object>();
        purgedProperties.put("queueEnabled", queueEnabled);
        purgedProperties.put("sendFirst", sendFirst);
        purgedProperties.put("retryIntervalMillis", retryIntervalMillis);
        purgedProperties.put("regenerateTemplate", regenerateTemplate);
        purgedProperties.put("retryCount", retryCount);
        purgedProperties.put("rotate", rotate);
        purgedProperties.put("threadCount", threadCount);
        purgedProperties.put("validateResponse", validateResponse);
        return purgedProperties;
    }
}

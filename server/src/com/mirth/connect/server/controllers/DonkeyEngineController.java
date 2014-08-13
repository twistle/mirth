/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.server.controllers;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.log4j.Logger;

import com.mirth.commons.encryption.Encryptor;
import com.mirth.connect.donkey.model.channel.ConnectorProperties;
import com.mirth.connect.donkey.model.channel.DeployedState;
import com.mirth.connect.donkey.model.channel.SourceConnectorProperties;
import com.mirth.connect.donkey.model.channel.SourceConnectorPropertiesInterface;
import com.mirth.connect.donkey.model.event.ErrorEventType;
import com.mirth.connect.donkey.model.event.Event;
import com.mirth.connect.donkey.model.message.BatchRawMessage;
import com.mirth.connect.donkey.model.message.RawMessage;
import com.mirth.connect.donkey.model.message.SerializationType;
import com.mirth.connect.donkey.model.message.XmlSerializer;
import com.mirth.connect.donkey.model.message.XmlSerializerException;
import com.mirth.connect.donkey.model.message.attachment.AttachmentHandler;
import com.mirth.connect.donkey.model.message.attachment.AttachmentHandlerProperties;
import com.mirth.connect.donkey.server.Constants;
import com.mirth.connect.donkey.server.DeployException;
import com.mirth.connect.donkey.server.Donkey;
import com.mirth.connect.donkey.server.DonkeyConfiguration;
import com.mirth.connect.donkey.server.StartException;
import com.mirth.connect.donkey.server.StopException;
import com.mirth.connect.donkey.server.channel.ChannelException;
import com.mirth.connect.donkey.server.channel.DestinationChain;
import com.mirth.connect.donkey.server.channel.DestinationConnector;
import com.mirth.connect.donkey.server.channel.DispatchResult;
import com.mirth.connect.donkey.server.channel.FilterTransformerExecutor;
import com.mirth.connect.donkey.server.channel.MetaDataReplacer;
import com.mirth.connect.donkey.server.channel.ResponseSelector;
import com.mirth.connect.donkey.server.channel.ResponseTransformerExecutor;
import com.mirth.connect.donkey.server.channel.SourceConnector;
import com.mirth.connect.donkey.server.channel.Statistics;
import com.mirth.connect.donkey.server.channel.StorageSettings;
import com.mirth.connect.donkey.server.channel.components.PostProcessor;
import com.mirth.connect.donkey.server.channel.components.PreProcessor;
import com.mirth.connect.donkey.server.data.buffered.BufferedDaoFactory;
import com.mirth.connect.donkey.server.data.passthru.DelayedStatisticsUpdater;
import com.mirth.connect.donkey.server.data.passthru.PassthruDaoFactory;
import com.mirth.connect.donkey.server.event.ErrorEvent;
import com.mirth.connect.donkey.server.event.EventDispatcher;
import com.mirth.connect.donkey.server.message.DataType;
import com.mirth.connect.donkey.server.message.ResponseValidator;
import com.mirth.connect.donkey.server.message.batch.BatchAdaptorFactory;
import com.mirth.connect.donkey.server.message.batch.BatchMessageException;
import com.mirth.connect.donkey.server.message.batch.BatchMessageReader;
import com.mirth.connect.donkey.server.message.batch.ResponseHandler;
import com.mirth.connect.donkey.server.message.batch.SimpleResponseHandler;
import com.mirth.connect.donkey.server.queue.ConnectorMessageQueue;
import com.mirth.connect.model.Channel;
import com.mirth.connect.model.ChannelProperties;
import com.mirth.connect.model.CodeTemplate.ContextType;
import com.mirth.connect.model.Connector;
import com.mirth.connect.model.ConnectorMetaData;
import com.mirth.connect.model.DashboardStatus;
import com.mirth.connect.model.DashboardStatus.StatusType;
import com.mirth.connect.model.Filter;
import com.mirth.connect.model.MessageStorageMode;
import com.mirth.connect.model.ServerEventContext;
import com.mirth.connect.model.Transformer;
import com.mirth.connect.model.attachments.AttachmentHandlerType;
import com.mirth.connect.model.datatype.BatchProperties;
import com.mirth.connect.model.datatype.DataTypeProperties;
import com.mirth.connect.model.datatype.SerializerProperties;
import com.mirth.connect.plugins.ChannelPlugin;
import com.mirth.connect.plugins.DataTypeServerPlugin;
import com.mirth.connect.server.attachments.JavaScriptAttachmentHandler;
import com.mirth.connect.server.attachments.MirthAttachmentHandler;
import com.mirth.connect.server.attachments.PassthruAttachmentHandler;
import com.mirth.connect.server.builders.JavaScriptBuilder;
import com.mirth.connect.server.channel.MirthMetaDataReplacer;
import com.mirth.connect.server.message.DataTypeFactory;
import com.mirth.connect.server.message.DefaultResponseValidator;
import com.mirth.connect.server.mybatis.MessageSearchResult;
import com.mirth.connect.server.transformers.JavaScriptFilterTransformer;
import com.mirth.connect.server.transformers.JavaScriptPostprocessor;
import com.mirth.connect.server.transformers.JavaScriptPreprocessor;
import com.mirth.connect.server.transformers.JavaScriptResponseTransformer;
import com.mirth.connect.server.util.GlobalChannelVariableStoreFactory;
import com.mirth.connect.server.util.GlobalVariableStore;
import com.mirth.connect.server.util.javascript.JavaScriptExecutorException;
import com.mirth.connect.server.util.javascript.JavaScriptUtil;

public class DonkeyEngineController implements EngineController {
    private static DonkeyEngineController instance = null;

    public static DonkeyEngineController getInstance() {
        synchronized (DonkeyEngineController.class) {
            if (instance == null) {
                instance = new DonkeyEngineController();
            }

            return instance;
        }
    }

    private Donkey donkey = Donkey.getInstance();
    private Logger logger = Logger.getLogger(this.getClass());
    private ConfigurationController configurationController = ControllerFactory.getFactory().createConfigurationController();
    private ScriptController scriptController = ControllerFactory.getFactory().createScriptController();
    private ChannelController channelController = ControllerFactory.getFactory().createChannelController();
    private com.mirth.connect.donkey.server.controllers.ChannelController donkeyChannelController = com.mirth.connect.donkey.server.controllers.ChannelController.getInstance();
    private EventController eventController = ControllerFactory.getFactory().createEventController();
    private ExtensionController extensionController = ControllerFactory.getFactory().createExtensionController();
    private int queueBufferSize = Constants.DEFAULT_QUEUE_BUFFER_SIZE;
    private Map<String, ExecutorService> engineExecutors = new HashMap<String, ExecutorService>();

    private enum StatusTask {
        START, STOP, PAUSE, RESUME
    };

    protected DonkeyEngineController() {}

    @Override
    public void startEngine() throws StartException, StopException, ControllerException, InterruptedException {
        logger.debug("starting donkey engine");

        Integer queueBufferSize = configurationController.getServerSettings().getQueueBufferSize();
        if (queueBufferSize != null) {
            this.queueBufferSize = queueBufferSize;
        }

        final Encryptor encryptor = configurationController.getEncryptor();

        com.mirth.connect.donkey.server.Encryptor donkeyEncryptor = new com.mirth.connect.donkey.server.Encryptor() {
            @Override
            public String encrypt(String text) {
                return encryptor.encrypt(text);
            }

            @Override
            public String decrypt(String text) {
                return encryptor.decrypt(text);
            }
        };

        EventDispatcher eventDispatcher = new EventDispatcher() {

            @Override
            public void dispatchEvent(Event event) {
                eventController.dispatchEvent(event);
            }
        };

        donkey.startEngine(new DonkeyConfiguration(configurationController.getApplicationDataDir(), configurationController.getDatabaseSettings().getProperties(), donkeyEncryptor, eventDispatcher, configurationController.getServerId()));
    }

    @Override
    public void stopEngine() throws StopException, InterruptedException {
        undeployChannels(getDeployedIds(), ServerEventContext.SYSTEM_USER_EVENT_CONTEXT);
        donkey.stopEngine();
    }

    @Override
    public boolean isRunning() {
        return donkey.isRunning();
    }

    @Override
    public void startupDeploy() throws StartException, StopException, InterruptedException {
        deployChannels(channelController.getChannelIds(), ServerEventContext.SYSTEM_USER_EVENT_CONTEXT);
    }

    @Override
    public void deployChannels(Set<String> channelIds, ServerEventContext context) {
        List<ChannelTask> deployTasks = new ArrayList<ChannelTask>();
        List<ChannelTask> undeployTasks = new ArrayList<ChannelTask>();

        for (String channelId : channelIds) {
            if (isDeployed(channelId)) {
                undeployTasks.add(new UndeployTask(channelId, context));
            }

            deployTasks.add(new DeployTask(channelId, context));
        }

        if (CollectionUtils.isNotEmpty(undeployTasks)) {
            waitForTasks(submitTasks(undeployTasks));
            executeChannelPluginOnUndeploy(context);
            executeGlobalUndeployScript();
        }

        if (CollectionUtils.isNotEmpty(deployTasks)) {
            executeGlobalDeployScript();
            executeChannelPluginOnDeploy(context);
            waitForTasks(submitTasks(deployTasks));
        }
    }

    @Override
    public void undeployChannels(Set<String> channelIds, ServerEventContext context) {
        List<ChannelTask> undeployTasks = new ArrayList<ChannelTask>();

        for (String channelId : channelIds) {
            undeployTasks.add(new UndeployTask(channelId, context));
        }

        if (CollectionUtils.isNotEmpty(undeployTasks)) {
            waitForTasks(submitTasks(undeployTasks));
            executeChannelPluginOnUndeploy(context);
            executeGlobalUndeployScript();
        }
    }

    @Override
    public void redeployAllChannels(ServerEventContext context) {
        undeployChannels(getDeployedIds(), context);
        clearGlobalMap();
        deployChannels(channelController.getChannelIds(), context);
    }

    @Override
    public void startChannels(Set<String> channelIds) {
        waitForTasks(submitTasks(buildChannelStatusTasks(channelIds, StatusTask.START)));
    }

    @Override
    public void stopChannels(Set<String> channelIds) {
        waitForTasks(submitTasks(buildChannelStatusTasks(channelIds, StatusTask.STOP)));
    }

    @Override
    public void pauseChannels(Set<String> channelIds) {
        waitForTasks(submitTasks(buildChannelStatusTasks(channelIds, StatusTask.PAUSE)));
    }

    @Override
    public void resumeChannels(Set<String> channelIds) {
        waitForTasks(submitTasks(buildChannelStatusTasks(channelIds, StatusTask.RESUME)));
    }

    @Override
    public void startConnector(Map<String, List<Integer>> connectorInfo) {
        waitForTasks(submitTasks(buildConnectorStatusTasks(connectorInfo, StatusTask.START)));
    }

    @Override
    public void stopConnector(Map<String, List<Integer>> connectorInfo) {
        waitForTasks(submitTasks(buildConnectorStatusTasks(connectorInfo, StatusTask.STOP)));
    }

    @Override
    public void haltChannels(Set<String> channelIds) {
        waitForTasks(submitHaltTasks(channelIds));
    }

    @Override
    public void removeChannels(Set<String> channelIds, ServerEventContext context) {
        List<ChannelTask> tasks = new ArrayList<ChannelTask>();

        for (Channel channel : channelController.getChannels(channelIds)) {
            tasks.add(new UndeployTask(channel.getId(), context));
            tasks.add(new RemoveTask(channel, context));
        }

        if (CollectionUtils.isNotEmpty(tasks)) {
            waitForTasks(submitTasks(tasks));
            executeChannelPluginOnUndeploy(context);
        }
    }

    @Override
    public void removeMessages(String channelId, Map<Long, MessageSearchResult> results) throws Exception {
        List<ChannelTask> tasks = new ArrayList<ChannelTask>();

        tasks.add(new RemoveMessagesTask(channelId, results));

        List<ChannelFuture> futures = submitTasks(tasks);
        if (CollectionUtils.isEmpty(futures)) {
            throw new InterruptedException();
        }

        // Don't use waitForTasks here because we want to throw any exceptions.
        for (ChannelFuture future : futures) {
            future.get();
        }
    };

    @Override
    public void removeAllMessages(Set<String> channelIds, boolean force, boolean clearStatistics) {
        List<ChannelTask> tasks = new ArrayList<ChannelTask>();

        for (String channelId : channelIds) {
            tasks.add(new RemoveAllMessagesTask(channelId, force, clearStatistics));
        }

        waitForTasks(submitTasks(tasks));
    }

    @Override
    public DashboardStatus getChannelStatus(String channelId) {
        com.mirth.connect.donkey.server.channel.Channel donkeyChannel = donkey.getDeployedChannels().get(channelId);
        if (donkeyChannel != null) {
            return getDashboardStatuses(Collections.singleton(donkeyChannel)).get(0);
        }
        return null;
    }

    @Override
    public List<DashboardStatus> getChannelStatusList() {
        return getChannelStatusList(null);
    }

    @Override
    public List<DashboardStatus> getChannelStatusList(Set<String> channelIds) {
        Collection<com.mirth.connect.donkey.server.channel.Channel> donkeyChannels = null;

        if (channelIds != null) {
            donkeyChannels = new ArrayList<com.mirth.connect.donkey.server.channel.Channel>(channelIds.size());

            for (com.mirth.connect.donkey.server.channel.Channel donkeyChannel : donkey.getDeployedChannels().values()) {
                if (channelIds.contains(donkeyChannel.getChannelId())) {
                    donkeyChannels.add(donkeyChannel);
                }
            }
        } else {
            donkeyChannels = donkey.getDeployedChannels().values();
        }

        return getDashboardStatuses(donkeyChannels);
    }

    private List<DashboardStatus> getDashboardStatuses(Collection<com.mirth.connect.donkey.server.channel.Channel> donkeyChannels) {
        List<DashboardStatus> statuses = new ArrayList<DashboardStatus>();

        Map<String, Integer> channelRevisions = null;
        try {
            channelRevisions = channelController.getChannelRevisions();
        } catch (ControllerException e) {
            logger.error("Error retrieving channel revisions", e);
        }

        for (com.mirth.connect.donkey.server.channel.Channel donkeyChannel : donkeyChannels) {
            String channelId = donkeyChannel.getChannelId();
            Channel deployedChannel = channelController.getDeployedChannelById(channelId);

            // Make sure the channel is actually still deployed
            if (deployedChannel != null) {
                Statistics stats = donkeyChannelController.getStatistics();
                Statistics lifetimeStats = donkeyChannelController.getTotalStatistics();

                DashboardStatus status = new DashboardStatus();
                status.setStatusType(StatusType.CHANNEL);
                status.setChannelId(channelId);
                status.setName(donkeyChannel.getName());
                status.setState(donkeyChannel.getCurrentState());
                status.setDeployedDate(donkeyChannel.getDeployDate());

                int channelRevision = 0;
                // Just in case the channel no longer exists
                if (channelRevisions != null && channelRevisions.containsKey(channelId)) {
                    channelRevision = channelRevisions.get(channelId);
                    status.setDeployedRevisionDelta(channelRevision - deployedChannel.getRevision());
                }

                status.setStatistics(stats.getConnectorStats(channelId, null));
                status.setLifetimeStatistics(lifetimeStats.getConnectorStats(channelId, null));
                status.setTags(deployedChannel.getProperties().getTags());

                DashboardStatus sourceStatus = new DashboardStatus();
                sourceStatus.setStatusType(StatusType.SOURCE_CONNECTOR);
                sourceStatus.setChannelId(channelId);
                sourceStatus.setMetaDataId(0);
                sourceStatus.setName("Source");
                sourceStatus.setState(donkeyChannel.getSourceConnector().getCurrentState());
                sourceStatus.setStatistics(stats.getConnectorStats(channelId, 0));
                sourceStatus.setLifetimeStatistics(lifetimeStats.getConnectorStats(channelId, 0));
                sourceStatus.setTags(deployedChannel.getProperties().getTags());
                sourceStatus.setQueueEnabled(!donkeyChannel.getSourceConnector().isRespondAfterProcessing());
                sourceStatus.setQueued(new Long(donkeyChannel.getSourceQueue().size()));

                status.setQueued(sourceStatus.getQueued());

                status.getChildStatuses().add(sourceStatus);

                for (DestinationChain chain : donkeyChannel.getDestinationChains()) {
                    for (Entry<Integer, DestinationConnector> connectorEntry : chain.getDestinationConnectors().entrySet()) {
                        Integer metaDataId = connectorEntry.getKey();
                        DestinationConnector connector = connectorEntry.getValue();

                        DashboardStatus destinationStatus = new DashboardStatus();
                        destinationStatus.setStatusType(StatusType.DESTINATION_CONNECTOR);
                        destinationStatus.setChannelId(channelId);
                        destinationStatus.setMetaDataId(metaDataId);
                        destinationStatus.setName(connector.getDestinationName());
                        destinationStatus.setState(connector.getCurrentState());
                        destinationStatus.setStatistics(stats.getConnectorStats(channelId, metaDataId));
                        destinationStatus.setLifetimeStatistics(lifetimeStats.getConnectorStats(channelId, metaDataId));
                        destinationStatus.setTags(deployedChannel.getProperties().getTags());
                        destinationStatus.setQueueEnabled(connector.isQueueEnabled());
                        destinationStatus.setQueued(new Long(connector.getQueue().size()));

                        status.setQueued(status.getQueued() + destinationStatus.getQueued());

                        status.getChildStatuses().add(destinationStatus);
                    }
                }

                statuses.add(status);
            }
        }

        Collections.sort(statuses, new Comparator<DashboardStatus>() {

            public int compare(DashboardStatus o1, DashboardStatus o2) {
                Calendar c1 = o1.getDeployedDate();
                Calendar c2 = o2.getDeployedDate();

                return c1.compareTo(c2);
            }

        });

        return statuses;
    }

    @Override
    public Set<String> getDeployedIds() {
        return donkey.getDeployedChannelIds();
    }

    @Override
    public boolean isDeployed(String channelId) {
        return donkey.getDeployedChannels().containsKey(channelId);
    }

    @Override
    public com.mirth.connect.donkey.server.channel.Channel getDeployedChannel(String channelId) {
        return donkey.getDeployedChannels().get(channelId);
    }

    @Override
    public DispatchResult dispatchRawMessage(String channelId, RawMessage rawMessage, boolean force, boolean canBatch) throws ChannelException, BatchMessageException {
        if (!isDeployed(channelId)) {
            ChannelException e = new ChannelException(true);
            logger.error("Could not find channel to route to: " + channelId, e);
            throw e;
        }

        SourceConnector sourceConnector = donkey.getDeployedChannels().get(channelId).getSourceConnector();

        if (canBatch && sourceConnector.isProcessBatch()) {
            if (rawMessage.isBinary()) {
                throw new BatchMessageException("Batch processing is not supported for binary data.");
            } else {
                BatchRawMessage batchRawMessage = new BatchRawMessage(new BatchMessageReader(rawMessage.getRawData()), rawMessage.getSourceMap());

                ResponseHandler responseHandler = new SimpleResponseHandler();
                sourceConnector.dispatchBatchMessage(batchRawMessage, responseHandler);

                return responseHandler.getDispatchResult();
            }
        } else {
            DispatchResult dispatchResult = null;

            try {
                dispatchResult = sourceConnector.dispatchRawMessage(rawMessage, force);
                dispatchResult.setAttemptedResponse(true);
            } finally {
                sourceConnector.finishDispatch(dispatchResult);
            }

            return dispatchResult;
        }
    }

    protected com.mirth.connect.donkey.server.channel.Channel convertToDonkeyChannel(Channel model) throws Exception {
        String channelId = model.getId();
        ChannelProperties channelProperties = model.getProperties();
        StorageSettings storageSettings = getStorageSettings(channelProperties.getMessageStorageMode(), channelProperties);

        com.mirth.connect.donkey.server.channel.Channel channel = new com.mirth.connect.donkey.server.channel.Channel();

        Map<String, Integer> destinationIdMap = new LinkedHashMap<String, Integer>();

        channel.setChannelId(channelId);
        channel.setLocalChannelId(donkeyChannelController.getLocalChannelId(channelId));
        channel.setServerId(ConfigurationController.getInstance().getServerId());
        channel.setName(model.getName());
        channel.setEnabled(model.isEnabled());
        channel.setRevision(model.getRevision());
        channel.setInitialState(channelProperties.getInitialState());
        channel.setStorageSettings(storageSettings);
        channel.setMetaDataColumns(channelProperties.getMetaDataColumns());
        channel.setAttachmentHandler(createAttachmentHandler(channelId, channelProperties.getAttachmentProperties()));
        channel.setPreProcessor(createPreProcessor(channelId, model.getPreprocessingScript(), destinationIdMap));
        channel.setPostProcessor(createPostProcessor(channelId, model.getPostprocessingScript()));
        channel.setSourceConnector(createSourceConnector(channel, model.getSourceConnector(), storageSettings, destinationIdMap));
        channel.setResponseSelector(new ResponseSelector(channel.getSourceConnector().getInboundDataType()));
        channel.setSourceFilterTransformer(createFilterTransformerExecutor(channelId, model.getSourceConnector(), destinationIdMap));

        ConnectorMessageQueue sourceQueue = new ConnectorMessageQueue();
        sourceQueue.setBufferCapacity(queueBufferSize);
        channel.setSourceQueue(sourceQueue);

        if (model.getSourceConnector().getProperties() instanceof SourceConnectorPropertiesInterface) {
            SourceConnectorProperties sourceConnectorProperties = ((SourceConnectorPropertiesInterface) model.getSourceConnector().getProperties()).getSourceConnectorProperties();
            channel.getResponseSelector().setRespondFromName(sourceConnectorProperties.getResponseVariable());
        }

        if (storageSettings.isEnabled()) {
            BufferedDaoFactory bufferedDaoFactory = new BufferedDaoFactory(donkey.getDaoFactory());
            bufferedDaoFactory.setEncryptData(channelProperties.isEncryptData());

            channel.setDaoFactory(bufferedDaoFactory);
        } else {
            channel.setDaoFactory(new PassthruDaoFactory(new DelayedStatisticsUpdater(donkey.getDaoFactory())));
        }

        DestinationChain chain = createDestinationChain(channel);

        for (Connector connector : model.getDestinationConnectors()) {
            if (connector.isEnabled()) {
                // read 'waitForPrevious' property and add new chains as needed
                // if there are currently no chains, add a new one regardless of 'waitForPrevious'
                if (!connector.isWaitForPrevious() || channel.getDestinationChains().size() == 0) {
                    chain = createDestinationChain(channel);
                    channel.addDestinationChain(chain);
                }

                Integer metaDataId = connector.getMetaDataId();
                destinationIdMap.put(connector.getName(), metaDataId);

                if (metaDataId == null) {
                    metaDataId = model.getNextMetaDataId();
                    model.setNextMetaDataId(metaDataId + 1);
                    connector.setMetaDataId(metaDataId);
                }

                chain.addDestination(connector.getMetaDataId(), createFilterTransformerExecutor(channelId, connector, destinationIdMap), createDestinationConnector(channel, connector, storageSettings, destinationIdMap));
            }
        }

        return channel;
    }

    public static StorageSettings getStorageSettings(MessageStorageMode messageStorageMode, ChannelProperties channelProperties) {
        StorageSettings storageSettings = new StorageSettings();
        storageSettings.setRemoveContentOnCompletion(channelProperties.isRemoveContentOnCompletion());
        storageSettings.setRemoveAttachmentsOnCompletion(channelProperties.isRemoveAttachmentsOnCompletion());
        storageSettings.setStoreAttachments(channelProperties.isStoreAttachments());

        // we assume that all storage settings are enabled by default
        switch (messageStorageMode) {
            case PRODUCTION:
                storageSettings.setStoreProcessedRaw(false);
                storageSettings.setStoreTransformed(false);
                storageSettings.setStoreResponseTransformed(false);
                storageSettings.setStoreProcessedResponse(false);
                break;

            case RAW:
                storageSettings.setMessageRecoveryEnabled(false);
                storageSettings.setDurable(false);
                storageSettings.setStoreMaps(false);
                storageSettings.setStoreResponseMap(false);
                storageSettings.setStoreMergedResponseMap(false);
                storageSettings.setStoreProcessedRaw(false);
                storageSettings.setStoreTransformed(false);
                storageSettings.setStoreSourceEncoded(false);
                storageSettings.setStoreDestinationEncoded(false);
                storageSettings.setStoreSent(false);
                storageSettings.setStoreResponseTransformed(false);
                storageSettings.setStoreProcessedResponse(false);
                storageSettings.setStoreResponse(false);
                storageSettings.setStoreSentResponse(false);
                break;

            case METADATA:
                storageSettings.setMessageRecoveryEnabled(false);
                storageSettings.setDurable(false);
                storageSettings.setRawDurable(false);
                storageSettings.setStoreMaps(false);
                storageSettings.setStoreResponseMap(false);
                storageSettings.setStoreMergedResponseMap(false);
                storageSettings.setStoreRaw(false);
                storageSettings.setStoreProcessedRaw(false);
                storageSettings.setStoreTransformed(false);
                storageSettings.setStoreSourceEncoded(false);
                storageSettings.setStoreDestinationEncoded(false);
                storageSettings.setStoreSent(false);
                storageSettings.setStoreResponseTransformed(false);
                storageSettings.setStoreProcessedResponse(false);
                storageSettings.setStoreResponse(false);
                storageSettings.setStoreSentResponse(false);
                break;

            case DISABLED:
                storageSettings.setEnabled(false);
                storageSettings.setMessageRecoveryEnabled(false);
                storageSettings.setDurable(false);
                storageSettings.setRawDurable(false);
                storageSettings.setStoreCustomMetaData(false);
                storageSettings.setStoreMaps(false);
                storageSettings.setStoreResponseMap(false);
                storageSettings.setStoreMergedResponseMap(false);
                storageSettings.setStoreRaw(false);
                storageSettings.setStoreProcessedRaw(false);
                storageSettings.setStoreTransformed(false);
                storageSettings.setStoreSourceEncoded(false);
                storageSettings.setStoreDestinationEncoded(false);
                storageSettings.setStoreSent(false);
                storageSettings.setStoreResponseTransformed(false);
                storageSettings.setStoreProcessedResponse(false);
                storageSettings.setStoreResponse(false);
                storageSettings.setStoreSentResponse(false);
                break;
        }

        return storageSettings;
    }

    private AttachmentHandler createAttachmentHandler(String channelId, AttachmentHandlerProperties attachmentHandlerProperties) throws Exception {
        AttachmentHandler attachmentHandler = null;

        if (AttachmentHandlerType.fromString(attachmentHandlerProperties.getType()) != AttachmentHandlerType.NONE) {
            Class<?> attachmentHandlerClass = Class.forName(attachmentHandlerProperties.getClassName());

            if (MirthAttachmentHandler.class.isAssignableFrom(attachmentHandlerClass)) {
                attachmentHandler = (MirthAttachmentHandler) attachmentHandlerClass.newInstance();
                attachmentHandler.setProperties(attachmentHandlerProperties);

                if (attachmentHandler instanceof JavaScriptAttachmentHandler) {
                    String scriptId = ScriptController.getScriptId(ScriptController.ATTACHMENT_SCRIPT_KEY, channelId);
                    String attachmentScript = attachmentHandlerProperties.getProperties().get("javascript.script");

                    if (attachmentScript != null) {
                        try {
                            Set<String> scriptOptions = new HashSet<String>();
                            scriptOptions.add("useAttachmentList");
                            JavaScriptUtil.compileAndAddScript(scriptId, attachmentScript, ContextType.CHANNEL_CONTEXT, scriptOptions);
                        } catch (Exception e) {
                            logger.error("Error compiling attachment handler script " + scriptId + ".", e);
                        }
                    }
                }
            } else {
                throw new Exception(attachmentHandlerProperties.getClassName() + " does not extend " + MirthAttachmentHandler.class.getName());
            }
        } else {
            attachmentHandler = new PassthruAttachmentHandler();
        }

        return attachmentHandler;
    }

    private PreProcessor createPreProcessor(String channelId, String preProcessingScript, Map<String, Integer> destinationIdMap) {
        String scriptId = ScriptController.getScriptId(ScriptController.PREPROCESSOR_SCRIPT_KEY, channelId);

        try {
            JavaScriptUtil.compileAndAddScript(scriptId, preProcessingScript, ContextType.CHANNEL_CONTEXT);
        } catch (Exception e) {
            logger.error("Error compiling preprocessor script " + scriptId + ".", e);
        }

        return new JavaScriptPreprocessor(destinationIdMap);
    }

    private PostProcessor createPostProcessor(String channelId, String postProcessingScript) {
        String scriptId = ScriptController.getScriptId(ScriptController.POSTPROCESSOR_SCRIPT_KEY, channelId);

        try {
            JavaScriptUtil.compileAndAddScript(scriptId, postProcessingScript, ContextType.CHANNEL_CONTEXT);
        } catch (Exception e) {
            logger.error("Error compiling postprocessor script " + scriptId + ".", e);
        }

        return new JavaScriptPostprocessor();
    }

    private SourceConnector createSourceConnector(com.mirth.connect.donkey.server.channel.Channel donkeyChannel, Connector model, StorageSettings storageSettings, Map<String, Integer> destinationIdMap) throws Exception {
        ExtensionController extensionController = ControllerFactory.getFactory().createExtensionController();
        ConnectorProperties connectorProperties = model.getProperties();
        ConnectorMetaData connectorMetaData = extensionController.getConnectorMetaData().get(connectorProperties.getName());
        SourceConnector sourceConnector = (SourceConnector) Class.forName(connectorMetaData.getServerClassName()).newInstance();

        setCommonConnectorProperties(donkeyChannel.getChannelId(), sourceConnector, model, destinationIdMap);

        sourceConnector.setMetaDataReplacer(createMetaDataReplacer(model));
        sourceConnector.setChannel(donkeyChannel);

        if (connectorProperties instanceof SourceConnectorPropertiesInterface) {
            SourceConnectorProperties sourceConnectorProperties = ((SourceConnectorPropertiesInterface) connectorProperties).getSourceConnectorProperties();
            sourceConnector.setRespondAfterProcessing(sourceConnectorProperties.isRespondAfterProcessing());

            DataTypeServerPlugin dataTypePlugin = ExtensionController.getInstance().getDataTypePlugins().get(model.getTransformer().getInboundDataType());
            DataTypeProperties dataTypeProperties = model.getTransformer().getInboundProperties();
            SerializerProperties serializerProperties = dataTypeProperties.getSerializerProperties();
            BatchProperties batchProperties = serializerProperties.getBatchProperties();

            if (batchProperties != null && sourceConnectorProperties.isProcessBatch()) {
                BatchAdaptorFactory batchAdaptorFactory = dataTypePlugin.getBatchAdaptorFactory(sourceConnector, serializerProperties);
                batchAdaptorFactory.setUseFirstReponse(sourceConnectorProperties.isFirstResponse());
                sourceConnector.setBatchAdaptorFactory(batchAdaptorFactory);
            }
        } else {
            sourceConnector.setRespondAfterProcessing(true);
        }

        return sourceConnector;
    }

    private FilterTransformerExecutor createFilterTransformerExecutor(String channelId, Connector connector, Map<String, Integer> destinationIdMap) throws Exception {
        boolean runFilterTransformer = false;
        String template = null;
        Transformer transformer = connector.getTransformer();
        Filter filter = connector.getFilter();

        DataType inboundDataType = DataTypeFactory.getDataType(transformer.getInboundDataType(), transformer.getInboundProperties());
        DataType outboundDataType = DataTypeFactory.getDataType(transformer.getOutboundDataType(), transformer.getOutboundProperties());

        // Check the conditions for skipping transformation
        // 1. Script is not empty
        // 2. Data Types are different
        // 3. The data type has properties settings that require a transformation
        // 4. The outbound template is not empty        

        if (!filter.getRules().isEmpty() || !transformer.getSteps().isEmpty() || !transformer.getInboundDataType().equals(transformer.getOutboundDataType())) {
            runFilterTransformer = true;
        }

        // Ask the inbound serializer if it needs to be transformed with serialization
        if (!runFilterTransformer) {
            runFilterTransformer = inboundDataType.getSerializer().isSerializationRequired(true);
        }

        // Ask the outbound serializier if it needs to be transformed with serialization
        if (!runFilterTransformer) {
            runFilterTransformer = outboundDataType.getSerializer().isSerializationRequired(false);
        }

        // Serialize the outbound template if needed
        if (StringUtils.isNotBlank(transformer.getOutboundTemplate())) {
            DataTypeServerPlugin outboundServerPlugin = ExtensionController.getInstance().getDataTypePlugins().get(transformer.getOutboundDataType());
            XmlSerializer serializer = outboundServerPlugin.getSerializer(transformer.getOutboundProperties().getSerializerProperties());

            if (outboundServerPlugin.isBinary() || outboundServerPlugin.getSerializationType() == SerializationType.RAW) {
                template = transformer.getOutboundTemplate();
            } else {
                try {
                    template = serializer.toXML(transformer.getOutboundTemplate());
                } catch (XmlSerializerException e) {
                    throw new XmlSerializerException("Error serializing transformer outbound template for connector \"" + connector.getName() + "\": " + e.getMessage(), e.getCause(), e.getFormattedError());
                }
            }

            runFilterTransformer = true;
        }

        FilterTransformerExecutor filterTransformerExecutor = new FilterTransformerExecutor(inboundDataType, outboundDataType);

        if (runFilterTransformer) {
            String script = JavaScriptBuilder.generateFilterTransformerScript(filter, transformer);
            filterTransformerExecutor.setFilterTransformer(new JavaScriptFilterTransformer(channelId, connector.getName(), script, template, destinationIdMap));
        }

        return filterTransformerExecutor;
    }

    private ResponseTransformerExecutor createResponseTransformerExecutor(String channelId, Connector connector, Map<String, Integer> destinationIdMap) throws Exception {
        boolean runResponseTransformer = false;
        String template = null;
        Transformer transformer = connector.getResponseTransformer();

        DataType inboundDataType = DataTypeFactory.getDataType(transformer.getInboundDataType(), transformer.getInboundProperties());
        DataType outboundDataType = DataTypeFactory.getDataType(transformer.getOutboundDataType(), transformer.getOutboundProperties());

        // Check the conditions for skipping transformation
        // 1. Script is not empty
        // 2. Data Types are different
        // 3. The data type has properties settings that require a transformation
        // 4. The outbound template is not empty        

        if (!transformer.getSteps().isEmpty() || !transformer.getInboundDataType().equals(transformer.getOutboundDataType())) {
            runResponseTransformer = true;
        }

        // Ask the inbound serializer if it needs to be transformed with serialization
        if (!runResponseTransformer) {
            runResponseTransformer = inboundDataType.getSerializer().isSerializationRequired(true);
        }

        // Ask the outbound serializier if it needs to be transformed with serialization
        if (!runResponseTransformer) {
            runResponseTransformer = outboundDataType.getSerializer().isSerializationRequired(false);
        }

        // Serialize the outbound template if needed
        if (StringUtils.isNotBlank(transformer.getOutboundTemplate())) {
            DataTypeServerPlugin outboundServerPlugin = ExtensionController.getInstance().getDataTypePlugins().get(transformer.getOutboundDataType());
            XmlSerializer serializer = outboundServerPlugin.getSerializer(transformer.getOutboundProperties().getSerializerProperties());

            if (outboundServerPlugin.isBinary() || outboundServerPlugin.getSerializationType() == SerializationType.RAW) {
                template = transformer.getOutboundTemplate();
            } else {
                try {
                    template = serializer.toXML(transformer.getOutboundTemplate());
                } catch (XmlSerializerException e) {
                    throw new XmlSerializerException("Error serializing response transformer outbound template for connector \"" + connector.getName() + "\": " + e.getMessage(), e.getCause(), e.getFormattedError());
                }
            }

            runResponseTransformer = true;
        }

        ResponseTransformerExecutor responseTransformerExecutor = new ResponseTransformerExecutor(inboundDataType, outboundDataType);

        if (runResponseTransformer) {
            String script = JavaScriptBuilder.generateResponseTransformerScript(transformer);
            responseTransformerExecutor.setResponseTransformer(new JavaScriptResponseTransformer(channelId, connector.getName(), script, template, destinationIdMap));
        }

        return responseTransformerExecutor;
    }

    private DestinationChain createDestinationChain(com.mirth.connect.donkey.server.channel.Channel donkeyChannel) {
        DestinationChain chain = new DestinationChain();
        chain.setChannelId(donkeyChannel.getChannelId());
        chain.setMetaDataReplacer(donkeyChannel.getSourceConnector().getMetaDataReplacer());
        chain.setMetaDataColumns(donkeyChannel.getMetaDataColumns());

        return chain;
    }

    private DestinationConnector createDestinationConnector(com.mirth.connect.donkey.server.channel.Channel donkeyChannel, Connector model, StorageSettings storageSettings, Map<String, Integer> destinationIdMap) throws Exception {
        ExtensionController extensionController = ControllerFactory.getFactory().createExtensionController();
        ConnectorProperties connectorProperties = model.getProperties();
        ConnectorMetaData connectorMetaData = extensionController.getConnectorMetaData().get(connectorProperties.getName());
        String className = connectorMetaData.getServerClassName();
        DestinationConnector destinationConnector = (DestinationConnector) Class.forName(className).newInstance();

        setCommonConnectorProperties(donkeyChannel.getChannelId(), destinationConnector, model, destinationIdMap);
        destinationConnector.setChannel(donkeyChannel);

        destinationConnector.setDestinationName(model.getName());

        // Create the response validator
        DataTypeServerPlugin dataTypePlugin = ExtensionController.getInstance().getDataTypePlugins().get(model.getResponseTransformer().getInboundDataType());
        DataTypeProperties dataTypeProperties = model.getResponseTransformer().getInboundProperties();
        SerializerProperties serializerProperties = dataTypeProperties.getSerializerProperties();
        ResponseValidator responseValidator = dataTypePlugin.getResponseValidator(serializerProperties.getSerializationProperties(), dataTypeProperties.getResponseValidationProperties());
        if (responseValidator == null) {
            responseValidator = new DefaultResponseValidator();
        }
        destinationConnector.setResponseValidator(responseValidator);
        destinationConnector.setResponseTransformerExecutor(createResponseTransformerExecutor(donkeyChannel.getChannelId(), model, destinationIdMap));

        ConnectorMessageQueue queue = new ConnectorMessageQueue();
        queue.setBufferCapacity(queueBufferSize);
        queue.setRotate(destinationConnector.isQueueRotate());
        destinationConnector.setQueue(queue);

        return destinationConnector;
    }

    private void setCommonConnectorProperties(String channelId, com.mirth.connect.donkey.server.channel.Connector connector, Connector model, Map<String, Integer> destinationIdMap) {
        connector.setChannelId(channelId);
        connector.setMetaDataId(model.getMetaDataId());
        connector.setConnectorProperties(model.getProperties());
        connector.setDestinationIdMap(destinationIdMap);

        Transformer transformerModel = model.getTransformer();

        connector.setInboundDataType(DataTypeFactory.getDataType(transformerModel.getInboundDataType(), transformerModel.getInboundProperties()));
        connector.setOutboundDataType(DataTypeFactory.getDataType(transformerModel.getOutboundDataType(), transformerModel.getOutboundProperties()));
    }

    private MetaDataReplacer createMetaDataReplacer(Connector connector) {
        // TODO: Extract this from the Connector model based on the inbound data type
        return new MirthMetaDataReplacer();
    }

    private void clearGlobalChannelMap(Channel channel) {
        if (channel.getProperties().isClearGlobalChannelMap()) {
            logger.debug("clearing global channel map for channel: " + channel.getId());
            GlobalChannelVariableStoreFactory.getInstance().get(channel.getId()).clear();
            GlobalChannelVariableStoreFactory.getInstance().get(channel.getId()).clearSync();
        }
    }

    private void clearGlobalMap() {
        try {
            if (configurationController.getServerSettings().getClearGlobalMap() == null || configurationController.getServerSettings().getClearGlobalMap()) {
                logger.debug("clearing global map");
                GlobalVariableStore globalVariableStore = GlobalVariableStore.getInstance();
                globalVariableStore.clear();
                globalVariableStore.clearSync();
            }
        } catch (ControllerException e) {
            logger.error("Could not clear the global map.", e);
        }
    }

    protected void executeGlobalDeployScript() {
        try {
            scriptController.executeGlobalDeployScript();
        } catch (Exception e) {
            logger.error("Error executing global deploy script.", e);
        }
    }

    protected void executeGlobalUndeployScript() {
        try {
            scriptController.executeGlobalUndeployScript();
        } catch (Exception e) {
            logger.error("Error executing global undeploy script.", e);
        }
    }

    protected void executeChannelPluginOnDeploy(ServerEventContext context) {
        // Execute the overall channel plugin deploy hook
        for (ChannelPlugin channelPlugin : extensionController.getChannelPlugins().values()) {
            channelPlugin.deploy(context);
        }
    }

    protected void executeChannelPluginOnUndeploy(ServerEventContext context) {
        // Execute the overall channel plugin undeploy hook
        for (ChannelPlugin channelPlugin : extensionController.getChannelPlugins().values()) {
            channelPlugin.undeploy(context);
        }
    }

    private synchronized ExecutorService getEngineExecutor(String channelId, boolean replace) {
        ExecutorService engineExecutor = engineExecutors.get(channelId);

        if (engineExecutor == null || replace) {
            engineExecutor = new ThreadPoolExecutor(0, 1, 10L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
            engineExecutors.put(channelId, engineExecutor);
        }

        return engineExecutor;
    }

    private List<ChannelTask> buildChannelStatusTasks(Set<String> channelIds, StatusTask task) {
        List<ChannelTask> tasks = new ArrayList<ChannelTask>();

        for (String channelId : channelIds) {
            tasks.add(new ChannelStatusTask(channelId, task));
        }

        return tasks;
    }

    private List<ChannelTask> buildConnectorStatusTasks(Map<String, List<Integer>> connectorInfo, StatusTask task) {
        List<ChannelTask> tasks = new ArrayList<ChannelTask>();

        for (Entry<String, List<Integer>> entry : connectorInfo.entrySet()) {
            String channelId = entry.getKey();
            List<Integer> metaDataIds = entry.getValue();

            for (Integer metaDataId : metaDataIds) {
                tasks.add(new ConnectorStatusTask(channelId, metaDataId, task));
            }
        }

        return tasks;
    }

    private void waitForTasks(List<ChannelFuture> futures) {
        for (ChannelFuture future : futures) {
            try {
                future.get();
            } catch (InterruptedException e) {
                logger.error(ExceptionUtils.getStackTrace(e));
            } catch (ExecutionException e) {
                logger.error(ExceptionUtils.getStackTrace(e.getCause()));
            } catch (CancellationException e) {
                logger.error("Task cancelled because the channel " + future.getChannelId() + " was halted or removed.", e);
            }
        }
    }

    private synchronized List<ChannelFuture> submitTasks(List<ChannelTask> tasks) {
        List<ChannelFuture> futures = new ArrayList<ChannelFuture>();
        for (ChannelTask task : tasks) {
            ExecutorService engineExecutor = getEngineExecutor(task.getChannelId(), false);

            try {
                futures.add(new ChannelFuture(task.getChannelId(), engineExecutor.submit(task)));
            } catch (RejectedExecutionException e) {
                /*
                 * This can happen if a channel was halted, in which case we don't want to perform
                 * whatever task this was anyway.
                 */
            }
        }

        return futures;
    }

    private synchronized List<ChannelFuture> submitHaltTasks(Set<String> channelIds) {
        List<ChannelFuture> futures = new ArrayList<ChannelFuture>();

        for (String channelId : channelIds) {
            ExecutorService engineExecutor = getEngineExecutor(channelId, false);
            // Shutdown the old executor to cancel existing tasks and prevent new tasks from being submitted to it.
            List<Runnable> tasks = engineExecutor.shutdownNow();
            // Cancel any tasks that had not yet started. Otherwise those tasks would be blocked at future.get() indefinitely.
            for (Runnable task : tasks) {
                ((Future<?>) task).cancel(true);
            }

            /*
             * Create a new executor to submit the halt task to. Since all the submit methods are
             * synchronized, it is not possible for any other tasks for this channel to occur before
             * the halt task.
             */
            engineExecutor = getEngineExecutor(channelId, true);
            futures.add(new ChannelFuture(channelId, engineExecutor.submit(new HaltTask(channelId))));
        }

        return futures;
    }

    private class DeployTask extends ChannelTask {

        private ServerEventContext context;

        public DeployTask(String channelId, ServerEventContext context) {
            super(channelId);
            this.context = context;
        }

        @Override
        public Void call() throws Exception {
            Channel channel = channelController.getChannelById(channelId);

            if (channel == null || !channel.isEnabled() || isDeployed(channelId)) {
                return null;
            }

            com.mirth.connect.donkey.server.channel.Channel donkeyChannel = null;

            try {
                donkeyChannel = convertToDonkeyChannel(channel);
            } catch (Exception e) {
                throw new DeployException(e.getMessage(), e);
            }

            try {
                scriptController.compileChannelScripts(channel);
            } catch (ScriptCompileException e) {
                throw new DeployException("Failed to deploy channel " + channelId + ".", e);
            }

            clearGlobalChannelMap(channel);

            try {
                scriptController.executeChannelDeployScript(channelId);
            } catch (Exception e) {
                Throwable t = e;
                if (e instanceof JavaScriptExecutorException) {
                    t = e.getCause();
                }

                eventController.dispatchEvent(new ErrorEvent(channel.getId(), null, ErrorEventType.DEPLOY_SCRIPT, null, null, "Error running channel deploy script", t));
                throw new DeployException("Failed to deploy channel " + channelId + ".", e);
            }

            channelController.putDeployedChannelInCache(channel);

            // Execute the individual channel plugin deploy hook
            for (ChannelPlugin channelPlugin : extensionController.getChannelPlugins().values()) {
                channelPlugin.deploy(channel, context);
            }

            donkeyChannel.setRevision(channel.getRevision());

            try {
                donkey.deployChannel(donkeyChannel);
            } catch (DeployException e) {
                // Remove the channel from the deployed channel cache if an exception occurred on deploy.
                channelController.removeDeployedChannelFromCache(channelId);
                // Remove the channel scripts from the script cache if an exception occurred on deploy.
                scriptController.removeChannelScriptsFromCache(channelId);

                throw e;
            }

            return null;
        }
    }

    private class UndeployTask extends ChannelTask {

        private ServerEventContext context;

        public UndeployTask(String channelId, ServerEventContext context) {
            super(channelId);
            this.context = context;
        }

        @Override
        public Void call() throws Exception {
            // Get a reference to the deployed channel for later
            com.mirth.connect.donkey.server.channel.Channel channel = getDeployedChannel(channelId);

            if (channel != null) {
                donkey.undeployChannel(channel);

                // Remove connector scripts
                if (channel.getSourceFilterTransformer().getFilterTransformer() != null) {
                    channel.getSourceFilterTransformer().getFilterTransformer().dispose();
                }

                for (DestinationChain chain : channel.getDestinationChains()) {
                    for (Integer metaDataId : chain.getDestinationConnectors().keySet()) {
                        if (chain.getFilterTransformerExecutors().get(metaDataId).getFilterTransformer() != null) {
                            chain.getFilterTransformerExecutors().get(metaDataId).getFilterTransformer().dispose();
                        }
                        if (chain.getDestinationConnectors().get(metaDataId).getResponseTransformerExecutor().getResponseTransformer() != null) {
                            chain.getDestinationConnectors().get(metaDataId).getResponseTransformerExecutor().getResponseTransformer().dispose();
                        }
                    }
                }

                // Execute the individual channel plugin undeploy hook
                for (ChannelPlugin channelPlugin : extensionController.getChannelPlugins().values()) {
                    channelPlugin.undeploy(channelId, context);
                }

                // Execute channel undeploy script
                try {
                    scriptController.executeChannelUndeployScript(channelId);
                } catch (Exception e) {
                    Throwable t = e;
                    if (e instanceof JavaScriptExecutorException) {
                        t = e.getCause();
                    }

                    eventController.dispatchEvent(new ErrorEvent(channelId, null, ErrorEventType.UNDEPLOY_SCRIPT, null, null, "Error running channel undeploy script", t));
                    logger.error("Error executing undeploy script for channel " + channelId + ".", e);
                }

                // Remove channel scripts
                scriptController.removeChannelScriptsFromCache(channelId);

                channelController.removeDeployedChannelFromCache(channelId);
            }

            return null;
        }
    }

    private class ChannelStatusTask extends ChannelTask {

        private StatusTask task;

        public ChannelStatusTask(String channelId, StatusTask task) {
            super(channelId);
            this.task = task;
        }

        @Override
        public Void call() throws Exception {
            if (task == StatusTask.START) {
                donkey.startChannel(channelId);
            } else if (task == StatusTask.STOP) {
                donkey.stopChannel(channelId);
            } else if (task == StatusTask.PAUSE) {
                donkey.pauseChannel(channelId);
            } else if (task == StatusTask.RESUME) {
                donkey.resumeChannel(channelId);
            }

            return null;
        }
    }

    private class ConnectorStatusTask extends ChannelTask {

        private Integer metaDataId;
        private StatusTask task;

        public ConnectorStatusTask(String channelId, Integer metaDataId, StatusTask task) {
            super(channelId);
            this.metaDataId = metaDataId;
            this.task = task;
        }

        @Override
        public Void call() throws Exception {
            if (task == StatusTask.START) {
                donkey.startConnector(channelId, metaDataId);
            } else if (task == StatusTask.STOP) {
                donkey.stopConnector(channelId, metaDataId);
            }

            return null;
        }
    }

    private class HaltTask extends ChannelTask {

        public HaltTask(String channelId) {
            super(channelId);
        }

        @Override
        public Void call() throws Exception {
            donkey.haltChannel(channelId);

            return null;
        }
    }

    private class RemoveTask extends ChannelTask {

        private Channel channel;
        private ServerEventContext context;

        public RemoveTask(Channel channel, ServerEventContext context) {
            super(channel.getId());
            this.channel = channel;
            this.context = context;
        }

        @Override
        public Void call() throws Exception {
            channelController.removeChannel(channel, context);

            synchronized (DonkeyEngineController.this) {
                ExecutorService engineExecutor = getEngineExecutor(channelId, false);
                // Cancel any tasks that had not yet started. Otherwise those tasks would be blocked at future.get() indefinitely.
                List<Runnable> tasks = engineExecutor.shutdownNow();
                for (Runnable task : tasks) {
                    ((Future<?>) task).cancel(true);
                }

                // Remove the executor since it has been shutdown. If another task comes in for this channel Id, a new executor will be created.
                engineExecutors.remove(channelId);
            }

            return null;
        }
    }

    private class RemoveMessagesTask extends ChannelTask {

        private Map<Long, MessageSearchResult> results;

        public RemoveMessagesTask(String channelId, Map<Long, MessageSearchResult> results) {
            super(channelId);
            this.results = results;
        }

        @Override
        public Void call() throws Exception {
            Map<Long, Set<Integer>> messages = new HashMap<Long, Set<Integer>>();

            // For each message that was retrieved
            for (Entry<Long, MessageSearchResult> entry : results.entrySet()) {
                Long messageId = entry.getKey();
                MessageSearchResult result = entry.getValue();
                Set<Integer> metaDataIds = result.getMetaDataIdSet();
                boolean processed = result.isProcessed();

                com.mirth.connect.donkey.server.channel.Channel channel = getDeployedChannel(channelId);
                // Allow unprocessed messages to be deleted only if the channel is undeployed or stopped.
                if (channel != null && (channel.getCurrentState() == DeployedState.STOPPED || processed)) {
                    if (metaDataIds.contains(0)) {
                        // Delete the entire message if the source connector message is to be deleted
                        messages.put(messageId, null);
                    } else {
                        // Otherwise only deleted the destination connector message
                        messages.put(messageId, metaDataIds);
                    }
                }
            }

            com.mirth.connect.donkey.server.channel.Channel.DELETE_PERMIT.acquire();

            try {
                com.mirth.connect.donkey.server.controllers.MessageController.getInstance().deleteMessages(channelId, messages);
            } finally {
                com.mirth.connect.donkey.server.channel.Channel.DELETE_PERMIT.release();
            }

            return null;
        }
    }

    private class RemoveAllMessagesTask extends ChannelTask {

        private boolean force;
        private boolean clearStatistics;

        public RemoveAllMessagesTask(String channelId, boolean force, boolean clearStatistics) {
            super(channelId);
            this.force = force;
            this.clearStatistics = clearStatistics;
        }

        @Override
        public Void call() throws Exception {
            donkey.removeAllMessages(channelId, force, clearStatistics);

            return null;
        }
    }

    private abstract class ChannelTask implements Callable<Void> {

        protected String channelId;

        public ChannelTask(String channelId) {
            this.channelId = channelId;
        }

        public String getChannelId() {
            return channelId;
        }
    }

    private class ChannelFuture {

        private String channelId;
        private Future<?> delegate;

        public ChannelFuture(String channelId, Future<?> delegate) {
            this.channelId = channelId;
            this.delegate = delegate;
        }

        public String getChannelId() {
            return channelId;
        }

        public Object get() throws InterruptedException, ExecutionException {
            return delegate.get();
        }
    }
}

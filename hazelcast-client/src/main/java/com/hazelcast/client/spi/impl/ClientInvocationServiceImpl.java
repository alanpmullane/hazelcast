/*
 * Copyright (c) 2008-2013, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.client.spi.impl;

import com.hazelcast.client.ClientRequest;
import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.connection.ClientConnectionManager;
import com.hazelcast.client.connection.nio.ClientConnection;
import com.hazelcast.client.spi.ClientInvocationService;
import com.hazelcast.client.spi.EventHandler;
import com.hazelcast.core.ICompletableFuture;
import com.hazelcast.nio.Address;
import com.hazelcast.nio.serialization.Data;
import com.hazelcast.nio.serialization.DataAdapter;
import com.hazelcast.nio.serialization.SerializationService;
import com.hazelcast.spi.exception.TargetNotMemberException;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;

/**
 * @author mdogan 5/16/13
 */
public final class ClientInvocationServiceImpl implements ClientInvocationService {

    private final HazelcastClient client;
    private final ClientConnectionManager connectionManager;

    private final ConcurrentMap<String, Integer> registrationMap = new ConcurrentHashMap<String, Integer>();
    private final ConcurrentMap<String, String> registrationAliasMap = new ConcurrentHashMap<String, String>();

    public ClientInvocationServiceImpl(HazelcastClient client) {
        this.client = client;
        this.connectionManager = client.getConnectionManager();
    }

    public <T> ICompletableFuture<T> invokeOnRandomTarget(ClientRequest request) throws Exception {
        return send(request);
    }

    public <T> ICompletableFuture<T> invokeOnTarget(ClientRequest request, Address target) throws Exception {
        return send(request, target);
    }

    public <T> ICompletableFuture<T> invokeOnKeyOwner(ClientRequest request, Object key) throws Exception {
        ClientPartitionServiceImpl partitionService = (ClientPartitionServiceImpl) client.getClientPartitionService();
        final Address owner = partitionService.getPartitionOwner(partitionService.getPartitionId(key));
        if (owner != null) {
            return invokeOnTarget(request, owner);
        }
        return invokeOnRandomTarget(request);
    }

    public <T> ICompletableFuture<T> invokeOnRandomTarget(ClientRequest request, EventHandler handler) throws Exception {
        return sendAndHandle(request, handler);
    }

    public <T> ICompletableFuture<T> invokeOnTarget(ClientRequest request, Address target, EventHandler handler) throws Exception {
        return sendAndHandle(request, target, handler);
    }

    public <T> ICompletableFuture<T> invokeOnKeyOwner(ClientRequest request, Object key, EventHandler handler) throws Exception {
        ClientPartitionServiceImpl partitionService = (ClientPartitionServiceImpl) client.getClientPartitionService();
        final Address owner = partitionService.getPartitionOwner(partitionService.getPartitionId(key));
        if (owner != null) {
            return invokeOnTarget(request, owner, handler);
        }
        return invokeOnRandomTarget(request, handler);
    }

    // NIO public

    public ICompletableFuture send(ClientRequest request, ClientConnection connection) throws IOException {
        return doSend(request, connection, null);
    }

    public Future reSend(ClientCallFuture future) throws IOException {
        final ClientConnection connection = connectionManager.tryToConnect(null);
        _send(future, connection);
        return future;
    }

    public void registerListener(String uuid, Integer callId) {
        registrationAliasMap.put(uuid, uuid);
        registrationMap.put(uuid, callId);
    }

    public void reRegisterListener(String uuid, String alias, Integer callId) {
        final String oldAlias = registrationAliasMap.put(uuid, alias);
        if (oldAlias != null) {
            registrationMap.remove(oldAlias);
            registrationMap.put(alias, callId);
        }
    }

    public boolean isRedoOperation() {
        return client.getClientConfig().isRedoOperation();
    }

    public boolean deRegisterListener(String alias) {
        final String uuid = registrationAliasMap.remove(alias);
        if (uuid != null) {
            final Integer callId = registrationMap.remove(alias);
            return connectionManager.removeEventHandler(callId);
        }
        return false;
    }


    //NIO private

    private ICompletableFuture send(ClientRequest request) throws IOException {
        final ClientConnection connection = connectionManager.tryToConnect(null);
        return doSend(request, connection, null);
    }

    private ICompletableFuture send(ClientRequest request, Address target) throws IOException {
        final ClientConnection connection = connectionManager.tryToConnect(target);
        return doSend(request, connection, null);
    }

    private ICompletableFuture sendAndHandle(ClientRequest request, EventHandler handler) throws IOException {
        final ClientConnection connection = connectionManager.tryToConnect(null);
        return doSend(request, connection, handler);
    }

    private ICompletableFuture sendAndHandle(ClientRequest request, Address target, EventHandler handler) throws IOException {
        final ClientConnection connection = connectionManager.tryToConnect(target);
        return doSend(request, connection, handler);
    }

    private ICompletableFuture doSend(ClientRequest request, ClientConnection connection, EventHandler handler) {
        final ClientCallFuture future = new ClientCallFuture(client, request, handler);
        _send(future, connection);
        return future;
    }

    private void _send(ClientCallFuture future, ClientConnection connection) {
        connection.registerCallId(future);
        future.setConnection(connection);
        final SerializationService ss = client.getSerializationService();
        final Data data = ss.toData(future.getRequest());
        if (!connection.write(new DataAdapter(data))) {
            future.notify(new TargetNotMemberException("Address : " + connection.getRemoteEndpoint()));
            final int callId = future.getRequest().getCallId();
            connection.deRegisterCallId(callId);
            connection.deRegisterEventHandler(callId);
        }
    }

}

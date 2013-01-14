/*
 * Copyright (c) 2008-2012, Hazelcast, Inc. All Rights Reserved.
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

package com.hazelcast.map.client;

import com.hazelcast.instance.Node;
import com.hazelcast.map.MapService;
import com.hazelcast.map.proxy.DataMapProxy;
import com.hazelcast.nio.Protocol;
import com.hazelcast.nio.serialization.Data;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;

public class MapGetAllHandler extends MapCommandHandler {
    public MapGetAllHandler(MapService mapService) {
        super(mapService);
    }

    @Override
    public Protocol processCall(Node node, Protocol protocol) {
        String name = protocol.args[0];
        int size = protocol.hasBuffer() ? protocol.buffers.length : 0;
        Set<Object> set = new HashSet<Object>();
        for (int i = 0; i < size; i++) {
            set.add(binaryToData(protocol.buffers[i].array()));
        }
        DataMapProxy dataMapProxy = (DataMapProxy) mapService.createClientProxy(name);
        ByteBuffer[] buffers = new ByteBuffer[size * 2];
        int i = 0;
        for (Object k : set) {
            buffers[i++] = ByteBuffer.wrap(((Data) k).buffer);
            Data v = dataMapProxy.get(k);
            if (v == null) {
                buffers[i++] = ByteBuffer.wrap(new byte[0]);
            } else {
                buffers[i++] = ByteBuffer.wrap(v.buffer);
            }
        }
        return protocol.success(buffers);
    }

    protected Protocol processMapOp(Protocol protocol, DataMapProxy dataMapProxy, Data data, Data data1) {
        return null;
    }
}


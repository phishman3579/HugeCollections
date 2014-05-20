/*
 * Copyright 2014 Higher Frequency Trading
 * <p/>
 * http://www.higherfrequencytrading.com
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.openhft.chronicle.sandbox.map.shared;

import net.openhft.collections.SharedHashMap;
import net.openhft.collections.VanillaSharedReplicatedHashMap;
import net.openhft.collections.VanillaSharedReplicatedHashMapBuilder;
import net.openhft.collections.map.replicators.ClientTcpSocketReplicator;
import net.openhft.collections.map.replicators.ServerTcpSocketReplicator;
import net.openhft.collections.map.replicators.SocketChannelEntryReader;
import net.openhft.collections.map.replicators.SocketChannelEntryWriter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.Closeable;
import java.io.IOException;
import java.util.TreeMap;

import static net.openhft.chronicle.sandbox.map.shared.Builder.getPersistenceFile;
import static net.openhft.collections.map.replicators.ClientTcpSocketReplicator.ClientPort;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Test VanillaSharedReplicatedHashMap where the Replicated is over a TCP Socket, but with 4 nodes
 *
 * @author Rob Austin.
 */
public class TCPSocketReplication4WayMapTest {


    private SharedHashMap<Integer, CharSequence> map1;
    private SharedHashMap<Integer, CharSequence> map2;
    private SharedHashMap<Integer, CharSequence> map3;
    private SharedHashMap<Integer, CharSequence> map4;


    static VanillaSharedReplicatedHashMap<Integer, CharSequence> newSocketShmIntString(
            final byte identifier,
            final int serverPort,
            ClientPort... clientSocketChannelProviderMaps) throws IOException {

        final VanillaSharedReplicatedHashMapBuilder builder =
                new VanillaSharedReplicatedHashMapBuilder()
                        .entries(1000)
                        .identifier(identifier);

        final VanillaSharedReplicatedHashMap<Integer, CharSequence> result =
                builder.create(getPersistenceFile(), Integer.class, CharSequence.class);

        final int adjustedEntrySize = result.maxEntrySize();
        final short maxNumberOfEntriesPerChunk = ServerTcpSocketReplicator.toMaxNumberOfEntriesPerChunk(1024 * 8, adjustedEntrySize);

        for (ClientPort clientSocketChannelProvider : clientSocketChannelProviderMaps) {
            final SocketChannelEntryWriter socketChannelEntryWriter0 = new SocketChannelEntryWriter(adjustedEntrySize, maxNumberOfEntriesPerChunk, result);
            final SocketChannelEntryReader socketChannelEntryReader = new SocketChannelEntryReader(builder.entrySize(), result);
            ClientTcpSocketReplicator clientTcpSocketReplicator = new ClientTcpSocketReplicator(clientSocketChannelProvider, socketChannelEntryReader, socketChannelEntryWriter0, result);
            result.addCloseable(clientTcpSocketReplicator);
        }

        final SocketChannelEntryWriter socketChannelEntryWriter = new SocketChannelEntryWriter(adjustedEntrySize, maxNumberOfEntriesPerChunk, result);

        ServerTcpSocketReplicator serverTcpSocketReplicator = new ServerTcpSocketReplicator(
                result,
                result,
                serverPort, socketChannelEntryWriter);

        result.addCloseable(serverTcpSocketReplicator);

        return result;
    }


    @Before
    public void setup() throws IOException {
        map1 = newSocketShmIntString((byte) 1, 8076, new ClientPort(8077, "localhost"), new ClientPort(8078, "localhost"), new ClientPort(8079, "localhost"));
        map2 = newSocketShmIntString((byte) 2, 8077, new ClientPort(8078, "localhost"), new ClientPort(8079, "localhost"), new ClientPort(8076, "localhost"));
        map3 = newSocketShmIntString((byte) 3, 8078, new ClientPort(8079, "localhost"), new ClientPort(8077, "localhost"), new ClientPort(8076, "localhost"));
        map4 = newSocketShmIntString((byte) 4, 8079, new ClientPort(8078, "localhost"), new ClientPort(8077, "localhost"), new ClientPort(8076, "localhost"));
    }

    @After
    public void tearDown() {

        // todo fix close, it not blocking ( in other-words we should wait till everything is closed before running the next test)

        for (final Closeable closeable : new Closeable[]{map1, map2, map3, map4}) {
            try {
                closeable.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    @Test
    public void test() throws IOException, InterruptedException {

        map1.put(1, "EXAMPLE-1");
        map2.put(2, "EXAMPLE-2");
        map3.put(3, "EXAMPLE-1");
        map3.remove(2);
        map3.put(3, "EXAMPLE-5");
        map4.put(4, "EXAMPLE-5");

        // allow time for the recompilation to resolve
        waitTillEqual(1000);

        assertEquals("map2", new TreeMap(map1), new TreeMap(map2));
        assertEquals("map3", new TreeMap(map1), new TreeMap(map3));
        assertEquals("map4", new TreeMap(map1), new TreeMap(map4));
        assertTrue("map2.empty", !map2.isEmpty());

    }


    /**
     * waits until map1 and map2 show the same value
     *
     * @param timeOutMs timeout in milliseconds
     * @throws InterruptedException
     */

    private void waitTillEqual(final int timeOutMs) throws InterruptedException {
        int t = 0;
        for (; t < timeOutMs; t++) {
            if (new TreeMap(map1).equals(new TreeMap(map2)) &&
                    new TreeMap(map1).equals(new TreeMap(map3)) &&
                    new TreeMap(map1).equals(new TreeMap(map4)))
                break;
            Thread.sleep(1);
        }

    }

}



/*
 * Copyright 2008-present MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mongodb.ce.PoolStatsListener;

import com.mongodb.management.*;
import com.mongodb.ServerAddress;
import com.mongodb.connection.ConnectionPoolSettings;
import com.mongodb.event.ConnectionCheckedInEvent;
import com.mongodb.event.ConnectionCheckedOutEvent;
import com.mongodb.event.ConnectionClosedEvent;
import com.mongodb.event.ConnectionCreatedEvent;
import com.mongodb.event.ConnectionPoolListener;
import com.mongodb.event.ConnectionPoolCreatedEvent;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * An MBean implementation for connection pool statistics.
 */
public class PoolStatsListener implements ConnectionPoolListener, ConnectionPoolStatisticsMBean {
    public ServerAddress serverAddress;
    public ConnectionPoolSettings settings;
    public AtomicInteger size = new AtomicInteger();
    public AtomicInteger checkedOutCount = new AtomicInteger();

    @Override
    public String getHost() {
        return serverAddress.getHost();
    }

    @Override
    public int getPort() {
        return serverAddress.getPort();
    }

    @Override
    public int getMinSize() {
        return settings.getMinSize();
    }

    @Override
    public int getMaxSize() {
        return settings.getMaxSize();
    }

    @Override
    public int getSize() {
        return size.get();
    }

    @Override
    public int getCheckedOutCount() {
        return checkedOutCount.get();
    }

    @Override
    public void connectionCheckedOut(ConnectionCheckedOutEvent event) {
        checkedOutCount.incrementAndGet();
    }

    @Override
    public void connectionCheckedIn(ConnectionCheckedInEvent event) {
        checkedOutCount.decrementAndGet();
    }

    @Override
    public void connectionCreated(ConnectionCreatedEvent event) {
        size.incrementAndGet();
    }

    @Override
    public void connectionClosed(ConnectionClosedEvent event) {
        size.decrementAndGet();
    }

    @Override
    public void connectionPoolCreated(ConnectionPoolCreatedEvent event) {
        this.serverAddress = event.getServerId().getAddress();
        this.settings = event.getSettings();
    }

}

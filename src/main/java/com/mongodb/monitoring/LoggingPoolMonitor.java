package com.mongodb.monitoring;

import com.mongodb.ServerAddress;
import com.mongodb.event.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A MongoDB connection pool monitor that logs pool events with embedded metrics.
 * No MDC or log pattern configuration needed - just add this listener and you get
 * comprehensive pool logging automatically.
 *
 * Pool metrics are displayed as [size/checkedOut/maxSize] where:
 * - size: Total connections in the pool
 * - checkedOut: Connections currently in use
 * - maxSize: Maximum allowed connections
 *
 * Supports multiple pools (replica sets) with per-pool accounting.
 *
 * Usage:
 * MongoClientSettings.builder()
 *     .applyToConnectionPoolSettings(builder ->
 *         builder.addConnectionPoolListener(new LoggingPoolMonitor()))
 *     .build();
 */
public class LoggingPoolMonitor implements ConnectionPoolListener {
    private static final Logger logger = LoggerFactory.getLogger(LoggingPoolMonitor.class);

    // Per-pool metrics tracking
    private static class PoolMetrics {
        final AtomicInteger size = new AtomicInteger(0);
        final AtomicInteger checkedOut = new AtomicInteger(0);
        volatile int maxSize = 0;

        String format() {
            return String.format("[%d/%d/%d]", size.get(), checkedOut.get(), maxSize);
        }
    }

    private final ConcurrentHashMap<ServerAddress, PoolMetrics> poolMetrics = new ConcurrentHashMap<>();

    // Logging levels
    private final boolean logConnectionEvents;
    private final boolean logCheckoutEvents;

    /**
     * Creates a monitor with default settings (logs important events only)
     */
    public LoggingPoolMonitor() {
        this(true, true);
    }

    /**
     * Creates a monitor with custom verbosity
     * @param logConnectionEvents Log connection created/closed events
     * @param logCheckoutEvents Log checkout/checkin events
     */
    public LoggingPoolMonitor(boolean logConnectionEvents, boolean logCheckoutEvents) {
        this.logConnectionEvents = logConnectionEvents;
        this.logCheckoutEvents = logCheckoutEvents;
    }

    private PoolMetrics getMetrics(ServerAddress address) {
        return poolMetrics.computeIfAbsent(address, k -> new PoolMetrics());
    }

    @Override
    public void connectionPoolCreated(ConnectionPoolCreatedEvent event) {
        ServerAddress address = event.getServerId().getAddress();
        PoolMetrics metrics = getMetrics(address);
        metrics.maxSize = event.getSettings().getMaxSize();

        logger.info("Pool created for {} [max: {}]", address, metrics.maxSize);
    }

    @Override
    public void connectionPoolCleared(ConnectionPoolClearedEvent event) {
        ServerAddress address = event.getServerId().getAddress();
        PoolMetrics metrics = getMetrics(address);
        metrics.size.set(0);
        metrics.checkedOut.set(0);

        logger.warn("Pool cleared for {} {}", address, metrics.format());
    }

    @Override
    public void connectionPoolClosed(ConnectionPoolClosedEvent event) {
        ServerAddress address = event.getServerId().getAddress();
        PoolMetrics metrics = poolMetrics.get(address);

        if (metrics != null) {
            logger.info("Pool closed for {} [final state: size={}, checkedOut={}]",
                       address, metrics.size.get(), metrics.checkedOut.get());
            poolMetrics.remove(address);
        }
    }

    @Override
    public void connectionCreated(ConnectionCreatedEvent event) {
        ServerAddress address = event.getConnectionId().getServerId().getAddress();
        PoolMetrics metrics = getMetrics(address);
        metrics.size.incrementAndGet();

        if (logConnectionEvents) {
            logger.info("Connection created #{} for {} {}",
                       event.getConnectionId().getLocalValue(),
                       address,
                       metrics.format());
        }
    }

    @Override
    public void connectionReady(ConnectionReadyEvent event) {
        // Connection is ready for use - no metrics change needed
    }

    @Override
    public void connectionClosed(ConnectionClosedEvent event) {
        ServerAddress address = event.getConnectionId().getServerId().getAddress();
        PoolMetrics metrics = poolMetrics.get(address);

        if (metrics != null) {
            metrics.size.decrementAndGet();

            if (logConnectionEvents) {
                String reason = event.getReason() != null ?
                    " reason: " + event.getReason() : "";
                logger.info("Connection closed #{} for {}{} {}",
                           event.getConnectionId().getLocalValue(),
                           address,
                           reason,
                           metrics.format());
            }
        }
    }

    @Override
    public void connectionCheckOutStarted(ConnectionCheckOutStartedEvent event) {
        ServerAddress address = event.getServerId().getAddress();
        PoolMetrics metrics = getMetrics(address);

        if (logCheckoutEvents) {
            logger.info("Checkout started for {} {}",
                       address,
                       metrics.format());
        }
    }

    @Override
    public void connectionCheckedOut(ConnectionCheckedOutEvent event) {
        ServerAddress address = event.getConnectionId().getServerId().getAddress();
        PoolMetrics metrics = getMetrics(address);
        metrics.checkedOut.incrementAndGet();

        if (logCheckoutEvents) {
            logger.info("Connection checked out #{} for {} {}",
                       event.getConnectionId().getLocalValue(),
                       address,
                       metrics.format());
        }
    }

    @Override
    public void connectionCheckOutFailed(ConnectionCheckOutFailedEvent event) {
        ServerAddress address = event.getServerId().getAddress();
        PoolMetrics metrics = getMetrics(address);

        // Always log failures as warnings
        logger.warn("Checkout FAILED for {}: {} {}",
                   address,
                   event.getReason(),
                   metrics.format());
    }

    @Override
    public void connectionCheckedIn(ConnectionCheckedInEvent event) {
        ServerAddress address = event.getConnectionId().getServerId().getAddress();
        PoolMetrics metrics = poolMetrics.get(address);

        if (metrics != null) {
            metrics.checkedOut.decrementAndGet();

            if (logCheckoutEvents) {
                logger.info("Connection checked in #{} for {} {}",
                           event.getConnectionId().getLocalValue(),
                           address,
                           metrics.format());
            }
        }
    }

    // Public getters for aggregate metrics if needed
    public int getTotalPoolSize() {
        return poolMetrics.values().stream().mapToInt(m -> m.size.get()).sum();
    }

    public int getTotalCheckedOut() {
        return poolMetrics.values().stream().mapToInt(m -> m.checkedOut.get()).sum();
    }
}
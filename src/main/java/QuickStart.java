import static com.mongodb.client.model.Filters.eq;
// import static com.sun.org.apache.xml.internal.security.algorithms.implementations.SignatureDSA.URI;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.ce.PoolStatsListener.PoolStatsListener;
import com.mongodb.event.*;
// import com.mongodb.management.ConnectionPoolStatisticsMBean;
// import com.mongodb.management.JMXConnectionPoolListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import java.util.concurrent.CountDownLatch;

import org.bson.Document;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.management.*;



public class QuickStart {
    static Logger logger;
    static volatile Integer maxPoolSize = null;
    static CountDownLatch poolCreatedLatch = new CountDownLatch(1);
    public static void main( String[] args ) {
        logger = LoggerFactory.getLogger(QuickStart.class);
        logger.debug("BSB Starting...");

        // Replace the placeholder with your MongoDB deployment's connection string
        ConnectionString uri = new ConnectionString("mongodb+srv://bsbishop:wpxy699M!@sandbox.vcdhe.mongodb.net/?retryWrites=true&w=majority&appName=sandbox");

        // JMXConnectionPoolListener connectionPoolListener = new JMXConnectionPoolListener();

//        PoolStatsListener poolStatsListener = new PoolStatsListener();

// Set poolStats MDC before logging
//        MDC.put("poolStats", poolStatsListener.getCurrentStats());
 //       Document result = database.runCommand(ping); // run some ops
// clear it
//        MDC.clear();

        PoolStatsListener connectionPoolListener = new PoolStatsListener() {
            @Override
            public void connectionPoolCreated(ConnectionPoolCreatedEvent e) {
                super.connectionPoolCreated(e);
                // Store max size for main thread to use
                maxPoolSize = getMaxSize();
                poolCreatedLatch.countDown();
            }

            @Override
            public void connectionCreated(ConnectionCreatedEvent e) {
                super.connectionCreated(e);
                MDC.put("size", String.valueOf(getSize()));
                // Update maxSize if it was set
                if (maxPoolSize != null) {
                    MDC.put("maxSize", String.valueOf(maxPoolSize));
                }
            }

            @Override
            public void connectionClosed(ConnectionClosedEvent e) {
                super.connectionClosed(e);
                MDC.put("size", String.valueOf(getSize()));
            }
            @Override
            public void connectionCheckOutStarted(ConnectionCheckOutStartedEvent e) {
                // Update MDC with current pool stats
                if (maxPoolSize != null) {
                    MDC.put("maxSize", String.valueOf(maxPoolSize));
                }
                MDC.put("size", String.valueOf(getSize()));
                MDC.put("checkedOutCount", String.valueOf(getCheckedOutCount()));

                logger.info("CheckoutStarted server={} cluster={}",
                        e.getServerId().getAddress(), e.getServerId().getClusterId().getValue());
            }

            @Override
            public void connectionCheckedOut(ConnectionCheckedOutEvent e) {
                super.connectionCheckedOut(e);
                // Update MDC after the count is incremented
                MDC.put("checkedOutCount", String.valueOf(getCheckedOutCount()));
                MDC.put("size", String.valueOf(getSize()));
                if (maxPoolSize != null) {
                    MDC.put("maxSize", String.valueOf(maxPoolSize));
                }
            }

            @Override
            public void connectionCheckOutFailed(ConnectionCheckOutFailedEvent e) {
                logger.warn("CheckoutFailed reason={} server={}", e.getReason(), e.getServerId().getAddress());
            }

            @Override
            public void connectionCheckedIn(ConnectionCheckedInEvent e) {
                super.connectionCheckedIn(e);
                // Update MDC after the count is decremented
                MDC.put("checkedOutCount", String.valueOf(getCheckedOutCount()));
                MDC.put("size", String.valueOf(getSize()));
                if (maxPoolSize != null) {
                    MDC.put("maxSize", String.valueOf(maxPoolSize));
                }
            }
        };

        // Note: These will be 0/null until connectionPoolCreated event fires
        MDC.put("size", String.valueOf(connectionPoolListener.getSize()));
        MDC.put("checkedOutCount", String.valueOf(connectionPoolListener.getCheckedOutCount()));
        MDC.put("maxSize", "0"); // Initial value, will be updated after pool creation

        MongoClientSettings settings =
                MongoClientSettings.builder()
                        .applyConnectionString(uri)
                        .applyToConnectionPoolSettings(builder -> builder.addConnectionPoolListener(connectionPoolListener))
                        .build();

        try (MongoClient mongoClient = MongoClients.create(settings)) {
            System.out.println("Navigate to JConsole to see your connection pools...");
            // Pauses the code execution so you can navigate to JConsole and inspect your connection pools
            // Thread.sleep(30 * 1000);
            // System.out.println("Starting...");
            for (int i = 0; i < 1; i++){

                MongoDatabase database = mongoClient.getDatabase("sample_mflix");
                MongoCollection<Document> collection = database.getCollection("movies");

                // Wait for pool to be created and update MDC
                try {
                    poolCreatedLatch.await();
                    if (maxPoolSize != null) {
                        MDC.put("maxSize", String.valueOf(maxPoolSize));
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                Document doc = collection.find(eq("title", "Back to the Future")).first();

                // Now the pool is created and we can access the stats
                logger.info("Pool Stats - CheckedOut: {}, Size: {}, MaxSize: {}",
                    connectionPoolListener.getCheckedOutCount(),
                    connectionPoolListener.getSize(),
                    connectionPoolListener.getMaxSize());

                if (doc != null) {
                    System.out.println(doc.toJson());
                } else {
                    System.out.println("No matching documents found.");
                }
            }
            System.out.println("Done.");
//            Thread.sleep(Long.MAX_VALUE);
        } catch (Exception e) {
            e.printStackTrace();
        }

        MDC.clear();
    }
}
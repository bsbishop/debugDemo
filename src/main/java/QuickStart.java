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

import org.bson.Document;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.management.*;



public class QuickStart {
    static Logger logger;
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
            public void connectionCheckOutStarted(ConnectionCheckOutStartedEvent e) {
                logger.info("CheckoutStarted server={} cluster={}",
                        e.getServerId().getAddress(), e.getServerId().getClusterId().getValue());
            }

            @Override
            public void connectionCheckedOut(ConnectionCheckedOutEvent e) {
                // logger.debug("CheckedOut connId={} server={}", e.getConnectionId(), e.getServerId().getAddress());
            }

            @Override
            public void connectionCheckOutFailed(ConnectionCheckOutFailedEvent e) {
                logger.warn("CheckoutFailed reason={} server={}", e.getReason(), e.getServerId().getAddress());
            }

            @Override
            public void connectionCheckedIn(ConnectionCheckedInEvent e) {
                // logger.debug("CheckedIn  connId={} server={}", e.getConnectionId(), e.getServerId().getAddress());
            }
        };

        // Note: These will be 0/null until connectionPoolCreated event fires
        MDC.put("size", String.valueOf(connectionPoolListener.getSize()));
        MDC.put("checkedOutCount", String.valueOf(connectionPoolListener.getCheckedOutCount()));
        // MDC.put("maxSize", String.valueOf(connectionPoolListener.getMaxSize())); // Will be null here!

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
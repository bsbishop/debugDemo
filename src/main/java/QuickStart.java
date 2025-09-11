import static com.mongodb.client.model.Filters.eq;
// import static com.sun.org.apache.xml.internal.security.algorithms.implementations.SignatureDSA.URI;
import static java.lang.Thread.sleep;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.management.JMXConnectionPoolListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.bson.Document;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

public class QuickStart {
    public static void main( String[] args ) {
        Logger logger = LoggerFactory.getLogger(QuickStart.class);
        logger.info("Starting...");

        // Replace the placeholder with your MongoDB deployment's connection string
        ConnectionString uri = new ConnectionString("mongodb+srv://bsbishop:wpxy699M!@sandbox.vcdhe.mongodb.net/?retryWrites=true&w=majority&appName=sandbox");

        JMXConnectionPoolListener connectionPoolListener = new JMXConnectionPoolListener();

        MongoClientSettings settings =
                MongoClientSettings.builder()
                        .applyConnectionString(uri)
                        .applyToConnectionPoolSettings(builder -> builder.addConnectionPoolListener(connectionPoolListener))
                        .build();

        try (MongoClient mongoClient = MongoClients.create(settings)) {
            System.out.println("Navigate to JConsole to see your connection pools...");
            // Pauses the code execution so you can navigate to JConsole and inspect your connection pools
            Thread.sleep(30 * 1000);
            System.out.println("Starting...");
            for (int i = 0; i < 100000; i++){
                MongoDatabase database = mongoClient.getDatabase("sample_mflix");
                MongoCollection<Document> collection = database.getCollection("movies");
                Document doc = collection.find(eq("title", "Back to the Future")).first();
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
    }
}
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.monitoring.LoggingPoolMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.mongodb.client.model.Filters.eq;

/**
 * Multi-threaded example showing proper MongoDB client setup with pool monitoring.
 * The LoggingPoolMonitor handles all pool logging automatically.
 * Tests connection pool behavior under concurrent load.
 */
public class QuickStart {
    private static final Logger logger = LoggerFactory.getLogger(QuickStart.class);

    private static final List<String> MOVIE_TITLES = Arrays.asList(
        "The Great Train Robbery", "Traffic in Souls", "Gertie the Dinosaur", "Civilization",
        "Where Are My Children?", "Wild and Woolly", "Salomè", "Three Ages", "The Iron Horse",
        "The Black Pirate", "The Strong Man", "It", "Wings", "Laugh, Clown, Laugh",
        "Steamboat Willie", "Disraeli", "Hallelujah", "David Golder", "Little Caesar",
        "Morocco", "Dishonored", "The Guardsman", "The Red Head", "Smilin' Through",
        "The Power and the Glory", "Queen Christina", "She Done Him Wrong", "Zoo in Budapest",
        "The Barretts of Wimpole Street", "Broadway Bill", "Masquerade in Vienna", "Toni",
        "Moscow Laughs", "Wonder Bar", "The Band Concert", "Alice Adams", "Broadway Melody of 1936",
        "David Copperfield", "The Dark Angel", "A Corner in Wheat", "Back to the Future"
    );

    public static void main(String[] args) {
        String uri = System.getenv("MONGODB_URI");
        if (uri == null) {
            logger.error("MONGODB_URI environment variable must be set");
            System.exit(1);
        }

        // Standard MongoDB client setup with one additional line for monitoring
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(uri))
                .applyToConnectionPoolSettings(builder ->
                    builder.addConnectionPoolListener(new LoggingPoolMonitor()))
                .build();

        try (MongoClient client = MongoClients.create(settings)) {
            logger.info("Starting multi-threaded movie search with {} threads...", MOVIE_TITLES.size());

            // Create thread pool for concurrent searches
            ExecutorService executor = Executors.newFixedThreadPool(10);
            AtomicInteger foundCount = new AtomicInteger(0);
            AtomicInteger notFoundCount = new AtomicInteger(0);

            // Submit search tasks for each movie
            for (int i = 0; i < MOVIE_TITLES.size(); i++) {
                final String title = MOVIE_TITLES.get(i);
                final int taskId = i + 1;

                executor.submit(() -> {
                    try {
                        logger.info("Task {} searching for: {}", taskId, title);

                        var movie = client.getDatabase("sample_mflix")
                                         .getCollection("movies")
                                         .find(eq("title", title))
                                         .first();

                        if (movie != null) {
                            foundCount.incrementAndGet();
                            logger.info("Task {} FOUND: {} ({})", taskId, title, movie.getInteger("year", 0));
                        } else {
                            notFoundCount.incrementAndGet();
                            logger.info("Task {} NOT FOUND: {}", taskId, title);
                        }
                    } catch (Exception e) {
                        logger.error("Task {} ERROR searching for {}: {}", taskId, title, e.getMessage());
                    }
                });
            }

            // Shutdown executor and wait for completion
            executor.shutdown();
            try {
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    logger.warn("Tasks did not complete within 60 seconds");
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                executor.shutdownNow();
            }

            logger.info("Search completed! Found: {}, Not Found: {}, Total: {}",
                       foundCount.get(), notFoundCount.get(), MOVIE_TITLES.size());

        } catch (Exception e) {
            logger.error("Error occurred", e);
        }
    }
}
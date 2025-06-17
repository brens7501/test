package com.example.twiliovoiceapp.database;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.twiliovoiceapp.model.TwilioPhoneNumber;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Main database class for the application.
 * Defines the database configuration and serves as the app's main access point to the persisted data.
 * Uses the Singleton pattern to ensure only one instance of the database is created.
 */
@Database(entities = {TwilioPhoneNumber.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    // DAO methods
    public abstract PhoneNumberDao phoneNumberDao();

    // Singleton instance
    private static volatile AppDatabase INSTANCE;
    
    // Thread pool for database operations
    private static final int NUMBER_OF_THREADS = 4;
    public static final ExecutorService databaseWriteExecutor =
            Executors.newFixedThreadPool(NUMBER_OF_THREADS);

    /**
     * Get the singleton instance of the database.
     * If the instance doesn't exist, it creates a new one.
     *
     * @param context The application context
     * @return The singleton database instance
     */
    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "twilio_voice_database")
                            .addCallback(sRoomDatabaseCallback)
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    /**
     * Callback for database creation and opening events.
     * Can be used to populate the database when it's first created.
     */
    private static final RoomDatabase.Callback sRoomDatabaseCallback = new RoomDatabase.Callback() {
        @Override
        public void onCreate(@NonNull SupportSQLiteDatabase db) {
            super.onCreate(db);
            
            // If you want to pre-populate the database,
            // you can do it here using databaseWriteExecutor
            
            databaseWriteExecutor.execute(() -> {
                // Initialize database here if needed
                // For example, you might want to add a default phone number
                // PhoneNumberDao dao = INSTANCE.phoneNumberDao();
                // dao.insert(new TwilioPhoneNumber(...));
            });
        }
    };
}

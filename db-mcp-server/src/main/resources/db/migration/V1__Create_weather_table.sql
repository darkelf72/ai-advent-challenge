-- Create weather table
CREATE TABLE IF NOT EXISTS weather (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    content TEXT NOT NULL,
    createdAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create index on createdAt for faster queries by time period
CREATE INDEX IF NOT EXISTS idx_weather_createdAt ON weather(createdAt);

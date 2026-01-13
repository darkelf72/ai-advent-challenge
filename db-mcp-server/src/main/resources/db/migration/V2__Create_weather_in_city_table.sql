-- Create weather_in_city table for storing detailed weather information
CREATE TABLE IF NOT EXISTS weather_in_city (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    date_time TEXT NOT NULL,
    city_name TEXT NOT NULL,
    temperature REAL NOT NULL,
    wind_speed REAL NOT NULL,
    wind_direction INTEGER NOT NULL
);

-- Create index on date_time for faster queries by time period
CREATE INDEX IF NOT EXISTS idx_weather_in_city_date_time ON weather_in_city(date_time);

-- Create index on city_name for faster queries by city
CREATE INDEX IF NOT EXISTS idx_weather_in_city_city_name ON weather_in_city(city_name);

-- Create client_config table
CREATE TABLE IF NOT EXISTS client_config (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    client_name VARCHAR(50) NOT NULL UNIQUE,
    system_prompt TEXT NOT NULL,
    temperature REAL NOT NULL,
    max_tokens INTEGER NOT NULL
);

-- Create message_history table
CREATE TABLE IF NOT EXISTS message_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    client_name VARCHAR(50) NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    message_order INTEGER NOT NULL,
    created_at INTEGER NOT NULL
);

-- Create index for faster queries on client_name
CREATE INDEX IF NOT EXISTS idx_message_history_client_name ON message_history(client_name);
CREATE INDEX IF NOT EXISTS idx_message_history_order ON message_history(client_name, message_order);

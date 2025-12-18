-- Add auto_summarize_threshold column to client_config table
ALTER TABLE client_config ADD COLUMN auto_summarize_threshold INTEGER NOT NULL DEFAULT 0;

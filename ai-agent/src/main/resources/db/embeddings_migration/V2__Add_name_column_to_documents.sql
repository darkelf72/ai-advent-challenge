-- Add 'name' column to documents table
ALTER TABLE documents ADD COLUMN name VARCHAR(255);

-- Update existing records with file_name value
UPDATE documents SET name = file_name WHERE name IS NULL;

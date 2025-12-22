-- Create documents table for storing document metadata
CREATE TABLE IF NOT EXISTS documents (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    file_name VARCHAR(255) NOT NULL,
    file_path TEXT NOT NULL,
    file_hash VARCHAR(64) NOT NULL UNIQUE,  -- SHA-256 hash for duplicate detection
    file_size_bytes INTEGER NOT NULL,
    total_chunks INTEGER NOT NULL,
    embedding_model VARCHAR(100) NOT NULL,   -- e.g., "nomic-embed-text"
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

-- Create indexes for documents table
CREATE INDEX IF NOT EXISTS idx_documents_file_hash ON documents(file_hash);
CREATE INDEX IF NOT EXISTS idx_documents_file_name ON documents(file_name);
CREATE INDEX IF NOT EXISTS idx_documents_created_at ON documents(created_at DESC);

-- Create document_chunks table for storing chunks with embeddings
CREATE TABLE IF NOT EXISTS document_chunks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    document_id INTEGER NOT NULL,
    chunk_index INTEGER NOT NULL,            -- Order of chunk in document (0-based)
    chunk_text TEXT NOT NULL,
    embedding_json TEXT NOT NULL,            -- JSON array for debugging/inspection
    embedding_blob BLOB NOT NULL,            -- Binary format for fast search/comparison
    token_count INTEGER NOT NULL,
    created_at INTEGER NOT NULL,
    FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE,
    UNIQUE (document_id, chunk_index)
);

-- Create indexes for document_chunks table
CREATE INDEX IF NOT EXISTS idx_chunks_document_id ON document_chunks(document_id);
CREATE INDEX IF NOT EXISTS idx_chunks_document_chunk ON document_chunks(document_id, chunk_index);

package embedding.chunking

/**
 * Exception thrown when attempting to process an unsupported file type.
 *
 * @property message Error message describing the unsupported file type
 */
class UnsupportedFileTypeException(message: String) : Exception(message)

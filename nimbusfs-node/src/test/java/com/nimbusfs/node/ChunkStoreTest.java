package com.nimbusfs.node;

import com.nimbusfs.node.storage.ChunkStore;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ChunkStore file operations.
 */
public class ChunkStoreTest {

    @TempDir
    Path tempDir;

    private ChunkStore store;

    @BeforeEach
    void setup() throws Exception {
        store = new ChunkStore(tempDir.toString());
        store.initialize();
    }

    @Test
    void testStoreAndRetrieveChunk() throws Exception {
        String chunkId = "550e8400-e29b-41d4-a716-446655440000";
        byte[] data = "Hello, NimbusFS Chunk!".getBytes();

        store.storeChunk(chunkId, data);
        assertTrue(store.hasChunk(chunkId));

        byte[] retrieved = store.retrieveChunk(chunkId);
        assertArrayEquals(data, retrieved);
    }

    @Test
    void testDeleteChunk() throws Exception {
        String chunkId = "660e8400-e29b-41d4-a716-446655440001";
        byte[] data = "Chunk to delete".getBytes();

        store.storeChunk(chunkId, data);
        assertTrue(store.hasChunk(chunkId));

        boolean deleted = store.deleteChunk(chunkId);
        assertTrue(deleted);
        assertFalse(store.hasChunk(chunkId));
    }

    @Test
    void testRetrieveNonExistentChunk() {
        String chunkId = "770e8400-e29b-41d4-a716-446655440002";
        assertThrows(Exception.class, () -> store.retrieveChunk(chunkId));
    }

    @Test
    void testUsedBytesAfterStorage() throws Exception {
        String chunkId = "880e8400-e29b-41d4-a716-446655440003";
        byte[] data = new byte[1024]; // 1 KB

        long before = store.getUsedBytes();
        store.storeChunk(chunkId, data);
        long after = store.getUsedBytes();

        assertTrue(after > before);
    }
}

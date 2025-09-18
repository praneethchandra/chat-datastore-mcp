package com.example.chatdatastore.service;

import com.example.chatdatastore.kv.KvClient;
import com.example.chatdatastore.model.KVShadow;
import com.example.chatdatastore.model.OutboxEvent;
import com.example.chatdatastore.repo.KVShadowRepo;
import com.example.chatdatastore.repo.OutboxRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HybridSyncServiceTest {

    @Mock
    private KvClient kvClient;

    @Mock
    private KVShadowRepo kvShadowRepo;

    @Mock
    private OutboxRepo outboxRepo;

    private HybridSyncService hybridSyncService;

    @BeforeEach
    void setUp() {
        hybridSyncService = new HybridSyncService(kvClient, kvShadowRepo, outboxRepo);
        // Set test configuration values
        ReflectionTestUtils.setField(hybridSyncService, "asyncTimeoutMs", 1000L);
        ReflectionTestUtils.setField(hybridSyncService, "maxAsyncThreads", 5);
    }

    @Test
    void testSyncKvSet_AsyncSuccess() {
        // Given
        String key = "test-key";
        String value = "test-value";
        Integer ttlSec = 300;
        String sessionId = "session-1";
        String interactionId = "interaction-1";

        when(kvShadowRepo.save(any(KVShadow.class))).thenReturn(null);

        // When
        HybridSyncService.SyncResult result = hybridSyncService.syncKvSet(key, value, ttlSec, sessionId, interactionId);

        // Then
        assertTrue(result.isSuccess());
        assertEquals("ASYNC_THREAD", result.getMethod());
        verify(kvShadowRepo, times(1)).save(any(KVShadow.class));
        verify(outboxRepo, never()).save(any(OutboxEvent.class));
    }

    @Test
    void testSyncKvSet_AsyncFailureFallbackToEvent() {
        // Given
        String key = "test-key";
        String value = "test-value";
        Integer ttlSec = 300;
        String sessionId = "session-1";
        String interactionId = "interaction-1";

        when(kvShadowRepo.save(any(KVShadow.class))).thenThrow(new RuntimeException("Database error"));
        when(outboxRepo.save(any(OutboxEvent.class))).thenReturn(null);

        // When
        HybridSyncService.SyncResult result = hybridSyncService.syncKvSet(key, value, ttlSec, sessionId, interactionId);

        // Then
        assertTrue(result.isSuccess());
        assertEquals("EVENT_BASED", result.getMethod());
        verify(kvShadowRepo, times(1)).save(any(KVShadow.class));
        verify(outboxRepo, times(1)).save(any(OutboxEvent.class));
    }

    @Test
    void testSyncKvDelete_AsyncSuccess() {
        // Given
        String key = "test-key";

        doNothing().when(kvShadowRepo).deleteById(key);

        // When
        HybridSyncService.SyncResult result = hybridSyncService.syncKvDelete(key);

        // Then
        assertTrue(result.isSuccess());
        assertEquals("ASYNC_THREAD", result.getMethod());
        verify(kvShadowRepo, times(1)).deleteById(key);
        verify(outboxRepo, never()).save(any(OutboxEvent.class));
    }

    @Test
    void testGetSyncStats() {
        // Given
        when(outboxRepo.countByProcessedFalse()).thenReturn(5L);
        when(outboxRepo.count()).thenReturn(100L);

        // When
        Map<String, Object> stats = hybridSyncService.getSyncStats();

        // Then
        assertNotNull(stats);
        assertTrue(stats.containsKey("asyncThreads"));
        assertTrue(stats.containsKey("configuration"));
        assertTrue(stats.containsKey("outboxEvents"));

        @SuppressWarnings("unchecked")
        Map<String, Object> outboxEvents = (Map<String, Object>) stats.get("outboxEvents");
        assertEquals(5L, outboxEvents.get("unprocessed"));
        assertEquals(100L, outboxEvents.get("total"));
    }

    @Test
    void testIsSystemUnderHighLoad_NormalLoad() {
        // Given
        when(outboxRepo.countByProcessedFalse()).thenReturn(50L);

        // When
        boolean isHighLoad = hybridSyncService.isSystemUnderHighLoad();

        // Then
        assertFalse(isHighLoad);
    }

    @Test
    void testIsSystemUnderHighLoad_HighEventBacklog() {
        // Given
        when(outboxRepo.countByProcessedFalse()).thenReturn(150L);

        // When
        boolean isHighLoad = hybridSyncService.isSystemUnderHighLoad();

        // Then
        assertTrue(isHighLoad);
    }

    @Test
    void testAdaptiveSync_NormalLoad() {
        // Given
        String key = "test-key";
        String value = "test-value";
        Integer ttlSec = 300;
        String sessionId = "session-1";
        String interactionId = "interaction-1";

        when(outboxRepo.countByProcessedFalse()).thenReturn(50L);
        when(kvShadowRepo.save(any(KVShadow.class))).thenReturn(null);

        // When
        HybridSyncService.SyncResult result = hybridSyncService.adaptiveSync(key, value, ttlSec, sessionId, interactionId);

        // Then
        assertTrue(result.isSuccess());
        assertEquals("ASYNC_THREAD", result.getMethod());
    }

    @Test
    void testAdaptiveSync_HighLoad() {
        // Given
        String key = "test-key";
        String value = "test-value";
        Integer ttlSec = 300;
        String sessionId = "session-1";
        String interactionId = "interaction-1";

        when(outboxRepo.countByProcessedFalse()).thenReturn(150L);
        when(outboxRepo.save(any(OutboxEvent.class))).thenReturn(null);

        // When
        HybridSyncService.SyncResult result = hybridSyncService.adaptiveSync(key, value, ttlSec, sessionId, interactionId);

        // Then
        assertTrue(result.isSuccess());
        assertEquals("EVENT_BASED", result.getMethod());
        verify(outboxRepo, times(1)).save(any(OutboxEvent.class));
        verify(kvShadowRepo, never()).save(any(KVShadow.class));
    }
}

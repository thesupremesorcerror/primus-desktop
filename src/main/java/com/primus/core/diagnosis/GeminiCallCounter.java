package com.primus.core.diagnosis;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Counts real Gemini API calls (GeminiClient.callGemini() and
 * EmbeddingClient.embed() both increment this) so the eval harness can
 * report an accurate llmCallCount per run instead of a hardcoded 0.
 *
 * Safe for the eval harness's usage pattern specifically: EvaluationService
 * runs each of the 50 eval cases sequentially (no parallelism), so
 * getAndReset() around one run always reflects exactly that run's calls.
 * It would NOT be accurate if multiple incidents were investigated
 * concurrently on different threads sharing this same counter.
 */
@Component
public class GeminiCallCounter {

    private final AtomicInteger count = new AtomicInteger(0);

    public void increment() {
        count.incrementAndGet();
    }

    public int getAndReset() {
        return count.getAndSet(0);
    }
}
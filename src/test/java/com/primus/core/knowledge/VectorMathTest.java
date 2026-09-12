package com.primus.core.knowledge;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VectorMathTest {

    private static final double DELTA = 0.0001;

    @Test
    void identicalVectorsHaveSimilarityOfOne() {
        List<Double> a = List.of(1.0, 2.0, 3.0);
        List<Double> b = List.of(1.0, 2.0, 3.0);

        assertEquals(1.0, VectorMath.cosineSimilarity(a, b), DELTA);
    }

    @Test
    void orthogonalVectorsHaveSimilarityOfZero() {
        List<Double> a = List.of(1.0, 0.0);
        List<Double> b = List.of(0.0, 1.0);

        assertEquals(0.0, VectorMath.cosineSimilarity(a, b), DELTA);
    }

    @Test
    void oppositeVectorsHaveSimilarityOfNegativeOne() {
        List<Double> a = List.of(1.0, 1.0);
        List<Double> b = List.of(-1.0, -1.0);

        assertEquals(-1.0, VectorMath.cosineSimilarity(a, b), DELTA);
    }

    @Test
    void similarButNotIdenticalVectorsHaveHighSimilarity() {
        List<Double> a = List.of(1.0, 2.0, 3.0);
        List<Double> b = List.of(1.1, 2.1, 2.9);

        double similarity = VectorMath.cosineSimilarity(a, b);
        assertEquals(1.0, similarity, 0.01); // very close, not exact
    }
}

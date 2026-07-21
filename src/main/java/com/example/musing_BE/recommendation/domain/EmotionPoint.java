package com.example.musing_BE.recommendation.domain;

/** 감정 2차원 좌표. valence(0 어두움~1 밝음), arousal(0 차분~1 격렬). */
public record EmotionPoint(double valence, double arousal) {

    public double distanceTo(EmotionPoint other) {
        double dv = valence - other.valence;
        double da = arousal - other.arousal;
        return Math.sqrt(dv * dv + da * da);
    }

    public static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}

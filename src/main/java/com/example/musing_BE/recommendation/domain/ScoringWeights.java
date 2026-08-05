package com.example.musing_BE.recommendation.domain;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 곡 선택 점수의 가중치 (RECOMMENDATION_STAGE2 §5.1, §7).
 * <p>모두 <b>검증되지 않은 시작값</b>이다. 실제 청취와 설문 데이터로 튜닝할 대상이라
 * 코드에 박지 않고 설정으로 뺐다.
 */
@Getter
@Component
public class ScoringWeights {

    /** valence(밝기) 거리 가중 */
    @Value("${musing.recommendation.weight-valence:1.0}")
    private double weightValence;

    /** arousal(활기) 거리 가중 */
    @Value("${musing.recommendation.weight-arousal:1.0}")
    private double weightArousal;

    /** 비 오는 날 어쿠스틱 가중 */
    @Value("${musing.recommendation.weight-acoustic-rain:0.5}")
    private double weightAcousticRain;

    /** seed와 같은 아티스트면 점수를 이만큼 깎아 유리하게 */
    @Value("${musing.recommendation.bonus-same-artist:0.15}")
    private double bonusSameArtist;

    /** seed와 같은 장르면 소폭 우대 */
    @Value("${musing.recommendation.bonus-same-genre:0.05}")
    private double bonusSameGenre;

    /** 한국 곡 우대 — 키울수록 한국 곡 비중이 는다 */
    @Value("${musing.recommendation.bonus-korean:0.03}")
    private double bonusKorean;

    /** 최적 1곡이 아니라 상위 K곡 중에서 고른다(다양성) */
    @Value("${musing.recommendation.top-k:30}")
    private int topK;

    /** 최근 추천 곡 제외 기간(일) */
    @Value("${musing.recommendation.exclude-recent-days:14}")
    private int excludeRecentDays;
}

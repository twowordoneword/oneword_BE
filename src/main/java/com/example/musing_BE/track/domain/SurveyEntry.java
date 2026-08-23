package com.example.musing_BE.track.domain;

/**
 * 설문 응답 한 줄. {@code "아이유 - 밤편지"} 처럼 가수와 곡이 함께 오는 형식을 담는다.
 *
 * <p><b>곡명이 왜 필요한가</b> — 가수 이름만으로 iTunes를 검색하면 동명이인이 잡힌다.
 * 실제로 "김광석"으로 검색했을 때 포크 가수가 아니라 <b>국악 연주자 김광석</b>이 나왔다.
 * 곡명을 함께 쓰면 그 곡을 부른 가수로 정확히 좁혀진다.
 */
public record SurveyEntry(String artist, String track) {

    /**
     * 구분자 우선순위. 공백을 낀 형태를 먼저 보고, 마지막에 맨 하이픈을 본다.
     * ⚠️ 맨 하이픈 때문에 {@code "Jay-Z"} 같은 이름은 잘못 나뉜다.
     * 곡 검색이 실패하면 이름으로 폴백하므로 대개 복구되고, 국내 설문에서는 드문 경우라 감수한다.
     */
    private static final String[] SEPARATORS = {" - ", " – ", " — ", " / ", "-"};

    /**
     * 응답 한 줄을 파싱한다. 구분자가 없으면 전체를 가수 이름으로 본다.
     *
     * <pre>
     * "아이유 - 밤편지"  → artist=아이유, track=밤편지
     * "아이유-밤편지"    → artist=아이유, track=밤편지
     * "아이유"           → artist=아이유, track=null
     * </pre>
     *
     * @return 가수 이름이 비어 있으면 null
     */
    public static SurveyEntry parse(String line) {
        if (line == null || line.isBlank()) return null;
        String s = line.trim();

        for (String sep : SEPARATORS) {
            int idx = s.indexOf(sep);
            if (idx > 0) {
                String artist = s.substring(0, idx).trim();
                String track = s.substring(idx + sep.length()).trim();
                if (artist.isEmpty()) return null;
                return new SurveyEntry(artist, track.isEmpty() ? null : track);
            }
        }
        return new SurveyEntry(s, null);
    }

    /** 곡 검색에 쓸 질의어. 곡명이 있으면 "가수 곡명", 없으면 가수만. */
    public String searchTerm() {
        return track == null ? artist : artist + " " + track;
    }

    public boolean hasTrack() {
        return track != null && !track.isBlank();
    }
}

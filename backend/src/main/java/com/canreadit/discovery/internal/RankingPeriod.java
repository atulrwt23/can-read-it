package com.canreadit.discovery.internal;

enum RankingPeriod {
    TODAY("views_today"),
    WEEK("views_7d"),
    ALL_TIME("views_all");

    /** Column of discovery.series_stats holding the views for this period (never user input). */
    final String column;

    RankingPeriod(String column) {
        this.column = column;
    }
}

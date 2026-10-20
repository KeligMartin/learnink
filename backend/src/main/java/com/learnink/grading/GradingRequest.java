package com.learnink.grading;

import com.learnink.query.QueryEngine;
import com.learnink.query.QueryTarget;

public record GradingRequest(QueryEngine engine, QueryTarget target, String referenceQuery, boolean orderMatters,
                             String studentQuery) {
}

package com.zoujuexian.aiagentdemo.core.react;

import java.util.List;

public class ReActResult {

    private final String finalAnswer;
    private final List<ReActState.Step> steps;
    private final boolean success;
    private final String errorMessage;
    private final int iterations;
    private final long totalTimeMs;

    private ReActResult(Builder builder) {
        this.finalAnswer = builder.finalAnswer;
        this.steps = builder.steps;
        this.success = builder.success;
        this.errorMessage = builder.errorMessage;
        this.iterations = builder.iterations;
        this.totalTimeMs = builder.totalTimeMs;
    }

    public String getFinalAnswer() {
        return finalAnswer;
    }

    public List<ReActState.Step> getSteps() {
        return steps;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public int getIterations() {
        return iterations;
    }

    public long getTotalTimeMs() {
        return totalTimeMs;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String finalAnswer;
        private List<ReActState.Step> steps;
        private boolean success = true;
        private String errorMessage;
        private int iterations;
        private long totalTimeMs;

        public Builder finalAnswer(String finalAnswer) {
            this.finalAnswer = finalAnswer;
            return this;
        }

        public Builder steps(List<ReActState.Step> steps) {
            this.steps = steps;
            return this;
        }

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder iterations(int iterations) {
            this.iterations = iterations;
            return this;
        }

        public Builder totalTimeMs(long totalTimeMs) {
            this.totalTimeMs = totalTimeMs;
            return this;
        }

        public ReActResult build() {
            return new ReActResult(this);
        }
    }
}

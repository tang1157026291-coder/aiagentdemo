package com.zoujuexian.aiagentdemo.core.react;

import java.util.ArrayList;
import java.util.List;

public class ReActState {

    private final List<Step> steps = new ArrayList<>();
    private String currentInput;
    private boolean finished = false;
    private String finalAnswer;

    public record Step(
            int stepNumber,
            String thought,
            String action,
            String actionInput,
            String observation,
            long timestamp
    ) {}

    public void addStep(Step step) {
        this.steps.add(step);
    }

    public List<Step> getSteps() {
        return steps;
    }

    public String getCurrentInput() {
        return currentInput;
    }

    public void setCurrentInput(String currentInput) {
        this.currentInput = currentInput;
    }

    public boolean isFinished() {
        return finished;
    }

    public void setFinished(boolean finished) {
        this.finished = finished;
    }

    public String getFinalAnswer() {
        return finalAnswer;
    }

    public void setFinalAnswer(String finalAnswer) {
        this.finalAnswer = finalAnswer;
    }

    public int getStepCount() {
        return steps.size();
    }
}

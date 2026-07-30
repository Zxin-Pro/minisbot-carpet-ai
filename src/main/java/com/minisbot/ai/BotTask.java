package com.minisbot.ai;

public class BotTask {
    private final String type;
    private final String target;
    private int progress = 0;
    private boolean completed = false;

    public BotTask(String type, String target) { this.type = type; this.target = target; }
    public String getType() { return type; }
    public String getTarget() { return target; }
    public int getProgress() { return progress; }
    public boolean isCompleted() { return completed; }
    public void setProgress(int p) { this.progress = Math.min(100, Math.max(0, p)); }
    public void complete() { this.completed = true; this.progress = 100; }
}

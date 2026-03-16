package com.thughari.jobtrackerpro.dto;

public class DeletionWarning {
    public final boolean hasPendingDeletion;
    public final long daysRemaining;

    public DeletionWarning(boolean hasPendingDeletion, long daysRemaining) {
        this.hasPendingDeletion = hasPendingDeletion;
        this.daysRemaining = daysRemaining;
    }
}
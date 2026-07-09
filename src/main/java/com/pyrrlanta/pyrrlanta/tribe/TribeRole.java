package com.pyrrlanta.pyrrlanta.tribe;

public enum TribeRole {
    MEMBER(0),
    OFFICER(1),
    LEADER(2);

    private final int rank;

    TribeRole(int rank) {
        this.rank = rank;
    }

    public boolean atLeast(TribeRole required) {
        return this.rank >= required.rank;
    }
}

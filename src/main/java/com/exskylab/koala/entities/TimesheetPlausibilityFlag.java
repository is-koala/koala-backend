package com.exskylab.koala.entities;

public enum TimesheetPlausibilityFlag {
    DEVICE_TIME_IMPLAUSIBLE,
    OUTSIDE_JOB_WINDOW,
    NON_MONOTONIC,
    FAR_FROM_STOP,
    OFF_SCHEDULE,
    STATIC_QR
}

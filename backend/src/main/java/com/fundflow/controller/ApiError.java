package com.fundflow.controller;

public record ApiError(
        String code,
        String message,
        int status
) {}
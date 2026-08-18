package com.pantrytracker.common;

/** Uniform error body: { "message": "..." } */
public record ApiResponse(String message) {}
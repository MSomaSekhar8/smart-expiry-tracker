package com.pantrytracker.barcode;

public final class BarcodeDtos {

    private BarcodeDtos() {}

    public record LookupResult(
            String barcode,
            String name,
            String brand,
            String category,
            boolean cached) {}
}
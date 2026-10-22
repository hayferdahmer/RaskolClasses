package dev.raskol.classes.api.items;

/** Request DTO for API consumers that need explicit deterministic generation. */
public record ItemCreateRequest(String itemId,int itemLevel,long seed) { }

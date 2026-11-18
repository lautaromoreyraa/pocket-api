package com.pocket.dto.ia;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GroqTranscripcionResponse(String text) {}

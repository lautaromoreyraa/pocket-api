package com.pocket.dto.audio;

import com.pocket.dto.gasto.GastoRequest;

import java.util.List;

public record AudioResponse(
        List<GastoRequest> detectados,
        String transcripcion
) {}

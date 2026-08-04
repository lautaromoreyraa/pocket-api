package com.pocket.dto.audio;

import java.util.List;

public record AudioResponse(
        List<GastoDetectado> detectados,
        String transcripcion
) {}

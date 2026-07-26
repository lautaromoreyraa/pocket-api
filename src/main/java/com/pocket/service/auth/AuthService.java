package com.pocket.service.auth;

import com.pocket.domain.Usuario;
import com.pocket.dto.auth.DispositivoRequest;
import com.pocket.dto.auth.TokenResponse;

public interface AuthService {

    TokenResponse identificar(DispositivoRequest request);

    Usuario actual();
}

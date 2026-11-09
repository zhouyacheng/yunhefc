package com.yunhefc.contracts.security;

import java.util.Optional;

public interface UserAccessPort {

    Optional<AuthPrincipal> load(Long userId);
}

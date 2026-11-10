package com.yunhefc.auth.infrastructure;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PrincipalRow {

    private Long id;
    private String phone;
    private Integer status;
    private Long teamId;
    private String playerName;
    private String roles;
    private String permissions;
}

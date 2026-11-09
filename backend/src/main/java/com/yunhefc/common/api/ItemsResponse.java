package com.yunhefc.common.api;

import java.util.List;

public record ItemsResponse<T>(List<T> items) {
}

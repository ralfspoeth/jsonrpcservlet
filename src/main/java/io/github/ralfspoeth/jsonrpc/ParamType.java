package io.github.ralfspoeth.jsonrpc;

import java.util.List;
import java.util.Map;

sealed interface ParamType {
    record ArrayParam(List<?> params) implements ParamType {}

    record MapParam(Map<String, ?> params) implements ParamType {}
}

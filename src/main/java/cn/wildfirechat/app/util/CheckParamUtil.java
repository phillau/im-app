package cn.wildfirechat.app.util;

import cn.wildfirechat.app.pojo.RestResult;

public class CheckParamUtil {
    public static RestResult check(String... params) {
        for (String param : params) {
            if (param == null || "".equals(param.replace(" ", ""))) {
                return RestResult.error(RestResult.RestCode.ERROR_EMPTY_PARAMS);
            }
        }
        return RestResult.ok(null);
    }
}

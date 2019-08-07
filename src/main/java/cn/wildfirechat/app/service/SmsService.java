package cn.wildfirechat.app.service;


import cn.wildfirechat.app.pojo.RestResult;

public interface SmsService {
    RestResult.RestCode sendCode(String mobile, String code);
}

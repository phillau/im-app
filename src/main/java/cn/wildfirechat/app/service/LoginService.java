package cn.wildfirechat.app.service;


import cn.wildfirechat.app.pojo.LoginRequest;
import cn.wildfirechat.app.pojo.RestResult;
import cn.wildfirechat.app.pojo.ConfirmSessionRequest;
import cn.wildfirechat.app.pojo.CreateSessionRequest;

public interface LoginService {
    RestResult sendCode(String mobile);
    RestResult login(LoginRequest request);


    RestResult createPcSession(CreateSessionRequest request);
    RestResult loginWithSession(String token);

    RestResult scanPc(String token);
    RestResult confirmPc(ConfirmSessionRequest request);

    RestResult loginWithPwd(LoginRequest request);

    RestResult registerByCode(LoginRequest request);

    RestResult findPwdByCode(LoginRequest request);
}

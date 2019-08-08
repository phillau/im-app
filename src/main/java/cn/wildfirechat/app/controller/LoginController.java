package cn.wildfirechat.app.controller;

import cn.wildfirechat.app.pojo.*;
import cn.wildfirechat.app.service.LoginService;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
public class LoginController {
    private static final Logger logger = LoggerFactory.getLogger(LoginController.class);
    private static final Gson gson = new Gson();

    @Autowired
    private LoginService mService;

    @PostMapping(value = "/send_code", produces = "application/json;charset=UTF-8")
    public Object sendCode(@RequestBody SendCodeRequest request) {
        logger.info("http request: send_code {}" , gson.toJson(request));
        RestResult restResult = mService.sendCode(request.getMobile());
        logger.info("http response: send_code {}" , gson.toJson(restResult));
        return restResult;
    }

    @PostMapping(value = "/login", produces = "application/json;charset=UTF-8")
    public Object login(@RequestBody LoginRequest request) {
        logger.info("http request: login {}" , gson.toJson(request));
        RestResult restResult = mService.login(request);
        logger.info("http response: login {}" , gson.toJson(restResult));
        return restResult;
    }

    /**
     * 根据验证码注册用户
     * @param request
     * @return
     */
    @PostMapping(value = "/im/app/registerByCode", produces = "application/json;charset=UTF-8")
    public Object registerByCode(@RequestBody LoginRequest request) {
        logger.info("http request: registerByCode {}" , gson.toJson(request));
        RestResult restResult = mService.registerByCode(request);
        logger.info("http response: registerByCode {}" , gson.toJson(restResult));
        return restResult;
    }

    /**
     * 根据用户名密码登录
     * @param request
     * @return
     */
    @PostMapping(value = "/im/app/loginWithPwd", produces = "application/json;charset=UTF-8")
    public Object loginWithPwd(@RequestBody LoginRequest request) {
        logger.info("http request: loginWithPwd {}" , gson.toJson(request));
        RestResult restResult = mService.loginWithPwd(request);
        logger.info("http response: loginWithPwd {}" , gson.toJson(restResult));
        return restResult;
    }

    /**
     * 找回密码
     * @param request
     * @return
     */
    @PostMapping(value = "/im/app/findPwdByCode", produces = "application/json;charset=UTF-8")
    public Object findPwdByCode(@RequestBody LoginRequest request) {
        logger.info("http request: loginWithPwd {}" , gson.toJson(request));
        RestResult restResult = mService.findPwdByCode(request);
        logger.info("http response: loginWithPwd {}" , gson.toJson(restResult));
        return restResult;
    }

    /* PC扫码操作
    1, PC -> App     创建会话
    2, PC -> App     轮询调用session_login进行登陆，如果已经扫码确认返回token，否则反正错误码9（已经扫码还没确认)或10(还没有被扫码)。
     */
    @PostMapping(value = "/pc_session", produces = "application/json;charset=UTF-8")
    public Object createPcSession(@RequestBody CreateSessionRequest request) {
        return mService.createPcSession(request);
    }

    @PostMapping(value = "/session_login/{token}", produces = "application/json;charset=UTF-8")
    public Object loginWithSession(@PathVariable("token") String token) {
        return mService.loginWithSession(token);
    }

    /* 手机扫码操作
    1，扫码，调用/scan_pc接口。
    2，调用/confirm_pc 接口进行确认
     */
    @PostMapping(value = "/scan_pc/{token}", produces = "application/json;charset=UTF-8")
    public Object scanPc(@PathVariable("token") String token) {
        return mService.scanPc(token);
    }

    @PostMapping(value = "/confirm_pc", produces = "application/json;charset=UTF-8")
    public Object confirmPc(@RequestBody ConfirmSessionRequest request) {
        return mService.confirmPc(request);
    }
}

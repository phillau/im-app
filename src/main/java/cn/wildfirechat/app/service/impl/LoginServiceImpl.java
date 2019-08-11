package cn.wildfirechat.app.service.impl;

import cn.wildfirechat.app.config.IMConfig;
import cn.wildfirechat.app.mapper.UserMapper;
import cn.wildfirechat.app.model.User;
import cn.wildfirechat.app.pojo.*;
import cn.wildfirechat.app.service.LoginService;
import cn.wildfirechat.app.service.SmsService;
import cn.wildfirechat.app.util.CheckParamUtil;
import cn.wildfirechat.app.util.MD5Util;
import cn.wildfirechat.app.util.Utils;
import cn.wildfirechat.common.ErrorCode;
import cn.wildfirechat.pojos.*;
import cn.wildfirechat.proto.ProtoConstants;
import cn.wildfirechat.sdk.ChatConfig;
import cn.wildfirechat.sdk.MessageAdmin;
import cn.wildfirechat.sdk.UserAdmin;
import cn.wildfirechat.sdk.model.IMResult;
import org.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static cn.wildfirechat.app.pojo.RestResult.RestCode.ERROR_SESSION_NOT_SCANED;
import static cn.wildfirechat.app.pojo.RestResult.RestCode.ERROR_SESSION_NOT_VERIFIED;

@org.springframework.stereotype.Service
public class LoginServiceImpl implements LoginService {
    @Autowired
    private UserMapper userMapper;

    static class Count {
        long count;
        long startTime;

        void reset() {
            count = 1;
            startTime = System.currentTimeMillis();
        }

        boolean increaseAndCheck() {
            long now = System.currentTimeMillis();
            if (now - startTime > 86400000) {
                reset();
                return true;
            }
            count++;
            if (count > 10) {
                return false;
            }
            return true;
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(LoginServiceImpl.class);
    private static ConcurrentHashMap<String, Record> mRecords = new ConcurrentHashMap<>();
    private static ConcurrentHashMap<String, PCSession> mPCSession = new ConcurrentHashMap<>();

    private static ConcurrentHashMap<String, Count> mCounts = new ConcurrentHashMap<>();

    @Autowired
    private SmsService smsService;

    @Autowired
    private IMConfig mIMConfig;

    @Value("${sms.super_code}")
    private String superCode;

    @PostConstruct
    private void init() {
        ChatConfig.initAdmin(mIMConfig.admin_url, mIMConfig.admin_secret);
    }

    @Override
    public RestResult sendCode(String mobile) {
        try {
            if (!Utils.isMobile(mobile)) {
                LOG.error("Not valid mobile {}", mobile);
                return RestResult.error(RestResult.RestCode.ERROR_INVALID_MOBILE);
            }

            Record record = mRecords.get(mobile);
            if (record != null && System.currentTimeMillis() - record.getTimestamp() < 60 * 1000) {
                LOG.error("Send code over frequency. timestamp {}, now {}", record.getTimestamp(), System.currentTimeMillis());
                return RestResult.error(RestResult.RestCode.ERROR_SEND_SMS_OVER_FREQUENCY);
            }
            Count count = mCounts.get(mobile);
            if (count == null) {
                count = new Count();
                mCounts.put(mobile, count);
            }

            if (!count.increaseAndCheck()) {
                LOG.error("Count check failure, already send {} messages today", count.count);
                return RestResult.error(RestResult.RestCode.ERROR_SEND_SMS_OVER_FREQUENCY);
            }

            String code = Utils.getRandomCode(4);

            RestResult.RestCode restCode = smsService.sendCode(mobile, code);
            if (restCode == RestResult.RestCode.SUCCESS) {
                mRecords.put(mobile, new Record(code, mobile));
                return RestResult.ok(restCode);
            } else {
                return RestResult.error(restCode);
            }
        } catch (JSONException e) {
            // json解析错误
            e.printStackTrace();
        }
        return RestResult.error(RestResult.RestCode.ERROR_SERVER_ERROR);
    }

    @Override
    public RestResult login(LoginRequest request) {
        String code = request.getCode();
        String mobile = request.getMobile();
        String clientId = request.getClientId();
        RestResult checkCodeResult = checkMobileCode(code, mobile);
        if (checkCodeResult.getCode() != 0) {
            return checkCodeResult;
        }

        try {
            //使用电话号码查询用户信息。
            IMResult<InputOutputUserInfo> userResult = UserAdmin.getUserByName(mobile);

            //如果用户信息不存在，创建用户
            InputOutputUserInfo user;
            boolean isNewUser = false;
            if (userResult.getErrorCode() == ErrorCode.ERROR_CODE_NOT_EXIST) {
                LOG.info("User not exist, try to create");
                user = new InputOutputUserInfo();
                user.setName(mobile);
                user.setDisplayName(mobile);
                user.setMobile(mobile);
                IMResult<OutputCreateUser> userIdResult = UserAdmin.createUser(user);
                if (userIdResult.getErrorCode() == ErrorCode.ERROR_CODE_SUCCESS) {
                    user.setUserId(userIdResult.getResult().getUserId());
                    isNewUser = true;
                } else {
                    LOG.info("Create user failure {}", userIdResult.code);
                    return RestResult.error(RestResult.RestCode.ERROR_SERVER_ERROR);
                }
            } else if (userResult.getCode() != 0) {
                LOG.error("Get user failure {}", userResult.code);
                return RestResult.error(RestResult.RestCode.ERROR_SERVER_ERROR);
            } else {
                user = userResult.getResult();
            }

            //使用用户id获取token
            IMResult<OutputGetIMTokenData> tokenResult = UserAdmin.getUserToken(user.getUserId(), clientId);
            if (tokenResult.getErrorCode() != ErrorCode.ERROR_CODE_SUCCESS) {
                LOG.error("Get user failure {}", tokenResult.code);
                return RestResult.error(RestResult.RestCode.ERROR_SERVER_ERROR);
            }

            //返回用户id，token和是否新建
            LoginResponse response = new LoginResponse();
            response.setUserId(user.getUserId());
            response.setToken(tokenResult.getResult().getToken());
            response.setRegister(isNewUser);

            if (isNewUser) {
                sendTextMessage(user.getUserId(), mIMConfig.welcome_for_new_user);
            } else {
                sendTextMessage(user.getUserId(), mIMConfig.welcome_for_back_user);
            }

            return RestResult.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            LOG.error("Exception happens {}", e);
            return RestResult.error(RestResult.RestCode.ERROR_SERVER_ERROR);
        }
    }

    private void sendTextMessage(String toUser, String text) {
        Conversation conversation = new Conversation();
        conversation.setTarget(toUser);
        conversation.setType(ProtoConstants.ConversationType.ConversationType_Private);
        MessagePayload payload = new MessagePayload();
        payload.setType(1);
        payload.setSearchableContent(text);


        try {
            IMResult<SendMessageResult> resultSendMessage = MessageAdmin.sendMessage("admin", conversation, payload);
            if (resultSendMessage != null && resultSendMessage.getErrorCode() == ErrorCode.ERROR_CODE_SUCCESS) {
                LOG.info("send message success");
            } else {
                LOG.error("send message error {}", resultSendMessage != null ? resultSendMessage.getErrorCode().code : "unknown");
            }
        } catch (Exception e) {
            e.printStackTrace();
            LOG.error("send message error {}", e.getLocalizedMessage());
        }
        try {
            IMResult<SendMessageResult> resultSendMessage = MessageAdmin.sendMessage("customer_service", conversation, payload);
            if (resultSendMessage != null && resultSendMessage.getErrorCode() == ErrorCode.ERROR_CODE_SUCCESS) {
                LOG.info("send message success");
            } else {
                LOG.error("send message error {}", resultSendMessage != null ? resultSendMessage.getErrorCode().code : "unknown");
            }
        } catch (Exception e) {
            e.printStackTrace();
            LOG.error("send message error {}", e.getLocalizedMessage());
        }

    }


    @Override
    public RestResult createPcSession(CreateSessionRequest request) {
        PCSession session = new PCSession();
        session.setClientId(request.getClientId());
        session.setCreateDt(System.currentTimeMillis());
        session.setDuration(300 * 1000); //300 seconds

        if (StringUtils.isEmpty(request.getToken())) {
            request.setToken(UUID.randomUUID().toString());
        }

        session.setToken(request.getToken());
        mPCSession.put(request.getToken(), session);

        SessionOutput output = session.toOutput();

        return RestResult.ok(output);
    }

    @Override
    public RestResult loginWithSession(String token) {
        PCSession session = mPCSession.get(token);
        if (session != null) {
            if (session.getStatus() == 2) {
                //使用用户id获取token
                try {
                    IMResult<OutputGetIMTokenData> tokenResult = UserAdmin.getUserToken(session.getConfirmedUserId(), session.getClientId());
                    if (tokenResult.getCode() != 0) {
                        LOG.error("Get user failure {}", tokenResult.code);
                        return RestResult.error(RestResult.RestCode.ERROR_SERVER_ERROR);
                    }

                    //返回用户id，token和是否新建
                    LoginResponse response = new LoginResponse();
                    response.setUserId(session.getConfirmedUserId());
                    response.setToken(tokenResult.getResult().getToken());
                    return RestResult.ok(response);
                } catch (Exception e) {
                    e.printStackTrace();
                    return RestResult.error(RestResult.RestCode.ERROR_SERVER_ERROR);
                }
            } else {
                if (session.getStatus() == 0) {
                    return RestResult.error(ERROR_SESSION_NOT_SCANED);
                } else {
                    return RestResult.error(ERROR_SESSION_NOT_VERIFIED);
                }
            }
        } else {
            return RestResult.error(RestResult.RestCode.ERROR_SESSION_EXPIRED);
        }
    }

    @Override
    public RestResult scanPc(String token) {
        PCSession session = mPCSession.get(token);
        if (session != null) {
            SessionOutput output = session.toOutput();
            if (output.getExpired() > 0) {
                session.setStatus(1);
                output.setStatus(1);
                return RestResult.ok(output);
            } else {
                return RestResult.error(RestResult.RestCode.ERROR_SESSION_EXPIRED);
            }
        } else {
            return RestResult.error(RestResult.RestCode.ERROR_SESSION_EXPIRED);
        }
    }

    @Override
    public RestResult confirmPc(ConfirmSessionRequest request) {
        PCSession session = mPCSession.get(request.getToken());
        if (session != null) {
            SessionOutput output = session.toOutput();
            if (output.getExpired() > 0) {
                //todo 检查IMtoken，确认用户id不是冒充的
                session.setStatus(2);
                output.setStatus(2);
                session.setConfirmedUserId(request.getUser_id());
                return RestResult.ok(output);
            } else {
                return RestResult.error(RestResult.RestCode.ERROR_SESSION_EXPIRED);
            }
        } else {
            return RestResult.error(RestResult.RestCode.ERROR_SESSION_EXPIRED);
        }
    }

    @Override
    public RestResult registerByCode(LoginRequest request) {
        String code = request.getCode();
        String mobile = request.getMobile();
        String clientId = request.getClientId();
        String password = request.getPassword();
        /**
         * 参数非空校验
         */
        RestResult check = CheckParamUtil.check(request.getCode(), request.getMobile(), request.getClientId(), request.getPassword());
        if (check.getCode() != 0) {
            return check;
        }
        /**
         * 手机验证码校验
         */
        RestResult checkCodeResult = checkMobileCode(code, mobile);
        if (checkCodeResult.getCode() != 0) {
            return checkCodeResult;
        }

        try {
            //使用电话号码查询用户信息。
            IMResult<InputOutputUserInfo> userResult = UserAdmin.getUserByName(mobile);

            //如果用户信息不存在，创建用户
            InputOutputUserInfo user;
            boolean isNewUser = false;
            if (userResult.getErrorCode() == ErrorCode.ERROR_CODE_NOT_EXIST) {
                LOG.info("User not exist, try to create");
                user = new InputOutputUserInfo();
                user.setName(mobile);
                user.setDisplayName(mobile);
                user.setMobile(mobile);
                user.setPassword(password);
                IMResult<OutputCreateUser> userIdResult = UserAdmin.createUser(user);
                if (userIdResult.getErrorCode() == ErrorCode.ERROR_CODE_SUCCESS) {
                    user.setUserId(userIdResult.getResult().getUserId());
                    isNewUser = true;
                } else {
                    LOG.info("Create user failure {}", userIdResult.code);
                    return RestResult.error(RestResult.RestCode.ERROR_SERVER_ERROR);
                }
            } else if (userResult.getCode() != 0) {
                LOG.error("Get user failure {}", userResult.code);
                return RestResult.error(RestResult.RestCode.ERROR_SERVER_ERROR);
            } else {
                user = userResult.getResult();
            }

            //使用用户id获取token
            IMResult<OutputGetIMTokenData> tokenResult = UserAdmin.getUserToken(user.getUserId(), clientId);
            if (tokenResult.getErrorCode() != ErrorCode.ERROR_CODE_SUCCESS) {
                LOG.error("Get user failure {}", tokenResult.code);
                return RestResult.error(RestResult.RestCode.ERROR_SERVER_ERROR);
            }


            if (isNewUser) {
                /**
                 * 新用户，系统管理员发送欢迎信息，返回用户id，token和是否新建
                 */
                LoginResponse response = new LoginResponse();
                response.setUserId(user.getUserId());
                response.setToken(tokenResult.getResult().getToken());
                response.setRegister(isNewUser);
                sendTextMessage(user.getUserId(), mIMConfig.welcome_for_new_user);
                return RestResult.ok(response);
            } else {
                /**
                 * 否则就是老用户，返回已注册，让用户直接登录
                 */
                return RestResult.error(RestResult.RestCode.ERROR_ALREADY_REGISTER);
            }
        } catch (Exception e) {
            e.printStackTrace();
            LOG.error("Exception happens {}", e);
            return RestResult.error(RestResult.RestCode.ERROR_SERVER_ERROR);
        }
    }

    @Override
    public RestResult findPwdByCode(LoginRequest request) {
        String code = request.getCode();
        String mobile = request.getMobile();
        String password = request.getPassword();
        /**
         * 参数非空校验
         */
        RestResult check = CheckParamUtil.check(request.getCode(), request.getMobile(), request.getPassword());
        if (check.getCode() != 0) {
            return check;
        }
        /**
         * 手机验证码校验
         */
        RestResult checkCodeResult = checkMobileCode(code, mobile);
        if (checkCodeResult.getCode() != 0) {
            return checkCodeResult;
        }
        User user = userMapper.selectByMobile(mobile);
        if (user == null) {
            return RestResult.error(RestResult.RestCode.ERROR_USER_NOT_EXIST);
        }
        User newUser = new User();
        newUser.setId(user.getId());
        newUser.setPasswdMd5(MD5Util.encode(password));
        userMapper.updateByPrimaryKeySelective(newUser);
        return RestResult.ok(null);
    }

    @Override
    public RestResult loginWithPwd(LoginRequest request) {
        String mobile = request.getMobile();
        String password = request.getPassword();
        String clientId = request.getClientId();
        /**
         * 参数非空校验
         */
        RestResult check = CheckParamUtil.check(request.getMobile(), request.getClientId(), request.getPassword());
        if (check.getCode() != 0) {
            return check;
        }
        User user = userMapper.selectByMobile(mobile);
        if (!MD5Util.encode(password).equals(user.getPasswdMd5())) {
            return RestResult.error(RestResult.RestCode.ERROR_PASSWORD);
        }
        //使用用户id获取token
        IMResult<OutputGetIMTokenData> tokenResult = new IMResult<>();
        try {
            tokenResult = UserAdmin.getUserToken(user.getUid(), clientId);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (tokenResult.getErrorCode() != ErrorCode.ERROR_CODE_SUCCESS) {
            LOG.error("Get user failure {}", tokenResult.code);
            return RestResult.error(RestResult.RestCode.ERROR_SERVER_ERROR);
        }

        //返回用户id，token和是否新建
        LoginResponse response = new LoginResponse();
        response.setUserId(user.getUid());
        response.setToken(tokenResult.getResult().getToken());

        sendTextMessage(user.getUid(), mIMConfig.welcome_for_new_user);

        return RestResult.ok(response);
    }

    /**
     * 校验手机验证码是否正确
     *
     * @param code
     * @param mobile
     * @return
     */
    private RestResult checkMobileCode(String code, String mobile) {
        if (StringUtils.isEmpty(superCode) || !code.equals(superCode)) {
            Record record = mRecords.get(mobile);
            if (record == null || !record.getCode().equals(code)) {
                LOG.error("not empty or not correct");
                return RestResult.error(RestResult.RestCode.ERROR_CODE_INCORRECT);
            }
            if (System.currentTimeMillis() - record.getTimestamp() > 5 * 60 * 1000) {
                LOG.error("Code expired. timestamp {}, now {}", record.getTimestamp(), System.currentTimeMillis());
                return RestResult.error(RestResult.RestCode.ERROR_CODE_EXPIRED);
            }
        }
        return RestResult.ok(null);
    }
}

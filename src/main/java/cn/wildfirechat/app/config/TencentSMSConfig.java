package cn.wildfirechat.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@ConfigurationProperties(prefix="sms")
@PropertySource(value = "file:config/tencent_sms.properties")
public class TencentSMSConfig {
    public int appid;
    public String appkey;
    public int templateId;
    public String superCode;

    public int getAppid() {
        return appid;
    }

    public void setAppid(int appid) {
        this.appid = appid;
    }

    public String getAppkey() {
        return appkey;
    }

    public void setAppkey(String appkey) {
        this.appkey = appkey;
    }

    public int getTemplateId() {
        return templateId;
    }

    public void setTemplateId(int templateId) {
        this.templateId = templateId;
    }

    public String getSuperCode() {
        return superCode;
    }

    public void setSuperCode(String superCode) {
        this.superCode = superCode;
    }
}

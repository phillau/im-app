package cn.wildfirechat.app.util;

import sun.misc.BASE64Encoder;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class MD5Util {
    public static String encode(String s) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            BASE64Encoder base64en = new BASE64Encoder();
            String md5String = base64en.encode(md5.digest(s.getBytes("utf-8")));
            return md5String;
        }catch (Exception e){
            return "";
        }
    }
}

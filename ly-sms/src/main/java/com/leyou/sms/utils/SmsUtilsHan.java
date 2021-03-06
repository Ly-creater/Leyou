package com.leyou.sms.utils;

import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.dysmsapi.model.v20170525.SendSmsRequest;
import com.aliyuncs.dysmsapi.model.v20170525.SendSmsResponse;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.http.MethodType;
import com.aliyuncs.profile.DefaultProfile;
import com.aliyuncs.profile.IClientProfile;
import com.leyou.sms.properties.SmsProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@EnableConfigurationProperties(SmsProperties.class)
public class SmsUtilsHan {
    @Autowired
    private SmsProperties prop;
    @Autowired
    private StringRedisTemplate redisTemplate;



    //产品名称:云通信短信API产品,开发者无需替换
    static final String product = "Dysmsapi";
    //产品域名,开发者无需替换
    static final String domain = "dysmsapi.aliyuncs.com";
    private static final Long TIME_BLANK=60000L;

    //  此处需要替换成开发者自己的AK(在阿里云访问控制台寻找)
//    String accessKeyId = prop.getAccessKeyId();
//    String accessKeySecret = prop.getAccessKeySecret();

    public SendSmsResponse sendSms(String phoneNumbers, String signName, String templateCode, String templateParam) {
        //限流一分钟之内只能发送一次
        String SMS_KEY = "SMS." + phoneNumbers;
        String s = redisTemplate.opsForValue().get(SMS_KEY);
        if(s!=null){
            Long time = Long.valueOf(s);
            if (System.currentTimeMillis()-time<TIME_BLANK) {
                log.error("【错误提示】：请勿频繁拉取验证码！！");
                return null;
            }
        }
        try {
            //可自助调整超时时间
            System.setProperty("sun.net.client.defaultConnectTimeout", "10000");
            System.setProperty("sun.net.client.defaultReadTimeout", "10000");

            //初始化acsClient,暂不支持region化
            IClientProfile profile = DefaultProfile.getProfile("cn-hangzhou", prop.getAccessKeyId(), prop.getAccessKeySecret());
            DefaultProfile.addEndpoint("cn-hangzhou", "cn-hangzhou", product, domain);
            IAcsClient acsClient = new DefaultAcsClient(profile);

            //组装请求对象-具体描述见控制台-文档部分内容
            SendSmsRequest request = new SendSmsRequest();
            request.setMethod(MethodType.POST);
            //必填:待发送手机号
            request.setPhoneNumbers(phoneNumbers);
            //必填:短信签名-可在短信控制台中找到
            request.setSignName(signName);
            //必填:短信模板-可在短信控制台中找到
            request.setTemplateCode(templateCode);
            //可选:模板中的变量替换JSON串,如模板内容为"亲爱的${name},您的验证码为${code}"时,此处的值为
            request.setTemplateParam(templateParam);

                       //选填-上行短信扩展码(无特殊需求用户请忽略此字段)
            //request.setSmsUpExtendCode("90997");

            //可选:outId为提供给业务方扩展字段,最终在短信回执消息中将此值带回给调用者
            //        request.setOutId("123456");

            //hint 此处可能会抛出异常，注意catch
            SendSmsResponse sendSmsResponse = acsClient.getAcsResponse(request);
            if (!"OK".equals(sendSmsResponse.getCode())) {
                log.error("【短信发送失败】：phoneNumbers:{} ,错误原因：{}" + phoneNumbers, sendSmsResponse.getMessage());
            }
            log.info("【短信发送成功】：phone：{}"+phoneNumbers);
            redisTemplate.opsForValue().set(SMS_KEY, String.valueOf(System.currentTimeMillis()), 1, TimeUnit.MINUTES);
            return sendSmsResponse;
        } catch (ClientException e) {
            log.error("【短信发送失败】：phoneNumbers:{} 错误原因：{}" + phoneNumbers, e);
            return null;
        }
    }
}

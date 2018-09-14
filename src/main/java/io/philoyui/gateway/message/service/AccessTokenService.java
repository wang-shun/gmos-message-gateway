package io.philoyui.gateway.message.service;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.philoyui.gateway.message.domain.MessageApp;
import io.philoyui.gateway.message.exp.GmosException;
import io.philoyui.gateway.message.domain.SubscribeRequest;
import io.philoyui.gateway.message.utils.RedisConstant;
import io.philoyui.gateway.message.utils.SignUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import redis.GcacheClient;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 *
 * 用于维护各个请求的访问令牌
 *
 * Created by yangyu-ds on 2018/9/14.
 */
@Component
public class AccessTokenService {

    @Autowired
    private GcacheClient gcacheClient;

    @Autowired
    private MessageAppService messageAppService;

    private Gson gson = new GsonBuilder().create();

    /**
     *
     * 为请求应用生成访问令牌，放到缓存中
     *
     * @param subscribeRequest
     * @return
     */
    public String generateToken(SubscribeRequest subscribeRequest) {

        MessageApp messageApp = checkAndGetApp(subscribeRequest.getAppKey());

        checkSign(subscribeRequest, messageApp.getSecret());

        String token = UUID.randomUUID().toString();

        gcacheClient.setex(buildTokenRedisKey(token),5 * 60,gson.toJson(subscribeRequest));

        return token;
    }

    /**
     *
     * @param token
     * @return
     */
    private String buildTokenRedisKey(String token) {
        return RedisConstant.TOKEN_KEY + "_" + token;
    }

    /**
     *
     * 对接收到的请求进行签名
     *
     * @param subscribeRequest
     * @param secret
     * @return
     */
    private void checkSign(SubscribeRequest subscribeRequest, String secret) {

        Map<String,String> parameters = new TreeMap<>();
        parameters.put("appKey",subscribeRequest.getAppKey());
        parameters.put("groupName",subscribeRequest.getGroupName());
        parameters.put("version",subscribeRequest.getVersion());
        parameters.put("timestamp",String.valueOf(subscribeRequest.getTimestamp()));
        String serverSign = SignUtils.sign(parameters,secret);
        if(subscribeRequest.getSign().equalsIgnoreCase(serverSign)){
            throw new GmosException("签名错误，可能是appKey或secret信息不正确");
        }

    }

    /**
     * 校验appKey是否存在
     * @param appKey
     * @return
     */
    private MessageApp checkAndGetApp(String appKey) {

        MessageApp subscribeApplication = messageAppService.findByAppKey(appKey);

        if(subscribeApplication==null){
            throw new GmosException("appKey对应的应用不存在");
        }

        return subscribeApplication;

    }

    /**
     * 解析Token为Request
     * @param token
     * @return
     */
    public SubscribeRequest resolveToken(String token) {

        String redisTokenResult = gcacheClient.get(buildTokenRedisKey(token));

        if(!Strings.isNullOrEmpty(redisTokenResult)){
            return gson.fromJson(redisTokenResult,SubscribeRequest.class);
        }

        throw new GmosException("请求token找不到" + token);
    }
}
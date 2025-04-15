package com.github.paicoding.forum.service.chatai.service.impl.qwen;

import cn.hutool.http.ContentType;
import com.github.paicoding.forum.api.model.vo.chat.ChatItemVo;
import com.github.paicoding.forum.core.util.JsonUtil;
import com.github.paicoding.forum.service.chatai.constants.ChatConstants;
import com.plexpt.chatgpt.listener.AbstractStreamListener;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 通义千问API集成
 * 负责处理与通义千问API的交互
 */
@Slf4j
@Component
public class QwenIntegration {

    @Autowired
    private QwenConf qwenConf;

    private OkHttpClient okHttpClient;

    //初始化一个OkHttpClient实例
    @PostConstruct
    public void init() {
        this.okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(qwenConf.getTimeout(), TimeUnit.SECONDS)   // 建立连接的超时时间
                .readTimeout(qwenConf.getTimeout(), TimeUnit.SECONDS)  // 建立连接后读取数据的超时时间
                .writeTimeout(qwenConf.getTimeout(), TimeUnit.SECONDS)
                .build();
    }

    //单伦
    public void streamReturn(ChatItemVo item, EventSourceListener listener) {
        // 创建一个新聊天消息对象，设置角色为用户，并填充用户的问题
        List<ChatMsg> msg = toMsg(item);
        // 执行流式聊天，传入包含用户消息的消息列表和监听器
        this.executeStreamChat(msg, listener);
    }

    /**
     * 多轮对话的场景，将历史聊天记录，传递给聊天机器人，以获取更好的结果
     *
     * @param list     包含历史聊天记录的列表，用于构建对话上下文
     * @param listener 事件源监听器，用于处理聊天机器人的响应事件
     */
    public void streamReturn(List<ChatItemVo> list, EventSourceListener listener) {
        // 构建多轮聊天的会话上下文
        List<ChatMsg> msgList = ChatConstants.toMsgList(list, this::toMsg);
        // 执行流式聊天，将构建好的对话上下文传递给聊天机器人，并监听响应事件
        this.executeStreamChat(msgList, listener);
    }

    private void executeStreamChat(ChatReq req, EventSourceListener listener) {
        // 设置请求为流式请求
        req.setStream(true);

        try {
            // 创建EventSource工厂，用于生成EventSource对象
            EventSource.Factory factory = EventSources.createFactory(okHttpClient);

            // 将聊天请求对象转换为JSON字符串
            String body = JsonUtil.toStr(req);

            // 构建请求对象，指定URL、认证头、内容类型头以及请求体
            Request request = new Request.Builder()
                    .url(qwenConf.getApiHost() + "/chat/completions")
                    .addHeader("Authorization", "Bearer " + qwenConf.getApiKey())
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(MediaType.parse(ContentType.JSON.getValue()), body))
                    .build();

            log.info(request.toString());
            // 使用工厂创建新的EventSource，并传入请求和监听器
            factory.newEventSource(request, listener);
        } catch (Exception e) {
            // 记录请求失败的日志
            // 记录请求失败的详细日志
            log.error("千问API请求异常: {}, 详细错误: {}", req, e.getMessage(), e);
            // 通知监听器发生错误
            if (listener instanceof AbstractStreamListener) {
                ((AbstractStreamListener) listener).onError(e, "请求发送失败: " + e.getMessage());
            }
        }
    }

    private void executeStreamChat(List<ChatMsg> list, EventSourceListener listener) {
        ChatReq req = new ChatReq();
        req.setModel("qwen-plus");
//        req.setModel("deepseek-chat");
        req.setMessages(list);
        this.executeStreamChat(req, listener);
    }



    @Data
    @Component
    @ConfigurationProperties(prefix = "qwen")
    public  class QwenConf {
        private String apiKey;
        private Long timeout;
        private String apiHost;
    }

    @Data
    public static class ChatReq{
        private String model;
        private boolean stream;
        private List<ChatMsg> messages;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatMsg{
        private String role;
        private String content;
    }


    public boolean directReturn(ChatItemVo item) {
        return false;
    }


    private List<ChatMsg> toMsg(ChatItemVo item) {
        List<ChatMsg> list = new ArrayList<>(2);
        if (item.getQuestion().startsWith(ChatConstants.PROMPT_TAG)) {
            // 提示词
            list.add(new ChatMsg("system", item.getQuestion().substring(ChatConstants.PROMPT_TAG.length())));
        } else {
            // 用户问答
            list.add(new ChatMsg("user", item.getQuestion()));
            if (StringUtils.isNotBlank(item.getAnswer())) {
                list.add(new ChatMsg("assistant", item.getAnswer()));
            }
        }
        return list;
    }
}

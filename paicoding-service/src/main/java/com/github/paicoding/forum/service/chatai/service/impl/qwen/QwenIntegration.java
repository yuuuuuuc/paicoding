package com.github.paicoding.forum.service.chatai.service.impl.qwen;

import cn.hutool.http.ContentType;
import cn.idev.excel.util.StringUtils;
import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.ResultCallback;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.utils.JsonUtils;
import com.beust.jcommander.Parameters;
import com.github.paicoding.forum.api.model.enums.ChatAnswerTypeEnum;
import com.github.paicoding.forum.api.model.enums.ai.AiChatStatEnum;
import com.github.paicoding.forum.api.model.vo.chat.ChatItemVo;
import com.github.paicoding.forum.api.model.vo.chat.ChatRecordsVo;
import com.github.paicoding.forum.core.util.JsonUtil;
import com.github.paicoding.forum.service.chatai.constants.ChatConstants;
import com.plexpt.chatgpt.listener.AbstractStreamListener;
import com.zhipu.oapi.service.v4.model.ChatMessageAccumulator;
import com.zhipu.oapi.service.v4.model.ModelData;
import io.reactivex.Flowable;
import jdk.internal.util.xml.impl.Input;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.annotation.Generated;
import javax.annotation.PostConstruct;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * 通义千问API集成
 * 负责处理与通义千问API的交互
 */
@Slf4j
@Setter
@Component
public class QwenIntegration {
    @Autowired
    private QwenConfig qwenConfig;

    public void streamReturn(Long user, ChatRecordsVo chatRecord, BiConsumer<AiChatStatEnum, ChatRecordsVo> callback) {
        try {
            // 获取用户的提问
            ChatItemVo item = chatRecord.getRecords().get(0);

            //阿里异步调用的实现方法
            Generation gen = new Generation();

            // 支持上下文的多轮聊天
            List<Message> userMsgList = ChatConstants.toMsgList(chatRecord.getRecords(), this::toMsg);
            // 设置请求参数
            GenerationParam param = GenerationParam.builder()
                    .apiKey(qwenConfig.getApikey())
                    .model(qwenConfig.getModel())
                    .messages(userMsgList)
                    .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                    .incrementalOutput(true)
                    .build();
            //创建一个初始值为0的信号量，用来阻塞当前线程，直到流式响应完成
            Semaphore semaphore = new Semaphore(0);
            StringBuilder fullContent = new StringBuilder();

            /**
             * 流式响应的ai生成调用方法
             * 主要实现SSE模式的数据处理
             */
            gen.streamCall(param, new ResultCallback<GenerationResult>() {
                // 处理流式返回的消息事件
                @Override
                public void onEvent(GenerationResult message) {
                    String content = message.getOutput().getChoices().get(0).getMessage().getContent();
                    fullContent.append(content);
                    log.info("Received message: {}", JsonUtils.toJson(message));
                    item.appendAnswer(content);
                    callback.accept(AiChatStatEnum.MID, chatRecord);
                }

                // 处理流式返回的结束事件
                @Override
                public void onComplete() {
                    item.setAnswerType(ChatAnswerTypeEnum.STREAM_END);
                    callback.accept(AiChatStatEnum.END, chatRecord);
                    log.info("Completed");
                    semaphore.release();// 释放信号量，允许主线程继续执行
                }

                // 处理错误
                @Override
                public void onError(Exception err) {
                    callback.accept(AiChatStatEnum.ERROR, chatRecord);
                    log.error("Exception occurred: {}", err.getMessage());
                    semaphore.release();
                }
            });
            //防止流式输出没结束，主线程就结束了
            semaphore.acquire();
            log.info("Full content: \n{}", fullContent);
        }catch (Exception e) {
            log.error("An exception occurred: {}", e.getMessage());
        }
    }



    @Data
    @Component
    @ConfigurationProperties(prefix = "qwen")
    private static class QwenConfig {
        private String apikey;
        private String apihost;
        private String model;
    }

    /**
     * 将ChatItemVo转换为阿里的Message列表
     * @param item ChatItemVo对象
     * @return Message列表
     */
    private List<Message> toMsg(ChatItemVo item) {
        //初始化容量为2的列表（用户提问和助手回答）
        List<Message> list = new ArrayList<>(2);
        //如果是提示词，创建一个角色为system的消息，直接返回只包含系统提示的列表
        if (item.getQuestion().startsWith(ChatConstants.PROMPT_TAG)) {
            // 提示词消息
            list.add(Message.builder()
                    .role(Role.SYSTEM.getValue())
                    .content(item.getQuestion().substring(ChatConstants.PROMPT_TAG.length()))
                    .build());
            return list;
        }
        // 创建一个角色为user的消息，包含用户的问题
        // 如果答案不为空，则创建一个角色为assistantistant的消息，包含助手的回答
        list.add(Message.builder().role(Role.USER.getValue()).content(item.getQuestion()).build());
        if (StringUtils.isNotBlank(item.getAnswer())) {
            list.add(Message.builder()
                    .role(Role.ASSISTANT.getValue())
                    .content(item.getAnswer())
                    .build());
        }
        return list;
    }
}

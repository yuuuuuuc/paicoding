package com.github.paicoding.forum.service.chatai.service.impl.qwen;

import cn.idev.excel.util.StringUtils;
import com.github.paicoding.forum.api.model.enums.ChatAnswerTypeEnum;
import com.github.paicoding.forum.api.model.enums.ai.AISourceEnum;
import com.github.paicoding.forum.api.model.enums.ai.AiChatStatEnum;
import com.github.paicoding.forum.api.model.vo.chat.ChatItemVo;
import com.github.paicoding.forum.api.model.vo.chat.ChatRecordsVo;
import com.github.paicoding.forum.core.util.JsonUtil;
import com.github.paicoding.forum.service.chatai.service.AbsChatService;
import com.plexpt.chatgpt.listener.AbstractStreamListener;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.sse.EventSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.function.BiConsumer;
@Slf4j
@Service
/**
 * Qwen 聊天接入
 *
 * 将提问通过QweIntegration进行处理传递给Qwen，然后将结果返回给用户
 */
public class QwenServiceImpl extends AbsChatService {
    @Autowired
    private QwenIntegration qwenIntegration;

    //同步交互方式的问答
    @Override
    public AiChatStatEnum doAnswer(Long user, ChatItemVo chat) {
        return null;
    }
    //异步交互方式的问答
    @Override
    public AiChatStatEnum doAsyncAnswer(Long user, ChatRecordsVo response, BiConsumer<AiChatStatEnum, ChatRecordsVo> consumer) {
        // 获取问答中的最新记录，用于问答
        ChatItemVo item = response.getRecords().get(0);
        // 创建一个抽象流监听器来处理流式返回的结果
        AbstractStreamListener listener = new AbstractStreamListener() {
            // 当连接打开时的处理
            @Override
            public void onOpen(EventSource eventSource, Response response) {
                super.onOpen(eventSource, response);
                if(log.isDebugEnabled()) {
                    log.debug("正确建立了连接: {}, res: {}", eventSource, response);
                }
            }

            //接收到信息进行处理
//            @Override
//            public void onMsg(String message) {
//                if (StringUtils.isNotBlank(message)) {
//                    lastMessage = message;
//                    item.appendAnswer(message);
//                    consumer.accept(AiChatStatEnum.MID, response);
//                }
//            }
            @Override
            public void onMsg(String message) {
                log.trace("Received SSE message: {}", message); // Log raw message for debugging
                if (StringUtils.isNotBlank(message) && !"[DONE]".equalsIgnoreCase(message.trim())) { // OpenAI style DONE marker, Qwen might differ
                    try {
                        // --- 解析 Qwen 返回的 SSE 消息 JSON ---
                        // 假设 Qwen 返回的 data 部分是这样的 JSON 结构 (需要根据文档调整)
                        // { "output": { "text": "...", "finish_reason": "..." }, ... }
                        com.fasterxml.jackson.databind.JsonNode rootNode = JsonUtil.toNode(message); // Assuming JsonUtil can parse to Jackson JsonNode
                        if (rootNode != null && rootNode.has("output") && rootNode.get("output").has("text")) {
                            String textChunk = rootNode.get("output").get("text").asText();
                            if (StringUtils.isNotBlank(textChunk)) {
                                // 记录上一次非空消息，用于 onClosed 判断
                                lastMessage = textChunk; // 或者记录整个 message JSON 字符串
                                item.appendAnswer(textChunk);
                                consumer.accept(AiChatStatEnum.MID, response);
                            }
                            // 可以检查 finish_reason
                            if (rootNode.get("output").has("finish_reason")) {
                                String finishReason = rootNode.get("output").get("finish_reason").asText();
                                if ("stop".equalsIgnoreCase(finishReason)) {
                                    log.info("Qwen finish_reason received: stop");
                                    // 这里可以考虑是否直接触发 onComplete 或 onClosed 逻辑，
                                    // 但通常 SSE 流会自己关闭，让 onClosed 处理更稳妥
                                } else if (finishReason != null && !"null".equalsIgnoreCase(finishReason)){
                                    log.warn("Qwen finish_reason: {}", finishReason);
                                }
                            }
                        } else {
                            log.warn("Received SSE message does not contain expected output.text: {}", message);
                        }
                        // ------------------------------------
                    } catch (Exception e) {
                        log.error("Error parsing Qwen SSE message: {}", message, e);
                        // 考虑是否需要通知错误
                        item.appendAnswer("\n[Error parsing response chunk]\n");
                        consumer.accept(AiChatStatEnum.ERROR, response);
                    }
                } else if ("[DONE]".equalsIgnoreCase(message.trim())) {
                    log.info("Received [DONE] marker.");
                    // OpenAI's convention, Qwen might not use this.
                    // If Qwen doesn't use [DONE], this branch might not be needed.
                }
            }

//            @Override
//            public void onError(Throwable throwable, String error) {
//                String errorMsg = "Error: ";
//                if (StringUtils.isNotBlank(error)) {
//                    errorMsg += error;
//                } else if (throwable != null && throwable.getMessage() != null) {
//                    errorMsg += throwable.getMessage();
//                } else {
//                    errorMsg += "Unknown error occurred";
//                }
//
//                item.appendAnswer(errorMsg)
//                        .setAnswerType(ChatAnswerTypeEnum.STREAM_END);
//                consumer.accept(AiChatStatEnum.ERROR, response);
//                log.error("Qwen API error", throwable);
//            }




            @Override
            public void onError(Throwable throwable, String error) {
                String detailedErrorMsg = "Qwen API Error.";
                if (StringUtils.isNotBlank(error)) {
                    detailedErrorMsg += " Message: " + error;
                }
                if (throwable != null) {
                    detailedErrorMsg += " Exception Type: " + throwable.getClass().getName();
                    if(throwable.getMessage() != null) {
                        detailedErrorMsg += ", Exception Message: " + throwable.getMessage();
                    }
                    log.error("Qwen API communication error. Error details: {}", detailedErrorMsg, throwable); // Log full stack trace
                } else {
                    log.error("Qwen API communication error. Error details: {}", detailedErrorMsg);
                }


                // User-facing message
                String userFriendlyError = "抱歉，与AI服务通信时出现问题，请稍后再试。";
                // 可以考虑根据特定错误类型或消息定制用户提示
                // if (throwable instanceof java.net.SocketTimeoutException) { userFriendlyError = "...超时..."; }


                // 更新状态并通知消费者
                item.appendAnswer("\n" + userFriendlyError) // 在已有内容后添加错误提示
                        .setAnswerType(ChatAnswerTypeEnum.STREAM_END); // 标记流结束
                consumer.accept(AiChatStatEnum.ERROR, response);


                // 主动关闭 EventSource (如果需要且有引用的话，但通常 listener 不持有 EventSource 引用)
                // if (eventSource != null) { eventSource.cancel(); }
            }

            @Override
            public void onClosed(EventSource eventSource) {
                if (item.getAnswerType() != ChatAnswerTypeEnum.STREAM_END) {
                    if (StringUtils.isBlank(lastMessage)) {
                        item.appendAnswer("Connection closed unexpectedly").setAnswerType(ChatAnswerTypeEnum.STREAM_END);
                        consumer.accept(AiChatStatEnum.ERROR, response);
                    } else {
                        item.appendAnswer("\n").setAnswerType(ChatAnswerTypeEnum.STREAM_END);
                        consumer.accept(AiChatStatEnum.END, response);
                    }
                }
            }
        };

        // 注册完成回调
        listener.setOnComplate(s -> {
            item.appendAnswer("\n").setAnswerType(ChatAnswerTypeEnum.STREAM_END);
            consumer.accept(AiChatStatEnum.END, response);
        });

        // 发起流式请求
        qwenIntegration.streamReturn(response.getRecords(), listener);
        return AiChatStatEnum.IGNORE;
    }

    //表明这个实现对应的AI模型
    @Override
    public AISourceEnum source() {
        return  AISourceEnum.QWEN_AI;
    }

}

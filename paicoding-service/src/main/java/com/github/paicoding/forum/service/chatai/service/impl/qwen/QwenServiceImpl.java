package com.github.paicoding.forum.service.chatai.service.impl.qwen;

import com.github.paicoding.forum.api.model.enums.ai.AISourceEnum;
import com.github.paicoding.forum.api.model.enums.ai.AiChatStatEnum;
import com.github.paicoding.forum.api.model.vo.chat.ChatItemVo;
import com.github.paicoding.forum.api.model.vo.chat.ChatRecordsVo;
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

            @Override
            public void onMsg(String s) {

            }

            @Override
            public void onError(Throwable throwable, String s) {

            }
        };

        return null;
    }

    //表明这个实现对应的AI模型
    @Override
    public AISourceEnum source() {
        return  AISourceEnum.QWEN_AI;
    }

}

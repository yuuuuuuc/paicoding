package com.github.paicoding.forum.service.chatai.service.impl.qwen;


import com.alibaba.dashscope.aigc.generation.Generation;
import com.github.paicoding.forum.api.model.enums.ai.AISourceEnum;
import com.github.paicoding.forum.api.model.enums.ai.AiChatStatEnum;
import com.github.paicoding.forum.api.model.vo.chat.ChatItemVo;
import com.github.paicoding.forum.api.model.vo.chat.ChatRecordsVo;
import com.github.paicoding.forum.service.chatai.service.AbsChatService;

import lombok.extern.slf4j.Slf4j;

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

    @Override
    public AiChatStatEnum doAnswer(Long user, ChatItemVo chat) {
        return null;
    }

    @Override
    public AiChatStatEnum doAsyncAnswer(Long user, ChatRecordsVo chatRecord, BiConsumer<AiChatStatEnum, ChatRecordsVo> consumer) {
        qwenIntegration.streamReturn(user, chatRecord, consumer);
        return AiChatStatEnum.IGNORE;
    }

    @Override
    public AISourceEnum source() {
        return AISourceEnum.QWEN_AI;
    }


}

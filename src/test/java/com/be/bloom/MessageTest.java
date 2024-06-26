package com.be.bloom;

import com.be.bloom.utils.SignitureUtils;
import org.junit.jupiter.api.Test;

public class MessageTest {

    @Test
    void messageReplaceTest() {
        String beforeMessage = "안녕, 신랑 이름! 신부 이름언니와 결혼 축하해.";
        String targetType = "신부";
        String targetName = "지은";

        String afterMessage = ("신랑".equals(targetType)) ? beforeMessage.replaceAll("신랑 이름", targetName) : beforeMessage.replaceAll("신부 이름", targetName);
        afterMessage = afterMessage.replaceAll("\\*", "").replaceAll("\\[", "").replaceAll("\\]", "");

        System.out.println("===== before =====");
        System.out.println(beforeMessage);
        System.out.println("===== after  =====");
        System.out.println(afterMessage);
    }

    @Test
    void getNowMillisTest() {
        System.out.println(System.currentTimeMillis());
    }

    @Test
    void getSignitureTest() throws Exception {
        System.out.println(System.currentTimeMillis());
        System.out.println(SignitureUtils.makeSignature(System.currentTimeMillis()+"", "GET", "/tuning/v2/tasks", "Ob1ADGptmoTwQtwD23vu", "b2Pd4Vi4Zedus0fLXLbk8Ju4BVRqpMDKXtrkGPpP"));
    }
}

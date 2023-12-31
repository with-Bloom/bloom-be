package com.be.poten.service;

import com.be.poten.domain.Message;
import com.be.poten.dto.ClovaRequestDto.ClovaRequest;
import com.be.poten.dto.ClovaRequestDto.ClovaRequestMessage;
import com.be.poten.dto.ClovaRequestDto.ClovaStudyRequest;
import com.be.poten.dto.message.MessageRequestDto;
import com.be.poten.mapper.MessageMapper;
import com.be.poten.utils.SignitureUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class MessageService {

    private final MessageMapper messageMapper;

    @Transactional
    public String executeAndGetMessage(MessageRequestDto message) {
        String clovaContent = transMessageToClovaContent(message);
        String result = postClova(message, clovaContent);
        insertMessage(Message.MessageOf(message, result));
        return result;
    }

    /**
     * message dto -> 하나의 문장으로 변환 (클로바 전달용)
     */
    private String transMessageToClovaContent(MessageRequestDto message) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n- 대상자: " + message.getTargetType());
        sb.append("\n- 대상자 이름: " + message.getTargetName());

        if(!"기타".equals(message.getRelationship())) {
            sb.append("\n- 대상자를 지칭하는 관계: " + message.getRelationship());
        }

        sb.append("\n- 축사자 이름: " + message.getUserName());
        sb.append("\n- 축사 분위기: " + message.getConcept());
        sb.append("\n- 축사에 들어갈 스토리텔링: " + message.getStory());
        sb.append("\n- 말투: " + message.getSpeechType());
        sb.append("\n- 마지막으로 해주고 싶은 말: " + message.getLastComment());
        sb.append("\n- 축사 진행 시간: " + message.getMinute());
        sb.append("\n- 최대 글자수: " + "1000자");

        return sb.toString();
    }

    private String postClova(MessageRequestDto message, String clovaContent) {
        RestTemplate template = new RestTemplate();

        // request data
        ArrayList<ClovaRequestMessage> messageList = new ArrayList();
        messageList.add(new ClovaRequestMessage("system", "항목을 입력하면 대상자에게 전달할 \\b결혼식 축사 문장을 추천 해드립니다.\\n(지정한 대상자의 이름과 지정한 축사자의 이름이 내용에 들어가야 함)"));
        messageList.add(new ClovaRequestMessage("user", clovaContent));

        ClovaRequest clovaRequest = ClovaRequest.builder()
                .messages(messageList)
                .topP(0.8)
                .topK(0)
                .maxTokens(2000)
                .temperature(0.5)
                .repeatPenalty(5.0)
                .stopBefore(new ArrayList<>())
                .includeAiFilters(true)
                .build();

        HttpHeaders headers = new HttpHeaders();
        /* 학습 */
        headers.set("X-NCP-CLOVASTUDIO-API-KEY", "NTA0MjU2MWZlZTcxNDJiY6LZd16S3lGRquDQ755+lODwkGu6Ue38A7hxsmmUpq4FftKdv/cXC7UAMEZCtJStq99tXgHVzmVUXKxgiuo8TS/CEdidD0bcFucaJKtw0OTKYOP8Mjh57nEIMzoDFYrt+ddcQM8UdpNQ4v3T5c8q/SJ7P/KeRKHEfnmFPlMBdBV5BkY/2hkXshroMxKBibtPTA==");
        headers.set("X-NCP-APIGW-API-KEY", "CNRSgHB8UsB0ReR29fU3NqjG8XRfWvifgKOhu6JY");
        /* 일반 */
//        headers.set("X-NCP-CLOVASTUDIO-API-KEY", "NTA0MjU2MWZlZTcxNDJiYzQNPMHi/Vt8f/jUw+uD7CxTg8NfmaRlPXQoigyeublY7eVnylTxPL1vEyeFjZDwRkkqeiMSeAXfdB1q9+QTAs9Z4BA6CjvC9odNIFkcQ2l6GU2dPPD8l70WoKJ7nOewsdU6Key8/+plx3IVU/6g6hv0JXRjZf89oC384tCoyzfO+IcQ6Mjw00pJLaaBRSr53bQ0lNhdj3GWf4bDr7u1aV0=");
//        headers.set("X-NCP-APIGW-API-KEY", "CNRSgHB8UsB0ReR29fU3NqjG8XRfWvifgKOhu6JY");

        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity formEntity = new HttpEntity<>(clovaRequest, headers);

        /* 학습 */
        String apiUrl = "https://clovastudio.stream.ntruss.com/testapp/v1/tasks/lmgwbobq/chat-completions";

        /* 일반 */
//        String apiUrl = "https://clovastudio.stream.ntruss.com/testapp/v1/chat-completions/HCX-002";

        LinkedHashMap resMap = template.postForObject(apiUrl, formEntity, LinkedHashMap.class);

        String code = (String) ((LinkedHashMap)resMap.get("status")).get("code");
        String msg = (String) ((LinkedHashMap)resMap.get("status")).get("message");
        String content = "";

        if("20000".equals(code)) {
            content = (String) ((LinkedHashMap)((LinkedHashMap)resMap.get("result")).get("message")).get("content");

            /* 데이터 후처리 */
            // [축사자 이름], [대상자 이름] replace
            content = content.replaceAll("\\[축사자 이름\\]", message.getUserName());
            content = content.replaceAll("\\[대상자 이름\\]", message.getTargetName());
            content = content.replaceAll("\\*","").replaceAll("축사 시작","").replaceAll("축사 종료","").replaceAll("제목:","").replaceAll("\\[","").replaceAll("\\]","");

            log.info("축사 응답 데이터: " + content);

        }else {
            content = "문장을 열심히 학습중이예요. 다음에 다시 시도 해주세요.";
            log.info("NCP error code: " + code + ", message: " + msg);
        }

        return content;
    }

    private void insertMessage(Message message) {

        messageMapper.insertMessage(message);

    }

    /**
     * 클로바 학습 api (미완성)
     */
    @Transactional
    public void executeClovaStudy() throws Exception {
        RestTemplate template = new RestTemplate();

        File file = new File("src/main/resources/clova_data.csv");

        ClovaStudyRequest clovaRequest = ClovaStudyRequest.builder()
                .name("test")
                .model("HCX-002")
                .method("LoRA")
                .taskType("GENERATION")
                .trainEpochs("4")
                .learningRate("1e-4f")
                .trainingDataset(file)
                .build();

        // File to MultiPartFile
        //File 인스턴스를 생성할 때 매개변수는 파일을 포함한 파일의 경로
//        File file = new File("src/main/resources/clova_data.csv");
//        FileItem fileItem = new DiskFileItem("file", Files.probeContentType(file.toPath()), false, file.getName(), (int) file.length(), file.getParentFile());
//
//        InputStream inputStream = new FileInputStream(file);
//        OutputStream outputStream = fileItem.getOutputStream();
//        IOUtils.copy(inputStream, outputStream);
//        // or IOUtils.copy(new FileInputStream(file), fileItem.getOutputStream());
//
//        MultipartFile multipartFile = new CommonsMultipartFile(fileItem);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-NCP-APIGW-TIMESTAMP", System.currentTimeMillis()+"");
        headers.set("X-NCP-IAM-ACCESS-KEY", "Ob1ADGptmoTwQtwD23vu");
//        headers.set("X-NCP-APIGW-SIGNATURE-V2", SignitureUtils.makeSignature(System.currentTimeMillis()+"", "POST", "/tuning/v2/tasks", "Ob1ADGptmoTwQtwD23vu", "b2Pd4Vi4Zedus0fLXLbk8Ju4BVRqpMDKXtrkGPpP"));
        headers.set("X-NCP-APIGW-SIGNATURE-V2", SignitureUtils.makeSignature(System.currentTimeMillis()+"", "GET", "/tuning/v2/tasks/lmgwbobq", "Ob1ADGptmoTwQtwD23vu", "b2Pd4Vi4Zedus0fLXLbk8Ju4BVRqpMDKXtrkGPpP"));
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        HttpEntity formEntity = new HttpEntity<>(clovaRequest, headers);

        String apiUrl = "https://clovastudio.apigw.ntruss.com/tuning/v2/tasks";

        LinkedHashMap resMap = template.postForObject(apiUrl, formEntity, LinkedHashMap.class);

//        String code = (String) ((LinkedHashMap)resMap.get("status")).get("code");
//        String content = "";
        System.out.println(resMap.toString());

    }

}
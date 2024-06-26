package com.be.bloom.service;

import com.be.bloom.common.exception.BaseException;
import com.be.bloom.domain.Message;
import com.be.bloom.dto.ClovaRequestDto.ClovaRequest;
import com.be.bloom.dto.ClovaRequestDto.ClovaRequestMessage;
import com.be.bloom.dto.ClovaRequestDto.ClovaStudyRequest;
import com.be.bloom.dto.ClovaRequestDto.PostClovaResponseDto;
import com.be.bloom.dto.message.MessageRequestDto;
import com.be.bloom.dto.message.GetMessageResponseDto;
import com.be.bloom.dto.message.PostMessageResponseDto;
import com.be.bloom.dto.message.UpdateMessageRequestDto;
import com.be.bloom.mapper.MessageMapper;
import com.be.bloom.utils.SignitureUtils;
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
    public PostMessageResponseDto executeAndGetMessage(MessageRequestDto message) {
        // 문장 생성
        String clovaContent = transMessageToClovaContent(message);
        PostClovaResponseDto result = postClova(message, clovaContent);

        // 생성 문장 저장
        String shareKey = messageMapper.getMessageShareKey();
        Message messageEntity = Message.MessageOf(message, shareKey, result);
        insertMessage(messageEntity);

        // 응답
        PostMessageResponseDto res = PostMessageResponseDto.PostMessageResponseDtoOf(messageEntity);
        return res;
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

    private PostClovaResponseDto postClova(MessageRequestDto message, String clovaContent) {
        RestTemplate template = new RestTemplate();

        // result Data
        PostClovaResponseDto result = new PostClovaResponseDto();
        result.setClovaContent(clovaContent);

        // request data
        ArrayList<ClovaRequestMessage> messageList = new ArrayList();
        messageList.add(new ClovaRequestMessage("system", "항목을 입력하면 대상자에게 전달할 결혼식 축사 문장을 추천 해드립니다. (지정한 대상자의 이름과 지정한 축사자의 이름이 내용에 들어가야 함)"));
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
        headers.set("X-NCP-CLOVASTUDIO-API-KEY", "NTA0MjU2MWZlZTcxNDJiYyV5UStnvBhxSgA17dI9+O9yqroI83RKeHVz4JtXgJIt");
        headers.set("X-NCP-APIGW-API-KEY", "CNRSgHB8UsB0ReR29fU3NqjG8XRfWvifgKOhu6JY");

        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity formEntity = new HttpEntity<>(clovaRequest, headers);

        /* 학습 */
        String apiUrl = "https://clovastudio.stream.ntruss.com/testapp/v1/tasks/rdu0q9cj/chat-completions";

        LinkedHashMap resMap = template.postForObject(apiUrl, formEntity, LinkedHashMap.class);

        String code = (String) ((LinkedHashMap)resMap.get("status")).get("code");
        String msg = (String) ((LinkedHashMap)resMap.get("status")).get("message");
        String beforeContent = "";
        String afterContent = "";

        if("20000".equals(code)) {
            /* 최초 데이터 */
            beforeContent = (String) ((LinkedHashMap)((LinkedHashMap)resMap.get("result")).get("message")).get("content");
            result.setResultRowData(beforeContent);

            /* 데이터 후처리 */
            // [축사자 이름], [대상자 이름], 대상자 replace
            afterContent = beforeContent;

            // 축사자 이름
            afterContent = afterContent.replaceAll("축사자 이름", message.getUserName());
            // 대상자 이름
            afterContent = afterContent.replaceAll("대상자 이름", message.getTargetName()).replaceAll("대상자", message.getTargetName());
            afterContent = ("신랑".equals(message.getTargetType())) ? afterContent.replaceAll("신랑 이름", message.getTargetName()) : afterContent.replaceAll("신부 이름", message.getTargetName());

            // 제거 처리: *, 축사 시작, 축사 종료, 축사 끝, 제목:, [, ], (문자열), \\b, 앞뒤공백
            afterContent = afterContent.replaceAll("\\*","").replaceAll("축사 시작","").replaceAll("축사 종료","").replaceAll("축사 끝","").replaceAll("제목:","").replaceAll("\\[","").replaceAll("\\]","").replaceAll("\\s*\\([^\\)]+\\)", "").replaceAll("\\\\b", "").trim();
            result.setResultData(afterContent);

            log.info("축사 최종 응답 데이터: " + afterContent);

        }else {
            throw new BaseException("[clovaX error] code:" + code + ", message: " + msg);
        }

        return result;
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

    /**
     * 메세지 조회 (id 기준)
     */
    public GetMessageResponseDto getMessage(String messageId) {
        return messageMapper.getMessage(messageId);
    }

    /**
     * 메세지 조회 (공유키 기준)
     */
    public GetMessageResponseDto getMessageByShareKey(String shareKey) {
        GetMessageResponseDto result = messageMapper.getMessageByShareKey(shareKey);
        if(result == null) {
            throw new BaseException("공유키가 존재하지 않습니다.");
        }
        return result;
    }

    /**
     * 메세지 수정 이력 저장
     */
    @Transactional
    public void updateMessage(UpdateMessageRequestDto message) {
        messageMapper.updateMessage(Message.MessageOf(message));
    }
}
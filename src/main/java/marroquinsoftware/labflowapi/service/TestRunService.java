package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.payload.TestRunDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface TestRunService {
    List<TestRunDTO> getRunsByTest(Long testId);
    List<TestRunDTO> getRunsByOrder(Long orderId);
    TestRunDTO addRunToTest(Long testId, TestRunDTO dto);
    TestRunDTO addAttachmentRunToTest(Long testId, List<MultipartFile> files);
    TestRunDTO verifyRun(Long testId, Long runId);
    TestRunDTO deleteRun(Long testId, Long runId);
    TestRunDTO deleteAttachment(Long testId, Long runId, Long attachmentId);
}

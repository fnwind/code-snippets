package fn.demo.experiments.upload;

import lombok.Data;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Data
class UploadContext {
    private String fileName;
    private String fileHash;
    private Long fileSize;
    private Integer totalChunks;
    private Set<Integer> uploadedChunks = ConcurrentHashMap.newKeySet();
    private String filePath;
}
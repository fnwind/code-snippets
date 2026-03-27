package fn.demo.experiments.upload;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/file")
public class UploadController {

    @Value("${file.target.dir:D:/uploads}")
    private String baseDir;

    // fileHash -> context
    private static final ConcurrentHashMap<String, UploadContext> CACHE = new ConcurrentHashMap<>();

    /**
     * 上传前校验（秒传 + 断点续传）
     */
    @PostMapping("/prepare")
    public Map<String, Object> prepare(@RequestParam Long fileSize,
                                       @RequestParam String contentHash,
                                       @RequestParam String fileName,
                                       @RequestParam Integer totalChunks) {

        Map<String, Object> result = new HashMap<>();
        UploadContext context = CACHE.get(contentHash);

        // 秒传：检查文件是否已存在
        File file = new File(baseDir, safeFileName(fileName));
        if (file.exists() && file.length() == fileSize) {
            result.put("uploaded", true);
            result.put("uploadedChunks", Collections.emptyList());
            return result;
        }

        // 创建或获取上传上下文
        if (context == null) {
            context = new UploadContext();
            context.setFileHash(contentHash);
            context.setFileName(safeFileName(fileName));
            context.setFileSize(fileSize);
            context.setTotalChunks(totalChunks);
            context.setFilePath(new File(baseDir, safeFileName(fileName)).getAbsolutePath());
            CACHE.put(contentHash, context);
        }

        // 返回已上传的分片信息
        result.put("uploaded", false);
        result.put("uploadedChunks", context.getUploadedChunks());
        return result;
    }

    /**
     * 上传分片
     */
    @PostMapping("/uploadChunk")
    public Boolean uploadChunk(@RequestParam("chunk") MultipartFile chunk,
                               @RequestParam String fileHash,
                               @RequestParam Integer chunkIndex,
                               @RequestParam Long start) {

        UploadContext context = CACHE.get(fileHash);
        if (context == null) {
            return false;
        }

        // 幂等：已经上传过的分片直接返回成功
        if (context.getUploadedChunks().contains(chunkIndex)) {
            return true;
        }

        File file = new File(context.getFilePath());

        try {
            // 确保目录存在
            file.getParentFile().mkdirs();

            // 同一个文件加锁，防止并发写入
            synchronized (fileHash.intern()) {
                try (RandomAccessFile raf = new RandomAccessFile(file, "rw");
                     InputStream in = chunk.getInputStream()) {

                    // 定位到分片开始位置
                    raf.seek(start);

                    // 写入分片数据
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = in.read(buffer)) != -1) {
                        raf.write(buffer, 0, len);
                    }
                }
            }

            // 标记分片已上传
            context.getUploadedChunks().add(chunkIndex);

            // 检查是否所有分片都已上传完成
            if (context.getUploadedChunks().size() == context.getTotalChunks()) {
                // 上传完成，移除上下文缓存
                CACHE.remove(fileHash);
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 防止路径穿越攻击
     */
    private String safeFileName(String fileName) {
        return Paths.get(fileName).getFileName().toString();
    }
}
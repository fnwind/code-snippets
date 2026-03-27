# 文件断点续传与分片上传

*by: TRAE*

## 核心功能

本项目实现了基于 Spring Boot 的分片上传和断点续传功能，主要包括以下特性：

1. **分片上传**：将大文件分割成多个小分片进行上传
2. **断点续传**：支持暂停和恢复上传，只上传未完成的分片
3. **秒传**：如果文件已存在，直接返回上传成功
4. **幂等性**：重复上传同一分片不会导致错误
5. **并发安全**：支持多用户并发上传

## 核心实现

### 1. 数据模型

```java
@Data
class UploadContext {
    private String fileName;       // 文件名
    private String fileHash;       // 文件哈希值
    private Long fileSize;         // 文件总大小
    private Integer totalChunks;   // 总分片数
    private Set<Integer> uploadedChunks = ConcurrentHashMap.newKeySet();  // 已上传分片索引
    private String filePath;       // 文件保存路径
}
```

### 2. 上传流程

#### 2.1 上传前准备（prepare）

```java
@PostMapping("/prepare")
public Map<String, Object> prepare(@RequestParam Long fileSize,
                                   @RequestParam String contentHash,
                                   @RequestParam String fileName,
                                   @RequestParam Integer totalChunks) {
    // 1. 检查文件是否已存在（秒传）
    File file = new File(baseDir, safeFileName(fileName));
    if (file.exists() && file.length() == fileSize) {
        return Map.of("uploaded", true, "uploadedChunks", Collections.emptyList());
    }
    
    // 2. 创建或获取上传上下文
    UploadContext context = CACHE.computeIfAbsent(contentHash, key -> {
        UploadContext ctx = new UploadContext();
        ctx.setFileHash(contentHash);
        ctx.setFileName(safeFileName(fileName));
        ctx.setFileSize(fileSize);
        ctx.setTotalChunks(totalChunks);
        ctx.setFilePath(new File(baseDir, safeFileName(fileName)).getAbsolutePath());
        return ctx;
    });
    
    // 3. 返回已上传分片信息
    return Map.of("uploaded", false, "uploadedChunks", context.getUploadedChunks());
}
```

#### 2.2 分片上传（uploadChunk）

```java
@PostMapping("/uploadChunk")
public Boolean uploadChunk(@RequestParam("chunk") MultipartFile chunk,
                           @RequestParam String fileHash,
                           @RequestParam Integer chunkIndex,
                           @RequestParam Long start) {
    // 1. 获取上传上下文
    UploadContext context = CACHE.get(fileHash);
    if (context == null) return false;
    
    // 2. 幂等性检查：已上传的分片直接返回成功
    if (context.getUploadedChunks().contains(chunkIndex)) return true;
    
    File file = new File(context.getFilePath());
    
    try {
        // 3. 确保目录存在
        file.getParentFile().mkdirs();
        
        // 4. 并发安全的文件写入
        synchronized (fileHash.intern()) {
            try (RandomAccessFile raf = new RandomAccessFile(file, "rw");
                 InputStream in = chunk.getInputStream()) {
                
                // 5. 定位到分片开始位置
                raf.seek(start);
                
                // 6. 写入分片数据
                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    raf.write(buffer, 0, len);
                }
            }
        }
        
        // 7. 标记分片已上传
        context.getUploadedChunks().add(chunkIndex);
        
        // 8. 检查是否所有分片都已上传完成
        if (context.getUploadedChunks().size() == context.getTotalChunks()) {
            // 上传完成，清理缓存
            CACHE.remove(fileHash);
        }
        
        return true;
    } catch (Exception e) {
        return false;
    }
}
```

### 3. 前端实现

```javascript
// 计算文件哈希
async function calculateFileHash(file) {
    // 简单的哈希计算实现
    return new Promise((resolve) => {
        const reader = new FileReader();
        reader.onload = function(e) {
            const buffer = e.target.result;
            let hash = 0;
            for (let i = 0; i < buffer.byteLength; i++) {
                hash = ((hash << 5) - hash) + buffer[i];
                hash = hash & hash;
            }
            resolve(Math.abs(hash).toString(16));
        };
        reader.readAsArrayBuffer(file.slice(0, 1024 * 1024));
    });
}

// 准备上传
async function prepareUpload() {
    const totalChunks = Math.ceil(file.size / chunkSize);
    const formData = new FormData();
    formData.append('fileSize', file.size);
    formData.append('contentHash', fileHash);
    formData.append('fileName', file.name);
    formData.append('totalChunks', totalChunks);
    
    const response = await fetch('/file/prepare', {
        method: 'POST',
        body: formData
    });
    
    return await response.json();
}

// 上传下一个分片
function uploadNextChunk() {
    if (isPaused || !isUploading) return;
    
    const totalChunks = Math.ceil(file.size / chunkSize);
    
    if (currentChunkIndex >= totalChunks) {
        // 上传完成
        return;
    }
    
    // 检查当前分片是否已上传
    if (uploadedChunks.includes(currentChunkIndex)) {
        currentChunkIndex++;
        uploadNextChunk();
        return;
    }
    
    // 计算分片的开始和结束位置
    const start = currentChunkIndex * chunkSize;
    const end = Math.min(start + chunkSize, file.size);
    const chunk = file.slice(start, end);
    
    // 上传分片
    const formData = new FormData();
    formData.append('chunk', chunk);
    formData.append('fileHash', fileHash);
    formData.append('chunkIndex', currentChunkIndex);
    formData.append('start', start);
    
    fetch('/file/uploadChunk', {
        method: 'POST',
        body: formData
    }).then(response => response.json())
      .then(success => {
          if (success) {
              uploadedChunks.push(currentChunkIndex);
              currentChunkIndex++;
              updateProgress((uploadedChunks.length / totalChunks) * 100);
              uploadNextChunk();
          } else {
              // 重试
              setTimeout(uploadNextChunk, 1000);
          }
      });
}
```

## 技术要点

### 1. 断点续传实现原理

- **文件唯一标识**：使用文件哈希值作为唯一标识
- **分片管理**：记录已上传的分片索引
- **随机访问**：使用 `RandomAccessFile` 实现文件的随机写入
- **断点恢复**：上传前检查已上传的分片，只上传未完成的分片

### 2. 并发安全

- **缓存机制**：使用 `ConcurrentHashMap` 存储上传上下文
- **文件锁**：使用 `synchronized (fileHash.intern())` 确保同一文件的并发写入安全
- **线程安全集合**：使用 `ConcurrentHashMap.newKeySet()` 存储已上传分片

### 3. 性能优化

- **分片大小**：默认 1MB，可以根据网络环境调整
- **批量上传**：可以扩展支持批量上传多个分片
- **进度显示**：实时显示上传进度

## 扩展建议

1. **哈希校验**：上传完成后对文件进行哈希校验
2. **分片合并**：对于需要合并的文件类型，可以在上传完成后进行合并
3. **分布式存储**：支持存储到分布式文件系统（如 MinIO、OSS 等）
4. **上传限流**：限制单用户的上传速度
5. **过期清理**：定期清理未完成的上传任务
6. **断点续传**：支持从任意位置恢复上传

## 注意事项

1. **文件哈希算法**：示例中使用的是简单的哈希算法，实际项目中应该使用更安全的哈希算法（如 MD5、SHA-256 等）
2. **路径安全**：使用 `safeFileName` 方法防止路径穿越攻击
3. **文件大小限制**：可以根据需求添加文件大小限制
4. **分片大小选择**：分片大小过大会影响上传速度，过小会增加请求次数，需要根据实际情况调整
5. **缓存管理**：示例中使用内存缓存，实际项目中可以使用 Redis 等分布式缓存
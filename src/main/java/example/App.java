package example;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.aliyun.fc.runtime.Context;
import com.aliyun.fc.runtime.StreamRequestHandler;

import javax.sound.sampled.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class App implements StreamRequestHandler {

    @Override
    public void handleRequest(InputStream inputStream, OutputStream outputStream, Context context) throws IOException {
        // 读取输入流中的请求数据
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        // 从请求中解析参数
        // 这里假设请求的格式是JSON，需要根据实际情况进行解析
        // 示例请求格式：{"inputPath":"oss://bucket/input.wav", "outputPrefix":"oss://bucket/output", "splitSizeBytes":1000000}
        // 注意：splitSizeBytes是分割的大小，单位是字节
        // 在实际使用中，可以通过阿里云函数的Event来传递参数
        // 这里简化处理，直接从请求中解析参数
        try {
            JSONObject jo = JSON.parseObject(sb.toString());
            String inputPath = jo.getString("inputPath");
            String outputPrefix = jo.getString("outputPrefix");
            long splitSizeBytes = jo.getLong("splitSizeBytes");

//            String inputPath = "/mnt/bbzn-sz/bb/103675920/1.wav";
//            String outputPrefix = "/mnt/bbzn-sz/bb/103675920/1";
//            long splitSizeBytes = 1048576;

            // 处理分割音频文件
            List<FileInfo> fileInfos = splitWaveFile(inputPath, outputPrefix, splitSizeBytes);
            System.out.println(fileInfos);

            // 将处理结果返回给调用方
            OutputStreamWriter writer = new OutputStreamWriter(outputStream);
            writer.write(JSON.toJSONString(fileInfos));
            writer.close();
        } catch (Exception e) {
            // 处理异常情况
            System.out.println("Exception:" + e.getMessage());
        }
    }

    public static List<FileInfo> splitWaveFile(String inputPath, String outputPrefix, long splitSizeBytes) throws UnsupportedAudioFileException, IOException, LineUnavailableException {

        String localPath = "/mnt/bbzn-sz/";

        List<FileInfo> fileInfos = new ArrayList<>();

        // 打开WAV文件作为音频输入流
        File inputFile = new File(localPath + inputPath);
        AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(inputFile);

        //获取音频时长，使用前提：当前环境下配有声卡，因为Clip类需要系统的能力，系统依赖声卡硬件驱动实现的
//        Clip clip = AudioSystem.getClip();
//        clip.open(audioInputStream);
//        float durationInMilliseconds = clip.getMicrosecondLength() / 1000f; //单位是微秒（1秒等于1,000,000微秒）
//        clip.stop();
//        clip.close();

        // 获取音频格式
        AudioFormat audioFormat = audioInputStream.getFormat();

        // 计算每个分割的帧数
        int frameSize = audioFormat.getFrameSize();
        long framesPerSplit = splitSizeBytes / frameSize;

        // 计算实际分割大小（考虑到帧对齐）
        long actualSplitSizeBytes = framesPerSplit * frameSize;

        // 读取和分割数据
        byte[] buffer = new byte[(int) actualSplitSizeBytes];
        long totalFrames = audioInputStream.getFrameLength();
        long framesLeft = totalFrames;
        int splitCount = (int) (framesLeft / framesPerSplit) + (framesLeft % framesPerSplit == 0 ? 0 : 1);

        // 计算时长（秒）
//        float durationInSeconds = (float) totalFrames / audioFormat.getFrameRate();

        for (int i = 1; i <= splitCount; i++) {
            long framesToRead = Math.min(framesPerSplit, framesLeft);
            int bytesRead = audioInputStream.read(buffer, 0, (int) (framesToRead * frameSize));

            if (bytesRead == -1) {
                break; // 如果没有更多数据可读，则退出循环
            }

            // 创建输出文件
            String outputPath = String.format("%s-%03d.wav", outputPrefix, i);
            AudioInputStream splitStream = new AudioInputStream(
                    new ByteArrayInputStream(buffer, 0, bytesRead),
                    audioFormat,
                    framesToRead
            );

            // 写入分割后的音频数据到文件
            try (FileOutputStream fos = new FileOutputStream(localPath + outputPath)) {
                AudioSystem.write(splitStream, AudioFileFormat.Type.WAVE, fos);
            }

            framesLeft -= framesToRead;

            // 计算时长（秒）
            float durationInSeconds = (float) framesToRead / audioFormat.getFrameRate();

            //文件信息
            FileInfo fileInfo = new FileInfo();
            fileInfo.fileUrl = outputPath;
            fileInfo.fileSize = bytesRead;
            fileInfo.fileDuration = (int)(durationInSeconds*1000);
            fileInfos.add(fileInfo);
        }

        audioInputStream.close();

        return fileInfos;
    }

    public static class FileInfo {
        /**
         * 文件地址
         */
        public String fileUrl;

        /**
         * 文件大小
         */
        public Integer fileSize;

        /**
         * 音频文件时长
         */
        public Integer fileDuration;

        @Override
        public String toString() {
            return "FileInfo{" +
                    "fileUrl=" + fileUrl +
                    ", fileSize=" + fileSize +
                    ", fileDuration=" + fileDuration +
                    '}';
        }
    }
}

package com.mathweb.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import ws.schild.jave.Encoder;
import ws.schild.jave.MultimediaObject;
import ws.schild.jave.info.MultimediaInfo;

import java.io.File;

@Service
public class VideoMetadataService {

    private static final Logger log =
            LoggerFactory.getLogger(VideoMetadataService.class);

    public long extractDuration(String filePath) {
        try {
            File file = new File(filePath);
            if (!file.exists()) return 0L;

            MultimediaObject media = new MultimediaObject(file);
            MultimediaInfo info = media.getInfo();
            long durationMs = info.getDuration();
            return durationMs / 1000; // convert to seconds

        } catch (Exception e) {
            log.error("Could not extract video duration from: {}",
                    filePath, e);
            return 0L;
        }
    }
}
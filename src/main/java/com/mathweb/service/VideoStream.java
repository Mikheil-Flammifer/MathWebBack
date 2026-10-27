package com.mathweb.service;

import org.springframework.core.io.Resource;

public record VideoStream(Resource resource, String mimeType) {}
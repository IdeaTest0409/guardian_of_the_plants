package com.example.guardianplants.service;

import com.example.guardianplants.ApiValidation;
import com.example.guardianplants.dto.ChatRequest;
import com.example.guardianplants.dto.ServerMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public class LiveImageService {

    private static final int TARGET_DATA_URL_CHARS = ApiValidation.MAX_CHAT_MESSAGE_CHARS - 20_000;
    private static final int[] MAX_DIMENSIONS = {1024, 896, 768, 640, 512};
    private static final float[] JPEG_QUALITIES = {0.82f, 0.74f, 0.66f, 0.58f};
    private static final String JPEG_PREFIX = "data:image/jpeg;base64,";

    private final ObjectMapper objectMapper;

    public LiveImageService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ChatRequest compressImages(ChatRequest request) {
        if (request == null || request.messages() == null) return request;
        List<ServerMessage> messages = request.messages().stream()
            .map(message -> new ServerMessage(message.role(), compressContent(message.content())))
            .toList();
        return new ChatRequest(request.deviceId(), request.conversationId(), messages, request.options());
    }

    public String compressDataUrl(String dataUrl) {
        if (dataUrl == null || !dataUrl.trim().startsWith("data:image/")) return dataUrl;
        String normalized = dataUrl.trim();
        if (normalized.startsWith("data:image/png")) {
            return normalized;
        }
        if (normalized.length() <= TARGET_DATA_URL_CHARS && normalized.startsWith(JPEG_PREFIX)) {
            return normalized;
        }

        try {
            DecodedImage decoded = decodeDataUrl(normalized);
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(decoded.bytes()));
            if (source == null) return normalized;

            String best = normalized;
            for (int maxDimension : MAX_DIMENSIONS) {
                BufferedImage scaled = scaleToMaxDimension(source, maxDimension);
                for (float quality : JPEG_QUALITIES) {
                    String candidate = encodeJpegDataUrl(scaled, quality);
                    if (candidate.length() < best.length()) {
                        best = candidate;
                    }
                    if (candidate.length() <= TARGET_DATA_URL_CHARS) {
                        return candidate;
                    }
                }
            }
            return best;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            return normalized;
        }
    }

    private Object compressContent(Object content) {
        if (content == null) return null;
        if (content instanceof String text) {
            return compressDataUrl(text);
        }
        if (content instanceof JsonNode node) {
            return compressContent(objectMapper.convertValue(node, Object.class));
        }
        if (content instanceof List<?> list) {
            List<Object> next = new ArrayList<>(list.size());
            for (Object item : list) {
                next.add(compressContent(item));
            }
            return next;
        }
        if (content instanceof Map<?, ?> map) {
            Map<String, Object> next = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                next.put(key, compressContent(entry.getValue()));
            }
            return next;
        }
        return content;
    }

    private DecodedImage decodeDataUrl(String dataUrl) {
        int comma = dataUrl.indexOf(',');
        if (comma < 0 || !dataUrl.substring(0, comma).contains(";base64")) {
            throw new IllegalArgumentException("image data URL must be base64");
        }
        String meta = dataUrl.substring(5, comma);
        byte[] bytes = Base64.getDecoder().decode(dataUrl.substring(comma + 1));
        return new DecodedImage(meta, bytes);
    }

    private BufferedImage scaleToMaxDimension(BufferedImage source, int maxDimension) {
        int width = source.getWidth();
        int height = source.getHeight();
        int longest = Math.max(width, height);
        if (longest <= maxDimension) {
            return toRgb(source, width, height);
        }
        double scale = (double) maxDimension / longest;
        int nextWidth = Math.max(1, (int) Math.round(width * scale));
        int nextHeight = Math.max(1, (int) Math.round(height * scale));
        return toRgb(source, nextWidth, nextHeight);
    }

    private BufferedImage toRgb(BufferedImage source, int width, int height) {
        BufferedImage rgb = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return rgb;
    }

    private String encodeJpegDataUrl(BufferedImage image, float quality) throws Exception {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(imageOutput);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return JPEG_PREFIX + Base64.getEncoder().encodeToString(output.toByteArray());
    }

    private record DecodedImage(String meta, byte[] bytes) {
    }
}

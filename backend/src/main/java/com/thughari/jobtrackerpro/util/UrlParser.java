package com.thughari.jobtrackerpro.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class UrlParser {
	
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[a-zA-Z0-9./?=&%_\\-]+");

    public static List<String> extractAndCleanUrls(String text) {
        if (text == null) return List.of();
        List<String> urls = new ArrayList<>();
        Matcher matcher = URL_PATTERN.matcher(text);
        while (matcher.find()) {
            urls.add(cleanTrackingParams(matcher.group()));
        }
        return urls.stream().distinct().collect(Collectors.toList());
    }

    private static String cleanTrackingParams(String url) {
        int qIndex = url.indexOf("?");
        return qIndex > 0 ? url.substring(0, qIndex) : url;
    }
    
    public static String trimNoise(String body) {
        if (body == null) return "";
        String[] markers = {"View similar jobs", "Unsubscribe", "©", "Help Center", "References"};
        for (String marker : markers) {
            int index = body.indexOf(marker);
            if (index > 0) body = body.substring(0, index);
        }
        return body.length() > 3000 ? body.substring(0, 3000 ) : body;
    }
}
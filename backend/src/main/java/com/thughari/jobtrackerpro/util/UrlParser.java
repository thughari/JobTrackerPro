package com.thughari.jobtrackerpro.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class UrlParser {
	
    private static final Pattern URL_PATTERN = Pattern.compile("(https?://|www\\.)[a-zA-Z0-9./?=&%_\\-+]+(?<![.,!?:;])");

    public static List<String> extractAndCleanUrls(String text) {
        if (text == null) return List.of();
        List<String> urls = new ArrayList<>();
        Matcher matcher = URL_PATTERN.matcher(text);
        while (matcher.find()) {
            String rawUrl = matcher.group();
            urls.add(processUrlByDomain(rawUrl));
        }
        return urls.stream()
                .filter(url -> !url.isBlank())
                .distinct()
                .collect(Collectors.toList());
    }


    private static String processUrlByDomain(String url) {
        String lowerUrl = url.toLowerCase();
        
        if (lowerUrl.contains("indeed.com")) {
            return url;
        }

        if (lowerUrl.contains("linkedin.com") || lowerUrl.contains("utm_") || lowerUrl.contains("ref=") || lowerUrl.contains("trk=")) {
            int qIndex = url.indexOf("?");
            return qIndex > 0 ? url.substring(0, qIndex) : url;
        }

        return url;
    }
    
    public static String trimNoise(String body) {
        if (body == null) return "";
        
        String cleanBody = body.replaceAll("(?is)<style.*?>.*?</style>", "")
                               .replaceAll("(?is)<script.*?>.*?</script>", "");

        String[] markers = {"View similar jobs", "Unsubscribe", "©", "Help Center", "References", "Privacy Policy"};
        for (String marker : markers) {
            int index = cleanBody.indexOf(marker);
            if (index > 0) cleanBody = cleanBody.substring(0, index);
        }
        
        return cleanBody.length() > 3000 ? cleanBody.substring(0, 3000) : cleanBody;
    }
}
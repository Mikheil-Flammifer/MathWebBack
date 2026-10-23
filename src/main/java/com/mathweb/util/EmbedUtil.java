package com.mathweb.util;

import com.mathweb.enums.EmbedType;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EmbedUtil {

    private EmbedUtil() {}

    public record Embed(EmbedType type, String ref) {}

    private static final Pattern DESMOS = Pattern.compile(
            "^https://www\\.desmos\\.com/calculator/([A-Za-z0-9]{5,20})(?:[/?#].*)?$");

    private static final Pattern GEOGEBRA = Pattern.compile(
            "^https://(?:www\\.)?geogebra\\.org/(?:m|classic|graphing|geometry|material/iframe/id)/"
                    + "([A-Za-z0-9]{3,20})(?:[/?#].*)?$");

    /** Returns empty if the link is not a recognised Desmos or GeoGebra link. */
    public static Optional<Embed> parse(String link) {
        if (link == null) return Optional.empty();
        String s = link.trim();

        Matcher d = DESMOS.matcher(s);
        if (d.matches()) return Optional.of(new Embed(EmbedType.DESMOS, d.group(1)));

        Matcher g = GEOGEBRA.matcher(s);
        if (g.matches()) return Optional.of(new Embed(EmbedType.GEOGEBRA, g.group(1)));

        return Optional.empty();
    }

    /** The iframe src for the frontend. */
    public static String iframeUrl(EmbedType type, String ref) {
        if (type == null || ref == null) return null;
        return switch (type) {
            case DESMOS -> "https://www.desmos.com/calculator/" + ref + "?embed";
            case GEOGEBRA -> "https://www.geogebra.org/material/iframe/id/" + ref;
        };
    }
}
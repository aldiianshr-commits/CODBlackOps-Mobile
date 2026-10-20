package com.codblackops.mobile.core.save;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Naming and rotation of save-data backups: {@code <GAMEID>_<yyyyMMdd-HHmmss>[_tag].zip}.
 */
public final class BackupPolicy {
    public static final int DEFAULT_KEEP = 15;
    private static final Pattern NAME = Pattern.compile("^([A-Z0-9]{6})_(\\d{8}-\\d{6})(?:_([a-z0-9-]+))?\\.zip$");

    private BackupPolicy() {
    }

    private static SimpleDateFormat format() {
        SimpleDateFormat f = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f;
    }

    /** @param tag optional lowercase tag such as "auto" or "pre-restore" (may be null). */
    public static String fileName(String gameId, long timeMs, String tag) {
        String base = gameId + "_" + format().format(new Date(timeMs));
        if (tag != null && !tag.isEmpty()) base += "_" + tag.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "-");
        return base + ".zip";
    }

    public static final class Entry implements Comparable<Entry> {
        public final String fileName;
        public final String gameId;
        public final long timeMs;
        public final String tag;

        Entry(String fileName, String gameId, long timeMs, String tag) {
            this.fileName = fileName;
            this.gameId = gameId;
            this.timeMs = timeMs;
            this.tag = tag;
        }

        @Override
        public int compareTo(Entry o) {
            return Long.compare(o.timeMs, timeMs); // newest first
        }
    }

    /** Parses a backup file name, or returns null if it is not one of ours. */
    public static Entry parse(String fileName) {
        if (fileName == null) return null;
        Matcher m = NAME.matcher(fileName);
        if (!m.matches()) return null;
        try {
            long t = format().parse(m.group(2)).getTime();
            return new Entry(fileName, m.group(1), t, m.group(3));
        } catch (ParseException e) {
            return null;
        }
    }

    /** Backups of {@code gameId} sorted newest first. */
    public static List<Entry> listFor(String gameId, List<String> fileNames) {
        List<Entry> out = new ArrayList<>();
        for (String n : fileNames) {
            Entry e = parse(n);
            if (e != null && e.gameId.equals(gameId)) out.add(e);
        }
        Collections.sort(out);
        return out;
    }

    /** File names that should be deleted so that only the newest {@code keep} remain. */
    public static List<String> toDelete(String gameId, List<String> fileNames, int keep) {
        List<Entry> sorted = listFor(gameId, fileNames);
        List<String> out = new ArrayList<>();
        for (int i = Math.max(0, keep); i < sorted.size(); i++) out.add(sorted.get(i).fileName);
        return out;
    }
}

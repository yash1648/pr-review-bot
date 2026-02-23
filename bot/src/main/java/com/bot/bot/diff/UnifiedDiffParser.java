package com.bot.bot.diff;

import com.bot.bot.domain.ChangeChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class UnifiedDiffParser {
    private static final Pattern FILE_HEADER = Pattern.compile("^(---|\\+\\+\\+)\\s+(.*)$");
    private static final Pattern HUNK_HEADER = Pattern.compile("^@@\\s+-\\d+(?:,\\d+)?\\s+\\+(\\d+)(?:,\\d+)?\\s+@@");
    private static final Pattern FILE_STATUS = Pattern.compile("^(---|\\+\\+\\+)\\s+([ab])/(.*)$");

    public List<ChangeChunk> parse(String diffContent) {
        List<ChangeChunk> chunks = new ArrayList<>();
        String[] lines = diffContent.split("\n");

        String currentFile = null;
        String changeType = "MODIFIED";
        int currentStartLine = 0;
        List<String> addedLines = new ArrayList<>();
        List<String> removedLines = new ArrayList<>();
        StringBuilder context = new StringBuilder();

        for (String line : lines) {
            if (line.startsWith("diff --git")) {
                // Extract filename
                Pattern p = Pattern.compile("diff --git a/(.+) b/(.+)");
                Matcher m = p.matcher(line);
                if (m.find()) {
                    currentFile = m.group(2);
                }
                continue;
            }

            if (line.startsWith("---")) {
                Pattern p = Pattern.compile("^---\\s+([ab]/)?(.*)$");
                Matcher m = p.matcher(line);
                if (m.find() && m.group(2).equals("/dev/null")) {
                    changeType = "ADDED";
                }
                continue;
            }

            if (line.startsWith("+++")) {
                Pattern p = Pattern.compile("^\\+\\+\\+\\s+([ab]/)?(.*)$");
                Matcher m = p.matcher(line);
                if (m.find() && m.group(2).equals("/dev/null")) {
                    changeType = "DELETED";
                }
                continue;
            }

            if (line.startsWith("@@")) {
                // Process previous chunk if exists
                if (currentFile != null && (!addedLines.isEmpty() || !removedLines.isEmpty())) {
                    ChangeChunk chunk = ChangeChunk.builder()
                            .filePath(currentFile)
                            .fileType(getFileType(currentFile))
                            .startLine(currentStartLine)
                            .addedLines(new ArrayList<>(addedLines))
                            .removedLines(new ArrayList<>(removedLines))
                            .changeType(changeType)
                            .context(context.toString())
                            .build();
                    chunks.add(chunk);
                }

                Matcher m = HUNK_HEADER.matcher(line);
                if (m.find()) {
                    currentStartLine = Integer.parseInt(m.group(1));
                }
                addedLines.clear();
                removedLines.clear();
                context.setLength(0);
                continue;
            }

            if (currentFile != null) {
                if (line.startsWith("+")) {
                    String content = line.substring(1);
                    addedLines.add(content);
                    context.append("+").append(content).append("\n");
                } else if (line.startsWith("-")) {
                    String content = line.substring(1);
                    removedLines.add(content);
                    context.append("-").append(content).append("\n");
                } else if (line.startsWith(" ")) {
                    String content = line.substring(1);
                    context.append(" ").append(content).append("\n");
                }
            }
        }

        // Don't forget the last chunk
        if (currentFile != null && (!addedLines.isEmpty() || !removedLines.isEmpty())) {
            ChangeChunk chunk = ChangeChunk.builder()
                    .filePath(currentFile)
                    .fileType(getFileType(currentFile))
                    .startLine(currentStartLine)
                    .addedLines(addedLines)
                    .removedLines(removedLines)
                    .changeType(changeType)
                    .context(context.toString())
                    .build();
            chunks.add(chunk);
        }

        return chunks;
    }

    private String getFileType(String filePath) {
        if (filePath == null) return "unknown";
        int lastDot = filePath.lastIndexOf('.');
        if (lastDot > 0) {
            return filePath.substring(lastDot + 1);
        }
        return "unknown";
    }
}
package com.bot.bot.diff;


import java.util.List;

/**
 * Represents a chunk of changes in a diff, including added, removed, and context lines. *
 * @param filepath
 * @param oldPath
 * @param newPath
 * @param oldStartLine
 * @param newStartLine
 * @param oldLineCount
 * @param newLineCount
 * @param addedLines
 * @param removedLines
 * @param contextLines
 * @param diffText
 * @param changeType
 */

public record ChangeChunk (
        String filepath,
        String oldPath,
        String newPath,
        int oldStartLine,
        int newStartLine,
        int oldLineCount,
        int newLineCount,
        List<String> addedLines,
        List<String> removedLines,
        List<String> contextLines,
        String diffText,
        ChangeType changeType
){
    public enum ChangeType{
        ADD, MODIFY, DELETE, RENAME
    }
    public boolean isJavaFile(){
        return filepath.toLowerCase().endsWith(".java");
    }
}

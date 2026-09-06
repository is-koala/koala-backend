package com.exskylab.koala.core.logging;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LogDiagnosabilityTest {

    private static final Pattern LOG_CALL = Pattern.compile("\\blog\\.(trace|debug|info|warn|error)\\s*\\(");
    private static final Pattern CATCH_CLAUSE = Pattern.compile("\\bcatch\\s*\\(");
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");
    private static final Pattern THROWABLE_NAME =
            Pattern.compile("(?i)^(e|ex|t|.*exception|.*throwable|.*error|.*cause)$");

    @Test
    void everyErrorLogInsideACatchBlockHandsTheExceptionToTheLogger() throws IOException {
        List<String> violations = new ArrayList<>();

        for (Path file : javaFiles()) {
            for (LogCall call : logCalls(file)) {
                if (!"error".equals(call.level) || call.caughtVariable == null) {
                    continue;
                }
                if (!call.caughtVariable.equals(lastArgument(call))) {
                    violations.add(describe(call));
                }
            }
        }

        assertTrue(violations.isEmpty(),
                "Bu log.error cagrilari yakalanan istisnayi logger'a vermiyor, yigin izi kayboluyor:\n  "
                        + String.join("\n  ", violations));
    }

    @Test
    void everyLogCallBindsAsManyArgumentsAsItHasPlaceholders() throws IOException {
        List<String> violations = new ArrayList<>();

        for (Path file : javaFiles()) {
            for (LogCall call : logCalls(file)) {
                if (call.arguments.isEmpty() || !call.arguments.getFirst().startsWith("\"")) {
                    continue;
                }

                int placeholders = countPlaceholders(call.arguments.getFirst());
                int bound = call.arguments.size() - 1;

                if (bound == placeholders) {
                    continue;
                }
                if (bound == placeholders + 1 && isThrowableArgument(lastArgument(call))) {
                    continue;
                }
                violations.add(describe(call) + " -> " + placeholders + " yer tutucu, " + bound + " argüman");
            }
        }

        assertTrue(violations.isEmpty(),
                "Bu log cagrilarinda yer tutucu ve argüman sayisi tutmuyor:\n  " + String.join("\n  ", violations));
    }

    @Test
    void theScannerActuallySeesTheLogCallsAndTheCatchBlocks() throws IOException {
        int calls = 0;
        int inCatch = 0;
        for (Path file : javaFiles()) {
            for (LogCall call : logCalls(file)) {
                calls++;
                if (call.caughtVariable != null) {
                    inCatch++;
                }
            }
        }

        assertTrue(calls > 400, "Beklenenden az log cagrisi tarandi: " + calls);
        assertTrue(inCatch > 40, "Beklenenden az catch blogu icindeki log cagrisi tarandi: " + inCatch);
    }

    private record LogCall(Path file, int line, String level, List<String> arguments, String caughtVariable) {
    }

    private record CatchBlock(String variable, int start, int end) {
    }

    private static String describe(LogCall call) {
        return call.file.getFileName() + ":" + call.line + " log." + call.level + "("
                + String.join(", ", call.arguments).replaceAll("\\s+", " ") + ")";
    }

    private static String lastArgument(LogCall call) {
        return call.arguments.isEmpty() ? "" : call.arguments.getLast();
    }

    private static boolean isThrowableArgument(String argument) {
        return IDENTIFIER.matcher(argument).matches() && THROWABLE_NAME.matcher(argument).matches();
    }

    private static int countPlaceholders(String format) {
        int count = 0;
        int index = format.indexOf("{}");
        while (index >= 0) {
            count++;
            index = format.indexOf("{}", index + 2);
        }
        return count;
    }

    private List<LogCall> logCalls(Path file) throws IOException {
        String source = Files.readString(file, StandardCharsets.UTF_8);
        String masked = mask(source);
        List<CatchBlock> catches = catchBlocks(masked);
        List<LogCall> calls = new ArrayList<>();

        Matcher matcher = LOG_CALL.matcher(masked);
        while (matcher.find()) {
            int open = matcher.end() - 1;
            int close = matchingClose(masked, open, '(', ')');
            if (close < 0) {
                continue;
            }
            calls.add(new LogCall(file, lineOf(source, matcher.start()), matcher.group(1),
                    splitArguments(source, masked, open + 1, close),
                    enclosingCatchVariable(catches, matcher.start())));
        }
        return calls;
    }

    private static String enclosingCatchVariable(List<CatchBlock> catches, int position) {
        String variable = null;
        int narrowest = Integer.MAX_VALUE;
        for (CatchBlock block : catches) {
            if (position > block.start && position < block.end && block.end - block.start < narrowest) {
                narrowest = block.end - block.start;
                variable = block.variable;
            }
        }
        return variable;
    }

    private static List<CatchBlock> catchBlocks(String masked) {
        List<CatchBlock> blocks = new ArrayList<>();
        Matcher matcher = CATCH_CLAUSE.matcher(masked);
        while (matcher.find()) {
            int open = matcher.end() - 1;
            int close = matchingClose(masked, open, '(', ')');
            if (close < 0) {
                continue;
            }
            String declaration = masked.substring(open + 1, close).trim();
            Matcher identifiers = IDENTIFIER.matcher(declaration);
            String variable = null;
            while (identifiers.find()) {
                variable = identifiers.group();
            }
            int brace = masked.indexOf('{', close);
            int end = brace < 0 ? -1 : matchingClose(masked, brace, '{', '}');
            if (variable != null && end > 0) {
                blocks.add(new CatchBlock(variable, brace, end));
            }
        }
        return blocks;
    }

    private static List<String> splitArguments(String source, String masked, int from, int to) {
        List<String> arguments = new ArrayList<>();
        int depth = 0;
        int start = from;
        for (int i = from; i < to; i++) {
            char c = masked.charAt(i);
            if (c == '(' || c == '[' || c == '{' || c == '<') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}' || c == '>') {
                depth--;
            } else if (c == ',' && depth == 0) {
                arguments.add(source.substring(start, i).trim());
                start = i + 1;
            }
        }
        String tail = source.substring(start, to).trim();
        if (!tail.isEmpty() || !arguments.isEmpty()) {
            arguments.add(tail);
        }
        return arguments;
    }

    private static int matchingClose(String masked, int open, char opening, char closing) {
        int depth = 0;
        for (int i = open; i < masked.length(); i++) {
            char c = masked.charAt(i);
            if (c == opening) {
                depth++;
            } else if (c == closing) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static int lineOf(String source, int position) {
        int line = 1;
        for (int i = 0; i < position; i++) {
            if (source.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    private static String mask(String source) {
        char[] masked = source.toCharArray();
        int i = 0;
        while (i < masked.length) {
            char c = masked[i];
            if (c == '/' && i + 1 < masked.length && masked[i + 1] == '/') {
                while (i < masked.length && masked[i] != '\n') {
                    masked[i++] = ' ';
                }
            } else if (c == '/' && i + 1 < masked.length && masked[i + 1] == '*') {
                while (i < masked.length && !(masked[i] == '*' && i + 1 < masked.length && masked[i + 1] == '/')) {
                    if (masked[i] != '\n') {
                        masked[i] = ' ';
                    }
                    i++;
                }
                i = Math.min(i + 2, masked.length);
            } else if (c == '"' && i + 2 < masked.length && masked[i + 1] == '"' && masked[i + 2] == '"') {
                masked[i] = ' ';
                masked[i + 1] = ' ';
                masked[i + 2] = ' ';
                i += 3;
                while (i + 2 < masked.length
                        && !(masked[i] == '"' && masked[i + 1] == '"' && masked[i + 2] == '"')) {
                    if (masked[i] != '\n') {
                        masked[i] = ' ';
                    }
                    i++;
                }
                i = Math.min(i + 3, masked.length);
            } else if (c == '"' || c == '\'') {
                i++;
                while (i < masked.length && masked[i] != c) {
                    if (masked[i] == '\\') {
                        masked[i++] = ' ';
                    }
                    if (i < masked.length) {
                        masked[i++] = ' ';
                    }
                }
                i++;
            } else {
                i++;
            }
        }
        return new String(masked);
    }

    private List<Path> javaFiles() throws IOException {
        try (Stream<Path> files = Files.walk(Path.of("src", "main", "java"))) {
            return files.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }
    }
}

package com.commandblockeditor;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CommandParser {
    private static final Pattern NUMBER_PATTERN = Pattern.compile("-?\\d+(\\.\\d+)?");
    private static final Pattern REPEAT_PATTERN = Pattern.compile("\\d+");
    private static final Pattern EXPRESSION_PATTERN = Pattern.compile("`([^`]+)`");
    private static final Pattern VALID_EXPRESSION_PATTERN = Pattern.compile("[0-9i+\\-*/()\\s.]+");

    private CommandParser() {
    }

    public static List<ParsedCommand> toCommands(List<String> editor) {
        List<ParsedCommand> commands = new ArrayList<>();
        List<String> expandedLines = expand(editor);

        for (int index = 0; index < expandedLines.size(); index++) {
            String line = expandedLines.get(index);
            String[] tokens = line.split(":", -1);
            String command = tokens.length > 1
                    ? joinFrom(tokens, 1, ":").trim()
                    : tokens[0].trim();

            String type = index == 0 ? "impulse" : "chain";
            boolean conditional = false;
            boolean auto = index != 0;

            if (tokens[0].contains("!") && index == 0) {
                type = "repeating";
            }

            if (tokens[0].contains("?")) {
                conditional = true;
            }

            if (tokens[0].contains("@")) {
                auto = index == 0;
            }

            commands.add(new ParsedCommand(type, conditional, auto, command));
        }

        return commands;
    }

    public static List<String> toEditor(List<ParsedCommand> commands) {
        List<Group> groups = new ArrayList<>();
        Group current = null;

        for (ParsedCommand command : commands) {
            List<Double> numbers = getNumbers(command.command());

            if (current == null || !normalize(command.command()).equals(normalize(current.command.command()))) {
                current = new Group(command, numbers);
                groups.add(current);
                continue;
            }

            if (current.patterns == null) {
                current.patterns = new ArrayList<>();
                for (int i = 0; i < numbers.size(); i++) {
                    double previous = i < current.lastNumbers.size() ? current.lastNumbers.get(i) : 0;
                    current.patterns.add(new NumberPattern(previous, clean(numbers.get(i) - previous)));
                }
                current.count++;
                current.lastNumbers = numbers;
                continue;
            }

            boolean valid = true;
            for (int i = 0; i < numbers.size(); i++) {
                if (i >= current.patterns.size()) {
                    valid = false;
                    break;
                }

                NumberPattern pattern = current.patterns.get(i);
                double expected = clean(pattern.start + current.count * pattern.diff);
                if (Double.compare(numbers.get(i), expected) != 0) {
                    valid = false;
                    break;
                }
            }

            if (valid) {
                current.count++;
                current.lastNumbers = numbers;
            } else {
                current = new Group(command, numbers);
                groups.add(current);
            }
        }

        List<String> editor = new ArrayList<>();
        for (int index = 0; index < groups.size(); index++) {
            Group group = groups.get(index);
            StringBuilder flags = new StringBuilder();

            if (group.command.conditional()) {
                flags.append("?");
            }
            if ("repeating".equals(group.command.type())) {
                flags.append("!");
            }
            if ((group.command.auto() && index == 0) || (!group.command.auto() && index != 0)) {
                flags.append("@");
            }
            if (group.count > 1) {
                flags.append(group.count);
            }

            String command = replaceNumbersWithPatterns(group);
            editor.add(flags.isEmpty() ? command : flags + ": " + command);
        }

        return editor;
    }

    private static List<String> expand(List<String> editor) {
        List<String> lines = new ArrayList<>();

        for (String line : editor) {
            String[] split = line.split(":", -1);
            int repeat = getRepeat(split[0]);

            for (int i = 0; i < repeat; i++) {
                lines.add(replaceExpressions(line, i));
            }
        }

        return lines;
    }

    private static int getRepeat(String prefix) {
        Matcher matcher = REPEAT_PATTERN.matcher(prefix);
        if (!matcher.find()) {
            return 1;
        }
        return Integer.parseInt(matcher.group());
    }

    private static String replaceExpressions(String line, int iteration) {
        Matcher matcher = EXPRESSION_PATTERN.matcher(line);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String expression = matcher.group(1);
            String replacement = "";

            if (VALID_EXPRESSION_PATTERN.matcher(expression).matches()) {
                try {
                    replacement = formatNumber(clean(new ExpressionParser(expression, iteration).parse()));
                } catch (IllegalArgumentException ignored) {
                    replacement = "";
                }
            }

            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }

        matcher.appendTail(result);
        return result.toString();
    }

    private static String replaceNumbersWithPatterns(Group group) {
        Matcher matcher = NUMBER_PATTERN.matcher(group.command.command());
        StringBuilder result = new StringBuilder();
        int patternIndex = 0;

        while (matcher.find()) {
            String replacement = matcher.group();

            if (group.patterns != null && patternIndex < group.patterns.size()) {
                NumberPattern pattern = group.patterns.get(patternIndex);
                replacement = patternExpression(pattern);
            }

            patternIndex++;
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }

        matcher.appendTail(result);
        return result.toString();
    }

    private static String patternExpression(NumberPattern pattern) {
        if (Double.compare(pattern.diff, 0) == 0) {
            return formatNumber(pattern.start);
        }
        if (Double.compare(pattern.start, 0) == 0 && Double.compare(pattern.diff, 1) == 0) {
            return "`i`";
        }
        if (Double.compare(pattern.start, 0) == 0) {
            return "`i*" + formatNumber(pattern.diff) + "`";
        }
        if (Double.compare(pattern.diff, 1) == 0) {
            return "`i+" + formatNumber(pattern.start) + "`";
        }
        return "`" + formatNumber(pattern.start) + "+i*" + formatNumber(pattern.diff) + "`";
    }

    private static List<Double> getNumbers(String command) {
        List<Double> numbers = new ArrayList<>();
        Matcher matcher = NUMBER_PATTERN.matcher(command);

        while (matcher.find()) {
            numbers.add(Double.parseDouble(matcher.group()));
        }

        return numbers;
    }

    private static String normalize(String command) {
        return NUMBER_PATTERN.matcher(command).replaceAll("");
    }

    private static String joinFrom(String[] tokens, int start, String delimiter) {
        StringBuilder builder = new StringBuilder();
        for (int i = start; i < tokens.length; i++) {
            if (i > start) {
                builder.append(delimiter);
            }
            builder.append(tokens[i]);
        }
        return builder.toString();
    }

    private static double clean(double number) {
        if (!Double.isFinite(number)) {
            throw new IllegalArgumentException("Expression result is not finite");
        }
        return new BigDecimal(number, new MathContext(15)).doubleValue();
    }

    private static String formatNumber(double number) {
        return BigDecimal.valueOf(number).stripTrailingZeros().toPlainString();
    }

    public record ParsedCommand(String type, boolean conditional, boolean auto, String command) {
    }

    private static final class Group {
        private final ParsedCommand command;
        private int count = 1;
        private List<NumberPattern> patterns;
        private List<Double> lastNumbers;

        private Group(ParsedCommand command, List<Double> lastNumbers) {
            this.command = command;
            this.lastNumbers = lastNumbers;
        }
    }

    private record NumberPattern(double start, double diff) {
    }

    private static final class ExpressionParser {
        private final String expression;
        private final int iteration;
        private int position;

        private ExpressionParser(String expression, int iteration) {
            this.expression = expression;
            this.iteration = iteration;
        }

        private double parse() {
            double value = parseExpression();
            skipWhitespace();
            if (position != expression.length()) {
                throw new IllegalArgumentException("Unexpected character");
            }
            return value;
        }

        private double parseExpression() {
            double value = parseTerm();

            while (true) {
                skipWhitespace();
                if (match('+')) {
                    value += parseTerm();
                } else if (match('-')) {
                    value -= parseTerm();
                } else {
                    return value;
                }
            }
        }

        private double parseTerm() {
            double value = parseFactor();

            while (true) {
                skipWhitespace();
                if (match('*')) {
                    value *= parseFactor();
                } else if (match('/')) {
                    value /= parseFactor();
                } else {
                    return value;
                }
            }
        }

        private double parseFactor() {
            skipWhitespace();

            if (match('+')) {
                return parseFactor();
            }
            if (match('-')) {
                return -parseFactor();
            }
            if (match('(')) {
                double value = parseExpression();
                if (!match(')')) {
                    throw new IllegalArgumentException("Missing closing parenthesis");
                }
                return value;
            }
            if (match('i')) {
                return iteration;
            }

            return parseNumber();
        }

        private double parseNumber() {
            int start = position;

            while (position < expression.length()) {
                char current = expression.charAt(position);
                if (!Character.isDigit(current) && current != '.') {
                    break;
                }
                position++;
            }

            if (start == position) {
                throw new IllegalArgumentException("Expected number");
            }

            return Double.parseDouble(expression.substring(start, position));
        }

        private boolean match(char expected) {
            skipWhitespace();
            if (position < expression.length() && expression.charAt(position) == expected) {
                position++;
                return true;
            }
            return false;
        }

        private void skipWhitespace() {
            while (position < expression.length() && Character.isWhitespace(expression.charAt(position))) {
                position++;
            }
        }
    }
}

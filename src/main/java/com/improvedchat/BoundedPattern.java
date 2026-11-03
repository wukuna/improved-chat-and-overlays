package com.improvedchat;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class BoundedPattern
{
    public static final int MAX_SOURCE = 32768;
    public static final int MAX_RULES = 128;
    private static final int MAX_EXPRESSION = 1024;
    private static final int MAX_INPUT = 16384;
    private static final int MATCH_WORK = 32768;
    private static final Map<Pattern, Boolean> DISABLED = Collections.synchronizedMap(new WeakHashMap<>());
    private static volatile boolean limited;

    private BoundedPattern() { }

    public static String source(String text)
    {
        if (text == null) return "";
        if (text.length() <= MAX_SOURCE) return text;
        limited = true;
        return "";
    }

    public static String[] lines(String source)
    {
        String[] lines = source(source).split("\\R");
        if (lines.length <= MAX_RULES) return lines;
        limited = true;
        return java.util.Arrays.copyOf(lines, MAX_RULES);
    }

    public static Pattern compile(String expression, int flags)
    {
        if (expression.length() > MAX_EXPRESSION)
        {
            limited = true;
            throw new PatternSyntaxException("Expression exceeds 1024 characters", expression, -1);
        }
        return Pattern.compile(expression, flags);
    }

    public static boolean find(Pattern pattern, String text)
    {
        if (disabled(pattern)) return false;
        if (text.length() > MAX_INPUT) { limited = true; return false; }
        try
        {
            return pattern.matcher(new GuardedText(text, 0, text.length(), new Budget())).find();
        }
        catch (LimitReached | StackOverflowError ex)
        {

            DISABLED.put(pattern, Boolean.TRUE);
            limited = true;
            return false;
        }
    }

    public static boolean disabled(Pattern pattern) { return DISABLED.containsKey(pattern); }
    public static void reportLimit() { limited = true; }

    public static String warning()
    {
        return limited ? "Some custom text rules exceeded safety limits and were skipped. Simplify the rules and edit them to retry. Saved rule text is unchanged." : null;
    }

    private static final class LimitReached extends RuntimeException
    {
        LimitReached() { super(null, null, false, false); }
    }

    private static final class Budget
    {
        int remaining = MATCH_WORK;
        void use(int amount) { if ((remaining -= amount) < 0) throw new LimitReached(); }
    }

    private static final class GuardedText implements CharSequence
    {
        final String value;
        final int start, end;
        final Budget budget;
        GuardedText(String value, int start, int end, Budget budget)
        { this.value = value; this.start = start; this.end = end; this.budget = budget; }
        public int length() { budget.use(1); return end - start; }
        public char charAt(int index)
        {
            budget.use(1);
            if (index < 0 || index >= end - start) throw new IndexOutOfBoundsException();
            return value.charAt(start + index);
        }
        public CharSequence subSequence(int from, int to)
        {
            budget.use(1);
            if (from < 0 || to < from || to > end - start) throw new IndexOutOfBoundsException();
            return new GuardedText(value, start + from, start + to, budget);
        }
        public String toString() { budget.use(end - start + 1); return value.substring(start, end); }
    }
}

/*
 * Adapted portions from Clean Chat by ldavid432.
 *
 * BSD 2-Clause License
 *
 * Copyright (c) 2025, ldavid432
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.improvedchat.chatbox.clean.util;

import static com.improvedchat.chatbox.clean.util.SimpleDateFormatUtil.formatTokenToRegex;
import static com.improvedchat.chatbox.clean.util.SimpleDateFormatUtil.getExpandedSize;
import static com.improvedchat.chatbox.clean.util.SimpleDateFormatUtil.isFormatChar;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FormatterExtractor {

	@Value
	public static class FormatSegment
	{
		public String token;
		public char tokenChar;
		public int tokenCount;
		public int startIndex;
		public int endIndex;
		public String value;
	}

	@Value
	public static class ExtractionResult
	{
		String formattedOutput;
		List<FormatSegment> segments;
		String remainingText;
	}

	public static ExtractionResult createFromFormatString(String formatString)
	{
		if (!SimpleDateFormatUtil.isValidPattern(formatString)) {
			return null;
		}

		List<FormatSegment> segments = new ArrayList<>();
		StringBuilder templateOutput = new StringBuilder();
		int currentPos = 0;
		int i = 0;

		while (i < formatString.length())
		{
			char c = formatString.charAt(i);

			if (c == '\'')
			{
				i++;
				while (i < formatString.length() && formatString.charAt(i) != '\'')
				{
					templateOutput.append(formatString.charAt(i));
					currentPos++;
					i++;
				}
				i++;
				continue;
			}

			if (isFormatChar(c))
			{

				int count = 1;
				while (i + count < formatString.length() && formatString.charAt(i + count) == c)
				{
					count++;
				}

				String token = String.valueOf(c).repeat(count);
				int expandedSize = getExpandedSize(c, count);

				String placeholder = " ".repeat(expandedSize);
				int startIndex = currentPos;
				int endIndex = currentPos + expandedSize;

				templateOutput.append(placeholder);
				segments.add(new FormatSegment(token, c, count, startIndex, endIndex, null));

				currentPos = endIndex;
				i += count;
			}
			else
			{

				templateOutput.append(c);
				currentPos++;
				i++;
			}
		}

		return new ExtractionResult(templateOutput.toString(), segments, "");
	}

	public static ExtractionResult extractFromText(ExtractionResult template, String text)
	{

		String cleanedText = removeColorTags(text);

		String regex = formatToRegexFromSegments(template.segments, template.formattedOutput);

		Pattern pattern = Pattern.compile("^" + regex);
		Matcher matcher = pattern.matcher(cleanedText);

		if (matcher.find())
		{
			String formattedOutput = matcher.group(0);
			int matchedLength = formattedOutput.length();

			int originalEndPos = findOriginalPosition(text, matchedLength);

			if (originalEndPos > text.length())
			{
				originalEndPos = text.length();
			}

			String remainingText = originalEndPos < text.length() ?
				text.substring(originalEndPos) : "";

			List<FormatSegment> populatedSegments = new ArrayList<>();
			for (int i = 0; i < template.segments.size(); i++)
			{
				FormatSegment seg = template.segments.get(i);
				String value = matcher.group(i + 1);
				populatedSegments.add(new FormatSegment(seg.token, seg.tokenChar, seg.tokenCount,
					seg.startIndex, seg.endIndex, value));
			}

			return new ExtractionResult(formattedOutput, populatedSegments, remainingText);
		}

		return null;
	}

	private static int findOriginalPosition(String originalText, int cleanedPosition)
	{
		int cleanedCount = 0;
		int originalPos = 0;

		while (originalPos < originalText.length() && cleanedCount < cleanedPosition)
		{

			if (originalText.startsWith("<col=", originalPos))
			{

				int closeTag = originalText.indexOf(">", originalPos);
				if (closeTag != -1)
				{
					originalPos = closeTag + 1;
					continue;
				}
			}

			if (originalText.startsWith("</col>", originalPos))
			{
				originalPos += 6;
				continue;
			}

			cleanedCount++;
			originalPos++;
		}

		return originalPos;
	}

	private static String removeColorTags(String text)
	{

		return text.replaceAll("<col=[0-9a-fA-F]{6,8}>|</col>", "");
	}

	private static String formatToRegexFromSegments(List<FormatSegment> segments, String template)
	{
		StringBuilder regex = new StringBuilder();
		int templatePos = 0;

		for (FormatSegment seg : segments)
		{

			if (templatePos < seg.startIndex)
			{
				String literal = template.substring(templatePos, seg.startIndex);
				regex.append(Pattern.quote(literal));
			}

			String regexPart = formatTokenToRegex(seg.tokenChar, seg.tokenCount);
			regex.append("(").append(regexPart).append(")");

			templatePos = seg.endIndex;
		}

		if (templatePos < template.length())
		{
			String literal = template.substring(templatePos);
			regex.append(Pattern.quote(literal));
		}

		return regex.toString();
	}

	public interface OutputPartConsumer
	{
		void consumeSegment(FormatSegment segment);

		void consumeText(String text, int startIndex, int endIndex);
	}

	public static void iterateOutputParts(ExtractionResult result, OutputPartConsumer consumer)
	{
		List<FormatSegment> segments = result.segments;
		String output = result.formattedOutput;

		int currentPos = 0;

		for (FormatSegment segment : segments)
		{

			if (currentPos < segment.startIndex)
			{
				String literalText = output.substring(currentPos, segment.startIndex);
				consumer.consumeText(literalText, 0, literalText.length());
				currentPos = segment.startIndex;
			}

			consumer.consumeSegment(segment);
			currentPos = segment.endIndex;
		}

		if (currentPos < output.length())
		{
			String remainingText = output.substring(currentPos);
			consumer.consumeText(remainingText, 0, remainingText.length());
		}
	}
}

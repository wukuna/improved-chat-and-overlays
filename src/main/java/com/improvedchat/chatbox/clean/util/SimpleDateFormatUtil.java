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

import java.text.SimpleDateFormat;

public class SimpleDateFormatUtil
{

	static boolean isFormatChar(char c)
	{
		return c == 'y' || c == 'Y' ||
			c == 'M' ||
			c == 'w' || c == 'W' ||
			c == 'd' || c == 'D' ||
			c == 'F' ||
			c == 'E' ||
			c == 'u' ||
			c == 'a' ||
			c == 'H' || c == 'h' ||
			c == 'K' || c == 'k' ||
			c == 'm' ||
			c == 's' || c == 'S' ||
			c == 'z' || c == 'Z' ||
			c == 'X';
	}

	static String formatTokenToRegex(char token, int count)
	{
		switch (token)
		{
			case 'y':
			case 'Y':
				if (count == 2)
				{
					return "\\d{2}";
				}
				else
				{
					return "\\d{4}";
				}

			case 'M':
				if (count == 1)
				{
					return "\\d{1,2}";
				}
				if (count == 2)
				{
					return "\\d{2}";
				}
				if (count == 3)
				{
					return "[A-Z][a-z]{2}";
				}
				return "[A-Z][a-z]+";

			case 'w':
				if (count == 1)
				{
					return "\\d{1,2}";
				}
				return "\\d{2}";

			case 'W':
				return "\\d";

			case 'd':
				if (count == 1)
				{
					return "\\d{1,2}";
				}
				return "\\d{2}";

			case 'D':
				if (count == 1)
				{
					return "\\d{1,3}";
				}
				if (count == 2)
				{
					return "\\d{1,3}";
				}
				return "\\d{3}";

			case 'F':
				return "\\d";

			case 'E':
				if (count < 4)
				{
					return "[A-Z][a-z]{2}";
				}
				return "[A-Z][a-z]+";

			case 'u':
				return "\\d";

			case 'a':
				return "AM|PM";

			case 'H':
				if (count == 1)
				{
					return "\\d{1,2}";
				}
				return "\\d{2}";

			case 'h':
				if (count == 1)
				{
					return "\\d{1,2}";
				}
				return "\\d{2}";

			case 'K':
			case 'k':
				if (count == 1)
				{
					return "\\d{1,2}";
				}
				return "\\d{2}";

			case 'm':
			case 's':
				if (count == 1)
				{
					return "\\d{1,2}";
				}
				return "\\d{2}";

			case 'S':
				return "\\d{" + count + "}";

			case 'z':
				return "[A-Z]{3,4}";

			case 'Z':
				return "[+-]\\d{4}";

			case 'X':
				if (count == 1)
				{
					return "[+-]\\d{2}|Z";
				}
				if (count == 2)
				{
					return "[+-]\\d{2}:\\d{2}|Z";
				}
				return "[+-]\\d{2}:\\d{2}:\\d{2}|Z";

			default:
				return ".{1," + count + "}";
		}
	}

	static int getExpandedSize(char token, int count)
	{
		switch (token)
		{
			case 'y':
				return count == 2 ? 2 : 4;
			case 'Y':
				return 4;
			case 'M':
				if (count == 1 || count == 2)
					return 2;
				if (count == 3)
					return 3;
				return 9;
			case 'w':
				return 2;
			case 'W':
				return 1;
			case 'd':
				return 2;
			case 'D':
				return 3;
			case 'F':
				return 1;
			case 'E':
				return count < 4 ? 3 : 9;
			case 'u':
				return 1;
			case 'a':
				return 2;
			case 'H':
				return 2;
			case 'h':
				return 2;
			case 'K':
				return 2;
			case 'k':
				return 2;
			case 'm':
				return 2;
			case 's':
				return 2;
			case 'S':
				return count;
			case 'z':
				return 4;
			case 'Z':
				return 5;
			case 'X':
				return 6;
			default:
				return count;
		}
	}

	public static boolean isValidPattern(String pattern)
	{
		if (pattern == null)
		{
			return false;
		}

		try
		{
			new SimpleDateFormat(pattern);
			return true;
		}
		catch (IllegalArgumentException e)
		{
			return false;
		}
	}
}

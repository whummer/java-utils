package io.hummer.util.str;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.codec.digest.DigestUtils;

/**
 * Often used utility functions for string manipulation.
 * @author Waldemar Hummer
 */
public class StringUtil {
	
	private static final StringUtil instance = new StringUtil();

	public List<String> getMatchingLines(String textWithNewlines, String regex) {
		List<String> result = new LinkedList<String>();
		for(String s : textWithNewlines.split("\n")) {
			if(s.matches(regex)) {
				result.add(s);
			}
		}
		return result;
	}

	public List<String> extractFromMatchingLines(
			String textWithNewlines, String regex) {
		return extractFromMatchingLines(textWithNewlines, "(" + regex + ")", 1);
	}
	public List<String> extractFromMatchingLines(
			String textWithNewlines, String regex, int regexGroupID) {
		List<String> result = new LinkedList<String>();
		List<List<String>> tmpResult = extractFromMatchingLines(
				textWithNewlines, regex, new int[]{regexGroupID});
		for(List<String> t : tmpResult) {
			result.add(t.get(0));
		}
		return result;
	}
	public List<List<String>> extractFromMatchingLines(
			String textWithNewlines, String regex, int ... regexGroupIDs) {
		List<List<String>> result = new LinkedList<List<String>>();
		for(String s : textWithNewlines.split("\n")) {
			Pattern p = Pattern.compile(regex);
			Matcher m = p.matcher(s);
			while(m.find()) {
				List<String> tmpResult = new LinkedList<String>();
				result.add(tmpResult);
				for(int regexGroupID : regexGroupIDs) {
					tmpResult.add(m.group(regexGroupID));
				}
			}
		}
		return result;
	}
		
	public static StringUtil getInstance() {
		return instance;
	}

	/**
	 * Concatenate the strings in a collection into a single string,
	 * with another string as delimter. The delimiter string is inserted 
	 * between successive strings; it is not inserted before the first 
	 * string or after the last string in the collection.
	 * @param strings
	 * @param delimiter
	 * @return
	 */
	public String join(Collection<String> strings, String delimiter) {
		StringBuilder b = new StringBuilder();
		List<String> parts = new ArrayList<String>();
		for(String s : strings) {
			parts.add(s);
		}
		for(int i = 0; i < parts.size() - 1; i ++) {
			b.append(parts.get(i));
			b.append(delimiter);
		}
		if(!parts.isEmpty()) {
			b.append(parts.get(parts.size() - 1));
		}
		return b.toString();
	}

	public boolean isEmpty(String in) {
		return in == null || in.trim().isEmpty();
	}
	
	public String getUnicodeCodeForExtendedChars(String s) {
		StringBuilder sb = new StringBuilder();
		for(int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if(c <= 0x7E) {
				sb.append(c);
			} else {
				sb.append(String.format("\\u%04X", (int) c));
			}
		}
		return sb.toString();
	}
	
	public String md5(String password) {
		return DigestUtils.md5Hex(password);
	}
	
	public boolean isMD5(String test) {
		return test.matches("[a-fA-F0-9]{32}");
	}
	
	public String trimForOutput(String str, int maxLength) {
		if(str == null)
			return null;
		if(maxLength <= 0)
			return "";
		if(str.length() <= maxLength)
			return str;
		return str.substring(0, maxLength) + " ... [trimmed " + 
			(str.length() - maxLength) + " chars]";
	}

	public String trim(String value, int maxLength) {
		if(value == null)
			return null;
		if(value.length() <= maxLength)
			return value;
		return value.substring(0, maxLength) + " [...]";
	}

	public List<String> replace(String template, String placeholder, List<?> replacements) {
		List<String> result = new LinkedList<String>();
		for(Object o : replacements) {
			String replacement = "" + o;
			result.add(template.replaceAll(placeholder, replacement));
		}
		return result;
	}
	public <T> String[] replace(String template, String placeholder, T[] replacements) {
		return replace(template, placeholder, Arrays.asList(replacements)).toArray(new String[0]);
	}

	public String format(double value, int decimalDigits) {
		NumberFormat nf = NumberFormat.getInstance();
		nf.setMinimumFractionDigits(decimalDigits);
		nf.setMaximumFractionDigits(decimalDigits);
		nf.setGroupingUsed(false);
		return nf.format(value);
	}

	public String encodeUrl(String urlString) {
		try {
			return URLEncoder.encode(urlString, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}

	public String toHexCode(long number) {
		return Long.toHexString(number);
	}
	public String hex(long number) {
		return toHexCode(number);
	}

	public boolean isRelativeURL(String url) {
		return !isAbsoluteURL(url);
	}
	public boolean isAbsoluteURL(String url) {
		try {
			new URL(url);
			return true;
		} catch (Exception e) {
			return false;
		}
	}
	public String concatURLs(String relativeTo, String fileURL) {
		try {
			if(relativeTo == null) {
				return fileURL;
			}
			if(fileURL == null) {
				return relativeTo;
			}
			return new URL(new URL(relativeTo), fileURL).toExternalForm();
		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		}
	}

	public static interface StringReplacer {

		String replace(String toReplace, Map<Integer,String> regexGroups);

	}

	public String replace(String string, String searchPattern, StringReplacer replacer) {
		return replace(string, searchPattern, replacer, 0);
	}
	public String replace(String string, String searchPattern, 
			StringReplacer replacer, int regexGroup) {
		Pattern pattern = Pattern.compile(searchPattern);
		Matcher matcher = pattern.matcher(string);
		StringBuilder builder = new StringBuilder();
		int i = 0;
		while (matcher.find()) {
			Map<Integer,String> regexGroups = new HashMap<Integer,String>();
			for(int j = 0; j < matcher.groupCount(); j ++) {
				regexGroups.put(j, matcher.group(j));
			}
		    String replacement = replacer.replace(matcher.group(regexGroup), regexGroups);
		    builder.append(string.substring(i, matcher.start()));
		    if (replacement == null)
		        builder.append(matcher.group(0));
		    else
		        builder.append(replacement);
		    i = matcher.end();
		}
		builder.append(string.substring(i, string.length()));
		return builder.toString();
	}

}

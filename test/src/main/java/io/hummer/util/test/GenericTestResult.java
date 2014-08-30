/*
 * Project 'WS-Aggregation':
 * http://www.infosys.tuwien.ac.at/prototype/WS-Aggregation/
 *
 * Copyright 2010 Vienna University of Technology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.hummer.util.test;

import io.hummer.util.coll.CollectionsUtil;
import io.hummer.util.cp.ClasspathUtil;
import io.hummer.util.io.IOUtil;
import io.hummer.util.log.LogUtil;
import io.hummer.util.xml.XMLUtil;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.w3c.dom.Element;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class GenericTestResult {
	
	private static final Logger logger = LogUtil.getLogger(GenericTestResult.class);

	// to eliminate "outliers" that possibly influence the overall result
	@XmlTransient
	private int eliminateXlowestValues = 0;
	@XmlTransient
	private int eliminateXhighestValues = 0;
	@XmlTransient
	private NumberFormat f = NumberFormat.getInstance(Locale.US);
	@XmlTransient
	private CollectionsUtil collUtil = new CollectionsUtil();
	@XmlTransient
	private IOUtil ioUtil = new IOUtil();
	@XmlTransient
	private ClasspathUtil cpUtil = new ClasspathUtil();

	public static final String CMD_FLIP_AXES = "##flipaxes";
	public static final String CMD_MULTI_LINE = "##multiline";
	public static final String CMD_DRAW_LINE_THROUGH_CANDLESTICKS = "##linesThroughCandles";
	public static final String CMD_MULTIPLE_CANDLES_PER_XTICK = "##multipleCandles";
	public static final String CMD_NO_LINES_JUST_POINTS = "##onlyPoints";
	public static final String CMD_ONLY_EVERY_2ND_XTICS = "##every2ndXtics";
	public static final String CMD_ONLY_EVERY_3RD_XTICS = "##every3rdXtics";
	public static final String CMD_MULTI_AXES = "##multipleAxes";

	@XmlRootElement
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class IterationResult {

		@XmlElement(name = "entry")
		private List<Entry> entries = new LinkedList<Entry>();

		public synchronized void addEntry(String valueName, double value) {
			entries.add(new Entry(valueName, value));
		}

		public synchronized List<Entry> getEntries() {
			return entries;
		}

		public void setEntries(List<Entry> entries) {
			this.entries = entries;
		}

		public void addEntryIfDifferent(String key, double value,
				String oldKeyPattnern, GenericTestResult parent) {
			List<Double> old = parent.getValuesByPattern(oldKeyPattnern);
			//System.out.println("Old/new value '" + key + "'/'" + oldKeyPattnern + "': " + old + "/" + value);
			if(old.isEmpty() || old.get(old.size() - 1) != value) {
				addEntry(key, value);
			}
		}

		public void addEntryAndRemoveOldIfSame(
				String keyPattern, String key, double value,
				GenericTestResult parent) {
			List<String> keys = parent.getAllValueNames(keyPattern);
			addEntry(key, value);
			if(keys.size() > 2) {
				String oldKey1 = keys.get(keys.size() - 2);
				String oldKey2 = keys.get(keys.size() - 1);
				Double old1 = parent.getValue(oldKey1);
				Double old2 = parent.getValue(oldKey2);
				if(old1.equals(old2) && old2.equals(value)) {
					/* remove oldKey2 which lies in the middle between oldKey1 and key */
					removeEntry(oldKey2);
				}
			}
		}

		public void removeEntry(String name) {
			List<Entry> toRemove = new LinkedList<Entry>();
			for(Entry e : toRemove) {
				if(e.name.equals(name)) {
					toRemove.add(e);
				}
			}
			for(Entry e : toRemove) {
				entries.remove(e);
			}
		}
	}

	@XmlRootElement
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class Entry {
		@XmlElement
		private String name;
		@XmlElement
		private Double value;

		public Entry() {
		}

		public Entry(String valueName, double value) {
			this.name = valueName;
			this.value = value;
		}

		public Double getValue() {
			return value;
		}

		public void setValue(double value) {
			this.value = value;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}

	public static enum ResultType {
		MEAN, THROUGHPUT, REGRESSION
	}
	
	public static enum EliminationMethod {
		REMOVE_EACH_NTH_ENTRY, CONSIDER_VALUE_DIFFERENCES
	}

	@XmlElement
	private List<IterationResult> iterations = new LinkedList<IterationResult>();
	@XmlElement
	private long startTime;
	@XmlElement
	private long finishTime;

	public GenericTestResult() {
		f.setMaximumFractionDigits(1);
		f.setMinimumFractionDigits(1);
		f.setGroupingUsed(false);
	}
	
	public void setNumberFormat(int minimumFractionDigits, int maximumFractionDigits) {
		f.setMaximumFractionDigits(maximumFractionDigits);
		f.setMinimumFractionDigits(minimumFractionDigits);
	}
	
	public IterationResult newIteration() {
		IterationResult i = new IterationResult();
		iterations.add(i);
		return i;
	}
	
	public double getStdDeviation(String valueName) {
		List<Double> values = getValues(valueName);
		return getStdDeviation(values);
	}

	public double getStdDeviation(List<Double> values) {
		return Math.sqrt(getSampleVariance(values));
	}

	public List<Double> getStdDeviations(String namePattern, List<String> levels) {
		List<Double> result = new LinkedList<Double>();
		for(String level : levels) {
			List<Double> values = getValues(namePattern.replace("<level>", level));
			result.add(getStdDeviation(values));
		}
		return result;
	}

	public double getSampleVariance(String valueName) {
		return getSampleVariance(getValues(valueName));
	}

	public double getSampleVariance(List<Double> values) {
		double mean = getMean(values);
		double sumSquares = 0;
		for (double l : values) {
			double diff = mean - (double) l;
			sumSquares += diff * diff;
		}
		double variance = sumSquares / (double) (values.size() - 1);
		return variance;
	}

	public Double getValue(String valueNameOrPattern) {
		return getValue(valueNameOrPattern, null);
	}
	public Double getValue(String valueNameOrPattern, Double defaultIfNull) {
		List<String> names = getAllValueNames(valueNameOrPattern);
		if(names.size() != 1) {
			logger.debug("Expected a single key for pattern '" + 
					valueNameOrPattern + "', got: " + names);
			return defaultIfNull;
		}
		List<Double> values = getValues(names.get(0), true, false);
		if(values.size() != 1) {
			logger.debug("Expected a single value for key '" + 
					names.get(0) + "', got: " + values);
			return defaultIfNull;
		}
		return values.get(0);
	}

	public List<Double> getValuesByPattern(String valueNamePattern) {
		return getValues(valueNamePattern, true, true);
	}

	public List<Double> getValues(String valueName) {
		return getValues(valueName, true, false);
	}
	public List<Double> getValues(String valueNameOrPattern, boolean addZeroes) {
		return getValues(valueNameOrPattern, addZeroes, false);
	}
	public List<Double> getValues(String valueNameOrPattern, boolean addZeroes, boolean treatNameAsPattern) {
		List<Double> result = new LinkedList<Double>();
		for (IterationResult r : iterations) {
			for (Entry e : r.getEntries()) {
				if ((!treatNameAsPattern && e.getName().equals(valueNameOrPattern))
						|| (treatNameAsPattern && e.getName().matches(valueNameOrPattern))) {
					double val = e.getValue();
					if (addZeroes || val != 0.0)
						result.add(val);
				}
			}
		}
		if (result.size() > eliminateXhighestValues) {
			for (int i = 0; i < eliminateXhighestValues; i++) {
				Double max = Collections.max(result);
				result.remove(max);
			}
		}
		if (result.size() > eliminateXlowestValues) {
			for (int i = 0; i < eliminateXlowestValues; i++) {
				Double min = Collections.min(result);
				result.remove(min);
			}
		}
		return result;
	}

	public double getTotal(String valueName) {
		return getTotal(getValues(valueName));
	}

	public double getTotal(List<Double> values) {
		double total = 0;
		for (Double d : values)
			total += d;
		return total;
	}

	public long getAmount(String valueName) {
		return getValues(valueName).size();
	}

	public double getMean(String valueName) {
		return getMean(getValues(valueName));
	}

	public double getMean(List<Double> values) {
		return getTotal(values) / (double) values.size();
	}

	public double getMedian(String valueName) {
		return getMedian(getValues(valueName));
	}

	public double getMedian(List<Double> values) {
		List<Double> copy = new ArrayList<Double>(values);
		Collections.sort(copy);
		return copy.get((int) ((double) copy.size() / 2.0));
	}

	public double getMaximum(String valueName) {
		return Collections.max(getValues(valueName));
	}

	public double getMinimum(String valueName) {
		try {
			return Collections.min(getValues(valueName));
		} catch (RuntimeException e) {
			logger.warn("Unable to get minimum value for key: " + valueName);
			throw e;
		}
	}

	public String getPlottableAverages(String[] yValueNames,
			String[]... xValueNames) {
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < yValueNames.length; i++) {
			b.append(getMean(yValueNames[i]));
			for (int j = 0; j < xValueNames.length; j++) {
				b.append(" ");
				b.append(getMean(xValueNames[j][i]));
			}
			b.append("\n");
		}
		return b.toString();
	}

	public String getPlottableAveragesAllLevels(String[] xValueNames, 
			ResultType type, String ... additionalCommands) {
		List<Integer> levels = getAllLevels(xValueNames[0]);
		return getPlottableAverages(levels, xValueNames, type, additionalCommands);
	}

	public String getPlottableAveragesAllLevels(String[] xValueNames, 
			String indexColumn, ResultType type, String ... additionalCommands) {
		List<Integer> levels = getAllLevels(indexColumn);
		return getPlottableAverages(levels, xValueNames, indexColumn, type, additionalCommands);
	}

	public List<Integer> getAllLevels(String xValueName) {
		return getAllLevels(xValueName, 2000);
	}
	public List<Integer> getAllLevels(String xValueName, int maxLevel) {
		List<Integer> levels = new LinkedList<Integer>();
		boolean goOn = true;
		boolean foundOne = false;
		for(int i = 0; i < maxLevel || goOn; i ++) {
			String name = xValueName.replaceAll("<level>", "" + i);
			if(getValues(name).size() > 0) {
				levels.add(i);
				foundOne = true;
			} else if(foundOne) {
				goOn = false;
			}
		}
		return levels;
	}
	/** Deprecated: use getAllLevelIDsByPattern(..) instead! */
	@Deprecated
	public List<Integer> getAllLevelsByPattern(String pattern, int resultGroupNumber) {
		List<String> values = getAllValueNames(pattern);
		List<Integer> levels = new LinkedList<Integer>();
		for(String v : values) {
			v = v.replaceAll(pattern, "$" + resultGroupNumber);
			try {
				int lev = Integer.parseInt(v);
				if(!levels.contains(lev))
					levels.add(lev);
			} catch (NumberFormatException e) { /* swallow */ }
		}
		return levels;
	}
	
	public List<String> getAllLevelIDsByPattern(String pattern, int resultGroupNumber) {
		List<String> values = getAllValueNames(pattern);
		List<String> levels = new LinkedList<String>();
		for(String v : values) {
			v = v.replaceAll(pattern, "$" + resultGroupNumber);
			if(!levels.contains(v))
				levels.add(v);
		}
		return levels;
	}

	public void createGnuplotMulti(List<?> levels,
			String[] xValueNames, String[] titles, ResultType type, 
			String xLabel, String yLabel,
			String outFile, String ... additionalCommands) throws Exception {
		if(additionalCommands == null)
			additionalCommands = new String[0];
		List<String> levelStrings = collUtil.toStringList(levels);
		LinkedList<String> temp = new LinkedList<String>(Arrays.asList(additionalCommands));
		temp.add(CMD_MULTI_LINE);
		additionalCommands = temp.toArray(new String[0]);
		createGnuplot("etc/createImageMulti.gnuplot", levelStrings, xValueNames, 
				titles, type, xLabel, yLabel, outFile, additionalCommands);
	}

	public void createGnuplotHisto(List<?> levels,
			String[] xValueNames, String[] titles, ResultType type, 
			String xLabel, String yLabel,
			String outFile, String ... additionalCommands) throws Exception {
		if(additionalCommands == null)
			additionalCommands = new String[0];
		LinkedList<String> temp = new LinkedList<String>(Arrays.asList(additionalCommands));
		additionalCommands = temp.toArray(new String[0]);
		createGnuplot("etc/createImageHisto.gnuplot", levels, xValueNames, 
				titles, type, xLabel, yLabel, outFile, additionalCommands);
	}

	public void createGnuplot(List<?> levels,
			String[] xValueNames, String[] titles, ResultType type, 
			String xLabel, String yLabel,
			String outFile, String ... additionalCommands) throws Exception {
		String file = Arrays.asList(additionalCommands).contains(
				GenericTestResult.CMD_MULTI_LINE) ? 
						"etc/createImageMulti.gnuplot" : "etc/createImage.gnuplot";
		createGnuplot(file, levels, xValueNames, titles, type, 
				xLabel, yLabel, outFile, additionalCommands);
	}

	public void createGnuplot(String[] xValueNames,
			String[] yValueNames, String[] titles, ResultType type, 
			String xLabel, String yLabel,
			String outFile, String ... additionalCommands) throws Exception {
		String plot = getPlottableAverages(yValueNames, xValueNames, additionalCommands);
		String file = Arrays.asList(additionalCommands).contains(GenericTestResult.CMD_MULTI_LINE) ? "etc/createImageMulti.gnuplot" : "etc/createImage.gnuplot";
		createGnuplot(file, xValueNames, yValueNames, null, plot, titles, 
				type, xLabel, yLabel, outFile, additionalCommands);
	}

	public void createGnuplot(String fileName, List<?> levels,
			String[] xValueNames, String[] titles, ResultType type, 
			String xLabel, String yLabel,
			String outFile, String ... additionalCommands) throws Exception {
		String plot = getPlottableAverages(levels, xValueNames, type, additionalCommands);
		createGnuplot(fileName, xValueNames, null, levels, plot, titles, 
				type, xLabel, yLabel, outFile, additionalCommands);
	}
	public void createGnuplot(String fileName, String[] xValueNames, 
			String[] yValueNames, List<?> levels,
			String plot, String[] titles, ResultType type, 
			String xLabel, String yLabel,
			String outFile, String ... additionalCommands) throws Exception {

		List<String> xValueNamesNew = new LinkedList<String>();
		List<Integer> xValueNamesLengths = new LinkedList<Integer>();
		List<String> commandsList = new LinkedList<String>(Arrays.asList(additionalCommands));
		boolean hasCandleSticks = false;
		for(String s : xValueNames) {
			String [] split = s.split(":");
			xValueNamesLengths.add(split.length);
			hasCandleSticks |= split.length > 1;
			for(String sp : split) {
				xValueNamesNew.add(sp);
			}
		}
		if(hasCandleSticks) {
			if(levels == null) {
				plot = getPlottableAverages(yValueNames, xValueNames);
			} else {
				plot = getPlottableAverages(levels, xValueNames, type, additionalCommands);
			}
		}

		String gnuplotOriginal = null;
		if(new File(fileName).exists()) {
			gnuplotOriginal = ioUtil.readFile(fileName);
		} else {
			String file = fileName;
			if(file.contains("/"))
				file = file.substring(file.indexOf("/") + 1);
			Map<URL,String> resources = cpUtil.getSystemResources(file);
			if(resources.isEmpty()) {
				logger.warn("Unable to find system resource '" + file + "'");
			} else {
				gnuplotOriginal = resources.values().iterator().next();
			}
		}
		boolean multiline = commandsList.contains(CMD_MULTI_LINE);
		boolean drawLineCandle = commandsList.contains(CMD_DRAW_LINE_THROUGH_CANDLESTICKS);
		boolean multipleCandles = commandsList.contains(CMD_MULTIPLE_CANDLES_PER_XTICK);
		boolean multiAxes = commandsList.contains(CMD_MULTI_AXES);

		if(multiAxes) {
			for(int i = 1; i <= titles.length; i ++) {
				commandsList.add("set y" + (i > 1 ? i : "") + "tics nomirror");
			}
		}

		String additional = "";
		for(String a : commandsList) {
			if(a.startsWith("##")) {
				// do not add
			} else if(a.startsWith("#")) {
				String left = a.substring(0, a.indexOf("="));
				String right = a.substring(a.indexOf("=") + 1);
				gnuplotOriginal = gnuplotOriginal.replaceAll(left, right);
			} else { 
				additional += a + "\n";
			}
		}
		String gnuplot = gnuplotOriginal.replaceAll("#<extra>", additional);

		if(type == ResultType.REGRESSION) {
			gnuplot = gnuplot.replaceAll("#regression", "");
			gnuplot = gnuplot.replaceAll("\"gnuplot.values\" using 1:2 title '<title1>'", 
					"\"gnuplot.values\" using 1:2 title '<title1>', regrFunc(x) title 'Linear Regression' with lines");
			gnuplot = gnuplot.replaceAll("with linespoints ls 1", "");
		}

		if(commandsList.contains(CMD_FLIP_AXES)) {
			for(int i = 1; i <= titles.length; i ++) {
				gnuplot = gnuplot.replaceAll("using 1:" + (i + 1), "using " + (i + 1) + ":1");
			}
			String tmp = xLabel;
			xLabel = yLabel;
			yLabel = tmp;
		}

		if(xLabel != null)
			gnuplot = gnuplot.replaceAll("<xlabel>", xLabel);
		if(yLabel != null)
			gnuplot = gnuplot.replaceAll("<ylabel>", yLabel);

		Map<Integer,String> fillStylePatterns = collUtil.asMap(0, "4").
				entry(1, "1").entry(2, "3").entry(3, "2").entry(4, "3").
				entry(5, "4").entry(6, "5").entry(7, "7");

		for(int i = 1; i <= titles.length; i ++) {
			gnuplot = gnuplot.replaceAll("#<do" + i + ">", "");
			if(hasCandleSticks || 
					commandsList.contains(CMD_NO_LINES_JUST_POINTS) || 
					commandsList.contains(CMD_MULTI_AXES)) {

				int colIndexStart = 1 + getSublistSum(xValueNamesLengths, 0, i - 1) - xValueNamesLengths.get(i - 1) + 1;
				int colIndexEnd = 1 + getSublistSum(xValueNamesLengths, 0, i - 1);
				String colIndexes = "";
				for(int j = colIndexStart; j <= colIndexEnd; j ++) {
					colIndexes += ":" + j;
				}
				String colIndexVariable = "(\\$1)";
				if(multipleCandles)
					colIndexVariable = "(\\$1-0.5+" + ((double)i)*(1.0/(double)titles.length) + ")";
				if(colIndexStart < colIndexEnd) {
					String plotLine = "\"gnuplot.values\" using " + colIndexVariable + colIndexes + 
							" title '<title" + i + ">' with candlesticks ls " + i + " fs pattern " + fillStylePatterns.get(i);

					if(drawLineCandle)
						plotLine += ", \"gnuplot.values\" using 1:" + (colIndexStart + 2) + " title '' with lines ls " + i;

					if(multiAxes && i > 1) {
						plotLine += " axes x1y" + i;
					}

					if(multiline) {
						plotLine = "plot " + plotLine;
					} else {
						if(i < titles.length)
							plotLine += ", \\\\";
					}
					gnuplot = gnuplot.replaceAll("#<plot" + i + ">.*\\n", plotLine + "\n");
				} else {
					String plotLine = "\"gnuplot.values\" using 1" + colIndexes + " title '<title" + i + ">' with linespoints ls " + i;
					if(commandsList.contains(CMD_NO_LINES_JUST_POINTS)) {
						plotLine = plotLine.replace("with linespoints", "with points");
					}

					if(multiAxes) {
						plotLine += " axes x1y" + i;
					}

					if(multiline) {
						plotLine = "plot " + plotLine;
					} else {
						if(i < titles.length)
							plotLine += ", \\\\";
					}
					gnuplot = gnuplot.replaceAll("#<plot" + i + ">.*\\n", plotLine + "\n");
				}
			}
			gnuplot = gnuplot.replaceAll("#<plot" + i + ">", "");

			if(titles != null && titles.length >= i)
				gnuplot = gnuplot.replace("<title" + i + ">", titles[i-1]);
		}
		for(int i = titles.length + 1; i < 10; i ++) {
			gnuplot = gnuplot.replaceAll("#<do" + i + ">", "#");
		}
		System.out.println(gnuplot);

		ioUtil.saveFile("createImage.temp.gnuplot", gnuplot);
		ioUtil.saveFile("gnuplot.values", plot);
		
		Process gpProc = Runtime.getRuntime().exec("gnuplot createImage.temp.gnuplot");
		gpProc.waitFor();
		String output = ioUtil.readFile(gpProc.getInputStream());
		output += ioUtil.readFile(gpProc.getErrorStream());
		System.out.println("gnuplot output:\n------\n" + output);
		if(output != null && (output.contains("Not enough columns") || output.contains("no valid points"))) {
			System.out.println("---> Apparently, gnuplot reported an error. Input values were:");
			System.out.println(plot);
		}
		String fixbb = "etc/fixbb";
		String fixbbTmp = null;
		if(!new File(fixbb).exists()) {
			fixbb = "../" + fixbb;
			if(!new File(fixbb).exists()) {
				fixbb = "../" + fixbb;
			}
			if(!new File(fixbb).exists()) {
				Map<URL,String> resources = cpUtil.getSystemResources("fixbb");
				if(!resources.isEmpty()) {
					fixbbTmp = UUID.randomUUID().toString();
					ioUtil.saveFile(fixbbTmp, resources.values().iterator().next());
					new File(fixbbTmp).setExecutable(true);
					new File(fixbbTmp).deleteOnExit();
					fixbb = "./" + fixbbTmp;
				}
			}
		}
		Runtime.getRuntime().exec(fixbb + " graph.eps").waitFor();
		if(fixbbTmp != null) {
			new File(fixbbTmp).delete();
		}
		if(outFile.endsWith(".pdf")) {
			Runtime.getRuntime().exec("epstopdf graph.eps").waitFor();
			new File("graph.pdf").renameTo(new File(outFile));
			new File("graph.pdf").delete();
		} else {
			new File("graph.eps").renameTo(new File(outFile));
		}
		new File("graph.eps").delete();
		new File("gnuplot.values").delete();
		new File("createImage.temp.gnuplot").delete();

	}

	private int getSublistSum(List<? extends Number> values, int fromIndexInclusive, int toIndexInclusive) {
		int sum = 0;
		for(int i = fromIndexInclusive; i <= toIndexInclusive; i ++) {
			if(i >= 0 && i < values.size())
				sum += values.get(i).intValue();
		}
		return sum;
	}

	public String getPlottableAverages(List<?> levels,
			String[] xValueNames, ResultType type, String ... additionalCommands) {
		return getPlottableAverages(levels, xValueNames, null, type, additionalCommands);
	}
	
	public String getPlottableAverages(List<?> levels,
			String[] xValueNames, String indexColumn, 
			ResultType type, String ... additionalCommands) {
		StringBuilder b = new StringBuilder();
		NumberFormat f = NumberFormat.getInstance(Locale.US);
		f.setMinimumFractionDigits(3);
		f.setMaximumFractionDigits(3);
		f.setGroupingUsed(false);
		
		List<String> commandsList = Arrays.asList(additionalCommands);
		
		for (int i = 0; i < levels.size(); i++) {
			String index = "" + levels.get(i);
			if(indexColumn != null) {
				index = "" + getMean(indexColumn.replaceAll("<level>", "" + levels.get(i)));
			}
			
			int modulo = 
					commandsList.contains(CMD_ONLY_EVERY_2ND_XTICS) ? 2 : 
					commandsList.contains(CMD_ONLY_EVERY_3RD_XTICS) ? 3 : 
					1;
			if(i % modulo == 0) {
				b.append(index);
			} else {
				b.append("_");
			}

			for (int j = 0; j < xValueNames.length; j++) {
				b.append("\t");
				String nameString = xValueNames[j];
				nameString = nameString.replaceAll("<level>", "" + levels.get(i));
				if (nameString.equals(xValueNames[j]))
					nameString = nameString + levels.get(i);

				
				String [] actualNames = nameString.split(":");
				for(String name : actualNames) {
					
					Double theValue = 0.0;
					if(name.endsWith(".min")) {
					
						theValue = getMinimum(name.substring(0, name.length() - ".min".length()));
					
					} else if(name.endsWith(".max")) {
		
						theValue = getMaximum(name.substring(0, name.length() - ".max".length()));
					
					} else {
						List<Double> values = getValues(name);
						if (type == ResultType.MEAN) {
							Number mean = getMean(values);
							theValue = mean.doubleValue();
						} else if (type == ResultType.THROUGHPUT) {
							theValue = getThroughput(name);
						}
						if(values.size() <= 0) {
							theValue = Double.NaN;
						}
					}
					if (theValue > 1000000) {
						b.append(theValue.longValue());
					} else {
						String val = f.format(theValue);
						if ((theValue).isNaN()) {
							//val = "0";
							val = "NaN";
						} 
						while (val.length() < 10)
							val = " " + val;
						b.append(val);
					}
					
				}
				
			}
			b.append("\n");
		}
		return b.toString();
	}

	public static String[] getArray(String prefix, List<Integer> numbers) {
		String[] result = new String[numbers.size()];
		for (int i = 0; i < numbers.size(); i++) {
			result[i] = prefix + numbers.get(i);
		}
		return result;
	}

	public List<IterationResult> getIterations() {
		return iterations;
	}

	public void setIterations(List<IterationResult> iterations) {
		this.iterations = iterations;
	}

	public long getFinishTime() {
		return finishTime;
	}

	public void setFinishTime() {
		this.finishTime = System.currentTimeMillis();
	}
	public void setFinishTime(long finishTime) {
		this.finishTime = finishTime;
	}

	public long getStartTime() {
		return startTime;
	}

	public void setStartTime() {
		this.startTime = System.currentTimeMillis();
	}
	public void setStartTime(long startTime) {
		this.startTime = startTime;
	}

	@Override
	public String toString() {
		StringBuilder b = new StringBuilder();
		List<String> names = new LinkedList<String>();
		if (iterations.size() > 0) {
			IterationResult iter = iterations.get(0);
			for (Entry e : iter.getEntries()) {
				String name = e.name;
				if (!names.contains(name)) {
					names.add(name);
					b.append(name + ":\n");
					b.append("\tTotal: " + getTotal(name) + "\n");
					b.append("\tAmount: " + getAmount(name) + "\n");
					b.append("\tMean: " + getMean(name) + "\n");
					b.append("\tStd.Dev.: " + getStdDeviation(name) + "\n");
				}
			}
		}
		return b.toString();
	}

	public long getDuration() {
		return finishTime - startTime;
	}
	
	public List<Entry> searchEntries(String namePattern, String valuePattern) {
		List<Entry> result = new LinkedList<Entry>();
		for(Entry e : getEntriesByNamePattern(namePattern)) {
			if(e.value != null && e.value.toString().matches(valuePattern)) {
				result.add(e);
			}
		}
		return result;
	}

	public static GenericTestResult loadLast(String fileNamePattern) throws Exception {
		return loadLast(fileNamePattern, "<time>");
	}
	public static GenericTestResult loadLast(String fileNamePattern, String placeHolder) throws Exception {
		String dir = fileNamePattern.contains("/") ? 
				fileNamePattern.substring(0, fileNamePattern.lastIndexOf("/")) : "./";
		String file = fileNamePattern.contains("/") ?
				fileNamePattern.substring(fileNamePattern.lastIndexOf("/") + 1) : fileNamePattern;
		List<Long> ids = new LinkedList<Long>();
		for(String f : new File(dir).list()) {
			String pattern = file.replace(placeHolder, "(.+)");
			Pattern p = Pattern.compile(pattern);
			Matcher m = p.matcher(f);
			if(m.matches()) {
				String id = m.group(1);
				try {
					ids.add(Long.parseLong(id));					
				} catch (Exception e) {
					logger.warn("Ignoring ID '" + id + "', which cannot be parsed as Long.");
				}
			}
		}
		String id = ids.isEmpty() ? "" : ("" + Collections.max(ids));
		return load(fileNamePattern.replace(placeHolder, id));
	}
	public static GenericTestResult load(String file) throws Exception {
		XMLUtil xmlUtil = new XMLUtil();
		IOUtil ioUtil = new IOUtil();
		String content = ioUtil.readFile(new FileInputStream(file));
		Element e  = xmlUtil.toElement(content);
		return xmlUtil.toJaxbObject(GenericTestResult.class, e);
	}

	public void saveOnShutdown(final String filePath) {
		saveOnShutdown(filePath, null);
	}
	public void saveOnShutdown(final String filePath, final Runnable runBefore) {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				if(runBefore != null) {
					runBefore.run();
				}
				save(filePath);
			}
		});
	}

	public boolean save(String filePath) {
		try {
			XMLUtil xmlUtil = new XMLUtil();
			String content = xmlUtil.toString(xmlUtil.toElement(this), true);
			ioUtil.saveFile(filePath, content);
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	public double getThroughput(String key) {
		double duration = getMean(key + "duration");
		double numResults = getValues(key).size();
		double resultsPerMS = numResults / duration;
		double resultsPerS = resultsPerMS * 1000.0;
		double resultsPerM = resultsPerS * 60.0;
		return resultsPerM;
	}

	public double getPercentile(String valueName, double p) {
		return getPercentile(getValues(valueName), p);
	}

	public double getPercentile(List<Double> values, double p) {
		Collections.sort(values);
		double size = values.size();
		double index = (size - 1.0) * p;
		return values.get((int) index);
	}

	public String getPlottableValuesBoxplot(List<Integer> levels,
			String... keyTemplates) {
		StringBuilder b = new StringBuilder();
		for (int l : levels) {
			b.append(l + "\t");
			for (String template : keyTemplates) {
				String key = template.replaceAll("<level>", "" + l);
				double min = getMinimum(key);
				double max = getMaximum(key);
				double p25 = getPercentile(key, 0.25);
				double p50 = getPercentile(key, 0.5);
				double p75 = getPercentile(key, 0.75);
				b.append(min + "\t" + p25 + "\t" + p50 + "\t" + p75 + "\t"
						+ max + "\t");
			}
			b.append("\n");
		}
		return b.toString();
	}
	
	public List<Entry> getEntriesByName(String name) {
		List<Entry> result = new LinkedList<Entry>();
		for(IterationResult i : iterations) {
			for(Entry e : new LinkedList<Entry>(i.entries)) {
				if(e.getName().equals(name)) {
					result.add(e);
				}
			}
		}
		return result;
	}
	public List<Entry> getEntriesByNamePattern(String namePattern) {
		List<Entry> result = new LinkedList<Entry>();
		for(IterationResult i : iterations) {
			for(Entry e : new LinkedList<Entry>(i.entries)) {
				if(e.getName().matches(namePattern)) {
					result.add(e);
				}
			}
		}
		return result;
	}

	public void limitNumberOfEntries(String levelsEntryNamePattern, 
			String valuesEntryNamePattern, int limit, EliminationMethod method) {
		List<Integer> levels = getAllLevels(levelsEntryNamePattern);
		int maxLevel = levels.size() + 1;
		int numValues = levels.size();
		if(numValues <= limit)
			return;
		int excess;
		if(method == EliminationMethod.CONSIDER_VALUE_DIFFERENCES) {
			do {
				// in a loop, determine the three entries (e1,e2,e3) with the smallest distance 
				// between e1 and e3 and then delete e2! This method assumes that the values are ORDERED!
				String levelEntryNameToRemove = null;
				double lowestDiffValue = Double.MAX_VALUE;
				for(int i = 0; i < levels.size() - 2; i ++) {
					String name1 = valuesEntryNamePattern.replaceAll("<level>", ""+levels.get(i));
					String name2 = valuesEntryNamePattern.replaceAll("<level>", ""+levels.get(i + 2));
					double value1 = getMean(name1);
					double value2 = getMean(name2);
					double diff = Math.abs(value2 - value1);
					if(diff < lowestDiffValue) {
						lowestDiffValue = diff;
						levelEntryNameToRemove = levelsEntryNamePattern.replaceAll("<level>", ""+levels.get(i + 1));
					}
				}
				for(IterationResult i : iterations) {
					for(Entry e : new LinkedList<Entry>(i.entries)) {
						if(e.getName().equals(levelEntryNameToRemove)) {
							i.entries.remove(e);
						}
					}
				}
				levels = getAllLevels(levelsEntryNamePattern, maxLevel);
				numValues = levels.size();
				excess = numValues - limit;
			} while(excess > 0);
		} else {
			throw new RuntimeException("Not yet implemented.");
		}
	}

	public void insertIntoLatexTemplate(String string, String outFile,
			String[] valueNames, List<Integer> levels) throws Exception {
		insertIntoLatexTemplate(string, new FileOutputStream(outFile), valueNames, levels, false);
	}
	
	public void insertIntoLatexTemplate(File templateFile, String outFile,
			String[] valueNames, List<Integer> levels, boolean doTTest) throws Exception {
		String template = ioUtil.readFile(new FileInputStream(templateFile));
		insertIntoLatexTemplate(template, new FileOutputStream(outFile), valueNames, levels, doTTest);
	}

	public void insertIntoLatexTemplate(File templateFile, String outFile) throws Exception {
		insertIntoLatexTemplate(templateFile, outFile, null);
	}

	public void insertIntoLatexTemplate(File templateFile, String outFile, String pattern) throws Exception {
		String template = ioUtil.readFile(new FileInputStream(templateFile));
		Pattern p = Pattern.compile("\\$([^\\$]+)\\$");
		Matcher m = p.matcher(template);
		List<String> fillIns = new LinkedList<String>();
		while(m.find()) {
			fillIns.add(m.group(1));
		}
		List<String> vNames = getAllValueNames(pattern);
		for(int i = 0; i < vNames.size(); i ++) {
			boolean isContained = false;
			String vName = vNames.get(i);
			for(String s : fillIns) {
				if(s.startsWith(vName)) {
					isContained = true;
					break;
				}
			}
			if(!isContained)
				vNames.remove(i--);
		}
		//System.out.println("required variable names in template: " + vNames);
		insertIntoLatexTemplate(templateFile, outFile, 
				vNames.toArray(new String[]{}), Arrays.asList(0), false);
	}
	
	public List<String> getAllValueNames() {
		return getAllValueNames(null);
	}
	public List<String> getAllValueNames(String pattern) {
		Pattern p = pattern != null ? Pattern.compile(pattern) : null;
		
		List<String> result = new LinkedList<String>();
		for(IterationResult i : iterations) {
			for(Entry e : i.getEntries()) {
				if(!result.contains(e.name)) {
					if(p == null || p.matcher(e.name).matches())
						result.add(e.name);
				}
			}
		}
		
		return result;
	}
	
	public String replace(String in, Map<String,String> searchReplace, int maxParallelReplacements) {
		
		if(searchReplace == null || searchReplace.isEmpty())
			return in;
		
		Map<String,String> thisSearchReplace = new HashMap<String, String>();
		int i = 0;
		for(String s : searchReplace.keySet()) {
			if((i++) >= maxParallelReplacements)
				break;
			thisSearchReplace.put(s, searchReplace.get(s));
		}
		
		String patternString = "(" + StringUtils.join(thisSearchReplace.keySet(), "|") + ")";

		for(String s : thisSearchReplace.keySet())
			searchReplace.remove(s);
		
		Pattern pattern = Pattern.compile(patternString);
		Matcher matcher = pattern.matcher(in);
	
		StringBuffer sb = new StringBuffer();
		while(matcher.find()) {
		    matcher.appendReplacement(sb, thisSearchReplace.get(matcher.group(1)));
		}
		matcher.appendTail(sb);

		in = sb.toString();

		if(searchReplace.size() > 0)
			return replace(in, searchReplace, maxParallelReplacements);
		
		return in;
	}

	public void insertIntoLatexTemplate(String templateContent, OutputStream out,
			String[] valueNames, List<Integer> levels, boolean doTTest) throws Exception {
		String content = templateContent;
		
		for (String name : valueNames) {
			for (int i : levels) {
				String theName = name.replaceAll("<level>", ""+i);
				String nameWithoutLevel = name.replaceAll("<level>", "");
				String nameInTemplate = theName;
				
				if(content.contains("$" + nameInTemplate + ".avg$")) {
					double avg = getMean(theName);
					content = content.replaceAll(
						"\\$" + nameInTemplate + ".avg\\$", "" + f.format(avg));
				}
				if(content.contains("$" + nameInTemplate + ".min$")) {
					double min = getMinimum(theName);
					content = content.replaceAll(
						"\\$" + nameInTemplate + ".min\\$", "" + f.format(min));
				}
				if(content.contains("$" + nameInTemplate + ".max$")) {
					double max = getMaximum(theName);
					content = content.replaceAll(
						"\\$" + nameInTemplate + ".max\\$", "" + f.format(max));
				}
				if(content.contains("$" + nameInTemplate + ".avgAllLev$")) {
					double avgAllLevels = getMean(name, levels);
					content = content.replaceAll(
						"\\$" + nameWithoutLevel + ".avgAllLev\\$", "" + f.format(avgAllLevels));
				}
				if(content.contains("$" + nameInTemplate + ".stddev$")) {
					double stddev = getStdDeviation(theName);
					content = content.replaceAll("\\$" + nameInTemplate
						+ ".stddev\\$", "" + f.format(stddev));
				}
				if(content.contains("$" + nameInTemplate + ".perct$")) {
					double perct = getPercentage(name, valueNames, levels);
					content = content.replaceAll("\\$" + nameInTemplate
						+ ".perct\\$", "" + f.format(perct));
				}
				
				if(doTTest) {
					for (String name1 : valueNames) {
						String theName1 = name1.replaceAll("<level>", ""+i);
						String nameInTemplate1 = name1.replaceAll("<level>", "");
						if(!theName1.equals(nameInTemplate1))
							nameInTemplate1 += "." + i;
						double t = getTValue(getMean(theName), getMean(theName1),
								getSampleVariance(theName),
								getSampleVariance(theName1), 
								(int) getAmount(theName));
						content = content.replaceAll("\\$" + nameInTemplate + "." + nameInTemplate1
								+ "." + i + ".t\\$", "" + f.format(t));
					}
				}
			}
		}
			
		BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(out));
		bw.write(content);
		bw.close();
	}

	private double getPercentage(String oneName, String[] allNames, List<Integer> levels) {
		double sum = 0;
		for(String a : allNames) {
			sum += getMean(a, levels);
		}
		double oneMean = getMean(oneName, levels);
		return (oneMean / sum) * 100.0;
	}
	
	private double getMean(String nameTemplate, List<Integer> levels) {
		double sum = 0;
		for(int l : levels) {
			String name = nameTemplate.replaceAll("<level>", ""+l);
			sum += getMean(name);		
		}
		return sum / (double)levels.size();
	}

	protected double getPercentage(String nameTemplate, int i, List<Integer> levels) {
		double sum = 0;
		for(int l : levels) {
			String name = nameTemplate.replaceAll("<level>", ""+l);
			sum += getMean(name);
		}
		String name = nameTemplate.replaceAll("<level>", ""+i);
		return getMean(name) / sum;
	}

	public static double getTValue(double avgA, double avgB, double varianceA,
			double varianceB, int iterations) {
		double diff = avgA - avgB;
		double sum = (varianceA + varianceB) / (double) (iterations);
		return diff / Math.sqrt(sum);
	}
	
	public static double[] convert(Double[] in) {
		double[] out = new double[in.length];
		for (int i = 0; i < in.length; i++)
			out[i] = in[i];
		return out;
	}

	public static double[] convert(List<Double> in) {
		return convert(in.toArray(new Double[0]));
	}

	private static double getBarPosition(final double barWidth,
			final double spacing, int group, int positionInGroup) {
		if (group <= 0 && positionInGroup <= 0)
			return 0.5;
		if (positionInGroup >= 0) {
			double add = 0;
			// if(positionInGroup == 0)
			// add = barWidth;
			return add
					+ barWidth
					+ getBarPosition(barWidth, spacing, group,
							positionInGroup - 1);
		}
		if (group > 0) {
			int newPositionInGroup = 1;
			int newGroup = group - 1;
			if (newGroup <= 0)
				newPositionInGroup = 4;
			else if (newGroup == 1 || newGroup == 2)
				newPositionInGroup = 3;
			else if (newGroup > 2 && newGroup < 7)
				newPositionInGroup = 2;
			double add = 0;
			if (newGroup >= 4)
				add = -barWidth;
			return add
					+ spacing
					+ getBarPosition(barWidth, spacing, newGroup,
							newPositionInGroup);
		}
		return 0;
	}

	public static double getBarLabelPosition(final double barWidth,
			final double spacing, int group) {
		double min = getBarPosition(barWidth, spacing, group, 0);
		int position = 0;
		if (group <= 0)
			position = 4;
		else if (group >= 1 && group <= 2)
			position = 3;
		else if (group == 3)
			position = 2;
		else if (group > 3 && group < 7)
			position = 1;
		double max = getBarPosition(barWidth, spacing, group, position);
		return (max + min) / 2.0;
	}

	public String getPlottableAverages3D(List<Integer> levels,
			String[] keyTemplates, ResultType type, String ... additionalCommands) {
		StringBuilder b = new StringBuilder();
		NumberFormat f = NumberFormat.getInstance(Locale.US);
		f.setMinimumFractionDigits(3);
		f.setMaximumFractionDigits(3);
		f.setGroupingUsed(false);
		for (int l : levels) {
			for (String t : keyTemplates) {
				String key = t.replaceAll("<level>", "" + l);
				Double val = 0.0;
				if (type == ResultType.THROUGHPUT)
					val = getThroughput(key);
				else if (type == ResultType.MEAN)
					val = getMean(key);
				if (val.isNaN())
					val = 0.0;
				b.append(f.format(val));
				b.append("\n");
			}
			b.append("\n");
		}
		return b.toString();
	}

	@Deprecated
	public static void combineResults(String pattern, String... fileNames)
			throws Exception {
		XMLUtil xmlUtil = new XMLUtil();
		GenericTestResult result = new GenericTestResult();
		int i = 0;
		for (String filename : fileNames) {
			JAXBContext context = JAXBContext.newInstance(GenericTestResult.class);
			Unmarshaller unmarsh = context.createUnmarshaller();
			GenericTestResult temp = (GenericTestResult) unmarsh.unmarshal(new File(filename));
			String contents = xmlUtil.toString(xmlUtil.toElement(temp));
			contents = contents.replaceAll(pattern + "[0-9]+", pattern + (i++));
			temp = xmlUtil.toJaxbObject(GenericTestResult.class, xmlUtil.toElement(contents));
			result.getIterations().addAll(temp.getIterations());
		}
		String out = xmlUtil.toString(xmlUtil.toElement(result), true);
		System.out.println(out);
	}

	public void mergeWith(GenericTestResult temp) {
		while(getIterations().size() < temp.getIterations().size())
			newIteration();
		for(int i = 0; i < temp.getIterations().size(); i ++) {
			IterationResult iter = temp.getIterations().get(i);
			getIterations().get(i).getEntries().addAll(iter.getEntries());
		}
	}

	public void mergeWith(Collection<GenericTestResult> temp) {
		for(GenericTestResult r : temp) {
			mergeWith(r);
		}
	}


//	public static void main(String[] args) throws Exception {
//
//		if (args.length > 0 && args[0].equals("combine")) {
//			combineResults(args[1], Arrays.copyOfRange(args, 2, args.length));
//			return;
//		}
//
//		String filename = "etc/performanceTestResults_PortfolioUnoptimized.xml";
//		if (args.length > 0) {
//			filename = args[0];
//			if (!new File(filename).exists()) {
//				filename = "etc/performanceTestResults_" + args[0] + ".xml";
//			}
//		} else
//			args = new String[] { "Voting" };
//		JAXBContext context = JAXBContext.newInstance(GenericTestResult.class);
//		Unmarshaller unmarsh = context.createUnmarshaller();
//		GenericTestResult result = (GenericTestResult) unmarsh
//				.unmarshal(new File(filename));
//
//		List<Integer> levels = Arrays.asList(10, 30, 100, 250, 500);
//		String[] valueNames = new String[] {};
//		String out = null;
//		ResultType type = ResultType.MEAN;
//		if (args.length > 0 && args[0].contains("Booking")) {
//			levels = Arrays
//					.asList(10, 50, 100, 200, 500, 750/* , 1000, 1500, 2000 */);
//			List<Integer> parallelities = Arrays.asList(1, 5, 10, 15, 30, 50);
//			// System.out.println(result.getThroughput("s500h2c3r1p1st1"));
//			// System.out.println(result.getThroughput("s500h2c3r1p5st1"));
//			// System.out.println(result.getThroughput("s500h2c3r1p10st1"));
//			// System.out.println(result.getThroughput("s500h2c3r1p15st1"));
//			// System.out.println(result.getThroughput("s500h2c3r1p30st1"));
//			// System.out.println(result.getThroughput("s500h2c3r1p50st1"));
//			String template = "s<level>h2a30c3r1p<par>st<strategy>";
//			List<String> names = new ArrayList<String>();
//			for (int strategy = 0; strategy < 5; strategy++) {
//				for (int par : parallelities) {
//					String s = template;
//					s = s.replaceAll("<strategy>", "" + strategy);
//					s = s.replaceAll("<par>", "" + par);
//					names.add(s);
//				}
//			}
//			// out = result.getPlottableAverages3D(levels, names.toArray(new
//			// String[]{}), ResultType.THROUGHPUT);
//
//			valueNames = names.toArray(new String[] {});
//			if (args.length > 1 && !args[1].equals("3D")) {
//
//				int strategy = Integer.parseInt(args[1]);
//				out = null;
//				levels = Arrays.asList(1, 5, 10, 20, 30, 50, 75, 100); // parallelities
//				valueNames = new String[] {
//						"s10h1a20c3r30p<level>st" + strategy,
//						"s50h1a20c3r30p<level>st" + strategy,
//						"s100h1a20c3r30p<level>st" + strategy,
//						"s200h1a20c3r30p<level>st" + strategy };
//				type = ResultType.THROUGHPUT;
//
//			} else if (args.length > 1 && args[1].equals("3D")) {
//				int strategy = 0;
//				if (args.length > 2) {
//					if (args.length > 2 && args[2].equals("aggregators")) {
//						parallelities = Arrays.asList(1, 5, 10, 15, 30, 50, /* */
//								75, 100);
//						strategy = 0;
//						template = "s200h2a<level>c3r1p<par>st" + strategy;
//						levels = Arrays.asList(1, 3, 5, /* */7, 10, 15/* */); // in
//																				// this
//																				// case:
//																				// parallelity
//																				// levels
//						names.clear();
//						for (int par : parallelities) {
//							String s = template;
//							s = s.replaceAll("<par>", "" + par);
//							names.add(s);
//						}
//						out = result.getPlottableAverages3D(levels, names
//								.toArray(new String[] {}),
//								ResultType.THROUGHPUT);
//
//					} else {
//						try {
//							strategy = Integer.parseInt(args[2]);
//						} catch (Exception e) {
//						}
//						levels = Arrays.asList(10, 50, 100, 200, 500/* , 750 */);
//						parallelities = Arrays.asList(1, 5, 10, 15, 30, 50, 75,
//								100);
//						template = "s<level>h1a20c3r1p<par>st" + strategy;
//						names.clear();
//						for (int par : parallelities) {
//							String s = template;
//							s = s.replaceAll("<par>", "" + par);
//							names.add(s);
//						}
//						out = result.getPlottableAverages3D(levels, names
//								.toArray(new String[] {}),
//								ResultType.THROUGHPUT);
//
//					}
//				}
//			} else if (args[0].equals("BookingNumAggregators")) {
//				// result.eliminateXhighestValues = 2;
//
//				levels = Arrays.asList(1,/* 3, */5,/* 7, */10, 15, 20/* ,25,30 */); // number
//																				// of
//																				// deployed
//																				// aggregators
//				// levels = Arrays.asList(1, 25, 50, 100); // parallelity
//
//				// out = result.getPlottableValuesBoxplot(levels,
//				// "s200h2a30c3r1p<level>st0", "s200h2a30c3r1p<level>st0");
//				// out = result.getPlottableValuesBoxplot(levels,
//				// "s200h2a<level>c3r1p1st0", "s200h2a<level>c3r1p50st0");
//				out = result.getPlottableValuesBoxplot(levels,
//						"s50h2a<level>c3r1p25st0", "s50h2a<level>c3r1p50st0",
//						"s50h2a<level>c3r1p100st0");
//			}
//
//		} else if (args.length > 0 && args[0].equals("Rendering")) {
//			levels = Arrays.asList(50, 100, 300, 700, 1200, 2000);
//			valueNames = new String[] { "s<level>h0c3r1", "s<level>h1c2r1",
//					"s<level>h1c3r1", "s<level>h2c2r1", "s<level>h2c3r1" };
//
//		} else if (args.length > 0 && args[0].equals("Voting")) {
//			levels = Arrays.asList(100, 200, 400, 700, 1000);
//			valueNames = new String[] { "s<level>h0a15c3r100p1st0",
//					"s<level>h1a15c2r100p1st0", "s<level>h1a15c3r100p1st0",
//					"s<level>h2a15c2r100p1st0", "s<level>h2a15c3r100p1st0" };
//			
//			if (args.length > 1 && args[1].equals("memGnuplot")) {
//				double barWidth = 0.3;
//				double spacing = 0.5;
//				String[] keys = new String[] { "s1000h2c3_m", "s1000h2c2_m",
//						"s1000h1c3_m", "s1000h1c2_m", "s1000h0c3_m" };
//				out = "";
//				// ("1" 0.675, "2" 1.635, "3" 2.595, "4" 3.555, "5" 4.515, "6"
//				// 5.475, "7" 6.435, "8" 7.395, "9" 8.355, "10" 9.315, "11"
//				// 10.275, "12" 11.235, "13" 12.195)
//				out = "set xtics (";
//				for (int i = 1; i <= 13; i++) {
//					out += "\"" + i + "\" "
//							+ getBarLabelPosition(barWidth, spacing, i - 1);
//					if (i < 13)
//						out += ", ";
//				}
//				out += ")\n";
//				out += "plot '-' with boxes lt 3 title \"13 Aggregators\", '-' with boxes fs pattern 5 lt 4 title \"7 Aggregators\", '-' title \"4 Aggregators\" with boxes fs pattern 6 lt 5, '-' title \"3 Aggregators\" with boxes fs pattern 10 lt 6, '-' title \"1 Aggregator\" with boxes fs pattern 9 lt 7\n";
//				int count = 0;
//				for (String key : keys) {
//					for (int i = 1; i <= 13; i++) {
//						// double pos = 0.5 + count*spacing + (i-1)*barWidth;
//						double pos = getBarPosition(barWidth, spacing, i - 1,
//								count);
//						if ((count == 4 && i <= 1) || (count == 3 && i <= 3)
//								|| (count == 2 && i <= 4)
//								|| (count == 1 && i <= 7)
//								|| (count == 0 && i <= 13)) {
//							out += pos + ", "
//									+ (result.getMean(key + i) / 1000000.0);
//							out += "\n";
//						}
//					}
//					out += "e\n";
//					count++;
//				}
//			} else if (args.length > 1 && args[1].equals("3D")) {
//				Integer[] numsResults = { 100, 200, 400, 700, 1000 };
//				String[] numsAggrs = { "s100h2c3r<level>", "s100h2c2r<level>",
//						"s100h1c3r<level>", "s100h1c2r<level>",
//						"s100h0c3r<level>" };
//				out = result.getPlottableAverages3D(Arrays.asList(numsResults),
//						numsAggrs, ResultType.MEAN);
//			}
//		} else if (args.length > 0
//				&& (args[0].equals("PortfolioUnoptimized") || args[0]
//						.equals("PortfolioOptimized"))) {
//			levels = Arrays.asList(1, 5, 10, 20, 30, 40, 50, 60);
//			valueNames = new String[] { "s<level>h2a15c3r10p1st0",
//					"s<level>h2a15c3r100p1st0", "s<level>h2a15c3r200p1st0" };
//			result.eliminateXhighestValues = 1;
//		}
//
//		if (out == null)
//			out = result.getPlottableAverages(levels, valueNames, type);
//		System.out.println(out);
//
//	}

}

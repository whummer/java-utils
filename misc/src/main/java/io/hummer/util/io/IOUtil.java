package io.hummer.util.io;

import io.hummer.util.log.LogUtil;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;

/**
 * Often used utility functions for input/output (I/O).
 * 
 * @author Waldemar Hummer
 */
public class IOUtil {

	static final Logger logger = LogUtil.getLogger(IOUtil.class);

	/** environment variables used for exec(...) */
	private String[] envp;

	/**
	 * Executes the given command in the host operating system, waits until the 
	 * process has finished, and returns the stdout output produced by the
	 * process.
	 */
	public String exec(String command) {
		return exec(command, true)[0];
	}

	/**
	 * Set an array of environment variables in the form "var=value"
	 * to be used with the exec(...) .
	 */
	public void setEnvironmentVariablesForExec(String ... envp) {
		if(this.envp == null) {
			this.envp = envp;
		} else {
			Set<String> vals = new HashSet<String>(Arrays.asList(this.envp));
			vals.addAll(Arrays.asList(envp));
			this.envp = vals.toArray(new String[0]);
		}
	}

	/**
	 * Executes the given command in the external Runtime, waits until the 
	 * process has finished (if parameter blocking==true), and returns the 
	 * stdout (array index 0) and stderr output (array index 1) produced 
	 * by the process.
	 */
	public String[] exec(String command, boolean blocking) {
		return exec(command, this.envp, true);
	}

	/**
	 * Executes the given command in the external Runtime using the additional
	 * environment variables in the given String array, waits until the 
	 * process has finished (if parameter blocking==true), and returns the 
	 * stdout (array index 0) and stderr output (array index 1) produced 
	 * by the process.
	 */
	public String[] exec(String command, String[] envp, boolean blocking) {
		try {
			Process p = null;
			if(envp != null) {
				p = Runtime.getRuntime().exec(new String[]{"sh", "-c", command});
			} else {
				p = Runtime.getRuntime().exec(new String[]{"sh", "-c", command}, envp);
			}
			String stdout = "", stderr = "";
			if(blocking) {
				p.waitFor();
				stdout = readFile(p.getInputStream());
				stderr = readFile(p.getErrorStream());
			}
			return new String[]{stdout, stderr};
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public boolean isUnixProcessRunning(String command) {
		return !getUnixPIDs(command).isEmpty();
	}
	public List<Integer> getUnixPIDs(String command) {
		List<Integer> result = new LinkedList<Integer>();
		String ps = exec("ps -C '" + command + "'");
		for(String p : ps.split("\\n")) {
			try {
				result.add(Integer.parseInt(p.trim().split("\\s+")[0].trim()));
			} catch (Exception e) {
				/* swallow */
			}
		}
		for(int pid : getUnixPIDs1(command)) {
			if(!result.contains(pid))
				result.add(pid);
		}
		return result;
	}

	private List<Integer> getUnixPIDs1(String command) {
		List<Integer> result = new LinkedList<Integer>();

		if (command.contains("/"))
			command = command.substring(command.lastIndexOf("/") + 1);

		try {
			Process p = Runtime.getRuntime().exec(
					new String[] { "ps", "-A", "-o", "comm,pid", "h" });
			p.waitFor();
			String list = readStream(p.getInputStream());
			String[] lines = list.split("\n");
			for (String s : lines) {
				s = s.replaceAll("\\s+", " ").trim();
				String[] pair = s.split(" ");
				try {
					int pid = Integer.parseInt(pair[1].trim());
					String cmd = pair[0].trim();
					if (cmd.equals(command)) {
						result.add(pid);
					}
				} catch (Exception e) {
					// swallow
				}
			}
			
		} catch (Exception e) {
			logger.info("Unable to determine PIDs of running processes.", e);
		}

		return result;
	}

	public String readURL(String urlString) throws Exception {
		URL url = new URL(urlString);
		return readFile(url.openStream());
	}

	public String readFile(File file) throws Exception {
		return readFile(new FileInputStream(file));
	}

	public String readStream(InputStream in) throws Exception {
		return new String(readBytes(in));
	}

	public String readStream(InputStream in, String encoding) throws Exception {
		return new String(readBytes(in), encoding);
	}

	public String readFile(InputStream in, String encoding) throws Exception {
		return readStream(in, encoding);
	}

	public String readFile(InputStream in) throws Exception {
		return readStream(in);
	}

	public static byte[] readBytes(InputStream in) throws Exception {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		int b = 0;
		while((b = in.read()) >= 0)
			bos.write(b);
		in.close();
		bos.close();
		return bos.toByteArray();
	}

	public String readFile(String url) throws Exception {
		InputStream in = null;
		URL u = null;
		try {
			u = new URL(url);
		} catch(Exception e) {
			u = new File(url).toURI().toURL();
		}
		in = u.openStream();
		return readFile(in);
	}

	public InputStream getInputStream(String fileURI) throws Exception {
		if(fileURI == null)
			return null;
		if(fileURI.matches("[a-z]+://.+")) {
			try {
				URL url = new URL(fileURI);
				return url.openStream();
			} catch(MalformedURLException e) {
				return null;
			}
		}
		return new FileInputStream(fileURI);
	}

	public void saveFile(String fName, String content) throws Exception {
		FileWriter writer = new FileWriter(fName);
		writer.write(content);
		writer.close();
	}

	public void saveFile(String file, byte[] data) throws Exception {
		FileOutputStream fos = new FileOutputStream(file);
		fos.write(data);
		fos.close();
	}

	public List<String> files(String directory) {
		String[] list = new File(directory).list();
		if(list == null) {
			logger.warn("Cannot list files of non-directory path: '" + directory + "'");
			return new LinkedList<String>();
		}
		return Arrays.asList(list);
	}

	public void rm_rf(String dir) {
		try {
			FileUtils.deleteDirectory(new File(dir));
		} catch (IOException e) {
			logger.warn("Unable to delete directory: " + dir, e);
		}
	}

	/**
	 * @param src
	 * @param dst
	 * @return true if file already existed and has been overwritten
	 */
	public boolean cp(String src, String dst) {
		try {
			File dstFile = new File(dst);
			boolean exists = dstFile.exists();
			File srcFile = new File(src);
			if(srcFile.isDirectory()) {
				FileUtils.copyDirectory(srcFile, dstFile);
			} else {
				FileUtils.copyFile(srcFile, dstFile);
			}
			return exists;
		} catch (IOException e) {
			logger.warn("Unable to copy file from '" + src + "' to '" + dst + "'", e);
			throw new RuntimeException(e);
		}
	}

	/**
	 * @param src
	 * @param dst
	 * @return true if file already existed and has been overwritten
	 */
	public boolean mv(String src, String dst) {
		try {
			File dstFile = new File(dst);
			boolean exists = dstFile.exists();
			FileUtils.moveFile(new File(src), dstFile);
			return exists;
		} catch (IOException e) {
			logger.warn("Unable to move file from '" + src + "' to '" + dst + "'", e);
			throw new RuntimeException(e);
		}
	}
	
	/**
	 * Copy the contents of an input stream to an output stream.
	 * @param fileInputStream
	 * @param output
	 * @throws IOException 
	 */
	public void pipe(InputStream in, OutputStream out) throws IOException {
		copy(in,out);
	}
	/**
	 * Copy the contents of an input stream to an output stream.
	 * @param fileInputStream
	 * @param output
	 * @throws IOException 
	 */
	public void copy(InputStream in, OutputStream out) throws IOException {
		IOUtils.copy(in,out);
	}

}

package io.hummer.util.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.LinkedBlockingQueue;

public class InputOutputStreamBuffer {
	private LinkedBlockingQueue<Integer> buffer = new LinkedBlockingQueue<Integer>();
	private boolean closed;
	protected int returnNext = -1;
	private OutputStream os = new OutputStream() {
		public void write(int b) throws IOException {
			try {
				buffer.put(b);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	};
	private InputStream is = new InputStream() {
		public int read() throws IOException {
			try {
				if(closed && buffer.size() <= 0)
					return -1;
				if(returnNext >= 0) {
					int tmp = returnNext;
					returnNext = -1;
					return tmp;
				}
				Integer val = buffer.take();
				if(val == null)
					return -1;
				return val;
			} catch (InterruptedException e) {
				e.printStackTrace();
				throw new RuntimeException(e);
			}
		}
	};
	public OutputStream getOS() {
		return os;
	}
	public InputStream getIS() {
		return is;
	}
	public void close() {
		closed = true;
		try {
			buffer.put(-1);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	public void open() {
		closed = false;
	}
}

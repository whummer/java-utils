package io.hummer.util.misc;

import java.net.BindException;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.lang.exception.ExceptionUtils;


/**
 * Miscellaneous utility functions.
 * @author Waldemar Hummer
 */
public class ExceptionsUtil {
	
	private static final ExceptionsUtil instance = new ExceptionsUtil();

	public static ExceptionsUtil getInstance() {
		return instance;
	}


	private void getAllCauses(Throwable t, List<Throwable> list) {
		if(t == null)
			return;
		list.add(t);
		getAllCauses(t.getCause(), list);
	}
		
	public List<Throwable> getAllCauses(Throwable t) {
		List<Throwable> result = new LinkedList<Throwable>();
		getAllCauses(t, result);
		return result;
	}

	public List<Class<?>> getAllCauseClasses(Throwable t) {
		List<Class<?>> result = new LinkedList<Class<?>>();
		for(Throwable t1 : getAllCauses(t))
			result.add(t1.getClass());
		return result;
	}

	public boolean containsCauseClass(Throwable t, Class<BindException> clazz) {
		return getAllCauseClasses(t).contains(clazz);
	}

	public String getStackTrace(Throwable t) {
		return ExceptionUtils.getStackTrace(t);
	}

}

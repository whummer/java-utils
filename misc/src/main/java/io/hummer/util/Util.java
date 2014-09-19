package io.hummer.util;

import org.apache.log4j.Logger;

import io.hummer.util.coll.CollectionsUtil;
import io.hummer.util.cp.ClasspathUtil;
import io.hummer.util.io.IOUtil;
import io.hummer.util.log.LogUtil;
import io.hummer.util.math.MathUtil;
import io.hummer.util.misc.ExceptionsUtil;
import io.hummer.util.misc.MiscUtil;
import io.hummer.util.net.NetUtil;
import io.hummer.util.str.StringUtil;
import io.hummer.util.time.TimeUtil;

/**
 * Util aggregator with references to sub-utils
 * @author hummer
 */
public class Util {

	private static final Util instance = new Util();

	public final IOUtil io = new IOUtil();
	public final CollectionsUtil coll = new CollectionsUtil();
	public final ClasspathUtil cp = new ClasspathUtil();
	public final ExceptionsUtil exc = new ExceptionsUtil();
	public final LogUtil log = new LogUtil();
	public final MathUtil math = new MathUtil();
	public final MiscUtil misc = new MiscUtil();
	public final NetUtil net = new NetUtil();
	public final StringUtil str = new StringUtil();
	public final TimeUtil time = new TimeUtil();

	public static Util getInstance() {
		return instance;
	}

	public static Logger getLogger(Class<?> clazz) {
		return LogUtil.getLogger(clazz);
	}
	public static Logger getLogger() {
		return LogUtil.getLogger();
	}

}

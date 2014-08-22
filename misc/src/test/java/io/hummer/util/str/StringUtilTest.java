package io.hummer.util.str;

import org.junit.Test;
import static org.junit.Assert.*;

public class StringUtilTest {

	@Test
	public void test() {
		StringUtil str = new StringUtil();
		assertFalse(str.isMD5(	"_9054025255fb1a26e4bc422aef54eb4"));
		assertFalse(str.isMD5(	"z9054025255fb1a26e4bc422aef54eb4"));
		assertFalse(str.isMD5(	"9054025255fb1a26e4bc422aef54eb4"));
		assertTrue(str.isMD5(	"79054025255fb1a26e4bc422aef54eb4"));
	}

}

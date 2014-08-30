package io.hummer.util.test.result;

import io.hummer.util.test.result.IterationBasedAggregatedDescriptiveStatistics.IterationBasedAggregatedDescriptiveStatisticsDefault;

import org.junit.Assert;
import org.junit.Test;

public class AggregatedStatisticsTest {

	@Test
	public void testStats() {
		IterationBasedAggregatedDescriptiveStatisticsDefault d = new IterationBasedAggregatedDescriptiveStatisticsDefault();
		double precision = 0.00000001;

		//d.addValue(0.0, 0.0);

		d.addValue(1.0, 1.0);
		d.addValue(1.0, 2.0);

		d.addValue(2.0, 2.0);
		d.addValue(2.0, 3.0);

		d.addValue(3.0, 3.0);
		d.addValue(3.0, 4.0);
		d.addValue(3.0, 5.0);

		//System.out.println(d.getStatistics(2, 3));
		Assert.assertEquals(3, d.getStatistics(2, 3).size());
		Assert.assertEquals(0, d.getLastStatistics(2, 5).getSecond().getMean(), precision);
		System.out.println(d.getLastStatistics(2, 4));
		Assert.assertEquals(3, d.getLastStatistics(2, 4).getSecond().getN());
		Assert.assertEquals(4, d.getLastStatistics(2, 3).getSecond().getMean(), precision);
		Assert.assertEquals(0, d.getLastStatistics(2, 5).getSecond().getMean(), precision);

		d.addValue(4.0, 3.0);
		d.addValue(4.0, 4.0);
		d.addValue(4.0, 5.0);

		Assert.assertEquals(32, d.getStatistics().getSum(), precision);
		Assert.assertEquals(3, d.getStatistics(2, 4).size());
//		System.out.println(d.getLastStatistics(2, 4));
		Assert.assertEquals(6, d.getLastStatistics(2, 4).getSecond().getN());
		Assert.assertEquals(4, d.getLastStatistics(2, 4).getSecond().getMean(), precision);

		d.addValue(8.0, 0.0);
//		System.out.println(d.getStatistics(2, 8));
		Assert.assertEquals(5, d.getStatistics(2, 8).size());
	}

}

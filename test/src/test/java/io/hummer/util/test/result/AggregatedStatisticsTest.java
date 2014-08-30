package io.hummer.util.test.result;

import io.hummer.util.test.result.IterationBasedAggregatedDescriptiveStatistics.IterationBasedAggregatedDescriptiveStatisticsDefault;

import org.junit.Assert;
import org.junit.Test;

public class AggregatedStatisticsTest {

	@Test
	public void testStats() {
		IterationBasedAggregatedDescriptiveStatisticsDefault d = new IterationBasedAggregatedDescriptiveStatisticsDefault();
		double precision = 0.00000001;

		d.addValue(0.0, 0.0);

		d.addValue(1.0, 1.0);
		d.addValue(1.0, 2.0);

		d.addValue(2.0, 2.0);
		d.addValue(2.0, 3.0);

		d.addValue(3.0, 3.0);
		d.addValue(3.0, 4.0);
		d.addValue(3.0, 5.0);

		Assert.assertEquals(2, d.getStatistics(2).size());

		d.addValue(4.0, 3.0);
		d.addValue(4.0, 4.0);
		d.addValue(4.0, 5.0);

		Assert.assertEquals(32, d.getStatistics().getSum(), precision);
		Assert.assertEquals(d.getStatistics(2).size(), 3);
		Assert.assertEquals(d.getLastStatistics(2).getSecond().getMean(), 4, precision);

		d.addValue(8.0, 0.0);
		System.out.println(d.getStatistics(2));
		Assert.assertEquals(d.getStatistics(2).size(), 5);
	}

}

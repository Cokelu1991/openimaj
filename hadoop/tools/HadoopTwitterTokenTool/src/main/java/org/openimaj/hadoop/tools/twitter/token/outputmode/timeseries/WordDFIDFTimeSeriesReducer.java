/**
 * Copyright (c) 2011, The University of Southampton and the individual contributors.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 *   * 	Redistributions of source code must retain the above copyright notice,
 * 	this list of conditions and the following disclaimer.
 *
 *   *	Redistributions in binary form must reproduce the above copyright notice,
 * 	this list of conditions and the following disclaimer in the documentation
 * 	and/or other materials provided with the distribution.
 *
 *   *	Neither the name of the University of Southampton nor the names of its
 * 	contributors may be used to endorse or promote products derived from this
 * 	software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.openimaj.hadoop.tools.twitter.token.outputmode.timeseries;

import java.io.StringWriter;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;
import org.openimaj.hadoop.tools.twitter.utils.WordDFIDF;
import org.openimaj.hadoop.tools.twitter.utils.WordDFIDFTimeSeries;
import org.openimaj.io.IOUtils;

/**
 * Given a stream of wordDFIDF as input, reads each DFIDF, constructs a time series and emits the time series
 * @author Sina Samangooei (ss@ecs.soton.ac.uk)
 *
 */
public class WordDFIDFTimeSeriesReducer extends Reducer<Text, BytesWritable, NullWritable, Text> {
	@Override
	protected void reduce(Text word, java.lang.Iterable<BytesWritable> dfidfs, Reducer<Text,BytesWritable,NullWritable,Text>.Context context) throws java.io.IOException ,InterruptedException {
		WordDFIDFTimeSeries dts = new WordDFIDFTimeSeries();
		for (BytesWritable bytesWritable : dfidfs) {
			WordDFIDF wd = IOUtils.deserialize(bytesWritable.getBytes(), WordDFIDF.class);
			dts.add(wd.timeperiod, wd);
		}
		StringWriter writer = new StringWriter();
		writer.write(word + " ");
		IOUtils.writeASCII(writer, dts);
		context.write(NullWritable.get(), new Text(writer .toString()));
	};
}

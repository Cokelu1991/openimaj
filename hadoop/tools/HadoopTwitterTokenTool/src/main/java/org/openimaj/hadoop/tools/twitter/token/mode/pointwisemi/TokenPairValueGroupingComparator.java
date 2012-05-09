package org.openimaj.hadoop.tools.twitter.token.mode.pointwisemi;

import java.io.IOException;
import java.util.Arrays;
import java.util.regex.Pattern;

import org.apache.hadoop.io.RawComparator;
import org.apache.hadoop.io.Text;
import org.openimaj.io.IOUtils;


/**
 * Read a tokenpair and make sure the single words appear before the pair words
 * @author Jonathon Hare <jsh2@ecs.soton.ac.uk>, Sina Samangooei <ss@ecs.soton.ac.uk>
 *
 */
public class TokenPairValueGroupingComparator implements RawComparator<Text> {

	@Override
	public int compare(Text o1, Text o2) {
		String o1s = o1.toString();
		String o2s = o2.toString();
		
		String[] o1split = o1s.split(Pattern.quote(PairEmit.TIMESPLIT));
		String[] o2split = o2s.split(Pattern.quote(PairEmit.TIMESPLIT));
		try{
			Long o1time = new Long(o1split[0]);
			Long o2time = new Long(o2split[0]);
			int timeCmp = o1time.compareTo(o2time);
			return timeCmp;			
		}
		catch(Exception e){
			e.printStackTrace();
			return 0;
		}
	}

	@Override
	public int compare(byte[] b1, int s1, int l1, byte[] b2, int s2, int l2) {
		byte[] o1arr = Arrays.copyOfRange(b1, s1, s1+l1);
		byte[] o2arr = Arrays.copyOfRange(b2, s2, s2+l2);
		String o1 = new String(o1arr);
		o1 = o1.substring(o1.indexOf('#')+1);
		String o2 = new String(o2arr);
		o2 = o2.substring(o2.indexOf('#')+1);
		return compare(new Text(o1),new Text(o2));
	}


}

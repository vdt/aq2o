package com.activequant.backtesting.reporting;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Date;

import com.activequant.domainmodel.TimeStamp;
import com.activequant.domainmodel.streaming.StreamEventIterator;
import com.activequant.domainmodel.streaming.TimeStreamEvent;
import com.activequant.domainmodel.trade.event.OrderFillEvent;

/**
 * Creates a stream event iterator from a transaction file list. 
 * Can be used to replay order fills in sync with market events, for example to generate reports. 
 * 
 * @author GhostRider
 * 
 */
public class TransactionFileStreamIterator extends StreamEventIterator<TimeStreamEvent> {

	private BufferedReader br;
	private String line;

	public TransactionFileStreamIterator(String fileName) throws Exception {
		File f = new File(fileName);
		br = new BufferedReader(new FileReader(f));
		// skip header 
		line = br.readLine();
		line = br.readLine();
	}

	@Override
	public boolean hasNext() {
		if (line != null)
			return true;
		return false;
	}

	/**
	 * format documentation see
	 * 
	 * http://developers.activequant.org/projects/pecora/wiki
	 */
	@Override
	public OrderFillEvent next() {
		try {
			String[] p = line.split(",");
			String id = p[0];
			String inst = p[1];			
			TimeStamp tsUnixGmt = new TimeStamp(new Date(Long.parseLong(p[3]) * 1000));
			String dir = p[4];
			Double price = Double.parseDouble(p[6]);
			Double quantity = Double.parseDouble(p[5]);

			OrderFillEvent ofe = new OrderFillEvent();
			ofe.setRefOrderId(id);
			ofe.setOptionalInstId(inst);
			ofe.setFillPrice(price);
			ofe.setFillAmount(quantity);
			ofe.setSide(dir);			
			ofe.setTimeStamp(tsUnixGmt);

			// shift it, so that it looks as if it emitted at the end of a
			// candle.

			line = br.readLine();
			return ofe;

		} catch (Exception e) {
			throw new RuntimeException(e);
		}

	}

}

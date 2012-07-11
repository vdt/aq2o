package com.activequant.trading;

import com.activequant.domainmodel.TimeStamp;
import com.activequant.tools.streaming.PNLChangeEvent;
import com.activequant.transport.ITransportFactory;

public interface IRiskCalculator {
	/**
	 * where to send risk events to. 
	 * @param transportFactory
	 */
	void setTransportFactory(ITransportFactory transportFactory);
	
	/**
	 * to indicate which position row changed. 
	 * @param rowIndex
	 */
	PNLChangeEvent execution(TimeStamp ts, String tradInstId, double price, double quantity);
	
	/**
	 * to indicate with price row changed. 
	 * @param rowIndex
	 */
	PNLChangeEvent pricesUpdated(int rowIndex);
}

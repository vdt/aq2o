package com.activequant.utils.recorder;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.log4j.Logger;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.activequant.component.ComponentBase;
import com.activequant.domainmodel.ETransportType;
import com.activequant.domainmodel.TimeFrame;
import com.activequant.domainmodel.exceptions.TransportException;
import com.activequant.domainmodel.streaming.MarketDataSnapshot;
import com.activequant.interfaces.archive.IArchiveFactory;
import com.activequant.interfaces.archive.IArchiveWriter;
import com.activequant.interfaces.dao.IDaoFactory;
import com.activequant.interfaces.transport.ITransportFactory;
import com.activequant.interfaces.utils.IEventListener;
import com.activequant.messages.AQMessages;
import com.activequant.messages.AQMessages.BaseMessage;
import com.activequant.messages.AQMessages.BaseMessage.CommandType;
import com.activequant.messages.Marshaller;

/**
 * A market data snapshot recorder.
 * 
 * @author GhostRider
 * 
 */
public class MDSRecorder extends ComponentBase {

	
	private Logger log = Logger.getLogger(MDSRecorder.class);
	final Timer t = new Timer(true);
	final long collectionPhase = 5000l;
	private final ConcurrentLinkedQueue<MarketDataSnapshot> collectionList = new ConcurrentLinkedQueue<MarketDataSnapshot>();
//	private SNMPReporter snmpReporter;

	private Marshaller marshaller = new Marshaller();
	private IArchiveWriter rawWriter = null;

	class InternalTimerTask extends TimerTask {
		int counter;

		@Override
		public void run() {

			Object o = collectionList.poll();
			counter = 0;
			while (o != null) {
				store((MarketDataSnapshot) o);
				o = collectionList.poll();
			}
			log.info("Collected " + counter + " events. ");
			if (counter > 0) {
				try {
					rawWriter.commit();
					log.info("Committed " + counter);
					// snmpReporter.addValue("MDSEVENTS", counter);
				} catch (Exception e) {
					e.printStackTrace();
					log.warn("Error while committing. ", e);
				}
			}
			t.schedule(new InternalTimerTask(),
					(collectionPhase - System.currentTimeMillis()
							% collectionPhase));
		}

		public void store(MarketDataSnapshot mds) {
			if (mds == null)
				return;
			counter++;
			String seriesId = mds.getMdiId();
			if (mds.getBidSizes() != null && mds.getBidSizes().length > 0) {
				double bestBidPx = mds.getBidPrices()[0];
				double bestBidQ = mds.getBidSizes()[0];
				rawWriter.write(seriesId, mds.getTimeStamp(), "BID", bestBidPx);
				rawWriter.write(seriesId, mds.getTimeStamp(), "BIDQUANTITY",
						bestBidQ);
				if (log.isDebugEnabled())
					log.debug("Wrote " + seriesId + ", " + mds.getTimeStamp()
							+ ", " + bestBidPx +", "+ bestBidQ);
				
				// let's dump all layers available. 
				for(int i=0;i<mds.getBidSizes().length;i++){
					rawWriter.write(seriesId, mds.getTimeStamp(), "BID_"+i, mds.getBidPrices()[i]);
					rawWriter.write(seriesId, mds.getTimeStamp(), "BIDQUANTITY_"+i, mds.getBidSizes()[i]);
				}
				// 
			}
			if (mds.getAskSizes() != null && mds.getAskSizes().length > 0) {
				double bestAskPx = mds.getAskPrices()[0];
				double bestAskQ = mds.getAskSizes()[0];
				rawWriter.write(seriesId, mds.getTimeStamp(), "ASK", bestAskPx);
				rawWriter.write(seriesId, mds.getTimeStamp(), "ASKQUANTITY",
						bestAskQ);
				
				if (log.isDebugEnabled())
					log.debug("Wrote " + seriesId + ", " + mds.getTimeStamp()
							+ ", " + bestAskPx +", "+ bestAskQ);
				
				// let's dump all layers available. 
				for(int i=0;i<mds.getAskSizes().length;i++){
					rawWriter.write(seriesId, mds.getTimeStamp(), "ASK_"+i, mds.getAskPrices()[i]);
					rawWriter.write(seriesId, mds.getTimeStamp(), "ASKQUANTITY_"+i, mds.getAskSizes()[i]);
				}
				
			}
		}
	}

	public MDSRecorder(ITransportFactory transFac, IArchiveFactory archFac, IDaoFactory daoFac, String mdiFile) throws Exception {
		super("MDSRecorder", transFac);

		rawWriter = archFac.getWriter(TimeFrame.RAW);

		subscribe(mdiFile);
		t.schedule(
				new InternalTimerTask(),
				(collectionPhase - System.currentTimeMillis() % collectionPhase));
		// t.schedule(new InternalTimerTask(), (collectionPhase -
		// System.currentTimeMillis()%collectionPhase));
		// t.schedule(new InternalTimerTask(), (collectionPhase -
		// System.currentTimeMillis()%collectionPhase));
//
//		snmpReporter = new SNMPReporter(InetAddress.getLocalHost()
//				.getHostAddress(), 65001);
//		snmpReporter.registerOID("MDSEVENTS", "1.3.6.1.1.0", ValueMode.VALUE);
	}

	private void subscribe(String mdiFile) throws IOException,
			TransportException {

		List<String> instruments = new ArrayList<String>();

		BufferedReader br = new BufferedReader(new InputStreamReader(
				new FileInputStream(mdiFile)));
		String l = br.readLine();
		while (l != null) {
			if (!l.startsWith("#") && !l.isEmpty()) {
				String symbol = l;
//				int depth = 1;
				if (l.indexOf(";") != -1) {
					String[] s = l.split(";");
					symbol = s[0];
//					depth = Integer.parseInt(s[1]);
				}
				instruments.add(symbol);
			}
			l = br.readLine();
		}
		for (String s : instruments) {
			log.info("Subscribing to " + s);
			transFac.getReceiver(ETransportType.MARKET_DATA, s).getRawEvent()
					.addEventListener(new IEventListener<byte[]>() {
						@Override
						public void eventFired(byte[] event) {
							BaseMessage bm;
							try {
								bm = marshaller.demarshall(event);
								if(log.isDebugEnabled())
									log.debug("Event type: " + bm.getType());
								if (bm.getType().equals(CommandType.MDS)) {
									MarketDataSnapshot mds = marshaller
											.demarshall(((AQMessages.MarketDataSnapshot) bm
													.getExtension(AQMessages.MarketDataSnapshot.cmd)));
									if(!mds.isResend())
										collectionList.add(mds);
									// else we drop it. 
								}
							} catch (Exception e) {
								e.printStackTrace();
								log.warn("Exception: ", e);
							}
						}
					});
		}
		br.close();
	}

	/**
	 * @param args
	 * @throws Exception 
	 */
	public static void main(String[] args) throws Exception {
		

		ApplicationContext appContext = new ClassPathXmlApplicationContext(
				new String[] { args[0] });

		IDaoFactory idf = (IDaoFactory) appContext.getBean("ibatisDao");
		IArchiveFactory af = (IArchiveFactory) appContext.getBean("archiveFactory");
		System.out.println("ARCH FAC: " + af);
		ITransportFactory transFac = appContext.getBean("jmsTransport", ITransportFactory.class);
		new MDSRecorder(transFac, af, idf, args[1]);
	}

	@Override
	public String getDescription() {
		return "MDSRecorder records market data snapshots. ";
	}

}

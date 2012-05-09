package com.activequant.archive.hbase;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;

import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.log4j.Logger;

import com.activequant.archive.IArchiveReader;
import com.activequant.archive.MultiValueTimeSeriesIterator;
import com.activequant.archive.TSContainer;
import com.activequant.archive.TimeSeriesIterator;
import com.activequant.domainmodel.TimeFrame;
import com.activequant.domainmodel.TimeStamp;
import com.activequant.domainmodel.Tuple;
import com.activequant.timeseries.TSContainer2;
import com.activequant.utils.UniqueTimeStampGenerator;

/**
 * Reader class to read from HBase. All timestamps are in UTC and in Date8Time6
 * format. Due to performance reasons, the reader will not return date8time6
 * objects.
 * 
 * Visibility on package level only is intended! compare the corresponding
 * factory class.
 * 
 * @author ustaudinger
 * 
 */
class HBaseArchiveReader extends HBaseBase implements IArchiveReader {

    private UniqueTimeStampGenerator timeStampGenerator = new UniqueTimeStampGenerator();
    private Logger log = Logger.getLogger(HBaseArchiveReader.class);

    HBaseArchiveReader(final String zookeeperHost, final TimeFrame tf) throws IOException {
        super(zookeeperHost, 2181, "TSDATA_" + tf.toString());
    }

    HBaseArchiveReader(final String zookeeperHost, int zookeeperPort, final TimeFrame tf) throws IOException {
        super(zookeeperHost, zookeeperPort, "TSDATA_" + tf.toString());
    }

    @Deprecated
    HBaseArchiveReader(final TimeFrame tf) throws IOException {
        super("TSDATA_" + tf.toString());
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.activequant.archive.IArchiveReader#getTimeSeries(java.lang.String,
     * java.lang.String, java.lang.Long)
     */
    public TSContainer getTimeSeries(final String instrumentId, final String value, final TimeStamp startTimeStamp) throws Exception {
        return getTimeSeries(instrumentId, value, startTimeStamp, timeStampGenerator.now());
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.activequant.archive.IArchiveReader#getTimeSeries(java.lang.String,
     * java.lang.String, java.lang.Long, java.lang.Long)
     */
    public TSContainer getTimeSeries(final String instrumentId, final String value, final TimeStamp startTimeStamp, final TimeStamp stopTimeStamp)
            throws Exception {
        ResultScanner scanner = getScanner(instrumentId, startTimeStamp, stopTimeStamp);

        List<TimeStamp> timeStamps = new ArrayList<TimeStamp>();
        List<Double> values = new ArrayList<Double>();
        try {
            for (Result rr = scanner.next(); rr != null; rr = scanner.next()) {
                if (rr.containsColumn("numbers".getBytes(), value.getBytes())) {
                    byte[] valB = rr.getValue("numbers".getBytes(), value.getBytes());
                    byte[] tsB = rr.getValue("numbers".getBytes(), "ts".getBytes());
                    Double val = Bytes.toDouble(valB);
                    Long ts = Bytes.toLong(tsB);
                    timeStamps.add(new TimeStamp(ts));
                    values.add(val);
                }
            }
        } finally {
            scanner.close();
        }
        TSContainer ret = new TSContainer(timeStamps.toArray(new TimeStamp[] {}), values.toArray(new Double[] {}));
        return ret;
    }


    public TimeSeriesIterator getTimeSeriesStream(String instrumentId, final String key, TimeStamp startTimeStamp, TimeStamp stopTimeStamp) throws Exception {
        final ResultScanner scanner = getScanner(instrumentId, startTimeStamp, stopTimeStamp);

        return new TimeSeriesIterator() {
            Iterator<Result> resultIterator = scanner.iterator();
            final Result rr = null;

            @Override
            public boolean hasNext() {
                return resultIterator.hasNext();
            }

            @Override
            public Tuple<TimeStamp, Double> next() {
                Result rr = resultIterator.next();
                if (rr != null) {
                    if (rr.containsColumn("numbers".getBytes(), key.getBytes())) {
                        byte[] valB = rr.getValue("numbers".getBytes(), key.getBytes());
                        byte[] tsB = rr.getValue("numbers".getBytes(), "ts".getBytes());
                        Double val = Bytes.toDouble(valB);
                        Long ts = Bytes.toLong(tsB);
                        return new Tuple<TimeStamp, Double>(new TimeStamp(ts), val);
                    }
                    return null;
                } else {
                    scanner.close();
                }
                return null;
            }
        };

    }

    @Override
    public MultiValueTimeSeriesIterator getMultiValueStream(String streamId, TimeStamp startTimeStamp, TimeStamp stopTimeStamp) throws Exception {
        final ResultScanner scanner = getScanner(streamId, startTimeStamp, stopTimeStamp);

        return new MultiValueTimeSeriesIterator() {
            Iterator<Result> resultIterator = scanner.iterator();
            Result rr = null;

            @Override
            public boolean hasNext() {
                return resultIterator.hasNext();
            }

            @Override
            public Tuple<TimeStamp, Map<String, Double>> next() {
                rr = resultIterator.next();
                Tuple<TimeStamp, Map<String, Double>> resultTuple = new Tuple<TimeStamp, Map<String, Double>>();
                Map<String, Double> resultMap = new HashMap<String, Double>();
                resultTuple.setB(resultMap);
            
                if (rr != null) {
                    NavigableMap<byte[],NavigableMap<byte[],byte[]>> valueMap = rr.getNoVersionMap();
                    byte[] tsB = rr.getValue("numbers".getBytes(), "ts".getBytes());
                    Long ts = Bytes.toLong(tsB);
                    TimeStamp timeStamp = new TimeStamp(ts);
                    resultTuple.setA(timeStamp);
                    
                    
                    NavigableMap<byte[], byte[]> numbersMap = valueMap.get("numbers".getBytes());
                    Iterator<Entry<byte[], byte[]>> numbersIt = numbersMap.entrySet().iterator();
                    while(numbersIt.hasNext()){
                        Entry<byte[], byte[]> entry = numbersIt.next();
                        String key = Bytes.toString(entry.getKey());
                        Double value = Bytes.toDouble(entry.getValue());
                        resultMap.put(key,  value);
                    }
                    
                } else {
                    scanner.close();
                }
                return resultTuple;
            }
        };
    }

}

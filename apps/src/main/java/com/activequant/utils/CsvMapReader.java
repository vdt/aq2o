package com.activequant.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import com.activequant.interfaces.utils.IEventListener;
import com.activequant.utils.events.Event;

/**
 * Event based, stream reader, will read CSV lines from a stream and will then
 * fire an event to an event listener.
 * 
 * @author ustaudinger
 * 
 */
public class CsvMapReader {

    private String[] headerNames = null;

    /**
     * use for files without header. if set, read in header will be ignored.
     */
    public void setHeader(String[] header) {
        this.headerNames = header;
    }

    /**
     * will replace all " and ' with nothing. reads in streaming manner. Trims
     * values to remove whitespace.
     * 
     * @param eventListener
     * @param fileName
     * @throws IOException
     */
    public void read(IEventListener<Map<String, String>> eventListener, InputStream inputStream) throws Exception {
        int rowCount = 1;
        String errorLine = "";
        try {
            Event<Map<String, String>> event = new Event<Map<String, String>>();
            event.addEventListener(eventListener);
            BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
            if (headerNames == null) {
                String header = br.readLine();
                header = header.replaceAll("\"", "");
                header = header.replaceAll("'", "");
                headerNames = header.split(",");
            }
            String line = br.readLine();
            while (line != null) {
                errorLine = line;
                rowCount++;
                line = line.replaceAll("\"", "");
                line = line.replaceAll("'", "");
                line = line.trim();
                String[] content = line.split(",");
                Map<String, String> map = new HashMap<String, String>();
                for (int i = 0; i < content.length; i++) {
                    String trimmedH = headerNames[i].trim().toUpperCase();
                    String trimmedC = content[i].trim();
                    map.put(trimmedH, trimmedC);
                }
                event.fire(map);
                line = br.readLine();
            }
        } catch (Exception ex) {
            throw new Exception("Error while parsing CSV stream, line " + rowCount + ": " + errorLine, ex);
        }
    }
}

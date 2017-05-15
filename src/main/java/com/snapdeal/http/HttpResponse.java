package com.snapdeal.http;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class HttpResponse {

	private static Logger log = LoggerFactory.getLogger(HttpResponse.class);
	
	private Map<String, String> headers = new HashMap<String, String>();
	private byte[] bodybytes;
	
	private String httpStatus;
	
	private boolean hasStreamResp;
	
	private ResponseState status = ResponseState.Pending_Read;
	private ResponseParsingException exptn;
	
	public HttpResponse(BufferedInputStream istr, ByteBuffer buf, boolean hasStreamResp) throws IOException, ResponseParsingException{
		parseHeaders(getHeaderBytes(istr, buf));
		bodybytes = getBodyBytes(istr);
		if(this.exptn != null){
			exptn.setText(new String(bodybytes));
			throw exptn;
		}
	}
	
	public String getHTTPStatus(){
		return this.httpStatus;
	}

	public String getHeaderValue(String name){
		return headers.get(name);
	}
	
	public <T extends Serializable> T getObject(Class<T> type) throws ResponseParsingException {
		log.debug("Parsing Body");
		String contype = headers.get("Content-Type");
		if(contype.equals("application/json")){
			ObjectMapper om = new ObjectMapper();
			try {
				return (T) om.readValue(bodybytes, type);
			} catch (JsonParseException e) {
				throw new ResponseParsingException(e);
			} catch (JsonMappingException e) {
				throw new ResponseParsingException(e);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		return null;
	}
	
	public String getBody(){
		BufferedReader rdr = new BufferedReader(
				new InputStreamReader(new ByteArrayInputStream(bodybytes)));
		
		return getFirstLine(rdr);
	}
	
	public ResponseState getState(){
		return this.status;
	}
	
	public static enum ResponseState {
		Pending_Read,
		Completed_Resp,
		Pending_LastChunk,
		Pending_NextChunk,
		Failed_Server,
		Invalid_Req,
		MalFormed_Resp
	}
	
	public static class ResponseParsingException extends Exception {

		private String text;
		
		public ResponseParsingException(String name) {
			super(name);
		}

		public ResponseParsingException(Throwable e) {
			super(e);
		}
		
		public void setText(String text){
			this.text = text;
		}
	}
	
	private void parseHeaders(byte[] hdrbytes) {
		log.debug("Parsing Headers");
		BufferedReader rdr = new BufferedReader(
				new InputStreamReader(new ByteArrayInputStream(hdrbytes)));
		
		try {
			String line = rdr.readLine();
			checkStatus(line);
			log.debug(line);
			line = rdr.readLine();
			log.debug(line);
			while(! line.equals("")){
				int indx = line.indexOf(": ");
				String hdr = line.substring(0, indx);
				String val = line.substring(indx+2);
				headers.put(hdr, val);
				line = rdr.readLine();
				log.debug(line);
			}					
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}		
	}
	
	private void checkStatus(String statusline){
		String[] terms = statusline.split(" ");
		if(terms == null || terms.length < 3)
			this.exptn = new ResponseParsingException(ResponseState.MalFormed_Resp.name());
		
		this.httpStatus = terms[1];
		{
			switch(terms[1].charAt(0)){
			case '4' : this.exptn = new ResponseParsingException(ResponseState.Invalid_Req.name()); break;
			case '5' : this.exptn = new ResponseParsingException(ResponseState.Failed_Server.name()); break;
			}
		}
	}
	
	private byte[] getBodyBytes(InputStream istr) throws IOException{
		log.debug("Reading Body");
		{
			String lenStr = headers.get("Content-Length");
			if(lenStr != null){
				ByteBuffer buf = ByteBuffer.allocate(Integer.parseInt(lenStr));
				istr.read(buf.array(), 0, buf.capacity());
				this.status = ResponseState.Completed_Resp;
				return buf.array();
			} else {				
				String tenc = headers.get("Transfer-Encoding");
				if("chunked".equalsIgnoreCase(tenc)){
					byte[] bytes = readNextChunk(istr);
					if(!hasStreamResp)
						this.status = ResponseState.Pending_LastChunk;
					else 
						this.status = ResponseState.Pending_NextChunk;
					
					return bytes;
				} else {			
					byte[] bytes = readInputEOF(istr);
					this.status = ResponseState.Completed_Resp;
					return bytes;
				}
			}
		}
	}
	
	private static final int crval = '\r';
	private static final int lfval = '\n';
	
	private byte[] readNextChunk(InputStream inp) throws IOException{
		int chunkLen = getNextChunkLen(inp);
		if(chunkLen > 0){
			return readNextChunk(inp, chunkLen); 
		}
		
		return new byte[0];
	}
	
	private int getNextChunkLen(InputStream inp) throws IOException{
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		{
			int n = inp.read();			
			
			// read till the CR
			while(n != -1 && n != crval){
				bos.write(n);
				n = inp.read();				
			}
			
			if(n != -1){
				n = inp.read(); // read LF
			}
			
			return Integer.parseInt(new String(bos.toByteArray()), 16);
		}		
	}
	
	private byte[] readNextChunk(InputStream inp, int len) throws IOException{
		ByteBuffer buf = ByteBuffer.allocate(len);
		
		{
			inp.read(buf.array(), 0, buf.capacity());
			inp.read(); // read CR
			inp.read(); // read LF
		}
		
		return buf.array();
	}
	
	private byte[] readInputEOF(InputStream inp){
		log.debug("Reading Body");
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		try {
			int n = inp.read();
			int cnt = 1;
			Date d = new Date();
			char c = (char)n;
			log.debug(d.getTime() +  " : Read " 
					+ cnt + " bytes, last byte: " + n + " , char: " + c);
			while(n != -1){
				bos.write(n);
				n = inp.read();
				cnt++;
				d = new Date();
				c = (char)n;
				log.debug(d.getTime() +  " : Read " 
				+ cnt + " bytes, last byte: " + n + " , char: " + c);
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return bos.toByteArray();
	}
		
	private String getFirstLine(BufferedReader rdr){
		try {
			String a = rdr.readLine();
			String b = a;
			while(b != null)
				b = rdr.readLine();
			
			return a;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return null;
	}
	
	private byte[] getHeaderBytes(BufferedInputStream istr, ByteBuffer buf) throws IOException{
		log.debug("Reading Header");
		ByteArrayOutputStream bos = new ByteArrayOutputStream(100);
		BufferedInputStream bis = istr;
		{
			if(buf != null){
				buf.flip();
				bos.write(buf.get());
			}
			Boolean isDelim = isHeaderDelim(bis, bos); 
			while(isDelim != null && ! isDelim)
				isDelim = isHeaderDelim(bis, bos);
			
			if(isDelim != null) {
				return  bos.toByteArray();
			}					
		}
		
		return null;
	}
	
	private Boolean isHeaderDelim(InputStream istr, OutputStream ostr) throws IOException{
		int curval = istr.read();
		ostr.write(curval);
		int numbytes = 1;
		
		while( (curval != -1) && (curval != crval) ){				
			curval = istr.read();
			ostr.write(curval);
			numbytes++;
		}
		
		if(curval != -1){
			curval = istr.read();
			ostr.write(curval);
			numbytes++;
			if(curval == lfval){
				if(numbytes > 2)
					return Boolean.FALSE;
				else
					return Boolean.TRUE;
			}				
		}
		
		return null;
	}

}
